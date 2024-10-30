#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2
params.hash =  'c4e44f6932f13f626b8640d54fa235c1bfea675f1ad3b5b022a99b3aeb18c637'
params.input = "quilt+s3://udp-spec#package=nf-quilt/source@${params.hash}"

packageFiles = Channel.fromPath(params.input)

process transfer {
    publishDir(
        /* groovylint-disable-next-line LineLength */
        path: 'quilt+s3://udp-spec?Status=In-flight&Program=CCR4&sample_name=sample123&OwnerEmail=ownerexample.com&author_name=JohnDoe&project_code=PROJ_ABC&flowcell_id=FC123456&jira_id=JIRA-456&instrument_name=NextSeq2000&xaira_registry=xaira-scratch&session_id=abc123-def456-ghi789&workflow=None#package=nf-quilt/dynamic',
        mode: 'copy',
    )
    container 'ubuntu:20.04'

    input:
    path x

    output:
    path 'inputs/**'

    """
    mkdir -p data
    mkdir -p inputs
    cp -r $x inputs/
    echo inputs/$x
    """
}

workflow {
    packageFiles | transfer | view { file -> file }
}
