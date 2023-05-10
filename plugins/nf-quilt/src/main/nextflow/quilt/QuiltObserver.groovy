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

    static QuiltPath asQuiltPath(Path path) {
        if (path == null) {
            return null
        }

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

    static QuiltPath normalizePath(QuiltPath path, Map params) {
        String bkt = path.getBucket()
        String pname = path.getPackageName()
        QuiltPath result = path
        params.each { key, value ->
            String val = "$value"
            if (val.contains(bkt) && val.contains(pname)) {
                QuiltPath npath = QuiltPathFactory.parse(val)
                result = npath
            }
        }
        return result
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        this.paths
    }

    @Override
    void onFilePublish(Path path, Path source) {
        log.debug("onFilePublish.Path[$path].Source[$source]")
        QuiltPath qPath = asQuiltPath(path) ?: asQuiltPath(source)

        if (qPath) {
            QuiltPath npath = normalizePath(qPath, session.getParams())
            this.paths.add(npath)
            log.debug("onFilePublish.QuiltPath[$qPath]: paths=${paths}")
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
