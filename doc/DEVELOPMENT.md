# Running from Git

To quickly run `nf-quilt` from this GitHub repository:

```bash
git clone https://github.com/quiltdata/nf-quilt.git
cd nf-quilt
make test-all # runs unit tests and installs depdencies
export WRITE_BUCKET=bucket-with-write-access
make pkg-test  # create "test/hurdat" package on s3://$WRITE_BUCKET
./launch.sh run nf-core/sarek -profile test,docker -plugins nf-quilt \
            --outdir "quilt+s3://$WRITE_BUCKET#package=nf-quilt/sarek"
```

## Using Development Versions

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

## Unit Testing

You can compile run unit tests with:

```bash
make check
```

## Verifying NextFlow

If this is your first time using NextFlow, you may also need to install a recent
[version of Java](https://www.java.com/en/download/help/download_options.html) for your platform.
NextFlow itself will take care of all the other dependencies.

You can verify and compile NextFlow with:

```bash
make nextflow
```

## Testing Installation

To verify that the plugin, nextflow, and your AWS credentials have been properly installed,
type:

```bash
   ./launch.sh run ./main.nf -profile standard -plugins $(PROJECT) --pub "quilt+s3://bucket#package=test/hurdat"
```

Replace "bucket" with an S3 bucket those credentials can write to.

## Running a Pipeine Locally

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
