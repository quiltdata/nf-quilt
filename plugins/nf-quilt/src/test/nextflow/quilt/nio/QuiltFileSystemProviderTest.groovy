/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import groovy.transform.CompileDynamic
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import groovy.util.logging.Slf4j
import spock.lang.IgnoreIf

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
@Slf4j
class QuiltFileSystemProviderTest extends QuiltSpecification {

    void 'should return Quilt storage scheme'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        expect:
        provider.getScheme() == 'quilt+s3'
    }

    // newDirectoryStream returns local path for read
    // newDirectoryStream returns package path for write
    // do we need a new schema for quilt+local?

    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should download file from remote to local destination'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        String filename = 'README.md'
        Path remoteFile = Paths.get('quilt+s3://quilt-example#package=examples%2fhurdat2&path=' + filename)
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        when:
        provider.download(remoteFile, tempFile)

        then:
        Files.exists(tempFile)
        Files.size(tempFile) > 0
    }

}
