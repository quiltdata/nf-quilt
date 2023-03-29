package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.benchling.jackson.nullable.JsonNullable;
import com.benchling.model.FieldType;
import com.benchling.model.FieldValue;

@Canonical
class Field {
    
    String displayValue
    
    Boolean isMulti
    
    String textValue
    
    FieldType type
    
    FieldValue value
}
