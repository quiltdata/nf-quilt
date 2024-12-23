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
import nextflow.quilt.jep.QuiltParser
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

    public final static String KEY_META = 'meta'
    public final static String KEY_MSG = 'message'
    public final static String KEY_QUILT = 'quilt'
    public final static String KEY_README = 'readme'
    public final static String KEY_SUMMARIZE = 'summarize'

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
        log.debug("\n\n\n# $title ${map.keySet()}")
        map.each {
            key, value -> log.info("\n## ${key}: ${value}")
        }
    }

    static Map<String, Object> extractMap(Map map, String key) {
        def child = map.remove(key)
        return (child instanceof Map) ? child : [:]
    }

    static void writeString(String text, QuiltPackage pkg, String filepath) {
        String dir = pkg.packageDest()
        Path path  = Paths.get(dir, filepath.split('/') as String[])
        try {
            path.getParent().toFile().mkdirs() // ensure directories exist first
            Files.write(path, text.bytes)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$text` to `$path` for `${pkg}`\n$e")
        }
    }

    static void copyFile(Path source, String destRoot, String relpath) {
        Path dest  = Paths.get(destRoot, relpath.split('/') as String[])
        try {
            dest.getParent().toFile().mkdirs() // ensure directories exist first
            Files.copy(source, dest)
        }
        catch (Exception e) {
            log.error("writeString: cannot write `$source` to `$dest` in `${destRoot}`\n$e.message()")
        }
    }

    static String now() {
        LocalDateTime time = LocalDateTime.now()
        return time.toString().replace(':', '-').replace('T', 't')
    }

    protected final QuiltPath path
    protected final QuiltPackage pkg
    protected final Session session
    protected final Map<String, Map<String,Object>> config

    protected final Map metadata
    protected final Expando flags = new Expando([
        catalog: false,
        force: false,
        message: DEFAULT_MSG,
        meta: true,
        readme: DEFAULT_README,
        summarize: DEFAULT_SUMMARIZE,
        workflow: false,
    ])

    QuiltProduct(QuiltPathify pathify, Session session) {
        log.debug("Creating QuiltProduct: ${pathify}")
        this.session = session
        this.config = session.config ?: [:]
        this.path = pathify.path
        this.pkg = pathify.pkg
        this.metadata = collectMetadata()
        if (session.isSuccess() || flags.getProperty(QuiltParser.P_FORCE) == true) {
            publish()
        } else {
            log.warn("not publishing: ${pkg} [unsuccessful session]")
        }
    }

    Map collectMetadata() {
        if (shouldSkip(KEY_META)) {
            log.debug("SKIP: metadata for ${pkg}")
            return [:]
        }
        config.remove('executor')
        config.remove('params')
        config.remove('session')
        writeMapToPackage(config, 'config')
        config.remove('process')
        printMap(config, 'config')

        Map<String, Object> quilt_cf = extractMap(config, KEY_QUILT)
        Map<String, Object> pkg_meta = pkg.getMetadata()
        updateFlags(pkg_meta, quilt_cf)

        Map<String, Object> params = session.getParams()
        if (params != null) {
            writeMapToPackage(params, 'params')
            params.remove('genomes')
            params.remove('test_data')
            printMap(params, 'params')
        }
        Map<String, Object> wf = session.getWorkflowMetadata()?.toMap()
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

        Map<String, Object> cf_meta = extractMap(quilt_cf, KEY_META)  // remove after setting flags
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

    Map getTemplateArgs() {
        Map params = [
            cmd: metadata.get('cmd'),
            config: config,
            meta: metadata,
            now: metadata.get('now'),
            pkg: flags.getProperty(QuiltParser.P_PKG)
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
        * @param cf Map of config (from nextflow.config)`
     */
    void updateFlags(Map pkg_meta, Map cf_meta) {
        for (String key : flags.getProperties().keySet()) {
            if (pkg_meta.containsKey(key)) {
                flags.setProperty(key, pkg_meta[key])
            } else if (cf_meta.containsKey(key)) {
                flags.setProperty(key, cf_meta[key])
            }
        }
        // TODO: should this only work for names inferred from S3 URIs?
        String pkgName = cf_meta.containsKey(QuiltParser.P_PKG) ? cf_meta[QuiltParser.P_PKG] : pkg.getPackageName()
        flags.setProperty(QuiltParser.P_PKG, pkgName)
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
        log.debug("publish.pushing: ${pkg}")
        try {
            String message = compileMessage()
            writeReadme(message)
            writeSummarize()
            def rc = pkg.push(message, metadata)
            log.debug("publish.pushed: ${rc}")
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
        Object catalog = flags.getProperty(QuiltParser.P_CAT)
        return catalog ? pkg.toCatalogURL(catalog.toString()) : pkg.toUriString()
    }

    String compileMessage() {
        String msg = flags.getProperty(KEY_MSG)
        GStringTemplateEngine engine = new GStringTemplateEngine()
        try {
            String output = engine.createTemplate(msg).make(getTemplateArgs())
            log.debug("compileMessage.output: ${output}")
            return output
        }
        catch (Exception e) {
            log.error("compileMessage failed: ${e.getMessage()}\n{$e}", flags)
        }
        return "compileMessage.FAILED\n$msg"
    }

    String compileReadme(String msg) {
        if (shouldSkip(KEY_README)) {
            log.debug("SKIP: readme for ${pkg}")
            return null
        }
        String raw_readme = flags.getProperty(KEY_README)
        String nf = metadata['workflow']?['nextflow']
        String nextflow = nf?.replace(', ', '```\n  - **')\
            ?.replace('nextflow.NextflowMeta(', '  - **')\
            ?.replace(')', '```')
            ?.replace(':', '**: ```')
        Map params = getTemplateArgs()
        params += [
            msg: msg,
            nextflow: nextflow,
        ]
        log.debug("compileReadme.params: ${params}")
        try {
            GStringTemplateEngine engine = new GStringTemplateEngine()
            String output = engine.createTemplate(raw_readme).make(params)
            log.debug("compileReadme.output: ${output}")
            return output
        }
        catch (Exception e) {
            log.error("compileReadme failed: ${e.getMessage()}\n{$e}", flags)
        }
        return "compileReadme.FAILED\n$raw_readme"
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
            log.debug("SKIP: summarize for ${flags}")
            return quilt_summarize
        }
        String summarize = flags.getProperty(KEY_SUMMARIZE)
        String[] wildcards = summarize.split(',')
        wildcards.each { wildcard ->
            List<Path> paths = match(wildcard)
            log.debug("writeSummarize: ${paths.size()} matches for ${wildcard}")
            paths.each { path ->
                String filename = path.getFileName()
                Map entry = ['path': path.toString(), 'title': filename]
                quilt_summarize.add(entry)
            }
        }

        try {
            String qs_json = QuiltPackage.arrayToJson(quilt_summarize)
            writeString(qs_json, pkg, SUMMARY_FILE)
        }
        catch (Exception e) {
            log.error("writeSummarize.toJson failed: ${e.getMessage()}\n{$e}", SUMMARY_FILE)
        }
        return quilt_summarize
    }

}
