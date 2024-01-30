#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl=2

test_file_local = 'README.md'
test_file_s3 = 's3://nf-core-gallery/nf-core/hlatyping/README_NF_QUILT.md'
test_file_quilt = 'quilt+s3://nf-core-gallery#package=nf-core/hlatyping&path=README_NF_QUILT.md'
// file(test_file_local), file(test_file_s3), file(test_file_quilt)
myFileChannel = Channel.fromList([file(test_file_s3), file(test_file_quilt)])

process CHECK_INPUT {
    container = 'quay.io/nextflow/bash'
    input:
        path input

    output:
        path 'README_NF_QUILT.md', emit: output

    script:
    """
    ls -l
    echo $input
    cp -f $input ../../tmp
    """
}

workflow {
    CHECK_INPUT(myFileChannel)
}
