package nextflow.quilt.nio
import nextflow.quilt.QuiltSpecification

import java.nio.file.FileSystemAlreadyExistsException

import spock.lang.Specification
import spock.lang.Unroll
import spock.lang.Ignore

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
class QuiltFileSystemProviderTest extends QuiltSpecification {

    def 'should return Quilt storage scheme'() {
        given:
        def provider = new QuiltFileSystemProvider()
        expect:
        provider.getScheme() == 'quilt+s3'
    }
}
