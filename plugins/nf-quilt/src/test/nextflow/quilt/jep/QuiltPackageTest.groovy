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

    private final static String packageURL = 'quilt+s3://quilt-example#package=examples%2fsmart-report@d68a7e9'
    private final static String url = packageURL + '&path=README.md'
    private final static String out_url = 'quilt+s3://quilt_dev_null#package=nf-quilt%2ftest'

    private QuiltPathFactory factory
    private QuiltPath qpath
    private QuiltPackage pkg

    def setup() {
        factory = new QuiltPathFactory()
        qpath = factory.parseUri(url)
        pkg = qpath.pkg()
    }

    void 'should create unique Package for associated Paths'() {
        given:
        def pkgPath = qpath.getJustPackage()
        def pkg2 = pkgPath.pkg()

        expect:
        pkg != null
        pkg.toString() == 'quilt_example_examples_smart_report'
        pkgPath.toUriString() == packageURL
        pkg == pkg2
    }

    void 'should distinguish Packages with same name in different Buckets '() {
        given:
        def url2 = url.replace('quilt-', 'quilted-')
        def qpath2 = factory.parseUri(url2)
        def pkg2 = qpath2.pkg()

        expect:
        url != url2
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
        def qroot = factory.parseUri(packageURL)
        expect:
        root == qroot
        qroot.isJustPackage()
        Files.isDirectory(qroot)
        Files.readAttributes(qroot, BasicFileAttributes)
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    void 'should pre-install files and get attributes'() {
        expect:
        pkg.install()
        pkg.isInstalled()
        Files.exists(qpath.localPath())
        Files.readAttributes(qpath, BasicFileAttributes)
    }

    @IgnoreIf({ System.getProperty('os.name').contains('ux') })
    void 'should deinstall files'() {
        expect:
        Files.exists(qpath.localPath())
        when:
        qpath.deinstall()
        then:
        !Files.exists(qpath.localPath())
        when:
        Files.readAttributes(qpath, BasicFileAttributes)
        then:
        thrown(java.nio.file.NoSuchFileException)
    }

    void 'should iterate over installed files '() {
        given:
        def root = qpath.getRoot()
        def qroot = factory.parseUri(packageURL)

        expect:
        root
        qroot
        root == qroot
        Files.isDirectory(qroot)
        pkg.install()
    //vs!Files.isDirectory(qpath)
    }

    void 'should write new files back to bucket '() {
        given:
        def cleanDate = QuiltPackage.today()
        //def qout = factory.parseUri(out_url)
        def opkg = qpath.pkg()
        def outPath = Paths.get(opkg.packageDest().toString(), "${cleanDate}.txt")
        // remove path
        // re-install package
        // verify file exists
        Files.writeString(outPath, cleanDate)
        expect:
        Files.exists(outPath)
    //opkg.push()
    //opkg.uninstall()
    //!Files.exists(outPath)
    //pkg.isInstalled()
    }

    void 'Package should return Attributes IFF the file exists'() {
    }

}
