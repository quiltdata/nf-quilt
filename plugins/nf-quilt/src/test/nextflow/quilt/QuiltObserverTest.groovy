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
import nextflow.quilt.QuiltSpecification
import nextflow.quilt.QuiltObserver

import java.nio.file.Path
import java.nio.file.Paths
import nextflow.Global
import nextflow.Session

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
class QuiltObserverTest extends QuiltSpecification {

    static String path = '/var/tmp/output/quilt-example#package=examples%2fhurdat'

    def 'should generate solid string for now'() {
        when:
        def now = QuiltObserver.now()
        then:
        now
        now.contains('T')
    }
}
