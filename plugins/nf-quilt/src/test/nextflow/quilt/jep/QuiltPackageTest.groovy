/* groovylint-disable MethodName */
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
package nextflow.quilt.jep

import nextflow.quilt.QuiltSpecification
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.quilt.nio.QuiltPath

import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Unroll
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileDynamic
class QuiltPackageTest extends QuiltSpecification {

    private final static String PACKAGE_URL = 'quilt+s3://quilt-example#package=examples%2fsmart-report@d68a7e9'
    private final static String TEST_URL = PACKAGE_URL + '&path=README.md'

    private QuiltPathFactory factory
    private QuiltPath qpath
    private QuiltPackage pkg

    void setup() {
        factory = new QuiltPathFactory()
        qpath = factory.parseUri(TEST_URL)
        pkg = qpath.pkg()
    }

    void 'should create unique Package for associated Paths'() {
        given:
        def pkgPath = qpath.getJustPackage()
        def pkg2 = pkgPath.pkg()

        expect:
        pkg != null
        pkg.toString() == 'QuiltPackage.quilt_example_examples_smart_report'
        pkgPath.toUriString() == PACKAGE_URL
        pkg == pkg2
    }

    @Unroll
    void 'should find relativeChildren of complete folders'() {
        given:
        Path path = Paths.get(new URI(PACKAGE_URL))
        when:
        QuiltPackage pkg = path.pkg()
        then:
        pkg.relativeChildren(subpath).size() == expected_size
        
        where:
        subpath               | expected_size
        ''                    | 8
        '.ipynb_checkpoints'  | 1
        '.ipynb_checkpoints/' | 1
        '.ipynb'              | 1
        '.ipynb/'             | 0

    }

    void 'should distinguish Packages with same name in different Buckets '() {
        given:
        def url2 = TEST_URL.replace('quilt-', 'quilted-')
        def qpath2 = factory.parseUri(url2)
        def pkg2 = qpath2.pkg()

        expect:
        TEST_URL != url2
        pkg != pkg2
        pkg.toString() != pkg2.toString()

        !Files.exists(qpath2.localPath())
    }

    void 'should create an install folder '() {
        given:
        Path installPath = pkg.packageDest()
        String tmpDirsLocation = System.getProperty('java.io.tmpdir')
        expect:
        installPath.toString().startsWith(tmpDirsLocation)
        Files.exists(installPath)
    }

    void 'should copy temp files into install folder'() {
        given:
        String filename = 'test.txt'
        Path installPath = pkg.packageDest()
        Path tempFile = File.createTempFile('test', '.txt').toPath()
        Path installedFile = Paths.get(installPath.toString(), filename)
        expect:
        Files.exists(tempFile)
        Files.exists(installPath)
        !Files.exists(installedFile)
        Files.copy(tempFile, installedFile)
        Files.exists(installedFile)
    }

    void 'should copy package files to temp Path'() {
        given:
        Path installPath = pkg.packageDest()
        expect:
        Files.exists(installPath)
        Files.isDirectory(installPath)
        Files.readAttributes(installPath, BasicFileAttributes)
    }


    void 'should get attributes for package folder'() {
        given:
        def root = qpath.getRoot()
        def qroot = factory.parseUri(PACKAGE_URL)
        expect:
        root == qroot
        qroot.isJustPackage()
        Files.isDirectory(qroot)
        Files.readAttributes(qroot, BasicFileAttributes)
    }

    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should successfully install files and get attributes'() {
        expect:
        pkg.install()
        pkg.isInstalled()
        Files.exists(qpath.localPath())
        Files.readAttributes(qpath, BasicFileAttributes)
    }


    void 'should return null on failed install'() {
        given:
        def url2 = TEST_URL.replace('quilt-', 'quilted-')
        def qpath2 = factory.parseUri(url2)
        def pkg2 = qpath2.pkg()

        expect:
        pkg2.install() == null
    }

    @IgnoreIf({ System.getProperty('os.name').contains('indows') })
    void 'should deinstall files'() {
        expect:
        Files.exists(qpath.localPath(true))
        Files.readAttributes(qpath, BasicFileAttributes)
        when:
        qpath.deinstall()
        then:
        !Files.exists(qpath.localPath(false))
    }

    void 'should iterate over installed files '() {
        given:
        def root = qpath.getRoot()
        def qroot = factory.parseUri(PACKAGE_URL)

        expect:
        root
        qroot
        root == qroot
        Files.isDirectory(qroot)
        pkg.install()
        !Files.isDirectory(qpath)
    }

    void 'should fail pushing new files to read-only bucket '() {
        given:
        def qout = factory.parseUri(TEST_URL)
        def opkg = qout.pkg()
        def outPath = Paths.get(opkg.packageDest().toString(), 'README.md')
        Files.writeString(outPath, "Time: ${timestamp}")
        expect:
        Files.exists(outPath)
        opkg.push() != 0
    }

    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should get writeable package '() {
        given:
        QuiltPackage opkg = writeablePackage('observer')
        expect:
        opkg
        opkg.is_force()
        opkg.bucket == writeBucket
        opkg.packageName.contains('test/observer')
    }

    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should succeed pushing new files to writeable bucket '() {
        given:
        QuiltPackage opkg = writeablePackage('observer')
        def outPath = Paths.get(opkg.packageDest().toString(), 'README.md')
        Files.writeString(outPath, "Time: ${timestamp}")
        expect:
        Files.exists(outPath)
        opkg.push() == 0
        opkg.reset()
        !Files.exists(outPath)
        opkg.install() // returns Path
        Files.exists(outPath)
    }

    // TODO: ensure metadata is correctly inserted into package
    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should not fail pushing invalid metadata '() {
        given:
        QuiltPackage opkg = writeablePackage('observer')
        Map meta =  ['key': "val=\'(key)\'"]
        expect:
        opkg.push('msg', meta) == 0
    }

    @Ignore('QuiltCore-java does not support workflows yet')
    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should fail if invalid workflow'() {
        given:
        String pkgName = "workflow-bad-${timestamp}"
        QuiltPackage bad_wf = writeablePackage(pkgName, 'missing-workflow')
        expect:
        bad_wf.push('missing-workflow first time', [:]) == 1
    }

    @Ignore('QuiltCore-java does not support workflows yet')
    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should fail push if unsatisfied workflow'() {
        given:
        Map meta =  [
            'Name': 'QuiltPackageTest',
            'Owner': 'Ernest',
            'Date': '1967-10-08',
            'Type': 'NGS'
        ]
        Map bad_meta = meta + ['Type': 'Workflow']
        QuiltPackage good_wf = writeablePackage('workflow-good', 'my-workflow')
        expect:
        good_wf.push('empty meta', [:]) == 1
        good_wf.push('bad_meta', bad_meta) == 1
        good_wf.push('my-workflow', meta) == 0
    }

}
