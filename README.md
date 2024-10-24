# nf-quilt

Nextflow plugin for reading and writing Quilt packages as a FileSystem

[`nf-quilt`](https://github.com/quiltdata/nf-quilt) (v0.3.2 or later) is a Nextflow [plugin](https://www.nextflow.io/docs/latest/plugins.html)
developed by [Quilt Data](https://quiltdata.com/) that enables you read and write directly
to Quilt packages using `quilt+s3` URIs wherever your Nextflow pipeline currently use `s3` URIs.

## NEW: Use nf-quilt plugin with existing S3 URIs

In v0.8+, the plugin can even be used with "native" S3 URIs.  You can continue using your exising S3 URIs,and Nextflow will write the data out as usual.  However, simply by adding the `nf-quilt` plugin, you can also "overlay" that data with a Quilt package containing all the metadata from that run.

For example:

```shell
nextflow run nf-core/rnaseq -plugins nf-quilt --outdir "s3://quilt-example-bucket/test/nf_quilt_rnaseq"
# other parameters omitted for brevity
```

will automatically create the package:

```url
quilt+s3://quilt-example-bucket#package=test/nf_quilt_rnaseq
```

## I. Using the nf-quilt plugin in Production

This plugin allows your existing pipelines, without modification,
to read and write versioned Quilt packages stored on Amazon S3.

Use the following three steps to configure Nextflow Tower or your command-line environment.
[Note: versions 0.7.0 and later no longer require the `quilt3` Python client.]

1. Enable the `nf-quilt` plugin

The usual way to enable a plugin is to add the following to your `nextflow.config` file,
or (in Tower) the "Advanced Options ->  Nextflow config file":

```groovy
plugins {
    id 'nf-quilt'
}
```

![Example Tower Configuration](./README-Tower.png)

You can alternatively specify the plugin as part of the command-line, .e.g.:

```bash
nextflow run ./main.nf -profile standard -plugins nf-quilt --outdir 'quilt+s3://bucket#package=prefix/suffix'
```

## II. Working With Quilt+ URIs (optional for output)

1. Obtain a Quilt+ URI for each package

Each Quilt+ package URI you read or write from has the form:

```string
quilt+s3://bucket#package=prefix/suffix
```

You must have the appropriate read or write permissions for that `bucket`,
and your environment must have the corresponding
[AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html).

In the Quilt catalog, you can find the Quilt+ URI for an existing package
in the `<> CODE | URI` section at the top.
You can also manually create URIs for new packages that don't yet exist.

See 'Advanced Options' below for more details.

1. Set the appropriate parameter(s) in your pipeline

For `nf-core` pipelines, use `--input` to read and `--outdir` to write.
Otherwise, whatever is passed to `Channel.fromPath` as input
and to `publishDir` as outdir.

You can also specify these as YAML to pass to `-params-file`:

```yaml
input: "quilt+s3://quilt-example#package=examples/hurdat"
outdir: "quilt+s3://nf-core-gallery#package=test/hurdat"
```

Note that `--key` on the command-line corresponds to `params.key` in your script.

1. Optional: use a pre-release plugin

If a plugin is not yet available in the Nextflow plugin registry, you can use a pre-release version.
From the command-line, do, e.g.:

```bash
# export NXF_VER=23.04.3
export LOG4J_DEBUG=true  # for verbose logging
export NXF_PLUGINS_TEST_REPOSITORY=https://github.com/quiltdata/nf-quilt/releases/download/0.8.7/nf-quilt-0.8.7-meta.json
nextflow run main.nf -plugins nf-quilt@0.8.7
```

For Tower, you can use the "Pre-run script" to set the environment variables.

## III. Advanced URI Options

There are a number of additional parameters you can add to Quilt+ URIs,
in order to customize the behavior of the plugin:

* Fragment Parameters:
  * **catalog**: specify the DNS hostname of the Quilt catalog to use (default: `open.quiltdata.com`)
  * **force**: force package update (even if already exists or local copy out-of-date)
  * **package**: specify the name of the package to read or write (default: `.`)
  * **path**: specify a path within the package to read or write (default: `.`) [not fully supported yet]
  * **workflow**: specify the name of a workflow to use for metadata validation (default: none)

* Query Parameters: also stored as package-level metadata
  * **msg**: specify the commit message to use when saving the package
  * **readme**: specify a string for the package README_NF_QUILT.md file
    (will substitute "${variables}"), or SKIP to not create a README
  * **metadata**: specify SKIP to not push any new metadata (implicit or explicit)
  * **_any other key_**: specify any other metadata key to store in the package

See below for more details.
When running from the git repository,
you can use the Makefile to test the various options,
as long as you set a WRITE_BUCKET:

```bash
export WRITE_BUCKET=bucket-with-write-access
make pkg-test  # create "test/hurdat" package on s3://$WRITE_BUCKET
```

### A. Quilt+ URIs for Metadata Workflows

Sometimes you may want to ensure the created package contains specific metadata.
This is done using [Quilt workflows](https://docs.quiltdata.com/advanced/workflows).
Specify the workflow name as an additional `workflow=` fragment parameter,
and any metadata properties as part of the query string.

```bash
make pkg-test QUERY='?mkey1=val1&mkey2=val2' FRAGMENT='&workflow=my_workflow'
```

Note that specifying a workflow means that package creation will fail (and nothing will be saved)
if the query string does not contain all the required metadata,
so you should carefully test it before running long jobs.

### B. Quilt+ URIs for Custom Data Products

Version 0.3.4 and later allow you to customize both the `msg`
and `readme` via metadata query keys:

```bash
make pkg-test QUERY='?msg=text+str&readme=GStr+%24msg+%24now+%24%7Bmeta[%22quilt%22]%7D'
```

The `readme` parameter is a Groovy GString template which expands the `${variables}`:

* `msg`: the current commit message
* `now`: the ISO 8601 date and time
* `meta`: the complete metadata (very large! use only  subsets)

## III. Running from Git

To quickly run `nf-quilt` from this GitHub repository:

```bash
git clone https://github.com/quiltdata/nf-quilt.git
cd nf-quilt
make test-all # runs unit tests and installs dependencies
export WRITE_BUCKET=bucket-with-write-access
make pkg-test  # create "test/hurdat" package on s3://$WRITE_BUCKET
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt \
            --outdir "quilt+s3://$WRITE_BUCKET#package=nf-quilt/sarek"
```

### Using Development Versions

If you want to use an unpublished plugin, you must run it with a development version of `nextflow`.
The simplest way to do that is to pull them both directly from GitHub:

```bash
git clone https://github.com/nextflow.io/nextflow.git
git clone https://github.com/quiltdata/nf-quilt.git
cd ./nf-quilt
```

### Unit Testing

You can compile run unit tests with:

```bash
make check
```

### Verifying Nextflow

If this is your first time using Nextflow, you may also need to install a recent
[version of Java](https://www.java.com/en/download/help/download_options.html) for your platform.
Nextflow itself will take care of all the other dependencies.

You can verify and compile Nextflow with:

```bash
make nextflow
```

### Testing Installation

To verify that the plugin, nextflow, and your AWS credentials have been properly installed,
type:

```bash
   ./launch.sh run ./main.nf -profile standard -plugins $(PROJECT) --pub "quilt+s3://bucket#package=test/hurdat"
```

Replace "bucket" with an S3 bucket that those credentials can write to.

### Running a Pipeline Locally

From inside the `nf-quilt` directory, call `./launch.sh` with a path to your pipeline.

For example, with a standard `nf-core` pipeline like `sarek`:

```bash
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt --outdir "quilt+s3://bucket#package=nf-quilt/sarek"
```

Otherwise, replace `nf-core/sarek` with the local path to your pipeline's `.nf` file,
and replace `outdir` with the appropriate parameter for `publishDir`.

## Appendix: Publishing the Plugin for Others to Use

The project should be hosted in a GitHub repository whose name should match the name of the plugin,
that is the name of the directory in the `plugins` folder (e.g. `nf-quilt`).

If your system is properly configured, use `make publish` to package, upload, and publish the plugin.

Otherwise, follow these steps:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):

   * `github_organization`: the GitHub organisation where the plugin repository is hosted.
   * `github_username`: The GitHub username granting access to the plugin repository.
   * `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   * `github_commit_email`: The email address associated with your GitHub account.

2. Use the following command to package and create a release for your plugin on GitHub:

   ```bash
   ./gradlew :plugins:nf-quilt:upload
   ```

3. Fork the [nextflow-io/plugins](https://github.com/nextflow-io/plugins) repository to one you can write to

4. Use the following command to publish your plugin to your fork:

   ```bash
   ./gradlew :plugins:publishIndex
   ```

5. Create a pull request to push your changes back to [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json)
