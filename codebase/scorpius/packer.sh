#!/usr/bin/env bash
set -e

usage() {
  echo "Usage: $0 <image> <config_file> [args...]"
  echo " e.g.: $0 bootstrap integration"
  echo "All images require environment variable AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) to be set unless you are using IAM roles"
  exit 1
}

if [ ${#} -ne 2 ]; then
  usage
fi

export USE_IAM_ROLES=$(awk -F\" '/^use_iam_roles/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$USE_IAM_ROLES" != "true" ]];then
  if [[ -z "$AWS_PROFILE" ]];then
    if [[ -z "$AWS_ACCESS_KEY_ID" ]] || [[ -z "$AWS_SECRET_ACCESS_KEY" ]];then
      echo "AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) are not set and USE_IAM_ROLES has not been set to true."
      echo "If you are not providing credentials please set USE_IAM_ROLES to true."
      usage
    fi
  else
    export AWS_ACCESS_KEY_ID=$(aws configure get ${AWS_PROFILE}.aws_access_key_id)
    export AWS_SECRET_ACCESS_KEY=$(aws configure get ${AWS_PROFILE}.aws_secret_access_key)
  fi
else
  export PACKER_IAM_INSTANCE_PROFILE=$(awk -F\" '/^packer_iam_instance_profile/{print $2}'  "environments/$CONFIG.tfvars")
fi

export IMAGE=$1  ; shift
export CONFIG=$1 ; shift

export AMI=$(awk -F\"      '/^packer_base_ami/{print $2}'      "environments/$CONFIG.tfvars")
export REGION=$(awk -F\"   '/^aws_region/{print $2}'           "environments/$CONFIG.tfvars")
export AWS_DEFAULT_REGION=$(awk -F\" '/^aws_region/{print $2}' "environments/$CONFIG.tfvars")
export ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
export OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
export MAIN_USER=$(awk -F\" '/^main_user/{print $2}'  "environments/$CONFIG.tfvars")
export ONLINE_PREDICTION=$(awk -F\" '/^online_prediction/{print $2}'  "environments/$CONFIG.tfvars")
export MACHINE_OS=$(awk -F\" '/^machine_os/{print $2}'  "environments/$CONFIG.tfvars")
export CREATE_VPC=$(awk -F\" '/^create_vpc/{print $2}'  "environments/$CONFIG.tfvars")
export ARTIFACTS_S3_BUCKET=$(awk -F\" '/^artifacts_s3_bucket/{print $2}'  "environments/$CONFIG.tfvars")
export LOGS_S3_PATH=$(awk -F\" '/^deployment_logs_s3_path/{print $2}'  "environments/$CONFIG.tfvars")
export DCOS_APPS_BUCKET=$(awk -F\" '/^dcos_apps_bucket/{print $2}'  "environments/$CONFIG.tfvars")
export PACKER_LOG_FILE=$(awk -F\" '/^packer_log_file/{print $2}'  "environments/$CONFIG.tfvars")
export S3_ENDPOINT=$(awk -F\" '/^s3_endpoint/{print $2}'  "environments/$CONFIG.tfvars")
export PACKER_SECURITY_GROUP=$(awk -F\" '/^packer_security_group/{print $2}'  "environments/$CONFIG.tfvars")
export PRIVATE_DEPLOYMENT=$(awk -F\" '/^private_deployment/{print $2}'  "environments/$CONFIG.tfvars")
export INSTALL_AMAZON_SSM_AGENT=$(awk -F\" '/^install_amazon_ssm_agent/{print $2}'  "environments/$CONFIG.tfvars")
export DCOS_VERSION=$(awk -F\" '/^dcos_version/{print $2}'  "environments/$CONFIG.tfvars")
PACKER_SSH_TIMEOUT=$(awk -F\" '/^packer_ssh_timeout/{print $2}'  "environments/$CONFIG.tfvars")
export PACKER_SSH_TIMEOUT=${PACKER_SSH_TIMEOUT:-10m}
INSTALL_AWS_CLI=$(awk -F\" '/^install_aws_cli/{print $2}'  "environments/$CONFIG.tfvars")
export INSTALL_AWS_CLI=${INSTALL_AWS_CLI:-true}
PACKER_SSH_PRIVATE_IP=$(awk -F\" '/^packer_ssh_private_ip/{print $2}'  "environments/$CONFIG.tfvars")
export PACKER_SSH_PRIVATE_IP=${PACKER_SSH_PRIVATE_IP:-false}
export AWS_CA_BUNDLE=$(awk -F\" '/^aws_ca_bundle/{print $2}'  "environments/$CONFIG.tfvars")

if [[ -z "$AWS_CA_BUNDLE" ]];then
  unset AWS_CA_BUNDLE
fi

if [[ $CREATE_VPC = "false" ]]; then
  export PACKER_VPC_ID=$(awk -F\" '/^vpc_id/{print $2}'  "environments/$CONFIG.tfvars")
  export PACKER_SUBNET_ID=$(awk -F\" '/^subnet_id_0/{print $2}'  "environments/$CONFIG.tfvars")
fi

if [[ "$PRIVATE_DEPLOYMENT" = "true" ]]; then
  echo "Getting information about required tools and software that must be available on s3 bucket"
  if [[ $ONLINE_PREDICTION = "true" ]]; then
    envsubst  < "./ansible/requirements_s3.raw.yml" > "./ansible/requirements_s3.yml"
  else
    envsubst  < "./ansible/requirements_s3_wo_psql.raw.yml" > "./ansible/requirements_s3.yml"
  fi

  export REQUIREMENTS_FILE="./ansible/requirements_s3.yml"

  if [[ -z "$DCOS_VERSION" ]];then
    export DCOS_DOWNLOAD_URL="https://${S3_ENDPOINT}/${ARTIFACTS_S3_BUCKET}/packages/dcos-latest/dcos_generate_config.ee.sh"
  else
    export DCOS_DOWNLOAD_URL="https://${S3_ENDPOINT}/${ARTIFACTS_S3_BUCKET}/packages/dcos-latest/1.12.1/dcos_generate_config.ee.sh"
  fi
else
  if [[ $ONLINE_PREDICTION = "true" ]]; then
    export REQUIREMENTS_FILE="./ansible/requirements.yml"
  else
    export REQUIREMENTS_FILE="./ansible/requirements_wo_psql.yml"
  fi
  if [[ -z "$DCOS_VERSION" ]];then
    export DCOS_DOWNLOAD_URL="https://downloads.mesosphere.com/dcos-enterprise/stable/dcos_generate_config.ee.sh"
  else
    export DCOS_DOWNLOAD_URL="https://downloads.dcos.io/dcos/stable/$DCOS_VERSION/dcos_generate_config.sh"
  fi
fi

export MASTER_XVDE_SIZE=$(awk -F\" '/^master_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export MASTER_XVDF_SIZE=$(awk -F\" '/^master_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export MASTER_XVDH_SIZE=$(awk -F\" '/^master_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")

export SLAVE_XVDE_SIZE=$(awk -F\" '/^slave_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_XVDF_SIZE=$(awk -F\" '/^slave_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_XVDG_SIZE=$(awk -F\" '/^slave_xvdg_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_XVDH_SIZE=$(awk -F\" '/^slave_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")

export PUBLIC_SLAVE_XVDE_SIZE=$(awk -F\" '/^public_slave_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export PUBLIC_SLAVE_XVDF_SIZE=$(awk -F\" '/^public_slave_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export PUBLIC_SLAVE_XVDH_SIZE=$(awk -F\" '/^public_slave_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")

export GPU_SLAVE_XVDE_SIZE=$(awk -F\" '/^gpu_slave_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export GPU_SLAVE_XVDF_SIZE=$(awk -F\" '/^gpu_slave_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export GPU_SLAVE_XVDH_SIZE=$(awk -F\" '/^gpu_slave_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")

export GPU_JUPYTER_XVDE_SIZE=$(awk -F\" '/^gpu_jupyter_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export GPU_JUPYTER_XVDF_SIZE=$(awk -F\" '/^gpu_jupyter_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export GPU_JUPYTER_XVDH_SIZE=$(awk -F\" '/^gpu_jupyter_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")

export SLAVE_JUPYTER_XVDE_SIZE=$(awk -F\" '/^slave_jupyter_xvde_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_JUPYTER_XVDF_SIZE=$(awk -F\" '/^slave_jupyter_xvdf_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_JUPYTER_XVDH_SIZE=$(awk -F\" '/^slave_jupyter_xvdh_size/{print $2}'  "environments/$CONFIG.tfvars")
export SLAVE_JUPYTER_XVDG_SIZE=$(awk -F\" '/^slave_jupyter_xvdg_size/{print $2}'  "environments/$CONFIG.tfvars")


export AWS_REGION="${REGION:-us-east-1}"

PWD=$(pwd)
FILE="$PWD/ansible/roles/deployer/files/id_rsa"
if [ -f "$FILE" ]
then
	echo "SSH Key already created"
else
  echo "Creating ssh key for deployer user"
	ssh-keygen -t rsa -N "" -f $FILE
fi

get_git_describe_with_dirty() {
  # produces abbrev'ed SHA1 of HEAD with possible -dirty suffix

  local D=$(git describe --all --dirty)
  local SHA1=$(git show-ref -s --abbrev refs/${D%-dirty})
  echo ${D/${D%-dirty}/$SHA1}
}

: ${BUILD_UUID:=$(uuidgen)}
GIT_COMMIT=1

run_packer() {
  set -x
  local UUID=$1       ; shift
  local GIT_COMMIT=$1 ; shift
  local opts

  #(( $# >= 1 ))
  [ "${IMAGE}" != "all" ] && opts="-only=${IMAGE}"
  packer build \
      -var "git_commit=$GIT_COMMIT" \
      -var "build_uuid=$UUID" \
      -var "image_name=$IMAGE" \
      -var "aws_access_key=$AWS_ACCESS_KEY_ID" \
      -var "aws_secret_key=$AWS_SECRET_ACCESS_KEY" \
      "$@" ${opts} packer/all.json
  set +x
}

run_packer $BUILD_UUID $GIT_COMMIT "$@"

rm -f ./ansible/requirements_s3.yml
