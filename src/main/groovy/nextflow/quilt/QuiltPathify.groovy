/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.quilt

import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Extracts QuiltPath objects from published Path
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltPathify  {

    boolean isOverlay = false
    QuiltPath path
    QuiltPackage pkg
    String uri

    /**
     * Converts an S3 path to a Quilt URI.
     *
     * This method takes an absolute path representing an S3 output uri
     * and extracts the bucket, prefix, suffix, and path components
     * to construct a Quilt URI.
     *
     * Example input:
     * <pre>
     * /udp-spec/nf-quilt/s3-test/inputs/a_folder/THING_TWO.md
     * </pre>
     * Extracted components:
     * - bucket: udp-spec
     * - prefix: nf-quilt (or `default_prefix` if missing)
     * - suffix: s3-test (or `default_suffix` if missing)
     * - path: inputs/a_folder/THING_TWO.md
     */
    static String uriFromS3File(String s3path) {
        log.debug("uriFromS3File: $s3path")
        String[] partsArray = s3path.split('/')
        List<String> parts = new ArrayList(partsArray.toList())
        // parts.eachWithIndex { p, i -> println("uriFromS3File.parts[$i]: $p") }
        if (parts.size() < 2) {
            log.error("uriFromS3File: invalid path: $s3path")
            return ''
        }
        parts.remove(0) // remove leading slash
        String file = parts.remove(parts.size() - 1)

        String bucket = parts.size() > 0 ? parts.remove(0) : QuiltParser.NULL_BUCKET
        String prefix = parts.size() > 0 ? parts.remove(0) : 'default_prefix'
        String suffix = parts.size() > 0 ? parts.remove(0) : 'default_suffix'
        // Encode each segment twice: URI.getFragment() decodes once, and
        // QuiltParser.parseQuery decodes the `path=` value again
        List<String> subParts = parts + [file]
        String sub_path = subParts.collect { String seg -> fragmentEncode(seg) }.join('/')

        log.debug("uriFromS3File: $bucket/$prefix/$suffix/$sub_path")
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = base + '&path=' + sub_path
        return uri
    }

    static String fragmentEncode(String segment) {
        String once = URLEncoder.encode(segment, 'UTF-8').replace('+', '%20')
        return once.replace('%', '%25')
    }

    // Constructor takes a Path and finds QuiltPath and QuiltPackage
    QuiltPathify(Path path) {
        if (path in QuiltPath) {
            this.path = (QuiltPath) path
            this.uri = this.path.toUriString()
            this.pkg = this.path.pkg()
        } else if (!findQuiltPath(path.getFileName().toString())) {
            makeQuiltPath(path.toString())
            this.isOverlay = true
        }
    }

    boolean findQuiltPath(String filename) {
        String base = QuiltPath.getRootPackage(filename)
        if (base == null) {
            return false
        }

        uri = "${QuiltParser.SCHEME}://${base}"
        path = QuiltPathFactory.parse(this.uri)
        pkg = path.pkg()
        return true
    }

    boolean makeQuiltPath(String s3File) {
        String quiltURI = uriFromS3File(s3File)
        this.path = QuiltPathFactory.parse(quiltURI)
        this.uri = this.path.toUriString()
        this.pkg = this.path.pkg()
        return true
    }

    /**
     * Add a file (or every file under a directory) published to a plain S3
     * outdir to the package, as entries that reference the published objects
     * in place. Nothing is copied: the package is pushed from S3 at the end.
     *
     * @param from the task's output if it still exists, else the published copy
     * @return false if the output cannot be added (so the push should be blocked)
     */
    boolean addOverlayEntries(Path from) {
        if (!this.isOverlay) {
            return false
        }
        String logicalKey = normalizedKey(path.sub_paths())
        if (!logicalKey) {
            log.error("addOverlayEntries: refusing sub-path `${path.sub_paths()}` for ${pkg}")
            return false
        }
        if (!Files.isDirectory(from)) {
            pkg.addRemoteEntry(logicalKey, localCopy(from))
            return true
        }
        return addOverlayTree(from, logicalKey)
    }

    /**
     * A safe, '/'-separated logical key, or null for the package root or a
     * path that tries to climb out of the package.
     */
    static String normalizedKey(String subPath) {
        List<String> segments = []
        for (String seg : (subPath ?: '').split('/')) {
            if (seg == '..') {
                if (segments.isEmpty()) {
                    return null
                }
                segments.remove(segments.size() - 1)
            } else if (seg && seg != '.') {
                segments.add(seg)
            }
        }
        return segments ? segments.join('/') : null
    }

    /** The task's own file on local disk, if that's where it is (for cheap hashing). */
    static Path localCopy(Path file) {
        return file.getFileSystem() == FileSystems.getDefault() ? file.toAbsolutePath() : null
    }

    /**
     * Record every regular file under {@code root} (publishDir can publish whole
     * directories). Follows symlinks, since staged inputs and their folders are
     * links in the work dir. Returns false if any part could not be read.
     */
    boolean addOverlayTree(Path root, String logicalKey) {
        final List<Path> failed = []
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {

                    @Override
                    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile()) {
                            String rel = root.relativize(file).collect { Path seg -> seg.toString() }.join('/')
                            pkg.addRemoteEntry("${logicalKey}/${rel}".toString(), localCopy(file))
                        }
                        return FileVisitResult.CONTINUE
                    }

                    @Override
                    FileVisitResult visitFileFailed(Path file, IOException exc) {
                        QuiltPathify.log.error("addOverlayTree: cannot read `$file`: $exc")
                        failed.add(file)
                        return FileVisitResult.CONTINUE
                    }

                })
        } catch (IOException | UncheckedIOException e) {
            log.error("addOverlayTree: cannot list `$root`: $e")
            return false
        }
        return failed.isEmpty()
    }

    boolean isBucketAccessible() {
        // ignore internal publishing from work dir
        if (path.getBucket() == 'home') {
            log.warn('isBucketAccessible: ignoring `home` bucket')
            return false
        }
        return pkg.isBucketAccessible()
    }

    String pkgKey() {
        return pkg.toKey()
    }

    String toString() {
        return "QuiltPathify[${uri}]"
    }

}
