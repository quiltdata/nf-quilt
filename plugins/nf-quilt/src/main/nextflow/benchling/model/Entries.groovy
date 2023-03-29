package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.benchling.model.Entry;

@Canonical
class Entries {
    
    List<Entry> entries = new ArrayList<>()
}
