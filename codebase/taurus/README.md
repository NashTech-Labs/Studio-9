[![Build Status](https://travis-ci.com/deepcortex/taurus.svg?token=JJjmXqHcerjaWXbcYsZ9&branch=master)](https://travis-ci.com/deepcortex/taurus)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/920b584087574fcf942aeee49a79c64d)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/taurus&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/920b584087574fcf942aeee49a79c64d)](https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=deepcortex/taurus&utm_campaign=Badge_Coverage)

# taurus

### Deployment

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uplaoded to S3 to contain the correct values for each varible.

develop: s3 cp s3://artifacts.dev.deecprtex.ai/configurations/services/taurus/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/taurus/${ENVIRONMENT}/vars.env


### Test the service locally

To start dev server

```sh
sbt
taurus/re-start
```
or
```sh
sbt "project taurus" run
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
- docker-stage (publish docker artifacts to ./taurus/target/docker/ folder)
- docker-publish (publish docker image to docker hub)
- deploy-p (deploy in production)
```

### Deployment

To create / update taurus-api-ecs stack from your local machine

```sh
./scripts/deploy/build-taurus-api-ecs-stack.sh <environment> <stack-name>
```

Allowed environments values: [ "development", "test", "staging", "production" ]

Example

```sh
./scripts/deploy/build-taurus-api-ecs-stack.sh production taurus-api-ecs-prod-stack
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
sbt "project taurus" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project taurus" release
```

To manually create / update  taurus stack from your local machine

```sh
sbt "project taurus" docker:publish makeDockerVersion
```

or

```sh
sbt "project taurus" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-taurus-stack.sh development
```

### Configuration settings

s3://{configuration}/[$ENVIRONMENT/taurus.env

!!! Do not forget to add validation to
the [build-taurus-stack.sh](https://github.com/deepcortex/taurus/blob/master/scripts/deploy/build-taurus-stack.sh#L38-L52)
file for the new environmental variables

### Stress tests

Populate missing values in [user.properties](https://github.com/sentrana/taurus/blob/master/scripts/jmeter-test-plans/user.properties) file

```sh
./apache-jmeter-3.0/bin/jmeter -n -t ./taurus-api-v1-testplan.jmx --addprop ./user.properties
```

or with SOCKS proxy

```sh
./apache-jmeter-3.0/bin/jmeter -n -t ./taurus-api-v1-testplan.jmx --addprop ./user.properties -DsocksProxyHost=localhost -DsocksProxyPort=1234
```

### Show a list of project dependencies that can be updated

```sh
sbt dependencyUpdates
```

### Add test coverage to Codacy locally

```sh
export TAURUS_CODACY_PROJECT_TOKEN=03f38d86eb7046c19650573a91baa06b
```

```sh
export CODACY_PROJECT_TOKEN=$TAURUS_CODACY_PROJECT_TOKEN
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


