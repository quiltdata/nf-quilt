/* groovylint-disable MethodName */
package nextflow.quilt.nio

import nextflow.quilt.QuiltSpecification
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage

import java.nio.file.Path
import java.nio.file.Paths
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic
import java.nio.file.ProviderMismatchException

import spock.lang.Unroll
import spock.lang.Ignore

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileDynamic
class QuiltPathTest extends QuiltSpecification {

    private static final String BKT = 'bucket'
    private static final String PKG_URL = "${QuiltParser.PREFIX}${BKT}#package=so%2fme"
    private static final String SUB_PATH = "${BKT}#package=s/d&path=f%2ffile.txt"

    QuiltPath pathify(String path) {
        if (!path.contains(BKT)) {
            QuiltPath pkgPath = QuiltPathFactory.parse(PKG_URL)
            QuiltFileSystem qfs = pkgPath.getFileSystem()
            return qfs.getPath(path)
        }
        String url = QuiltParser.PREFIX + path
        return QuiltPathFactory.parse(url)
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
    void 'should return fsName and isJustPackage'() {
        when:
        Path p = pathify(path)
        then:
        p.isJustPackage() == expected
        p.getFileSystem().toString() == fsName

        where:
        path                               | expected  |  fsName
        'bucket'                           | true      | 'bucket.package.default'
        'bucket#package=sum/data'          | true      | 'bucket.sum.data'
        'bucket#path=file.txt'             | false     | 'bucket.package.default'
        'bucket#package=sum/data/file.txt' | false     | 'bucket.sum.data'
        '#path=file-name.txt'              | false     | 'bucket.so.me'
    }

    void 'should validate getFileName'() {
        expect:
        pathify(path).getFileName().toString() == fileName

        where:
        path                                   | fileName
        'bucket'                               | 'bucket'
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
        'bucket#path=some%2fdata%2ffile.txt' | 'bucket#path=some%2fdata'
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

        where:
        base                        | path                        | expected
        'bucket#package=so%2fme'    | 'file-name.txt'             | 'bucket#package=so%2fme&path=file-name.txt'
        'bucket#package=da/ta'      | 'dir%2ffile-name.txt'       | 'bucket#package=da%2fta&path=dir%2ffile-name.txt'
        'bucket#package=da/ta'      | '/dir%2ffile-name.txt'      | 'bucket#package=da%2fta&path=dir%2ffile-name.txt'
        'bucket'                    | 'some%2ffile-name.txt'      | 'bucket#path=some%2ffile-name.txt'
    }

    void 'should resolve another QuiltPath'() {
        given:
        QuiltPath basePath = pathify('bucket#package=so%2fme')
        QuiltPath otherPath = pathify('bucket#package=da/ta')

        expect:
        basePath.resolve(otherPath) == otherPath
    }

    void 'should resolve error for non-QuiltPath'() {
        given:
        QuiltPath basePath = pathify('bucket#package=so%2fme')
        Path nonQuiltPath = Paths.get('file-name.txt')
        when:
        basePath.resolve(nonQuiltPath)

        then:
        thrown ProviderMismatchException
    }

    @Ignore('FIXME: test subpath in QuiltParser first')
    void 'should validate subpath: #expected'() {
        expect:
        pathify(path).subpath(from, to).getPath() == expected
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
        path                          | prefix                | expected
        'bucket#package=s/d/file.txt' | 'bucket#package=s%2fd' | true
        'bucket#package=s/d/file.txt' | 'bucket#package=s' | true
        'bucket#package=s/d/file.txt' | 'bucket' | true
        'bucket#package=s/d/file.txt' | 'file.txt' | false
        'data%2ffile.txt'             | 'data'              | true
        'data%2ffile.txt'             | 'file.txt'          | false
    }

    @Unroll
    void 'should validate endsWith'() {
        expect:
        pathify(path).endsWith(suffix) == expected
        //pathify(path).endsWith(pathify(suffix)) == expected

        where:
        path              | suffix            | expected
        SUB_PATH          | 'file.txt'        | true
        SUB_PATH          | 'f%2ffile.txt'    | true
        SUB_PATH          | '/f%2ffile.txt'   | false
        SUB_PATH          | 'bucket'          | false
        'data%2ffile.txt' | 'data'            | false
        'data%2ffile.txt' | 'file.txt'        | true
    }

    @Unroll
    void 'should validate normalise'() {
        expect:
        pathify(path).normalize() == pathify(expected)
        where:
        path                               | expected
        'bucket#path=s/d/file.txt'         | 'bucket#path=s/d/file.txt'
        'bucket#path=some%2f..%2ffile.txt' | 'bucket#path=file.txt'
        'file.txt'                         | 'file.txt'
    }

    @Unroll
    void 'should validate resolveSibling: #path'() {
        expect:
        pathify(base).resolveSibling(path) == pathify(expected)

        where:
        base                        | path                          | expected
        'bucket#path=some%2fpath'   | 'file-name.txt'               | 'bucket#path=some%2ffile-name.txt'
        'bucket#path=data'          | 'other%2ffile-name.txt'       | 'bucket#path=other%2ffile-name.txt'
        'bucket'                    | 'some%2ffile-name.txt'        | 'bucket#path=some%2ffile-name.txt'
    }

    String based(String fragment = '') {
        return "bucket#package=so%2fme${fragment}"
    }

    String sepJoin(String... parts) {
        if (QuiltPackage.osSep() == '/') {
            return parts.join('%2f')
        }
        return parts.join('%5c')
    }
    @Unroll
    void 'should validate relativize'() {
        expect:
        pathify(path).relativize(pathify(other)).toString() == pathify(expected).toString()
        where:
        path               | other                       | expected
        based()            | based('%2fdata%2ffile.txt') | sepJoin('data', 'file.txt')
        based('%2fdata')   | based('%2fdata%2ffile.txt') | 'file.txt'
        based('&path=foo') | based('&path=foo%2fbar')    | 'bar'
    }

    void 'should error on relativize if no common path'() {
        when:
        pathify('bucket#package=so%2fme&path=bar').relativize(pathify('bucket#package=so%2fme&path=foo'))
        then:
        thrown IllegalArgumentException
    }

    void 'should reconstruct full URLs'() {
        given:
        QuiltPath pkgPath = QuiltPathFactory.parse(PKG_URL)
        QuiltPath fullPath = QuiltPathFactory.parse(testURI)
        expect:
        !pkgPath.toUriString().contains('?')
        fullPath.toUriString().contains('?')
    }

    void 'should return a special-case null bucket on invalid paths'() {
        when:
        QuiltPackage.resetPackageCache()
        QuiltPath path = QuiltPathFactory.parse('quilt+s3://./')
        then:
        path.toString() == '.'
        path.pkg() != null
    }

    void 'should return prior path on invalid paths'() {
        when:
        QuiltPackage.resetPackageCache()
        QuiltPath pkgPath = QuiltPathFactory.parse(PKG_URL)
        QuiltPath path = QuiltPathFactory.parse('quilt+s3://./')
        QuiltPackage prior = pkgPath.pkg()
        then:
        prior
        path.pkg() == prior
    }

    void 'should getNameCount'() {
        expect:
        pathify(path).getNameCount() == expected
        where:
        path | expected
        'bucket#package=so%2fme' | 0
        'bucket#package=so%2fme&path=file-name.txt' | 1
        'bucket#package=so%2fme&path=folder/name.txt' | 2
        'bucket#package=so%2fme&path=folder%2fname.txt' | 2
    }

    void 'should error on getName'() {
        when:
        pathify('bucket#package=so%2fme').getName(-1)
        then:
        thrown UnsupportedOperationException
    }

    void 'should return subPaths'() {
        given:
        Path path = pathify('bucket#package=so%2fme&path=folder/name.txt')
        Path subPath = path.subpath(1, 2)
        expect:
        subPath
        subPath.toString() == 'bucket#package=so%2fme&path=name.txt'
    }

    void 'should match endsWith'() {
        given:
        QuiltPath path = pathify('bucket#package=so%2fme&path=folder/name.txt')
        expect:
        path.endsWith('name.txt')
        !path.endsWith('folder')
    }

    void 'should error on resolveSibling'() {
        when:
        Path path = pathify('bucket#package=so%2fme&path=folder/name.txt')
        pathify('bucket#package=so%2fme').resolveSibling(path)
        then:
        thrown UnsupportedOperationException
    }

    void 'should error on toAbsolutePath if not absolute'() {
        when:
        pathify('bucket').toAbsolutePath()
        then:
        thrown UnsupportedOperationException
    }

    void 'should error on toRealPath'() {
        when:
        pathify('bucket#package=so%2fme').toRealPath()
        then:
        thrown UnsupportedOperationException
    }

    void 'should error on toFile'() {
        when:
        pathify('bucket#package=so%2fme').toFile()
        then:
        thrown UnsupportedOperationException
    }

    void 'should error on iterator'() {
        when:
        pathify('bucket#package=so%2fme').iterator()
        then:
        thrown UnsupportedOperationException
    }

    void 'should error on register'() {
        when:
        pathify('bucket#package=so%2fme').register(null)
        then:
        thrown UnsupportedOperationException

        when:
        pathify('bucket#package=so%2fme').register(null, null)
        then:
        thrown UnsupportedOperationException
    }

}
