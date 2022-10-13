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

// https://github.com/LukeGoodsell/nextflow/blob/master/src/main/groovy/nextflow/executor/SimpleFileCopyStrategy.groovy

package nextflow.quilt3.nio

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.SimpleFileCopyStrategy
import nextflow.processor.TaskBean
import nextflow.util.Escape

/**
 * Implements File Copy for Quilt storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltFileCopyStrategy extends SimpleFileCopyStrategy {

    final TaskBean task

    QuiltFileCopyStrategy(TaskBean bean) {
        super(bean)
        this.task = bean
    }

    static String uploadCmd(String source, Path target) {
        "quilt3 push ${Escape.path(source)} ${Escape.uriPath(target)}"
    }
}
