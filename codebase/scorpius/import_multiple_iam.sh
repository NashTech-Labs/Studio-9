#!/bin/bash

CONFIG=$1

if [ ${#} -ne 1 ]; then
  echo "Must supply an arguement for CONFIG"
fi

OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")

# init vpc
./terraform.sh init $CONFIG iam

# import IAM app user
./terraform.sh import $CONFIG iam aws_iam_user.app $OWNER-$ENVIRONMENT-app

# import bastion instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.bastion_instance_profile $OWNER-$ENVIRONMENT-bastion_role

# import bootstrap instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.bootstrap_instance_profile $OWNER-$ENVIRONMENT-bootstrap_role

# import master instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.master_instance_profile $OWNER-$ENVIRONMENT-master_role

# import slave instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.slave_instance_profile $OWNER-$ENVIRONMENT-slave_role

# import captain instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.captain_instance_profile $OWNER-$ENVIRONMENT-captain_role

# refresh to obtain outputs
./terraform.sh refresh $CONFIG iam