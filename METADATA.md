#### Quilt+ URIs for Metadata Workflows

Sometimes you may want to ensure the created package contains specific metadata.
This is done using [Quilt workflows](https://docs.quiltdata.com/advanced/workflows).
Specify the workflow name as an additional `workflow=` hash parameter,
and any metadata properties as part of the query string.

```string
quilt+s3://bucket?mkey1=val1&mkey2=val2#package=prefix/suffix&workflow=my_workflow
```

Note that specifying a workflow means that package creation will fail (and nothing will be saved)
if the query string does not contain all the required metadata,
so you should carefully test it beforehand.
