#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2

params.input = 'quilt+s3://quilt-example#package=examples/hurdat'
params.outdir = '/var/tmp'

packageFiles = Channel.fromPath(params.input)

process transfer {
    publishDir params.outdir, mode: 'copy', overwrite: true
    container 'ubuntu:20.04'

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
