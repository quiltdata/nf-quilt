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

// https://medium.com/geekculture/how-to-execute-python-modules-from-java-2384041a3d6d
// package nextflow.quilt3.jep

package nextflow.quilt3.jep
import nextflow.quilt3.nio.QuiltPath

import jep.Interpreter;
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.text.SimpleDateFormat
import java.util.Date
import java.lang.ProcessBuilder

@Slf4j
@CompileStatic
class QuiltPackage {
    private static final Map<String,QuiltPackage> packages = [:]
    private static final String installPrefix = "QuiltPackage"
    public static final Path installRoot = Files.createTempDirectory(installPrefix)

    private final String bucket
    private final String pkg_name
    private final String hash
    private final Path folder
    private boolean installed

    static public QuiltPackage ForParsed(QuiltParser parsed) {
        def pkgKey = parsed.toPackageString()
        def pkg = packages.get(pkgKey)
        if( pkg ) return pkg
        pkg = new QuiltPackage(parsed)
        packages[pkgKey] = pkg
        try {
            pkg.install()
        }
        catch (Exception e) {
            log.debug "Package `${parsed.toUriString()}` does not yet exist"
        }
        return pkg
    }

    static protected boolean deleteDirectory(Path rootPath) {
        if (!Files.exists(rootPath)) return false
        try {
            final List<Path> pathsToDelete = Files.walk(rootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
            for(Path path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        }
        catch (java.nio.file.NoSuchFileException e) { }
        return true
    }

    static public String today() {
        Date dateObj =  new Date()
        new SimpleDateFormat('yyyy-MM-dd').format(dateObj)
    }

    QuiltPackage(QuiltParser parsed) {
        this.bucket = parsed.bucket()
        this.pkg_name = parsed.pkg_name()
        this.hash = parsed.hash()
        this.folder = Paths.get(installRoot.toString(), this.toString())
        assert this.folder
        this.setup()
    }

    void reset() {
        deleteDirectory(this.folder)
    }

    void setup() {
        Files.createDirectories(this.folder)
        this.installed = false
    }

    String key_dest() {
        "--dest ${packageDest()}"
    }

    String key_dir() {
        "--dir ${packageDest()}"
    }


    String key_force() {
        "--force true"
    }

    String key_hash() {
        "--top-hash $hash"
    }

    String key_meta(String meta="[]") {
        "--meta '$meta'"
    }

    String key_msg(prefix="") {
        "--message 'nf-quilt3:${prefix}@${today()}'"
    }

    String key_path() {
        "--path=${packageDest()}"
    }

    String key_registry() {
        "--registry s3://${bucket}"
    }

    Object call(String... args) {
        def command = ['quilt3']
        command.addAll(args)
        def cmd = command.join(" ")
        log.debug "call `${cmd}`"

        ProcessBuilder pb = new ProcessBuilder('bash','-c', cmd)
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String result = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();
        log.debug "`call.exitCode` ${exitCode}: ${result}"
    }

    // usage: quilt3 install [-h] [--registry REGISTRY] [--top-hash TOP_HASH] [--dest DEST] [--dest-registry DEST_REGISTRY] [--path PATH] name
    Path install() {
        if ('latest' == hash) {
            call('install',pkg_name,key_registry(),key_dest())
        } else {
            call('install',pkg_name,key_registry(),key_hash(),key_dest())
        }
        installed = true
        packageDest()
    }

    boolean isInstalled() {
        installed
    }

    Path packageDest() {
        folder
    }

    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    boolean push(String msg = "update", String meta = "[]") {
        log.debug "`push` $this"
        try {
            call('push',pkg_name,key_dir(),key_registry(),key_meta(meta),key_msg(msg))
        }
        catch (Exception e) {
            log.error "Failed `push` ${this}: ${e}"
            return false
        }
        return true
    }

    @Override
    String toString() {
        "${bucket}_${pkg_name}".replaceAll(/[-\/]/,'_')
    }

}
