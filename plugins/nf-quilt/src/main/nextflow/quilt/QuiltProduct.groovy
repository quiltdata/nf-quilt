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
import nextflow.quilt.nio.QuiltPath
import nextflow.Session

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.FileSystem
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.PathMatcher
import java.nio.file.SimpleFileVisitor
import java.time.LocalDateTime

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.text.GStringTemplateEngine
import groovy.json.JsonOutput

/**
 * Plugin observer of workflow events
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltProduct {

    private final static String KEY_META = 'metadata'
    private final static String KEY_README = 'readme'
    private final static String KEY_SKIP = 'SKIP'
    private final static String KEY_SUMMARIZE = 'summarize'

    /* groovylint-disable-next-line GStringExpressionWithinString */
    private final static String DEFAULT_README = '''
# ${now}
## ${msg}

## workflow
### scriptFile: ${meta['workflow']?.get('scriptFile')}
### sessionId: ${meta['workflow']?.get('sessionId')}
- start: ${meta['time_start']}
- complete: ${meta['time_complete']}

## processes
${meta['workflow']['stats']['processes']}
'''
    private final static String DEFAULT_SUMMARIZE = '*.md,*.html'

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

    private final QuiltPath path
    private final QuiltPackage pkg
    private final Session session
    private String msg
    private Map meta
    final Integer pubStatus

    QuiltProduct(QuiltPath path, Session session) {
        this.path = path
        this.pkg = path.pkg()
        this.msg =  pkg.toString()
        this.meta = [pkg: msg, time_start: now()]
        this.session = session
        this.pubStatus = -1
        if (session.isSuccess() || pkg.is_force()) {
            try {
                this.pubStatus = publish()
            }
            catch (Exception e) {
                log.error("publish failed: ${e.getMessage()}", pkg.meta)
            }
        } else {
            log.info("not publishing: ${pkg} [unsuccessful session]")
        }
    }

    int publish() {
        log.debug("publish($msg)")
        meta = setupMeta()
        log.debug("setupMeta: $meta")
        String text = setupReadme()
        log.debug("setupReadme: $text")
        // Map quilt_summarize = setupSummarize()
        // log.debug("setupSummarize: $quilt_summarize")
        int rc = pkg.push(msg, meta)
        log.info("$rc: pushed package[$pkg] $msg")
        if (rc > 0) {
            print("ERROR[package push failed]: $pkg\n")
        } else {
            print("SUCCESS: $pkg\n")
        }

        return rc
    }

    boolean shouldSkip(key) {
        return pkg.meta.containsKey(key) && pkg.meta[key] == KEY_SKIP
    }

    Map setupMeta() {
        try {
            meta = getMetadata(session.config)
            meta['quilt'] = [package_id: pkg.toString(), uri: path.toUriString()]
            msg = "${meta['config']['runName']}: ${meta['cmd']}"
            meta.remove('config')
        }
        catch (Exception e) {
            log.error("setupMeta failed: ${e.getMessage()}", pkg.meta)
        }
        writeString("$meta", pkg, 'quilt_metadata.txt')
        writeString(QuiltPackage.toJson(meta), pkg, 'quilt_metadata.json')
        return shouldSkip(KEY_META) ? [:] : meta
    }

    String writeNextflowMedata(Map map, String suffix) {
        String filename = "nextflow_${suffix}.json"
        log.debug("writeNextflowMedata[$suffix]: ${filename}")
        writeString(QuiltPackage.toJson(map), pkg, filename)
        return filename
    }

    Map getMetadata(Map cf) {
        if (cf != null) {
            cf.remove('executor')
            cf.remove('params')
            cf.remove('session')
            writeNextflowMedata(cf, 'config')
            cf.remove('process')
            printMap(cf, 'config')
        }
        Map params = session.getParams()
        if (params != null) {
            writeNextflowMedata(params, 'params')
            params.remove('genomes')
            params.remove('test_data')
            //printMap(params, 'params')
        }
        Map wf = session.getWorkflowMetadata().toMap()
        String start = wf['start']
        String complete = wf['complete']
        String cmd = wf['commandLine']
        if (wf != null) {
            BIG_KEYS.each { k -> wf[k] = "${wf[k]}" }
            writeNextflowMedata(wf, 'workflow')
            wf.remove('container')
            wf.remove('start')
            wf.remove('complete')
            wf.remove('workflowStats')
            wf.remove('commandLine')
            //printMap(wf, 'workflow')
            log.info("\npublishing: ${wf['runName']}")
        }
        return [
            cmd: cmd,
            config: cf,
            params: params,
            time_start: start,
            time_complete: complete,
            workflow: wf,
        ]
    }

    String setupReadme() {
        String text = 'Stub README'
        try {
            text = readme()
        }
        catch (Exception e) {
            log.error("setupReadme failed: ${e.getMessage()}", pkg.meta)
        }
        if (text != null && text.length() > 0) {
            //log.debug("setupReadme: ${text.length()} bytes")
            writeString(text, pkg, 'README.md')
        }
        return text
    }

    String readme() {
        if (shouldSkip(KEY_README)) {
            log.info("readme=SKIP for ${pkg}")
            return null
        }
        GStringTemplateEngine engine = new GStringTemplateEngine()
        String raw_readme = pkg.meta_overrides(KEY_README, DEFAULT_README)
        //log.debug("readme: ${raw_readme}")
        Writable template = engine.createTemplate(raw_readme).make([meta: meta, msg: msg, now: now()])
        return template.toString()
    }

    List<Path> match(String glob) throws IOException {
        String dir = pkg.packageDest()
        Path folder = Paths.get(dir)
        FileSystem fs = folder.getFileSystem()
        PathMatcher pathMatcher = fs.getPathMatcher(glob)
        List<Path> matches = []

        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path path,
                    BasicFileAttributes attrs) throws IOException {
                if (pathMatcher.matches(path)) {
                    Path rel = folder.relativize(path)
                    matches.add(rel)
                }
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFileFailed(Path file, IOException exc)
                    throws IOException {
                return FileVisitResult.CONTINUE
            }

        })
        return matches
    }

    Map setupSummarize() {
        Map quilt_summarize = [:]
        if (shouldSkip(KEY_SUMMARIZE)) {
            log.info("summarize=SKIP for ${pkg}")
            return quilt_summarize
        }
        String summarize = pkg.meta_overrides(KEY_SUMMARIZE, DEFAULT_SUMMARIZE)
        String[] wildcards = summarize.split(',')
        wildcards.each { wildcard ->
            List<Path> paths = match(wildcard)
            log.info("summarize: ${paths.size()} files\n\t${paths}")
            paths.each { path ->
                String filename = path.getFileName()
                quilt_summarize[filename] = path
            }
        }

        String qs_json = JsonOutput.toJson(quilt_summarize.keySet() as String[])
        writeString(qs_json, pkg, 'quilt_summarize.json')
        return quilt_summarize
    }

}
