/* groovylint-disable LineLength */
package benchling.api

import benchling.model.Entry
import benchling.model.EntryById
import benchling.model.EntryUpdate
import groovy.transform.CompileStatic

@CompileStatic
class EntriesApi {

    ApiUtils apiUtils = new ApiUtils()

    void getEntry(String entryId, Closure onSuccess, Closure onFailure) {
        String resourcePath = "/entries/${entryId}"

        // params
        def queryParams = [:]
        def headerParams = [:]
        def bodyParams
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

    void updateEntry(String entryId, EntryUpdate entryUpdate, Closure onSuccess, Closure onFailure) {
        String resourcePath = "/entries/${entryId}"

        // params
        def queryParams = [:]
        def headerParams = [:]
        def bodyParams
        def contentType

        // verify required params are set
        if (entryId == null) {
            throw new MissingPropertyException('missing required params entryId')
        }

        contentType = 'application/json'
        bodyParams = entryUpdate

        apiUtils.invokeApi(onSuccess, onFailure, null, null, resourcePath, queryParams, headerParams, bodyParams, contentType,
            'PATCH', '',
            Entry
        )
    }

}
