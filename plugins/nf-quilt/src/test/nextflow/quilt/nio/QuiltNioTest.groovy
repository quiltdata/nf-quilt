package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import nextflow.quilt.jep.QuiltPackage

import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes

import spock.lang.IgnoreIf
import spock.lang.Ignore
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class QuiltNioTest extends QuiltSpecification {

    static String null_url = 'quilt+s3://quilt-dev-null#package=test/null'
    static null_path(f) { null_url + "&path=$f" }
    //https://open.quiltdata.com/b/quilt-example/tree/examples/hurdat/
    static String packageURL = 'quilt+s3://quilt-example#package=examples/hurdat'
    static packagePath(f) { packageURL + "&path=$f" }
    static String write_url = packagePath('folder/file-name.txt')
    static String read_url = packagePath('data/atlantic-storms.csv')
    static String TEXT = 'Hello world!'

    void 'should create valid URIs' () {
        given:
        URI uri = new URI(write_url)
        expect:
        uri
        when:
        Path path = Paths.get(uri)
        then:
        path
    }

    void 'should write to a path' () {
        given:
        Path path = Paths.get(new URI(write_url))

        when:
        Path dest = createObject(path, TEXT)
        then:
        existsPath(dest.toString())
        new String(Files.readAllBytes(path)).trim() == TEXT
        Files.readAllLines(path, Charset.forName('UTF-8')).get(0).trim() == TEXT
        readObject(path).trim() == TEXT
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    void 'should read from a path' () {
        given:
        Path path = Paths.get(new URI(read_url))

        when:
        String text = readObject(path)
        then:
        text.startsWith('id')
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    void 'should read file attributes' () {
        given:
        final start = System.currentTimeMillis()
        final root = 'folder'
        final start_path = "${root}/${start}.txt"
        final start_url = packagePath(start_path)
        Path path = Paths.get(new URI(start_url))

        when:
        createObject(path, TEXT)
        and:

        //
        // -- readAttributes
        //
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes)
        then:
        attrs.isRegularFile()
        !attrs.isDirectory()
        attrs.size() == 12
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == start_path
        //attrs.lastAccessTime() == null
        attrs.lastModifiedTime().toMillis() - start < 5_000
        attrs.creationTime().toMillis() - start < 5_000

        //
        // -- getLastModifiedTime
        //
        when:
        def time = Files.getLastModifiedTime(path)
        then:
        time == attrs.lastModifiedTime()

        //
        // -- getFileAttributeView
        //
        when:
        BasicFileAttributeView view = Files.getFileAttributeView(path, BasicFileAttributeView)
        then:
        view.readAttributes().toString() == attrs.toString()

        //
        // -- readAttributes for a directory
        //
        when:
        attrs = Files.readAttributes(path.getParent(), BasicFileAttributes)
        then:
        !attrs.isRegularFile()
        attrs.isDirectory()
        attrs.size() == 128
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == root
        //attrs.lastAccessTime() == null
        attrs.lastModifiedTime() != null
        attrs.creationTime() != null

        //
        // -- readAttributes for a package
        //
        when:
        attrs = Files.readAttributes(Paths.get(new URI(packageURL)), BasicFileAttributes)
        then:
        !attrs.isRegularFile()
        attrs.isDirectory()
        attrs.size() == 224
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == '/'
        attrs.creationTime() != null
        //attrs.lastAccessTime() == null
        attrs.lastModifiedTime().toMillis() - start < 5_000
    }

    void 'should copy a stream to path' () {
        given:
        String surl = packagePath('stream.txt')
        Path path = Paths.get(new URI(surl))
        ByteArrayInputStream stream = new ByteArrayInputStream(new String(TEXT).bytes)
        Files.copy(stream, path)
        and:
        existsPath(path)
        readObject(path).trim() == TEXT

        when:
        stream = new ByteArrayInputStream(new String(TEXT).bytes)
        Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING)
        then:
        existsPath(path)
        readObject(path).trim() == TEXT

        when:
        stream = new ByteArrayInputStream(new String(TEXT).bytes)
        Files.copy(stream, path)
        then:
        thrown(FileAlreadyExistsException)
    }

    void 'copy local file to a bucket' () {
        given:
        Path path = Paths.get(new URI(write_url))
        Path source = Files.createTempFile('test', 'nf')
        source.text = TEXT
        Files.deleteIfExists(path.localPath())

        when:
        Files.copy(source, path)
        then:
        readObject(path).trim() == TEXT

        cleanup:
        if (source) Files.delete(source)
    }

    @Ignore
    void 'copy a remote file to a bucket' () {
        given:
        Path path = Paths.get(new URI(write_url))
        final source_url = write_url.replace('test_folder', 'source')
        final source = Paths.get(new URI(source_url))
        Files.write(source, TEXT.bytes)
        and:
        Files.copy(source, path)
        expect:
        existsPath(source)
        existsPath(path)
        readObject(path).trim() == TEXT
    }

    @Ignore
    void 'move a remote file to a bucket' () {
        given:
        Path path = Paths.get(new URI(write_url))
        final source_url = write_url.replace('test_folder', 'source')
        final source = Paths.get(new URI(source_url))
        Files.write(source, TEXT.bytes)
        and:
        Files.move(source, path)
        expect:
        !existsPath(source)
        existsPath(path)
        readObject(path).trim() == TEXT
    }

    void 'should create a package' () {
        given:
        Path path = Paths.get(new URI(packageURL))
        when:
        QuiltPackage pkg = path.pkg()
        then:
        pkg
        pkg.relativeChildren('')
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    void 'should iterate over package folders/files' () {
        given:
        Path path = Paths.get(new URI(packageURL))
        when:
        QuiltPathIterator itr = new QuiltPathIterator(path, null)
        then:
        itr != null

        itr.hasNext()
        itr.next().toString().contains('path=data')
        itr.next().toString().contains('path=folder') //whuh?
        itr.next().toString().contains('path=notebooks')
        itr.next().toString().contains('path=quilt_summarize.json')

        when:
        Path spath = itr.next()
        QuiltPathIterator sitr = new QuiltPathIterator(spath, null)
        then:
        spath.toString().contains('path=scripts')
        sitr.hasNext()
        sitr.next().toString().contains('path=scripts%2fbuild.py')
    }

    void 'should create a directory' () {
        given:
        Path dir = Paths.get(new URI(packageURL))

        when:
        Files.createDirectory(dir)
        then:
        existsPath(dir)
    }

    void 'should create a directory tree' () {
        given:
        Path dir = Paths.get(new URI(packagePath('alpha/bravo/omega/')))
        when:
        Files.createDirectories(dir)
        then:
        existsPath(Paths.get(new URI(packagePath('alpha'))))
        existsPath(Paths.get(new URI(packagePath('alpha/bravo'))))
        existsPath(Paths.get(new URI(packagePath('alpha/bravo/omega'))))

        when:
        Files.createDirectory(dir)
        then:
        noExceptionThrown()
    }

    void 'should create a file' () {
        given:
        String curl = packagePath('create.txt')
        Path path = Paths.get(new URI(curl))
        Files.createFile(path)
        expect:
        existsPath(path)
    }

    @Ignore
    void 'should create temp file and directory' () {
        given:
        Path base = Paths.get(new URI(packageURL))

        when:
        Path t1 = Files.createTempDirectory(base, 'test')
        then:
        existsPaths(t1)

        when:
        Path t2 = Files.createTempFile(base, 'prefix', 'suffix')
        then:
        existsPaths(t2)
    }

    void 'should delete a file' () {
        given:
        Path path = Paths.get(new URI(write_url))
        Path dest = createObject(path, TEXT)

        when:
        Files.delete(dest)
        sleep 100
        then:
        !existsPath(dest.toString())
    }

    void 'should throw a NoSuchFileException when deleting an object not existing' () {
        when:
        Path path  = Paths.get(new URI(write_url))
        Files.delete(path)
        then:
        thrown(NoSuchFileException)
    }

    void 'should validate exists method' () {
        when:
        String vurl = packagePath('bolder/file.txt')
        Path path  = Paths.get(new URI(vurl))
        createObject(path, TEXT)
        QuiltPath p = Paths.get(new URI(file)) as QuiltPath
        then:
        existsPath(p) == flag

        where:
        flag | file
        true | packagePath('bolder/file.txt')
        true | packagePath('bolder/file.txt')
        false | packagePath('bolder/fooooo.txt')
    }

    void 'should check if it is a directory' () {
        given:
        Path dir = Paths.get(new URI(packageURL))
        expect:
        Files.isDirectory(dir)
        !Files.isRegularFile(dir)

        when:
        Path file = dir.resolve('this/and/that')
        createObject(file, 'Hello world')
        then:
        !Files.isDirectory(file)
        Files.isRegularFile(file)
        Files.isReadable(file)
        Files.isWritable(file)
        !Files.isExecutable(file)
        !Files.isSymbolicLink(file)

        expect:
        Files.isDirectory(file.parent)
        !Files.isRegularFile(file.parent)
        Files.isReadable(file)
        Files.isWritable(file)
        !Files.isExecutable(file)
        !Files.isSymbolicLink(file)
    }

    void 'should check that is the same file' () {
        given:
        Path file1 = Paths.get(new URI(packagePath('some/data/file.txt')))
        Path file2 = Paths.get(new URI(packagePath('some/data/file.txt')))
        Path file3 = Paths.get(new URI(packagePath('some/data/fooo.txt')))

        expect:
        Files.isSameFile(file1, file2)
        !Files.isSameFile(file1, file3)
    }

    void 'should create a newBufferedReader' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        createObject(path, TEXT)

        when:
        def reader = Files.newBufferedReader(path, Charset.forName('UTF-8'))
        then:
        reader.text == TEXT

        when:
        Path unknown = Paths.get(new URI(packagePath('unknown.txt')))
        Files.newBufferedReader(unknown, Charset.forName('UTF-8'))
        then:
        thrown(NullPointerException)
    }

    void 'should create a newBufferedWriter' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        def writer = Files.newBufferedWriter(path, Charset.forName('UTF-8'))
        TEXT.readLines().each { it -> writer.println(it) }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should create a newInputStream' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        createObject(path, TEXT)

        when:
        def reader = Files.newInputStream(path)
        then:
        reader.text == TEXT
    }

    void 'should create a newOutputStream' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        def writer = Files.newOutputStream(path)
        TEXT.readLines().each { it ->
            writer.write(it.bytes)
            writer.write((int)('\n' as char))
        }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should read a newByteChannel' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        createObject(path, TEXT)

        when:
        def channel = Files.newByteChannel(path)
        then:
        readChannel(channel, 10).trim() == TEXT
    }

    void 'should write a byte channel' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        def channel = Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        writeChannel(channel, TEXT, 200)
        channel.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should check file size' () {
        given:
        Path path  = Paths.get(new URI(write_url))
        createObject(path, TEXT)
        expect:
        Files.size(path) == TEXT.size()

        when:
        Files.size(path.resolve('xxx'))
        then:
        thrown(FileSystemException)
    }

    @Ignore
    void 'should stream directory content' () {
        given:
        createObject(null_path('foo/file1.txt'), 'A')
        createObject(null_path('foo/file2.txt'), 'BB')
        createObject(null_path('foo/bar/file3.txt'), 'CCC')
        createObject(null_path('foo/bar/baz/file4.txt'), 'DDDD')
        createObject(null_path('foo/bar/file5.txt'), 'EEEEE')
        createObject(null_path('foo/file6.txt'), 'FFFFFF')

        when:
        Path p = Paths.get(new URI(null_url))
        List<String> list = Files.newDirectoryStream(p).collect {
            it.getFileName().toString()
        }
        then:
        list.size() == 1
        list == [ 'foo' ]

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo')))).collect { it.getFileName().toString() }
        then:
        list.size() == 4
        list as Set == [ 'file1.txt', 'file2.txt', 'bar', 'file6.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo/bar')))).collect { it.getFileName().toString() }
        then:
        list.size() == 3
        list as Set == [ 'file3.txt', 'baz', 'file5.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo/bar/baz')))).collect { it.getFileName().toString() }
        then:
        list.size() == 1
        list  == [ 'file4.txt' ]
    }

    @Ignore
    void 'should check walkTree' () {
        given:
        createObject(null_path('foo/file1.txt'), 'A')
        createObject(null_path('foo/file2.txt'), 'BB')
        createObject(null_path('foo/bar/file3.txt'), 'CCC')
        createObject(null_path('foo/bar/baz/file4.txt'), 'DDDD')
        createObject(null_path('foo/bar/file5.txt'), 'EEEEE')
        createObject(null_path('foo/file6.txt'), 'FFFFFF')

        when:
        List<String> dirs = []
        Map<String,BasicFileAttributes> files = [:]
        Path base = Paths.get(new URI(null_url))
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dirs << base.relativize(dir).toString()
                return FileVisitResult.CONTINUE
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files[file.getFileName().toString()] = attrs
                return FileVisitResult.CONTINUE
            }

        })

        then:
        files.size() == 6
        files ['file1.txt'].size() == 1
        files ['file2.txt'].size() == 2
        files ['file3.txt'].size() == 3
        files ['file4.txt'].size() == 4
        files ['file5.txt'].size() == 5
        files ['file6.txt'].size() == 6
        dirs.size() == 4
        dirs.contains('null')
        dirs.contains('foo')
        dirs.contains('foo/bar')
        dirs.contains('foo/bar/baz')

        when:
        dirs = []
        files = [:]
        base = Paths.get(new URI(null_path('foo/bar/')))
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dirs << base.relativize(dir).toString()
                return FileVisitResult.CONTINUE
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files[file.getFileName().toString()] = attrs
                return FileVisitResult.CONTINUE
            }

        })

        then:
        files.size() == 3
        files.containsKey('file3.txt')
        files.containsKey('file4.txt')
        files.containsKey('file5.txt')
        dirs.size() == 2
        dirs.contains('null')
        dirs.contains('baz')
    }

    @Ignore
    void 'should handle dir and files having the same name' () {
        given:
        createObject(packagePath('foo'), 'file-1')
        createObject(packagePath('foo/bar'), 'file-2')
        createObject(packagePath('foo/baz'), 'file-3')
        and:
        Path root = Paths.get(new URI(packageURL))

        when:
        Path file1 = root.resolve('foo')
        then:
        Files.isRegularFile(file1)
        !Files.isDirectory(file1)
        file1.text == 'file-1'

        when:
        Path dir1 = root.resolve('foo/')
        then:
        !Files.isRegularFile(dir1)
        Files.isDirectory(dir1)

        when:
        Path file2 = root.resolve('foo/bar')
        then:
        Files.isRegularFile(file2)
        !Files.isDirectory(file2)
        file2.text == 'file-2'

        when:
        Path parent = file2.parent
        then:
        !Files.isRegularFile(parent)
        Files.isDirectory(parent)

        when:
        Set<String> dirs = []
        Map<String,BasicFileAttributes> files = [:]
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dirs << root.relativize(dir).toString()
                return FileVisitResult.CONTINUE
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files[root.relativize(file).toString()] = attrs
                return FileVisitResult.CONTINUE
            }

        })
        then:
        dirs.size() == 2
        dirs.contains('')
        dirs.contains('foo')
        files.size() == 3
        files.containsKey('foo')
        files.containsKey('foo/bar')
        files.containsKey('foo/baz')
    }

    void 'should handle file names with same prefix' () {
        given:
        createObject(packagePath('transcript_index.junctions.fa'), 'foo')
        createObject(packagePath('alpha-beta/file1'), 'bar')
        createObject(packagePath('alpha/file2'), 'baz')

        when:
        URI uri = new URI(packagePath(file))
        QuiltPath p = Paths.get(uri) as QuiltPath
        then:
        existsPath(p) == flag

        where:
        flag  | file
        true  | 'transcript_index.junctions.fa'
        false | 'transcript_index.junctions'
        true  | 'alpha-beta/file1'
        true  | 'alpha/file2'
        true  | 'alpha-beta'
        true  | 'alpha'
    }

}
