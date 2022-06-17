[![Build Status](https://travis-ci.com/deepcortex/aries.svg?token=NvQ9e1gJgR195rmfXncf&branch=develop)](https://travis-ci.com/deepcortex/aries)

master:
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/777faf05871f43618713860af1dca65b?branch=master)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/aries&amp;utm_campaign=Badge_Grade) [![Codacy Badge](https://api.codacy.com/project/badge/Coverage/777faf05871f43618713860af1dca65b?branch=master)](https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=deepcortex/aries&utm_campaign=Badge_Coverage)

develop:
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/777faf05871f43618713860af1dca65b?branch=develop)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/aries&amp;utm_campaign=Badge_Grade) [![Codacy Badge](https://api.codacy.com/project/badge/Coverage/777faf05871f43618713860af1dca65b?branch=develop)](https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=deepcortex/aries&utm_campaign=Badge_Coverage)

# aries

### Deployment

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uplaoded to S3 to contain the correct values for each varible.

develop: s3 cp s3://artifacts.dev.deecprtex.ai/configurations/services/aries-api-rest/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/aries-api-rest/${ENVIRONMENT}/vars.env

### To create and populate ElasticSearch

#### To Build cortex-jobs index

```sh
./scripts/elasticsearch/scripts/build-index-jobs-dev.sh
```

### Test the service locally

To start dev server

```sh
sbt
aries-api-rest/re-start
```
or
```sh
sbt "project aries-api-rest" run
```

To run test requests

### Status Endpoints
```sh
curl -i -w %{time_connect}:%{time_starttransfer}:%{time_total} http://127.0.0.1:9000/v1/health
curl -i http://127.0.0.1:9000/v1/about
curl -i http://127.0.0.1:9000/v1/status
```
### Command Endpoints
#### Create Job
```sh
curl -XPOST -i -w %{time_connect}:%{time_starttransfer}:%{time_total} -H "Content-Type: application/json" http://127.0.0.1:9000/v1/jobs -d '{"id":"04f26db5-c420-45ad-8ca7-6cdf0e462650","created":"2017-08-07T17:56:10Z","owner":"14f26db5-c420-45ad-9ca8-6cdf0e462650","jobType":"train","status":"submitted","inputPath":"inputPath","startedAt":"2017-08-07T17:56:10Z","completedAt":"2017-08-07T17:56:10Z","outputPath":"outputPath"}' --user command-user:command-password
```
#### Update Job
```sh
curl -XPUT -i -w %{time_connect}:%{time_starttransfer}:%{time_total} -H "Content-Type: application/json" http://127.0.0.1:9000/v1/jobs/04f26db5-c420-45ad-8ca7-6cdf0e462650 -d '{"status":"running"}' --user command-user:command-password
```
#### Delete Job
```sh
curl -XDELETE 'http://127.0.0.1:9000/v1/jobs/04f26db5-c420-45ad-8ca7-6cdf0e462650'  --user command-user:command-password
```
#### Create Heartbeat
```sh
curl -XPOST -i -w %{time_connect}:%{time_starttransfer}:%{time_total} -H "Content-Type: application/json" http://127.0.0.1:9000/v1/heartbeats -d '{"jobId":"54f26db5-c420-45ad-8ca7-6cdf0e462650","created":"2017-08-07T17:56:10Z"}' --user command-user:command-password
```
### Query Endpoints
#### Get Job
```sh
curl -XGET 'http://127.0.0.1:9000/v1/jobs/04f26db5-c420-45ad-8ca7-6cdf0e462650' --user search-user:search-password
```
#### List Jobs
```sh
curl -XGET 'http://127.0.0.1:9000/v1/jobs' --user search-user:search-password
```
#### Search Field
```sh
curl -XGET 'http://127.0.0.1:9000/v1/jobs/search?owner=14f26db5-c420-45ad-9ca8-6cdf0e462650&status=submitted&job_type=train' --user search-user:search-password
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
- docker-stage (publish docker artifacts to ./aries-api-rest/target/docker/ folder)
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
sbt "project aries-api-rest" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project aries-api-rest" release
```

To manually create / update  aries-api-rest stack from your local machine

```sh
sbt "project aries-api-rest" docker:publish makeDockerVersion
```

or

```sh
sbt "project aries-api-rest" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-aries-api-rest-stack.sh development
```


### Show a list of project dependencies that can be updated

```sh
sbt dependencyUpdates
```

### Add test coverage to Codacy locally

```sh
export ARIES_CODACY_PROJECT_TOKEN=<Project_Token>
```

```sh
export CODACY_PROJECT_TOKEN=$ARIES_CODACY_PROJECT_TOKEN
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
