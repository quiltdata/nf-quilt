/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class S3PathTest extends QuiltSpecification {

    private static final String S3_URL = 's3://quilt-example'
    private static final String S3_PKG = 's3://quilt-example/examples/wellplates'

    void 'should access S3 URIs as Paths'() {
        given:
        URI uri = new URI(S3_URL)
        URI uri2 = new URI(S3_PKG)
        expect:
        uri
        uri2
        when:
        Path path = Paths.get(uri)
        then:
        path
        path.exists()
    }
    
}
