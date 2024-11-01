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

    final private Lock lock = new ReentrantLock() // Need this because of threads
    // Is this overkill? Do we ever have more than one output URI per run?
    final private Map<String,QuiltPathify> publishedPaths = [:]

    boolean checkExtractedPath(QuiltPathify pathify) {
        String key = pathify.pkgKey()
        if (key in publishedPaths) {
            return true
        }
        log.debug("checkExtractedPath: $key not in publishedPaths")
        addPublishedPath(key, pathify)
        return false
    }

    void addPublishedPath(String key, QuiltPathify pathify) {
        lock.lock()
        try {
            publishedPaths[key] = pathify
        } finally {
            lock.unlock()
        }
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug("`onFlowCreate` $this")
        this.session = session
        this.workDir = session.config.workDir
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.info("\nonFilePublish.dest:$destination <- src:$source")
        if (!session) {
            log.debug('onFilePublish: no session intialized')
            return
        }
        QuiltPathify pathify = new QuiltPathify(destination)
        if (!pathify.bucketExists()) {
            log.debug("onFilePublish.bucketExists[false]: $pathify")
            return
        }
        if (pathify.isOverlay && source == null) {
            log.error("onFilePublish.isOverlay: no source for $pathify")
            return
        }
        checkExtractedPath(pathify)
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
