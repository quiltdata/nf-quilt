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
import nextflow.quilt.QuiltProduct

import java.nio.file.Path
import java.nio.file.Paths
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltObserverTest extends QuiltSpecification {

    void 'should generate solid string for timestamp'() {
        when:
        def now = QuiltProduct.now()
        then:
        now
        now.contains('T')
    }

    void 'should extract Quilt path from appropriate UNIX Path'() {
        given:
        Path pkg = Paths.get('/var/tmp/output/quilt-example#package=examples%2fhurdat')
        Path unpkg = Paths.get('/var/tmp/output/quilt-example/examples/hurdat')
        expect:
        QuiltObserver.asQuiltPath(unpkg) == null
        QuiltObserver.asQuiltPath(pkg).toString() == 'quilt-example#package=examples%2fhurdat'
    }

    void 'normalize path to original params if present'() {
        given:
        Map params = [outdir: fullURL]
        String subURL = fullURL.replace('?key=val&key2=val2', '')
        QuiltPath path = QuiltPathFactory.parse(subURL)
        QuiltPath path_norm = QuiltObserver.normalizePath(path, params)
        String noURL = subURL.replace('bkt', 'bucket')
        QuiltPath no_path = QuiltPathFactory.parse(noURL)
        QuiltPath no_path_norm = QuiltObserver.normalizePath(no_path, params)
        expect:
        path_norm
        "$path_norm" != "$path"
        path_norm.file_key() == ''
        no_path_norm
        "$no_path_norm" == "$no_path"
    }

}
