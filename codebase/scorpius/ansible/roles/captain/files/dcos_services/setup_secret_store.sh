#!/usr/bin/env bash

source secrets.sh

declare -A SECRETS

SECRETS=(["orion_http_search_user_password"]=${ORION_HTTP_SEARCH_USER_PASSWORD} ["pegasus_http_auth_user_password"]=${PEGASUS_HTTP_AUTH_USER_PASSWORD} ["baile_online_prediction_password"]=${BAILE_ONLINE_PREDICTION_PASSWORD}
["aries_http_search_user_password"]=${ARIES_HTTP_SEARCH_USER_PASSWORD} ["aries_http_command_user_password"]=${ARIES_HTTP_COMMAND_USER_PASSWORD} ["argo_http_auth_user_password"]=${ARGO_HTTP_AUTH_USER_PASSWORD}
["cortex_http_search_user_password"]=${CORTEX_HTTP_SEARCH_USER_PASSWORD} ["rabbitmq_password"]=${RABBITMQ_PASSWORD} ["gemini_http_auth_password"]="${GEMINI_HTTP_AUTH_PASSWORD}"
["baile_http_auth_user_password"]="${BAILE_HTTP_AUTH_USER_PASSWORD}" ["cortex_http_search_user_password"]=${CORTEX_HTTP_SEARCH_USER_PASSWORD} ["rabbitmq_password"]=${RABBITMQ_PASSWORD} ["gemini_http_auth_password"]="${GEMINI_HTTP_AUTH_PASSWORD}" ["redshift_password"]="${REDSHIFT_PASSWORD}")

for key in ${!SECRETS[@]}; do
    curl -X PUT --cacert dcos-ca.crt -H "Authorization: token=$(dcos config show core.dcos_acs_token)" -d '{"value":'\"${SECRETS[${key}]}\"'}' $(dcos config show core.dcos_url)/secrets/v1/secret/default/$key -H 'Content-Type: application/json'
done

