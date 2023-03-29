package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Canonical
class EntryCreator {
    
    String handle
    
    String id
    
    String name
}
