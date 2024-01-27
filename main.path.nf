#!/usr/bin/env nextflow
/* groovylint-disable CompileStatic */

nextflow.enable.dsl=2

test_file_local = 'README.md'
test_file_s3 = 's3://quilt-example/examples/hurdat2/README.md'
test_file_quilt = 'quilt+s3://quilt-example#package=examples/hurdat2&path=README.md'

myFileChannel = Channel.fromList([file(test_file_local), file(test_file_s3), file(test_file_quilt)])

process CHECK_INPUT {
    input:
        path input

    output:
        path 'README.md', emit: output

    script:
    """
    ls -l
    echo $input
    if [ "$input" != "README.md" ]; then
        cp -f $input README.md
    fi
    """
}

workflow {
    CHECK_INPUT(myFileChannel)
}
