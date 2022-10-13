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

import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Implements {@link BasicFileAttributeView} for Quilt storage blob
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltFileAttributesView implements BasicFileAttributeView {

    private final QuiltFileAttributes target;

    QuiltFileAttributesView(QuiltFileAttributes target) {
        log.debug "QuiltFileAttributesView: $target"
        this.target = target;
    }

    @Override
    String name() {
        return 'basic'
    }

    @Override
    BasicFileAttributes readAttributes() throws IOException {
        return target
    }

    /**
     * This API is implemented is not supported but instead of throwing an exception just do nothing
     * to not break the method {@link java.nio.file.CopyMoveHelper#copyToForeignTarget(java.nio.file.Path, java.nio.file.Path, java.nio.file.CopyOption...)}
     *
     * @param lastModifiedTime
     * @param lastAccessTime
     * @param createTime
     * @throws IOException
     */
    @Override
    void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        // TBD
    }

}
