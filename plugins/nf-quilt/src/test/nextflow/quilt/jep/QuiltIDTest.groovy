package nextflow.quilt.jep
import nextflow.quilt.QuiltSpecification

import spock.lang.Unroll
import spock.lang.Ignore
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class QuiltIDTest extends QuiltSpecification {
    def 'should null on missing bucket'() {
        when:
        def id = QuiltID.fetch(null, "pkg/name")
        then:
        null == id
    }

    def 'should default on missing pgk_suffix'() {
        when:
        def id = QuiltID.fetch("bucket", "pkg")
        then:
        id.toString() == "bucket.pkg.default"
    }

    def 'should default on missing pgk_name'() {
        when:
        def id = QuiltID.fetch("bucket", null)
        then:
        id.toString() == "bucket.null.default"
    }

    def 'should decompose pkg names'() {
        when:
        def id = QuiltID.fetch("bucket", "pkg/name")
        then:
        id.toString() == "bucket.pkg.name"
    }
}
