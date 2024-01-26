/* groovylint-disable ExplicitCallToEqualsMethod, Instanceof */
/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.quilt.nio

import static java.nio.file.StandardOpenOption.APPEND
import static java.nio.file.StandardOpenOption.WRITE

import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.nio.file.FileSystems
import java.nio.file.FileAlreadyExistsException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage
import nextflow.file.FileSystemTransferAware
import nextflow.file.CopyOptions
import nextflow.file.FileHelper

/**
 * Implements NIO File system provider for Quilt Blob Storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
class QuiltFileSystemProvider extends FileSystemProvider implements FileSystemTransferAware {

    private final Map<String,String> myEnv = new HashMap<>(System.getenv())
    private final Map<String,QuiltFileSystem> fileSystems = [:]
    private Map<Path,BasicFileAttributes> attributesCache = [:]

    static QuiltPath asQuiltPath(Path path) {
        if (path in QuiltPath) {
            return (QuiltPath)path
        }
        String pathString = path?.toString() ?: '-'
        if (pathString.startsWith(QuiltParser.SCHEME + ':/')) {
            QuiltPath qPath = QuiltPathFactory.parse(pathString)
            return qPath
        }
        String pathClassName = path?.class?.name ?: '-'
        throw new IllegalArgumentException(
            "Not a valid Quilt blob storage path object: `${path}` [${pathClassName}]"
        )
    }

    static QuiltFileSystem getQuiltFilesystem(Path path) {
        final qPath = asQuiltPath(path)
        final fs = qPath.getFileSystem()
        if (fs !in QuiltFileSystem) {
            String pathClassName = path?.class?.name ?: '-'
            throw new IllegalArgumentException("Not a valid Quilt file system: `$fs` [${pathClassName}]")
        }
        return (QuiltFileSystem)fs
    }

    static FileSystemProvider provider(Path path) {
        return path.getFileSystem().provider()
    }

    /**
     * QuiltFileSystemProvider
     */

    boolean canUpload(Path source, Path target) {
        log.debug "QuiltFileSystemProvider.canUpload: ${source} -> ${target}"
        return FileSystems.getDefault().equals(source.getFileSystem()) && target instanceof QuiltPath
    }

    boolean canDownload(Path source, Path target) {
        log.debug "QuiltFileSystemProvider.canDownload: ${source} -> ${target}"
        return source instanceof QuiltPath && FileSystems.getDefault().equals(target.getFileSystem())
    }

    void download(Path remoteFile, Path localDestination, CopyOption... options) throws IOException {
        log.debug "QuiltFileSystemProvider.download: ${remoteFile} -> ${localDestination}"
        QuiltPath qPath = asQuiltPath(remoteFile)
        Path cachedFile = qPath.localPath()
        log.debug "download.cachedFile: ${cachedFile} [${qPath}]"

        QuiltPackage pkg = qPath.pkg()
        String pathName = pkg.parsed.getPath()

        Path localRoot = localDestination.getParent().getParent().getParent().getParent()
        Path finalDestination = Paths.get(localRoot.toUriString(), 'tmp', pathName)
        log.debug "download.finalDestination: ${finalDestination} [${localRoot}]"
        FileHelper.deletePath(finalDestination) // FIXME: be more intelligent about this

        final CopyOptions opts = CopyOptions.parse(options)
        // delete target if it exists and REPLACE_EXISTING is specified
        if (opts.replaceExisting()) {
            FileHelper.deletePath(localDestination)
        }
        else if (Files.exists(localDestination)) {
            throw new FileAlreadyExistsException(localDestination.toString())
        }

        log.info "download.Files.copy -> ${localDestination}"
        Files.copy(cachedFile, localDestination, options)
        // log.info "download.Files.copy -> ${finalDestination}"
        // Files.copy(localDestination, finalDestination, options)
        log.info 'download.Files copied'
    }

    void upload(Path localFile, Path remoteDestination, CopyOption... options) throws IOException {
        log.debug "QuiltFileSystemProvider.upload: ${localFile} -> ${remoteDestination}"
        final CopyOptions opts = CopyOptions.parse(options)
        // delete target if it exists and REPLACE_EXISTING is specified
        if (opts.replaceExisting()) {
            QuiltPath qPath = asQuiltPath(remoteDestination)
            qPath.deinstall()
        }
        else if (Files.exists(remoteDestination)) {
            throw new FileAlreadyExistsException(remoteDestination.toString())
        }

        QuiltPath qPath = asQuiltPath(remoteDestination)
        Path cachedFile = qPath.localPath()
        if (Files.exists(cachedFile)) {
            throw new FileAlreadyExistsException(remoteDestination.toString())
        }
        Files.copy(localFile, cachedFile, options)
    }

    /**
     * @inheritDoc
     */
    @Override
    String getScheme() {
        return QuiltParser.SCHEME
    }

    String getQuiltIDS(URI uri) {
        assert uri
        QuiltParser parsed = QuiltParser.forURI(uri)
        return parsed.quiltID().toString()
    }

    /**
     * Constructs a new {@code FileSystem} object identified by a URI. This
     * method is invoked by the {@link java.nio.file.FileSystems#newFileSystem(URI,Map)}
     * method to open a new file system identified by a URI.
     *
     * <p> The {@code uri} parameter is an absolute, hierarchical URI, with a
     * scheme equal (without regard to case) to the scheme supported by this
     * provider. The exact form of the URI is highly provider dependent. The
     * {@code env} parameter is a map of provider specific properties to configure
     * the file system.
     *
     * <p> This method throws {@link java.nio.file.FileSystemAlreadyExistsException} if the
     * file system already exists because it was previously created by an
     * invocation of this method. Once a file system is {@link
     * java.nio.file.FileSystem#close closed} it is provider-dependent if the
     * provider allows a new file system to be created with the same URI as a
     * file system it previously created.
     *
     * @param   uri
     *          URI reference
     * @param   config
     *          A map of provider specific properties to configure the file system;
     *          may be empty
     *
     * @return  A new file system
     *
     * @throws  IllegalArgumentException
     *          If the pre-conditions for the {@code uri} parameter aren't met,
     *          or the {@code env} parameter does not contain properties required
     *          by the provider, or a property value is invalid
     * @throws  IOException
     *          An I/O error occurs creating the file system
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission required by the file system provider implementation
     * @throws  java.nio.file.FileSystemAlreadyExistsException
     *          If the file system has already been created
     */
    @Override
    QuiltFileSystem newFileSystem(URI uri, Map<String, ?> config) throws IOException {
        final quiltIDS = getQuiltIDS(uri)
        return newFileSystem(quiltIDS, config)
    }

    /* groovylint-disable-next-line UnusedMethodParameter */
    QuiltFileSystem newFileSystem(String quiltIDS, Map<String, ?> env) throws IOException {
        final fs = new QuiltFileSystem(quiltIDS, this)
        fileSystems[quiltIDS] = fs
        return fs
    }

    /**
     * Returns an existing {@code FileSystem} created by this provider.
     *
     * <p> This method returns a reference to a {@code FileSystem} that was
     * created by invoking the {@link #newFileSystem(URI,Map) newFileSystem(URI,Map)}
     * method. File systems created the {@link #newFileSystem(Path,Map)
     * newFileSystem(Path,Map)} method are not returned by this method.
     * The file system is identified by its {@code URI}. Its exact form
     * is highly provider dependent. In the case of the default provider the URI's
     * path component is {@code "/"} and the authority, query and fragment components
     * are undefined (Undefined components are represented by {@code null}).
     *
     * <p> Once a file system created by this provider is {@link
     * java.nio.file.FileSystem#close closed} it is provider-dependent if this
     * method returns a reference to the closed file system or throws {@link
     * java.nio.file.FileSystemNotFoundException}. If the provider allows a new file system to
     * be created with the same URI as a file system it previously created then
     * this method throws the exception if invoked after the file system is
     * closed (and before a new instance is created by the {@link #newFileSystem
     * newFileSystem} method).
     *
     * @param   uri
     *          URI reference
     *
     * @return  The file system
     *
     * @throws  IllegalArgumentException
     *          If the pre-conditions for the {@code uri} parameter aren't met
     * @throws  java.nio.file.FileSystemNotFoundException
     *          If the file system does not exist
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission.
     */
    @Override
    FileSystem getFileSystem(URI uri) {
        final quiltIDS = getQuiltIDS(uri)
        return getFileSystem(quiltIDS, false)
    }

    QuiltFileSystem getFileSystem(String quiltIDS, boolean canCreate) {
        QuiltFileSystem fs = fileSystems.get(quiltIDS)
        if (fs) { return fs }
        if (canCreate) {
            return newFileSystem(quiltIDS, myEnv)
        }
        throw new FileSystemNotFoundException("Missing Quilt file system for quiltIDS: `$quiltIDS`")
    }

    /**
     * Return a {@code Path} object by converting the given {@link URI}. The
     * resulting {@code Path} is associated with a {@link FileSystem} that
     * already exists or is constructed automatically.
     *
     * <p> The exact form of the URI is file system provider dependent. In the
     * case of the default provider, the URI scheme is {@code "file"} and the
     * given URI has a non-empty path component, and undefined query, and
     * fragment components. The resulting {@code Path} is associated with the
     * default {@link java.nio.file.FileSystems#getDefault default} {@code FileSystem}.
     *
     * @param   uri
     *          The URI to convert
     *
     * @return  The resulting {@code Path}
     *
     * @throws  IllegalArgumentException
     *          If the URI scheme does not identify this provider or other
     *          preconditions on the uri parameter do not hold
     * @throws  java.nio.file.FileSystemNotFoundException
     *          The file system, identified by the URI, does not exist and
     *          cannot be created automatically
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission.
     */
    @Override
    QuiltPath getPath(URI uri) {
        QuiltParser parsed = QuiltParser.forURI(uri)
        log.debug("QuiltFileSystemProvider.getPath`[${uri}]")
        final fs = getFileSystem(parsed.quiltIDS(), true)
        return new QuiltPath(fs, parsed)
    }

    void checkRoot(Path path) {
        if (path.toString() == '/') {
            throw new UnsupportedOperationException("Operation 'checkRoot' not supported on root path")
        }
    }

    /**
    * Open a file for reading or writing.

    * @param path: the path to the file to open or create
    * @param options: options specifying how the file is opened, e.g. StandardOpenOption.WRITE
    * @param attrs: (not supported, values will be ignored)
    * @return
    * @throws IOException
    */
    void notifyFilePublish(QuiltPath destination) { //, Path source=null) {
        final sess = Global.session
        /* groovylint-disable-next-line Instanceof */
        if (sess instanceof Session) {
            sess.notifyFilePublish((Path)destination) //, source)
        }
    }

    @Override
    SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        //log.debug("Creating `newByteChannel`: ${path} <- ${options}")
        final modeWrite = options.contains(WRITE) || options.contains(APPEND)

        final QuiltPath qPath = asQuiltPath(path)
        Path installedPath = qPath.localPath()
        Path parent = installedPath.getParent()
        Files.createDirectories(parent)
        try {
            if (modeWrite) {
                log.debug("\tWriting to: $installedPath")
                //options = [WRITE,CREATE] as Set<OpenOption>
                attributesCache = [:] // reset cache
                notifyFilePublish(qPath)
            }
            //log.debug("\tOpening channel to: $installedPath")
            FileChannel channel = FileChannel.open(installedPath, options)
            return channel
        }
        catch (java.nio.file.NoSuchFileException e) {
            log.error("Failed `FileChannel.open`: ${installedPath} <- ${options}")
        }
      }

    DirectoryStream<Path> emptyStream() throws IOException {
        return new DirectoryStream<Path>() {

            @Override
            Iterator<Path> iterator() {
                return Collections.emptyIterator()
            }

            /* groovylint-disable-next-line CloseWithoutCloseable */
            @Override void close() throws IOException { }

        }
    }

    @Override
    DirectoryStream<Path> newDirectoryStream(Path obj, DirectoryStream.Filter<? super Path> filter) throws IOException {
        final qPath = asQuiltPath(obj)
        //final dirPath = qPath.localPath()
        //Files.newDirectoryStream(dirPath, filter)

        //log.debug("QuiltFileSystemProvider.newDirectoryStream[${qPath.file_key()}]: ${qPath}")

        return new DirectoryStream<Path>() {

            @Override
            Iterator<Path> iterator() {
                return new QuiltPathIterator(qPath, filter)
            }

            /* groovylint-disable-next-line CloseWithoutCloseable */
            @Override void close() throws IOException { }

        }
    }

    /* groovylint-disable BuilderMethodWithSideEffects */
    @Override
    void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        final path = asQuiltPath(dir).localPath()
        //log.debug("Calling createDirectory[${path}]: ${dir} ")
        Files.createDirectories(path)
    }
    /* groovylint-enable BuilderMethodWithSideEffects */

    @Override
    void delete(Path obj) throws IOException {
        checkRoot(obj)
        final path = asQuiltPath(obj)
        getQuiltFilesystem(path).delete(path)
        attributesCache = [:] // reset cache
    }

    @Override
    void copy(Path from, Path to, CopyOption... options) throws IOException {
        //log.debug("Attempting `copy`: ${from} -> ${to}")
        assert provider(from) == provider(to)
        if (from == to) {
            return // nothing to do -- just return
        }

        checkRoot(from); checkRoot(to)
        final source = asQuiltPath(from)
        final target = asQuiltPath(to)
        final fs = getQuiltFilesystem(source)
        fs.copy(source, target)
    }

    @Override
    void move(Path source, Path target, CopyOption... options) throws IOException {
        copy(source, target, options)
        delete(source)
    }

    @Override
    boolean isSameFile(Path path, Path path2) throws IOException {
        return path == path2
    }

    @Override
    boolean isHidden(Path path) throws IOException {
        return path.getFileName()?.toString()?.startsWith('.')
    }

    @Override
    FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("Operation 'getFileStore' is not supported by QuiltFileSystem")
    }

    @Override
    void checkAccess(Path path, AccessMode... modes) throws IOException {
        //log.debug("Calling `checkAccess`: ${path}")
        checkRoot(path)
        QuiltPath qPath = asQuiltPath(path)
        readAttributes(qPath, QuiltFileAttributes)
        if (AccessMode.EXECUTE in modes) {
            throw new AccessDeniedException(qPath.toUriString(), null, 'Execute permission not allowed')
        }
    }

    @Override
    def <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        //log.debug("Calling `getFileAttributeView`: ${path}")
        checkRoot(path)
        if (type == BasicFileAttributeView || type == QuiltFileAttributesView) {
            QuiltPath qPath = asQuiltPath(path)
            QuiltFileSystem fs = qPath.filesystem
            return (V)fs.getFileAttributeView(qPath)
        }
        throw new UnsupportedOperationException("Operation 'getFileAttributeView' is not supported by QuiltFileSystem")
    }

    @Override
    def <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
         throws IOException {
        //log.debug '<A>BasicFileAttributes QuiltFileSystemProvider.readAttributes()'
        def attr = attributesCache.get(path)
        if (attr) {
            return attr
        }
        if (type == BasicFileAttributes || type == QuiltFileAttributes) {
            QuiltPath qPath = asQuiltPath(path)
            QuiltFileSystem fs = qPath.filesystem
            def result = (A)fs.readAttributes(qPath)
            if (result) {
                attributesCache[path] = result
                return result
            }
            //log.debug("readAttributes: File ${qPath.localPath()} not found")
            throw new NoSuchFileException(qPath.toUriString())
        }
        throw new UnsupportedOperationException("Not a valid Quilt Storage file attribute type: $type")
         }

    @Override
    Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        //log.debug 'Map<String, Object> QuiltFileSystemProvider.readAttributes()'
        throw new UnsupportedOperationException("Operation Map 'readAttributes' is not supported by QuiltFileSystem")
    }

    @Override
    void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Operation 'setAttribute' is not supported by QuiltFileSystem")
    }

}
