#!/bin/bash

# -s: stacks - a list of comma separated values to overwrite which asgs to shutdown

usage() {
  echo "Usage: Must set environment variables for CONFIG, AWS_PROFILE or (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)"
  exit 1
}

VARS=("CONFIG")
for i in "${VARS[@]}"; do
  if [[ -z "${!i}" ]];then
    echo "$i is not set"
    usage
  fi
done

export USE_IAM_ROLES=$(awk -F\" '/^use_iam_roles/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$USE_IAM_ROLES" != "true" ]] && [[ -z "$AWS_PROFILE" ]] && ([[ -z "$AWS_ACCESS_KEY_ID" ]] || [[ -z "$AWS_SECRET_ACCESS_KEY" ]]);then
  echo "AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) are not set and USE_IAM_ROLES has not been set to true."
  echo "If you are not providing credentials please set USE_IAM_ROLES to true."
  usage
fi

if [ ${#} -ne 1 ]; then
  echo "Must specify machine"
  exit 1
fi

MACHINE=$1

export AWS_DEFAULT_REGION=$(awk -F\" '/^aws_region/{print $2}'  "environments/$CONFIG.tfvars")
ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")

instance_ids=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$OWNER-$ENVIRONMENT-$MACHINE-asg" --query "AutoScalingGroups[].Instances[].InstanceId" --output text)

aws ec2 start-instances --instance-ids $instance_ids

echo "$MACHINE stopped"