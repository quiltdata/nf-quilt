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

import java.nio.file.Path
import java.nio.file.Paths
import nextflow.Global
import nextflow.Session
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltPathSerializerTest extends QuiltSpecification {

    static String url = 'quilt+s3://bucket#package=pkg%2fname&path=sample.csv'

    void 'should serialize a Quilt path'() {
        given:
        Global.session = Mock(Session) {
            getConfig() >> [quilt:[project:'foo', region:'x']]
        }

        when:
        URI uri = URI.create(url)
        Path path  = Paths.get(uri)
        then:
        path in QuiltPath
        path.toUri() == uri
        path.toUriString() == url
    }

}
