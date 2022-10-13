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

package nextflow.quilt3
import nextflow.quilt3.nio.QuiltPath

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider

import nextflow.plugin.Plugins
import nextflow.plugin.TestPluginDescriptorFinder
import nextflow.plugin.TestPluginManager
import nextflow.plugin.extension.PluginExtensionProvider
import org.pf4j.PluginDescriptorFinder
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Specification
import sun.nio.fs.UnixPath

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
abstract class QuiltSpecification extends Specification {

    @Shared String pluginsMode

    def setupSpec() {
        // reset previous instances
        PluginExtensionProvider.reset()
        // this need to be set *before* the plugin manager class is created
        pluginsMode = System.getProperty('pf4j.mode')
        System.setProperty('pf4j.mode', 'dev')
        // the plugin root should
        def root = Path.of('.').toAbsolutePath().normalize()
        def manager = new TestPluginManager(root){
            @Override
            protected PluginDescriptorFinder createPluginDescriptorFinder() {
                return new TestPluginDescriptorFinder(){
                    @Override
                    protected Path getManifestPath(Path pluginPath) {
                        return pluginPath.resolve('src/resources/META-INF/MANIFEST.MF')
                    }
                }
            }
        }
        Plugins.init(root, 'dev', manager)
        Plugins.startIfMissing('nf-quilt3')
    }

    def cleanupSpec() {
        Plugins.stop()
        PluginExtensionProvider.reset()
        pluginsMode ? System.setProperty('pf4j.mode',pluginsMode) : System.clearProperty('pf4j.mode')
    }

    Path createObject(String url, String text) {
        assert url
        def path = Paths.get(new URI(url))
        createObject(path, text)
    }

    Path createObject(Path path, String text) {
        assert path
        log.debug "Write String[$text] to '$path'"
        Files.write(path, text.bytes)
        path.localPath()
    }

    boolean existsPath(String path) {
        assert url
        log.debug "Check path string exists '$path'"
        Files.exists(Paths.get(path))
    }

    boolean existsPath(QuiltPath path) {
        log.debug "Check path object exists '$path'"
        final local = path.localPath()
        existsPath(local.toString())
    }

    String readObject(Path path) {
        log.debug "Read String from '$path'"
        new String(Files.readAllBytes(path))
    }

    String readChannel(SeekableByteChannel sbc, int buffLen )  {
        def buffer = new ByteArrayOutputStream()
        ByteBuffer bf = ByteBuffer.allocate(buffLen)
        while((sbc.read(bf))>0) {
            bf.flip();
            buffer.write(bf.array(), 0, bf.limit())
            bf.clear();
        }

        buffer.toString()
    }

    void writeChannel( SeekableByteChannel channel, String content, int buffLen ) {

        def bytes = content.getBytes()
        ByteBuffer buf = ByteBuffer.allocate(buffLen);
        int i=0
        while( i < bytes.size()) {

            def len = Math.min(buffLen, bytes.size()-i);
            buf.clear();
            buf.put(bytes, i, len);
            buf.flip();
            channel.write(buf);

            i += len
        }

    }


    protected Path mockQuiltPath(String path, boolean isDir=false) {
        assert path.startsWith('quilt+s3://')

        def tokens = path.tokenize('/')
        def bucket = tokens[1]
        def file = '/' + tokens[2..-1].join('/')

        def attr = Mock(BasicFileAttributes)
        attr.isDirectory() >> isDir
        attr.isRegularFile() >> !isDir
        attr.isSymbolicLink() >> false

        def provider = Mock(FileSystemProvider)
        provider.getScheme() >> 'quilt'
        provider.readAttributes(_, _, _) >> attr

        def fs = Mock(FileSystem)
        fs.provider() >> provider
        fs.toString() >> ('quilt+s3://' + bucket)
        def uri = GroovyMock(URI)
        uri.toString() >> path


        def result = GroovyMock(Path)
        result.bucket() >> bucket
        result.toUriString() >> path
        result.toString() >> file
        result.getFileSystem() >> fs
        result.toUri() >> uri
        result.resolve(_) >> { mockQuiltPath("$path/${it[0]}") }
        result.toAbsolutePath() >> result
        result.asBoolean() >> true
        result.getParent() >> { def p=path.lastIndexOf('/'); p!=-1 ? mockQuiltPath("${path.substring(0,p)}", true) : null }
        result.getFileName() >> { Paths.get(tokens[-1]) }
        result.getName() >> tokens[1]
        return result
    }

}
