#!/bin/sh

export es_host=localhost
export es_port=9200
export es_index=cortex_job_heartbeats_dev
export mapping_path=./mappings/cortex-job-heartbeats-dev.json
source $(dirname $0)/build-index.sh
