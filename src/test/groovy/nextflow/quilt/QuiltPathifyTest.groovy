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

import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import spock.lang.IgnoreIf
import groovy.transform.CompileDynamic

/**
 * Test class for QuiltPathify
 *
 * Author: Ernest Prabhakar <ernest@quiltdata.io>
 */

@CompileDynamic
class QuiltPathifyTest extends QuiltSpecification {

    final static private String DP = 'default_prefix'
    final static private String DS = 'default_suffix'
    final static private String DB = QuiltParser.NULL_BUCKET

    QuiltPathify getPathify(String uri = SpecURI()) {
        QuiltPath path = QuiltPathFactory.parse(uri)
        QuiltPathify pathify = new QuiltPathify(path)
        return pathify
    }

    void 'test uriFromS3File'() {
        expect:
        def quilt_uri = QuiltPathify.uriFromS3File(s3path)
        quilt_uri == expected

        where:
        s3path                      | expected
        '/bkt/pre/suf/fold/FILE.md' | 'quilt+s3://bkt#package=pre%2fsuf&path=fold/FILE.md'
        '/bkt/pre/suf/FILE.md'      | 'quilt+s3://bkt#package=pre%2fsuf&path=FILE.md'
        '/bkt/pre/FILE.md'          | "quilt+s3://bkt#package=pre%2f${DS}&path=FILE.md"
        '/bkt/FILE.md'              | "quilt+s3://bkt#package=${DP}%2f${DS}&path=FILE.md"
        '/FILE.md'                  | "quilt+s3://${DB}#package=${DP}%2f${DS}&path=FILE.md"
    }

    void 'test with QuiltPath'() {
        when:
        String uri = SpecURI()
        QuiltPath path = QuiltPathFactory.parse(uri)
        QuiltPathify pathify = new QuiltPathify(path)

        then:
        pathify.isOverlay == false
        pathify.uri == uri
        pathify.path == path
        getPathify().uri == uri
    }

    void 'test findQuiltPath returns boolean'() {
        when:
        QuiltPathify pathify = getPathify()

        then:
        rc == pathify.findQuiltPath(path)

        where:
        rc    | path
        false | 'FILE.md'
        true  | 'bucket#package=prefix%2fsuffix&path=FILE.md'
        true  | 'bkt#package=pre%2fsuffix&path=inputs%2fbkt#package=pre%2fsuffix@af541d2%2fdata.tsv'
    }

    void 'test findQuiltPath overrides attributes'() {
        when:
        QuiltPathify pathify = getPathify()
        pathify.findQuiltPath('buck#package=prefix%2fsuffix&path=.%2fFILE.md')

        then:
        pathify.isOverlay == false
        pathify.uri == 'quilt+s3://buck#package=prefix%2fsuffix&path=.%2fFILE.md'
        pathify.path.toString() == 'buck#package=prefix%2fsuffix&path=.%2fFILE.md'
        pathify.pkg.toUriString() == 'quilt+s3://buck#package=prefix%2fsuffix&path=.%2fFILE.md'
    }

    void 'test findQuiltPath preserves metadata'() {
        when:
        String now = QuiltProduct.now()
        String meta = "meta=${now}"
        String uriWith = uniqueQueryURI(meta)
        String uriWithout = uriWith.replace("?$meta", '')
        QuiltPathify pathify1 = getPathify(uriWith)

        then:
        uriWith.contains(meta)
        pathify1.uri == uriWith
        pathify1.pkg.toUriString() == uriWith

        when:
        QuiltPathify pathify2 = getPathify(uriWithout)

        then:
        pathify2.pkgKey() == pathify1.pkgKey()
        pathify2.uri == uriWithout
        pathify2.pkg.toUriString() == uriWith
    }

    // TODO: Test findQuiltPath works for dynamic URIs

    void 'test makeQuiltPath'() {
        when:
        QuiltPathify pathify = getPathify()
        pathify.makeQuiltPath(s3File)

        then:
        pathify.isOverlay == false
        pathify.uri == "quilt+s3://${uri}"
        pathify.path.toString() == uri
        pathify.pkg.toUriString() == "quilt+s3://${uri}"

        where:
        s3File                      | uri
        '/bkt/pre/suf/fold/FILE.md' | 'bkt#package=pre%2fsuf&path=fold%2fFILE.md'
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'test copyToCache copies overlay file to its package sub-path'() {
        given:
        // Publish destination: bucket/ns/name come from the first three segments
        QuiltPathify pathify = new QuiltPathify(Paths.get("/bkt/pre/suf/${sub_path}"))
        Path destDir = pathify.pkg.packageDest()
        Path dest = destDir.resolve(sub_path)
        Path tempFolder = Files.createTempDirectory('src')
        Path source = tempFolder.resolve('source.md')
        Files.writeString(source, 'hello')

        when:
        boolean copied = pathify.copyToCache(source)

        then:
        pathify.isOverlay
        pathify.path.sub_paths() == sub_path
        copied
        Files.readString(dest) == 'hello'

        when: 'copied again (e.g. -resume)'
        Files.writeString(source, 'updated')
        pathify.copyToCache(source)

        then:
        Files.readString(dest) == 'updated'

        cleanup:
        QuiltPackage.deleteDirectory(destDir.resolve(sub_path.split('/')[0]))
        QuiltPackage.deleteDirectory(tempFolder)

        where:
        sub_path << ['FILE.md', 'a_folder/FILE.md', 'a/b/FILE.md']
    }

    @IgnoreIf({ System.getProperty('os.name').toLowerCase().contains('windows') })
    void 'test copyToCache refuses sub-paths that escape the package folder'() {
        given:
        QuiltPathify pathify = new QuiltPathify(Paths.get('/bkt/pre/suf/../../escape/FILE.md'))
        Path source = Files.createTempFile('src', '.md')

        expect:
        !pathify.copyToCache(source)
        !Files.exists(pathify.pkg.packageDest().resolve('../../escape/FILE.md').normalize())

        cleanup:
        Files.deleteIfExists(source)
    }

    void 'test copyToCache ignores non-overlay paths'() {
        expect:
        !new QuiltPathify(QuiltPathFactory.parse('quilt+s3://bkt#package=pre%2fsuf&path=FILE.md'))
            .copyToCache(Paths.get('unused'))
    }

}
