[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e31f5afbb6cd4d9691dd697fdf4a643c)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/pegasus&amp;utm_campaign=Badge_Grade) [![Build Status](https://travis-ci.com/deepcortex/pegasus.svg?token=JJjmXqHcerjaWXbcYsZ9&branch=master)](https://travis-ci.com/deepcortex/pegasus)

# Pegasus

### Deployment

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uplaoded to S3 to contain the correct values for each varible.

develop: s3 cp s3://artifacts.dev.deecprtex.ai/configurations/services/pegasus-api-rest/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/pegasus-api-rest/${ENVIRONMENT}/vars.env

## Run the server

```sh
sbt "project pegasus-api-rest" run
```

## API Reference

### Load data

Loads data from S3 into Redshift:

Request:
```
POST /data/load
Content-type: application/json
{
  "tableName": "supplier",
  "dataSource": "s3://dev.deepcortex.ai/load/supplier.tbl",
  "delimiter": "|"
}
```
Response:
```
Status Code: 200 OK
```

For further information, refer to: [COPY - Amazon Redshift](http://docs.aws.amazon.com/redshift/latest/dg/r_COPY.html)

### Unload data

Unloads data from Redshift into S3

Request:
```
POST /data/unload
{
  "tableName": "supplier",
  "outputObjectPath": "s3://dev.deepcortex.ai/unload/supplier_",
  "delimiter": "|"
}
```
Response:
```
Status Code: 200 OK
```

For further information, refer to: [UNLOAD - Amazon Redshift](http://docs.aws.amazon.com/redshift/latest/dg/r_UNLOAD.html)

### Status Endpoints
```sh
curl -i -w %{time_connect}:%{time_starttransfer}:%{time_total} http://127.0.0.1:9000/v1/health
curl -i http://127.0.0.1:9000/v1/about
curl -i http://127.0.0.1:9000/v1/status
```

## Other useful commands

### Make
```sh
- test (run tests)
- test-it (run IT tests)
- test-ete (run E2E tests)
- test-bench (run benchmark tests)
- test-all (run all tests)
- codacy-coverage (update Codacy code coverage)
- run-l (run locally)
- get-version (get the current version of the project)
- docker-stage (publish docker artifacts to ./pegasus-api-rest/target/docker/ folder)
- docker-publish (publish docker image to docker hub)
- deploy-p (deploy in production)
```

### Deployment


To deploy develop branch

```sh
//commit all your changes
git push
```

To deploy master branch

```sh
//commit all your changes
git push
sbt "project pegasus-api-rest" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project pegasus-api-rest" release
```

To manually create / update  pegasus-api-rest stack from your local machine

```sh
sbt "project pegasus-api-rest" docker:publish makeDockerVersion
```

or

```sh
sbt "project pegasus-api-rest" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-pegasus-api-rest-stack.sh development
```


### Show a list of project dependencies that can be updated

```sh
sbt dependencyUpdates
```

### Add test coverage to Codacy locally

```sh
export PEGASUS_CODACY_PROJECT_TOKEN=<Project_Token>
```

```sh
export CODACY_PROJECT_TOKEN=$PEGASUS_CODACY_PROJECT_TOKEN
sbt clean coverage testAll
sbt coverageReport
sbt coverageAggregate
sbt codacyCoverage
```

### Install dependencies on MAC OS X to run deploy scripts locally

jq
```sh
brew install jq
```

envsubst
```sh
brew install gettext
brew link --force gettext
```


