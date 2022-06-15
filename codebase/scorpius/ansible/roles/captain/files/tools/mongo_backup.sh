#!/bin/bash

DATABASE=$1

MONGODB_HOST_0=$(aws ec2 describe-instances --filters "Name=tag:Role,Values=slave" "Name=tag:environment,Values=$ENVIRONMENT" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].PrivateIpAddress" --output text)
MONGODB_PRIMARY=$(mongo --quiet "mongodb://useradmin:$MONGODB_USERADMIN_PASSWORD@$MONGODB_HOST_0:27017/admin" --eval "rs.isMaster().primary" | tail -1)
DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

ssh -i /opt/private_key -o StrictHostKeyChecking=no -f -L 27017:"$MONGODB_PRIMARY" deployer@"$DCOS_MASTER_PRIVATE_IP" sleep 10
mongodump -h localhost:27017 -d $DATABASE -u admin -p $MONGODB_ROOTADMIN_PASSWORD --authenticationDatabase admin --out dump$DATABASE --quiet

DATE=`date +%Y_%m_%d`

tar -czf dump$DATABASE.tar.gz dump$DATABASE
aws s3 cp dump$DATABASE.tar.gz s3://$AWS_S3_BUCKET/mongo_backups/$DATE/dump$DATABASE.tar.gz

rm -rf dump$DATABASE
rm -f dump$DATABASE.tar.gz