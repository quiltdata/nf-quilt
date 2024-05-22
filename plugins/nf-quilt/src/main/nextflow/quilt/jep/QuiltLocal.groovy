/* groovylint-disable ReturnNullFromCatchBlock */
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

import nextflow.quilt.jep.Quilt

import com.quiltdata.quiltcore.Entry
import com.quiltdata.quiltcore.Namespace
import com.quiltdata.quiltcore.Manifest
import com.quiltdata.quiltcore.key.LocalPhysicalKey

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

@Slf4j
@CompileStatic
class QuiltLocal {

    public static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)
    public static final QuiltLocal DOMAIN = new QuiltLocal(INSTALL_ROOT)
    private static final String INSTALL_PREFIX = 'QuiltPackage'
    final Path localRoot

    static List<Path> listDirectory(Path rootPath) {
        return Files.walk(rootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
    }

    static boolean deleteDirectory(Path rootPath) {
        try {
            if (!Files.exists(rootPath)) { return false }
        }
        catch (SecurityException e) {
            log.warn("Cannnot verify whether `$rootPath` exists: $e")
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

    QuiltLocal(Path localRoot) {
        this.localRoot = localRoot
        setup()
    }

    void reset() {
        deleteDirectory(this.localRoot)
        setup()
    }

    void setup() {
        Files.createDirectories(this.localRoot)
    }

    Path packageDest(QuiltPackage pkg) {
        Path folder = Paths.get(this.localRoot.toString(), ".quilt", "installed", pkg.packageName)
        Files.createDirectories(folder)
        return folder
    }

    void resetDest(QuiltPackage pkg) {
        Path dest = packageDest(pkg)
        deleteDirectory(dest)
    }

    Path install(QuiltPackage pkg) {
        String uri = String.format("quilt+s3://%s#package=%s", pkg.bucket, pkg.packageName)
        String domain_path = this.localRoot.toString();
        try {
            String dest_string =  Quilt.install(domain_path, uri)
            log.debug("Package installed to $dest_string")
            return Paths.get(dest_string)
        } catch (Exception e) {
            log.error('ERROR: failed to install package', e)
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
    }

 // https://stackoverflow.com/questions/15022219
    // /does-files-createtempdirectory-remove-the-directory-after-jvm-exits-normally
    void recursiveDeleteOnExit() throws IOException {
        Files.walkFileTree(localRoot, new SimpleFileVisitor<Path>() {

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

    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    String push(QuiltPackage pkg, String msg = 'update', Map meta = [:]) {
        String domain_path = this.localRoot.toString();
        String dest_string = Quilt.commit(domain_path, pkg.packageName, msg)
        try {
          return Quilt.push(domain_path, pkg.packageName)
        } catch (Exception e) {
            log.error('ERROR: Failed to push manifest', e)
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
        return dest_string
    }

}
