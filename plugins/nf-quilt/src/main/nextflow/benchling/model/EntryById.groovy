package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.benchling.model.Entry;

@Canonical
class EntryById {
    
    Entry entry
}
