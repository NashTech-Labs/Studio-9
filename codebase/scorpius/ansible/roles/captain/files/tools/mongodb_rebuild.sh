#!/bin/bash

nodes=$(dcos percona-server-mongodb pod status --json | jq -r '.pods[].instances[] | select(.tasks[].status=="LOST") | .name')

IFS=$'\n' read -rd '' -a pods <<< "${nodes}"

for i in "${pods[@]}"
do
    dcos percona-server-mongodb pod replace "${i}"
done
