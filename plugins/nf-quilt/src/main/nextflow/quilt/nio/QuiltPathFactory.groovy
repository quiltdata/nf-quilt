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
package nextflow.quilt.nio

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.quilt.jep.QuiltParser
import nextflow.file.FileSystemPathFactory
import nextflow.file.FileHelper

/**
 * Implements FileSystemPathFactory interface for Google storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
class QuiltPathFactory extends FileSystemPathFactory {

    static QuiltPath parse(String uriString) {
        QuiltPathFactory factory = new QuiltPathFactory()
        return (QuiltPath) factory.parseUri(uriString)
    }

    @Override
    protected Path parseUri(String uriString) {
        if (!uriString.startsWith(QuiltParser.SCHEME)) {
            return null
        }
        String[] split = uriString.split('/')
        String newString = (split[1] == '') ? uriString : uriString.replaceFirst(/\/+/, '//')
        final uri = new URI(newString)
        return FileHelper.getOrCreateFileSystemFor(uri).provider().getPath(uri)
    }

    @Override
    protected String toUriString(Path p) {
        /* groovylint-disable-next-line Instanceof */
        if (p instanceof QuiltPath) {
            return p.toUriString()
        }
        return null
    }

    protected String getBashLib(Path path) {
        return path in QuiltPath ? QuiltBashLib.script() : null
    }

    protected String getUploadCmd(String source, Path target) {
        return target in QuiltPath ?  QuiltFileCopyStrategy.uploadCmd(source, target) : null
    }

}
