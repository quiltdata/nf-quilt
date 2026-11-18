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
import nextflow.quilt.jep.QuiltPackage.RemoteEntry
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
    void 'addOverlay references a published S3 file from the package'() {
        given: 'a file published to s3://bkt/pre/<pkg>/run/out.txt'
        String name = "overlay${System.nanoTime()}"
        Path destination = Paths.get("/bkt/pre/${name}/run/out.txt")
        QuiltPathify pathify = new QuiltPathify(destination)
        Path workDir = Files.createTempDirectory('work')
        Path source = workDir.resolve('out.txt')
        Files.writeString(source, 'task output')

        expect:
        pathify.isOverlay
        new QuiltObserver().addOverlay(pathify, destination, source)
        pathify.pkg.getRemoteEntries() == ['run/out.txt': new RemoteEntry("pre/${name}/run/out.txt", source.toAbsolutePath())]
        !Files.exists(pathify.pkg.packageDest().resolve('run/out.txt'))

        cleanup:
        pathify.pkg.reset()
        QuiltPackage.deleteDirectory(workDir)
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'addOverlay lists a directory from the published copy when the source is #reason'() {
        given: 'the published directory exists (stand-in for the S3 prefix)'
        Path published = Files.createTempDirectory('s3')
        Path destination = published.resolve("bkt/pre/overlay${System.nanoTime()}/run/reports")
        Files.createDirectories(destination)
        Files.writeString(destination.resolve('a.txt'), 'published')
        QuiltPathify pathify = new QuiltPathify(Paths.get('/' + published.relativize(destination).toString()))

        expect:
        new QuiltObserver().addOverlay(pathify, destination, source)
        pathify.pkg.getRemoteEntries().keySet() == ['run/reports/a.txt'] as Set

        cleanup:
        pathify.pkg.reset()
        QuiltPackage.deleteDirectory(published)

        where:
        reason          | source
        'missing'       | null
        'moved away'    | Paths.get('/nonexistent/work/reports')
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'addOverlay records a failure and blocks publishing that package'() {
        given: 'a published path that cannot be placed inside its package'
        QuiltObserver observer = new QuiltObserver()
        Path destination = Paths.get("/bkt/pre/overlay${System.nanoTime()}/../../escape/out.txt")
        QuiltPathify pathify = new QuiltPathify(destination)
        String key = pathify.pkgKey()

        expect:
        observer.shouldPublish(key)
        !observer.addOverlay(pathify, destination, Paths.get('/nonexistent/out.txt'))
        observer.countFailedCopies(key) == 1
        !observer.shouldPublish(key)
        pathify.pkg.getRemoteEntries().isEmpty()

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
        pathify.pkg.getRemoteEntries() == ['run/out.txt': new RemoteEntry("test/${name}/run/out.txt", source.toAbsolutePath())]

        cleanup:
        pathify?.pkg?.reset()
        Files.deleteIfExists(source)
    }

}
