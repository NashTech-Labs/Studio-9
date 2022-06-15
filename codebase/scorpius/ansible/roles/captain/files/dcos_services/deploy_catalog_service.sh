#!/bin/bash

SERVICE_NAME=$1
TEMPLATE=$2
ENV_VARS=$3
VERSION=$4

source ${ENV_VARS}

envsubst < ${TEMPLATE} > options.json

dcos package install $SERVICE_NAME --options=options.json --package-version=$VERSION --yes