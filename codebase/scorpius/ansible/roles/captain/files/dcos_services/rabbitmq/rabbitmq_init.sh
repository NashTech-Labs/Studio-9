#!/bin/bash

export RABBITMQ_USER="$RABBIT_USERNAME"
export RABBITMQ_PASSWORD="$RABBIT_PASSWORD"
export DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ssh -i /opt/private_key -o StrictHostKeyChecking=no -f -L 5672:rabbitmq.marathon.l4lb.thisdcos.directory:5672 deployer@"$DCOS_MASTER_PRIVATE_IP" sleep 10
java -jar $DIR/rabbitmq_init.jar

sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no -f -L 15672:rabbitmq.marathon.l4lb.thisdcos.directory:15672 deployer@"$DCOS_MASTER_PRIVATE_IP" sleep 10
curl -XPUT -i -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" -H "Content-Type: application/json" http://127.0.0.1:15672/api/policies/%2f/ha-all -d '{"pattern":".*", "definition":{"ha-mode":"all","ha-sync-mode":"automatic"}}'