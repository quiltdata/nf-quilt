#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2
params.hash =  'c4e44f6932f13f626b8640d54fa235c1bfea675f1ad3b5b022a99b3aeb18c637'
params.input = "quilt+s3://udp-spec#package=nf-quilt/source@${params.hash}"

packageFiles = Channel.fromPath(params.input)

process transfer {
    publishDir(
        path: 'quilt+s3://udp-spec#package=nf-quilt/dynamic',
        mode: 'copy',
    )
    container 'ubuntu:20.04'

    input:
    path x

    output:
    path 'inputs/**'

    """
    mkdir -p data
    cp -r $x inputs/
    echo inputs/$x
    """
}

workflow {
    packageFiles | transfer | view { file -> file }
}
