#!/bin/bash

CONFIG=$1

if [ ${#} -ne 1 ]; then
  echo "Must supply an arguement for CONFIG"
fi

OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
USE_APP_IAM_USER=$(awk -F\" '/^use_app_iam_user/{print $2}'  "environments/$CONFIG.tfvars")

# init iam
./terraform.sh init $CONFIG iam

# import IAM app user
if [[ "$USE_APP_IAM_USER" = "true" ]];then
  ./terraform.sh import $CONFIG iam aws_iam_user.app $OWNER-$ENVIRONMENT-app
fi

# import main deepcortex instance profile
./terraform.sh import $CONFIG iam aws_iam_instance_profile.deepcortex_main_instance_profile $OWNER-$ENVIRONMENT-deepcortex_main_role

# import main deepcortex main role arn
./terraform.sh import $CONFIG iam aws_iam_role.deepcortex_main_role $OWNER-$ENVIRONMENT-deepcortex_main_role

# refresh to obtain outputs
./terraform.sh refresh $CONFIG iam