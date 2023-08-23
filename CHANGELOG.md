# Changelog

## [0.4.4] 2023-08-18 UNRELEASED

- Add parameter to skip README and metadata (default is to include both)
- Verify package merges in any existing metadata before push
- Do NOT append to README.md; overwrite (or just skip)
- JDK 11 compatibility (e.g., remove stripIndent)

## [0.4.3] 2023-08-04

- Do not publish Quilt package unless workflow succeeds
- [or the metadata specifies &force=true]

## [0.4.2] 2023-07-02

- Fix normalizePath test (return exact match, else last de-pathed match)
- Skip creating README.md if zero-length string

## [0.4.1] 2023-06-02

Beta release (not yet on nextflow-io/plugins)

- Only match unpinned packages for normalizePath
- Append to README.md if it exists

## [0.4.0] 2023-05-19

- Use `msg` fragment parameter as commit message when writing packages
- Removed Benchling support (will add back in a future release)
- Don't crash when writing to Quilt+ URIs with `&path=` fragments (by ignoring that part)

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
