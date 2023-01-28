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

    void 'should fetch by bucket and package'() {
        when:
        QuiltID id = QuiltID.fetch(bucket, pkg)
        String ids = id?.toString()
        then:
        ids == result

        where:
        bucket   | pkg        | result
        'bucket' | 'pkg/name' | 'bucket.pkg.name'
        'bucket' | 'pkg'      | 'bucket.pkg.default'
        'bucket' | 'pkg/'     | 'bucket.pkg.default'
        'bucket' | '/'        | 'bucket.null.default'
        'bucket' | 'p'        | 'bucket.null.default'
        'bucket' | '/name'    | 'bucket..name'
        null     | 'pkg/name' | 'null'
        'bucket' | null       | 'bucket.null.default'
    }

}
