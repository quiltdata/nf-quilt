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

import spock.lang.IgnoreIf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import groovy.util.logging.Slf4j
import com.quiltdata.quiltcore.Entry
import com.quiltdata.quiltcore.Manifest
import com.quiltdata.quiltcore.key.S3PhysicalKey
import java.security.MessageDigest
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
    private final static PKG = 'aics_mnist'
    private final static String READONLY_URL = "quilt+s3://allencell#package=aics%2f${PKG}&path=${PKG}.ipynb"

    private QuiltPathFactory factory
    private QuiltPath qpath
    private QuiltPackage pkg

    void setup() {
        factory = new QuiltPathFactory()
        qpath = QuiltPathFactory.parse(TEST_URL)
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
        def qpath2 = QuiltPathFactory.parse(url2)
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
        def qroot = QuiltPathFactory.parse(PACKAGE_URL)
        expect:
        root == qroot
        qroot.isJustPackage()
        Files.isDirectory(qroot)
        Files.readAttributes(qroot, BasicFileAttributes)
    }

    void 'should not raise error on null bucket'() {
        given:
        QuiltPackage.resetPackageCache()
        def qpath = QuiltPathFactory.parse('quilt+s3://./')
        def pkg = qpath.pkg()
        expect:
        pkg.isNull()
        !pkg.install()
        !pkg.isInstalled()
        Files.exists(qpath.localPath())
        Files.readAttributes(qpath, BasicFileAttributes)
    }

    void 'should successfully install files and get attributes'() {
        expect:
        pkg.install()
        pkg.isInstalled()
        Files.exists(qpath.localPath())
        Files.readAttributes(qpath, BasicFileAttributes)
    }

    void 'should download the specified path'() {
        given:
        def qpath = QuiltPathFactory.parse(TEST_URL)
        def qpkg = qpath.pkg()
        Path outputFolder = pkg.packageDest()
        Path readmeFile = outputFolder.resolve('README.md')

        expect:
        !qpath.isJustPackage()
        Files.isDirectory(outputFolder)
        //!Files.exists(readmeFile)

        qpkg.install()
        Files.exists(qpath.localPath())
        Files.isRegularFile(qpath.localPath())

        Files.exists(readmeFile)
        Files.isRegularFile(readmeFile)
        Files.isReadable(readmeFile)
        readObject(readmeFile).startsWith('# Quilt Smart Reports')
    }

    void 'should return null on failed implicit install'() {
        given:
        def url2 = TEST_URL.replace('quilt-', 'quilted-')
        def qpath2 = QuiltPathFactory.parse(url2)
        def pkg2 = qpath2.pkg()

        expect:
        pkg2.install(true) == null
    }

    void 'should throw error on explict install'() {
        given:
        def url2 = TEST_URL.replace('quilt-', 'quilted-')
        def qpath2 = QuiltPathFactory.parse(url2)
        def pkg2 = qpath2.pkg()

        when:
        pkg2.install()

        then:
        thrown(RuntimeException)
    }

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

    void 'should iterate over installed files '() {
        given:
        def root = qpath.getRoot()
        def qroot = QuiltPathFactory.parse(PACKAGE_URL)

        expect:
        root
        qroot
        root == qroot
        Files.isDirectory(qroot)
        pkg.install()
    }

    void 'should fail pushing new files to read-only bucket '() {
        given:
        def qout = QuiltPathFactory.parse(READONLY_URL)
        def opkg = qout.pkg()
        opkg.install()
        def outPath = Paths.get(opkg.packageDest().toString(), "${PKG}.ipynb")
        Files.writeString(outPath, "Time: ${timestamp}")
        expect:
        Files.exists(outPath)
        when:
        opkg.push()
        then:
        thrown(RuntimeException)
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should get writeable package '() {
        given:
        QuiltPackage opkg = writeablePackage('observer')
        expect:
        opkg
        opkg.is_force()
        opkg.bucketAccessible
        opkg.getPackageName().contains('test/observer')
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
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
    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should not fail pushing invalid metadata / no workflow'() {
        given:
        QuiltPackage opkg = writeablePackage('observer')
        Map meta =  ['key': "val=\'(key)\'"]
        expect:
        opkg.push('msg', meta)
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should fail push if invalid workflow'() {
        given:
        String pkgName = "workflow-bad-${timestamp}"
        QuiltPackage bad_wf = writeablePackage(pkgName, 'missing-workflow')
        when:
        bad_wf.push('missing-workflow first time', [:])
        then:
        thrown(RuntimeException)
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
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
        thrown(RuntimeException)

        when:
        good_wf.push('bad_meta', bad_meta)
        then:
        thrown(RuntimeException)

        expect:
        good_wf.push('my-workflow', meta)
    }


    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should push published S3 outputs in place'() {
        given: 'two outputs already published under the package prefix'
        String suffix = "remote-${timestamp}"
        QuiltPackage opkg = writeablePackage(suffix)
        String prefix = opkg.getPackageName()
        Path local = Files.createTempFile('out', '.txt')
        Files.writeString(local, 'hashed locally')
        new S3PhysicalKey(writeBucket, "${prefix}/run/local.txt", null).putBytes('hashed locally'.bytes)
        new S3PhysicalKey(writeBucket, "${prefix}/run/dir/remote.txt", null).putBytes('hashed remotely'.bytes)
        // e.g. a shallow outdir: the object is not under the package name
        new S3PhysicalKey(writeBucket, "${prefix}-elsewhere/shallow.txt", null).putBytes('shallow'.bytes)
        opkg.addRemoteEntry('run/local.txt', "${prefix}/run/local.txt", local)
        opkg.addRemoteEntry('run/dir/remote.txt', "${prefix}/run/dir/remote.txt")
        opkg.addRemoteEntry('shallow.txt', "${prefix}-elsewhere/shallow.txt")
        Files.writeString(opkg.packageDest().resolve(QuiltProduct.README_FILE), 'generated')

        when:
        Manifest pushed = opkg.push('remote', [:])
        Map<String,Entry> entries = pushed.getEntries()

        then: 'outputs are referenced where they were published, generated files uploaded'
        entries.keySet() == [QuiltProduct.README_FILE, 'run/dir/remote.txt', 'run/local.txt', 'shallow.txt'] as Set
        s3Key(entries['shallow.txt']) == "${prefix}-elsewhere/shallow.txt"
        s3Key(entries['run/local.txt']) == "${prefix}/run/local.txt"
        s3Key(entries['run/dir/remote.txt']) == "${prefix}/run/dir/remote.txt"
        s3Key(entries[QuiltProduct.README_FILE]) == "${prefix}/${QuiltProduct.README_FILE}"
        entries['run/local.txt'].hash.value == sha256('hashed locally')
        entries['run/dir/remote.txt'].hash.value == sha256('hashed remotely')
        entries['run/dir/remote.txt'].size == 'hashed remotely'.length()
        ((S3PhysicalKey) entries['run/dir/remote.txt'].physicalKey).versionId

        when: 'installing the pushed revision'
        opkg.reset()
        opkg.install()

        then:
        Files.readString(opkg.packageDest().resolve('run/dir/remote.txt')) == 'hashed remotely'
        Files.readString(opkg.packageDest().resolve(QuiltProduct.README_FILE)) == 'generated'

        cleanup:
        Files.deleteIfExists(local)
        opkg?.reset()
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should fail push when a published output is missing'() {
        given:
        QuiltPackage opkg = writeablePackage("remote-missing-${timestamp}")
        opkg.addRemoteEntry('run/never-published.txt', "${opkg.getPackageName()}/run/never-published.txt")
        Files.writeString(opkg.packageDest().resolve(QuiltProduct.README_FILE), 'generated')

        when:
        opkg.push('missing', [:])

        then:
        thrown(RuntimeException)

        cleanup:
        opkg?.reset()
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should validate workflows when pushing published outputs'() {
        given:
        QuiltPackage opkg = writeablePackage("remote-wf-${timestamp}", 'missing-workflow')
        String key = "${opkg.getPackageName()}/run/out.txt"
        new S3PhysicalKey(writeBucket, key, null).putBytes('output'.bytes)
        opkg.addRemoteEntry('run/out.txt', key)
        Files.writeString(opkg.packageDest().resolve(QuiltProduct.README_FILE), 'generated')

        when:
        opkg.push('bad workflow', [:])

        then:
        RuntimeException e = thrown()
        e.cause.class.simpleName == 'WorkflowException'

        cleanup:
        opkg?.reset()
    }

    void 'addRemoteEntry stops trusting the local copy when a key is published twice'() {
        given:
        QuiltPackage opkg = new QuiltPackage(QuiltParser.forUriString('quilt+s3://bkt#package=pre%2fdup'))
        Path first = Paths.get('/work/a/out.txt')
        Path second = Paths.get('/work/b/out.txt')

        when: 'the same file is recorded again (e.g. -resume)'
        opkg.addRemoteEntry('out.txt', 'pre/dup/out.txt', first)
        opkg.addRemoteEntry('out.txt', 'pre/dup/out.txt', first)

        then:
        opkg.getRemoteEntries()['out.txt'].localCopy == first

        when: 'a different file is published to the same key'
        opkg.addRemoteEntry('out.txt', 'pre/dup/out.txt', second)

        then: 'hash the object itself'
        opkg.getRemoteEntries()['out.txt'] == new QuiltPackage.RemoteEntry('pre/dup/out.txt', null)
    }

    private static String s3Key(Entry entry) {
        return ((S3PhysicalKey) entry.physicalKey).key
    }

    private static String sha256(String text) {
        return MessageDigest.getInstance('SHA-256').digest(text.bytes).encodeHex().toString()
    }

}
