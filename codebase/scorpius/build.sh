#!/usr/bin/env bash
set -e

# optional arguments:
# -b: shutdown boostrap - can be set to true destroy bootstrap node after the cluster deploys
# -g: gpu on start - can be set to false to exclude spinning up a gpu node after the cluster deploys
# -i: imports - a list of comma separated values to overwrite which terraform modules should be imported
# -m: deploy mode - can be set to simple to exclude download of DC/OS cli and extra output
# -s: stacks - a list of comma separated values to overwrite which terraform stacks to build
# -p: packer - can be set to false to exclude packer builds
# -t: s3 type - can be set to existing to import existing S3 buckets
# -n: step number - can be set to start at a step other than 1
# -d: nodes - can be set to specify the number of nodes it different from default

usage() {
  echo "Usage: Must set environment variables for CONFIG, AWS_PROFILE or (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY), DCOS_USERNAME, DCOS_PASSWORD, DOCKER_EMAIL_LOGIN, DOCKER_REGISTRY_AUTH_TOKEN"
  exit 1
}

OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")

configureDCOSCli() {
  if $(dcos cluster list | grep -q "$CLUSTER_NAME"); then
    echo "cluster already set-up"
    dcos cluster attach "$CLUSTER_NAME"
  else
    dcos cluster setup http://${DCOS_MASTER_ELB} --username=${DCOS_USERNAME} --no-check
    dcos cluster attach "$CLUSTER_NAME"
  fi
  echo "Connected to cluster."
  echo ""
  echo "*** You can now access the Master Node: "
  echo "External URL http://${DCOS_MASTER_ELB_EXT}"
  echo "Internal URL http://${DCOS_MASTER_ELB_IN}"
  echo ""
}

destroyRogueNodes() {
  SLAVES_IPS_ON_AWS=( `aws ec2 describe-instances --region $AWS_DEFAULT_REGION --filters "Name=tag:Name,Values=$OWNER-$ENVIRONMENT-slave*,$OWNER-$ENVIRONMENT-public-slave*" --output text --query 'Reservations[*].Instances[*].NetworkInterfaces[*].PrivateIpAddresses[*].PrivateIpAddress' | awk '{print $0}' | tr '\n' ' '` )
  SLAVES_IPS_ON_DCOS=( `dcos node | grep agent | awk '{print $2}' | tr '\n' ' '` )

  echo "AWS slave IP addresses"
  echo ${SLAVES_IPS_ON_AWS[@]}

  echo "DC/OS slave IP addresses"
  echo ${SLAVES_IPS_ON_DCOS[@]}

  for slave_on_aws in "${SLAVES_IPS_ON_AWS[@]}";
  do
    skip=;
    for slave_on_dcos in "${SLAVES_IPS_ON_DCOS[@]}";
    do
      [[ $slave_on_aws == $slave_on_dcos ]] && { skip=1;
      break; };
    done;
    [[ -n $skip ]] || ROGUE_NODES+=("$slave_on_aws");
  done

  echo "The following nodes were not connected properly: "
  echo ${ROGUE_NODES[@]}

  for rogue_node in "${ROGUE_NODES[@]}"
  do
    instance_id=( `aws ec2 describe-instances --region $AWS_DEFAULT_REGION --filters "Name=private-ip-address,Values=$rogue_node" --output text --query 'Reservations[*].Instances[*].InstanceId'` )
    aws ec2 terminate-instances --region $AWS_DEFAULT_REGION --instance-ids $instance_id
    echo "Destroying rogue aws-instances. It will allow autoscaling group to spin up fresh instances"
  done
}

destroyFailedMaster() {
  MASTER_INSTANCE_NAME=$OWNER-$ENVIRONMENT-master
  instance_id=$(aws ec2 describe-instances --region $AWS_DEFAULT_REGION --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query 'Reservations[*].Instances[*].InstanceId' --output text)
  aws ec2 terminate-instances --region $AWS_DEFAULT_REGION --instance-ids $instance_id
  echo "Destroying failed master. It will allow autoscaling group to spin up a fresh instance"
}

buildTerraformTemplate() {

  local CONFIG=$1
  local i=$2

  sh terraform.sh init $CONFIG "$i"
  sh terraform.sh plan $CONFIG "$i"
  sh terraform.sh apply $CONFIG "$i"
}

connectToMachine() {
  MACHINE=$1
  echo "Starting ssh agent..."
  eval `ssh-agent -s`
  SSH_KEY_NAME=$(awk -F\" '/^ssh_key_name/{print $2}'  "environments/$CONFIG.tfvars")

  if [[ -z $SSH_KEY_NAME ]];then
    read -p "Enter the file name of the ssh key pair you would like to use to connect to the $MACHINE machine: " SSH_KEY_NAME
    echo ""
  fi

  ssh-add ~/.ssh/$SSH_KEY_NAME
}

if [[ -z "$CONFIG" ]];then
  echo "CONFIG is not set"
  usage
fi

export USE_IAM_ROLES=$(awk -F\" '/^use_iam_roles/{print $2}'  "environments/$CONFIG.tfvars")

if [[ "$USE_IAM_ROLES" != "true" ]] && [[ -z "$AWS_PROFILE" ]] && ([[ -z "$AWS_ACCESS_KEY_ID" ]] || [[ -z "$AWS_SECRET_ACCESS_KEY" ]]);then
  echo "AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) are not set and USE_IAM_ROLES has not been set to true."
  echo "If you are not providing credentials please set USE_IAM_ROLES to true."
  usage
fi

parse_args()
{
  while getopts ":b:d:d:g:i:m:n:p:s:t:y:" opt "$@"; do
    case "$opt" in
      b) SHUTDOWN_BOOTSTRAP="$OPTARG" ;;
      g) GPU_ON_START="$OPTARG" ;;
      i) set -f
         IFS=,
         IMPORTS=($OPTARG) ;;
      m) DEPLOY_MODE="$OPTARG" ;;
      d) NODE_NUMBER="$OPTARG" ;;
      n) STEP_NUMBER="$OPTARG" ;;
      p) PACKER="$OPTARG" ;;
      s) set -f
         IFS=,
         STACKS=($OPTARG) ;;
      t) S3_TYPE="$OPTARG" ;;
      y) AUTO_YES="$OPTARG" ;;
      :) error "option -$OPTARG requires an argument." ;;
      \?) error "unknown option: -$OPTARG" ;;
    esac
  done
}

export AWS_DEFAULT_REGION=$(awk -F\" '/^aws_region/{print $2}'  "environments/$CONFIG.tfvars")

parse_args "$@"

STEP=${STEP_NUMBER:-1}

if [[ $STEP -le 1 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step 1: Initialize terraform S3 bucket? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 1"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    sh terraform_init_backend.sh $CONFIG
  done
fi

CREATE_VPC=$(awk -F\" '/^create_vpc/{print $2}'  "environments/$CONFIG.tfvars")
CREATE_IAM=$(awk -F\" '/^create_iam/{print $2}'  "environments/$CONFIG.tfvars")
ONLY_PUBLIC=$(awk -F\" '/^only_public/{print $2}'  "environments/$CONFIG.tfvars")
ONLINE_PREDICTION=$(awk -F\" '/^online_prediction/{print $2}'  "environments/$CONFIG.tfvars")
PRIVATE_DEPLOYMENT=$(awk -F\" '/^private_deployment/{print $2}'  "environments/$CONFIG.tfvars")
AWS_DCOS_STACK_BUCKET=$(awk -F\" '/^dcos_stack_bucket/{print $2}'  "environments/$CONFIG.tfvars")
AWS_DCOS_APPS_BUCKET=$(awk -F\" '/^dcos_apps_bucket/{print $2}'  "environments/$CONFIG.tfvars")
SINGULAR_IAM_ROLE=$(awk -F\" '/^singular_iam_role/{print $2}'  "environments/$CONFIG.tfvars")
INCLUDE_REDSHIFT=$(awk -F\" '/^include_redshift/{print $2}'  "environments/$CONFIG.tfvars")


if [[ $STEP -le 2 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]];then
      while true; do
        read -n 1 -p "Run step 2: Build base infrastructure from terraform modules? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 2"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    if [[ -z $STACKS && -z $IMPORTS ]];then
      STACKS=()
      IMPORTS=()

      if [[ "$CREATE_VPC" = "true" ]];then
        STACKS+=("vpc")
        echo "Adding VPC to Stack"
      else
        IMPORTS+=("vpc")
        echo "Adding VPC to Imports"
      fi

      if [[ "$S3_TYPE" = "existing" ]];then

        while true; do
          read -p "Enter a comma separate list of buckets to import or leave blank to import them all: " buckets
          echo ""
          read -n 1 -p "You will import the following buckets \"$buckets\", is this correct? [y/n or q to exit] " yn
          echo ""
          case $yn in
              [Yy]* ) break;;
              [Nn]* ) continue;;
              [Qq]* ) exit 0;;
              * ) echo "Please provide a yes or no answer."
          esac
        done

        IMPORTS+=("s3")
        export BUCKETS_TO_IMPORT=$buckets
        echo "Adding S3 to imports for bucket(s) \"$buckets\""

        if [[ $buckets != "all" ]];then
          STACKS+=("s3_buckets")
          echo "Adding s3_buckets to Stack for all other buckets"
        fi

      else
        STACKS+=("s3_buckets")
        echo "Adding s3_buckets to Stack for all other buckets"
      fi

      if [[ "$CREATE_IAM" = "true" ]];then
        STACKS+=("iam")
        echo "Adding IAM to Stack"
      else
        echo "Adding IAM to imports"
        if [[ "$SINGULAR_IAM_ROLE" = "true" ]];then
          IMPORTS+=("singular_iam")
        else
          IMPORTS+=("multiple_iam")
        fi
      fi

      STACKS+=("base")
      echo "Adding base to Stack"

      if [[ "$ONLY_PUBLIC" != "true" ]];then
        STACKS+=("nat")
        echo "Adding nat to Stack"
      fi

      if [[ "$ONLINE_PREDICTION" = "true" ]];then
        STACKS+=("online_prediction")
        echo "Adding online_prediction to Stack"
      fi

      if [[ "$INCLUDE_REDSHIFT" = "true" ]];then
        STACKS+=("redshift")
        echo "Adding redshift to Stack"
      fi

    fi

    if [[ $IMPORTS != "none" ]];then
      for _ in once; do
        for i in "${IMPORTS[@]}"; do
          if [[ $AUTO_YES != "true" ]]; then
            while true; do
              read -n 1 -p "Would you like to import the $i module? [y/n or q to exit] " yn
              echo ""
              case $yn in
                  [Yy]* ) echo "Importing $i" && sh import_$i.sh $CONFIG; break;;
                  [Nn]* ) break;;
                  [Qq]* ) exit 0;;
                  * ) echo "Please provide a yes or no answer."
              esac
            done
          else
            echo "Importing $i" && sh import_$i.sh $CONFIG $EXTRA_PARAMS
          fi
        done
      done
    fi

    if [[ $STACKS != "none" ]];then
      for _ in once; do
        for i in "${STACKS[@]}"; do
          if [[ $AUTO_YES != "true" ]]; then
            while true; do
              read -n 1 -p "Would you like to build the $i template? [y/n or q to exit] " yn
              echo ""
              case $yn in
                  [Yy]* ) echo "Building $i" && buildTerraformTemplate $CONFIG "$i"; break;;
                  [Nn]* ) break;;
                  [Qq]* ) exit 0;;
                  * ) echo "Please provide a yes or no answer."
              esac
            done
          else
            echo "Building $i" && buildTerraformTemplate $CONFIG "$i"
          fi
        done
      done
    fi
  done

  STACKS=""

fi

BASTION_INSTANCE_NAME=$OWNER-$ENVIRONMENT-bastion
BASTION_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$BASTION_INSTANCE_NAME --query "Reservations[*].Instances[*].PublicIpAddress" --output text)
THRESHOLD_IN_MINUTES=$(awk -F\" '/^threshold_for_rogue_nodes/{print $2}'  "environments/$CONFIG.tfvars")

if [[ $STEP -le 3 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step 3: Build packer AMIs for DC/OS Cluster ? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 3"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    if [[ "$PACKER" != "false" ]] && ([[ "${STACKS[@]}" =~ "platform" ]] || [[ -z $STACKS ]]);then
      sh packer.sh all $CONFIG;
    fi
  done
fi

if [[ -z $STACKS ]]; then
  STACKS="platform"
fi

if [[ $STACKS != "none" && $STACKS =~ "platform" ]];then
  if [[ $STEP -le 5 ]];then
    for _ in once; do
      if [[ $AUTO_YES != "true" ]]; then
        while true; do
          read -n 1 -p "Run step 6: Build DC/OS cluster? [y/n or q to exit] " yn
          echo ""
          case $yn in
              [Yy]* ) echo "Running step 6"; break;;
              [Nn]* ) break 2;;
              [Qq]* ) exit 0;;
              * ) echo "Please provide a yes or no answer."
          esac
        done
      fi

      echo "Building DC/OS cluster" && buildTerraformTemplate $CONFIG "platform"

    done
  fi
fi

if ([[ "$DEPLOY_MODE" != "simple" ]] && [[ "${STACKS[@]}" =~ "platform" ]]) || [[ "$STACKS" = "none" ]];then

  CLUSTER_NAME=$(awk -F\" '/^cluster_name/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_SLAVES=$(awk -F\" '/^slave_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_PUB_SLAVES=$(awk -F\" '/^public_slave_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_GPU_SLAVES=$(awk -F\" '/^gpu_slave_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_GPU_JUPYTER=$(awk -F\" '/^gpu_jupyter_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_SLAVES_JUPYTER=$(awk -F\" '/^slave_jupyter_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")


  if [[ -z $NODE_NUMBER ]];then
    DCOS_NODES=$((NUM_SLAVES + NUM_PUB_SLAVES + NUM_GPU_SLAVES + NUM_GPU_JUPYTER + NUM_SLAVES_JUPYTER))
  else
    DCOS_NODES=$NODE_NUMBER
  fi

  DCOS_SERVICES=$(awk -F\" '/^dcos_services/{print $2}'  "environments/$CONFIG.tfvars")
  DEPLOY_SG=$(awk -F\" '/^deploy_sg/{print $2}'  "environments/$CONFIG.tfvars")

  if [[ "$ONLINE_PREDICTION" != "true" ]];then
    DCOS_SERVICES=$((DCOS_SERVICES - 3))
  fi

  if [[ "$PRIVATE_DEPLOYMENT" = "true" ]];then
    DCOS_SERVICES=$((DCOS_SERVICES + 1))
    echo "There are a total of ${DCOS_SERVICES} dcos services that will deploy during private deployment"
  fi

  DCOS_MASTER_ELB_EXT=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-master-elb --output=text --query "LoadBalancerDescriptions[*].DNSName")
  BAILE_ELB_EXT=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-baile-elb --output=text --query "LoadBalancerDescriptions[*].DNSName")
  DCOS_MASTER_ELB_IN=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-master-elb-in --output=text --query "LoadBalancerDescriptions[*].DNSName")
  BAILE_ELB_IN=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-baile-elb-in --output=text --query "LoadBalancerDescriptions[*].DNSName")

  if [[ "$DEPLOY_SG" = "" ]];then
    DCOS_MASTER_ELB=${DCOS_MASTER_ELB_EXT}
    BAILE_ELB=${BAILE_ELB_EXT}
  else
    DCOS_MASTER_ELB=${DCOS_MASTER_ELB_IN}
    BAILE_ELB=${BAILE_ELB_IN}
  fi

  echo "*** Waiting for DeepCortex to finish building and initializing."
  echo "*** This may take up to 90 minutes..."
  echo ""

  COUNT=0
  echo "Deploying Master node."
  until $(curl --output /dev/null --silent --head --fail http://$DCOS_MASTER_ELB); do
    sleep 60
    COUNT=$((COUNT+1))
    echo "Master node has been deploying for $COUNT minutes."
    if [[ "$COUNT" -gt "$THRESHOLD_IN_MINUTES" ]]; then
      echo "destroying failed master..."
      destroyFailedMaster
      COUNT=0
      echo "Trying to redeploy master....."
    fi
  done
  echo ""
  echo "Master node deployed."
  echo "Connecting to the DC/OS cluster..."
  configureDCOSCli

  COUNT=0
  echo "Deploying public and private slave nodes."
  NODES=$(dcos node | grep agent | wc -l)
  until [[ $NODES -eq $DCOS_NODES ]]; do
    echo "There are currently ${NODES} nodes connected. Waiting for $((DCOS_NODES - NODES)) more node(s) to connect..."
    sleep 60
    COUNT=$((COUNT + 1))
    echo "Nodes have been deploying for $COUNT minutes."
    if [[ "$COUNT" -gt "$THRESHOLD_IN_MINUTES" ]]; then
      echo "destroying rogue nodes..."
      destroyRogueNodes
      COUNT=0
      echo "Trying to redeploy nodes....."
    fi
    NODES=$(dcos node | grep agent | wc -l)
  done
  echo "All nodes connected."
  echo ""

  echo "Connecting to captain node to start configuration and initialization processes"

  CAPTAIN_INSTANCE_NAME=$OWNER-$ENVIRONMENT-captain
  CAPTAIN_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$CAPTAIN_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output text)
  connectToMachine $CAPTAIN_INSTANCE_NAME
  ssh -A -o ServerAliveInterval=60 -o StrictHostKeyChecking=no -t ec2-user@$BASTION_IP "ssh -t -o ServerAliveInterval=60 -o StrictHostKeyChecking=no ec2-user@$CAPTAIN_PRIVATE_IP sudo bash /opt/dcos_services/deploy_all_services.sh"

  echo "All services deployed."
  echo "Waiting for DeepCortex to become available"

  until $(curl --output /dev/null --silent --head --fail http://$BAILE_ELB); do
    sleep 30
  done
  echo "*** You can now access DeepCortex: "
  echo "External URL http://${BAILE_ELB_EXT}"
  echo "Internal URL http://${BAILE_ELB_IN}"

  echo "Starting up Slave node for Jupyter Service"
  bash set_capacity.sh slave-jupyter 1

  if [[ "$GPU_ON_START" != "false" ]];then
    for _ in once; do
      while true; do
        read -n 1 -p "Would you like to start up a gpu node? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done

      echo "Starting up GPU node"
      bash set_capacity.sh gpu-slave 1

      echo "Starting up GPU node for Jupyter Service"
      bash set_capacity.sh gpu-jupyter 1

      COUNT=0
      echo "Deploying gpu node."
      echo "If step lasts longer than 30 minutes there may be an issue with the node."
      echo "To attempt a fix, terminate the GPU node in AWS so the auto scaling group can deploy a new one."
      echo ""
      NODES=$(dcos node | grep agent | wc -l)
      echo "Waiting for the GPU node to connect..."
      until [[ $NODES -eq $((DCOS_NODES + 3)) ]]; do
        sleep 60
        COUNT=$((COUNT+1))
        echo "GPU node and GPU Jupyter node has been deploying for $COUNT minutes."
        NODES=$(dcos node | grep agent | wc -l)
      done
      echo "GPU is restarting to initialize CUDA"
      sleep 120
      echo "GPU node connected"
      echo "Please be sure the GPU node shows as healthy in the DC/OS UI before performing any tasks using this machine."

      if [[ "$SHUTDOWN_BOOTSTRAP" == "true" ]];then
        echo "Shutting down bootstrap node"
        bash set_capacity.sh bootstrap 0
      fi

      echo "*** Deployment complete"

    done
  fi
  echo "*** Deployment complete"
fi
