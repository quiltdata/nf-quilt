# nf-quilt

Nextflow plugin for interacting with Quilt packages as a FileSystem

[`nf-quilt`](https://github.com/quiltdata/nf-quilt) (v0.3.2 or later) is a NextFlow [plugin](https://www.nextflow.io/docs/latest/plugins.html)
developed by [Quilt Data](https://quiltdata.com/) that enables you read and write directly
to Quilt packages using `quilt+s3` URIs wherever you currently use `s3`, `az` or `gs` URIs.

Inspired by the original [`nf-quilt`](https://github.com/nextflow-io/nf-quilt) plugin (v0.2.0) developed by Seqera labs

# Prerequisite:  `quilt3` command-line tool

Depending on your configuration, you may need to first install the `quilt3` command-line tool.
This is distributed as an open source Python package you can install using `pip`,
and must be available in the PATH used by `nextflow`

```bash
pip install quilt3
which quilt3
```

## I. QuickStart

To quickly run `nf-quilt` from this GitHub repository:

```bash
git clone https://github.com/quiltdata/nf-quilt.git
cd nf-quilt
make test-all # runs unit tests and installs depdencies
make pkg-test BUCKET=destination-bucket # create "test/hurdat" package
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt \
            --outdir "quilt+s3://destination-bucket#package=nf-quilt/sarek"

```
## II. Usage

_NOTE: If using a published version of `nf-quilt` (e.g., v0.3.2 when it is avialable),
you can start using this immediately with [NextFlow Tower](https://cloud.tower.nf) or 
the `nextflow` [command-line tool](https://github.com/nextflow-io/nextflow).  
Otherwise, see "Using Development Versions" below._

There are three simple steps to redirecting your existing NextFlow pipelines to read and write from
Quilt packages. Please note that it has only been tested on NextFlow version 22.10.6.

### 1. Construct a Quilt+ URI for each package

Each Quilt+ package URI has the form: 

```string
quilt+s3://bucket#package=prefix/suffix
```

You must have read or write permissions to that `bucket`,
and your environment must have the corresponding
[AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html).

If you are running the Quilt web catalog, you can find the Quilt+ URI
for an existing package in the `<> CODE | URI` section at the top.
You can also manually create URIs for new packages that don't exist.

#### Quilt+ URIs for Metadata Workflows 

Sometimes you may want to ensure the created package contains specific metadata.
This is done using [Quilt workflows](https://docs.quiltdata.com/advanced/workflows).
Specify the workflow name as an additional `workflow=` hash parameter,
and any metadata properties as part of the query string.

```string
quilt+s3://bucket#package=prefix/suffix&workflow=my_workflow?mkey1=val1&mkey2=val2
```

Note that specifying a workflow means that package creation will fail (and nothing will be saved)
if the query string does not contain all the required metadata,
so you should carefully test it beforehand.


### 2. Set the appropriate parameter(s) in your pipeline

There is no formal convention, but most pipelines specify a parameter such as 'outdir' or 'pub'
which is used as the argument to `publishDir`, and 'input' or 'reads' for the source channel.
You can substitute a Quilt+ URI anywhere you currently use an S3 URI.

Note that keys specified on the command-line override the corresponding 'params.key' in your script.

### 3. Tell NextFlow to use the nf-quilt plugin

There are two ways to tell NextFlow to use this plugin.  

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

### Unit Testing 

You can compile run unit tests with:

```bash
make check
```

### Verifying NextFlow

If this is your first time using NextFlow, you may also need to install a recent 
[version of Java](https://www.java.com/en/download/help/download_options.html) for your platform.
NextFlow itself will take care of all the other dependencies.

You can verify and compile NextFlow with:

```bash
make nextflow-22-10
```

### Testing Installation

To verify that the plugin, nextflow, and your AWS credentials have been properly installed,
type:
```bash
   ./launch.sh run ./main.nf -profile standard -plugins $(PROJECT) --pub "quilt+s3://bucket#package=test/hurdat"
```

Replace "bucket" with an S3 bucket those credentials can write to.

### Running a Pipeine Locally

From inside the `nf-quilt` directory, call `./launch.sh` with a path to your pipeline.

For example, with a standard `nf-core` pipeline like `sarek`:

```bash
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt --outdir "quilt+s3://bucket#package=nf-quilt/sarek"
```

Otherwise, replace `nf-core/sarek` with the local path to your pipeline's `.nf` file,
and replace `outdir` with the appropriate parameter for `publishDir`.

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
