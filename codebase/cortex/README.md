[![Build Status](https://travis-ci.com/deepcortex/cortex.svg?token=pvwDNvw6P8fj9zJxpA1p&branch=master)](https://travis-ci.com/deepcortex/cortex)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/675ff3c8d31f469d9e96248930ca3dc5)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/cortex&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/675ff3c8d31f469d9e96248930ca3dc5)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/cortex&amp;utm_campaign=Badge_Coverage)

# cortex

### Deployment

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uplaoded to S3 to contain the correct values for each varible.

develop: s3 cp s3://artifacts.dev.deecprtex.ai/configurations/services/cortex-api-rest/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/cortex-api-rest/${ENVIRONMENT}/vars.env

### To create Cloud Formation stack
````
./scripts/deploy/build-cortex-api-ecs-stack.sh production cortex-api-ecs-prod-stack
````

### Test the service locally

To start dev server

```sh
sbt
cortex-api-rest/re-start
```
or
```sh
sbt "project cortex-api-rest" run
```

To run test requests

```sh
curl -i -w %{time_connect}:%{time_starttransfer}:%{time_total} http://127.0.0.1:9000/v1/health
curl -i http://127.0.0.1:9000/v1/about
curl -i http://127.0.0.1:9000/v1/status
curl -XGET 'http://127.0.0.1:9000/v1/jobs/search?id=id&owner=owner' --user search-user:search-password
curl -XPOST -i -w %{time_connect}:%{time_starttransfer}:%{time_total} -H "Content-Type: application/json" http://127.0.0.1:9000/v1/jobs/search -d \
    '{"id":"id","owner":"owner"}' --user search-user:search-password
```

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
- docker-stage (publish docker artifacts to ./cortex-api-rest/target/docker/ folder)
- docker-publish (publish docker image to docker hub)
- deploy-p (deploy in production)
```

### Deployment

To create / update cortex-api-ecs stack from your local machine

```sh
./scripts/deploy/build-cortex-api-ecs-stack.sh <environment> <stack-name>
```

Allowed environments values: [ "development", "test", "staging", "production" ]

Example

```sh
./scripts/deploy/build-cortex-api-ecs-stack.sh production cortex-api-ecs-prod-stack
```

To deploy develop branch

```sh
//commit all your changes
git push
```

To deploy master branch

```sh
//commit all your changes
git push
sbt "project cortex-api-rest" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project cortex-api-rest" release
```

To manually create / update  cortex-api-rest stack from your local machine

```sh
sbt "project cortex-api-rest" docker:publish makeDockerVersion
```

or

```sh
sbt "project cortex-api-rest" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-cortex-api-rest-stack.sh development
```

### Configuration settings

s3://{configuration}/[$ENVIRONMENT/cortex-api-rest.env

!!! Do not forget to add validation to
the [build-cortex-api-rest-stack.sh](https://github.com/deepcortex/cortex/blob/master/scripts/deploy/build-cortex-api-rest-stack.sh#L38-L52)
file for the new environmental variables

### Stress tests

Populate missing values in [user.properties](https://github.com/sentrana/cortex/blob/master/scripts/jmeter-test-plans/user.properties) file

```sh
./apache-jmeter-3.0/bin/jmeter -n -t ./cortex-api-v1-testplan.jmx --addprop ./user.properties
```

or with SOCKS proxy

```sh
./apache-jmeter-3.0/bin/jmeter -n -t ./cortex-api-v1-testplan.jmx --addprop ./user.properties -DsocksProxyHost=localhost -DsocksProxyPort=1234
```

### Show a list of project dependencies that can be updated

```sh
sbt dependencyUpdates
```

### Add test coverage to Codacy locally

```sh
export CORTEX_CODACY_PROJECT_TOKEN=fbcd26cf9302445e87d9c98d9b9465a1
```

```sh
export CODACY_PROJECT_TOKEN=$CORTEX_CODACY_PROJECT_TOKEN
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


