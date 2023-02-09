# nf-quilt

Nextflow plugin for interacting with Quilt packages as a FileSystem

`nf-quilt` (v0.3.2 or later) is a NextFlow [plugin](https://www.nextflow.io/docs/latest/plugins.html)
developed by [Quilt Data](https://quiltdata.com/) that enables you read and write directly
to Quilt packages using `quilt+` URIs wherever you currently use `s3`, `az` or `gs` URIs.

Inspired by the original `nf-quilt` plugin (v0.2.0) developed by Seqera labs

## I. QuickStart (Local)

To quickly run `nf-quilt` from this GitHub repository:

```bash
git clone https://github.com/nextflow.io/nextflow.git
git clone https://github.com/quiltdata/nf-quilt.git
cd nf-quilt
pip install quilt3
make check # ./gradlew check
make pkg-test BUCKET=bucket-I-can-write-to
```

See "Running in Tower" below for how to use development versions of `nf-quilt` with
[NextFlow Tower](https://cloud.tower.nf).


## II. Usage (Production)

There are three simple steps to redirecting your existing NextFlow pipelines to read and write from
Quilt packages. Please note that it has only been tested on NextFlow version 22.10.6.

### 1. Construct a Quilt+ URI for each package

Each Quilt+ package URI has the form `quilt+s3://_bucket_#package=_prefix/suffix_`.
You must have read or write permissions to that `bucket`,
and your environment must have the corresponding
[AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html).

If you are running the Quilt web catalog, you can find the Quilt+ URI
for an existing package in the `<> CODE | URI` section at the top.
Note that you can write to package that does not yet exist,
as long as you can access the bucket.

### 2. Set the appropriate parameter(s) in your pipeline

There is no formal convention, but most pipelines specify a parameter such as 'outdir' or 'pub'
which is used as the argument to `publishDir`, and 'input' or 'reads' for the source channel.
You can substitute a Quilt+ URI anywhere you currently use an S3 URI.
Note that keys specified on the command-line override the corresponding 'params.key' in your script.

### 3. Tell NextFlow to use the nf-quilt plugin

If using a published version of nf-quilt (e.g., v0.3.2 when it is avilable),
there are two ways to tell NextFlow to use it.
You can edit `nextflow.config` to add it permanently:

```groovy
plugins {
    id 'nf-quilt' version '0.3.2' # add only this line if you already have other plugins
}
```

Or you can specify it each time on the command-line, e.g.:

```bash

nextflow run ./main.nf -profile standard -plugins nf-quilt@0.3.2 --pub 'quilt+s3://bucket#package=prefix/suffix'
```

You can also run published versions of `nf-quilt` directly in NextFlow Tower.

## III. Using Development Versions

If you want to use an unpublished plugin, you must run it with a development version of `nextflow`.
The simplest way to do that is to pull them both directly from GitHub:

```bash
git clone https://github.com/nextflow.io/nextflow.git
git clone https://github.com/quiltdata/nf-quilt.git
cd ./nf-quilt
```

You also need to use Python to install the `quilt3` command-line tool used by `nf-quilt`:

```bash
pip install quilt3
which quilt3
```

### Testing Installation

To verify that the plugin, nextflow, and your AWS credentials have been properly installed,
type:
```bash
make pkg-test BUCKET=my-s3-bucket #  copies the `test/hurdat` package to `s3://my-s3-bucket`
```

### Running Locally

From inside the `nf-quilt` directory, call `./launch.sh` with a path to your pipeline.

For example, with a standard `nf-core` pipeline like `sarek`:

```bash
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt --outdir "quilt+s3://bucket#package=nf-quilt/sarek&path=."
```

Otherwise, replace `nf-core/sarek` with the local path to your pipeline's `.nf` file

### Running in Tower

The [NextFlow Tower](https://cloud.tower.nf) web application does not currently support unpublished plugins.
However, you can use `nextflow -with-tower` to push local plugins to Tower.

#### Local Tower Configuration

If you have not previously run with Tower locally, you need to first
[configure](https://help.tower.nf/22.3/getting-started/usage/) your local environment:
```bash
export NXF_VER=22.10.6
export TOWER_ACCESS_TOKEN=eyxxxxxxxxxxxxxxxQ1ZTE=
export TOWER_WORKSPACE_ID=000000000000000
```

#### Testing Tower Configuration

To verify that Tower has been properly configured, try:

```bash
make tower-test BUCKET=my-s3-bucket #  copies the `test/hurdat` package to `s3://my-s3-bucket`
```

The Makefile will also read any configured variables in a `.env` file from that directory.


#### Testing Your Pipeline

Before adding `nf-quilt`, you should ensure your pipeline can be invoked locally.

To start with, you can run your pipeline in Tower and copy the Launch command, e.g.:
```bash
nextflow run 'https://github.com/nf-core/rnaseq'
       -name hopeful_lavoisier
       -params-file 'https://api.tower.nf/ephemeral/LqmfOXbqlUvw5d2CzE2nYg.json'
       -with-tower
       -r 3.10.1
       -profile test
```

However, you usually cannot access `api.tower.nf` from your command-line.
Instead, you need to:

1. Copy the "Pipeline parameters" from the "Launch settings" page into, say 'params.json'
2. Use that new file as the argument for `-params-file`
3. Replace `nextflow` with `./launch.sh`
4. Change the `-name` to something different, e.g. `quilt_test_1`

Then run the resulting command locally on your machine, e.g.:

```bash
./launch.sh run 'https://github.com/nf-core/rnaseq' \
   -name quilt_test_1 \
   -params-file params.json \
   -with-tower \
   -r 3.10.1 \
   -profile test \
```

#### Running Your Pipeline with `nf-quilt`

Once that works, you can extend it as described in section II:

1. Find which parameters in `params.json` are used for input/output
2. Overrid them with appropriate Quilt+ URIs
3. Tell NextFlow to use `-plugins nf-quilt`

```bash
./launch.sh run 'https://github.com/nf-core/rnaseq' \
   -name quilt_test_2 \
   -params-file params.json \
   -with-tower \
   -r 3.10.1 \
   -profile test \
   -plugins nf-quilt \
   -outdir "quilt+s3://quilt-ernest-staging#package=test/tower"
```

See [Tower Documentation](https://help.tower.nf/22.3/getting-started/usage/) for more on how to use `-with-tower` directly.


## IV. Package, upload and publish

The project should be hosted in a GitHub repository whose name should match the name of the plugin, 
that is the name of the directory in the `plugins` folder (e.g. `nf-quilt`).

Follow these steps to package, upload and publish the plugin:

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
