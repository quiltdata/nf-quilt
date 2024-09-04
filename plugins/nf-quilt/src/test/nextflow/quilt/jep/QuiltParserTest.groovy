/* groovylint-disable MethodName */
package nextflow.quilt.jep

import nextflow.quilt.QuiltSpecification
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class QuiltParserTest extends QuiltSpecification {

    private static final String REL_URL = 'quilt+s3://bucket-name#package=quilt/test@abc1&path=sub%2F..%2Fpath'
    private static final String TEST_URL =
        'quilt+s3://quilt-ernest-staging#package=nf-quilt/sarek/pipeline_info/execution_trace_2022-10-13_01-01-31.txt'

    void 'should host Quilt URL scheme'() {
        expect:
        QuiltParser.SCHEME == 'quilt+s3'
        QuiltParser.PREFIX == 'quilt+s3://'
    }

    void 'should error on invalid schema'() {
        when:
        QuiltParser.forUriString('quilt3://bucket/')
        then:
        thrown(IllegalArgumentException)
    }

    void 'should parse over-long packages into path'() {
        when:
        QuiltParser parser = QuiltParser.forUriString(TEST_URL)
        then:
        parser.getBucket() == 'quilt-ernest-staging'
        parser.getPackageName() == 'nf-quilt/sarek'
        parser.getPath() == 'pipeline_info/execution_trace_2022-10-13_01-01-31.txt'
    }

    void 'should modify path segments appropriately'() {
        when:
        QuiltParser parser = QuiltParser.forUriString(REL_URL)
        then:
        parser.getPath() == 'sub/../path'
        parser.appendPath('child').getPath() == 'sub/../path/child'
        parser.normalized().getPath() == 'path'
        QuiltParser p1 = parser.dropPath()
        p1.getPath() == 'sub/..'
        QuiltParser p2 = p1.dropPath()
        p2.getPath() == 'sub'
        QuiltParser p3 = p2.dropPath()
        p3.getPath() == ''
        QuiltParser p4 = p3.dropPath()
        p4.getPath() == ''
    }

    void 'should decompose URIs'() {
        when:
        QuiltParser parser = QuiltParser.forBarePath(bare)
        then:
        parser.getBucket() == bucket
        parser.getPackageName() == pkg
        parser.getPath() == path
        parser.getHash() == hash
        parser.getTag() == tag

        where:
        bare                              | bucket   | query                      | pkg   | path   | hash   | tag
        'bucket'                          | 'bucket' | null                       | null  | ''     | null   | null
        'BuCKet'                          | 'bucket' | null                       | null  | ''     | null   | null
        'b#frag'                          | 'b'      | 'frag'                     | null  | ''     | null   | null
        'B#package=q%2Fp'                 | 'b'      | 'package=q/p'              | 'q/p' | ''     | null   | null
        'B#package=q%2Fp@hash'            | 'b'      | 'package=q/p@hash'         | 'q/p' | ''     | 'hash' | null
        'B#package=q%2Fp:tag&path=a%2Fb'  | 'b'      | 'package=q/p:tag&path=a/b' | 'q/p' | 'a/b'  | null   | 'tag'
    }

    void 'should parse package into components'() {
        when:
        QuiltParser parser = QuiltParser.forUriString(TEST_URL)
        // log.info("QuiltParserTest[parse package] ${parser.class.getDeclaredMethods()}")
        then:
        parser
        when:
        String result = parser.parsePkg(packageID)
        then:
        result == pkg
        parser.getHash() == hash
        parser.getTag() == tag

        where:
        packageID   | pkg     | hash   | tag
        null        | null    | null   | null
        'a/b'       | 'a/b'   | null   | null
        'a/b@hash'  | 'a/b'   | 'hash' | null
        'a/b:tag'   | 'a/b'   | null   | 'tag'
    }

    void 'should extract other parameters from URI'() {
        when:
        QuiltParser parser = QuiltParser.forUriString(testURI)
        Map<String,Object> meta = parser.getMetadata()

        then:
        parser.getBucket() == 'bkt'
        parser.getPackageName() == 'pre/suf'
        parser.getPath() == 'p/t'
        parser.getPropertyName() == 'prop'
        parser.getWorkflowName() == 'wf'
        parser.getCatalogName() == 'quiltdata.com'
        meta
        meta['key'] == 'val'
        meta['key2'] == 'val2'
    }

    void 'should unparse other parameters back to URI'() {
        when:
        QuiltParser parser = QuiltParser.forUriString(testURI)
        String unparsed = parser.toUriString()

        then:
        unparsed.replace('%2f', '/') == testURI
    }

    void 'should collect array parameters from query string'() {
        when:
        String query = 'key=val1,val2&quay=vale1&quay=vale2'
        Map<String,Object> result = QuiltParser.parseQuery(query)
        println "QuiltParserTest[$query] -> ${result}"
        String unparsed = QuiltParser.unparseQuery(result)

        then:
        result['key'] == 'val1,val2'
        result['quay'] == ['vale1', 'vale2']
        unparsed == query.replace(',', '%2C')
    }

}
