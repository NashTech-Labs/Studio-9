#!/bin/bash

function abort() {
    set -e
    exit 1
}

set +e

echo "TRAVIS_BRANCH: ${TRAVIS_BRANCH}"
echo "TRAVIS_TAG: ${TRAVIS_TAG}"
echo "TRAVIS_PULL_REQUEST: ${TRAVIS_PULL_REQUEST}"

if [[ "${TRAVIS_BRANCH}" =~ ^[0-9]+.[0-9]+.[0-9]+$ ]]
then
    ENVIRONMENT=production
    export $ENVIRONMENT
elif [ "${TRAVIS_BRANCH}" == 'master' ]
then
    unset ENVIRONMENT
    echo "*** INFO ***"
    echo "The build doesn't publish pushes to Master branch. Use [sbt \"project project orion-ipc-rabbitmq\" \"release with-defaults\"] command"
    exit 0
elif [ "${TRAVIS_BRANCH}" == 'develop' ]
then
	ENVIRONMENT=development
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

echo "Running deploy script for $ENVIRONMENT environment..."

[ ! -z "${ENVIRONMENT}" ] &&
echo "Building and publishing project..." &&
sbt ++$TRAVIS_SCALA_VERSION clean coverageOff publish