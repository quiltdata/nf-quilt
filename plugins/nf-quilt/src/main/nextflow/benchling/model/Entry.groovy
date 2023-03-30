/* groovylint-disable LineLength */
package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class Entry {

    /* The canonical url of the Entry in the API. */
    URI apiURL

    //EntryArchiveRecord archiveRecord
    /* Array of users assigned to review the entry, if any.  */
    //List<UserSummary> assignedReviewers = []
    /* Array of UserSummary Resources of the authors of the entry. This defaults to the creator but can be manually changed.  */
    //List<UserSummary> authors = []
    /* DateTime the entry was created at */
    Date createdAt

    //EntryCreator creator

    // Map<String, CustomField> customFields = [:]
    /* Array of day objects. Each day object has a date field (string) and notes field (array of notes, expand further for details on note types).  */
    //List<EntryDay> days = []
    /* User-friendly ID of the entry */
    String displayId
    /* ID of the Entry Template this Entry was created from */
    String entryTemplateId

    Map<String, Field> fields = [:]
    /* ID of the folder that contains the entry */
    String folderId
    /* ID of the entry */
    String id
    /* DateTime the entry was last modified */
    String modifiedAt
    /* Title of the entry */
    String name

    //EntryReviewRecord reviewRecord

    //SchemaProperty4 schema
    /* URL of the entry */
    String webURL

}
