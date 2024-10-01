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
class QuiltPathExtractor  {

    private QuiltPackage pkg
    private String uri
    QuiltPath path
    boolean isOverlay = false

    static QuiltPathExtractor fromString(String path) {
        return new QuiltPathExtractor(Paths.get(path))
    }

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
     * This method takes an S3 path and extracts the bucket, prefix, suffix, and path components
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
    static String quiltURIfromPath(String s3path) {
        log.debug("quiltURIfromPath: $s3path")
        String[] partsArray = s3path.split('/')
        List<String> parts = new ArrayList(partsArray.toList())
        // parts.eachWithIndex { p, i -> println("quiltURIfromPath.parts[$i]: $p") }

        String bucket = parts.remove(0) ?: QuiltParser.NULL_BUCKET
        String prefix = parts.remove(0) ?: 'default_prefix'
        String suffix = parts.remove(0) ?: 'default_suffix'
        String dest = parts.join('/')

        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = base + '&dest=' + ((dest) ?: '/')
        return uri
    }

    // Constructor takes a Path and finds QuiltPath and QuiltPackage
    QuiltPathExtractor(Path path) {
        if (path in QuiltPath) {
            this.path = (QuiltPath) path
            this.uri = this.path.toUriString()
            this.pkg = this.path.pkg()
        } else if (!findQuiltPath(path.getFileName())) {
            makeQuiltPath(path)
            this.isOverlay = true
        }
    }

    boolean findQuiltPath(Path filename) {
        if (!filename.contains('#package')) {
            return false
        }

        this.uri = "${QuiltParser.SCHEME}://${filename}"
        this.path = QuiltPathFactory.parse(this.uri)
        this.pkg = this.path.pkg()
        String key = this.pkg.toKey()
        if (QuiltPackage.hasKey(key)) {
            this.pkg = QuiltPackage.forUriString(this.uri)
            this.uri = this.pkg.toUriString() // may contain metadata
        }
        return true
    }

    boolean makeQuiltPath(Path path) {
        String quiltURI = quiltURIfromPath(path.toString())
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

}
