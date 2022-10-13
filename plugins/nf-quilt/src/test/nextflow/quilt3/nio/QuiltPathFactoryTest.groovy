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
import spock.lang.Unroll

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

class QuiltPathFactoryTest extends QuiltSpecification {

    static String pkg_url = 'quilt3://quilt-example/examples/hurdat'
    static String url = pkg_url + '/scripts/build.py?tophash=f8d1478d93&summarize=pattern1&summarize=pattern2&metadata=filename.json'

    @Unroll
    def 'should decompose Quilt URLs' () {
        given:
        def qpath = QuiltPathFactory.Parse(url)
        expect:
        qpath != null
        qpath.bucket() == 'quilt-example'
        qpath.pkg_name() == 'examples/hurdat'
        qpath.file_key() == 'scripts/build.py'
        qpath.option('tophash') == 'f8d1478d93'
        qpath.option('metadata') == 'filename.json'
        qpath.option('summarize') == 'pattern2' // should be a list
    }

    def 'should create quilt path #PATH' () {
        given:
        Global.session = Mock(Session) {
            getConfig() >> [quilt:[project:'foo', region:'x']]
        }

        expect:
        QuiltPathFactory.Parse(PATH).toString() == STR

        where:
        _ | PATH                                        | STR
        _ | 'quilt3://reg/user/pkg/'                    | 'quilt3://reg/user/pkg/'
        _ | 'quilt3://reg/user/pkg'                     | 'quilt3://reg/user/pkg/'
        _ | 'quilt3://reg/pkg/name/opt/file/key'        | 'quilt3://reg/pkg/name/opt/file/key'
        _ | 'quilt3://reg/user/pkg?tophash=hex'         | 'quilt3://reg/user/pkg/'
    }

    def 'should create Channel from URL' () {
        expect:
        def channel = Channel.fromPath(url) // +'/*'
        channel
    }

}
