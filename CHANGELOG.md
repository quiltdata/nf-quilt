# Changelog

## [1.1.1] 2026-11-18

- Packages for `s3://` outdirs now reference the published outputs in place (pinned to their S3 version IDs) instead of copying every output to the head node's temp dir and uploading it again. Only nf-quilt's generated files (README, summary, metadata) are uploaded
  - Hashes come from the task's local output only when its MD5 matches the object's S3 ETag (single-part or common multipart chunk sizes), proving the bytes are the same; otherwise the pinned object version is streamed from S3 (still no local disk)
  - This run's outputs take precedence over stale files from an installed earlier revision of the same package
  - Directories are listed when published, so the package has exactly this run's files, not stale files left under that S3 prefix
  - Entries point at each output's actual S3 key, so outdirs less than two levels below the bucket (package `<prefix>/default_suffix`) work too
  - If a recorded output can't be read from S3 at the end of the run, the push fails (listing each output and the reason) instead of publishing an incomplete package; this is checked, and the workflow validated, before any output is hashed
  - `summarize` globs also match these referenced outputs
  - Workflow validation (`quilt.workflow`) still runs before anything is written

## [1.1.0] 2026-11-18

- When `--outdir` is below the package root (e.g. `s3://bucket/ns/pkg/run-name/` or `quilt+s3://bucket#package=ns%2fpkg&path=run-name`), write `README_NF_QUILT.md`, `quilt_summarize.json` and `nf-quilt/{config,params,workflow}.json` under that sub-path instead of the package root (#341)
  - `quilt_summarize.json` only lists files under the sub-path, with paths relative to it, so one run's summary no longer includes another run's outputs
  - **Behavior change:** `summarize` globs (default or from `quilt.summarize`) are now matched relative to that sub-path, not the package root
  - If `params.outdir` is missing or resolves to a different package, files are written at the package root as before, with a warning
- Add a `make deep-run` smoke test that publishes with an outdir one level below the package
- Fix `s3://` outdirs pushing packages without the run's outputs: each file (or directory) published to a plain S3 outdir is now copied into the package via `copyToCache`, from the task's output or, if that is gone (`publishDir` mode `'move'`, or no source on older Nextflow), from the published copy
  - Published directories are copied in full, following symlinks (staged inputs are links in the work dir)
  - If any output can't be copied, that package is not pushed (an error names the count), rather than pushing an incomplete package with a SUCCESS message
  - Overlay file names with `+`, `&`, `=`, `%`, `#` or spaces keep their exact names (previously mangled, or a crash for `%`)

## [1.0.4] 2026-11-18

### Fixes

- Fix publishing to `quilt+s3://` on Nextflow 26.x: drop non-standard copy options (e.g. `SkipDirOption[.fusion]`) before copying into the local package cache, which previously failed every publish with `UnsupportedOperationException` (#346)
- Fix `QuiltParser` path order when a package URI has extra segments plus `&path=`: `package=ns/name/run&path=sub` now parses to path `run/sub` instead of `sub/run` (#349)
  - **Behavior change:** outputs published to such a `quilt+s3://` outdir now land under `run/sub/...` instead of `sub/run/...`
- Fold extra package segments into the path even when the package has an `@hash` or `:tag` (`package=ns/name/run@hash` is now package `ns/name`, path `run`), and drop empty segments from `ns/name//run` (#349)
- `QuiltPathify.copyToCache` places an overlay file at its package sub-path (e.g. `a_folder/FILE.md`) instead of flattening it to the bare filename, overwrites stale cached copies, and refuses sub-paths outside the package folder. The helper is not yet called during publishing, so this does not change run output (#352)

### Security and dependencies

- Fix all CVEs reported in the bundled (runtime) dependencies: netty 4.2.21.Final, vert.x 5.0.12, jackson 2.23.0, and log4j 2.26.1 as the SLF4J binding in place of log4j 3.0.0-beta3. Vulnerability scanners (grype, osv-scanner, trivy) now block CI (#351)
- Update vert.x to 5.2.3 and `io.nextflow.nextflow-plugin` to 1.0.0-beta.16 (#353)
- Build with Gradle 9.9.1 (#353, #354)
- Lock runtime dependencies in `gradle.lockfile` (run `make lock` after changing dependencies by hand) (#347)

### Build and CI

- Build with `./gradlew` from JDK 25: the Gradle daemon now runs on JDK 21, downloading Temurin 21 when no local JDK 21 is found (#345)
- Run the CI tests on Java 17, 21 and 25 rather than always on the JDK 21 toolchain; `-PtestJavaVersion=<N>` runs them on an installed JDK N (#348)
- Upgrade MegaLinter to v10 and make it pass; group Renovate updates into one minor and one major PR (#347), run Renovate's Gradle updates on Java 21 (#355), and update the GitHub Actions used by CI (#354)

## [1.0.1] 2026-04-30

- Disable Jackson `FAIL_ON_EMPTY_BEANS` so config serialization survives Nextflow 25.10.x dynamic process directive closures that transitively pull in `SecretsLoader$1` (#332)
- Add `make bump [LEVEL=patch|minor|major]` target backed by `wf/bump-version.sh`

## [1.0.0] 2026-04-29

- First release published to the [Nextflow Plugin Registry](https://registry.nextflow.io)
- Use "package" as default prefix
- Modernize main*.nf files
- Fix Jackson serialization of `java.time.OffsetDateTime` from Nextflow 24.10+ workflow metadata (#321)
- Restore working test input package (replace deleted `nf-core-gallery` with `udp-spec/nf-quilt/source`)
- Remove `wf/main.gallery.nf` (referenced the deleted `nf-core-gallery` bucket)
- Migrate to the `io.nextflow.nextflow-plugin` Gradle build system; collapse the
  legacy `plugins/nf-quilt/` subproject into a single-module layout (#323, #324)
- Publish releases via the [Nextflow Plugin Registry](https://registry.nextflow.io)
  using `./gradlew releasePlugin`; drop the `nextflow-io/plugins` PR flow and
  `NXF_PLUGINS_TEST_REPOSITORY` GitHub-release pinning (#323)
- Update README and developer documentation to reflect the new build/publish
  workflow (`make assemble` / `install` / `package` / `release`)
- Restore the JaCoCo line-coverage gate (70% minimum) wired into `./gradlew check`

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
