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
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

import com.quiltdata.quiltcore.Entry
import com.quiltdata.quiltcore.Registry
import com.quiltdata.quiltcore.Namespace
import com.quiltdata.quiltcore.Manifest
import com.quiltdata.quiltcore.key.LocalPhysicalKey
import com.quiltdata.quiltcore.key.S3PhysicalKey

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

@Slf4j
@CompileStatic
class QuiltLocal {

    private static final String INSTALL_PREFIX = 'QuiltPackage'
    static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)
    final Path cachePath

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

    QuiltLocal(Path cachePath) {
        this.cachePath = cachePath
    }

    QuiltLocal() {
        this.cachePath = INSTALL_ROOT
    }

    void reset() {
        deleteDirectory(this.folder)
        setup()
    }

    void setup() {
        Files.createDirectories(this.folder)
        this.installed = false
        install() // FIXME: only needed for nextflow < 23.12?
    }

    Path install() {
        Path dest = packageDest()

        try {
            log.info("installing $packageName from $bucket...")
            S3PhysicalKey registryPath = new S3PhysicalKey(bucket, '', null)
            Registry registry = new Registry(registryPath)
            Namespace namespace = registry.getNamespace(packageName)
            String resolvedHash = (hash == 'latest' || hash == null || hash == 'null')
              ? namespace.getHash('latest')
              : hash
            log.debug("hash: $hash -> $resolvedHash")
            Manifest manifest = namespace.getManifest(resolvedHash)

            manifest.install(dest)
            log.debug("done: installed into $dest)")
            println("Children: ${relativeChildren('')}")
        } catch (IOException e) {
            log.error("failed to install $packageName")
            // this is non-fatal error, so we don't want to stop the pipeline
            /* groovylint-disable-next-line ReturnNullFromCatchBlock */
            return null
        }

        installed = true
        recursiveDeleteOnExit()

        return dest
    }

 // https://stackoverflow.com/questions/15022219
    // /does-files-createtempdirectory-remove-the-directory-after-jvm-exits-normally
    void recursiveDeleteOnExit() throws IOException {
        Path path = cachePath
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

    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    Manifest push(String msg = 'update', Map meta = [:]) {
        S3PhysicalKey registryPath = new S3PhysicalKey(bucket, '', null)
        Registry registry = new Registry(registryPath)
        Namespace namespace = registry.getNamespace(packageName)

        Manifest.Builder builder = Manifest.builder()

        Files.walk(packageDest()).filter(f -> Files.isRegularFile(f)).forEach(f -> {
            log.debug("push: ${f} -> ${packageDest()}")
            String logicalKey = packageDest().relativize(f)
            LocalPhysicalKey physicalKey = new LocalPhysicalKey(f)
            long size = Files.size(f)
            builder.addEntry(logicalKey, new Entry(physicalKey, size, null, null))
        });

        Map<String, Object> fullMeta = [
            'version': Manifest.VERSION,
            'user_meta': meta + this.meta,
        ]
        ObjectMapper mapper = new ObjectMapper()
        builder.setMetadata((ObjectNode)mapper.valueToTree(fullMeta))

        Manifest m = builder.build()
        log.debug("push[${this.parsed}]: ${m}")
        try {
            Manifest manifest = m.push(namespace, "nf-quilt:${today()}-${msg}", parsed.workflowName)
            log.debug("pushed[${this.parsed}]: ${manifest}")
            return manifest
        } catch (Exception e) {
            log.error('ERROR: Failed to push manifest', e)
            print("FAILED: ${this.parsed}\n")
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
        return m
    }

}
