#!/bin/bash

ARTIFACTS_S3_BUCKET=$1

aws s3 cp s3://$ARTIFACTS_S3_BUCKET/packages/docker-tars/dc-mongodb-migrate.tar .
aws s3 cp s3://$ARTIFACTS_S3_BUCKET/packages/docker-tars/dc-models-migrate.tar .

docker load --input dc-mongodb-migrate.tar
docker load --input dc-models-migrate.tar