package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class EntryUpdate {

    /* IDs of users to set as the entry's authors. */
    String authorIds
    /* Schema fields to set on the entry */
    Map fields
    /* ID of the folder that will contain the entry */
    String folderId
    /* New name of the entry */
    String name
    /* ID of the schema for the entry */
    String schemaId

}
