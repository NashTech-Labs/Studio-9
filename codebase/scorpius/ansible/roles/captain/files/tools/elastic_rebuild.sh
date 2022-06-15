#!/bin/bash

nodes=$(dcos elastic pod status --json | jq -r '.pods[].instances[] | select(.tasks[].status=="LOST") | .name')

IFS=$'\n' read -rd '' -a pods <<< "${nodes}"

for i in "${pods[@]}"
do
    dcos elastic pod replace "${i}"
done

