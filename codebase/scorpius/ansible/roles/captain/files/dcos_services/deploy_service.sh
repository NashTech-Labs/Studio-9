#!/bin/bash

TEMPLATE=$1
ENV_VARS=$2

source ${ENV_VARS}

envsubst < ${TEMPLATE} > marathon.json

dcos marathon app add marathon.json

echo "marathon app added on dcos"
