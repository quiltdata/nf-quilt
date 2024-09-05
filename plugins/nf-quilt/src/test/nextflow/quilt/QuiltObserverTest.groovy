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
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import nextflow.quilt.QuiltObserver
import nextflow.Session

import java.nio.file.Path
import java.nio.file.Paths
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltObserverTest extends QuiltSpecification {

    private static final String SPEC_KEY = 'udp-spec/nf-quilt/source'
    private static final String TEST_KEY = 'bkt/pre/suf'

    QuiltObserver makeObserver(boolean success = false) {
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        QuiltObserver observer = new QuiltObserver()
        Session session = GroovyMock(Session) {
            getParams() >> [outDir: SpecURI(), pubDir: testURI, inDir: quilt_uri]
            isSuccess() >> success
            config >> [quilt: [outputPrefixes: ['pub']]]
        }
        observer.onFlowCreate(session)
        return observer
    }

    void 'should extract appropriate UNIX Path asQuiltPath'() {
        expect:
        String unixFolder = "/var/tmp/output/${pkgString}"
        Path unixPath = Paths.get(unixFolder)
        QuiltObserver.asQuiltPath(unixPath).toString() == pkgString
        where:
        pkgString << ['quilt-example#package=examples%2fhurdat', 'udp-spec#package=nf-quilt%2fsource']
    }

    void 'should form pkgKey from QuiltPath'() {
        given:
        Path testPath = QuiltPathFactory.parse(testURI)
        Path specPath = QuiltPathFactory.parse(SpecURI())
        expect:
        QuiltObserver.pkgKey(testPath) == TEST_KEY
        QuiltObserver.pkgKey(specPath) == SPEC_KEY
    }

    void 'should extract quiltURIfromS3'() {
        expect:
        QuiltObserver.quiltURIfromS3(s3_uri) == quilt_uri
        where:
        s3_uri | quilt_uri
        's3://bucket/prefix/suffix' | 'quilt+s3://bucket#package=prefix%2fsuffix'
    }

    void 'should findOutputParams'() {
        given:
        QuiltObserver observer = makeObserver()
        expect:
        observer.outputURIs
        !observer.outputURIs.containsKey('bucket/prefix/suffix')
        observer.outputURIs.size() == 2

        observer.outputURIs.containsKey(key)
        observer.outputURIs[key] == uri
        observer.confirmPath(QuiltPathFactory.parse(uri))
        where:
        key | uri
        SPEC_KEY | SpecURI()
        TEST_KEY | testURI
    }

    void 'should set outputPrefixes from config'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        Map<String, Map<String, Object>> config = ['quilt': ['outputPrefixes': ['bucket', 'file']]]
        observer.checkConfig(config)
        expect:
        observer.outputPrefixes.size() == 2
        observer.outputPrefixes.contains('bucket')
        observer.outputPrefixes.contains('file')
    }

    void 'should not confirmPath for non-output URIs'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        QuiltPath specPath = QuiltPathFactory.parse(SpecURI())
        QuiltPath testPath = QuiltPathFactory.parse(testURI)
        expect:
        !observer.confirmPath(specPath)
        !observer.confirmPath(testPath)
    }

    void 'should matchPath for compatible S3 paths'() {
        given:
        QuiltObserver observer = makeObserver()
        expect:
        observer.matchPath(key)
        observer.matchPath("/var/tmp/output/$key")
        observer.matchPath("/var/tmp/output/$key/folder/file")
        !observer.matchPath(uri)
        where:
        key | uri
        SPEC_KEY | SpecURI()
        TEST_KEY | testURI
    }

    void 'should not error on onFlowComplete'() {
        given:
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        QuiltObserver observer = new QuiltObserver()
        QuiltPath qPath = QuiltPathFactory.parse(quilt_uri)
        Session session = GroovyMock(Session) {
            // getWorkflowMetadata() >> metadata
            getParams() >> [outdir: quilt_uri]
            isSuccess() >> false
            config >> [:]
        }
        observer.onFlowCreate(session)
        observer.onFilePublish(qPath, qPath)
        when:
        observer.onFlowComplete()
        then:
        true
    }

}
