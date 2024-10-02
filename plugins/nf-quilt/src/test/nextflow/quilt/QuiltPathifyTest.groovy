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

import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory

// import java.nio.file.Path
// import java.nio.file.Paths
//import spock.lang.Ignore
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
    }

    // Test findQuiltPath updates uri/path/pkg
    void 'test findQuiltPath overrides attributes'() {
        when:
        QuiltPathify pathify = getPathify()
        println("pathify1: ${pathify.uri}")
        pathify.findQuiltPath('buck#package=prefix%2fsuffix&path=.%2fFILE.md')
        println("pathify2: ${pathify.uri}")

        then:
        pathify.isOverlay == false
        pathify.uri == 'quilt+s3://buck#package=prefix%2fsuffix&path=.%2fFILE.md'
        pathify.path.toString() == 'buck#package=prefix%2fsuffix&path=.%2fFILE.md'
        pathify.pkg.toUriString() == 'quilt+s3://buck#package=prefix%2fsuffix&path=.%2fFILE.md'
        pathify.pkgKey() == 'buck#package=prefix%2fsuffix'
    }

    void 'test findQuiltPath preserves metadata'() {
        when:
        String pathWithout = 'bucket#package=prefix%2fsuffix&path=FILE.md'
        String pathWith = pathWithout.replace('#', '?key=value#')
        String uriWith = "quilt+s3://${pathWith}"
        QuiltPathify pathify = getPathify(uriWith)

        then:
        pathify.uri == uriWith
    }

    // Test findQuiltPath.getRoot() retrieves metadata from prior package
    // Test makeQuiltPath creates new uri/path/pkg
    // Test makeQuiltPath sets isOverlay
    // Test copyToPackage copies overly file to package folder

}
