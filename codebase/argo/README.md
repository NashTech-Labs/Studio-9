[![Codacy Badge](https://api.codacy.com/project/badge/Grade/5c1c8eb1bac940b7b19e01fc516b5c52)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/argo&amp;utm_campaign=Badge_Grade) [![Build Status](https://travis-ci.com/deepcortex/argo.svg?token=JJjmXqHcerjaWXbcYsZ9&branch=master)](https://travis-ci.com/deepcortex/argo)

# Argo

## Set-up

### Deployment

Copy scripts/deploy/vars with actual values to the proper S3 location.
Actual passwords, IP addresses, and other info should not be stored in the vars.env file in this project.
Be sure to modify the file being uplaoded to S3 to contain the correct values for each varible.

develop: s3 cp s3://artifacts.dev.deecprtex.ai/configurations/services/argo-api-rest/${ENVIRONMENT}/vars.env
production: s3 cp s3://artifacts.deecprtex.ai/configurations/services/argo-api-rest/${ENVIRONMENT}/vars.env

### Create ElasticSearch indexes

```sh
./scripts/elasticsearch/scripts/build-index-config-settings-dev.sh
```

### Run the server

```sh
sbt "project argo-api-rest" run
```

## API Reference

### Create or update a Config Setting

Request:
```
PUT /services/{serviceName}/config-settings/{configSettingName}
Content-type: application/json
  {
    "settingValue": "some-postfix",
    "tags": ["jobA"]
  }
```
Response:
```
Status Code: 200 OK
{
  "serviceName": "online-prediction",
  "settingName": "s3.input.postfix"
  "settingValue": "some-postfix",
  "tags": [
    "jobA"
  ],
  "createdAt": "2017-12-03T19:21:55Z",
  "updatedAt": "2017-12-03T19:29:47Z"
}
```

### Get a Config Setting
Request:
```
GET /services/{serviceName}/config-settings/{configSettingName}
```
Response:
```
Status Code: 200 OK
{
  "serviceName": "online-prediction",
  "settingName": "s3.input.postfix"
  "settingValue": "some-postfix",
  "tags": [
    "jobA"
  ],
  "createdAt": "2017-12-03T19:21:55Z",
  "updatedAt": "2017-12-03T19:29:47Z"
}
```

### Delete a Config Setting
Request:
```
DELETE /services/{serviceName}/config-settings/{configSettingName}
```
Response:
```
Status Code: 200 OK
{
  "serviceName": "online-prediction",
  "settingName": "s3.input.postfix"
  "settingValue": "some-postfix",
  "tags": [
    "jobA"
  ],
  "createdAt": "2017-12-03T19:21:55Z",
  "updatedAt": "2017-12-03T19:29:47Z"
}
```

### Get all Config Settings
Request:
```
GET /services/{serviceName}/config-settings
```
Response:
```
Status Code: 200 OK
[
  {
    "serviceName": "online-prediction",
    "settingName": "s3.input.postfix"
    "settingValue": "some-postfix",
    "tags": [
      "jobA"
    ],
    "createdAt": "2017-12-03T19:21:55Z",
    "updatedAt": "2017-12-03T19:29:47Z"
  },
  {
    "serviceName": "online-prediction",
    "settingName": "s3.access.key"
    "settingValue": "some-access-key"
    "tags": [
      "jobA"
    ],
    "createdAt": "2017-12-03T19:21:55Z",
    "updatedAt": "2017-12-03T19:29:47Z"
  }
]
```


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
- docker-stage (publish docker artifacts to ./argo-api-rest/target/docker/ folder)
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
sbt "project argo-api-rest" "release with-defaults"
```

or if your need to set the build version manually
```sh
//commit all your changes
git push
sbt "project argo-api-rest" release
```

To manually create / update  argo-api-rest stack from your local machine

```sh
sbt "project argo-api-rest" docker:publish makeDockerVersion
```

or

```sh
sbt "project argo-api-rest" makeDockerVersion
//modify ./target/docker-image.version file to use existing container
./scripts/deploy/build-argo-api-rest-stack.sh development
```


### Show a list of project dependencies that can be updated

```sh
sbt dependencyUpdates
```

### Add test coverage to Codacy locally

```sh
export ARGO_CODACY_PROJECT_TOKEN=<Project_Token>
```

```sh
export CODACY_PROJECT_TOKEN=$ARGO_CODACY_PROJECT_TOKEN
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


