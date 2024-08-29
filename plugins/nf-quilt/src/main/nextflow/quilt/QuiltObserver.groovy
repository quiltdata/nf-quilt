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

import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

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
    final private Map<String,List<Path>> packageOverlays = [:]

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

    // FIXME: Only works on S3 paths with at least four parts
    String extractPackageURI(Path nonQuiltPath) {
        /// Extract s3://bucket/prefix/suffix/{body} from nonQuiltPath
        /// into quilt+s3://bucket#package=prefix%2fsuffix&path=folder/file.ext
        String s3_uri = nonQuiltPath
        String[] parts = s3_uri.split('/')
        String bucket = parts[1]
        String prefix = parts[2]
        String suffix = parts[3]
        String folder_path = parts[4..-2].join('/')
        String file_path = parts[-1]

        String base = "quilt+s3://${bucket}#package=${prefix}%2f${suffix}"
        String uri = "${base}&path=${folder_path}/${file_path}"
        log.debug("extractPackaging[${nonQuiltPath}] -> ${uri}")

        String key = pkgKey(QuiltPathFactory.parse(uri))
        List<Path> current = packageOverlays.get(key, []) as List<Path>
        current << nonQuiltPath
        packageOverlays[key] = current
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

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.debug("`onProcessComplete` ${handler.task}")
        log.debug("`onProcessComplete.trace` ${trace}")
    }

    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.debug("`onProcessCached` ${handler.task}")
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
            List<Path> overlays = packageOverlays.get(key, []) as List<Path>
            log.debug("onFlowComplete.pkg: $path overlays[${overlays?.size()}]: $overlays")
            new QuiltProduct(path, session, overlays)
        }
    }

}
