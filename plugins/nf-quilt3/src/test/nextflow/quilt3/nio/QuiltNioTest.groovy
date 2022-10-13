package nextflow.quilt3.nio
import nextflow.quilt3.QuiltSpecification
import nextflow.quilt3.jep.QuiltParser

import java.nio.charset.Charset
import java.nio.file.DirectoryNotEmptyException
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
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Ignore
import groovy.util.logging.Slf4j

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
class QuiltNioTest extends QuiltSpecification {

    static String null_url = 'quilt+s3://quilt-dev-null#package=test/null'
    public static def null_path(f) { null_url+"&path=$f" }
    static String pkg_url = 'quilt+s3://quilt-example#package=examples/hurdat'
    public static def pkg_path(f) { pkg_url+"&path=$f" }
    static String url = pkg_path('folder/file-name.txt')
    static String TEXT = "Hello world!"

    def 'should create valid URIs' () {
        given:
        def uri = new URI(url)
        expect:
        uri
        when:
        def path = Paths.get(uri)
        then:
        path
    }

    def 'should write a file' () {
        given:
        def path = Paths.get(new URI(url))

        when:
        Path dest = createObject(path, TEXT)
        then:
        existsPath(dest.toString())
        new String(Files.readAllBytes(path)).trim() == TEXT
        Files.readAllLines(path, Charset.forName('UTF-8')).get(0).trim() == TEXT
        readObject(path).trim() == TEXT
    }

    @IgnoreIf({true})
    def 'should read file attributes' () {
        given:
        final start = System.currentTimeMillis()
        final start_url = pkg_path("folder/${start}.txt")
        def path = Paths.get(new URI(start_url))

        when:
        createObject(path, TEXT)
        and:

        //
        // -- readAttributes
        //
        def attrs = Files.readAttributes(path, BasicFileAttributes)
        def unprefixed = start_url.replace(QuiltParser.PREFIX,"").replace('/','%2f')
        then:
        attrs.isRegularFile()
        !attrs.isDirectory()
        attrs.size() == 12
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == unprefixed
        //attrs.lastAccessTime() == null
        attrs.lastModifiedTime().toMillis()-start < 5_000
        attrs.creationTime().toMillis()-start < 5_000

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
        def view = Files.getFileAttributeView(path, BasicFileAttributeView)
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
        attrs.size() == 0
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == unprefixed.replace("/file-name.txt","")
        attrs.lastAccessTime() == null
        attrs.lastModifiedTime() == null
        attrs.creationTime() == null

        //
        // -- readAttributes for a package
        //
        when:
        attrs = Files.readAttributes(Paths.get(new URI(pkg_url)), BasicFileAttributes)
        then:
        !attrs.isRegularFile()
        attrs.isDirectory()
        attrs.size() == 0
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == pkg_url.replace(QuiltParser.PREFIX,"")
        attrs.creationTime() == null
        attrs.lastAccessTime() == null
        attrs.lastModifiedTime().toMillis()-start < 5_000
    }

    def 'should copy a stream to path' () {
        given:
        def surl = pkg_path("stream.txt")
        def path = Paths.get(new URI(surl))
        def stream = new ByteArrayInputStream(new String(TEXT).bytes)
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

    def 'copy local file to a bucket' () {
        given:
        def path = Paths.get(new URI(url))
        def source = Files.createTempFile('test','nf')
        source.text = TEXT
        Files.deleteIfExists(path.localPath())

        when:
        Files.copy(source, path)
        then:
        readObject(path).trim() == TEXT

        cleanup:
        if( source ) Files.delete(source)
    }

    @Ignore
    def 'copy a remote file to a bucket' () {
        given:
        def path = Paths.get(new URI(url))
        final source_url = url.replace("folder","source")
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
    def 'move a remote file to a bucket' () {
        given:
        def path = Paths.get(new URI(url))
        final source_url = url.replace("folder","source")
        final source = Paths.get(new URI(source_url))
        Files.write(source, TEXT.bytes)
        and:
        Files.move(source, path)
        expect:
        !existsPath(source)
        existsPath(path)
        readObject(path).trim() == TEXT
    }

    def 'should create a directory' () {
        given:
        def dir = Paths.get(new URI(pkg_url))

        when:
        Files.createDirectory(dir)
        then:
        existsPath(dir)
    }

    @Ignore
    def 'should create a directory tree' () {
        given:
        def dir = Paths.get(new URI(pkg_path("alpha/bravo/omega/")))
        when:
        Files.createDirectories(dir)
        then:
        existsPath(Paths.get(new URI(pkg_path("alpha"))))
        existsPath(Paths.get(new URI(pkg_path("alpha/bravo"))))
        existsPath(Paths.get(new URI(pkg_path("alpha/bravo/omega"))))

        when:
        Files.createDirectories(dir)
        then:
        noExceptionThrown()
    }

    def 'should create a file' () {
        given:
        def curl = pkg_path("create.txt")
        def path = Paths.get(new URI(curl))
        Files.createFile(path)
        expect:
        existsPath(path)
    }

    @Ignore
    def 'should create temp file and directory' () {
        given:
        def base = Paths.get(new URI(pkg_url))

        when:
        def t1 = Files.createTempDirectory(base, 'test')
        then:
        existsPaths(t1)

        when:
        def t2 = Files.createTempFile(base, 'prefix', 'suffix')
        then:
        existsPaths(t2)
    }

    def 'should delete a file' () {
        given:
        def path = Paths.get(new URI(url))
        Path dest = createObject(path,TEXT)

        when:
        Files.delete(dest)
        sleep 100
        then:
        !existsPath(dest.toString())
    }

    def 'should throw a NoSuchFileException when deleting an object not existing' () {
        when:
        def path = Paths.get(new URI(url))
        Files.delete(path)
        then:
        thrown(NoSuchFileException)
    }

    def 'should validate exists method' () {
        when:
        def vurl = pkg_path("bolder/file.txt")
        def path = Paths.get(new URI(vurl))
        createObject(path,TEXT)
        QuiltPath p = Paths.get(new URI(file)) as QuiltPath
        then:
        existsPath(p) == flag

        where:
        flag | file
        true | pkg_path("bolder/file.txt")
        true | pkg_path("bolder/file.txt")
        false| pkg_path("bolder/fooooo.txt")
    }

    def 'should check if it is a directory' () {
        given:
        def dir = Paths.get(new URI(pkg_url))
        expect:
        Files.isDirectory(dir)
        !Files.isRegularFile(dir)

        when:
        def file = dir.resolve('this/and/that')
        Path dest = createObject(file, 'Hello world')
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

    def 'should check that is the same file' () {

        given:
        def file1 = Paths.get(new URI(pkg_path("some/data/file.txt")))
        def file2 = Paths.get(new URI(pkg_path("some/data/file.txt")))
        def file3 = Paths.get(new URI(pkg_path("some/data/fooo.txt")))

        expect:
        Files.isSameFile(file1, file2)
        !Files.isSameFile(file1, file3)

    }

    @Ignore
    def 'should create a newBufferedReader' () {
        given:
        def path = Paths.get(new URI(url))
        createObject(path, TEXT)

        when:
        def reader = Files.newBufferedReader(path, Charset.forName('UTF-8'))
        then:
        reader.text == TEXT

        when:
        def unknown = Paths.get(new URI(pkg_path("unknown.txt")))
        Files.newBufferedReader(unknown, Charset.forName('UTF-8'))
        then:
        thrown(NoSuchFileException)
    }

    def 'should create a newBufferedWriter' () {
        given:
        def path = Paths.get(new URI(url))
        def writer = Files.newBufferedWriter(path, Charset.forName('UTF-8'))
        TEXT.readLines().each { it -> writer.println(it) }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    def 'should create a newInputStream' () {
        given:
        def path = Paths.get(new URI(url))
        createObject(path, TEXT)

        when:
        def reader = Files.newInputStream(path)
        then:
        reader.text == TEXT
    }

    def 'should create a newOutputStream' () {
        given:
        def path = Paths.get(new URI(url))
        def writer = Files.newOutputStream(path)
        TEXT.readLines().each { it ->
            writer.write(it.bytes);
            writer.write((int)('\n' as char))
        }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    def 'should read a newByteChannel' () {
        given:
        def path = Paths.get(new URI(url))
        createObject(path, TEXT)

        when:
        def channel = Files.newByteChannel(path)
        then:
        readChannel(channel, 10).trim() == TEXT
    }

    def 'should write a byte channel' () {
        given:
        def path = Paths.get(new URI(url))
        def channel = Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        writeChannel(channel, TEXT, 200)
        channel.close()
        expect:
        readObject(path).trim() == TEXT
    }

    def 'should check file size' () {
        given:
        def path = Paths.get(new URI(url))
        createObject(path, TEXT)
        expect:
        Files.size(path) == TEXT.size()

        when:
        Files.size(path.resolve('xxx'))
        then:
        thrown(FileSystemException)
    }

    def 'should stream directory content' () {
        given:
        createObject(null_path("foo/file1.txt"),'A')
        createObject(null_path("foo/file2.txt"),'BB')
        createObject(null_path("foo/bar/file3.txt"),'CCC')
        createObject(null_path("foo/bar/baz/file4.txt"),'DDDD')
        createObject(null_path("foo/bar/file5.txt"),'EEEEE')
        createObject(null_path("foo/file6.txt"),'FFFFFF')

        when:
        def p = Paths.get(new URI(null_url))
        def list = Files.newDirectoryStream(p).collect {
             log.debug "newDirectoryStream[$p]: $it"
             it.getFileName().toString()
        }
        then:
        list.size() == 1
        list == [ 'foo' ]

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path("foo")))).collect { it.getFileName().toString() }
        then:
        list.size() == 4
        list as Set == [ 'file1.txt', 'file2.txt', 'bar', 'file6.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path("foo/bar")))).collect { it.getFileName().toString() }
        then:
        list.size() == 3
        list as Set == [ 'file3.txt', 'baz', 'file5.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path("foo/bar/baz")))).collect { it.getFileName().toString() }
        then:
        list.size() == 1
        list  == [ 'file4.txt' ]
    }

    @IgnoreIf({true})
    def 'should check walkTree' () {

        given:
        createObject(null_path("foo/file1.txt"),'A')
        createObject(null_path("foo/file2.txt"),'BB')
        createObject(null_path("foo/bar/file3.txt"),'CCC')
        createObject(null_path("foo/bar/baz/file4.txt"),'DDDD')
        createObject(null_path("foo/bar/file5.txt"),'EEEEE')
        createObject(null_path("foo/file6.txt"),'FFFFFF')

        when:
        List<String> dirs = []
        Map<String,BasicFileAttributes> files = [:]
        def base = Paths.get(new URI(null_url))
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                dirs << base.relativize(dir).toString()
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                files[file.getFileName().toString()] = attrs
                return FileVisitResult.CONTINUE;
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
        dirs.contains("")
        dirs.contains('foo')
        dirs.contains('foo/bar')
        dirs.contains('foo/bar/baz')


        when:
        dirs = []
        files = [:]
        base = Paths.get(new URI(null_path("foo/bar/")))
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                dirs << base.relativize(dir).toString()
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                files[file.getFileName().toString()] = attrs
                return FileVisitResult.CONTINUE;
            }
        })

        then:
        files.size()==3
        files.containsKey('file3.txt')
        files.containsKey('file4.txt')
        files.containsKey('file5.txt')
        dirs.size() == 2
        dirs.contains("")
        dirs.contains('baz')
    }

    @Ignore
    def 'should handle dir and files having the same name' () {

        given:
        createObject(pkg_path("foo"),'file-1')
        createObject(pkg_path("foo/bar"),'file-2')
        createObject(pkg_path("foo/baz"),'file-3')
        and:
        def root = Paths.get(new URI(pkg_url))

        when:
        def file1 = root.resolve('foo')
        then:
        Files.isRegularFile(file1)
        !Files.isDirectory(file1)
        file1.text == 'file-1'

        when:
        def dir1 = root.resolve('foo/')
        then:
        !Files.isRegularFile(dir1)
        Files.isDirectory(dir1)

        when:
        def file2 = root.resolve('foo/bar')
        then:
        Files.isRegularFile(file2)
        !Files.isDirectory(file2)
        file2.text == 'file-2'

        when:
        def parent = file2.parent
        then:
        !Files.isRegularFile(parent)
        Files.isDirectory(parent)

        when:
        Set<String> dirs = []
        Map<String,BasicFileAttributes> files = [:]
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                dirs << root.relativize(dir).toString()
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                files[root.relativize(file).toString()] = attrs
                return FileVisitResult.CONTINUE;
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

    def 'should handle file names with same prefix' () {
        given:
        createObject(pkg_path("transcript_index.junctions.fa"), 'foo')
        createObject(pkg_path("alpha-beta/file1"), 'bar')
        createObject(pkg_path("alpha/file2"), 'baz')

        when:
        def uri = new URI(pkg_path(file))
        QuiltPath p = Paths.get(uri) as QuiltPath
        then:
        existsPath(p) == flag

        where:
        flag  | file
        true  | "transcript_index.junctions.fa"
        false | "transcript_index.junctions"
        true  | "alpha-beta/file1"
        true  | "alpha/file2"
        true  | "alpha-beta"
        true  | "alpha"
    }

}
