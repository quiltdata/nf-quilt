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
import nextflow.quilt.jep.QuiltPackage
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

    Session mockSession(boolean success = false) {
        String quilt_uri = 'quilt+s3://bucket#package=prefix%2fsuffix'
        return GroovyMock(Session) {
            getParams() >> [outDir: SpecURI(), pubDir: testURI, inDir: quilt_uri]
            isSuccess() >> success
            config >> [quilt: [outputPrefixes: ['pub']]]
            workDir >> Paths.get('./work')
        }
    }
    QuiltObserver makeObserver(boolean success = false) {
        QuiltObserver observer = new QuiltObserver()
        observer.onFlowCreate(mockSession(success))
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
        QuiltObserver.pkgKey(testPath) == QuiltPackage.osConvert(TEST_KEY)
        QuiltObserver.pkgKey(specPath) == QuiltPackage.osConvert(SPEC_KEY)
    }

    void 'should extract quiltURIfromS3'() {
        expect:
        QuiltObserver.quiltURIfromS3(s3_uri) == quilt_uri
        where:
        s3_uri | quilt_uri
        's3://bucket/prefix/suffix' | 'quilt+s3://bucket#package=prefix%2fsuffix&dest=prefix%2fsuffix'
        's3://bucket/prefix'        | 'quilt+s3://bucket#package=prefix%2fdefault_suffix&dest=prefix'
        's3://bucket'               | 'quilt+s3://bucket#package=default_prefix%2fdefault_suffix&dest=/'
        's3://bucket/folder/prefix/suffix' | 'quilt+s3://bucket#package=prefix%2fsuffix&dest=folder%2fprefix%2fsuffix'
    }

    void 'should return workRelative path for source'() {
        given:
        QuiltObserver observer = makeObserver()
        Path workDir = observer.session.workDir
        println("workDir: $workDir")
        String subPath = 'output/file.txt'
        String workPath = "job/hash/${subPath}"
        Path source = Paths.get(workDir.toString(), workPath)
        println("source: $source")
        expect:
        String relPath = observer.workRelative(source)
        println("relPath: $relPath")
        relPath == QuiltPackage.osConvert(subPath)
        println('done')
    }

    void 'should findOutputParams'() {
        given:
        QuiltObserver observer = makeObserver()
        String targetKey = QuiltPackage.osConvert('bucket/prefix/suffix')
        expect:
        observer.outputURIs
        !observer.outputURIs.containsKey(targetKey)
        observer.outputURIs.size() == 2

        observer.outputURIs.containsKey(key)
        observer.outputURIs[key] == uri
        observer.confirmQuiltPath(QuiltPathFactory.parse(uri))
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

    void 'should not confirmQuiltPath for non-output URIs'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        QuiltPath specPath = QuiltPathFactory.parse(SpecURI())
        QuiltPath testPath = QuiltPathFactory.parse(testURI)
        expect:
        !observer.confirmQuiltPath(specPath)
        !observer.confirmQuiltPath(testPath)
    }

    void 'should return: #rc if canOverlayPath with: #path'() {
        given:
        QuiltObserver observer = makeObserver()
        expect:
        observer.session.workDir.toString() == QuiltPackage.osConvert('./work')
        rc == observer.canOverlayPath(Paths.get(path), Paths.get(path))
        where:
        rc    | path
        true  | SPEC_KEY
        true  | "./work/${SPEC_KEY}"
        true  | "/tmp/${SPEC_KEY}"
        true  | "output/${TEST_KEY}"
        false | 'output/not/a/key'
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
