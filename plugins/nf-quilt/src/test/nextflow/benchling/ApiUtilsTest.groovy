/* groovylint-disable MethodName */
/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.quilt.nio

import benchling.api.ApiUtils

import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileDynamic
class ApiUtilsTest extends Specification {

    final private @Shared Object api = new ApiUtils()

    @IgnoreIf({ env.BENCHLING_TENANT != null })
    void 'realClient returns null if missing envars'() {
        assert api
    }

}
