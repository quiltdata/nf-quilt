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
 * Test class for QuiltPathExtractor
 *
 * Author: Ernest Prabhakar <ernest@quiltdata.io>
 */

@CompileDynamic
class QuiltPathExtractorTest extends QuiltSpecification {

    final static private String DP = 'default_prefix'
    final static private String DS = 'default_suffix'
    final static private String DB = QuiltParser.NULL_BUCKET

    QuiltPathExtractor extracted() {
        String uri = SpecURI()
        QuiltPath path = QuiltPathFactory.parse(uri)
        QuiltPathExtractor extract = new QuiltPathExtractor(path)
        return extract
    }

    void 'test uriFromS3File'() {
        expect:
        def quilt_uri = QuiltPathExtractor.uriFromS3File(s3path)
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
        QuiltPathExtractor extract = new QuiltPathExtractor(path)

        then:
        extract.isOverlay == false
        extract.uri == uri
        extract.path == path
        extracted().uri == uri
    }

    void 'test boolean findQuiltPath'() {
        when:
        QuiltPathExtractor extract = extracted()

        then:
        rc == extract.findQuiltPath(path)

        where:
        rc    | path
        false | 'FILE.md'
        true  | 'bucket#package=prefix%2fsuffix&path=FILE.md'
    }

    // Test findQuiltPath updates uri/path/pkg
    void 'test settors findQuiltPath'() {
        when:
        QuiltPathExtractor extract = extracted()
        extract.findQuiltPath('bucket#package=prefix%2fsuffix&path=.%2fFILE.md')

        then:
        extract.isOverlay == false
        extract.uri == 'quilt+s3://bucket#package=prefix%2fsuffix&path=FILE.md'
        extract.path.toString() == 'bucket#package=prefix%2fsuffix&path=.%2fFILE.md'
        extract.pkg.toUriString() == 'quilt+s3://bucket#package=prefix%2fsuffix&path=FILE.md'
        extract.pkgKey() == 'bucket#package=prefix%2fsuffix'
    }

    // Test findQuiltPath retrieves existing metadata
    // Test makeQuiltPath creates new uri/path/pkg
    // Test makeQuiltPath sets isOverlay
    // Test copyToPackage copies overly file to package folder

}
