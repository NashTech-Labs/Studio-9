[![Build Status](https://travis-ci.com/deepcortex/orion.svg?token=pvwDNvw6P8fj9zJxpA1p&branch=master)](https://travis-ci.com/deepcortex/orion)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/961f73c42c8f4fad87395aa5b50fd5cb)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/orion&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/961f73c42c8f4fad87395aa5b50fd5cb)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/orion&amp;utm_campaign=Badge_Coverage)

# orion

### Deployment

Prerequisites:

1. [Install aws cli](https://docs.aws.amazon.com/cli/latest/userguide/installing.html)
2. [Configure aws cli](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-quick-configuration)

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uploaded to S3 to contain the correct values for each variable.

develop: s3 cp s3://artifacts.dev.deepcortex.ai/configurations/services/orion-api-rest/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/orion-api-rest/${ENVIRONMENT}/vars.env

### To resolve dependencies from "DeepCortex Internal Repository" maven repository

##### For SBT

Add these lines to your ~/.bash_profile

```
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID_ARTIFACTS_DEEPCORTEX_AI=AKIAIQBIZHXAHKOCU3FA
export AWS_SECRET_KEY_ARTIFACTS_DEEPCORTEX_AI=/5r0v26+hWTyZttryuhC1XvwewvZ2kxsKyDwillO
```

##### For IntelliJ

Create file ~/.sbt/.s3credentials

```
accessKey = AKIAIQBIZHXAHKOCU3FA
secretKey = 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
```

### Test the service locally

First, start zookeeper:

```sh
docker run --rm --name some-zookeeper -d -p 2181:2181 zookeeper
```

Then start dev server

```sh
sbt
orion-api-rest/re-start
```
or
```sh
sbt "project orion-api-rest" run
```

To run test requests

```sh
curl -i -w %{time_connect}:%{time_starttransfer}:%{time_total} http://127.0.0.1:9000/v1/health
curl -i http://127.0.0.1:9000/v1/about
curl -i http://127.0.0.1:9000/v1/status
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
- docker-stage (publish docker artifacts to ./api-rest/target/docker/ folder)
- docker-publish (publish docker image to docker hub)
- deploy-p (deploy in production)
```

### Deployment

To create / update orion-api-ecs stack from your local machine

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
sbt "project orion-api-rest" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project orion-api-rest" release
```

To manually create / update  orion-api-rest stack from your local machine

```sh
sbt "project orion-api-rest" docker:publish makeDockerVersion
```

or

```sh
sbt "project orion-api-rest" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-cortex-api-rest-stack.sh development
```

### Configuration settings

s3://{configuration}/[$ENVIRONMENT/orion-api-rest.env

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
export ORION_CODACY_PROJECT_TOKEN=171571b64e3b4ef495d88d2dc7db8b5e
```

```sh
export ORION_PROJECT_TOKEN=$ORION_CODACY_PROJECT_TOKEN
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


