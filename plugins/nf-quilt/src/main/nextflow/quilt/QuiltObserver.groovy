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

import nextflow.quilt.jep.QuiltParser
import nextflow.quilt.jep.QuiltPackage
import nextflow.quilt.nio.QuiltPath
import nextflow.quilt.nio.QuiltPathFactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver

/**
 * Plugin observer of workflow events
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltObserver implements TraceObserver {

    private final static String[] BIG_KEYS = [
        'nextflow', 'commandLine', 'scriptFile', 'projectDir',
        'homeDir', 'workDir', 'launchDir', 'manifest', 'configFiles'
    ]
    private final Set<QuiltPackage> pkgs = [] as Set
    private Map config
    private Map quiltConfig
    private Session session

    static void printMap(Map map, String title) {
        log.debug "\n\n\n# $title"
        map.each {
            key, value -> log.debug "\n## ${key}: ${value}"
        }
    }

    static void writeString(String text, QuiltPackage pkg, String filename) {
        String dir = pkg.packageDest()
        Path path  = Paths.get(dir, filename)
        try {
            Files.write(path, text.bytes)
        }
        catch (Exception e) {
            log.error "writeString: cannot write `$text` to `$path` for `${pkg}`"
        }
    }

    static String now() {
        LocalDateTime time = LocalDateTime.now()
        return time.toString()
    }

    static QuiltPath asQuiltPath(Path path) {
        if (path in QuiltPath) {
            return (QuiltPath) path
        }
        String strPath = path.getFileName()
        if (strPath.contains('#package')) {
            String url = "${QuiltParser.SCHEME}://${strPath}"
            return QuiltPathFactory.parse(url)
        }
        return null
    }

    @Override
    void onFlowCreate(Session session) {
        log.debug "`onFlowCreate` $this"
        this.session = session
        this.config = session.config
        this.quiltConfig = session.config.navigate('quilt') as Map
        this.pkgs
    }

    @Override
    void onFilePublish(Path path, Path source) { //
        log.debug "onFilePublish.Path[$path].Source[$source]"
        QuiltPath qPath = asQuiltPath(path)

        if (qPath) {
            QuiltPackage pkg = qPath.pkg()
            this.pkgs.add(pkg)
            log.debug "onFilePublish.QuiltPath[$qPath]: pkgs=${pkgs}"
        } else {
            log.warn "onFilePublish.QuiltPath missing: $path"
        }
    }

    @Override
    void onFlowComplete() {
        log.debug "`onFlowComplete` ${pkgs}"
        // publish pkgs to repository
        this.pkgs.each { pkg -> publish(pkg) }
    }

    String readme(Map meta, String msg) {
        return """
            # ${now()}
            ## $msg

            ## workflow
            ### scriptFile: ${meta['workflow']['scriptFile']}
            ### sessionId: ${meta['workflow']['sessionId']}
            - start: ${meta['time_start']}
            - complete: ${meta['time_complete']}

            ## processes
            ${meta['workflow']['stats']['processes']}

            """.stripIndent()
    }

    void publish(QuiltPackage pkg) {
        String msg = pkg
        Map meta = [pkg: msg]
        String text = 'Stub README'
        String jsonMeta = JsonOutput.toJson(meta)
        try {
            meta = getMetadata()
            msg = "${meta['config']['runName']}: ${meta['workflow']['commandLine']}"
            text = readme(meta, msg)
            //meta.remove('config')
            jsonMeta = JsonOutput.toJson(meta)
        }
        catch (Exception e) {
            log.error('publish: cannot generate metadata (QuiltObserver uninitialized?)', e)
        }
        writeString(text, pkg, 'README.md')
        writeString("$meta", pkg, 'quilt_metadata.txt')
        def rc = pkg.push(msg, jsonMeta)
        log.info "$rc: pushed package[$pkg] $msg"
    }

    Map getMetadata() {
        // TODO: Write out config files
        Map cf = config
        cf.remove('params')
        cf.remove('session')
        cf.remove('executor')
        printMap(cf, 'config')
        Map params = session.getParams()
        params.remove('genomes')
        printMap(params, 'params')
        Map wf = session.getWorkflowMetadata().toMap()
        String start = wf['start']
        String complete = wf['complete']
        BIG_KEYS.each { k -> wf[k] = "${wf[k]}" }
        wf.remove('container')
        wf.remove('start')
        wf.remove('complete')
        printMap(wf, 'workflow')
        log.info "\npublishing: ${wf['runName']}"
        return [params: params, config: cf, workflow: wf, time_start: start, time_complete: complete]
    }

}
