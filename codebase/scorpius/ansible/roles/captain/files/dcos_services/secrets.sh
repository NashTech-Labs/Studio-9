#!/usr/bin/env bash

#orion-api-rest service secrets
export ORION_HTTP_SEARCH_USER_PASSWORD=${ORION_HTTP_SEARCH_USER_PASSWORD}

#pegasus-api-rest service secrets
export PEGASUS_HTTP_AUTH_USER_PASSWORD=${PEGASUS_HTTP_AUTH_USER_PASSWORD}

#gemini service secrets
export GEMINI_HTTP_AUTH_PASSWORD="${GEMINI_HTTP_AUTH_PASSWORD}"

#common secret variables used in aries-api-rest, cortex-api-rest, baile and taurus service
export ARIES_HTTP_SEARCH_USER_PASSWORD=${ARIES_HTTP_SEARCH_USER_PASSWORD}

#common secret variables used in aries-api-rest and cortex-api-rest service
export ARIES_HTTP_COMMAND_USER_PASSWORD=${ARIES_HTTP_COMMAND_USER_PASSWORD}

#common secret variable used in argo-api-rest and taurus service
export ARGO_HTTP_AUTH_USER_PASSWORD=${ARGO_HTTP_AUTH_USER_PASSWORD}

#common variable used in baile, cortex-api-rest and taurus service secrets
export CORTEX_HTTP_SEARCH_USER_PASSWORD=${CORTEX_HTTP_SEARCH_USER_PASSWORD}

#common secret variable used in cortex-api-rest, orion, pegasus-api-rest,taurus, logstash and rabbitmq service
export RABBITMQ_PASSWORD=${RABBIT_PASSWORD}

#common secret variable used in baile and taurus service
export BAILE_ONLINE_PREDICTION_PASSWORD=${ONLINE_PREDICTION_PASSWORD}

export BAILE_HTTP_AUTH_USER_PASSWORD=${BAILE_HTTP_AUTH_USER_PASSWORD}

#common secret variable used in baile , orion and sql-server service
export REDSHIFT_PASSWORD=${REDSHIFT_PASSWORD}
