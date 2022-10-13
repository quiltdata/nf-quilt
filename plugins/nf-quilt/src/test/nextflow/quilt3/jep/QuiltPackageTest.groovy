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
import nextflow.quilt.nio.QuiltFileAttributesView

import nextflow.Global
import nextflow.Session
import spock.lang.Unroll
import spock.lang.Shared
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import groovy.util.logging.Slf4j

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
class QuiltPackageTest extends QuiltSpecification {
    QuiltPathFactory factory
    QuiltPath qpath
    QuiltPackage pkg

    static String pkg_url = 'quilt3://quilt-dev-null/test/nf-quilt/'
    static String url = pkg_url + 'README.md?tophash=b744ee498f'
    static String out_url = 'quilt3://quilt-ernest-staging/nf-quilt/test'

    def setup() {
        factory = new QuiltPathFactory()
        qpath = factory.parseUri(url)
        pkg = qpath.pkg()
    }

    @Unroll
    def 'should create unique Package for associated Paths' () {
        given:
        def pkgPath = qpath.getPackage()
        def pkg2 = pkgPath.pkg()

        expect:
        pkg != null
        pkg.toString() == "quilt_dev_null_test_nf_quilt"
        pkgPath.toUriString() == pkg_url
        pkg == pkg2
    }

    def 'should distinguish Packages with same name in different Buckets ' () {
        given:
        def url2 = url.replace('-dev','-dev2')
        def qpath2 = factory.parseUri(url2)
        def pkg2 = qpath2.pkg()

        expect:
        url != url2
        pkg != pkg2
        pkg.toString() != pkg2.toString()

        !Files.exists(qpath2.installPath())
    }

    def 'should create an install folder ' () {
        given:
        Path installPath = pkg.installPath()
        String tmpDirsLocation = System.getProperty("java.io.tmpdir")
        expect:
        installPath.toString().startsWith(tmpDirsLocation)
        Files.exists(installPath)
    }

    def 'should get attributes for package folder' () {
        given:
        def qroot = factory.parseUri(pkg_url)
        expect:
        qroot.isPackage()
        Files.readAttributes(qroot, BasicFileAttributes)
    }

    def 'should pre-install files and get attributes' () {
        expect:
        pkg.isInstalled()
        Files.exists(qpath.installPath())
        Files.readAttributes(qpath, BasicFileAttributes)
    }

    def 'should deinstall files' () {
        given:
        Files.exists(qpath.installPath())
        expect:
        qpath.deinstall()
        !Files.exists(qpath.installPath())
        when:
        Files.readAttributes(qpath, BasicFileAttributes)
        then:
        thrown(java.nio.file.NoSuchFileException)
    }

    def 'should iterate over installed files ' () {
        given:
        def root = qpath.getRoot()
        def qroot = factory.parseUri(pkg_url)

        expect:
        root
        qroot
        root == qroot
        Files.isDirectory(qroot)
        pkg.install()
        //vs!Files.isDirectory(qpath)
    }

    def 'should write new files back to bucket ' () {
        given:
        def cleanDate = QuiltPackage.today()
        def qout = factory.parseUri(out_url)
        def opkg = qpath.pkg()
        def outPath = Paths.get(opkg.installPath().toString(), "${cleanDate}.txt")
        // remove path
        // re-install package
        // verify file exists
        Files.writeString(outPath, cleanDate);
        expect:
        Files.exists(outPath)
        //opkg.push()
        //opkg.uninstall()
        //!Files.exists(outPath)
        //pkg.isInstalled()
    }


    def 'Package should return Attributes IFF the file exists' () {
    }

}
