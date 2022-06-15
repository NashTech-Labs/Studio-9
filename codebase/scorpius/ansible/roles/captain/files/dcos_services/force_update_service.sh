#!/bin/bash

APP_NAME=$1
ENV_VARS=$APP_NAME/env_vars.sh
TEMPLATE=$APP_NAME/marathon.json

source ${ENV_VARS}

envsubst < ${TEMPLATE} > marathon.json

dcos marathon app update $APP_NAME < marathon.json --force
