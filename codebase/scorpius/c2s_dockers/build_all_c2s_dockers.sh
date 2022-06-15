#!/bin/bash

parse_args()
{
  while getopts ":d:s:" opt "$@"; do
    case "$opt" in
      d) DESTINATION_S3_PATH="$OPTARG" ;;
      s) SOURCE_S3_PATH="$OPTARG" ;;
      :) error "option -$OPTARG requires an argument." ;;
      \?) error "unknown option: -$OPTARG" ;;
    esac
  done
}

parse_args "$@"

VARS=("SOURCE_S3_PATH" "DESTINATION_S3_PATH")

for i in "${VARS[@]}"; do
  if [[ -z "${!i}" ]];then
    echo "$i is not set"
    usage
  fi
done


bash build_dockers.sh -t java -i "baile,cortex-job-master" -s "$SOURCE_S3_PATH" -d "$DESTINATION_S3_PATH"
bash build_dockers.sh -t python -i "cortex-tasks-sklearn,cortex-tasks-gpu,dc-models-migrate" -s "$SOURCE_S3_PATH" -d "$DESTINATION_S3_PATH"