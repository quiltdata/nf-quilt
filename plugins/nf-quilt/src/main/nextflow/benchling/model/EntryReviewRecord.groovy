package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class EntryReviewRecord {

    /* Reviewer's Comments */
    String comment
    /* Review Status of the entry */
    String status

}
