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

import java.nio.file.Files
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
    // package key -> number of published files that could not be added to it
    final private Map<String,Integer> failedCopies = [:]

    boolean checkExtractedPath(QuiltPathify pathify) {
        String key = pathify.pkgKey()
        if (key in publishedPaths) {
            return true
        }
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

    int countPublishedPaths() {
        return publishedPaths.size()
    }

    @Override
    void onFlowCreate(Session session) {
        log.info("`onFlowCreate` $session")
        this.session = session
        this.workDir = session.config.workDir
    }

    @Override
    void onFilePublish(Path destination, Path source) {
        // Path source may be null, won't work with older versions of Nextflow
        log.info("onFilePublish.dest:$destination <- src:$source")
        if (session == null) {
            log.warn('onFilePublish: no session intialized')
            return
        }
        QuiltPathify pathify = new QuiltPathify(destination)
        if (!pathify.isBucketAccessible()) {
            log.debug("onFilePublish.isBucketAccessible[false]: $pathify")
            return
        }
        if (pathify.isOverlay) {
            addOverlay(pathify, destination, source)
        }
        checkExtractedPath(pathify)
    }

    /**
     * Add a file published to a plain (non-Quilt) S3 outdir to its package, so
     * the package pushed at the end of the run contains the run's outputs, not
     * just nf-quilt's generated files. The package references the published
     * objects in place; nothing is copied.
     *
     * Lists directories from the task's output; falls back to the published
     * copy when there is no source (older Nextflow) or it is gone (publishDir
     * mode 'move').
     */
    boolean addOverlay(QuiltPathify pathify, Path destination, Path source) {
        Path from = (source != null && Files.exists(source)) ? source : destination
        log.debug("addOverlay: $from -> $pathify")
        if (pathify.addOverlayEntries(from)) {
            return true
        }
        log.error("addOverlay: could not add $destination to $pathify")
        String key = pathify.pkgKey()
        lock.lock()
        try {
            failedCopies[key] = (failedCopies[key] ?: 0) + 1
        } finally {
            lock.unlock()
        }
        return false
    }

    int countFailedCopies(String key) {
        return failedCopies[key] ?: 0
    }

    /**
     * Don't push a package that is missing outputs: a partial package with a
     * SUCCESS message is worse than none.
     */
    boolean shouldPublish(String key) {
        int failed = countFailedCopies(key)
        if (failed > 0) {
            log.error("not publishing ${key}: ${failed} published file(s) could not be added to the package")
            print("\nNOT PUBLISHED: ${key} is missing ${failed} output(s); see errors above\n")
            return false
        }
        return true
    }

    @Override
    void onFlowComplete() {
        log.debug("onFlowComplete.publishedPaths[${publishedPaths.size()}]: $publishedPaths")
        // create a QuiltProduct for each unique package key
        publishedPaths.each { key, path ->
            log.debug("onFlowComplete: $key -> $path")
            if (shouldPublish(key)) {
                new QuiltProduct(path, session)
            }
        }
    }

}
