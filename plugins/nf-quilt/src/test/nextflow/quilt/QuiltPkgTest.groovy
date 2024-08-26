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
        String baseURI = 'quilt+s3://udp-spec#package=nf-quilt/'
        QuiltPathFactory factory = new QuiltPathFactory()
        QuiltPath qpath = factory.parseUri(baseURI + suffix)
        println("Parsed: $qpath")
        QuiltPackage pkg = qpath.pkg()
        println("Package: $pkg")
        return pkg
    }

    /* static String destURI() {
        String manifestVersion = '0.7.17'  // FIXME: Get from manifest
        return baseURI + 'dest_' + manifestVersion
    }
    */

    void 'should return a package for a valid suffix'() {
        when:
        QuiltPackage pkg = GetPackage('source')
        then:
        pkg != null
    }

    void 'should confirm contents of source URI'() {
        when:
        QuiltPackage pkg = GetPackage('source')
        then:
        Path out = pkg.packageDest()
        pkg.install()
        then:
        Files.exists(out.resolve(file))
        where:
        file << CONTENTS
    }

}
