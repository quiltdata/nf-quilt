package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.benchling.jackson.nullable.JsonNullable;
import com.benchling.model.ArchiveRecord;
import com.benchling.model.FieldType;

@Canonical
class FieldDefinition {
    
    ArchiveRecord archiveRecord
    
    String id
    
    Boolean isMulti
    
    Boolean isRequired
    
    String name
    
    FieldType type
}
