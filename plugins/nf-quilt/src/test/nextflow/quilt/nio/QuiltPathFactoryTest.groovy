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
import nextflow.Channel
import nextflow.Global
import nextflow.Session
import java.nio.file.Path
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltPathFactoryTest extends QuiltSpecification {

    private final static String PACKAGE_URL = 'quilt+s3://quilt-example#package=examples%2fhurdat@f8d1478d93'
    private final static String TEST_URL = PACKAGE_URL + '&path=scripts%2fbuild.py'

    void 'should decompose Quilt URLs'() {
        given:
        Path qpath = QuiltPathFactory.parse(TEST_URL)
        expect:
        qpath != null
        qpath.getBucket() == 'quilt-example'
        qpath.getPackageName() == 'examples/hurdat'
        qpath.file_key() == 'scripts/build.py'
    }

    void 'should create quilt path #PATH'() {
        given:
        Global.session = Mock(Session) {
            getConfig() >> [quilt:[project:'foo', region:'x']]
        }

        expect:
        QuiltPathFactory.parse(PATH).toString() == STR

        where:
        _ | PATH                                          | STR
        _ | 'quilt+s3://reg#package=user/pkg'             | 'reg#package=user%2fpkg'
        _ | 'quilt+s3:/reg#package=user/pkg&path=test.md' | 'reg#package=user%2fpkg&path=test.md'
    }

    void 'should create Channel from URL'() {
        expect:
        def channel = Channel.fromPath(TEST_URL) // +'/*'
        channel
    }

    void 'should getUriString back from path'() {
        given:
        Path qpath = QuiltPathFactory.parse(TEST_URL)
        expect:
        TEST_URL == QuiltPathFactory.getUriString(qpath)
    }

}
