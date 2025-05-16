# Changelog

## [0.9.2] 2025-05-15

- Use "package" as default prefix
- Modernize main*.nf files
- Fix catalog URL handling in QuiltProduct.displayName()
- Fix boolean flag parsing from query string parameters
- Fix error message consistency in QuiltProduct.copyFile test

## [0.9.1] 2024-12-24

- Minor code cleanup

## [0.9.0] 2024-12-23

- Only supports Nextflow 24.10 and later (uses Groovy 4)
- Shift to quiltcore 0.1.6 convenience methods
- Shift metadata and configuration to `quilt` scope of nextflow.config
- Moved test workflows into `wf` folder
- Write the output URI when publishing
- Improve handling of dynamically-specified URIs
- Rewrite README.md, splitting out developer documentation to README_DEV.md

## [0.8.12] 2024-12-03 UNPUBLISHED

- Extract flags and metadata from `nextflow.config`
- Remove unused methods and keys
- Remove support for propertyName
- Use IntelliJ to fix types and lint

## [0.8.11] 2024-11-5 UNPUBLISHED

- Catch _all_ toJson errors

## [0.8.10] 2024-11-4 UNPUBLISHED

- Ignore publish to 'home'
- Fix: Cannot set property 'package_id' on null object

## [0.8.9] 2024-10-31 UNPUBLISHED

- Handle multiple/internal publishDir calls

## [0.8.8] 2024-10-31 UNPUBLISHED

- Debug build

## [0.8.7] 2024-10-23 UNPUBLISHED

- Use package cache instead of `params` to find output URIs (in order to support
  dynamic URIs set by, e.g. `main.nf`)
- Allow setting metadata from inside the workflow

## [0.8.6] 2024-09-11

- Fix addOverlay bug on subfolders
- Fix Windows tests
- Improve test coverage

## [0.8.5] 2024-09-10a

- Error with packaging subfolders on S3 overlay
- Improved overlay debugging

## [0.8.4] 2024-09-10

- Fix bug with unrecognized output URIs

## [0.8.3] 2024-09-08

- Fix Windows bug with overlay files

## [0.8.2] 2024-09-07

- Use copyFile rather than writeString for overlay files [requires NextFlow 23
  or later]
- Restore README and quilt_summarize to output

## [0.8.1] 2024-09-05

- Get output URI directly from params
- Add `dest` parameter to Quilt URIs inferred from S3 URIs
- Specify `outputPrefixes` using `quilt` section of `nextflow.config`
- Stop proactively installing packages

## [0.8.0] 2024-08-31

- Add and improve code coverage using jacoco
- Support S3 URIs as an overlay plugin
- Fix bug with pathless input URIs

## [0.7.17] UNRELEASED

- support ChunkedChecksums

## [0.7.16] 2024-06-17

- use prior package, if any, for relative paths

## [0.7.15] 2024-06-17

- use null bucket for relative paths (e.g., nf-schema)

## [0.7.14] 2024-06-14

- improve package name debugging

## [0.7.13] 2024-06-13

- catch (java.nio.file.NoSuchFileException e) on deinstall

## [0.7.12] 2024-05-16

- Fix getFileName()
- Pin PACKAGE_URL for examples/hurdat

## [0.7.11] 2024-05-14 UNOFFICIAL

- Republish correctly

## [0.7.10] 2024-05-10 FAILED TO UPLOAD

- Debug build of QuiltCore-Java
- Fail when pushing to read-only buckets

## [0.7.9] 2024-01-30

- Auto-install (needed for < 23.12?)

## [0.7.8] 2024-01-30 UNOFFICIAL

- Add debugging for installs

## [0.7.7] 2024-01-24 UNOFFICIAL

- Properly implement and test getFilename()
- Install package just before download
- Add and pass `path-input` integration test
- Add unit test for "&path=" Quilt+ URIs

## [0.7.6] 2024-01-10 UNOFFICIAL

- Re-enable crash on failure

## [0.7.5] 2023-10-17

- Grab all multiqc subfolders

## [0.7.4] 2023-10-16b UNOFFICIAL

- Actually print the exception stack trace
- `make install` to enable nextflow testing

## [0.7.3] 2023-10-16a UNOFFICIAL

- Catch and log exception on push failure

## [0.7.2] 2023-10-16 UNOFFICIAL

- Throw exceptions on push failure

## [0.7.1] 2023-10-15

- Fix [quiltdata/nf-quilt#145](https://github.com/quiltdata/nf-quilt/issues/145)
- Remove automatic pre-install of packages

## [0.7.0] 2023-10-05

- Officially QuiltCore 0.1.0 instead of Python

## [0.6.0] 2023-10-03 UNOFFICIAL

(interim release still using Python)

- Stop overwriting README.md (use README_NF_QUILT.md instead)
  - improve formatting
- Smarter quilt_summarize.json
  - Top level (only): md, html, pdf, csv, tsv
  - multiqc sub-folder HTML

## [0.5.0] 2023-09-04 UNOFFICIAL

- Switch to quiltcore-java [NOTE: this pre-release does NOT check workflows]
- Do not pre-install packages (only install before write)

## [0.4.5] 2023-08-23

- fix metadata in README.md
- write out nf-quilt/*.json files
- add summarize wildcards (md, html) for QuiltProduct
- redo normalizedPaths
- reduce logging (especially stack traces and filesystem calls)
- convert non-fatal errors to warnings

## [0.4.4] 2023-08-22

- JDK 11 compatibility (i.e., remove stripIndent)
- Add parameter to skip README and metadata (default is to include both)
- Improve test coverage of metadata and QuiltProduct

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
- Don't crash when writing to Quilt+ URIs with `&path=` fragments (by ignoring
  that part)

## [0.3.5] 2023-04-05

- Runs on >=22.10.6

## [0.3.4] 2023-04-04

- Alpha: Call Benchling API after publishing package
- accept "?commit_message=text+string&readme=GString" in Quilt+ URI
- Support Nextflow 23+

## [0.3.3] 2023-03-08

- Fail gracefully on `quilt3` errors
- Detailed Nextflow Tower instructions
- Call `quilt3` during integration tests
- Add full Quilt+ URL support, including metadata in query string

## [0.3.2] 2023-02-24

- First official release on
  [nextflow-io/plugins](https://github.com/nextflow-io/plugins/commits/main/plugins.json)
- Read and write from Quilt+ URIs
