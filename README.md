# nf-quilt

Nextflow plugin for interacting with [Quilt](https://quiltdata.com/) packages as a FileSystem

`nf-quilt` is a plugin developed by Quilt Data that enables you read and write directly
to Quilt packages using `quilt+` URLs wherever you currently use `s3`, `az` or `gs` URLs.

Inspired by the original `nf-quilt` plugin developed by Seqera labs

## Getting Started

To add the `nf-quilt` plugin to your workflow, you may need Nextflow 23.01 (or later) and Python 3.9 (or later).  Note this assumes you have already [installed groovy](https://groovy-lang.org/install.html).

### Quilt Configuration

This plugin uses the `quilt3` CLI to call the Quilt API.
You must install the `quilt3` Python module and ensure the CLI is in your path:

```bash
pip3 install quilt3
which quilt3 # e.g., /usr/local/bin/quilt3
```

### Loading the nf-quilt plugin

Once this plugin is officially published on `nextflow-io/plugins`, 
you can enable it by modifying `nextflow.config`.
Add the following snippet, or just add that one 'id' if you already have other plugins):
```groovy
plugins {
    id 'nf-quilt'
}
```

You can instead add `-plugins nf-quilt` as an argument to `launch.sh`.

If the plug-in is not yet published, you will need to run it directly from git
as described under "Development."

### Reading and Writing Quilt URLs

Next, create a Quilt URL for the S3 bucket where you want to store (and eventually read) your results.
You must specify a package name containing exactly one '/', such as `instrument/experiment`
e.g. "quilt+s3://raw-bucket#package=nf-quilt/sarek"

Note your command-line environment must have 
[AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html) 
that allow you to read/write that bucket.

Finally, run your Nextflow pipeline as usual, setting that URL as your output directory, .e.g.:

```bash
./launch.sh run nf-core/sarek -profile test,docker --outdir quilt+s3://raw-bucket#package=nf-quilt/sarek&path=.
```

You can also use Quilt packages as input to nextflow jobs, e.g.:

```bash
nextflow run my/analysis --indir quilt+s3://raw-bucket#package=experiment/instrument --outdir quilt+s3://prod-bucket#package=experiment/analysis
```


## Development

_Based on [nf-hello](https://github.com/nextflow-io/nf-hello)_

### Unit testing

Run the following command in the project root directory (ie. where the file `settings.gradle` is located):

```bash
make check
```

### Testing and debugging

1. Clone the Nextflow repository into a sibling directory, .e.g:

```bash
git clone --depth 1 https://github.com/nextflow-io/nextflow ../nextflow
```

2. Compile the plugin alongside the Nextflow code:

```bash
make compile
```

3. Run Nextflow with the plugin, using `./launch.sh` as a drop-in replacement for the `nextflow` command, and adding the option `-plugins nf-quilt` to load the plugin:

```bash
./launch.sh run nextflow-io/hello -plugins nf-quilt
```

4. Use Makefile to run tests against your own writeable S3 Bucket

```bash
make pkg-test BUCKET=my-s3-bucket # default, simply copies a package
make sarek BUCKET=my-s3-bucket # runs nf-core/sarek, or any other pipeline that uses `--outdir`
```

### Package, upload and publish

The project should be hosted in a GitHub repository whose name should match the name of the plugin, that is the name of the directory in the `plugins` folder (e.g. `nf-quilt`).

Follow these steps to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):

   * `github_organization`: the GitHub organisation where the plugin repository is hosted.
   * `github_username`: The GitHub username granting access to the plugin repository.
   * `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   * `github_commit_email`: The email address associated with your GitHub account.

3. Use the following command to package and create a release for your plugin on GitHub:
   ```bash
   ./gradlew :plugins:nf-quilt:upload
   ```

4. Fork the [nextflow-io/plugins](https://github.com/nextflow-io/plugins) repository to one you can write to

5. Use the following command to publish your plugin to your fork:
    ```bash
    ./gradlew :plugins:publishIndex
    ```
6. Create a pull request to push your changes back to [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json)
