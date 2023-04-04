package benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class EntryDay {

    /* A Date string */
    String date

    List<Object> notes = []

}
