package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.benchling.jackson.nullable.JsonNullable;
import com.benchling.model.CustomField;
import com.benchling.model.EntryArchiveRecord;
import com.benchling.model.EntryCreator;
import com.benchling.model.EntryDay;
import com.benchling.model.EntryReviewRecord;
import com.benchling.model.Field;
import com.benchling.model.SchemaProperty4;
import com.benchling.model.UserSummary;

@Canonical
class Entry {
    /* The canonical url of the Entry in the API. */
    URI apiURL
    
    EntryArchiveRecord archiveRecord
    /* Array of users assigned to review the entry, if any.  */
    List<UserSummary> assignedReviewers = new ArrayList<>()
    /* Array of UserSummary Resources of the authors of the entry. This defaults to the creator but can be manually changed.  */
    List<UserSummary> authors = new ArrayList<>()
    /* DateTime the entry was created at */
    Date createdAt
    
    EntryCreator creator
    
    Map<String, CustomField> customFields = new HashMap<>()
    /* Array of day objects. Each day object has a date field (string) and notes field (array of notes, expand further for details on note types).  */
    List<EntryDay> days = new ArrayList<>()
    /* User-friendly ID of the entry */
    String displayId
    /* ID of the Entry Template this Entry was created from */
    String entryTemplateId
    
    Map<String, Field> fields = new HashMap<>()
    /* ID of the folder that contains the entry */
    String folderId
    /* ID of the entry */
    String id
    /* DateTime the entry was last modified */
    String modifiedAt
    /* Title of the entry */
    String name
    
    EntryReviewRecord reviewRecord
    
    SchemaProperty4 schema
    /* URL of the entry */
    String webURL
}
