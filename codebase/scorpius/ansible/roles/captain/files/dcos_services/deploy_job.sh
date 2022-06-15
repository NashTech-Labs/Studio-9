#!/bin/bash

TEMPLATE=$1
ENV_VARS=$2

source ${ENV_VARS}

envsubst < ${TEMPLATE} > marathon.json

dcos job add marathon.json