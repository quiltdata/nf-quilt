# Benchling API

No description provided (generated by Openapi Generator <https://github.com/openapitools/openapi-generator>)

This Groovy package, using the [http-builder-ng library](https://http-builder-ng.github.io/http-builder-ng/), is automatically generated by the [OpenAPI Generator](https://openapi-generator.tech) project:

- API version: 2.0.0
- Package version: 2.0.0
- Build date: 2023-03-28T18:14:55.022924-07:00[America/Los_Angeles]
- Build package: benchling.codegen.languages.GroovyClientCodegen

## Requirements

- Groovy 2.5.7
- Gradle 4.9

## Build

First, create the gradle wrapper script:

```bash
gradle wrapper
```

Then, run:

```bash
./gradlew check assemble
```

## Getting Started

```groovy
def apiInstance = new AaSequencesApi()
def aaSequencesArchive = new AaSequencesArchive() // AaSequencesArchive | 

apiInstance.archiveAASequences(aaSequencesArchive)
    {
    // on success
    def result = (AaSequencesArchivalChange)it
    println result
    
}
    {
    // on failure
    statusCode, message ->
        println "${statusCode} ${message}"
};
```