#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl=2

test_file_local = 'README.md'
test_file_s3 = 's3://quilt-example/examples/protein-paint/README.md'
test_file_quilt = 'quilt+s3://quilt-example#package=examples/protein-paint&path=README.md'

myFileChannel = Channel.fromList([file(test_file_local), file(test_file_s3), file(test_file_quilt)])

process CHECK_INPUT {
    input:
        path input

    output:
        path 'README.md', emit: output

    script:
    """
    cp $input ../../tmp/
    """
}

workflow {
    CHECK_INPUT(myFileChannel)
}
