#!/bin/sh

export es_host=localhost
export es_port=9200
export es_index=cortex_config_settings_dev
export mapping_path=./mappings/cortex-config-settings.json
source $(dirname $0)/build-index.sh