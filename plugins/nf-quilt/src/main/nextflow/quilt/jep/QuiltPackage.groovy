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
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.time.LocalDate
import com.quiltdata.quiltcore.Manifest

@Slf4j
@CompileStatic
class QuiltPackage {

    private static final Map<String,QuiltPackage> PKGS = [:]
    private static final String INSTALL_PREFIX = 'QuiltPackage'
    static final Path INSTALL_ROOT = Files.createTempDirectory(INSTALL_PREFIX)

    protected final String bucket
    protected final String packageName
    private final QuiltParser parsed
    private final String hash
    private final Map meta
    private boolean installed

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

    static QuiltPackage forParsed(QuiltParser parsed) {
        def pkgKey = parsed.toPackageString()
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
        Path subfolder = packageDest().resolve(subpath)
        String base = subfolder.toString() + '/'
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
        QuiltLocal.DEFAULT.resetDest(this)
        setup()
    }

    void setup() {
        this.installed = false
        install() // FIXME: only needed for nextflow < 23.12?
    }

    boolean is_force() {
        return parsed.options[QuiltParser.P_FORCE]
    }

    boolean isInstalled() {
        return installed
    }

    Path packageDest() {
        return QuiltLocal.DEFAULT.packageDest(this)
    }

    String workflowName() {
        return parsed.workflowName
    }

    Path install() {
        try {
            Path dest = QuiltLocal.DEFAULT.install(this)
            installed = true
            return dest
        } catch (IOException e) {
            log.error("failed to install $packageName")
            // this is non-fatal error, so we don't want to stop the pipeline
        }
        return null
    }

    // https://docs.quiltdata.com/v/version-5.0.x/examples/gitlike#install-a-package
    Manifest push(String msg = 'update', Map meta = [:]) {
        try {
            Manifest manifest = QuiltLocal.DEFAULT.push(this, "nf-quilt:${today()}-${msg}", meta)
            log.debug("pushed[${this.parsed}]: ${manifest}")
            return manifest
        } catch (Exception e) {
            log.error('ERROR: Failed to push manifest', e)
            print("FAILED: ${this.parsed}\n")
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
        /* groovylint-disable-next-line ReturnNullFromCatchBlock */
        return null
    }

    @Override
    String toString() {
        return "QuiltPackage.${bucket}_${packageName}".replaceAll(/[-\/]/, '_')
    }

    String meta_overrides(String key, Serializable baseline = null) {
        Object temp = meta[key] ? meta[key] : baseline
        return temp.toString()
    }

}
