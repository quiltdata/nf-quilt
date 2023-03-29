package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.benchling.jackson.nullable.JsonNullable;
import com.benchling.model.Entry;
import com.benchling.model.EventBaseSchema;

@Canonical
class EntryUpdatedFieldsEvent {
    
    Date createdAt
    
    Boolean deprecated
    /* These properties have been dropped from the payload due to size.  */
    List<String> excludedProperties = new ArrayList<>()
    
    String id
    
    EventBaseSchema schema
    /* These properties have been updated, causing this message  */
    List<String> updates = new ArrayList<>()
    
    Entry entry
    
    String eventType
}
