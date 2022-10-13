package nextflow.quilt3.jep
import nextflow.quilt3.QuiltSpecification

import spock.lang.Unroll
import spock.lang.Ignore
import groovy.util.logging.Slf4j

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
class QuiltIDTest extends QuiltSpecification {
    def 'should null on missing bucket'() {
        when:
        def id = QuiltID.Fetch(null, "pkg/name")
        then:
        null == id
    }

    def 'should default on missing pgk_suffix'() {
        when:
        def id = QuiltID.Fetch("bucket", "pkg")
        then:
        id.toString() == "default.pkg.bucket"
    }

    def 'should default on missing pgk_name'() {
        when:
        def id = QuiltID.Fetch("bucket", null)
        then:
        id.toString() == "default.null.bucket"
    }

    def 'should decompose pkg names'() {
        when:
        def id = QuiltID.Fetch("bucket", "pkg/name")
        then:
        id.toString() == "name.pkg.bucket"
    }
}
