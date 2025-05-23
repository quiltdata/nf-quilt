#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl=2


process CHECK_INPUT {
    container 'ubuntu:20.04'
    input:
        path input

    output:
        path 'COPY_THIS.md', emit: output

    script:
    """
    ls -l
    echo $input
    cp -f $input ../../tmp
    """
}

workflow {
    test_file_local = 'work/COPY_THIS.md'
    test_file_s3 = 's3://udp-spec/nf-quilt/source/COPY_THIS.md'
    test_file_quilt = 'quilt+s3://udp-spec#package=nf-quilt/source&path=COPY_THIS.md'

    myFileChannel = Channel.fromList([file(test_file_local), file(test_file_s3), file(test_file_quilt)])
    CHECK_INPUT(myFileChannel)
}
