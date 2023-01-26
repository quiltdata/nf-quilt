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

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Implements {@link BasicFileAttributes} for Quilt storage blob
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
class QuiltFileAttributes implements BasicFileAttributes {

    private final QuiltPath path
    private final String key
    private final String orig_key
    private final BasicFileAttributes attrs

    QuiltFileAttributes(QuiltPath path, String key, BasicFileAttributes attrs) {
        this.path = path
        this.key = path.isJustPackage() ? '/' : path.file_key()
        this.orig_key = key
        this.attrs = attrs
        log.debug "QuiltFileAttributes($path): this=$this"
    }

    @Override
    FileTime lastModifiedTime() {
        return attrs.lastModifiedTime()
    }

    @Override
    FileTime lastAccessTime() {
        return attrs.lastAccessTime()
    }

    @Override
    FileTime creationTime() {
        return attrs.creationTime()
    }

    @Override
    boolean isRegularFile() {
        return attrs.isRegularFile()
    }

    @Override
    boolean isDirectory() {
        return attrs.isDirectory()
    }

    @Override
    boolean isSymbolicLink() {
        return attrs.isSymbolicLink()
    }

    @Override
    boolean isOther() {
        return attrs.isOther()
    }

    @Override
    long size() {
        return attrs.size()
    }

    @Override
    Object fileKey() {
        log.debug "QuiltFileAttributes.fileKey: $key"
        return key
    }

    @Override
    String toString() {
        return "[${key}: lastModified=${lastModifiedTime()}, size=${size()}, isDirectory=${isDirectory()}]"
    }

}
