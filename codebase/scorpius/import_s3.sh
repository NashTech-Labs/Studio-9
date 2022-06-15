#!/bin/bash

CONFIG=$1
BUCKETS=${BUCKETS_TO_IMPORT}

if [ ${#} -ne 1 ]; then
  echo "Must supply an arguement for CONFIG"
fi

AWS_DCOS_STACK_BUCKET=$(awk -F\" '/^dcos_stack_bucket/{print $2}'  "environments/$CONFIG.tfvars")
AWS_DCOS_APPS_BUCKET=$(awk -F\" '/^dcos_apps_bucket/{print $2}'  "environments/$CONFIG.tfvars")

# init s3
./terraform.sh init $CONFIG s3_buckets

# import existing stack bucket
if [[ $BUCKETS =~ "stack" || $BUCKETS = "all" ]]; then
    ./terraform.sh import $CONFIG s3_buckets aws_s3_bucket.dcos_stack_bucket $AWS_DCOS_STACK_BUCKET
fi

# import existing apps bucket
if [[ $BUCKETS =~ "apps" || $BUCKETS = "all" ]]; then
    echo "importinh apps bucket"
    ./terraform.sh import $CONFIG s3_buckets aws_s3_bucket.dcos_apps_bucket $AWS_DCOS_APPS_BUCKET
fi

# refresh to obtain outputs
./terraform.sh refresh $CONFIG s3_buckets