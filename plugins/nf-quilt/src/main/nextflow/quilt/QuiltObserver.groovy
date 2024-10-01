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
import nextflow.quilt.nio.QuiltPath
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

    final private Map<String,Object> configMetadata = [:]
    final private Lock lock = new ReentrantLock() // Need this because of threads
    // Is this overkill? Do we ever have more than one output URI per run?
    final private Map<String,QuiltPath> publishedPaths = [:]

    void checkConfig(Map<String, Map<String,Object>> config) {
        Object metadata = config.get('quilt')?.get('metadata')
        if (metadata) {
            configMetadata.putAll(metadata as Map<String,Object>)
        }
    }

    boolean checkExtractedPath(QuiltPathExtractor extract) {
        String key = extract.pkgKey()
        if (key in publishedPaths) {
            return true
        }
        log.debug("checkExtractedPath: $key not in publishedPaths")
        addPublishedPath(key, extract.path)
        return false
    }

    void addPublishedPath(String key, QuiltPath qPath) {
        lock.lock()
        try {
            publishedPaths[key] = qPath
        } finally {
            lock.unlock()
        }
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        this.workDir = session.config.workDir
        checkConfig(session.config)
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.debug("onFilePublish.Path[$destination] <- $source")
        if (!session) {
            log.debug('onFilePublish: no session intialized')
            return
        }
        QuiltPathExtractor extract = new QuiltPathExtractor(destination)
        if (extract.isOverlay && source == null) {
            log.error("onFilePublish.isOverlay: no source for $extract")
            return
        }
        checkExtractedPath(extract)
    }

    @Override
    void onFlowComplete() {
        log.debug("onFlowComplete.publishedPaths[${publishedPaths.size()}]: $publishedPaths")
        // create a QuiltProduct for each unique package key
        publishedPaths.each { key, path ->
            log.debug("onFlowComplete: $key -> $path")
            new QuiltProduct(path, session)
        }
    }

}
