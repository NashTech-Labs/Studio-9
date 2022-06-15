#!/bin/bash

source $(dirname $0)/common-scripts.sh

set -e
set -o pipefail

echo "TRAVIS_BRANCH: ${TRAVIS_BRANCH}"
echo "TRAVIS_TAG: ${TRAVIS_TAG}"
echo "TRAVIS_PULL_REQUEST: ${TRAVIS_PULL_REQUEST}"

if [[ "${TRAVIS_BRANCH}" =~ ^v[0-9]+.[0-9]+.[0-9]+$ ]]
then
    ENVIRONMENT=production
    CONFIG_S3_BUCKET=artifacts.deepcortex.ai
    export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID_ARTIFACTS_DEEPCORTEX_AI
    export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY_ARTIFACTS_DEEPCORTEX_AI
    export $ENVIRONMENT
elif [ "${TRAVIS_BRANCH}" == 'master' ]
then
    unset ENVIRONMENT
    echo "*** INFO ***"
    echo "The build doesn't deploy pushes to Master branch. Use [sbt \"project ${APP_NAME}\" \"release with-defaults\"] command"
    exit 0
elif [ "${TRAVIS_BRANCH}" == 'develop' ]
then
    ENVIRONMENT=development
    CONFIG_S3_BUCKET=artifacts.dev.deepcortex.ai
    export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID_ARTIFACTS_DEV_DEEPCORTEX_AI
    export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY_ARTIFACTS_DEV_DEEPCORTEX_AI
    export $ENVIRONMENT
else
    echo "*** ERROR ***"
    echo "Cannot accociate ${TRAVIS_BRANCH} with any existing environment"
    abort
fi
echo "ENVIRONMENT: $ENVIRONMENT"

[[ "" == "$ENVIRONMENT" ]] &&
echo "*** ERROR ***" &&
echo "ENVIRONMENT is empty" && abort

function initializeCommonParameters() {

    echo "*** Reading settings for [$ENVIRONMENT] environment"

    common_configuration_path="s3://${CONFIG_S3_BUCKET}/configurations/services/common.env"

    export $(aws s3 cp ${common_configuration_path} - | xargs) > /dev/null

    parameters=(
        "DCOS_MASTER"
        "DCOS_USERNAME"
        "DCOS_PASSWORD"
        "DOCKER_USERNAME"
        "DOCKER_PASSWORD"
        "GATEWAY"
    )

    for parameter in "${parameters[@]}"
    do
       :
       validateStackParameter ${parameter} "Check [${common_configuration_path}] file" &&
       echo "export ${parameter}=[secure]"
    done

    echo "Common parameters initialized"
}

function exportParameters() {

    VARS_FILE_NAME=$1
    configuration_base_path="s3://${CONFIG_S3_BUCKET}/configurations/services/${APP_NAME}"
    configuration_path=${configuration_base_path}/${ENVIRONMENT}/${VARS_FILE_NAME}.env

    export $(aws s3 cp ${configuration_path} - | xargs) > /dev/null

    parameters=($(grep "=" $(dirname $0)/${VARS_FILE_NAME}.env | grep -o "^[^=]*"))

    for parameter in "${parameters[@]}"
        do
           :
           validateStackParameter "${parameter}" "Check [${configuration_path}] file" &&
           echo "export ${parameter}=[secure]"
        done
}

function initializeParameters() {
    echo "*** Reading settings for [$ENVIRONMENT] environment"

    DOCKER_IMAGE_VERSION=`cat ./target/docker-image.version`

    validateStackParameter "DOCKER_IMAGE_VERSION" "The file docker-image.version doesn't exist or empty.\nRun [sbt \"project ${APP_NAME}\" makeDockerVersion] command to generate this file."
    export "DOCKER_IMAGE_VERSION=$DOCKER_IMAGE_VERSION"
    echo "DOCKER_IMAGE_VERSION: $DOCKER_IMAGE_VERSION"

    exportParameters vars
    env_vars=""

    for parameter in "${parameters[@]}"
        do
           :
           next_line="\"${parameter}\": \"\${${parameter}}\","
           NL=$'\n'
           TAB=$'\t'
           env_vars="$env_vars$NL$TAB$next_line"
       done

    exportParameters secret-vars

    for parameter in "${parameters[@]}"
        do
           :
           next_line="\"${parameter}\": { \"secret\": \"\${${parameter}}\" },"
           NL=$'\n'
           TAB=$'\t'
           env_vars="$env_vars$NL$TAB$next_line"
        done

    env_vars_escaped=$(printf '%s\n' "${env_vars%?}" | sed 's,[\/&],\\&,g;s/$/\\/')
    env_vars_final=${env_vars_escaped%?}

    secret_env_vars=""

    for parameter in "${parameters[@]}"
        do
           :
           next_line="\"\${${parameter}}\": { \"source\": \"\${${parameter}}\" },"
           NL=$'\n'
           TAB=$'\t'
           secret_env_vars="$secret_env_vars$NL$TAB$next_line"
       done

    secret_vars_escaped=$(printf '%s\n' "${secret_env_vars%?}" | sed 's,[\/&],\\&,g;s/$/\\/')
    secret_vars_final=${secret_vars_escaped%?}

    sed -e "s/REPLACE_WITH_ENV_VARS/${env_vars_final}/" -e "s/REPLACE_WITH_SECRET_VARS/${secret_vars_final}/" $(dirname $0)/marathon.json.template > $(dirname $0)/tmp_marathon.json

    envsubst < $(dirname $0)/tmp_marathon.json > $(dirname $0)/marathon.json
    envsubst < nginx/marathon.json.template > nginx/marathon.json

    echo "Service parameters initialized"
}

echo "Running deploy script for $ENVIRONMENT environment..."

[ ! -z "${ENVIRONMENT}" ] &&
initializeCommonParameters &&
echo "Building and publishing docker image..." &&
docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD" &&
sbt ++$TRAVIS_SCALA_VERSION "project ${APP_NAME}" docker:publish makeDockerVersion &&
echo "Deploying the docker image to DC/OS..."

if [[ "$ENVIRONMENT" == "production" ]] && [[ "${BUILD_PROD}" != "true" ]]; then
  echo "The production environment is currently not set up or is disabled."
  exit 0
fi

initializeParameters &&
source $(dirname $0)/build-and-push-nginx-container.sh &&
source $(dirname $0)/deploy-dcos.sh

