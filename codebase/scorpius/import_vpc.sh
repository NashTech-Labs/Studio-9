#!/bin/bash

CONFIG=$1

if [ ${#} -ne 1 ]; then
  echo "Must supply an arguement for CONFIG"
fi

VPC_ID=$(awk -F\" '/^vpc_id/{print $2}'  "environments/$CONFIG.tfvars")
VPCE_ID=$(awk -F\" '/^vpce_id/{print $2}'  "environments/$CONFIG.tfvars")
SUBNET_ID_0=$(awk -F\" '/^subnet_id_0/{print $2}'  "environments/$CONFIG.tfvars")
SUBNET_ID_1=$(awk -F\" '/^subnet_id_1/{print $2}'  "environments/$CONFIG.tfvars")

# init vpc
./terraform.sh init $CONFIG vpc

# import existing VPC
./terraform.sh import $CONFIG vpc module.vpc.aws_vpc.vpc $VPC_ID

# import existing VPCE
./terraform.sh import $CONFIG vpc module.vpc.aws_vpc_endpoint.s3 $VPCE_ID

# import existing public subnets
./terraform.sh import $CONFIG vpc 'module.vpc.aws_subnet.public[0]' $SUBNET_ID_0
./terraform.sh import $CONFIG vpc 'module.vpc.aws_subnet.public[1]' $SUBNET_ID_1

# refresh to obtain outputs
./terraform.sh refresh $CONFIG vpc