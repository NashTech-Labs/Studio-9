#!/usr/bin/env bash
set -e

WORKSPACE_ROOT="workspace"
ENVIRONMENT_ROOT="environments"

usage() {
  echo "Usage: $0 <action> <environment> <stack> [args...]"
  echo " e.g.: $0 plan integration"
  echo " "
  echo "    <action> = init|plan|plan-destroy|apply"
  exit 1
}

if [ ${#} -lt 3 ]; then
 usage
fi

export USE_IAM_ROLES=$(awk -F\" '/^use_iam_roles/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$USE_IAM_ROLES" != "true" ]] && [[ -z "$AWS_PROFILE" ]] && ([[ -z "$AWS_ACCESS_KEY_ID" ]] || [[ -z "$AWS_SECRET_ACCESS_KEY" ]]);then
  echo "AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) are not set and USE_IAM_ROLES has not been set to true."
  echo "If you are not providing credentials please set USE_IAM_ROLES to true."
  usage
fi

export ACTION=$1
export CONFIG=$2
export STACK=$3

shift 3

set_backend_variables() {
  REGION=$(awk -F\" '/aws_region/{print $2}'  "environments/$CONFIG.tfvars")
  ACCOUNT=$(awk -F\" '/account/{print $2}'  "environments/$CONFIG.tfvars")
  ENVIRONMENT=$(awk -F\" '/environment /{print $2}'  "environments/$CONFIG.tfvars" | tr -d '\n')
  BUCKET="$(awk -F\" '/^tf_bucket/{print $2}'  "environments/$CONFIG.tfvars")"
  echo "Using bucket [$BUCKET] for account [$ACCOUNT]"
}

set_backend_variables
WORKDIR="$WORKSPACE_ROOT/$ACCOUNT-$REGION-$ENVIRONMENT-$STACK"
DEBUG_OUT="Environment: $ENVIRONMENT Region: $REGION"

PRIVATE_DEPLOYMENT=$(awk -F\" '/^private_deployment/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$PRIVATE_DEPLOYMENT" = "true" ]];then
  VARS=("DCOS_USERNAME" "DCOS_PASSWORD")
else
  VARS=("DCOS_USERNAME" "DCOS_PASSWORD")
fi

for i in "${VARS[@]}"; do
  if [[ -z "${!i}" ]];then
    echo "$i is not set"
    usage
  fi
done

for i in "${VARS[@]}"; do
  var=$i
  val=$(echo "$i" | awk '{print tolower($0)}')
  export TF_VAR_$val=${!var}
done

export AWS_REGION=$REGION

CREATE_IAM=$(awk -F\" '/^create_iam/{print $2}'  "environments/$CONFIG.tfvars")
USE_APP_IAM_USER=$(awk -F\" '/^use_app_iam_user/{print $2}'  "environments/$CONFIG.tfvars")
EMAIL_ON=$(awk -F\" '/^email_on/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$CREATE_IAM" != "true" ]] && [[ "$USE_APP_IAM_USER" = "true" ]];then
  if [[ -z "$APPS_AWS_ACCESS_KEY_ID" ]] || [[ -z "$APPS_AWS_SECRET_ACCESS_KEY" ]];then
    echo "App user access keys are not set"
    exit 1
  fi

  export TF_VAR_apps_access_key=$APPS_AWS_ACCESS_KEY_ID
  export TF_VAR_apps_secret_key=$APPS_AWS_SECRET_ACCESS_KEY
fi

if [[ "$EMAIL_ON" = "true" ]];then
  if [[ -z "$SMTP_AWS_ACCESS_KEY_ID" ]] || [[ -z "$SMTP_AWS_SECRET_ACCESS_KEY" ]];then
    echo "SMTP user AWS access keys are not set and must be set if you are using email in user management."
    echo "Set SMTP_AWS_ACCESS_KEY_ID and SMTP_AWS_SECRET_ACCESS_KEY and run the build again."
    exit 1
  fi
  export TF_VAR_smtp_aws_access_key_id=$SMTP_AWS_ACCESS_KEY_ID
  export TF_VAR_smtp_aws_secret_access_key=$SMTP_AWS_SECRET_ACCESS_KEY
fi

case ${ACTION} in
init)
  if [ -e "$WORKDIR" ]
  then
    echo "Re-Inititializing $DEBUG_OUT"
    cd "$WORKDIR"
    terraform init \
      -backend-config "bucket=$BUCKET" \
      -backend-config "key=$REGION/$ENVIRONMENT/$STACK/terraform.tfstate" \
      -backend-config "region=${REGION}"
  else
    echo "Inititializing $DEBUG_OUT"
    mkdir -p "$WORKDIR"
    cd "$WORKDIR"
    terraform init  \
      -backend-config "bucket=$BUCKET" \
      -backend-config "key=$REGION/$ENVIRONMENT/$STACK/terraform.tfstate" \
      -backend-config "region=${REGION}" \
      -from-module="../../terraform/$STACK/"
  fi
  #echo "Copying environment setting to workspace"
  #cp "$ENVIRONMENT_ROOT/$CONFIG.tfvars" "$WORKDIR/terraform.tfvars"
  ;;
clean)
  echo "Cleaning workspace for $DEBUG_OUT"
  rm -rf "$WORKDIR"
  ;;
import)
  [ -e "$WORKDIR" ] || {
      echo >&2 "Please run init first"
      exit 1
  }
  echo "Importing modules and resources"
  cp "$ENVIRONMENT_ROOT/$CONFIG.tfvars" "$WORKDIR/terraform.tfvars"
  cd "$WORKDIR"
  terraform import "$@"
  ;;
refresh)
  [ -e "$WORKDIR" ] || {
      echo >&2 "Please run init first"
      exit 1
  }
  echo "Refreshing modules and resources"
  cd "$WORKDIR"
  terraform refresh "$@"
  ;;
output)
  [ -e "$WORKDIR" ] || {
      echo >&2 "Please run init first"
      exit 1
  }
  echo "Refreshing modules and resources"
  cd "$WORKDIR"
  terraform refresh "$@"
  ;;
state-rm)
  [ -e "$WORKDIR" ] || {
      ./terraform.sh init $CONFIG $STACK
  }
  echo "Refreshing modules and resources"
  cd "$WORKDIR"
  terraform state rm "$@"
  ;;
plan)
  [ -e "$WORKDIR" ] || {
      echo >&2 "Please run init first"
      exit 1
  }
  echo "Planning for $DEBUG_OUT"

  cp "$ENVIRONMENT_ROOT/$CONFIG.tfvars" "$WORKDIR/terraform.tfvars"
  rsync -ar --no-links "terraform/$STACK/" "$WORKDIR/"
  cd "$WORKDIR"
  terraform plan -out "$ENVIRONMENT.tfplan" "$@"
  ;;
plan-destroy)
  echo "Planning destructon of $DEBUG_OUT"
  [ -e "$WORKDIR" ] || {
      echo >&2 "Please run init first"
      exit 1
  }
  cd "$WORKDIR"
  terraform plan -destroy -out "$ENVIRONMENT.tfplan" "$@"
  ;;
destroy)
  echo "Destroying $DEBUG_OUT"
  [ -e "$WORKDIR" ] || {
      ./terraform.sh init $CONFIG $STACK
  }
  cp "$ENVIRONMENT_ROOT/$CONFIG.tfvars" "$WORKDIR/terraform.tfvars"
  cd "$WORKDIR"
  terraform destroy "$@"
  ;;
apply)
  [ -e "$WORKDIR/$ENVIRONMENT.tfplan" ] || {
      echo >&2 "No $ENVIRONMENT.tfplan found - please run plan or plan-destroy mode first"
      exit 1
  }
  echo "Applying for $DEBUG_OUT"
  cd "$WORKDIR"
  terraform apply "$ENVIRONMENT.tfplan"
  ;;
show)
  [ -e "$WORKDIR/$ENVIRONMENT.tfplan" ] || {
      echo >&2 "No $ENVIRONMENT.tfplan found - please run plan or plan-destroy mode first"
      exit 1
  }
  echo "Showing for $DEBUG_OUT"
  cd "$WORKDIR"
  terraform show "$ENVIRONMENT.tfplan"
  ;;
console)
  [ -e "$WORKDIR/$ENVIRONMENT.tfplan" ] || {
      echo >&2 "No $ENVIRONMENT.tfplan found - please run plan or plan-destroy mode first"
      exit 1
  }
  echo "Starting console for $DEBUG_OUT"
  cd "$WORKDIR"
  terraform console
  ;;
esac
