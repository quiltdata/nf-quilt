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
import nextflow.quilt.QuiltProduct

import spock.lang.Ignore
import spock.lang.IgnoreIf
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
    private final static String TEST_URL = PACKAGE_URL + "&path=README.md"

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
        Files.exists(qpath.localPath())
        when:
        qpath.deinstall()
        then:
        !Files.exists(qpath.localPath())
        /* when:
        Files.readAttributes(qpath, BasicFileAttributes)
        then:
        thrown(java.nio.file.NoSuchFileException) */
    }

    @Ignore()
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
        def outPath = Paths.get(opkg.packageDest().toString(), QuiltProduct.README_FILE)
        Files.writeString(outPath, "Time: ${timestamp}")
        expect:
        Files.exists(outPath)
        when:
        opkg.push()
        then:
        thrown(java.io.IOException)
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
        def outPath = Paths.get(opkg.packageDest().toString(), QuiltProduct.README_FILE)
        Files.writeString(outPath, "Time: ${timestamp}")
        expect:
        Files.exists(outPath)
        opkg.push()
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
        opkg.push('msg', meta)
    }

    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should fail if invalid workflow'() {
        given:
        String pkgName = "workflow-bad-${timestamp}"
        QuiltPackage bad_wf = writeablePackage(pkgName, 'missing-workflow')
        when:
        bad_wf.push('missing-workflow first time', [:])
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)
    }

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

        when:
        good_wf.push('empty meta', [:])
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        when:
        good_wf.push('bad_meta', bad_meta)
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        expect:
        good_wf.push('my-workflow', meta)
    }

}
