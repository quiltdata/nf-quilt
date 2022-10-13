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

package nextflow.quilt3.nio

import java.nio.file.Path

import groovy.transform.CompileStatic
import nextflow.Global
import nextflow.Session
import nextflow.quilt3.QuiltOpts
import nextflow.quilt3.jep.QuiltParser
import nextflow.file.FileSystemPathFactory
import nextflow.file.FileHelper
/**
 * Implements FileSystemPathFactory interface for Google storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@CompileStatic
class QuiltPathFactory extends FileSystemPathFactory {

    static public QuiltPath Parse(String path) {
        QuiltPathFactory factory = new QuiltPathFactory()
        (QuiltPath) factory.parseUri(path)
    }

    @Override
    protected Path parseUri(String uri_string) {
        if( !uri_string.startsWith(QuiltParser.PREFIX) )
            return null
        final uri = new URI(uri_string)
        return FileHelper.getOrCreateFileSystemFor(uri).provider().getPath(uri)
    }

    @Override
    protected String toUriString(Path p) {
      if( p instanceof QuiltPath ) {
          return p.toUriString()
      }
      return null
    }

    protected String getBashLib(Path path) {
        return path instanceof QuiltPath ? QuiltBashLib.script() : null
    }

    protected String getUploadCmd(String source, Path target) {
        return target instanceof QuiltPath ?  QuiltFileCopyStrategy.uploadCmd(source, target) : null
    }

}
