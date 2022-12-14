package nextflow.quilt.jep
import nextflow.quilt.QuiltSpecification

import spock.lang.Unroll
import spock.lang.Ignore
import groovy.util.logging.Slf4j

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
class QuiltParserTest extends QuiltSpecification {

    static String path_url = 'quilt+s3://bucket-name#package=quilt/test@abc1&path=sub%2Fpath'
    static String rel_url = 'quilt+s3://bucket-name#package=quilt/test@abc1&path=sub%2F..%2Fpath'
    static String tag_url = 'quilt+s3://bucket-name#package=quilt/test:later&path=sub%2Fpath'
    static String hash_url = 'quilt+s3://quilt-ernest-staging#package=test/hurdat@e4bed47503f9dde90a00b915ef75bd1ad294378870ba2e388084266e4f7ed909'
    static String test_url = 'quilt+s3://quilt-ernest-staging#package=nf-quilt/sarek/pipeline_info/execution_trace_2022-10-13_01-01-31.txt'

    def 'should host Quilt URL scheme'() {
        expect:
        QuiltParser.SCHEME == 'quilt+s3'
        QuiltParser.PREFIX == 'quilt+s3://'
    }

    def 'should error on invalid schema'() {
        when:
        def parser = QuiltParser.ForUriString("quilt3://bucket/")
        then:
        thrown(IllegalArgumentException)
    }

    def 'should parse over-long packages into path'() {
        when:
        def parser = QuiltParser.ForUriString(test_url)
        then:
        parser.bucket() == "quilt-ernest-staging"
        parser.pkg_name() == "nf-quilt/sarek"
        parser.path() == "pipeline_info/execution_trace_2022-10-13_01-01-31.txt"
      }

    def 'should modify path segments appropriately'() {
        when:
        def parser = QuiltParser.ForUriString(rel_url)
        then:
        parser.path() == "sub/../path"
        parser.appendPath("child").path() == "sub/../path/child"
        parser.normalized().path() == "path"
        def p1 = parser.dropPath()
        p1.path() == "sub/.."
        def p2 = p1.dropPath()
        p2.path() == "sub"
        def p3 = p2.dropPath()
        p3.path() == ""
        def p4 = p3.dropPath()
        p4.path() == ""
    }

    def 'should decompose URIs'() {
        when:
        def parser = QuiltParser.ForBarePath(bare)
        then:
        parser.bucket() == bucket
        parser.pkg_name() == pkg
        parser.path() == path
        parser.hash() == hash
        parser.tag() == tag

        where:
        bare                                 | bucket   | query                    | pkg    | path | hash     | tag
        'bucket'                             | 'bucket' | null                     | null   | "" | null   | null
        'BuCKet'                             | 'bucket' | null                     | null   | "" | null   | null
        'b#frag'                             | 'b'      | 'frag'                   | null   | "" | null   | null
        'B#package=q%2Fp'                    | 'b'      | 'package=q/p'            | 'q/p'  | "" | null   | null
        'B#package=q%2Fp@hash'               | 'b'      | 'package=q/p@hash'        | 'q/p' | "" | 'hash' | null
        'B#package=q%2Fp:tag&path=a%2Fb'     | 'b'      | 'package=q/p:tag&path=a/b'| 'q/p' | 'a/b'| null | 'tag'
    }

}
