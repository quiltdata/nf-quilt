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
package nextflow.quilt.nio

import nextflow.Session
import nextflow.quilt.QuiltSpecification
import nextflow.quilt.QuiltProduct
import nextflow.quilt.jep.QuiltParser

import groovy.transform.CompileDynamic
import spock.lang.IgnoreIf

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltProductTest extends QuiltSpecification {

    QuiltProduct makeProduct(String query=null) {
        String subURL = query ? fullURL.replace('key=val&key2=val2', query) : fullURL
        Session session = Mock(Session)
        QuiltPath path = QuiltPathFactory.parse(subURL)
        return new QuiltProduct(path, session)
    }

    QuiltProduct makeWriteProduct(Map meta = [:]) {
        String subURL = writeableURL('quilt_product_test') + '&workflow=my-workflow'
        if (meta) {
            String query = QuiltParser.unparseQuery(meta)
            subURL = subURL.replace('#', "?${query}#")
        }
        Session session = Mock(Session)
        QuiltPath path = QuiltPathFactory.parse(subURL)
        return new QuiltProduct(path, session)
    }

    void 'now should generate solid string for timestamp'() {
        when:
        def now = QuiltProduct.now()
        then:
        now
        now.contains('T')
        !now.contains(' ')
    }

    void 'should create from session'() {
        given:
        QuiltProduct product = makeProduct()
        expect:
        product
        !product.shouldSkip('key')
        !product.shouldSkip('missing_key')
        !product.shouldSkip(QuiltProduct.KEY_SKIP)
        !product.shouldSkip(QuiltProduct.KEY_README)
        !product.shouldSkip(QuiltProduct.KEY_META)
    }

    void 'shouldSkip is true if key=SKIP'() {
        given:
        QuiltProduct product = makeProduct('readme=SKIP&metadata=SKIP')
        expect:
        !product.shouldSkip(QuiltProduct.KEY_SKIP)
        product.shouldSkip(QuiltProduct.KEY_README)
        product.shouldSkip(QuiltProduct.KEY_META)

        !makeProduct('?readme=now').shouldSkip()
    }

    void 'does not create README if readme=SKIP'() {
        given:
        QuiltProduct skipREADME = makeProduct('readme=SKIP')
        String text = skipREADME.setupReadme()
        def files = skipREADME.pkg.folder.list().sort()
        expect:
        skipREADME.shouldSkip(QuiltProduct.KEY_README)
        !text
        files.size() == 0
    }

    void 'always creates README if readme!=SKIP'() {
        given:
        String readme_text = 'hasREADME'
        QuiltProduct hasREADME = makeProduct("readme=${readme_text}")
        String text = hasREADME.setupReadme()
        def files = hasREADME.pkg.folder.list().sort()
        expect:
        text == readme_text
        !hasREADME.shouldSkip(QuiltProduct.KEY_README)
        files.size() == 1
    }

    void 'setupSummarize empty if no files are present'() {
        given:
        QuiltProduct product = makeProduct('readme=SKIP')
        product.pkg.reset()
        expect:
        !product.match('*.md')
        product.setupSummarize() == [:]
    }

    void 'should create summarize if files are present'() {
        String readme_text = 'hasREADME'
        QuiltProduct product = makeProduct("readme=${readme_text}")
        product.setupReadme()
        expect:
        product.match('*.md')
        product.setupSummarize()
    }

    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'pushes previous metadata if metadata=SKIP'() {
        given:
        Map meta = [
            'Name': 'QuiltPackageTest',
            'Owner': 'Ernest',
            'Date': '1967-10-08',
            'Type': 'NGS'
        ]
        Map bad_meta = meta + ['Type': 'Workflow']
        Map skip_meta = ['metadata': 'SKIP']

        expect:
        makeWriteProduct().pubStatus == 1 // no metadata
        makeWriteProduct(meta).pubStatus == 0 // valid metadata
        makeWriteProduct().pubStatus == 1 // invalid default metadata
        makeWriteProduct(bad_meta).pubStatus == 1 // invalid explicit metadata
        makeWriteProduct(skip_meta).pubStatus == 1 // no default metadata
        // NOTE: push does NOT update local registry
        makeWriteProduct().pkg.install() // try to merge existing metadata
        makeWriteProduct(skip_meta).pubStatus == 1 // still fails on implicit metadata
    }

}
