/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import groovy.transform.CompileDynamic
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.CopyOption
import java.nio.file.StandardCopyOption
import java.nio.file.DirectoryStream
import groovy.util.logging.Slf4j

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
@Slf4j
class QuiltFileSystemProviderTest extends QuiltSpecification {

    static Path parsedURIWithPath(boolean withPath = false) {
        String packageURI = SpecURI()
        if (withPath) {
            packageURI += '&path=COPY_THIS.md'
        }
        return QuiltPathFactory.parse(packageURI)
    }

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

    void 'should canUpload and canDownload based on local and remote paths'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path localPath = Paths.get('README.md')
        Path remotePath = parsedURIWithPath(true)

        expect:
        provider.canUpload(localPath, remotePath)
        provider.canDownload(remotePath, localPath)
        !provider.canUpload(remotePath, localPath)
        !provider.canDownload(localPath, remotePath)
    }

    // newDirectoryStream returns local path for read
    // newDirectoryStream returns package path for write
    // do we need a new schema for quilt+local?

    void 'should download file from remote to local destination'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = parsedURIWithPath(true)
        String filename = remoteFile.getFileName()
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        expect:
        !Files.exists(tempFile)

        when:
        provider.download(remoteFile, tempFile)

        then:
        Files.exists(tempFile)
        Files.size(tempFile) > 0
    }

    void 'should download folders from remote to local destination'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFolder = parsedURIWithPath(false)
        Path tempFolder = Files.createTempDirectory('quilt')
        CopyOption opt = StandardCopyOption.REPLACE_EXISTING
        when:
        provider.download(remoteFolder, tempFolder, opt)

        then:
        Files.exists(tempFolder)
        Files.list(tempFolder).count() > 0
    }

    void 'should fail to upload a file to itself'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = parsedURIWithPath(true)

        when:
        provider.upload(remoteFile, remoteFile)

        then:
        thrown java.nio.file.FileAlreadyExistsException
    }

    void 'should throw error when checkRoot is root'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = Paths.get('/')

        when:
        provider.checkRoot(remoteFile)

        then:
        thrown UnsupportedOperationException
    }

    void 'should return DirectoryStream for emptyStream'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        DirectoryStream<Path> stream = provider.emptyStream()

        expect:
        stream != null
        stream.iterator().hasNext() == false
    }

    void 'should error when copying from remote to local path'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = parsedURIWithPath(true)
        String filename = remoteFile.getFileName()
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        when:
        provider.copy(remoteFile, tempFile)

        then:
        thrown org.codehaus.groovy.runtime.powerassert.PowerAssertionError
    }

    void 'should do nothing when copying a path to itself'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = parsedURIWithPath(true)

        when:
        provider.copy(remoteFile, remoteFile)

        then:
        Files.exists(remoteFile.localPath())
    }

    void 'should error when moving from remote to local path'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        Path remoteFile = parsedURIWithPath(true)
        String filename = remoteFile.getFileName()
        Path tempFolder = Files.createTempDirectory('quilt')
        Path tempFile = tempFolder.resolve(filename)

        when:
        provider.move(remoteFile, tempFile)

        then:
        thrown org.codehaus.groovy.runtime.powerassert.PowerAssertionError
    }

    void 'should recognize when path isHidden'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()

        expect:
        provider.isHidden(Paths.get(remoteFile)) == isHidden

        where:
        remoteFile | isHidden
        'foo'  | false
        '.foo' | true
    }

    void 'should throw error on getFileStore'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()

        when:
        provider.getFileStore(Paths.get('foo'))

        then:
        thrown UnsupportedOperationException
    }

    void 'should throw error on getFileAttributeView with unknown type'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()

        when:
        provider.getFileAttributeView(Paths.get('foo'), DirectoryStream)

        then:
        thrown UnsupportedOperationException
    }

    void 'should throw error on readAttributes with unknown type'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()
        QuiltPath qPath = parsedURIWithPath(true)

        when:
        provider.readAttributes(qPath, DirectoryStream)

        then:
        thrown UnsupportedOperationException
    }

    void 'should throw error on readAttributes with String'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()

        when:
        provider.readAttributes(Paths.get('foo'), 'basic:isDirectory')

        then:
        thrown UnsupportedOperationException
    }

    void 'should throw error on setAttributes'() {
        given:
        QuiltFileSystemProvider provider = new QuiltFileSystemProvider()

        when:
        provider.setAttribute(Paths.get('foo'), 'basic:isDirectory', provider)

        then:
        thrown UnsupportedOperationException
    }

}
