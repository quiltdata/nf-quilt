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
import java.nio.file.FileSystems
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

    public final static String README_FILE = 'README_NF_QUILT.md'
    public final static String SUMMARY_FILE = 'quilt_summarize.json'

    private final static String KEY_META = 'metadata'
    private final static String KEY_README = 'readme'
    private final static String KEY_SKIP = 'SKIP'
    private final static String KEY_SUMMARIZE = 'summarize'

    /* groovylint-disable-next-line GStringExpressionWithinString */
    private final static String DEFAULT_README = '''
# ${pkg}

## ${now}

## Run Command

```bash
${cmd}
```

### Workflow

- **workflow run name**: ```${meta['workflow']?.get('runName')}```
- **scriptFile**: ```${meta['workflow']?.get('scriptFile')}```
- **sessionI**: ```${meta['workflow']?.get('sessionId')}```
- **start**: ```${meta['time_start']}```
- **complete**: ```${meta['time_complete']}```

### Nextflow

${nextflow}

### Processes

`${meta['workflow']?.get('stats')?.get('processes')}`
'''
    private final static String DEFAULT_SUMMARIZE = '*.md,*.html,*.?sv,*.pdf,igv.json,**/multiqc_report.html'

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

    static void writeString(String text, QuiltPackage pkg, String filepath) {
        String dir = pkg.packageDest()
        Path path  = Paths.get(dir, filepath.split('/') as String[])
        try {
            path.getParent().toFile().mkdirs() // ensure directories exist first
            Files.write(path, text.bytes)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$text` to `$path` for `${pkg}`")
        }
    }

    static void copyFile(Path source, String destRoot, String relpath) {
        Path dest  = Paths.get(destRoot, relpath.split('/') as String[])
        try {
            dest.getParent().toFile().mkdirs() // ensure directories exist first
            Files.copy(source, dest)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$source` to `$dest` in `${destRoot}`")
        }
    }

    static String now() {
        LocalDateTime time = LocalDateTime.now()
        return time.toString().replace(':', '-').replace('T', 't')
    }

    private final QuiltPath path
    private final QuiltPackage pkg
    private final Session session
    private String msg
    private Map meta

    QuiltProduct(QuiltPathify pathify, Session session) {
        this.path = pathify.path
        this.pkg = pathify.pkg
        this.msg =  pkg.toString()
        this.meta = pkg.meta + [pkg: msg, time_start: now()]
        this.session = session

        if (session.isSuccess() || pkg.is_force()) {
            publish()
        } else {
            log.info("not publishing: ${pkg} [unsuccessful session]")
        }
    }

    void publish() {
        log.debug("publish($msg)")
        addSessionMeta()
        setupReadme()
        setupSummarize()
        try {
            log.info("publish.pushing: ${pkg}")
            def m = pkg.push(msg, meta)
            log.info("publish.pushed: ${m}")
        }
        catch (Exception e) {
            log.error("Exception: ${e}")
            print("FAILED: $pkg\n")
            e.printStackTrace()
            /* groovylint-disable-next-line ThrowRuntimeException */
            throw new RuntimeException(e)
        }
        print("SUCCESS: $pkg\n")
    }

    boolean shouldSkip(key) {
        print("shouldSkip[$key]: ${pkg.meta}")
        return pkg.meta.containsKey(key) && pkg.meta[key] == KEY_SKIP
    }

    boolean addSessionMeta() {
        if (shouldSkip(KEY_META)) {
            return false
        }
        try {
            Map smeta = getMetadata(session.config)
            println("addSessionMeta.smeta: ${smeta}")
            smeta['quilt'] = [package_id: pkg.toString(), uri: path.toUriString()]
            msg = "${smeta['config']['runName']}: ${smeta['cmd']}"
            smeta.remove('config')
            meta += smeta
        } catch (Exception e) {
            log.error("addSessionMeta.getMetadata failed: ${e.getMessage()}", pkg.meta)
            return false
        }
        println("addSessionMeta.meta: ${meta}")
        writeNextflowMetadata(meta, 'metadata')
        return true
    }

    String writeNextflowMetadata(Map map, String suffix) {
        String filename = "nf-quilt/${suffix}.json"
        // log.debug("writeNextflowMetadata[$suffix]: ${filename}")
        writeString(QuiltPackage.toJson(map), pkg, filename)
        return filename
    }

    Map getMetadata(Map cf) {
        // add metadata from quilt and URI
        if (cf != null) {
            cf.remove('executor')
            cf.remove('params')
            cf.remove('session')
            writeNextflowMetadata(cf, 'config')
            cf.remove('process')
            printMap(cf, 'config')
        }
        Map params = session.getParams()
        if (params != null) {
            writeNextflowMetadata(params, 'params')
            params.remove('genomes')
            params.remove('test_data')
        // printMap(params, 'params')
        }
        Map wf = session.getWorkflowMetadata().toMap()
        String start = wf['start']
        String complete = wf['complete']
        String cmd = wf['commandLine']
        if (wf != null) {
            BIG_KEYS.each { k -> wf[k] = "${wf[k]}" }
            writeNextflowMetadata(wf, 'workflow')
            wf.remove('container')
            wf.remove('start')
            wf.remove('complete')
            wf.remove('workflowStats')
            wf.remove('commandLine')
            // printMap(wf, 'workflow')
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
            text = makeReadme()
        }
        catch (Exception e) {
            log.error("setupReadme failed: ${e.getMessage()}\n{$e}", pkg.meta)
        }
        if (text != null && text.length() > 0) {
            //log.debug("setupReadme: ${text.length()} bytes")
            writeString(text, pkg, README_FILE)
        }
        return text
    }

    String makeReadme() {
        if (shouldSkip(KEY_README)) {
            log.info("readme=SKIP for ${pkg}")
            return null
        }
        GStringTemplateEngine engine = new GStringTemplateEngine()
        String raw_readme = pkg.meta_overrides(KEY_README, DEFAULT_README)
        String cmd = "${meta['cmd']}".replace(' -', ' \\\n  -')
        String nf = meta['workflow']?['nextflow']
        String nextflow = nf?.replace(', ', '```\n  - **')\
            ?.replace('nextflow.NextflowMeta(', '  - **')\
            ?.replace(')', '```')
            ?.replace(':', '**: ```')
        Map params = [
            cmd: cmd,
            meta: meta,
            msg: msg,
            nextflow: nextflow,
            now: now(),
            pkg: pkg.packageName,
        ]
        log.debug("makeReadme.params: ${params}")
        String template = engine.createTemplate(raw_readme).make(params)
        log.debug("makeReadme.template: ${template}")
        return template
    }

    List<Path> match(String glob) throws IOException {
        String dir = pkg.packageDest()
        Path folder = Paths.get(dir)
        FileSystem fs = FileSystems.getDefault()
        PathMatcher pathMatcher = fs.getPathMatcher("glob:${glob}")
        List<Path> matches = []

        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path path,
                    BasicFileAttributes attrs) throws IOException {
                Path rel = folder.relativize(path)
                if (pathMatcher.matches(rel)) {
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

    List<Map> setupSummarize() {
        List<Map> quilt_summarize = []
        if (shouldSkip(KEY_SUMMARIZE)) {
            return quilt_summarize
        }
        String summarize = pkg.meta_overrides(KEY_SUMMARIZE, DEFAULT_SUMMARIZE)
        String[] wildcards = summarize.split(',')
        wildcards.each { wildcard ->
            List<Path> paths = match(wildcard)
            paths.each { path ->
                String filename = path.getFileName()
                Map entry = ['path': path.toString(), 'title': filename]
                quilt_summarize.add(entry)
            }
        }

        String qs_json = JsonOutput.toJson(quilt_summarize)
        writeString(qs_json, pkg, SUMMARY_FILE)
        return quilt_summarize
    }

}
