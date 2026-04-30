# Developer Documentation

## Using Pre-Release Versions

Once a version is published to the [Nextflow Plugin
Registry](https://registry.nextflow.io), users can install it directly via
`-plugins nf-quilt@<version>`; no GitHub-release pinning or
`NXF_PLUGINS_TEST_REPOSITORY` override is required.

```bash
nextflow run main.nf -plugins nf-quilt@0.9.2
```

To pin the plugin in `nextflow.config`:

```groovy
plugins {
    id 'nf-quilt@0.9.2'
}
```

For Tower, set this in the "Advanced Options -> Nextflow config file" (or in
the "Pre-run script" if you need to override env vars).

## Using Development Versions

To work on `nf-quilt` locally, clone the repo and build it with the supplied
Gradle wrapper. The build now uses the
[`io.nextflow.nextflow-plugin`](https://docs.seqera.io/nextflow/guides/migrate-plugin)
Gradle plugin (single-module layout — no `plugins/` subdirectory or
`buildSrc`).

```bash
git clone https://github.com/quiltdata/nf-quilt.git
cd ./nf-quilt
```

### Verifying Java

You need JDK 21 (Nextflow 24.10+ requirement). On macOS you can install it via
Homebrew:

```bash
brew install openjdk@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Running from Git

```bash
# compile + run unit tests
make test-all
# create a "test/dest-<version>" package on s3://$WRITE_BUCKET
make pkg-test WRITE_BUCKET=your-writeable-bucket
```

`pkg-test` requires that the current `Plugin-Version` has already been
installed locally via `make install` (which runs `./gradlew installPlugin`).

This ensures you have properly installed Nextflow and configured your local
<--markdownlint-disable-next-line MD041-->
[AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html).

You can also set `WRITE_BUCKET` and other parameters in a `.env` file in the
project root, and they will be automatically read by the Makefile.

### Running a Pipeline Locally

After `make install`, the plugin is available to any local `nextflow` invocation
via `-plugins nf-quilt@<version>`. For example, with a standard `nf-core`
pipeline like `sarek`:

```bash
nextflow run nf-core/sarek -profile test,docker -plugins nf-quilt@0.9.2 \
  --outdir "quilt+s3://bucket#package=nf-quilt/sarek"
```

Or with a local pipeline:

```bash
nextflow run ./main.nf -profile standard -plugins nf-quilt@0.9.2 \
  --outdir "quilt+s3://bucket#package=test/hurdat"
```

There are standard Makefile targets for both `sarek` and `fetchngs` pipelines,
which test against the latest published version of nf-quilt:

```bash
make sarek
make fetchngs
```

### Build Targets

| Target | Action |
| --- | --- |
| `make assemble` | Compile and build the plugin jar (`./gradlew assemble`) |
| `make install` | Install the built plugin into the local Nextflow plugin cache (`./gradlew installPlugin`) |
| `make package` | Produce the distributable plugin zip + metadata (`./gradlew packagePlugin`) |
| `make release` | Publish to the Nextflow Plugin Registry (`./gradlew releasePlugin`); requires `NPR_API_KEY` |
| `make check` | Run unit tests + JaCoCo coverage gate |
| `make coverage` | Generate the JaCoCo HTML report and open it |

### Unit Testing

You can compile and run all unit tests with:

```bash
make check
```

This also runs `jacocoTestCoverageVerification` (70% line coverage minimum).

To show the output of the tests, use:

```bash
make verify
```

To fast-fail on the first failing test, use:

```bash
make fast
```

## Publishing the Plugin to the Nextflow Plugin Registry

Releases are published to the [Nextflow Plugin
Registry](https://registry.nextflow.io). The flow is fully automated through
the `io.nextflow.nextflow-plugin` Gradle plugin — there is no longer a step
involving `nextflow-io/plugins` PRs or GitHub-release uploads.

1. Bump the `version` in `build.gradle` and add a corresponding section to
   `CHANGELOG.md`.
2. Set the registry API key:

   ```bash
   export NPR_API_KEY=<your-registry-api-key>
   ```

3. Publish:

   ```bash
   make release   # or: ./gradlew releasePlugin
   ```

This packages the plugin, uploads the artefacts, and registers the new version
with the registry. After it succeeds, users can install with `-plugins
nf-quilt@<version>` immediately.
