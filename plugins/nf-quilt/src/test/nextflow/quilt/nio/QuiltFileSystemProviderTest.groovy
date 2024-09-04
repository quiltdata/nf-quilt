/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import groovy.transform.CompileDynamic
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.CopyOption
import java.nio.file.StandardCopyOption
import groovy.util.logging.Slf4j
import org.codehaus.groovy.runtime.powerassert.PowerAssertionError

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

    void 'should error asQuiltPath with non-Quilt path'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path path = Paths.get('README.md')
        when:
        provider.asQuiltPath(path)
        then:
        thrown IllegalArgumentException
    }

    // newDirectoryStream returns local path for read
    // newDirectoryStream returns package path for write
    // do we need a new schema for quilt+local?

    void 'should download file from remote to local destination'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        String filename = 'README.md'
        Path remoteFile = QuiltPathFactory.parse('quilt+s3://quilt-example#package=examples%2fhurdat2&path=' + filename)
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        when:
        provider.download(remoteFile, tempFile)

        then:
        Files.exists(tempFile)
        Files.size(tempFile) > 0
    }

    void 'should download folders from remote to local destination'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFolder = QuiltPathFactory.parse('quilt+s3://quilt-example#package=examples%2fhurdat2')
        Path tempFolder = Files.createTempDirectory('quilt')
        CopyOption opt = StandardCopyOption.REPLACE_EXISTING
        when:
        provider.download(remoteFolder, tempFolder, opt)

        then:
        Files.exists(tempFolder)
        Files.list(tempFolder).count() > 0
    }

    void 'should error when copying from remote to local path'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        String filename = 'README.md'
        Path remoteFile = QuiltPathFactory.parse('quilt+s3://quilt-example#package=examples%2fhurdat2&path=' + filename)
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        when:
        provider.copy(remoteFile, tempFile)

        then:
        thrown PowerAssertionError
    }

    void 'should do nothing when copying a path to itself'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = QuiltPathFactory.parse('quilt+s3://quilt-example#package=examples%2fhurdat2&path=README.md')

        when:
        provider.copy(remoteFile, remoteFile)

        then:
        Files.exists(remoteFile.localPath())
    }

}
