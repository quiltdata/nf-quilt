/* groovylint-disable MethodName, PublicMethodsBeforeNonPublicMethods */
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
package nextflow.quilt

import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.quilt.jep.QuiltPackage

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileDynamic

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@CompileDynamic
class QuiltPkgTest extends QuiltSpecification {

    private static final List<String> CONTENTS = [
        'COPY_THIS.md',
        'a_folder/THING_ONE.md',
        'a_folder/THING_TWO.md',
    ]

    private static QuiltPackage GetPackage(String suffix) {
        String baseURI = SpecURI().replace('source', suffix)
        QuiltPathFactory factory = new QuiltPathFactory()
        QuiltPath qpath = factory.parseUri(baseURI)
        println("Parsed: $qpath")
        QuiltPackage pkg = qpath.pkg()
        println("Package: $pkg")
        return pkg
    }

    static String manifestVersion() {
        String subPath = 'src/resources/META-INF/MANIFEST.MF'
        File manifestFile = new File(subPath)
        def manifest = new java.util.jar.Manifest(new FileInputStream(manifestFile))
        def attrs = manifest.getMainAttributes()
        String version = attrs.getValue('Plugin-Version')
        return version
    }

    static String destPackage() {
        return 'dest-' + manifestVersion()
    }

    void 'should return a package for a valid suffix'() {
        when:
        QuiltPackage pkg = GetPackage('source')
        then:
        pkg != null
    }

    void 'should get a valid version'() {
        given:
        String version = manifestVersion()
        expect:
        version
        version != '0.0.0'
    }

    void 'should confirm contents of source URI'() {
        when:
        QuiltPackage pkg = GetPackage('source')
        Path out = pkg.packageDest()
        then:
        pkg.install()
        then:
        Files.exists(out.resolve(file))
        where:
        file << CONTENTS
    }

    void 'should confirm contents of dest URI'() {
        when:
        QuiltPackage pkg = GetPackage(destPackage())
        Path out = pkg.packageDest()
        then:
        pkg.install(true)
        then:
        Files.exists(out.resolve("inputs/$file"))
        where:
        file << CONTENTS
    }

}
