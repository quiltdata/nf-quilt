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
import spock.lang.Ignore

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

    void 'normalized Paths from params, and match'() {
        given:
        String subURL = fullURL.replace('?key=val&key2=val2', '')
        String noURL = fullURL.replace('bkt', 'bucket')
        String newURL = fullURL.replace('bkt', 'new-bucket')
        Session session = Stub(Session)
        session.getParams() >> [subdir: subURL, outdir: fullURL, nodir: noURL]
        QuiltObserver observer = new QuiltObserver()

        when:
        observer.onFlowCreate(session)
        String n_bkt = observer.uniqueURIs['bkt/pre/suf']
        String n_bucket = observer.uniqueURIs['bucket/pre/suf']
        String n_new = QuiltObserver.pathless(newURL).replace('pre/suf', 'pre%2fsuf')

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
        Path newPath = QuiltPathFactory.parse(newURL)

        then:
        observer.checkPath(fullPath) == n_bkt
        observer.checkPath(subPath) == n_bkt
        observer.checkPath(noPath) == n_bucket
        observer.checkPath(newPath) == n_new
    }

    // FIXME: Should infer package name from pubdir/outdir parameters, not just each file's path
    void 'should extract package URI from output path'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        Path path = Paths.get(filepath)
        String uri = observer.extractPackageURI(path)
        expect:
        uri == quilt_uri
        where:
        filepath | quilt_uri
        '/bucket/prefix/suffix/folder/file.ext' | 'quilt+s3://bucket#package=prefix%2fsuffix&path=folder/file.ext'
        '/bucket/prefix/suffix/file.ext' | 'quilt+s3://bucket#package=prefix%2fsuffix&path=file.ext'
        '/bucket/prefix/file.ext' | 'quilt+s3://bucket#package=prefix%2fdefault_suffix&path=file.ext'
        '/bucket/file.ext' | 'quilt+s3://bucket#package=default_prefix%2fdefault_suffix&path=file.ext'
    }

    void 'should recover URI from onFilePublish QuiltPath'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        Path path = Paths.get(key)
        Path quiltPath = QuiltPathFactory.parse(quilt_uri)
        observer.onFilePublish(quiltPath, path)
        String pkgKey = observer.pkgKey(quiltPath)
        expect:
        pkgKey == key
        observer.uniqueURIs[key] == quilt_uri
        observer.publishedURIs[key] == quilt_uri
        where:
        key | quilt_uri
        'bucket/prefix/suffix' | 'quilt+s3://bucket#package=prefix%2fsuffix'
    }

    @Ignore('FIXME: handle onFilePublish with local Path')
    void 'should extract URI from onFilePublish local Path'() {
        given:
        QuiltObserver observer = new QuiltObserver()
        Path quiltPath = QuiltPathFactory.parse(quilt_uri)
        Path path = Paths.get('/'+key)
        observer.onFilePublish(path, quiltPath)
        String pkgKey = observer.pkgKey(quiltPath)
        expect:
        pkgKey == key
        observer.uniqueURIs[key] == quilt_uri
        observer.publishedURIs[key] == quilt_uri
        where:
        key | quilt_uri
        'bucket/prefix/suffix' | 'quilt+s3://bucket#package=prefix%2fsuffix'
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
        }
        observer.onFlowCreate(session)
        observer.onFilePublish(qPath, qPath)
        when:
        observer.onFlowComplete()
        then:
        true
    }

}
