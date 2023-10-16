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
import nextflow.quilt.jep.QuiltPackage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import groovy.transform.CompileDynamic
import spock.lang.IgnoreIf
import spock.lang.Unroll

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
        product.setupSummarize() == []
    }

    void 'should create summarize if files are present'() {
        String readme_text = 'hasREADME'
        QuiltProduct product = makeProduct("readme=${readme_text}")
        product.setupReadme()
        product.match('*.md')
        List<Map> quilt_summarize = product.setupSummarize()
        expect:
        quilt_summarize
        quilt_summarize.size() == 1
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

        when:
        makeWriteProduct() // no metadata
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        expect:
        makeWriteProduct(meta) // valid metadata

        when:
        makeWriteProduct() // invalid default metadata
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        when:
        makeWriteProduct(bad_meta) // invalid explicit metadata
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        when:
        makeWriteProduct(skip_meta) // no default metadata
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)

        // NOTE: push does NOT update local registry
        expect:
        makeWriteProduct().pkg.install() // try to merge existing metadata

        when:
        makeWriteProduct(skip_meta) // still fails on implicit metadata
        then:
        thrown(com.quiltdata.quiltcore.workflows.WorkflowException)
    }

    void writeFile(root, filename) {
        Path outPath = Paths.get(root, filename)
        outPath.getParent().toFile().mkdirs()
        Files.writeString(outPath, "#Time, Filename\n${timestamp},${filename}")
        println("writeFile: ${filename} -> ${outPath}")
    }

    int writeFiles(dest) {
        String root  = dest.toString()
        String[] filenames = [
            'SUMMARIZE_ME.md',
            'SUMMARIZE_ME.csv',
            'SUMMARIZE_ME.tsv',
            'SUMMARIZE_ME.html',
            'SUMMARIZE_ME.pdf',
            'SUMMARIZE_ME.xml',
            'multiqc/multiqc_report.html'
        ]
        filenames.each { writeFile(root, it) }
        return filenames.size()
    }

    @Unroll
    @IgnoreIf({ env.WRITE_BUCKET == 'quilt-example' || env.WRITE_BUCKET ==  null })
    void 'should summarize top-level readable files + multiqc '() {
        given:
        String sumURL = writeableURL('summarized')
        QuiltPackage sumPkg = writeablePackage('summarized')
        writeFiles(sumPkg.packageDest())

        and:
        Session session = Mock(Session)
        QuiltPath path = QuiltPathFactory.parse(sumURL)
        QuiltProduct product = new QuiltProduct(path, session)

        expect:
        product.publish()
        sumPkg.install()
        Files.exists(Paths.get(sumPkg.packageDest().toString(), QuiltProduct.SUMMARY_FILE))
    }

}
