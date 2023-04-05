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

import nextflow.quilt.jep.QuiltPackage
import nextflow.Session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.text.GStringTemplateEngine

/**
 * Plugin observer of workflow events
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltProduct {

    /* groovylint-disable-next-line GStringExpressionWithinString */
    private final static String README_TEMPLATE = '''
            # $now
            ## $msg

            ## workflow
            ### scriptFile: ${meta['workflow']['scriptFile']}
            ### sessionId: ${meta['workflow']['sessionId']}
            - start: ${meta['time_start']}
            - complete: ${meta['time_complete']}

            ## processes
            ${meta['workflow']['stats']['processes']}

            '''.stripIndent()

    private final static String[] BIG_KEYS = [
        'nextflow', 'commandLine', 'scriptFile', 'projectDir',
        'homeDir', 'workDir', 'launchDir', 'manifest', 'configFiles'
    ]

    static void printMap(Map map, String title) {
        log.info("\n\n\n# $title")
        map.each {
            key, value -> log.info("\n## ${key}: ${value}")
        }
    }

    static void writeString(String text, QuiltPackage pkg, String filename) {
        String dir = pkg.packageDest()
        Path path  = Paths.get(dir, filename)
        try {
            Files.write(path, text.bytes)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$text` to `$path` for `${pkg}`")
        }
    }

    static String now() {
        LocalDateTime time = LocalDateTime.now()
        return time.toString()
    }

    private final QuiltPackage pkg
    private final Session session
    private String msg
    private Map meta

    QuiltProduct(QuiltPackage pkg, Session session) {
        this.pkg = pkg
        this.msg =  pkg.toString()
        this.meta = [pkg: msg, time_start: now()]
        this.session = session
        publish()
    }

    String readme() {
        GStringTemplateEngine engine = new GStringTemplateEngine()
        String raw_readme = pkg.meta_overrides('readme', README_TEMPLATE)
        Writable template = engine.createTemplate(raw_readme).make([meta: meta, msg: msg, now: now()])
        return template.toString()
    }

    int publish() {
        String text = 'Stub README'
        try {
            meta = getMetadata(session.config)
            msg = "${meta['config']['runName']}: ${meta['cmd']}"
            text = readme()
            meta.remove('config')
        }
        catch (Exception e) {
            log.error('publish: cannot generate metadata (QuiltObserver uninitialized?)', e)
        }
        writeString(text, pkg, 'README.md')
        writeString("$meta", pkg, 'quilt_metadata.txt')
        writeString(QuiltPackage.toJson(meta), pkg, 'quilt_metadata.json')

        def rc = pkg.push(msg, meta)
        log.info("$rc: pushed package[$pkg] $msg")
        if (rc > 0) {
            print("ERROR[package push failed]: $pkg")
        }

        return rc
    }

    Map getMetadata(Map cf) {
        // TODO: Write out config files
        cf.remove('params')
        cf.remove('session')
        cf.remove('executor')
        printMap(cf, 'config')
        Map params = session.getParams()
        params.remove('genomes')
        params.remove('test_data')
        printMap(params, 'params')
        Map wf = session.getWorkflowMetadata().toMap()
        String start = wf['start']
        String complete = wf['complete']
        String cmd = wf['commandLine']
        BIG_KEYS.each { k -> wf[k] = "${wf[k]}" }
        wf.remove('container')
        wf.remove('start')
        wf.remove('complete')
        wf.remove('workflowStats')
        wf.remove('commandLine')
        printMap(wf, 'workflow')
        log.info("\npublishing: ${wf['runName']}")
        return [
            cmd: cmd,
            config: cf,
            params: params,
            quilt:  cf.navigate('quilt') as Map,
            time_start: start,
            time_complete: complete,
            workflow: wf,
        ]
    }

}
