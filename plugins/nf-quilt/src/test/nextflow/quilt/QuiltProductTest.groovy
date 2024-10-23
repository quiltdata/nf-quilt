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
        QuiltPath path = QuiltPathFactory.parse(url)
        QuiltPathify pathify = new QuiltPathify(path)
        Session session = GroovyMock(Session) {
            getWorkflowMetadata() >> wf_meta
            getParams() >> [outdir: url]
            isSuccess() >> success
            config >> [quilt: [metadata: [cfkey: 'cfval']], runName: 'my-run', publishing: false]
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

    QuiltProduct makeWriteProduct(Map meta = [:]) {
        String subURL = writeableURI('quilt_product_test') + '&workflow=my-workflow'
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
        product
        product.pkg
        product.session != null
        product.session.getWorkflowMetadata() != null
        product.meta != null
        product.meta.size() == 4
        product.meta.key == 'val'
    }

    void 'should generate solid string for timestamp from now'() {
        when:
        def now = QuiltProduct.now()
        then:
        now
        now.contains('t')
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
        QuiltProduct product = makeProduct('readme=SKIP')
        expect:
        !product.shouldSkip(QuiltProduct.KEY_SKIP)
        !product.shouldSkip(QuiltProduct.KEY_META)
        product.shouldSkip(QuiltProduct.KEY_README)

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
        when:
        QuiltProduct defaultREADME = makeProduct()
        String text = defaultREADME.setupReadme()
        def files = defaultREADME.pkg.folder.list().sort()

        then:
        !defaultREADME.shouldSkip(QuiltProduct.KEY_README)
        files.size() == 1

        when:
        String readme_text = 'hasREADME'
        QuiltProduct hasREADME = makeProduct("readme=${readme_text}")
        text = hasREADME.setupReadme()
        files = hasREADME.pkg.folder.list().sort()
        then:
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

    @Ignore('Not implemented yet: pushes previous metadata')
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
        QuiltProduct product = new QuiltProduct(path, session)

        expect:
        product.publish()
        sumPkg.install()
        Files.exists(Paths.get(sumPkg.packageDest().toString(), QuiltProduct.SUMMARY_FILE))
    }

    void 'should getMetadata from Map'() {
        given:
        Map meta = [
            'Name': 'QuiltPackageTest',
            'Owner': 'Ernest',
            'Date': '1967-10-08',
            'Type': 'NGS'
        ]
        QuiltProduct product = makeProduct()
        Map quilt_meta = product.getMetadata(meta)

        expect:
        quilt_meta != null
        quilt_meta.config == meta
    }

    void 'should addSessionMeta from session'() {
        when:
        QuiltProduct product = makeProduct('a=b&c=d')
        Map start_meta = product.meta

        then:
        start_meta != null
        start_meta.size() == 4
        start_meta.a == 'b'
        product.addSessionMeta()

        when:
        Map end_meta = product.meta

        then:
        end_meta != null
        end_meta.size() > 4
        end_meta.a == 'b'
    }

    void 'should throw error on publish'() {
        given:
        QuiltProduct product = makeProduct()

        when:
        product.publish()

        then:
        thrown(RuntimeException)
    }

    void 'should throw error if session.isSuccess'() {
        when:
        makeProduct(query: null, success: true)

        then:
        thrown(RuntimeException)
    }

}
