package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
// ArchiveRecord archiveRecord
class FieldDefinition {

    String id

    Boolean isMulti

    Boolean isRequired

    String name

    FieldType type

}
