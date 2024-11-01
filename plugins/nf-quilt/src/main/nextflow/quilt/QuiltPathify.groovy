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

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

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

    /*
     * Copy a file from source to destRoot/relpath
     */
    static void copyFile(Path source, String destRoot, String relpath) {
        Path dest  = Paths.get(destRoot, relpath.split('/') as String[])
        try {
            dest.getParent().toFile().mkdirs() // ensure directories exist first
            Files.copy(source, dest)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$source` to `$dest` in `${destRoot}`")
        }
    }

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
        String folder = parts.join('/')
        String sub_path = folder.length() > 0 ? folder + '/' + file : file

        log.debug("uriFromS3File: $bucket/$prefix/$suffix/$sub_path")
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = base + '&path=' + sub_path
        return uri
    }

    // Constructor takes a Path and finds QuiltPath and QuiltPackage
    QuiltPathify(Path path) {
        println("QuiltPathify: $path")
        if (path in QuiltPath) {
            this.path = (QuiltPath) path
            println("\tQuiltPathify.QuiltPath: $this.path")
            this.uri = this.path.toUriString()
            println("\t\tQuiltPathify.QuiltPath.uri: $this.uri")
            this.pkg = this.path.pkg()
            println("\t\tQuiltPathify.QuiltPath.pkg: $this.pkg")
        } else if (!findQuiltPath(path.getFileName().toString())) {
            makeQuiltPath(path.toString())
            this.isOverlay = true
        }
    }

    boolean findQuiltPath(String filename) {
        println("findQuiltPath: $filename")
        String base = QuiltPath.getRootPackage(filename)
        if (base == null) {
            return false
        }

        uri = "${QuiltParser.SCHEME}://${base}"
        println("\tfindQuiltPath.uri: $uri")
        path = QuiltPathFactory.parse(this.uri)
        println("\tfindQuiltPath.path: $path")
        pkg = path.pkg()
        println("\tfindQuiltPath.pkg: $pkg")
        return true
    }

    boolean makeQuiltPath(String s3File) {
        println("makeQuiltPath: $s3File")
        String quiltURI = uriFromS3File(s3File)
        println("\tmakeQuiltPath.quiltURI: $quiltURI")
        this.path = QuiltPathFactory.parse(quiltURI)
        println("\tmakeQuiltPath.path: $path")
        this.uri = this.path.toUriString()
        println("\tmakeQuiltPath.uri: $uri")
        this.pkg = this.path.pkg()
        println("\tmakeQuiltPath.pkg: $pkg")
        return true
    }

    boolean copyToCache(Path source) {
        println("copyToCache: $source -> $path")
        if (!this.isOverlay) {
            return false
        }
        String localPath = source.getFileName() // FIXME: should be relative to workdir
        println("\tcopyToCache.localPath: $localPath")
        Path destDir = pkg.packageDest()
        println("\tcopyToCache.destDir: $destDir")
        println("copyToCache: $source -> $destDir / $localPath")
        copyFile(source, destDir.toString(), localPath)
        return true
    }

    boolean isBucketAccessible() {
        return pkg.isBucketAccessible()
    }

    String pkgKey() {
        return pkg.toKey()
    }

    String toString() {
        return "QuiltPathify[${uri}]"
    }

}
