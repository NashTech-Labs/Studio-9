#!/bin/bash

export DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

echo "Repopulating docker registry"
sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no deployer@$DCOS_MASTER_PRIVATE_IP "sudo /bin/bash /opt/populate-local-registry.sh $ARTIFACTS_S3_BUCKET"
