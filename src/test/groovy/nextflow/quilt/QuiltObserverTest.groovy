/* groovylint-disable MethodName */
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

import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.Session
import spock.lang.IgnoreIf
import java.nio.file.Files
import nextflow.quilt.jep.QuiltPackage
//import spock.lang.Ignore

import java.nio.file.Paths
import java.nio.file.Path
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltObserverTest extends QuiltSpecification {

    Session mockSession(boolean success = false) {
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        return GroovyMock(Session) {
            getParams() >> [pubNot: 'foo', pubBad: 'foo:bar', outdir: SpecURI(), pubDir: testURI, inDir: quilt_uri]
            isSuccess() >> success
            config >> [quilt: [metadata: [key: 'value']]]
            workDir >> Paths.get('./work')
        }
    }
    QuiltObserver makeObserver(boolean success = false) {
        QuiltObserver observer = new QuiltObserver()
        observer.onFlowCreate(mockSession(success))
        return observer
    }

    void 'should not error on onFlowComplete success'() {
        given:
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        QuiltObserver observer = makeObserver()
        QuiltPath badPath = QuiltPathFactory.parse(quilt_uri)
        when:
        observer.onFlowCreate(mockSession(false))
        observer.onFilePublish(badPath)
        observer.onFlowComplete()
        observer.countPublishedPaths() > 0
        then:
        true
    }

    void 'should not add publishedPaths if uninitialized'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        QuiltPath validPath = QuiltPathFactory.parse(SpecURI())

        when:
        // uninitialized
        observer.onFilePublish(validPath, validPath.localPath())
        then:
        validPath.pkg().isBucketAccessible()
        observer.countPublishedPaths() == 0
    }

    void 'should only add publishedPaths if valid path'() {
        given:
        QuiltObserver observer = makeObserver()
        QuiltPath badPath = QuiltPathFactory.parse('quilt+s3://bucket#package=prefix%2fsuffix')
        QuiltPath validPath = QuiltPathFactory.parse(SpecURI())
        Path localPath = Paths.get('/work/bkt/prefix/suffix')

        when:
        // bad path
        observer.onFlowCreate(mockSession(false))
        observer.onFilePublish(badPath, badPath.localPath())
        then:
        observer.countPublishedPaths() == 0

        when:
        // no source
        observer.onFilePublish(badPath)
        then:
        observer.countPublishedPaths() == 0

        when:
        // local path (treated as overlay)
        observer.onFilePublish(localPath)
        then:
        observer.countPublishedPaths() == 0

        when:
        // valid bucket
        observer.onFilePublish(validPath, validPath.localPath())
        observer.onFlowComplete()
        then:
        validPath.pkg().isBucketAccessible()
        observer.countPublishedPaths() == 1
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'cacheOverlay copies a published S3 file into the package folder'() {
        given: 'a file published to s3://bkt/pre/<pkg>/run/out.txt'
        String name = "overlay${System.nanoTime()}"
        Path destination = Paths.get("/bkt/pre/${name}/run/out.txt")
        QuiltPathify pathify = new QuiltPathify(destination)
        Path cached = pathify.pkg.packageDest().resolve('run/out.txt')
        Path workDir = Files.createTempDirectory('work')
        Path source = workDir.resolve('out.txt')
        Files.writeString(source, 'task output')

        expect:
        pathify.isOverlay
        new QuiltObserver().cacheOverlay(pathify, destination, source)
        Files.readString(cached) == 'task output'

        cleanup:
        pathify.pkg.reset()
        QuiltPackage.deleteDirectory(workDir)
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'cacheOverlay falls back to the published file when the source is #reason'() {
        given: 'the published copy exists (stand-in for the S3 object)'
        Path published = Files.createTempDirectory('s3')
        Path destination = published.resolve("bkt/pre/overlay${System.nanoTime()}/run/out.txt")
        Files.createDirectories(destination.parent)
        Files.writeString(destination, 'published')
        QuiltPathify pathify = new QuiltPathify(Paths.get('/' + published.relativize(destination).toString()))
        Path cached = pathify.pkg.packageDest().resolve('run/out.txt')

        expect:
        new QuiltObserver().cacheOverlay(pathify, destination, source)
        Files.readString(cached) == 'published'

        cleanup:
        pathify.pkg.reset()
        QuiltPackage.deleteDirectory(published)

        where:
        reason          | source
        'missing'       | null
        'moved away'    | Paths.get('/nonexistent/work/out.txt')
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'cacheOverlay records a failed copy and blocks publishing that package'() {
        given: 'neither the task output nor the published copy can be read'
        QuiltObserver observer = new QuiltObserver()
        Path destination = Paths.get("/bkt/pre/overlay${System.nanoTime()}/run/out.txt")
        QuiltPathify pathify = new QuiltPathify(destination)
        String key = pathify.pkgKey()

        expect:
        observer.shouldPublish(key)
        !observer.cacheOverlay(pathify, destination, Paths.get('/nonexistent/out.txt'))
        observer.countFailedCopies(key) == 1
        !observer.shouldPublish(key)
        !Files.exists(pathify.pkg.packageDest().resolve('run/out.txt'))

        cleanup:
        pathify.pkg.reset()
    }

    @IgnoreIf({ env.WRITE_BUCKET == null || System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'onFilePublish adds a file published to an s3 outdir to the package'() {
        given:
        QuiltObserver observer = makeObserver()
        String name = "overlay${System.nanoTime()}"
        Path destination = Paths.get("/${writeBucket}/test/${name}/run/out.txt")
        Path source = Files.createTempFile('out', '.txt')
        Files.writeString(source, 'published output')

        when:
        observer.onFilePublish(destination, source)
        QuiltPathify pathify = new QuiltPathify(destination)

        then:
        observer.countPublishedPaths() == 1
        Files.readString(pathify.pkg.packageDest().resolve('run/out.txt')) == 'published output'

        cleanup:
        pathify?.pkg?.reset()
        Files.deleteIfExists(source)
    }

}
