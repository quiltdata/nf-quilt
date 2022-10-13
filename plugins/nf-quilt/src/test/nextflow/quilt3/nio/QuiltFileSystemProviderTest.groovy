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

    def 'should return a blob path' () {
        given:
        def fs = Mock(QuiltFileSystem)
        fs.getBucketName() >> 'bucket'
        def provider = Spy(QuiltFileSystemProvider)

        when:
        def path = provider.getPath(new URI(uri))
        then:
        1 * provider.getFileSystem0(_, true) >> fs
        path == new QuiltPath(fs, expected)

        where:
        uri                                 | expected
        'quilt+s3://bucket'                   | '/bucket/'
        'quilt+s3://bucket/'                  | '/bucket/'
        'quilt+s3://bucket/this/and/that'     | '/bucket/this/and/that'
        'quilt+s3://bucket/this/and/that/'    | '/bucket/this/and/that/'

    }

    def 'should get a storage path' () {
        given:
        def fs = Mock(QuiltFileSystem)
        fs.getBucketName() >> bucket
        def provider = Spy(QuiltFileSystemProvider)

        when:
        def path = provider.getPath(objectName)
        then:
        1 * provider.getFileSystem0(bucket, true) >> fs
        path == new QuiltPath(fs, expected)

        where:
        bucket              | objectName            | expected
        'bucket'            | 'bucket'              | '/bucket/'
        'bucket'            | 'bucket/'             | '/bucket/'
        'bucket'            | 'bucket/a/b'          | '/bucket/a/b'
        'bucket'            | 'bucket/a/b/'         | '/bucket/a/b/'
    }

    def 'should return the bucket given a URI'() {
        given:
        def provider = new QuiltFileSystemProvider()

        expect:
        provider.getBucketName(new URI('quilt+s3://bucket/alpha/bravo')) == 'bucket'
        provider.getBucketName(new URI('quilt+s3://BUCKET/alpha/bravo')) == 'bucket'

        when:
        provider.getBucketName(new URI('s3://xxx'))
        then:
        thrown(IllegalArgumentException)

        when:
        provider.getBucketName(new URI('quilt+s3:/alpha/bravo'))
        then:
        thrown(IllegalArgumentException)

        when:
        provider.getBucketName(new URI('/alpha/bravo'))
        then:
        thrown(IllegalArgumentException)
    }
}
