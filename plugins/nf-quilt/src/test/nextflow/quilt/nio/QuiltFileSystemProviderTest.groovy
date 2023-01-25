package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification


import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltFileSystemProviderTest extends QuiltSpecification {

    def 'should return Quilt storage scheme'() {
        given:
        def provider = new QuiltFileSystemProvider()
        expect:
        provider.getScheme() == 'quilt+s3'
    }

    // newDirectoryStream returns local path for read
    // newDirectoryStream returns package path for write
    // do we need a new schema for quilt+local?

}
