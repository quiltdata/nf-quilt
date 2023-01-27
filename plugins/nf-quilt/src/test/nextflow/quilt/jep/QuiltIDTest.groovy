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
class QuiltIDTest extends QuiltSpecification {

    void 'should null on missing bucket'() {
        when:
        QuiltID id = QuiltID.fetch(null, 'pkg/name')
        then:
        null == id
    }

    void 'should default on missing pgk_suffix'() {
        when:
        QuiltID id = QuiltID.fetch('bucket', 'pkg')
        then:
        id.toString() == 'bucket.pkg.default'
    }

    void 'should default on missing pgk_name'() {
        when:
        QuiltID id = QuiltID.fetch('bucket', null)
        then:
        id.toString() == 'bucket.null.default'
    }

    void 'should decompose pkg names'() {
        when:
        QuiltID id = QuiltID.fetch('bucket', 'pkg/name')
        then:
        id.toString() == 'bucket.pkg.name'
    }

}
