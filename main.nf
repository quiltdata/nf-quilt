#!/usr/bin/env nextflow
nextflow.enable.dsl=2

params.src = 'quilt+s3://quilt-example#package=examples/hurdat'
params.pub = '/var/tmp'
params.out = 'output'

packageFiles = Channel.fromPath(params.src)

process transfer {
    publishDir params.pub, mode: 'copy', overwrite: true

    input:
      path x

    output:
      path params.out + '/*'

    """
    mkdir -p $params.out
    cp -r $x $params.out
    echo $params.out/$x
    """
}

workflow {
  packageFiles | transfer | view { it }
}
