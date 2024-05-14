#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2

params.input = 'quilt+s3://quilt-example#package=examples/hurdat'

packageFiles = Channel.fromPath(params.input)

process CHECK_INPUT {
    container 'ubuntu:20.04'
    input:
        path input

    output:
        path 'README.md', emit: output

    script:
    """
    ls -l
    echo $input
    cp -f $input ../../tmp
    """
}

workflow {
    packageFiles | CHECK_INPUT | view { file -> file }
}
