/* groovylint-disable LineLength */
package benchling.api

import benchling.model.EntryById
import groovy.transform.CompileStatic

@CompileStatic
class EntriesApi {

    ApiUtils apiUtils = new ApiUtils()

    void getEntry(String entryId, Closure onSuccess, Closure onFailure) {
        String resourcePath = "/entries/${entryId}"

        // params
        def queryParams = [:]
        def headerParams = [:]
        def bodyParams = null
        def contentType

        // verify required params are set
        if (entryId == null) {
            throw new MissingPropertyException('missing required params entryId')
        }

        apiUtils.invokeApi(onSuccess, onFailure, null, null, resourcePath, queryParams, headerParams, bodyParams, contentType,
            'GET', '',
            EntryById
        )
    }

    void updateEntry(String entryId, Map entryUpdate, Closure onSuccess, Closure onFailure) {
        String resourcePath = "/entries/${entryId}"

        // params
        def queryParams = [:]
        def headerParams = [:]
        def bodyParams = entryUpdate
        def contentType = 'application/json'

        // verify required params are set
        if (entryId == null) {
            throw new MissingPropertyException('missing required params entryId')
        }
        if (entryUpdate == null) {
            throw new MissingPropertyException('missing required params entryUpdate')
        }
        apiUtils.invokeApi(onSuccess, onFailure, null, null, resourcePath, queryParams, headerParams, bodyParams, contentType,
            'PATCH', '',
            EntryById
        )
    }

}
