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
package nextflow.quilt

import nextflow.Session
import nextflow.script.WorkflowMetadata
import com.quiltdata.quiltcore.workflows.WorkflowException

import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import groovy.transform.CompileDynamic
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileDynamic
class QuiltProductTest extends QuiltSpecification {

    QuiltProduct makeProductFromUrl(String url, boolean success = false) {
        WorkflowMetadata wf_meta = GroovyMock(WorkflowMetadata) {
            toMap() >> [start:'2022-01-01', complete:'2022-01-02']
        }
        println("makeProductFromUrl: ${url}")
        QuiltPath path = QuiltPathFactory.parse(url)
        QuiltPathify pathify = new QuiltPathify(path)
        Session session = GroovyMock(Session) {
            getWorkflowMetadata() >> wf_meta
            getParams() >> [outdir: url]
            isSuccess() >> success
            config >> [quilt: [meta: [cf_key: 'cf_val']]]
        }
        return new QuiltProduct(pathify, session)
    }

    QuiltProduct makeProduct(String query=null, boolean success = false) {
        if (query == null) {
            return makeProductFromUrl(testURI, success)
        }
        String subURL = uniqueQueryURI(query)
        return makeProductFromUrl(subURL, success)
    }

    QuiltProduct makeConfigProduct(Map qconfig = null) {
        QuiltPath path = QuiltPathFactory.parse(testURI)
        QuiltPathify pathify = new QuiltPathify(path)
        Session session = GroovyMock(Session)
        session.config >> [quilt: qconfig]
        println("makeConfigProduct[$testURI]")
        QuiltProduct product = new QuiltProduct(pathify, session)
        println('makeConfigProduct.done')
        return product
    }

    QuiltProduct makeWriteProduct(Map meta = [:]) {
        String subURL = writeableURI('quilt_product_test') // + '&workflow=universal'
        if (meta) {
            String query = QuiltParser.unparseQuery(meta)
            subURL = subURL.replace('#', "?${query}#")
        }
        return makeProductFromUrl(subURL)
    }

    void 'should generate mocks from makeProduct'() {
        given:
        QuiltProduct product = makeProduct()

        expect:
        product.pkg
        product.session != null
        product.session.getWorkflowMetadata() != null
        product.metadata != null
        product.metadata.key == 'val'
        product.metadata.key2 == 'val2'
    }

    void 'should generate solid string for timestamp from now'() {
        when:
        def now = QuiltProduct.now()
        then:
        now
        now.contains('t')
        !now.contains(' ')
    }

    void 'should create QuiltProduct from session'() {
        given:
        QuiltProduct product = makeProduct()
        expect:
        product
        !product.shouldSkip('key')
        !product.shouldSkip('missing_key')
        !product.shouldSkip(QuiltProduct.KEY_SUMMARIZE)
        !product.shouldSkip(QuiltProduct.KEY_README)
        !product.shouldSkip(QuiltProduct.KEY_META)
    }

    void 'shouldSkip if key is false'() {
        given:
        Map qconfig = [:]
        qconfig[QuiltProduct.KEY_META] = false
        qconfig[QuiltProduct.KEY_README] = false
        qconfig[QuiltProduct.KEY_SUMMARIZE] = false
        println("qconfig: ${qconfig}")
        QuiltProduct product = makeConfigProduct(qconfig)

        expect:
        product.shouldSkip(key)

        where:
        key << [
            // QuiltProduct.KEY_META,
            // QuiltProduct.KEY_README,
            QuiltProduct.KEY_SUMMARIZE
        ]
    }

    void 'overrides config meta with query string'() {
        when:
        println('\nOVERRIDE CONFIG META\n')
        QuiltProduct product = makeProduct()

        then:
        product.metadata
        //product.metadata['cf_key'] == 'cf_val'
        product.metadata['key'] == 'val'
        product.metadata['key2'] == 'val2'

        when:
        QuiltProduct cf_product = makeProduct('cf_key=override&key3=another')

        then:
        cf_product.metadata
        cf_product.metadata['cf_key'] == 'override'
        cf_product.metadata['key3'] == 'another'
    }

    void 'overrides config force with query string'() {
        expect:
        true
    }

    void 'overrides config catalog with query string'() {
        expect:
        true
    }

    void 'skips README if false'() {
        expect:
        true
    }

    void 'overrides default msg with config'() {
        expect:
        true
    }

    void 'match files if present'() {
        QuiltProduct product = makeProduct()
        String filename = "text.txt"
        QuiltProduct.writeString("test", product.pkg, filename)
        expect:
        product.match("*")[0].toString() == filename
        product.match("*.txt")[0].toString() == filename
        !product.match('temp.txt')
    }

    void 'overrides default README with config'() {
        when:
        QuiltProduct defaultREADME = makeProduct()
        String text = defaultREADME.compileReadme('msg')
        def files = defaultREADME.match('*')

        then:
        text.size() > 0
        !defaultREADME.shouldSkip(QuiltProduct.KEY_README)
        files.size() > 0

        when:
        String readme_text = 'hasREADME'
        QuiltProduct hasREADME = makeProduct("readme=${readme_text}")
        text = hasREADME.compileReadme('msg')
        files = hasREADME.match('*')
        then:
        text == readme_text
        !hasREADME.shouldSkip(QuiltProduct.KEY_README)
        files.size() > 0
    }

    void 'writeSummarize empty if no files are present'() {
        given:
        QuiltProduct product = makeProduct('readme=SKIP')
        product.pkg.reset()
        expect:
        !product.match('*.md')
        product.writeSummarize() == []
    }

    void 'should create summarize if files are present'() {
        String readme_text = 'hasREADME'
        QuiltProduct product = makeProduct("readme=${readme_text}")
        product.compileReadme('msg')
        product.match('*.md')
        List<Map> quilt_summarize = product.writeSummarize()
        expect:
        quilt_summarize
        quilt_summarize.size() == 1
    }

    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    void 'should copyFile'() {
        given:
        QuiltProduct product = makeWriteProduct()
        String filename = 'test.md'
        String text = 'test'
        Path src = Paths.get(product.pkg.packageDest().toString(), filename)
        Path dest = Paths.get(product.pkg.packageDest().toString(), 'copy', filename)
        Files.writeString(src, text)

        when:
        product.copyFile(src, dest.toString(), text)

        then:
        Files.exists(dest)
    }

    @Ignore('Not implemented yet: pushes previous metadata')
    void 'pushes previous metadata if meta=SKIP'() {
        given:
        Map meta = [
            'Name': 'QuiltPackageTest',
            'Owner': 'Ernest',
            'Date': '1967-10-08',
            'Type': 'NGS'
        ]
        Map bad_meta = meta + ['Type': 'Workflow']
        Map skip_meta = ['meta': 'SKIP']

        when:
        makeWriteProduct() // no metadata
        then:
        thrown(WorkflowException)

        expect:
        makeWriteProduct(meta) // valid metadata

        when:
        makeWriteProduct() // invalid default metadata
        then:
        thrown(WorkflowException)

        when:
        makeWriteProduct(bad_meta) // invalid explicit metadata
        then:
        thrown(WorkflowException)

        when:
        makeWriteProduct(skip_meta) // no default metadata
        then:
        thrown(WorkflowException)

        // NOTE: push does NOT update local registry
        expect:
        makeWriteProduct().pkg.install() // try to merge existing metadata

        when:
        makeWriteProduct(skip_meta) // still fails on implicit metadata
        then:
        thrown(WorkflowException)
    }

    void writeFile(String root, String filename) {
        Path outPath = Paths.get(root, filename)
        outPath.getParent().toFile().mkdirs()
        Files.writeString(outPath, "#Time, Filename\n${timestamp},${filename}")
    // println("writeFile: ${filename} -> ${outPath}")
    }

    int writeFiles(dest) {
        String root  = dest
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
    @IgnoreIf({ env.WRITE_BUCKET ==  null })
    @Ignore('Invalid test: top-level summarize')
    void 'should summarize top-level readable files + multiqc '() {
        given:
        String sumURL = writeableURI('summarized')
        QuiltPackage sumPkg = writeablePackage('summarized')
        writeFiles(sumPkg.packageDest())

        and:
        Session session = Mock(Session)
        QuiltPath path = QuiltPathFactory.parse(sumURL)
        QuiltPathify pathify = new QuiltPathify(path)
        QuiltProduct product = new QuiltProduct(pathify, session)

        expect:
        product.publish()
        sumPkg.install()
        Files.exists(Paths.get(sumPkg.packageDest().toString(), QuiltProduct.SUMMARY_FILE))
    }

    void 'should concatMetadata from session'() {
        when:
        QuiltProduct product = makeProduct('a=b&c=d')
        Map start_meta = product.metadata

        then:
        start_meta != null
        start_meta.a == 'b'

        when:
        Map end_meta = product.metadata

        then:
        end_meta != null
        end_meta.size() > 4
        end_meta.a == 'b'
    }

    void 'should not throw error on publish'() {
        given:
        QuiltProduct product = makeProduct()

        when:
        product.publish()

        then:
        true
    }

    void 'should not throw error if session.isSuccess'() {
        when:
        makeProduct('', true)

        then:
        true
    }

}
