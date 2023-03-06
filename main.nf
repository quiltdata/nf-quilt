#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2

params.src = 'quilt+s3://quilt-example#package=examples/hurdat'
params.pub = '/var/tmp'

packageFiles = Channel.fromPath(params.src)

process transfer {
    publishDir params.pub, mode: 'copy', overwrite: true

    input:
    path x

    output:
    path 'output/*'

    """
    mkdir -p output
    cp -r $x output
    echo output/$x
    """
}

workflow {
    packageFiles | transfer | view { file -> file }
}
