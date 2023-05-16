# Using Quilt with Benchling

Benchling is a "strongly-typed" environment that requires explict schemas for all metadata.

Because of this, you must do two things before Quilt can call the Benchling API:

## A. Create Tenant Schema

Once per tenant, the Administrator must define an "entry schema" for the Quilt metadata fields.

## B. Add Notebook Schema

For every notebook that will be updated by Quilt, the author must explicitly add that schema
(or a new schema with the same fields) to the notebook
