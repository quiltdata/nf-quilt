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
    final private Map<String,String> uniqueURIs = [:]
    final private Map<String,String> publishedURIs = [:]

    // Is this overkill? Do we only ever have one output package per run?
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

    static String pkgKey(QuiltPath path) {
        return "${path.getBucket()}/${path.getPackageName()}"
    }

    static String pathless(String uri) {
        return uri.replaceFirst(/&path=[^&]+/, '')
    }

    String checkPath(QuiltPath path, boolean published = false) {
        log.debug("checkPath[$path] published[$published]")
        String key = pkgKey(path)
        String uri = pathless(path.toUriString())
        // only keep the longest pathless URI for each key
        if (uniqueURIs[key]?.length() < uri.length()) {
            uniqueURIs[key] = uri
        }
        if (published) {
            publishedURIs[key] = uniqueURIs[key]
        }
        return uniqueURIs[key]
    }

    String extractPackageURI(Path nonQuiltPath) {
        String pathString = nonQuiltPath.toUri()
        println("extractPackageURI.pathString[${nonQuiltPath}] -> $pathString")
        String[] partsArray = pathString.split('/')
        def parts = new ArrayList(partsArray.toList())
        parts.eachWithIndex { p, i -> println("extractPackageURI.parts[$i]: $p") }

        if (parts.size() < 3) {
            throw new IllegalArgumentException("Invalid pathString: $pathString ($nonQuiltPath)")
        }
        parts.remove(0)
        parts.remove(0)
        parts.remove(0) // remove 'file:///'
        if (parts[0][1] == ':') {
            parts.remove(0) // remove 'C:'
        }
        String bucket = parts.remove(0)
        String file_path = parts.remove(parts.size() - 1)
        String prefix = parts.size() > 0 ? parts.remove(0) : 'default_prefix'
        String suffix = parts.size() > 0 ? parts.remove(0) : 'default_suffix'
        if (parts.size() > 0) {
            String folder_path = parts.join('/')
            file_path = folder_path + '/' + file_path
        }

        // TODO: should overlay packages always force to new versions?
        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = "${base}&path=${file_path}"
        log.debug("extractPackaging[${nonQuiltPath}] -> ${uri}")

        String key = pkgKey(QuiltPathFactory.parse(uri))
        Map<String, Path> current = packageOverlays.get(key, [:]) as Map<String, Path>
        current[file_path] = nonQuiltPath
        lock.withLock {
            uniqueURIs[key] = base
            publishedURIs[key] = base
            packageOverlays[key] = current
        }
        log.debug("extractPackaging[$key]] -> ${packageOverlays}")
        return uri
    }

    void checkParams(Map params) {
        log.debug("checkParams[$params]")
        params.each { k, value ->
            String uri = "$value"
            if (uri.startsWith(QuiltParser.SCHEME)) {
                log.debug("checkParams.uri[$k]: $uri")
                QuiltPath path = QuiltPathFactory.parse(uri)
                checkPath(path)
            }
        }
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        checkParams(session.getParams())
    }

    // NOTE: TraceFileObserver calls onFilePublish _before_ onFlowCreate
    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.debug("onFilePublish.Path[$destination] <- $source")
        QuiltPath qPath = asQuiltPath(destination)
        if (qPath) {
            checkPath(qPath, true)
        } else {
            String uri = extractPackageURI(destination)
            log.debug("onFilePublish.NonQuiltPath[$destination]: $uri")
        }
    }

    @Override
    void onFlowComplete() {
        log.debug("onFlowComplete.publishedURIs[${publishedURIs.size()}]: $publishedURIs")
        // create QuiltProduct for each unique package URI
        publishedURIs.each { key, uri ->
            QuiltPath path = QuiltPathFactory.parse(uri)
            Map<String, Path> overlays = packageOverlays.get(key, [:]) as Map<String, Path>
            log.debug("onFlowComplete.pkg: $path overlays[${overlays?.size()}]: $overlays")
            new QuiltProduct(path, session, overlays)
        }
    }

}
