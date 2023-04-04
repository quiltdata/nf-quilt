package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class SchemaProperty4 {

    /* ID of the entry schema */
    String id
    /* DateTime the Entry Schema was last modified */
    String modifiedAt
    /* Name of the entry schema */
    String name

}
