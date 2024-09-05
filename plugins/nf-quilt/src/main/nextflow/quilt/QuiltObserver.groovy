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
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.trace.TraceObserver

import java.nio.file.Path
// import java.util.concurrent.locks.Lock
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

    // Is this overkill? Do we only ever have one output URI per run?
    private String[] outputPrefixes = ['pub', 'out']
    final private Map<String,String> outputURIs = [:]
    final private Map<String, Map<String, Path>> packageOverlays = [:]
    // final private Lock lock = new ReentrantLock() // Need this because of threads

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

    static String pkgKey(QuiltPath path) {
        return "${path.getBucket()}/${path.getPackageName()}"
    }

    static String quiltURIfromS3(String s3uri) {
        String[] partsArray = s3uri.split('/')
        List<String> parts = new ArrayList(partsArray.toList())
        parts.eachWithIndex { p, i -> println("quiltURIfromS3.parts[$i]: $p") }

        if (parts.size() < 3) {
            throw new IllegalArgumentException("Invalid s3uri[${parts.size()}]: $parts")
        }
        parts = parts.drop(3)
        if (parts[0].endsWith(':')) {
            parts = parts.drop(1)
        }
        String bucket = parts.remove(0)
        String prefix = parts.size() > 0 ? parts.remove(0) : 'default_prefix'
        String suffix = parts.size() > 0 ? parts.remove(0) : 'default_suffix'
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = (parts.size() > 0) ? "${base}&path=${parts.join('/')}" : base
        return uri
    }

    void findOutputParams(Map<String, Object> params) {
        log.debug("findOutputParams[$params]")
        params.each { key, value ->
            String uri = "$value"
            if (outputPrefixes.any { key.startsWith(it) }) {
                String[] splits = uri.split(':')
                if (splits.size() == 0) {
                    log.warn("Output parameter not a URI: $uri")
                    return
                }
                String scheme = splits[0]
                if (scheme == 's3') {
                    uri = quiltURIfromS3(uri)
                } else if (scheme != 'quilt+s3') {
                    log.warn("Unrecognized output URI: $uri")
                }
                outputURIs[key] = uri
            }
        }
    }

    void checkConfig(Map<String, Map<String,Object>> config) {
        Object prefixes = config.get('quilt')?.get('outputPrefixes')
        if (prefixes) {
            outputPrefixes = prefixes as String[]
        }
    }

    boolean confirmPath(QuiltPath path) {
        log.debug("checkPath[$path]")
        String key = pkgKey(path)
        if (!outputURIs.containsKey(key)) {
            log.warn("Output URI not found for key[$key] from path[$path]")
            return false
        }
        return true
    }

    boolean matchPath(String path) {
        log.debug("matchPath[$path]")
        Set<String> keys = outputURIs.keySet()
        if (keys.contains(path)) {
            log.debug("matchPath: found key for $path")
            return true
        }
        log.warn("matchPath: no key found for $path in $keys")
        return false
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        findOutputParams(session.getParams())
        checkConfig(session.config)
    }

    // NOTE: TraceFileObserver calls onFilePublish _before_ onFlowCreate
    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.debug("onFilePublish.Path[$destination] <- $source")
        QuiltPath qPath = asQuiltPath(destination)
        if (qPath) {
            confirmPath(qPath)
        } else {
            matchPath(destination.toString())
        }
        confirmPath(qPath)
    }

    @Override
    void onFlowComplete() {
        log.debug("onFlowComplete.outputURIs[${outputURIs.size()}]: $outputURIs")
        // create QuiltProduct for each unique package URI
        outputURIs.each { key, uri ->
            QuiltPath path = QuiltPathFactory.parse(uri)
            Map<String, Path> overlays = packageOverlays.get(key, [:]) as Map<String, Path>
            // log.debug("onFlowComplete.pkg: $path overlays[${overlays?.size()}]: $overlays")
            new QuiltProduct(path, session, overlays)
        }
    }

}
