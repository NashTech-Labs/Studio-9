#!/bin/sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export OVERWRITE_INDEX="${1:-false}"
export es_index=cortex_config_settings_dev
export mapping_path=../mappings/cortex-config-settings-dev.json
source "$DIR/build-index.sh"
