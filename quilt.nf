#!/usr/bin/env nextflow
nextflow.enable.dsl=2

//params.src = 'quilt+s3://quilt-example#package=examples/hurdat'
params.pub = 'quilt+s3://quilt-ernest-staging#package=test/hurdat'
params.src = '/Users/quilt/Downloads/Packages/igv_demo'
//params.pub = '/Users/quilt/Downloads/Packages/test_nf22'
params.out = 'output'

pkg_files = Channel.fromPath(params.src)

process publish {
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
  pkg_files | publish | view { it }
}
