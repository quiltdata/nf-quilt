package com.benchling.model;

import groovy.transform.Canonical
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.benchling.model.EntryCreateAuthorIds;
import com.benchling.model.InitialTable;

@Canonical
class EntryCreate {
    
    EntryCreateAuthorIds authorIds
    /* Custom fields to add to the entry */
    Map customFields
    /* ID of the template to clone the entry from */
    String entryTemplateId
    /* Fields to set on the entry. Must correspond with the schema's field definitions.  */
    Map fields
    /* ID of the folder that will contain the entry */
    String folderId
    /* An array of table API IDs and blob id pairs to seed tables from the template while creating the entry. The entryTemplateId parameter must be set to use this parameter. The table API IDs should be the API Identifiers of the tables in the given template. - If a template table has one row, the values in that row act as default values for cloned entries. - If a template table has multiple rows, there is no default value and those rows are added to the cloned entry along with the provided csv data. - If a table has default values, they will be populated in any respective undefined columns in the csv data. - If a table has no default values, undefined columns from csv data will be empty. - If no csv data is provided for a table, the table in the entry will be populated with whatever values are in the respective template table.  */
    List<InitialTable> initialTables = new ArrayList<>()
    /* Name of the entry */
    String name
    /* ID of the entry's schema */
    String schemaId
}
