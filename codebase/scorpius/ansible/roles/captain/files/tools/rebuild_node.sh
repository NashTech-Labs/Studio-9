#!/bin/bash

echo "Rebuilding elastic nodes"
bash elastic_rebuild.sh

echo "Rebuilding mongodb nodes"
bash mongodb_rebuild.sh

echo "Ensuring rabbitMQ queues are in place"
bash ../dcos_services/rabbitmq/rabbitmq_init.sh

echo "Re-establishing connections for cortex-rest-api and orion-rest-api"
dcos marathon app restart cortex-api-rest
dcos marathon app restart orion-api-rest