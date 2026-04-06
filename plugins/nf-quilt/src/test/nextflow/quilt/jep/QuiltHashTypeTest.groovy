/* groovylint-disable MethodName */
/*
 * Copyright 2025, Quilt Data Inc
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
package nextflow.quilt.jep

import com.quiltdata.quiltcore.Entry
import nextflow.quilt.QuiltSpecification
import spock.lang.Specification

/**
 * Test hash type support in quiltcore Entry class
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
class QuiltHashTypeTest extends Specification {

    def 'should parse SHA256 hash type'() {
        when:
        def result = Entry.HashType.enumFor('SHA256')

        then:
        result == Entry.HashType.SHA256
    }

    def 'should parse sha2-256-chunked hash type'() {
        when:
        def result = Entry.HashType.enumFor('sha2-256-chunked')

        then:
        result == Entry.HashType.SHA2_256_Chunked
    }

    def 'should handle case-insensitive hash type names'() {
        when:
        def result1 = Entry.HashType.enumFor('sha256')
        def result2 = Entry.HashType.enumFor('SHA256')
        def result3 = Entry.HashType.enumFor('Sha256')

        then:
        result1 == Entry.HashType.SHA256
        result2 == Entry.HashType.SHA256
        result3 == Entry.HashType.SHA256
    }

    def 'should handle different separator formats for chunked'() {
        when:
        def result1 = Entry.HashType.enumFor('sha2-256-chunked')
        def result2 = Entry.HashType.enumFor('SHA2_256_Chunked')
        def result3 = Entry.HashType.enumFor('sha2_256_chunked')

        then:
        result1 == Entry.HashType.SHA2_256_Chunked
        result2 == Entry.HashType.SHA2_256_Chunked
        result3 == Entry.HashType.SHA2_256_Chunked
    }

    def 'should throw IllegalArgumentException for unknown hash type'() {
        when:
        Entry.HashType.enumFor('invalid-hash-type')

        then:
        thrown(IllegalArgumentException)
    }
}
