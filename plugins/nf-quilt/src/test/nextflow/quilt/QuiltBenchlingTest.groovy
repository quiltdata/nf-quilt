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

    // TODO: Get values from environment = System.getenv('BENCHLING_API_KEY')

    final private @Shared qb = new QuiltBenchling()
    final private @Shared entryID = 'etr_YF5KqGtT'
    final private @Shared authorID = 'ent_9CIv9U8P'
    final private @Shared bench = "${QuiltBenchling.ENTRY_ID}=${entryID}&${QuiltBenchling.AUTHOR_ID}=${authorID}"
    final private @Shared uri = "quilt+s3://bkt?${bench}#package=pre/suf"

    @IgnoreIf({ env.BENCHLING_TENANT == null })
    void 'should get Entry by ID'() {
        when:
        Entry entry = qb.get(entryID)
        then:
        assert entry
        uri
    }

    @IgnoreIf({ env.BENCHLING_TENANT == null })
    void 'should update Entry by ID'() {
        when:
        String test_uri = 'quilt+s3://test-uri'
        String test_url = 'https://test-url'
        Entry entry = qb.updateURIs(entryID, authorID, test_uri, test_url)
        then:
        assert entry
        assert entry.id == entryID
        def fields = entry.fields
        assert fields[QuiltBenchling.K_URI].value == test_uri
        assert fields[QuiltBenchling.K_URL].value == test_url
    }

}
