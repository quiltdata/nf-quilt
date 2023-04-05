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

import nextflow.quilt.QuiltBenchling
import benchling.model.Entry

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
class QuiltBenchlingTest extends Specification {

    final private @Shared qb = new QuiltBenchling()
    final private @Shared testID = '1'
    final private @Shared uri = "quilt+s3://bkt?${QuiltBenchling.EXPERIMENT_ID}=1#package=pre/suf"

    @IgnoreIf({ env.BENCHLING_TENANT == null })
    void 'should get Entry by ID'() {
        when:
        Entry entry = qb.get(testID)
        then:
        assert entry
        uri
    }

    @IgnoreIf({ env.BENCHLING_TENANT == null })
    void 'should update Entry by ID'() {
        when:
        Entry entry = qb.update(testID, 'field', 'test')
        then:
        assert entry
    }

}
