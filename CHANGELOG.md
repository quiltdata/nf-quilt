# Changelog

## UNRELEASED

## [0.4.0] 2023-05-19

- Removed Benchling support (will add back in a future release)
- Added support for writing Quilt+ URIs with `#path=` fragments (by ignoring them)
- Use `msg` fragment parameter as commit message when writing packages

## [0.3.5] 2023-04-05

- Runs on >=22.10.6

## [0.3.4] 2023-04-04

- Alpha: Call Benchling API after publishing package
- accept "?commit_message=text+string&readme=GString" in Quilt+ URI
- Support NextFlow 23+

## [0.3.3] 2023-03-08

- Fail gracefully on `quilt3` errors
- Detailed NextFlow Tower instructions
- Call `quilt3` during integration tests
- Add full Quilt+ URL support, including metadata in query string

## [0.3.2] 2023-02-24

- First official release on [nextflow-io/plugins](https://github.com/nextflow-io/plugins/commits/main/plugins.json)
- Read and write from Quilt+ URIs
