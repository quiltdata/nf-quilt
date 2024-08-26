#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */
/* $ ./launch.sh run ./main.s3.nf */


nextflow.enable.dsl = 2
params.input = './out/**'
params.outdir = 's3://udp-spec/nf-quilt/s3-only/'

packageFiles = Channel.fromPath(params.input)

process transfer {
    publishDir params.outdir, mode: 'copy', overwrite: true
    container 'ubuntu:20.04'

    input:
    path x

    output:
    path 'resourced/**'

    """
    mkdir -p resourced
    cp -r $x resourced/
    echo resourced/$x
    """
}

workflow {
    packageFiles | transfer | view { file -> file }
}
