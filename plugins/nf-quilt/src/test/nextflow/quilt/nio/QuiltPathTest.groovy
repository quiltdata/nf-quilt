package nextflow.quilt.nio
import nextflow.quilt.QuiltSpecification
import nextflow.quilt.jep.QuiltParser

import java.nio.file.Path
import java.nio.file.Paths
import groovy.util.logging.Slf4j

import spock.lang.Shared
import spock.lang.Unroll
import spock.lang.Ignore
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class QuiltPathTest extends QuiltSpecification {

    static final String BKT = "bucket"
    static final String PKG_URL = "${QuiltParser.PREFIX}${BKT}#package=so%2fme"
    static QuiltPath pkgPath
    static QuiltFileSystem QFS
    static String sub_path = "${BKT}#package=s/d&path=f%2ffile.txt"

    private QuiltPath pathify(String path) {
        if (!pkgPath) { pkgPath = Paths.get(new URI(PKG_URL)) }
        if (!QFS) { QFS = pkgPath.getFileSystem() }
        if (!path.contains(BKT)) {
            return QFS.getPath(path)
        }
        String url = QuiltParser.PREFIX + path
        Paths.get(new URI(url))
    }

    @Unroll
    void 'should create a path: #objectName'() {

        when:
        Path path = pathify(objectName)
        then:
        path.toString() == expected
        //path.directory == dir

        where:
        objectName                      | expected                          | dir
        'bucket#package=a%2fb&path=b.c' | 'bucket#package=a%2fb&path=b.c'   | false
        'bucket#package=a%2fb%2fb.c'    | 'bucket#package=a%2fb&path=b.c'   | false // Postel's Law
        'bucket#package=a%2fb&path=c'   | 'bucket#package=a%2fb&path=c'     | false
        'bucket#package=a%2fb&path=c/f' | 'bucket#package=a%2fb&path=c%2ff' | true
        '#path=b.c'                     | 'null#path=b.c'                   | false
        '#package=o%2fs&path=b.c'       | 'null#package=o%2fs&path=b.c'     | false
    }

    void 'should validate equals and hashCode'() {

        when:
        Path path1 = pathify('bucket#package=so%2fme&path=file-name.txt')
        Path path2 = pathify('bucket#package=so%2fme&path=file-name.txt')
        Path path3 = pathify('bucket#package=ot%2fher&path=file-name.txt')
        Path path4 = pathify('bucket2#package=so%2fme&path=file-name.txt')

        then:
        path1 == path2
        path1 != path3
        path1 != path4

        path1.hashCode() == path2.hashCode()
        path1.hashCode() != path3.hashCode()

        when:
        Path rel1 = pathify('file.txt')
        Path rel2 = pathify('file.txt')
        then:
        rel1 == rel2
        rel1.hashCode() == rel2.hashCode()

    }

    void 'should validate isAbsolute'() {
        when:
        Path path1 = pathify('bucket#package=so%2fme&path=file-name.txt')
        Path path2 = pathify('file-name.txt')

        then:
        path1.isAbsolute()
        !path2.isAbsolute()
    }

    void 'should validate getRoot'() {
        when:
        Path path1 = pathify('bucket#package=so%2fme&path=file-name.txt')
        Path path2 = pathify('#path=file-name.txt')

        then:
        path1.getRoot() == pathify('bucket#package=so%2fme')
        path1.getRoot().toString() == 'bucket#package=so%2fme'
        path2.getRoot().toString() == 'null'
    }

    @Unroll
    void 'should return fsName and isJustPackage' () {
        when:
        Path p = pathify(path)
        then:
        p.isJustPackage() == expected
        p.getFileSystem().toString() == fsName

        where:
        path                               | expected  |  fsName
        'bucket'                           | true      | 'bucket.null.default'
        'bucket#package=sum/data'          | true      | 'bucket.sum.data'
        'bucket#path=file.txt'             | false     | 'bucket.null.default'
        'bucket#package=sum/data/file.txt' | false     | 'bucket.sum.data'
        '#path=file-name.txt'              | false     | 'bucket.so.me'
    }

    @Ignore
    void 'should validate getFileName'() {
        expect:
        pathify(path).getFileName() == pathify(fileName)

        where:
        path                                   | fileName
        'bucket#path=file.txt'                 | 'file.txt'
        'bucket#path=some%2fdata%2ffile.txt'   | 'file.txt'
        '#path=file-name.txt'                  | 'file-name.txt'
    }

    @Unroll
    void 'should validate getParent: #path'() {
        given:
        Path parent_path = (parent ? pathify(parent) : null)
        expect:
        pathify(path).getParent() == parent_path

        where:
        path                                | parent
        'bucket#path=some%2fdata%2ffile.txt'| 'bucket#path=some%2fdata'
        'bucket#path=data%2ffile.txt'       | 'bucket#path=data'
        'bucket#path=file-name.txt'         | 'bucket#path=/'
        'bucket'                            | 'bucket'
    }

    @Unroll
    void 'should validate toUri: #uri'() {
        expect:
        pathify(path).toUri() == new URI(uri)
        pathify(path).toUri().scheme == new URI(uri).scheme
        pathify(path).toUri().authority == new URI(uri).authority
        pathify(path).toUri().path == new URI(uri).path

        where:
        path                                     | uri
        'alpha#package=so%2fme&path=file.txt'    | 'quilt+s3://alpha#package=so%2fme&path=file.txt'
        'alpha%2f'                               | 'quilt+s3://alpha/'
        'alpha'                                  | 'quilt+s3://alpha'
        '#path=some-file.txt'                    | 'quilt+s3://null#path=some-file.txt'
    }


    @Unroll
    void 'should validate resolve: base:=#base; path=#path'() {

        expect:
        pathify(base).resolve(path) == pathify(expected)
        //pathify(base).resolve( pathify(path) ) == pathify(expected)

        where:
        base                        | path                        | expected
        'bucket#package=so%2fme'    | 'file-name.txt'             | 'bucket#package=so%2fme&path=file-name.txt'
        'bucket#package=da/ta'      | 'dir%2ffile-name.txt'       | 'bucket#package=da%2fta&path=dir%2ffile-name.txt'
        'bucket#package=da/ta'      | '/dir%2ffile-name.txt'      | 'bucket#package=da%2fta&path=dir%2ffile-name.txt'
        'bucket'                    | 'some%2ffile-name.txt'      | 'bucket#path=some%2ffile-name.txt'
    }

    @Ignore
    void 'should validate subpath: #expected'() {
        expect:
        pathify(path).subpath(from, to) == pathify(expected)
        where:
        path                                             | from  | to    | expected
        'bucket#package=some%2fbig%2fdata%2ffile.txt'    | 0     | 1     | 'data'
        'bucket#path=data%2ffile.txt'                    | 0     | 2     | 'data/file.txt'
        'bucket#package=some%2fbig&path=data%2ffile.txt' | 1     | 2     | 'file.txt'
    }

    @Unroll
    void 'should validate startsWith: #prefix'() {
        expect:
        pathify(path).startsWith(prefix) == expected
        pathify(path).startsWith(pathify(prefix)) == expected

        where:
        path                         | prefix                | expected
        'bucket#package=s/d/file.txt'| 'bucket#package=s%2fd'| true
        'bucket#package=s/d/file.txt'| 'bucket#package=s'  | true
        'bucket#package=s/d/file.txt'| 'bucket'            | true
        'bucket#package=s/d/file.txt'| 'file.txt'          | false
        'data%2ffile.txt'            | 'data'              | true
        'data%2ffile.txt'            | 'file.txt'          | false
    }

    @Unroll
    void 'should validate endsWith'() {
        expect:
        pathify(path).endsWith(suffix) == expected
        //pathify(path).endsWith(pathify(suffix)) == expected

        where:
        path             | suffix            | expected
        sub_path         | 'file.txt'        | true
        sub_path         | 'f%2ffile.txt'    | true
        sub_path         | '/f%2ffile.txt'   | false
        sub_path         | 'bucket'          | false
        'data%2ffile.txt'| 'data'            | false
        'data%2ffile.txt'| 'file.txt'        | true
    }

    @Unroll
    void 'should validate normalise'() {
        expect:
        pathify(path).normalize() == pathify(expected)
        where:
        path                              | expected
        'bucket#path=s/d/file.txt'        | 'bucket#path=s/d/file.txt'
        'bucket#path=some%2f..%2ffile.txt'| 'bucket#path=file.txt'
        'file.txt'                        | 'file.txt'
    }

    @Unroll
    void 'should validate resolveSibling: #path' () {
        expect:
        pathify(base).resolveSibling(path) == pathify(expected)

        where:
        base                        | path                          | expected
        'bucket#path=some%2fpath'   | 'file-name.txt'               | 'bucket#path=some%2ffile-name.txt'
        'bucket#path=data'          | 'other%2ffile-name.txt'       | 'bucket#path=other%2ffile-name.txt'
        'bucket'                    | 'some%2ffile-name.txt'        | 'bucket#path=some%2ffile-name.txt'
    }

    @Unroll
    void 'should validate relativize' () {
        expect:
        pathify(path).relativize(pathify(other)).toString() == pathify(expected).toString()
        where:
        path                              | other                                      | expected
        'bucket#package=so%2fme'          | 'bucket#package=so%2fme%2fdata%2ffile.txt' | 'data%2ffile.txt'
        'bucket#package=so%2fme%2fdata'   | 'bucket#package=so%2fme%2fdata%2ffile.txt' | 'file.txt'
        'bucket#package=so%2fme&path=foo' | 'bucket#package=so%2fme&path=foo%2fbar'    | 'bar'
    }
}
