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

    private final Set<QuiltPath> paths = [] as Set
    private Session session
    private Map<String,String> packageURIs = [:]

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

    static Map<String,String> normalizedPaths(Map params) {
        Map<String,String> result = [:]
        params.each { k, value ->
            String uri = "$value"
            if (uri.startsWith(QuiltParser.SCHEME)) {
                log.debug("normalizedPaths.uri[$k]: $uri")
                QuiltPath path = QuiltPathFactory.parse(uri)
                String bkt = path.getBucket()
                String pname = path.getPackageName()
                String key = "${bkt}/${pname}"
                String pathless = uri.replaceFirst(/&path=[^&]+/, '')
                log.debug("normalizedPaths.pathless[$key]: $pathless")
                // only keep the longest match for that key
                if (result[key]?.length() < pathless.length()) {
                    log.debug("normalizedPath[$key] replace ${result[key]}")
                    result[key] = pathless
                }
            }
        }
        return result
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        this.packageURIs = normalizedPaths(session.getParams())
        this.paths // already initialized
    }

    QuiltPath matchPath(QuiltPath path) {
        log.debug("matchPath[$path]")
        String bkt = path.getBucket()
        String pname = path.getPackageName()
        String key = "${bkt}/${pname}"
        String uri = packageURIs[key]
        if (uri) {
            log.debug("matchPath[$path] -> $uri")
            return QuiltPathFactory.parse(uri)
        }
        return path
    }

    // NOTE: TraceFileObserver calls onFilePublish _before_ onFlowCreate
    @Override
    void onFilePublish(Path path) { //, Path source
        log.debug("onFilePublish.Path[$path]") //.Source[$source]
        QuiltPath qPath = asQuiltPath(path)

        if (qPath) {
            QuiltPath npath = matchPath(qPath)
            // add if not already present
            if (npath && !(npath in paths)) {
                log.debug("onFilePublish.QuiltPath[$qPath] -> [$npath] (added)")
                paths << npath
            } else {
                log.debug("onFilePublish.QuiltPath[$qPath] -> [$npath] (skipped)")
            }
        } else {
            log.warn("onFilePublish.QuiltPath missing: $path")
        }
    }

    @Override
    void onFlowComplete() {
        log.debug("`onFlowComplete` ${paths}")
        // publish pkgs to repository
        this.paths.each { path -> new QuiltProduct(path, session) }
    }

}
