/* groovylint-disable MethodName */
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

import spock.lang.Ignore
import spock.lang.IgnoreIf
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class QuiltNioTest extends QuiltSpecification {

    private static final String NULL_URL = 'quilt+s3://quilt-dev-null#package=test/null'
    //https://open.quiltdata.com/b/quilt-example/tree/examples/hurdat/
    private static final String PACKAGE_URL = 'quilt+s3://quilt-example#package=examples/hurdat@f8d1478d93'
    private static final String WRITE_URL = packagePath('folder/file-name.txt')
    private static final String READ_URL = packagePath('data/atlantic-storms.csv')
    private static final String TEXT = 'Hello world!'

    static String null_path(String f) { return NULL_URL + "&path=$f" }
    static String packagePath(String f) { return PACKAGE_URL + "&path=$f" }

    void 'should create valid URIs'() {
        given:
        URI uri = new URI(WRITE_URL)
        expect:
        uri
        when:
        Path path = Paths.get(uri)
        then:
        path
    }

    void 'should write to a path'() {
        given:
        Path path = Paths.get(new URI(WRITE_URL))

        when:
        Path dest = makeObject(path, TEXT)
        then:
        existsPath(dest.toString())
        new String(Files.readAllBytes(path)).trim() == TEXT
        Files.readAllLines(path, Charset.forName('UTF-8')).get(0).trim() == TEXT
        readObject(path).trim() == TEXT
    }

    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should read from a path'() {
        given:
        QuiltPath path = Paths.get(new URI(READ_URL)) as QuiltPath
        path.pkg().install()

        when:

        when:
        String text = readObject(path)
        then:
        text.startsWith('id')
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should read file attributes'() {
        given:
        final start = System.currentTimeMillis()
        final root = 'folder'
        final start_path = "${root}/${start}.txt"
        final start_url = packagePath(start_path)
        Path path = Paths.get(new URI(start_url))

        when:
        makeObject(path, TEXT)
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
        attrs = Files.readAttributes(Paths.get(new URI(PACKAGE_URL)), BasicFileAttributes)
        then:
        !attrs.isRegularFile()
        attrs.isDirectory()
        attrs.size() > 200
        !attrs.isSymbolicLink()
        !attrs.isOther()
        attrs.fileKey() == '/'
        attrs.creationTime() != null
        //attrs.lastAccessTime() == null
        attrs.lastModifiedTime().toMillis() - start < 5_000
    }

    void 'should copy a stream to path'() {
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

    void 'copy local file to a bucket'() {
        given:
        Path path = Paths.get(new URI(WRITE_URL))
        Path source = Files.createTempFile('test', 'nf')
        source.text = TEXT
        Files.deleteIfExists(path.localPath())

        when:
        Files.copy(source, path)
        then:
        readObject(path).trim() == TEXT

        cleanup:
        if (source) { Files.delete(source) }
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'copy a remote file to a bucket'() {
        given:
        Path path = Paths.get(new URI(WRITE_URL))
        final source_url = WRITE_URL.replace('test_folder', 'source')
        final source = Paths.get(new URI(source_url))
        Files.write(source, TEXT.bytes)
        and:
        Files.copy(source, path)
        expect:
        existsPath(source)
        existsPath(path)
        readObject(path).trim() == TEXT
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'move a remote file to a bucket'() {
        given:
        Path path = Paths.get(new URI(WRITE_URL))
        final source_url = WRITE_URL.replace('test_folder', 'source')
        final source = Paths.get(new URI(source_url))
        Files.write(source, TEXT.bytes)
        and:
        Files.move(source, path)
        expect:
        !existsPath(source)
        existsPath(path)
        readObject(path).trim() == TEXT
    }

    void 'should create a package'() {
        given:
        Path path = Paths.get(new URI(PACKAGE_URL))
        when:
        QuiltPackage pkg = path.pkg()
        then:
        pkg
        pkg.relativeChildren('')
    }

    /* FIXME: Test fails on Windows and Ubuntu. STDOUT shows:
     Children: [README_NF_QUILT.md, alpha, bolder, create.txt, data, folder,
                nf-quilt, notebooks, quilt_summarize.json, scripts, stream.txt]
     Test has: [path=data, path=folder, path=notebooks,
                path=quilt_summarize.json, path=scripts, path=stream.txt]
    */
    // @IgnoreIf({ System.getProperty('os.name').contains('indows') || System.getProperty('os.name').contains('ux') })
    void 'should iterate over package folders/files'() {
        given:
        Path path = Paths.get(new URI(PACKAGE_URL))
        when:
        QuiltPathIterator itr = new QuiltPathIterator(path, null)
        println "ITR: ${itr}"
        then:
        itr != null
        itr.hasNext()

        when:
        String[] ilist = itr*.toString()*.replaceFirst('quilt-example#package=examples%2fhurdat&', '').toArray()
        println "ILIST: ${ilist}"
        then:
        ilist.size() > 4
        ilist.contains('path=README_NF_QUILT.md')
        ilist.contains('path=data')
        ilist.contains('path=folder')
        ilist.contains('path=scripts')
    }

    void 'should create a directory'() {
        given:
        Path dir = Paths.get(new URI(PACKAGE_URL))

        when:
        Files.createDirectory(dir)
        then:
        existsPath(dir)
    }

    void 'should create a directory tree'() {
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

    void 'should create a file'() {
        given:
        String curl = packagePath('create.txt')
        Path path = Paths.get(new URI(curl))
        Files.createFile(path)
        expect:
        existsPath(path)
    }

    @Ignore('toAbsolutePath not implemented yet')
    void 'should create temp file and directory'() {
        given:
        Path base = Paths.get(new URI(PACKAGE_URL)).toAbsolutePath()
        println "BASE: ${base}"

        when:
        Path t1 = Files.createTempDirectory(base, 'test')
        then:
        existsPaths(t1)

        when:
        Path t2 = Files.createTempFile(base, 'prefix', 'suffix')
        then:
        existsPaths(t2)
    }

    void 'should delete a file'() {
        given:
        Path path = Paths.get(new URI(WRITE_URL))
        Path dest = makeObject(path, TEXT)

        when:
        Files.delete(dest)
        sleep 100
        then:
        !existsPath(dest.toString())
    }

    @Ignore('Catching in deinstall seems to swallow all such errors')
    void 'should throw a NoSuchFileException when deleting an object not existing'() {
        when:
        Path path  = Paths.get(new URI(WRITE_URL))
        Files.delete(path)
        then:
        thrown(NoSuchFileException)
    }

    void 'should validate exists method'() {
        when:
        String vurl = packagePath('bolder/file.txt')
        Path path  = Paths.get(new URI(vurl))
        makeObject(path, TEXT)
        QuiltPath p = Paths.get(new URI(file)) as QuiltPath
        then:
        existsPath(p) == flag

        where:
        flag | file
        true | packagePath('bolder/file.txt')
        true | packagePath('bolder/file.txt')
        false | packagePath('bolder/fooooo.txt')
    }

    void 'should check if it is a directory'() {
        given:
        Path dir = Paths.get(new URI(PACKAGE_URL))
        expect:
        Files.isDirectory(dir)
        !Files.isRegularFile(dir)

        when:
        Path file = dir.resolve('this/and/that')
        makeObject(file, 'Hello world')
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

    void 'should check that is the same file'() {
        given:
        Path file1 = Paths.get(new URI(packagePath('some/data/file.txt')))
        Path file2 = Paths.get(new URI(packagePath('some/data/file.txt')))
        Path file3 = Paths.get(new URI(packagePath('some/data/fooo.txt')))

        expect:
        Files.isSameFile(file1, file2)
        !Files.isSameFile(file1, file3)
    }

    void 'should create a newBufferedReader'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        makeObject(path, TEXT)

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

    void 'should create a newBufferedWriter'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        def writer = Files.newBufferedWriter(path, Charset.forName('UTF-8'))
        TEXT.readLines().each { line -> writer.println(line) }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should create a newInputStream'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        makeObject(path, TEXT)

        when:
        def reader = Files.newInputStream(path)
        then:
        reader.text == TEXT
    }

    void 'should create a newOutputStream'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        def writer = Files.newOutputStream(path)
        TEXT.readLines().each { line ->
            writer.write(line.bytes)
            writer.write((int)('\n' as char))
        }
        writer.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should read a newByteChannel'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        makeObject(path, TEXT)

        when:
        def channel = Files.newByteChannel(path)
        then:
        readChannel(channel, 10).trim() == TEXT
    }

    void 'should write a byte channel'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        def channel = Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        writeChannel(channel, TEXT, 200)
        channel.close()
        expect:
        readObject(path).trim() == TEXT
    }

    void 'should check file size'() {
        given:
        Path path  = Paths.get(new URI(WRITE_URL))
        makeObject(path, TEXT)
        expect:
        Files.size(path) == TEXT.size()

        when:
        Files.size(path.resolve('xxx'))
        then:
        thrown(FileSystemException)
    }

    // @Ignore
    void 'should stream directory content'() {
        given:
        makeObject(null_path('foo/file1.txt'), 'A')
        makeObject(null_path('foo/file2.txt'), 'BB')
        makeObject(null_path('foo/bar/file3.txt'), 'CCC')
        makeObject(null_path('foo/bar/baz/file4.txt'), 'DDDD')
        makeObject(null_path('foo/bar/file5.txt'), 'EEEEE')
        makeObject(null_path('foo/file6.txt'), 'FFFFFF')

        when:
        Path p = Paths.get(new URI(NULL_URL))
        List<String> list = Files.newDirectoryStream(p).collect {
            path -> path.getFileName().toString()
        }
        then:
        list.size() == 1
        list == [ 'foo' ]

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo')))).collect {
             path -> path.getFileName().toString()
        }
        then:
        list.size() == 4
        list as Set == [ 'file1.txt', 'file2.txt', 'bar', 'file6.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo/bar')))).collect {
            path -> path.getFileName().toString()
        }
        then:
        list.size() == 3
        list as Set == [ 'file3.txt', 'baz', 'file5.txt' ] as Set

        when:
        list = Files.newDirectoryStream(Paths.get(new URI(null_path('foo/bar/baz')))).collect {
            path -> path.getFileName().toString()
        }
        then:
        list.size() == 1
        list  == [ 'file4.txt' ]
    }

    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should check walkTree'() {
        given:
        makeObject(null_path('foo/file1.txt'), 'A')
        makeObject(null_path('foo/file2.txt'), 'BB')
        makeObject(null_path('foo/bar/file3.txt'), 'CCC')
        makeObject(null_path('foo/bar/baz/file4.txt'), 'DDDD')
        makeObject(null_path('foo/bar/file5.txt'), 'EEEEE')
        makeObject(null_path('foo/file6.txt'), 'FFFFFF')

        when:
        List<String> dirs = []
        Map<String,BasicFileAttributes> files = [:]
        Path base = Paths.get(new URI(NULL_URL))
        Files.walkFileTree(base, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dirs << base.relativize(dir).toString()
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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

    @Ignore('Not implemented yet: dir and files having the same name')
    void 'should handle dir and files having the same name'() {
        given:
        makeObject(packagePath('foo'), 'file-1')
        makeObject(packagePath('foo/bar'), 'file-2')
        makeObject(packagePath('foo/baz'), 'file-3')
        and:
        Path root = Paths.get(new URI(PACKAGE_URL))

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
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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

    void 'should handle file names with same prefix'() {
        given:
        makeObject(packagePath('transcript_index.junctions.fa'), 'foo')
        makeObject(packagePath('alpha-beta/file1'), 'bar')
        makeObject(packagePath('alpha/file2'), 'baz')

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
