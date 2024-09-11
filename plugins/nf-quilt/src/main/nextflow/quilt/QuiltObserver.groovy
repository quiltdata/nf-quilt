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

import nextflow.Session
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.trace.TraceObserver

import java.nio.file.Path
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Plugin observer of workflow events
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltObserver implements TraceObserver {

    private Session session
    private String workDir

    // Is this overkill? Do we only ever have one output URI per run?
    private String[] outputPrefixes = ['pub', 'out']
    final private Map<String,String> outputURIs = [:]
    final private Map<String, Map<String, Path>> packageOverlays = [:]
    final private Lock lock = new ReentrantLock() // Need this because of threads

    static QuiltPath asQuiltPath(Path path) {
        if (path in QuiltPath) {
            return (QuiltPath) path
        }
        String strPath = path.getFileName()
        if (strPath.contains('#package')) {
            String url = "${QuiltParser.SCHEME}://${strPath}"
            return QuiltPathFactory.parse(url)
        }
        return null
    }

    static String quiltURIfromS3(String s3uri) {
        log.debug("quiltURIfromS3: $s3uri")
        String[] partsArray = s3uri.split('/')
        List<String> parts = new ArrayList(partsArray.toList())
        // parts.eachWithIndex { p, i -> println("quiltURIfromS3.parts[$i]: $p") }

        if (parts.size() < 2) {
            throw new IllegalArgumentException("Invalid s3uri[${parts.size()}]: $parts")
        }
        parts = parts.drop(2)
        if (parts[0].endsWith(':')) {
            parts = parts.drop(1)
        }
        String bucket = parts.remove(0)
        String dest = parts.join('%2f')
        String suffix = parts.size() > 1 ? parts.removeLast() : 'default_suffix'
        String prefix = parts.size() > 0 ? parts.removeLast() : 'default_prefix'
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = base + '&dest=' + ((dest) ?: '/')
        return uri
    }

    static String pkgKey(QuiltPath path) {
        return QuiltPackage.osConvert("${path.getBucket()}/${path.getPackageName()}")
    }

    void findOutputParams(Map<String, Object> params) {
        log.debug("findOutputParams[$params]")
        params.each { key, value ->
            String uri = "$value"
            if (outputPrefixes.any { key.startsWith(it) && !key.contains('-') }) {
                String[] splits = uri.split(':')
                if (splits.size() < 2) {
                    log.debug("Unrecognized URI[$uri] for key[$key] matching $outputPrefixes")
                    return
                }
                String scheme = splits[0]
                if (scheme == 's3') {
                    uri = quiltURIfromS3(uri)
                } else if (scheme != 'quilt+s3') {
                    log.warn("Unrecognized scheme:$scheme for output URI[$key]: $uri")
                    return
                }
                QuiltPath path = QuiltPathFactory.parse(uri)
                String pkgKey = pkgKey(path)
                outputURIs[pkgKey] = uri
            }
        }
    }

    void checkConfig(Map<String, Map<String,Object>> config) {
        Object prefixes = config.get('quilt')?.get('outputPrefixes')
        if (prefixes) {
            outputPrefixes = prefixes as String[]
        }
    }

    String workRelative(Path src) {
        Path source = src.toAbsolutePath().normalize()
        Path workDir = session.workDir.toAbsolutePath().normalize()
        try {
            Path subPath = workDir.relativize(source)
            // drop first two components, which are the workDir
            Path relPath = subPath.subpath(2, subPath.getNameCount())
            return relPath.toString()
        } catch (IllegalArgumentException e) {
            log.error("workRelative.fallback: $e")
            log.warn("Cannot relativize source:${source.getClass()} to workDir:${workDir.getClass()}")
            return source.toString()
        }
    }

    String pkgRelative(String pkgKey, Path dest) {
        String destString = dest.toAbsolutePath().normalize()
        // find pkgKey in destination.toString()
        int index = destString.indexOf(pkgKey)
        // return the portion after the end of pkgKey
        if (index >= 0) {
            return destString.substring(index + pkgKey.length() + 1)
        }
        return null
    }

    void addOverlay(String pkgKey, Path dest, Path source) {
        lock.lock()
        try {
            Map<String, Path> overlays = packageOverlays.get(pkgKey, [:]) as Map<String, Path>
            String relPath = workRelative(source)
            log.debug("addOverlay[$relPath] = dest:$dest <= source:$source")
            overlays[relPath] = source
            packageOverlays[pkgKey] = overlays
        } finally {
            lock.unlock()
        }
    }

    boolean confirmQuiltPath(QuiltPath qPath) {
        log.debug("confirmQuiltPath[$qPath]")
        String key = pkgKey(qPath)
        log.debug("confirmQuiltPath: key[$key] in outputURIs[${outputURIs.size()}]: $outputURIs")
        return outputURIs.containsKey(key) ? true : false
    }

    boolean canOverlayPath(Path dest, Path source) {
        log.debug("canOverlayPath[$dest] <- $source")
        Set<String> keys = outputURIs.keySet()
        log.debug("canOverlayPath: keys[${keys.size()}]: $keys")
        for (String key : keys) {
            log.debug("canOverlayPath: checking key[$key] for $dest")
            if (dest.toString().contains(key)) {
                log.debug("canOverlayPath: matched key[$key] to $dest")
                addOverlay(key, dest, source)
                return true
            }
        }
        log.error("canOverlayPath: no key found for $dest in $keys")
        return false
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        this.workDir = session.config.workDir
        findOutputParams(session.getParams())
        checkConfig(session.config)
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.debug("onFilePublish.Path[$destination] <- $source")
        if (!outputURIs) {
            // NOTE: TraceFileObserver calls onFilePublish _before_ onFlowCreate
            log.debug('onFilePublish: no outputURIs yet')
            return
        }
        QuiltPath qPath = asQuiltPath(destination)
        boolean ok = (qPath != null) ? confirmQuiltPath(qPath) : canOverlayPath(destination, source)
        if (!ok) {
            log.error("onFilePublish: no match for $destination")
        }
    }

    @Override
    void onFlowComplete() {
        log.debug("onFlowComplete.outputURIs[${outputURIs.size()}]: $outputURIs")
        // create QuiltProduct for each unique package URI
        outputURIs.each { key, uri ->
            QuiltPath path = QuiltPathFactory.parse(uri)
            Map<String, Path> overlays = packageOverlays.get(key, [:]) as Map<String, Path>
            log.debug("onFlowComplete.pkg: $path overlays[${overlays?.size()}]: $overlays")
            new QuiltProduct(path, session, overlays)
        }
    }

}
