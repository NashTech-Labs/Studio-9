#!/bin/sh

export es_index=cortex_jobs_prod
export es_host=localhost
export es_port=9200
export es_username="${ES_USERNAME:-elastic}"
export es_password="${ES_PASSWORD:-changeme}"
source $(dirname $0)/build-index.sh
