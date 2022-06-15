#!/bin/bash

dcos cluster setup http://${DCOS_MASTER} --username=${DCOS_USERNAME} --password-env=DCOS_PASSWORD --no-check
echo "DCOS cluster is setup for ${DCOS_MASTER}"