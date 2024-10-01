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

// import nextflow.quilt.jep.QuiltPackage
//import spock.lang.Ignore
import nextflow.quilt.jep.QuiltParser

// import java.nio.file.Path
// import java.nio.file.Paths
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

    void 'test quiltURIfromPath'() {
        expect:
        assert QuiltPathExtractor.fromString(s3path) == expected

        where:
        s3path                      | expected
        '/bkt/pre/suf/fold/FILE.md' | 'quilt+s3://bkt#package=pre%2fsuf&path=fold/FILE.md'
        '/bkt/pre/suf/FILE.md'      | 'quilt+s3://bkt#package=pre%2fsuf&path=/'
        '/bkt/pre/FILE.md'          | "quilt+s3://bkt#package=pre%2f${DS}&path=fold/FILE.md"
        '/bkt/FILE.md'              | "quilt+s3://bkt#package=${DP}%2f${DS}&path=fold/FILE.md"
        '/FILE.md'                  | "quilt+s3://${DB}#package=${DP}%2f${DS}&path=FILE.md"
        '/'                         | "quilt+s3://${DB}#package=${DP}%2f${DS}&path=/"
        ''                          | "quilt+s3://${DB}#package=${DP}%2f${DS}&path=/"
    }

}
