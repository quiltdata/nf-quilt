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
package nextflow.quilt.jep

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import java.time.LocalDate

@Slf4j
@CompileStatic
class QuiltPackage {

    private static final Map<String,QuiltPackage> PKGS = [:]
    private static final String INSTALL_PREFIX = 'QuiltPackage'
    static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)

    private final String bucket
    private final String packageName
    private final String hash
    private final Path folder
    private boolean installed

    static String today() {
        LocalDate date = LocalDate.now()
        return date.toString()
    }

    static QuiltPackage forParsed(QuiltParser parsed) {
        def pkgKey = parsed.toPackageString()
        def pkg = PKGS.get(pkgKey)
        if (pkg) { return pkg }

        pkg = new QuiltPackage(parsed)
        PKGS[pkgKey] = pkg
        try {
            log.debug "Installing `${pkg}` for.pkgKey $pkgKey"
            pkg.install()
        }
        catch (Exception e) {
            log.warn "Package `${parsed.toUriString()}` does not yet exist"
        }
        return pkg
    }

    static List<Path> listDirectory(Path rootPath) {
        return Files.walk(rootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
    }

    static boolean deleteDirectory(Path rootPath) {
        try {
            if (!Files.exists(rootPath)) { return false }
        }
        catch (SecurityException e) {
            log.warn "Cannnot verify whether `$rootPath` exists: $e"
        }
        try {
            final List<Path> pathsToDelete = listDirectory(rootPath)
            for (Path path : pathsToDelete) {
                Files.deleteIfExists(path)
            }
        }
        catch (java.nio.file.NoSuchFileException e) {
            log.debug 'deleteDirectory: ignore non-existent files'
        }
        return true
    }

    QuiltPackage(QuiltParser parsed) {
        this.installed = false
        this.bucket = parsed.getBucket()
        this.packageName = parsed.getPackageName()
        this.hash = parsed.getHash()
        this.folder = Paths.get(INSTALL_ROOT.toString(), this.toString())
        log.debug "QuiltParser.folder[${this.folder}]"
        this.setup()
    }

    /**
     * Returns {@code List<String>} of object keys below a subpath.
     *
     * <p> Because the `quilt3` CLI does not provide a direct way to list
     * the logical keys ("files") inside a package, we have to infer it
     * from the actual files inside the install folder.
     *
     * <p> To do this, we list the full path of the files directly inside
     * the subfolder, then remove the top-level folder ("base") to get
     * the relative keys.
     *
     * @param   subpath
     *          folder inside the package (use '' for top-level)
     *
     * @return  List of the child object keys, as Strings
     */
    List<String> relativeChildren(String subpath) {
        Path subfolder = folder.resolve(subpath)
        String base = subfolder.toString() + '/'
        List<String> result = []
        final String[] children = subfolder.list().sort()
        log.debug "relativeChildren[${base}] $children"
        for (String pathString : children) {
            def relative = pathString.replace(base, '')
            result.add(relative)
        }
        return result
    }

    void reset() {
        deleteDirectory(this.folder)
    }

    void setup() {
        Files.createDirectories(this.folder)
        this.installed = false
    }

    String key_dest() {
        return "--dest ${packageDest()}"
    }

    String key_dir() {
        return "--dir ${packageDest()}"
    }

    String key_force() {
        return '--force true'
    }

    String key_hash() {
        return "--top-hash $hash"
    }

    String key_meta(String meta='[]') {
        return "--meta '$meta'"
    }

    String key_msg(prefix='') {
        return "--message 'nf-quilt:${prefix}@${today()}'"
    }

    String key_path() {
        return "--path=${packageDest()}"
    }

    String key_registry() {
        return "--registry s3://${bucket}"
    }

    int call(String... args) {
        def command = ['quilt3']
        command.addAll(args)
        def cmd = command.join(' ')
        log.debug "call `${cmd}`"

        ProcessBuilder pb = new ProcessBuilder('bash', '-c', cmd)
        pb.redirectErrorStream(true)

        Process p = pb.start()
        String result = new String(p.getInputStream().readAllBytes())
        int exitCode = p.waitFor()
        if (exitCode > 0) {
            log.warn "`call.exitCode` ${exitCode}: ${result}"
        }
        return exitCode
    }

    // usage: quilt3 install [-h] [--registry REGISTRY] [--top-hash TOP_HASH]
    // [--dest DEST] [--dest-registry DEST_REGISTRY] [--path PATH] name
    Path install() {
        if (hash == 'latest' || hash == null || hash == 'null') {
            call('install', packageName, key_registry(), key_dest())
        } else {
            call('install', packageName, key_registry(), key_hash(), key_dest())
        }
        installed = true
        recursiveDeleteOnExit()
        return packageDest()
    }

    // https://stackoverflow.com/questions/15022219
    // /does-files-createtempdirectory-remove-the-directory-after-jvm-exits-normally
    void recursiveDeleteOnExit() throws IOException {
        Path path = packageDest()
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path file, @SuppressWarnings('unused') BasicFileAttributes attrs) {
                file.toFile().deleteOnExit()
                return FileVisitResult.CONTINUE
            }
            @Override
            FileVisitResult preVisitDirectory(Path dir, @SuppressWarnings('unused') BasicFileAttributes attrs) {
                dir.toFile().deleteOnExit()
                return FileVisitResult.CONTINUE
            }

        })
    }

    boolean isInstalled() {
        return installed
    }

    Path packageDest() {
        return folder
    }

    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    boolean push(String msg = 'update', String meta = '[]') {
        log.debug "`push` $this"
        try {
            call('push', packageName, key_dir(), key_registry(), key_meta(meta), key_msg(msg))
        }
        catch (Exception e) {
            log.error("Failed `push` ${this}", e)
            return false
        }
        return true
    }

    @Override
    String toString() {
        return "${bucket}_${packageName}".replaceAll(/[-\/]/, '_')
    }

}
