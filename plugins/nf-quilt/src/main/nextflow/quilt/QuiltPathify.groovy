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

        println("uriFromS3File: $bucket/$prefix/$suffix/$sub_path")
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = base + '&path=' + sub_path
        return uri
    }

    // Constructor takes a Path and finds QuiltPath and QuiltPackage
    QuiltPathify(Path path) {
        if (path in QuiltPath) {
            this.path = (QuiltPath) path
            this.uri = this.path.toUriString()
            this.pkg = this.path.pkg()
        } else if (!findQuiltPath(path.getFileName().toString())) {
            makeQuiltPath(path)
            this.isOverlay = true
        }
    }

    boolean findQuiltPath(String filename) {
        println("findQuiltPath.filename: $filename")
        // check for '#package' in filename
        if (!filename.toString().contains('#package')) {
            println("findQuiltPath: no package in $filename")
            return false
        }

        uri = "${QuiltParser.SCHEME}://${filename}"
        println("findQuiltPath.uri: $uri")
        path = QuiltPathFactory.parse(this.uri)
        println("findQuiltPath.path: $path")
        pkg = path.pkg()
        println("findQuiltPath.pkg: $pkg")
        println("findQuiltPath.uri2: $uri")
        return true
    }

    boolean makeQuiltPath(Path path) {
        String quiltURI = uriFromS3File(path.toString())
        this.path = QuiltPathFactory.parse(quiltURI)
        this.uri = this.path.toUriString()
        this.pkg = this.path.pkg()
        return true
    }

    boolean copyToPackage(Path source) {
        if (!this.isOverlay) {
            return false
        }
        String localPath = path.sub_paths()
        Path destDir = pkg.packageDest()
        log.debug("copyToPackage: $source -> $destDir / $localPath")
        copyFile(source, destDir.toString(), localPath)
        return true
    }

    String pkgKey() {
        return pkg.toKey()
    }

    boolean hasRoot() {
        return (QuiltPackage.hasKey(pkgKey()))
    }

    QuiltPackage getRoot() {
        if (hasRoot()) {
            return QuiltPackage.forKey(pkgKey())
        }
        return null
    }

}
