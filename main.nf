#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl = 2
params.hash =  '90527238d0e2ebc9e1ba98265e5f79440b557697fed3f0d29ec95942cbb4e4e0'
params.input = "quilt+s3://nf-core-gallery#package=test/observer@${params.hash}&catalog=demo.quiltdata.com"
params.outdir = '/var/tmp'

process transfer {
    publishDir params.outdir, mode: 'copy', overwrite: true
    container 'ubuntu:20.04'

    input:
    path x

    output:
    path 'inputs/**'

    script:
    """
    mkdir -p data
    cp -r $x inputs/
    echo inputs/$x
    """
}

workflow {
    Channel.fromPath(params.input) | transfer | view { file -> file }
}
