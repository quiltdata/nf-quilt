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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.stream.Collectors
import java.time.LocalDate

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.quiltdata.quiltcore.Entry
import com.quiltdata.quiltcore.Registry
import com.quiltdata.quiltcore.Namespace
import com.quiltdata.quiltcore.Manifest
import com.quiltdata.quiltcore.S3ClientStore
import com.quiltdata.quiltcore.key.LocalPhysicalKey
import com.quiltdata.quiltcore.key.PhysicalKey
import com.quiltdata.quiltcore.key.S3PhysicalKey
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

@Slf4j
@CompileStatic
class QuiltPackage {

    private static final Map<String,QuiltPackage> PKGS = [:]
    private static final String INSTALL_PREFIX = 'QuiltPackage'
    static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)
    // concurrent HEAD/hash requests when referencing published outputs
    static final int REMOTE_THREADS = 16

    private final String packageName
    private final String bucket
    private final QuiltParser parsed
    private final String hash
    private final Path folder
    private final Map meta
    private boolean installed
    // Outputs already published to this package's S3 prefix (overlay mode):
    // logical key -> the task's local copy, if any (only used to hash cheaply)
    private final Map<String,Path> remoteEntries = Collections.synchronizedMap(new TreeMap<String,Path>())

    static String osSep() {
        return FileSystems.getDefault().getSeparator()
    }

    static String osConvert(String path) {
        return path.replace('/', FileSystems.getDefault().getSeparator())
    }

    static String today() {
        LocalDate date = LocalDate.now()
        return date.toString()
    }

    static void resetPackageCache() {
        PKGS.clear()
    }

    static QuiltPackage forParsed(QuiltParser parsed) {
        boolean isNull = parsed.hasNullBucket()
        if (isNull && !PKGS.isEmpty()) {
            return PKGS.values().last()
        }

        String pkgKey = parsed.toPackageString(true) // ignore metadata for Key
        log.debug("QuiltPackage.forParsed[${pkgKey}]")
        def pkg = PKGS.get(pkgKey)
        if (pkg) { return pkg }

        pkg = new QuiltPackage(parsed)
        PKGS[pkgKey] = pkg
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
            log.warn("Cannnot verify whether `$rootPath` exists: $e")
        }
        try {
            final List<Path> pathsToDelete = listDirectory(rootPath)
            for (Path path : pathsToDelete) {
                Files.deleteIfExists(path)
            }
        }
        catch (NoSuchFileException e) {
            log.debug "deleteDirectory: ignore non-existent files\n$e"
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
        remoteEntries.clear()
        setup()
    }

    /**
     * Include an object already published at s3://bucket/packageName/logicalKey
     * in the package, in place, instead of copying it into packageDest().
     */
    void addRemoteEntry(String logicalKey, Path localCopy = null) {
        remoteEntries.put(logicalKey, localCopy)
    }

    Map<String,Path> getRemoteEntries() {
        synchronized (remoteEntries) {
            return new TreeMap<String,Path>(remoteEntries)
        }
    }

    void setup() {
        Files.createDirectories(this.folder)
        this.installed = false
    }

    boolean is_force() {
        return parsed.getOptions(QuiltParser.P_FORCE)
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
        } catch (IOException e) {
            log.error("isBucketAccessible: failed to check $bucket", e)
            return false
        }
        return true
    }

    Path packageDest() {
        return folder
    }

    String getPackageName() {
        return packageName
    }

    Map getMetadata() {
        return this.meta
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
            log.debug("${implicitStr}installing $packageName from $bucket...")
            String registryURI = "s3://${bucket}"
            Namespace namespace = Registry.CreateNamespaceAtUri(packageName, registryURI)
            String resolvedHash = (hash == 'latest' || hash == null || hash == 'null')
              ? namespace.getHash('latest')
              : hash
            log.debug("hash: $hash -> $resolvedHash")
            Manifest manifest = namespace.getManifest(resolvedHash)

            manifest.install(dest)
            log.debug("install: ${implicitStr}installed into $dest)")
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
        String registryURI = "s3://${bucket}"
        Namespace namespace = Registry.CreateNamespaceAtUri(pkgName, registryURI)

        Map<String, Object> user_meta = meta + this.meta as Map<String, Object>
        //     public static Manifest BuildFromDir(Path dir, Object user_meta, String regex) {

        Manifest m = Manifest.BuildFromDir(packageDest(), user_meta, null)
        log.debug("push[${pkgName}]: ${m}")
        try {
            String message = "nf-quilt:${today()}-${msg}"
            Manifest manifest = remoteEntries.isEmpty()
                ? m.push(namespace, message, parsed.workflowName)
                : pushWithRemote(namespace, m, message, parsed.workflowName)
            log.debug("pushed[${pkgName}]: ${manifest}")
            return manifest
        } catch (Exception e) {
            log.error('ERROR: Failed to push manifest', e)
            print("FAILED: ${this.parsed}\n")
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
        // return m
    }

    /**
     * Push a package whose outputs are already in S3 (overlay mode). The
     * manifest references the published objects where they are; only the files
     * in packageDest() (README, metadata, summary) are uploaded.
     *
     * Mirrors quiltcore's Manifest.push, which only accepts local files.
     */
    Manifest pushWithRemote(Namespace namespace, Manifest local, String message, String workflow) {
        String prefix = namespace.getName()
        S3Client s3 = S3ClientStore.getClient(bucket)
        SortedMap<String,Entry> localEntries = local.getEntries()
        Map<String,Path> remotes = getRemoteEntries().findAll { String key, Path p -> !localEntries.containsKey(key) }
        log.info("push[${prefix}]: ${localEntries.size()} generated file(s), ${remotes.size()} published output(s)")

        SortedMap<String,Entry> entries = new TreeMap<String,Entry>(remoteManifestEntries(s3, prefix, remotes))
        entries.putAll(localEntries)
        Manifest.Builder draft = Manifest.builder()
        draft.setMetadata(local.getMetadata())
        entries.each { String key, Entry entry -> draft.addEntry(key, entry) }
        JsonNode workflowInfo = validate(draft.build(), namespace, message, workflow)

        ObjectNode metadata = local.getMetadata()
        metadata.put('message', message)
        if (workflowInfo == null) {
            metadata.remove('workflow')
        } else {
            metadata.set('workflow', workflowInfo)
        }
        Manifest.Builder builder = Manifest.builder()
        builder.setMetadata(metadata)
        entries.each { String key, Entry entry ->
            builder.addEntry(key, entry.getPhysicalKey() in LocalPhysicalKey
                ? uploadEntry(s3, "${prefix}/${key}", entry)
                : entry)
        }
        Manifest manifest = builder.build()
        writeRevision(namespace, manifest)
        return manifest
    }

    /** Look up (and hash) each published output, in parallel. */
    Map<String,Entry> remoteManifestEntries(S3Client s3, String prefix, Map<String,Path> remotes) {
        Map<String,Entry> result = [:]
        if (remotes.isEmpty()) {
            return result
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(REMOTE_THREADS, remotes.size()))
        try {
            Map<String,Future<Entry>> futures = [:]
            remotes.each { String key, Path localCopy ->
                String s3Key = "${prefix}/${key}"
                futures[key] = pool.submit({ -> remoteEntry(s3, s3Key, localCopy) } as Callable<Entry>)
            }
            List<String> missing = []
            futures.each { String key, Future<Entry> future ->
                try {
                    result[key] = future.get()
                } catch (ExecutionException e) {
                    log.error("push: cannot add published output `${key}`: ${e.cause}")
                    missing.add(key)
                }
            }
            if (missing) {
                throw new IOException("${missing.size()} published output(s) missing from s3://${bucket}/${prefix}: " +
                    missing.take(10).join(', '))
            }
        } finally {
            pool.shutdownNow()
        }
        return result
    }

    /**
     * Entry for the object at s3://bucket/s3Key, pinned to its current version.
     * Hashes the task's local copy when it is still there and the same size,
     * otherwise streams the object (no local disk either way).
     */
    Entry remoteEntry(S3Client s3, String s3Key, Path localCopy) {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(s3Key).build()
        HeadObjectResponse head = s3.headObject(request)
        long size = head.contentLength()
        S3PhysicalKey physical = new S3PhysicalKey(bucket, s3Key, head.versionId())
        boolean useLocal = localCopy != null && Files.isRegularFile(localCopy) && Files.size(localCopy) == size
        PhysicalKey source = useLocal ? new LocalPhysicalKey(localCopy.toAbsolutePath()) : physical
        Entry.Hash hash = new Entry(source, size, null, null).withHash().getHash()
        return new Entry(physical, size, hash, null)
    }

    Entry uploadEntry(S3Client s3, String s3Key, Entry entry) {
        Entry hashed = entry.withHash()
        Path file = Paths.get(((LocalPhysicalKey) entry.getPhysicalKey()).getPath())
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(s3Key).build()
        PutObjectResponse response = s3.putObject(request, RequestBody.fromFile(file))
        S3PhysicalKey physical = new S3PhysicalKey(bucket, s3Key, response.versionId())
        return new Entry(physical, hashed.getSize(), hashed.getHash(), hashed.getMetadata())
    }

    /** Run quiltcore's (private) workflow validation, as Manifest.push does. */
    static JsonNode validate(Manifest manifest, Namespace namespace, String message, String workflow) {
        Method method = Manifest.getDeclaredMethod('validate', Namespace, String, String)
        method.setAccessible(true)
        try {
            return (JsonNode) method.invoke(manifest, namespace, message, workflow)
        } catch (InvocationTargetException e) {
            throw e.getCause()
        }
    }

    /** Write the manifest and point `latest` (and a timestamp tag) at it, as Manifest.push does. */
    static void writeRevision(Namespace namespace, Manifest manifest) {
        String topHash = manifest.calculateTopHash()
        log.info("push: writing manifest ${topHash}")
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        manifest.serializeToOutputStream(out)
        namespace.getVersions().resolve(topHash).putBytes(out.toByteArray())
        byte[] hashBytes = topHash.getBytes(StandardCharsets.UTF_8)
        String timestamp = (System.currentTimeMillis().intdiv(1000L)).toString()
        namespace.getPath().resolve(timestamp).putBytes(hashBytes)
        namespace.getPath().resolve('latest').putBytes(hashBytes)
    }

    @Override
    String toString() {
        return "QuiltPackage.${bucket}_${packageName}".replaceAll(/[-\/]/, '_')
    }

    String toUriString() {
        return parsed.toUriString()
    }

    String toCatalogURL(String catalog) {
        return "https://${catalog}/b/${bucket}/packages/${packageName}"
    }

    String toKey() {
        return parsed.toPackageString(true)
    }

}
