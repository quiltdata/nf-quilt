/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import nextflow.quilt.jep.QuiltParser
import java.nio.file.Path
import java.nio.file.Paths
import groovy.transform.CompileDynamic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileDynamic
class QuiltFileSystemTest extends QuiltSpecification {

    void 'should test getPath'() {
        given:
        String BUCKET_NAME = 'bucket'
        QuiltFileSystemProvider provider = Stub(QuiltFileSystemProvider)
        and:
        QuiltFileSystem fs = new QuiltFileSystem(BUCKET_NAME, provider)

        when:
        Path fs_path = fs.getPath(path)
        String url = QuiltParser.PREFIX + path
        Path url_path = Paths.get(new URI(url))

        then:
        fs_path
        url_path
        fs_path.toString() == path

        where:
        call | path
        1   | 'file-name.txt'
        1   | 'bucket#package=alpha%2fbravo'
    }

    void 'should test basic properties'() {
        given:
        String BUCKET_NAME = 'bucket'
        QuiltFileSystemProvider provider = Stub(QuiltFileSystemProvider)

        when:
        QuiltFileSystem fs = new QuiltFileSystem(BUCKET_NAME, provider)
        then:
        fs.getSeparator() == '/'
        fs.isOpen()
        fs.provider() == provider
        !fs.isReadOnly()
        fs.supportedFileAttributeViews() == ['basic'] as Set
    }

}
