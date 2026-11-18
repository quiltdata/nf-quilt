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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory
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
    private final static String S3_PREFIX = 's3://'

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    private static final ObjectWriter OBJECT_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
    static String toJson(Object value) {
        String result = OBJECT_WRITER.writeValueAsString(value)
        return result
    }

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
    protected final String outdirPrefix

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
        this.outdirPrefix = computeOutdirPrefix(session.getParams()?.get('outdir'), path)
        this.metadata = collectMetadata()
        if (session.isSuccess() || flags.getProperty(QuiltParser.P_FORCE) == true) {
            publish()
        } else {
            log.warn("not publishing: ${pkg} [unsuccessful session]")
        }
    }

    /**
     * The part of `params.outdir` below the package root, e.g. `run-name` for
     * `s3://bucket/ns/pkg/run-name/`. Generated files (README, summarize,
     * `nf-quilt/*.json`) are written there, next to the run's outputs.
     *
     * Returns '' (package root) when outdir is missing, is the package root,
     * or does not resolve to the same package as the published files.
     */
    static String computeOutdirPrefix(Object outdir, QuiltPath target) {
        if (outdir == null || outdir.toString().trim().isEmpty()) {
            log.warn('computeOutdirPrefix: no `params.outdir`; writing nf-quilt files at the package root')
            return ''
        }
        Map<String,String> out = parseOutdir(outdir)
        if (out == null) {
            log.warn("computeOutdirPrefix: cannot resolve outdir `${outdir}` to a Quilt package; using the package root")
            return ''
        }
        if (out.bucket != target.getBucket() || out.pkg != target.getPackageName()) {
            log.warn("computeOutdirPrefix: outdir `${outdir}` is not in package " +
                "`${target.getBucket()}#${target.getPackageName()}`; using the package root")
            return ''
        }
        List<String> segments = out.sub.split('/').findAll { String seg ->
            seg && seg != '.'
        } as List<String>
        if (segments.contains('..')) {
            log.warn("computeOutdirPrefix: outdir `${outdir}` leaves the package; using the package root")
            return ''
        }
        String prefix = segments.join('/')
        log.debug("computeOutdirPrefix: `${prefix}` from outdir `${outdir}`")
        return prefix
    }

    /**
     * Resolve an outdir to its bucket, package name and sub-path, or null.
     *
     * `s3://` outdirs are split directly (no URI round trip, so keys such as
     * `date=2024-01-01` or `run+1` survive), using the same positional rule as
     * {@link QuiltPathify#uriFromS3File} for published files: the segments after
     * the bucket are namespace, name, then the sub-path.
     * `quilt+s3://` outdirs go through QuiltParser.
     */
    static Map<String,String> parseOutdir(Object outdir) {
        if (outdir instanceof QuiltPath) {
            return quiltOutdir((QuiltPath) outdir)
        }
        String uri = (outdir instanceof Path) ? ((Path) outdir).toUri().toString() : outdir.toString().trim()
        try {
            if (uri.startsWith(QuiltParser.PREFIX)) {
                return quiltOutdir(QuiltPathFactory.parse(uri))
            }
            if (uri.startsWith(S3_PREFIX)) {
                String[] parts = uri.substring(S3_PREFIX.length()).replaceFirst('/+$', '').split('/')
                if (parts.length < 3) {
                    return null
                }
                String sub = parts.length > 3 ? parts[3..-1].join('/') : ''
                return [bucket: parts[0].toLowerCase(), pkg: "${parts[1]}/${parts[2]}".toString(), sub: sub]
            }
        } catch (Exception e) {
            log.warn("parseOutdir: cannot parse `${uri}`: ${e.message}")
        }
        return null
    }

    private static Map<String,String> quiltOutdir(QuiltPath outPath) {
        return [bucket: outPath.getBucket(), pkg: outPath.getPackageName(), sub: outPath.sub_paths()]
    }

    String prefixedPath(String relpath) {
        return outdirPrefix ? "${outdirPrefix}/${relpath}" : relpath
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
    void updateFlags(Map pkgMeta, Map cfMeta) {
        for (String key : flags.getProperties().keySet()) {
            if (pkgMeta.containsKey(key)) {
                flags.setProperty(key, pkgMeta[key])
            } else if (cfMeta.containsKey(key)) {
                flags.setProperty(key, cfMeta[key])
            }
        }
        // TODO: should this only work for names inferred from S3 URIs?
        String pkgName = cfMeta.containsKey(QuiltParser.P_PKG) ? cfMeta[QuiltParser.P_PKG] : pkg.getPackageName()
        flags.setProperty(QuiltParser.P_PKG, pkgName)
    }

    String writeMapToPackage(Map map, String prefix) {
        String filename = prefixedPath("nf-quilt/${prefix}.json")
        log.debug("writeMapToPackage[$prefix]: ${filename}")
        try {
            writeString(toJson(map), pkg, filename)
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
            writeString(text, pkg, prefixedPath(README_FILE))
        }
        return text
    }

    /**
     * Files under {@code base} (package-relative, '' for the root) whose path
     * relative to {@code base} matches {@code glob}.
     */
    List<Path> match(String glob, String base = '') throws IOException {
        String dir = pkg.packageDest()
        Path folder = base ? Paths.get(dir, base.split('/') as String[]) : Paths.get(dir)
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
            // Only this run's files; entries are relative to the summarize file's folder
            List<Path> paths = match(wildcard, outdirPrefix)
            log.debug("writeSummarize: ${paths.size()} matches for ${wildcard}")
            paths.each { path ->
                String filename = path.getFileName()
                String relpath = path.collect { Path name -> name.toString() }.join('/')
                Map entry = ['path': relpath, 'title': filename]
                quilt_summarize.add(entry)
            }
        }

        try {
            String qs_json = toJson(quilt_summarize)
            writeString(qs_json, pkg, prefixedPath(SUMMARY_FILE))
        }
        catch (Exception e) {
            log.error("writeSummarize.toJson failed: ${e.getMessage()}\n{$e}", SUMMARY_FILE)
        }
        return quilt_summarize
    }

}
