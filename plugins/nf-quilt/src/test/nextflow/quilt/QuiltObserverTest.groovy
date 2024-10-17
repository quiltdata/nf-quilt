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

    void 'should set metadata from config'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        Map<String, Map<String, Object>> config = [quilt: [metadata: [key: 'value']]]
        expect:
        observer.configMetadata.size() == 0
        observer.checkConfig(config)
        observer.configMetadata.size() == 1
    }

    void 'should not error on onFlowComplete success'() {
        given:
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        QuiltObserver observer = new QuiltObserver()
        QuiltPath qPath = QuiltPathFactory.parse(quilt_uri)
        observer.onFlowCreate(mockSession(false))
        observer.onFilePublish(qPath.localPath())
        when:
        observer.onFlowComplete()
        then:
        true
    }

}
