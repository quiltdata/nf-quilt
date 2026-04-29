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

}
