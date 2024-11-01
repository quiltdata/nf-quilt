#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2

params.input = 'quilt+s3://udp-spec#package=nf-quilt/source'
params.outdir = '/var/tmp'

packageFiles = Channel.fromPath(params.input)

process transferFail {
    publishDir params.outdir, mode: 'copy', overwrite: true

    container 'ubuntu:20.04'

    input:
    path x

    output:
    path 'output/*'

    beforeScript = 'exit 1'

    """
    mkdir -p output
    cp -r $x output
    echo output/$x
    """
}

workflow {
    packageFiles | transferFail | view { file -> file }
}
