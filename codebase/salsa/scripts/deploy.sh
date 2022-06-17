#!/bin/bash

set -e
set -o pipefail

echo "TRAVIS_BRANCH: ${TRAVIS_BRANCH}"
echo "TRAVIS_TAG: ${TRAVIS_TAG}"
echo "TRAVIS_PULL_REQUEST: ${TRAVIS_PULL_REQUEST}"

BUILD_TARGET=build:prod

if [[ "${TRAVIS_BRANCH}" =~ ^v[0-9]+.[0-9]+.[0-9]+$ ]]
then
    ENVIRONMENT=production
    S3_BUCKET=studio9.ai
    export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID_ARTIFACTS_STUDIO9_AI
    export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY_ARTIFACTS_STUDIO9_AI
    export $ENVIRONMENT
elif [[ "${TRAVIS_BRANCH}" =~ ^release/[0-9]+.[0-9]+.[0-9]+$ ]]
then
    # unset ENVIRONMENT
    # echo "*** INFO ***"
    # echo "The build doesn't deploy pushes to release branches yet."
    # exit 0

    ENVIRONMENT=dev
    BUILD_TARGET=build:falcon
    S3_BUCKET=studio9-stage-dcos-apps
    export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID_ARTIFACTS_DEV_STUDIO9_AI
    export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY_ARTIFACTS_DEV_STUDIO9_AI
    export $ENVIRONMENT
elif [ "${TRAVIS_BRANCH}" == 'master' ]
then
    unset ENVIRONMENT
    echo "*** INFO ***"
    echo "The build doesn't deploy pushes to Master branch."
    exit 0
elif [ "${TRAVIS_BRANCH}" == 'develop' ]
then
    ENVIRONMENT=dev
    S3_BUCKET=studio9-develop-dcos-apps
    export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID_ARTIFACTS_DEV_STUDIO9_AI
    export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_KEY_ARTIFACTS_DEV_STUDIO9_AI
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

if [[ "$ENVIRONMENT" == "production" ]] && [[ "${BUILD_PROD}" != "true" ]]; then
  echo "The production environment is currently not set up or is disabled."
  exit 0
fi

yarn run $BUILD_TARGET
aws s3 sync dist s3://$S3_BUCKET/static-content/$ENVIRONMENT/ --delete
