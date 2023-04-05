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
package nextflow.quilt

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import benchling.model.Entry
import benchling.model.EntryUpdate
import benchling.model.EntryById
import benchling.api.EntriesApi

/**
 * Push Quilt package data to Benchling notebook
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltBenchling {

    public static final String EXPERIMENT_ID = 'benchling.experiment_id'

    private final EntriesApi api

    QuiltBenchling() {
        api = new EntriesApi()
        log.debug("Creating QuiltBenchling: $api")
    }

    Entry get(String entryId) {
        Entry result = null
        api.getEntry(entryId) { EntryById byid ->
            log.debug "Successfully retrieved byid: $byid"
            result = byid.entry
        } { error ->
            log.debug "Failed to retrieve byid: $error"
        }
        return result
    }

    Entry update(String entryId, String field, String value) {
        EntryUpdate update = new EntryUpdate()
        update.fields = [(field): value]
        Entry result = null
        api.updateEntry(entryId, update) { EntryById byid ->
            log.debug "Successfully updated byid: $byid"
            result = byid.entry
        } { error ->
            log.debug "Failed to retrieve byid: $error"
        }
        return result
    }

}
