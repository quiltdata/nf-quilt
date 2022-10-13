package nextflow.quilt.nio
import nextflow.quilt.QuiltSpecification
import nextflow.quilt.jep.QuiltParser

import java.nio.file.Paths

import spock.lang.Ignore

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class QuiltFileSystemTest extends QuiltSpecification {

    def 'should test getPath' () {
        given:
        def BUCKET_NAME = 'bucket'
        def provider = Stub(QuiltFileSystemProvider)
        and:
        def fs = new QuiltFileSystem(BUCKET_NAME, provider)

        when:
        def fs_path = fs.getPath(path)
        def url = QuiltParser.PREFIX + fullpath
        def url_path = Paths.get(new URI(url))

        then:
        fs_path
        url_path
        fs_path.toString() == url_path.toString()

        where:
        call| path                  | fullpath
        1   | 'bucket#package=alpha/bravo' | 'bucket/alpha/bravo'
        1   | 'file-name.txt'       | 'file-name.txt'
    }

    def 'should test basic properties' () {

        given:
        def BUCKET_NAME = 'bucket'
        def provider = Stub(QuiltFileSystemProvider)

        when:
        def fs = new QuiltFileSystem(BUCKET_NAME, provider)
        then:
        fs.getSeparator() == '/'
        fs.isOpen()
        fs.provider() == provider
        !fs.isReadOnly()
        fs.supportedFileAttributeViews() == ['basic'] as Set
    }

}
