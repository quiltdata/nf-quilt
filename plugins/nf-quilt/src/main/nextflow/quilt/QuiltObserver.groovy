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
import nextflow.trace.TraceObserver

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

    void checkParams(Map params) {
        log.debug("checkParams[$params]")
        params.each { k, value ->
            String uri = "$value"
            if (uri.startsWith(QuiltParser.SCHEME)) {
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
    void onFilePublish(Path path) { //, Path source
        log.debug("onFilePublish.Path[$path]") //.Source[$source]
        QuiltPath qPath = asQuiltPath(path)
        if (qPath) {
            checkPath(qPath, true)
        } else {
            log.warn("onFilePublish.not.QuiltPath: $path")
        }
    }

    @Override
    void onFlowComplete() {
        log.debug("`onFlowComplete` ${publishedURIs}")
        // create QuiltProduct for each unique package URI
        publishedURIs.each { k, uri ->
            QuiltPath path = QuiltPathFactory.parse(uri)
            new QuiltProduct(path, session)
        }
    }

}
