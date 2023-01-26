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

import java.nio.file.DirectoryStream
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Iterator for Quilt storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
class QuiltPathIterator implements Iterator<Path> {

    private Iterator<String> itr

    private DirectoryStream.Filter<? super Path> filter

    private String[] children

    private QuiltPath next

    private QuiltPath dir

    QuiltPathIterator(QuiltPath dir, DirectoryStream.Filter<? super Path> filter) {
        this.dir = dir
        this.children = dir.pkg().relativeChildren(dir.sub_paths())
        this.itr = this.children.iterator()
        this.filter = filter
        advance()
    }

    private void advance() {
        QuiltPath result = null
        while (result == null && itr.hasNext()) {
            def item = itr.next()
            Path path  = dir.resolve(item)
            if (path == dir)    // make sure to  skip the original path
                result = null
            else if (filter)
                result = filter.accept(path) ? path : null
            else
                result = path
        }

        next = result
    }

    @Override
    boolean hasNext() {
        return next != null
    }

    @Override
    Path next() {
        def result = next
        if (result == null) {
            throw new NoSuchElementException()
        }
        advance()
        return result
    }

    @Override
    void remove() {
        throw new UnsupportedOperationException("Operation 'remove' is not supported by QuiltPathIterator")
    }

}
