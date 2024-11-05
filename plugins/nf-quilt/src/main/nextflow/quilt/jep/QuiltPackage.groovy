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

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import java.time.LocalDate

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
class QuiltPackage {

    private static final Map<String,QuiltPackage> PKGS = [:]
    private static final String INSTALL_PREFIX = 'QuiltPackage'
    static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)

    private final String bucket
    private final String packageName
    private final QuiltParser parsed
    private final String hash
    private final Path folder
    private final Map meta
    private boolean installed

    static String osSep() {
        return FileSystems.getDefault().getSeparator()
    }

    static String osJoin(String... parts) {
        return parts.join(osSep())
    }

    static String osConvert(String path) {
        return path.replace('/', FileSystems.getDefault().getSeparator())
    }

    static String today() {
        LocalDate date = LocalDate.now()
        return date.toString()
    }

    static String sanitize(String str) {
        return str.replace('\'', '_')
    }

    static String toJson(Map dict) {
        List<String> entries = dict.collect { key, value ->
            String prefix = JsonOutput.toJson(key)
            String suffix = "toJson.error: ${value}"
            log.debug("QuiltPackage.toJson: ${prefix} [${suffix.length()}]")
            try {
                suffix = JsonOutput.toJson(value)
            }
            catch (Exception e) {
                log.error(suffix, e)
            }
            return "${prefix}:${suffix}".toString()
        }
        return sanitize("{${entries.join(',')}}".toString())
    }

    static void resetPackageCache() {
        PKGS.clear()
    }

    static QuiltPackage forUriString(String uri) {
        QuiltParser parsed = QuiltParser.forUriString(uri)
        return forParsed(parsed)
    }

    static QuiltPackage forParsed(QuiltParser parsed) {
        println("QuiltPackage.forParsed: $parsed")
        boolean isNull = parsed.hasNullBucket()
        if (isNull && !PKGS.isEmpty()) {
            return PKGS.values().last()
        }

        String pkgKey = parsed.toPackageString(true) // ignore metadata for Key
        log.info("QuiltPackage.forParsed[${pkgKey}]")
        def pkg = PKGS.get(pkgKey)
        if (pkg) { return pkg }

        pkg = new QuiltPackage(parsed)
        PKGS[pkgKey] = pkg
        return pkg
    }

    static boolean hasKey(String pkgKey) {
        return PKGS.containsKey(pkgKey)
    }

    static QuiltPackage forKey(String pkgKey) {
        if (hasKey(pkgKey)) {
            return PKGS.get(pkgKey)
        }
        return null
    }

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

    QuiltPackage(QuiltParser parsed) {
        this.parsed = parsed
        this.installed = false
        this.bucket = parsed.getBucket()
        this.packageName = parsed.getPackageName()
        this.hash = parsed.getHash()
        this.meta = parsed.getMetadata()
        this.folder = Paths.get(INSTALL_ROOT.toString(), this.toString())
        log.debug("QuiltPackage.folder[${this.folder}]")
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
        String base = subfolder.toString() + osSep()
        List<String> result = []
        final String[] children = subfolder.list().sort()
        //log.debug("relativeChildren[${base}] $children")
        for (String pathString : children) {
            def relative = pathString.replace(base, '')
            result.add(relative)
        }
        return result
    }

    void reset() {
        deleteDirectory(this.folder)
        setup()
    }

    void setup() {
        Files.createDirectories(this.folder)
        this.installed = false
    }

    boolean is_force() {
        return parsed.options[QuiltParser.P_FORCE]
    }

    boolean isNull() {
        return parsed.hasNullBucket()
    }

    boolean isInstalled() {
        return installed
    }

    boolean isBucketAccessible() {
        S3PhysicalKey key = new S3PhysicalKey(bucket, '', null)
        try {
            key.listRecursively()
        } catch (Exception e) {
            log.error("isBucketAccessible: failed to check $bucket", e)
            return false
        }
        return true
    }

    Path packageDest() {
        return folder
    }

    /*
     * Package methods
     */

    Path install(boolean implicit=false) {
        if (isNull()) {
            log.debug('null bucket: no need to install')
            return null
        }
        Path dest = packageDest()
        String implicitStr = implicit ? 'implicitly ' : ''

        try {
            log.info("${implicitStr}installing $packageName from $bucket...")
            S3PhysicalKey registryPath = new S3PhysicalKey(bucket, '', null)
            Registry registry = new Registry(registryPath)
            Namespace namespace = registry.getNamespace(packageName)
            String resolvedHash = (hash == 'latest' || hash == null || hash == 'null')
              ? namespace.getHash('latest')
              : hash
            log.debug("hash: $hash -> $resolvedHash")
            Manifest manifest = namespace.getManifest(resolvedHash)

            manifest.install(dest)
            log.info("install: ${implicitStr}installed into $dest)")
            log.debug("QuiltPackage.install.Children: ${relativeChildren('')}")
        } catch (IOException e) {
            if (!implicit) {
                log.error("failed to install $packageName", e)
                print("INSTALL FAILED: ${this.parsed}\n")
                e.printStackTrace()
                /* groovylint-disable-next-line ThrowRuntimeException */
                throw new RuntimeException(e)
            }
            log.warn("failed to (implicitly) install $packageName")
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
    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    Manifest push(String msg = 'update', Map meta = [:], String pkg = null) {
        if (isNull()) {
            log.debug('null bucket: no need to push')
            return null
        }
        String pkgName = pkg ?: packageName
        S3PhysicalKey registryPath = new S3PhysicalKey(bucket, '', null)
        Registry registry = new Registry(registryPath)
        Namespace namespace = registry.getNamespace(pkgName)

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
        log.debug("push[${pkgName}]: ${m}")
        try {
            Manifest manifest = m.push(namespace, "nf-quilt:${today()}-${msg}", parsed.workflowName)
            log.debug("pushed[${pkgName}]: ${manifest}")
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

    @Override
    String toString() {
        return "QuiltPackage.${bucket}_${packageName}".replaceAll(/[-\/]/, '_')
    }

    String toUriString() {
        return parsed.toUriString()
    }

    String toKey() {
        return parsed.toPackageString(true)
    }

    String meta_overrides(String key, Serializable baseline = null) {
        Object temp = meta[key] ? meta[key] : baseline
        return temp.toString()
    }

}
