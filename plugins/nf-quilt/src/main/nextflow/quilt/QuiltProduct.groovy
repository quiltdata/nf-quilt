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
import groovy.text.GStringTemplateEngine
import groovy.util.logging.Slf4j
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

    private final static String KEY_CATALOG = 'catalog'
    private final static String KEY_FORCE = 'force'
    private final static String KEY_META = 'meta'
    private final static String KEY_MSG = 'message'
    private final static String KEY_PKG = 'package'
    private final static String KEY_QUILT = 'quilt'
    private final static String KEY_README = 'readme'
    private final static String KEY_SUMMARIZE = 'summarize'

/* groovylint-disable-next-line GStringExpressionWithinString */
    private final static String DEFAULT_MSG = '''
${config.get('runName')}: ${meta.get('cmd')}
'''

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

`${meta['workflow']?.get('stats')?.getAt('processes')}`
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
    private final Map<String, Map<String,Object>> config

    private final Map metadata
    private final Expando flags = new Expando([
        catalog: false,
        force: false,
        message: DEFAULT_MSG,
        readme: DEFAULT_README,
        summarize: DEFAULT_SUMMARIZE,
        workflow: false,
    ])

    QuiltProduct(QuiltPathify pathify, Session session) {
        this.session = session
        this.config = session.config ?: [:]
        this.path = pathify.path
        this.pkg = pathify.pkg
        println("QuiltProduct.pkg: ${pkg.toUriString()}")
        this.metadata = getMetadata()
        println("QuiltProduct.metadata: ${metadata}")
        println("QuiltProduct.flags: ${flags}")
        if (session.isSuccess() || flags.getProperty(KEY_FORCE)) {
            publish()
        } else {
            log.info("not publishing: ${pkg} [unsuccessful session]")
        }
    }

    Map getMetadata() {
        if (shouldSkip(KEY_META)) {
            log.info("SKIP: metadata for ${pkg}")
            return [:]
        }
        println("getMetadata.config: ${config}")
        config.remove('executor')
        config.remove('params')
        config.remove('session')
        writeMapToPackage(config, 'config')
        config.remove('process')
        printMap(config, 'config')

        Map<String, Object> quilt_cf = config.navigate(KEY_QUILT) as Map<String, Object> ?: [:]
        config.remove(KEY_QUILT)

        Map<String, Object> cf_meta = quilt_cf.navigate(KEY_META) as Map<String, Object> ?: [:]
        quilt_cf.remove(KEY_META)
        Map pkg_meta = pkg.meta
        updateFlags(pkg_meta, quilt_cf)

        Map params = session.getParams()
        println("getMetadata.params: ${params}")
        if (params != null) {
            writeMapToPackage(params, 'params')
            params.remove('genomes')
            params.remove('test_data')
            printMap(params, 'params')
        }
        Map wf = session.getWorkflowMetadata()?.toMap()
        println("getMetadata.wf: ${wf}")
        String start = wf?.get('start')
        String complete = wf?.get('complete')
        String cmd = wf?.get('commandLine')
        if (wf != null) {
            BIG_KEYS.each { k -> wf[k] = "${wf[k]}" }
            writeMapToPackage(wf, 'workflow')
            wf.remove('container')
            wf.remove('start')
            wf.remove('complete')
            wf.remove('workflowStats')
            wf.remove('commandLine')
            printMap(wf, 'workflow')
            log.info("\npublishing: ${wf['runName']}")
        }

        Map base_meta = cf_meta + pkg_meta
        log.debug("getMetadata.base_meta: ${base_meta}")
        return base_meta + [
            cmd: cmd,
            now: now(),
            params: params,
            quilt: quilt_cf,
            time_start: start,
            time_complete: complete,
            uri: path.toUriString(),
            workflow: wf,
        ]
    }

    Map getParams() {
        Map params = [
            cmd: metadata.get('cmd'),
            meta: metadata,
            now: metadata.get('now'),
            pkg: flags.getProperty(KEY_PKG)
        ]
        return params
    }

    boolean shouldSkip(String key) {
        return flags.getProperty(key) == false
    }

    /**
     * Update flags from default values
     * Use metadata if available, otherwise use config
     *
        * @param meta Map of package metadata
        * @param cf Map of config (from nextflow.config)
     */
    void updateFlags(Map meta, Map cf) {
        flags.each { String key, value ->
            if (meta.containsKey(key)) {
                flags.setProperty(key, meta[key])
            } else if (cf.containsKey(key)) {
                flags.setProperty(key, cf[key])
            }
        }
        // FIXME: should this only work for names inferred from S3 URIs?
        String pkgName = cf.containsKey(KEY_PKG) ? cf[KEY_PKG] : pkg.packageName
        flags.setProperty(KEY_PKG, pkgName)
    }

    String writeMapToPackage(Map map, String prefix) {
        String filename = "nf-quilt/${prefix}.json"
        log.debug("writeMapToPackage[$prefix]: ${filename}")
        try {
            writeString(QuiltPackage.toJson(map), pkg, filename)
        } catch (Exception e) {
            log.error("writeMapToPackage.toJson failed: ${e.getMessage()}", map)
        }
        return filename
    }

/*
 * Publish the package to the Quilt catalog
 */

    void publish() {
        log.info("publish.pushing: ${pkg}")
        try {
            String message = compileMessage()
            writeReadme(message)
            writeSummarize()
            def rc = pkg.push(message, metadata)
            log.info("publish.pushed: ${rc}")
        }
        catch (Exception e) {
            log.error("Exception: ${e}")
            print("publish.FAILED: $pkg\n")
            e.printStackTrace()
            return
        }
        print("\nSUCCESS: ${displayName()}\n")
    }

    String displayName() {
        Object catalog = flags.getProperty(KEY_CATALOG)
        return catalog ? pkg.toCatalogURL(catalog.toString()) : pkg.toUriString()
    }

    String compileMessage() {
        String msg = flags.getProperty(KEY_MSG)
        GStringTemplateEngine engine = new GStringTemplateEngine()
        String output = engine.createTemplate(msg).make(getParams())
        log.debug("compileMessage.output: ${output}")
        return output
    }

    String compileReadme(String msg) {
        if (shouldSkip(KEY_README)) {
            log.info("SKIP: readme for ${pkg}")
            return null
        }
        String raw_readme = flags.getProperty(KEY_README)
        String nf = metadata['workflow']?['nextflow']
        String nextflow = nf?.replace(', ', '```\n  - **')\
            ?.replace('nextflow.NextflowMeta(', '  - **')\
            ?.replace(')', '```')
            ?.replace(':', '**: ```')
        Map params = getParams()
        params += [
            msg: msg,
            nextflow: nextflow,
        ]
        log.debug("compileReadme.params: ${params}")
        GStringTemplateEngine engine = new GStringTemplateEngine()
        String output = engine.createTemplate(raw_readme).make(params)
        log.debug("compileReadme.output: ${output}")
        return output
    }

    String writeReadme(String message) {
        String text = 'Stub README'
        try {
            text = compileReadme(message)
        }
        catch (Exception e) {
            log.error("writeReadme failed: ${e.getMessage()}\n{$e}", flags)
        }
        if (text != null && text.length() > 0) {
            log.debug("writeReadme: ${text.length()} bytes")
            writeString(text, pkg, README_FILE)
        }
        return text
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

    List<Map> writeSummarize() {
        List<Map> quilt_summarize = []
        if (shouldSkip(KEY_SUMMARIZE)) {
            return quilt_summarize
        }
        String summarize = flags.getProperty(KEY_SUMMARIZE)
        String[] wildcards = summarize.split(',')
        wildcards.each { wildcard ->
            List<Path> paths = match(wildcard)
            paths.each { path ->
                String filename = path.getFileName()
                Map entry = ['path': path.toString(), 'title': filename]
                quilt_summarize.add(entry)
            }
        }

        try {
            String qs_json = JsonOutput.toJson(quilt_summarize)
            writeString(qs_json, pkg, SUMMARY_FILE)
        }
        catch (Exception e) {
            log.error("writeSummarize.toJson failed: ${e.getMessage()}\n{$e}", SUMMARY_FILE)
        }
        return quilt_summarize
    }

}
