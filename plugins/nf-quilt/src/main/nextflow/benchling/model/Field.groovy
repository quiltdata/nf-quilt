package com.benchling.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class Field {

    String displayValue

    Boolean isMulti

    String textValue

    FieldType type

    FieldValue value

}
