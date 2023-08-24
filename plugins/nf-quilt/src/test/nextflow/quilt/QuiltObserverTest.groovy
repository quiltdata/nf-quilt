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

    void 'should extract Quilt path from appropriate UNIX Path'() {
        given:
        Path pkg = Paths.get('/var/tmp/output/quilt-example#package=examples%2fhurdat')
        Path unpkg = Paths.get('/var/tmp/output/quilt-example/examples/hurdat')
        expect:
        QuiltObserver.asQuiltPath(unpkg) == null
        QuiltObserver.asQuiltPath(pkg).toString() == 'quilt-example#package=examples%2fhurdat'
    }

    void 'normalizedPaths from params, for matching'() {
        given:
        String subURL = fullURL.replace('?key=val&key2=val2', '')
        String noURL = fullURL.replace('bkt', 'bucket')
        Session session = Stub(Session)
        session.getParams() >> [subdir: subURL, outdir: fullURL, nodir: noURL]
        QuiltObserver observer = new QuiltObserver()

        when:
        observer.onFlowCreate(session)
        String n_bkt = observer.packageURIs['bkt/pre/suf']
        String n_bucket = observer.packageURIs['bucket/pre/suf']

        then:
        observer
        n_bkt != null
        n_bucket != null
        fullURL.contains('&path=')
        !n_bkt.contains('&path=')
        !n_bucket.contains('&path=')
        n_bkt.split('#')[0] == 'quilt+s3://bkt?key=val&key2=val2'
        n_bkt.contains('quilt+s3://bkt?key=val&key2=val2')
        n_bucket.contains('quilt+s3://bucket?key=val&key2=val2')

        when:
        Path fullPath = QuiltPathFactory.parse(fullURL)
        Path subPath = QuiltPathFactory.parse(subURL)
        Path noPath = QuiltPathFactory.parse(noURL)
        Path bktPath = QuiltPathFactory.parse(n_bkt)
        Path bucketPath = QuiltPathFactory.parse(n_bucket)

        then:
        observer.matchPath(fullPath) == bktPath
        observer.matchPath(subPath) == bktPath
        observer.matchPath(noPath) == bucketPath
    }

}
