package nextflow.quilt.nio
import nextflow.quilt.QuiltSpecification

import spock.lang.Shared
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class QuiltPathTest extends QuiltSpecification {

    @Shared
    Map<String,QuiltFileSystem> cache = new HashMap<>()

    private QuiltPath pathify(String path) {
        def url = QuiltPathFactory.PREFIX + path
        return Paths.get(new URI(url))
    }

    @Unroll
    def 'should create a path: #objectName'() {

        when:
        def path = pathify(objectName)
        then:
        path.toString() == expected
        path.directory == dir

        where:
        objectName              | expected              | dir
        '/bucket/a/b/file.txt'  | '/bucket/a/b/file.txt'    | false
        '/bucket/a/b/c'         | '/bucket/a/b/c'       | false
        '/bucket/a/b/c/'        | '/bucket/a/b/c'       | true
        '/bucket'               | '/bucket'             | true
        '/bucket/'              | '/bucket'             | true

    }

    def 'should validate equals and hashCode'() {

        when:
        def path1 = pathify('/bucket/so/me/file-name.txt')
        def path2 = pathify('/bucket/so/me/file-name.txt')
        def path3 = pathify('/bucket/ot/her/file-name.txt')
        def path4 = pathify('/bucket2/so/me/file-name.txt')

        then:
        path1 == path2
        path1 != path3
        path1 != path4

        path1.hashCode() == path2.hashCode()
        path1.hashCode() != path3.hashCode()

        when:
        def rel1 = pathify('file.txt')
        def rel2 = pathify('file.txt')
        then:
        rel1 == rel2
        rel1.hashCode() == rel2.hashCode()
    }

    def 'should validate isAbsolute'() {
        when:
        def path1 = pathify('/some/file-name.txt')
        def path2 = pathify('file-name.txt')

        then:
        path1.isAbsolute()
        !path2.isAbsolute()
    }

    def 'should validate getRoot'() {
        when:
        def path1 = pathify('/bucket/so/me/file-name.txt')
        def path2 = pathify('file-name.txt')

        then:
        path1.root == pathify('/bucket')
        path1.root.toString() == '/bucket'
        path2.root == null
    }

    @Unroll
    def 'should return bucket name and id' () {
        when:
        def p = pathify(path)
        then:
        //p.isContainer() == expected
        p.bucket() == bucketName

        where:
        path                                    | expected  |  bucketName
        '/nxf-bucket/file-name.txt'             | false     | 'nxf-bucket'
        '/nxf-bucket/some/data/file-name.txt'   | false     | 'nxf-bucket'
        'file-name.txt'                         | false     | null
        '/nxf-bucket'                           | true      | 'nxf-bucket'
    }

    @Unroll
    def 'should validate getFileName'() {
        expect:
        pathify(path).getFileName() == pathify(fileName)

        where:
        path                                    | fileName
        '/nxf-bucket/file-name.txt'             | 'file-name.txt'
        '/nxf-bucket/some/data/file-name.txt'   | 'file-name.txt'
        'file-name.txt'                         | 'file-name.txt'
        '/nxf-bucket'                           | 'nxf-bucket'
    }


    @Unroll
    def 'should validate getParent: #path'() {
        expect:
        pathify(path).getParent() == (parent ? pathify(parent) : null)

        where:
        path                                    | parent
        '/nxf-bucket/some/data/file-name.txt'   | '/nxf-bucket/some/data'
        '/nxf-bucket/file-name.txt'             | '/nxf-bucket'
        'file-name.txt'                         | null
        '/nxf-bucket'                           | null
    }

    @Unroll
    def 'should validate toUri: #uri'() {
        expect:
        pathify(path).toUri() == new URI(uri)
        pathify(path).toUri().scheme == new URI(uri).scheme
        pathify(path).toUri().authority == new URI(uri).authority
        pathify(path).toUri().path == new URI(uri).path

        where:
        path                            | uri
        '/alpha/so/me/file.txt'          | 'quilt+s3://alpha/so/me/file.txt'
        '/alpha/'                       | 'quilt+s3://alpha'
        '/alpha'                        | 'quilt+s3://alpha'
        'some-file.txt'                 | 'quilt+s3:some-file.txt'
    }


    @Unroll
    def 'should validate toString: #path'() {
        expect:
        pathify(path).toString() == str

        where:
        path                    | str
        '/alpha/so/me/file.txt'  | '/alpha/so/me/file.txt'
        '/alpha'                | '/alpha'
        '/alpha/'               | '/alpha'
        'some-file.txt'         | 'some-file.txt'
    }


    @Unroll
    def 'should validate resolve: base:=#base; path=#path'() {

        expect:
        pathify(base).resolve(path) == pathify(expected)
        pathify(base).resolve( pathify(path) ) == pathify(expected)

        where:
        base                        | path                          | expected
        '/nxf-bucket/some/path'     | 'file-name.txt'               | '/nxf-bucket/some/path/file-name.txt'
        '/nxf-bucket/data'          | 'path/file-name.txt'          | '/nxf-bucket/data/path/file-name.txt'
        '/bucket/data'              | '/other/file-name.txt'        | '/other/file-name.txt'
        '/nxf-bucket'               | 'some/file-name.txt'          | '/nxf-bucket/some/file-name.txt'
    }

    @Unroll
    def 'should validate subpath: #expected'() {
        expect:
        pathify(path).subpath(from, to) == pathify(expected)
        where:
        path                                | from  | to    | expected
        '/bucket/some/big/data/file.txt'    | 0     | 2     | 'bucket/some'
        '/bucket/some/big/data/file.txt'    | 1     | 2     | 'some'
        '/bucket/some/big/data/file.txt'    | 4     | 5     | 'file.txt'
    }

    @Unroll
    def 'should validate startsWith'() {
        expect:
        pathify(path).startsWith(prefix) == expected
        pathify(path).startsWith(pathify(prefix)) == expected

        where:
        path                            | prefix            | expected
        '/bucket/some/data/file.txt'    | '/bucket/some'    | true
        '/bucket/some/data/file.txt'    | '/bucket/'        | true
        '/bucket/some/data/file.txt'    | '/bucket'         | true
        '/bucket/some/data/file.txt'    | 'file.txt'        | false
        'data/file.txt'                 | 'data'            | true
        'data/file.txt'                 | 'file.txt'        | false
    }

    def 'should validate endsWith'() {
        expect:
        pathify(path).endsWith(suffix) == expected
        pathify(path).endsWith(pathify(suffix)) == expected

        where:
        path                            | suffix            | expected
        '/bucket/some/data/file.txt'    | 'file.txt'        | true
        '/bucket/some/data/file.txt'    | 'data/file.txt'   | true
        '/bucket/some/data/file.txt'    | '/data/file.txt'  | false
        '/bucket/some/data/file.txt'    | '/bucket'         | false
        'data/file.txt'                 | 'data'            | false
        'data/file.txt'                 | 'file.txt'        | true
    }


    def 'should validate normalise'() {
        expect:
        pathify(path).normalize() == pathify(expected)
        where:
        path                            | expected
        '/bucket/some/data/file.txt'    | '/bucket/some/data/file.txt'
        '/bucket/some/../file.txt'      | '/bucket/file.txt'
        'bucket/some/../file.txt'       | 'bucket/file.txt'
        'file.txt'                      | 'file.txt'

    }

    @Unroll
    def 'should validate resolveSibling' () {
        expect:
        pathify(base).resolveSibling(path) == pathify(expected)
        pathify(base).resolveSibling(pathify(path)) == pathify(expected)

        where:
        base                    | path                          | expected
        '/bucket/some/path'     | 'file-name.txt'               | '/bucket/some/file-name.txt'
        '/bucket/data'          | 'path/file-name.txt'          | '/bucket/path/file-name.txt'
        '/bucket/data'          | '/other/file-name.txt'        | '/other/file-name.txt'
        '/bucket'               | 'some/file-name.txt'          | '/some/file-name.txt'
    }

    @Unroll
    def 'should validate relativize' () {
        expect:
        pathify(path).relativize(pathify(other)) == pathify(expected)
        where:
        path                    | other                                 | expected
        '/nxf-bucket/some/path' | '/nxf-bucket/some/path/data/file.txt' | 'data/file.txt'
    }

    def 'should validate toAbsolutePath' () {
        expect:
        pathify('/bucket/da/ta/file.txt').toAbsolutePath() == pathify('/bucket/da/ta/file.txt')

        when:
        pathify('file.txt').toAbsolutePath()
        then:
        thrown(UnsupportedOperationException)
    }

    def 'should validate toRealPath' () {
        expect:
        pathify('/bucket/da/ta/file.txt').toRealPath() == pathify('/bucket/da/ta/file.txt')

        when:
        pathify('file.txt').toRealPath()
        then:
        thrown(UnsupportedOperationException)
    }

    def 'should validate iterator' () {
        given:
        def itr = pathify('/nxf-bucket/so/me/file-name.txt').iterator()
        expect:
        itr.hasNext()
        itr.next() == pathify('nxf-bucket')
        itr.hasNext()
        itr.next() == pathify('some')
        itr.hasNext()
        itr.next() == pathify('file-name.txt')
        !itr.hasNext()

    }

}
