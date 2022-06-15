#!/bin/bash

export DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no -f -L 9200:coordinator.elastic.l4lb.thisdcos.directory:9200 deployer@"$DCOS_MASTER_PRIVATE_IP" sleep 10
bash "$DIR/build-index-jobs-dev.sh"
bash "$DIR/build-index-job-heartbeats-dev.sh"
bash "$DIR/build-index-config-settings-dev.sh"
