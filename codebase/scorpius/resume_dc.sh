#!/bin/bash

# -s: stacks - a list of comma separated values to overwrite which asgs to resume

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

export AWS_DEFAULT_REGION=$(awk -F\" '/^aws_region/{print $2}'  "environments/$CONFIG.tfvars")
ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
ONLINE_PREDICTION=$(awk -F\" '/^online_prediction/{print $2}'  "environments/$CONFIG.tfvars")
PRIVATE_DEPLOYMENT=$(awk -F\" '/^private_deployment/{print $2}'  "environments/$CONFIG.tfvars")

parse_args()
{
  while getopts ":b:g:s:" opt "$@"; do
    case "$opt" in
      s) set -f
         OLD_IFS=$IFS
         IFS=,
         ASGS=($OPTARG)
         IFS=$OLD_IFS;;
      :) error "option -$OPTARG requires an argument." ;;
      \?) error "unknown option: -$OPTARG" ;;
    esac
  done
}

ASGS=("slave" "gpu-slave" "public-slave" "captain" "bootstrap" "master" "bastion")

parse_args "$@"

for i in "${ASGS[@]}"; do
  aws autoscaling resume-processes --auto-scaling-group-name "$OWNER-$ENVIRONMENT-$i-asg"
  echo "$OWNER-$ENVIRONMENT-$i-asg resumed"
  instance_ids=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$OWNER-$ENVIRONMENT-$i-asg" --query "AutoScalingGroups[].Instances[].InstanceId" --output text)
  for i in $instance_ids; do
    echo "starting instance $i"
    aws ec2 start-instances --instance-ids $i
  done
done

containsElement () {
  local e match="$1"
  shift
  for e; do
    [[ "$e" == "$match" ]] && return 0
  done
  return 1
}

if [[ "$DEPLOY_MODE" != "simple" ]] && (containsElement "slave" "${ASGS[@]}" || containsElement "master" "${ASGS[@]}");then

  ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
  OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
  CLUSTER_NAME=$(awk -F\" '/^cluster_name/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_SLAVES=$(awk -F\" '/^slave_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_PUB_SLAVES=$(awk -F\" '/^public_slave_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_GPU_SLAVES=1
  NUM_SLAVES_JUPYTER=$(awk -F\" '/^slave_jupyter_asg_desired_capacity/{print $2}'  "environments/$CONFIG.tfvars")
  NUM_GPU_SLAVES_JUPYTER=1
  DCOS_NODES=$((NUM_SLAVES + NUM_PUB_SLAVES + NUM_GPU_SLAVES + NUM_GPU_SLAVES_JUPYTER + NUM_SLAVES_JUPYTER))
  DCOS_SERVICES=$(awk -F\" '/^dcos_services/{print $2}'  "environments/$CONFIG.tfvars")

  if [[ "$ONLINE_PREDICTION" != "true" ]];then
    DCOS_SERVICES=$((DCOS_SERVICES - 3))
  fi

  if [[ "$PRIVATE_DEPLOYMENT" = "true" ]];then
    DCOS_SERVICES=$((DCOS_SERVICES + 1))
  fi

  DCOS_MASTER_ELB=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-master-elb --output=text --query "LoadBalancerDescriptions[*].DNSName")
  BAILE_ELB=$(aws elb describe-load-balancers --load-balancer-names=$OWNER-$ENVIRONMENT-baile-elb --output=text --query "LoadBalancerDescriptions[*].DNSName")

  echo "*** Waiting for DeepCortex to resume."
  echo "*** This may take up to 60 minutes..."
  echo ""

  COUNT=0
  echo "Restarting Master node."
  echo ""
  until $(curl --output /dev/null --silent --head --fail http://$DCOS_MASTER_ELB); do
    sleep 60
    COUNT=$((COUNT+1))
    echo "Master node has been restarting for $COUNT minutes."
  done
  echo ""
  echo "Master node up."
  echo "Connecting to the DC/OS cluster..."
  dcos cluster setup http://${DCOS_MASTER_ELB} --username=${DCOS_USERNAME} --password-env=DCOS_PASSWORD --no-check
  dcos cluster attach "$CLUSTER_NAME"
  echo "Connected to cluster."
  echo ""
  echo "*** You can now access the Mater Node at http://${DCOS_MASTER_ELB}"
  echo ""

  COUNT=0
  echo "Restarting public and private slave nodes."
  echo ""
  NODES=$(dcos node | grep agent | wc -l)
  until [[ $NODES -eq $DCOS_NODES ]]; do
    echo "There are currently ${NODES} nodes connected. Waiting for $(( DCOS_NODES - NODES )) more node(s) to connect..."
    sleep 60
    COUNT=$((COUNT+1))
    echo "Nodes have been restarting for $COUNT minutes."
    NODES=$(dcos node | grep agent | wc -l)
  done
  echo "All nodes connected."
  echo ""

  COUNT=0
  echo "Re-deploying all services."
  echo ""
  SERVICES_DEPLOYING=$(dcos marathon app list | grep scale | wc -l)
  SERVICES_RUNNING=$(dcos marathon app list | grep False | wc -l)
  until [[ $SERVICES_DEPLOYING -eq 0 ]] && [[ $SERVICES_RUNNING -eq $DCOS_SERVICES ]]; do
    SERVICES_LEFT=$((DCOS_SERVICES - SERVICES_RUNNING))
    echo "There are currently $SERVICES_LEFT services still re-deploying..."
    sleep 60
    COUNT=$((COUNT+1))
    echo "Services have been re-deploying for $COUNT minutes."
    SERVICES_DEPLOYING=$(dcos marathon app list | grep scale | wc -l)
    SERVICES_RUNNING=$(dcos marathon app list | grep False | wc -l)
  done
  echo "All services re-deployed."
  echo ""

  get_healthy_services() {
    HEALTHY_SERVICES=0
    old_IFS=$IFS
    IFS=$'\n'
    lines=($(dcos marathon app list))
    IFS=${old_IFS}
    HEALTHY_SERVICES=0
    for i in "${lines[@]:1}"; do
      stats=($i)
      health=("${stats[4]}")
      old_IFS=$IFS
      IFS='/'
      health_split=($health)
      if [[ "${health_split[0]}" == "${health_split[1]}" ]]; then
        HEALTHY_SERVICES=$((HEALTHY_SERVICES + 1))
      fi
      IFS=${old_IFS}
    done
  }

  COUNT=0
  echo "Waiting for services to become healthy."
  echo ""
  get_healthy_services
  until [[ $HEALTHY_SERVICES -eq $DCOS_SERVICES ]]; do
    UNHEALTHY_SERVICES=$((DCOS_SERVICES - HEALTHY_SERVICES))
    echo "There are still $UNHEALTHY_SERVICES unhealthy services."
    sleep 60
    COUNT=$((COUNT+1))
    echo "Waiting on healthy services for $COUNT minutes."
    get_healthy_services
  done

  echo "All services are now healthy"
  echo ""

  echo "Restarting some services to ensure proper initialization."
  dcos marathon app restart baile
  dcos marathon app restart orion-api-rest
  dcos marathon app restart cortex-api-rest
  dcos marathon app restart pegasus-api-rest
  dcos marathon app restart taurus

  RESTARTED_SERVICES=3
  SERVICES_DEPLOYING=$(dcos marathon app list | grep restart | wc -l)
  SERVICES_RUNNING=$(dcos marathon app list | grep False | wc -l)
  COUNT=0
  until [[ $SERVICES_DEPLOYING -eq 0 ]] && [[ $SERVICES_RUNNING -eq $DCOS_SERVICES ]]; do
    SERVICES_LEFT=$SERVICES_DEPLOYING
    echo "There are currently $SERVICES_LEFT services still restarting..."
    sleep 60
    COUNT=$((COUNT+1))
    echo "Services have been restarting for $COUNT minutes."
    SERVICES_DEPLOYING=$(dcos marathon app list | grep restart | wc -l)
    SERVICES_RUNNING=$(dcos marathon app list | grep False | wc -l)
  done
  echo "All services restarted."
  echo ""

  until $(curl --output /dev/null --silent --head --fail http://$BAILE_ELB); do
    sleep 30
  done

  echo "*** You can now access DeepCortex at: http://$BAILE_ELB"

  if [[ "$SHUTDOWN_BOOTSTRAP" == "true" ]];then
    echo "Shutting down bootstrap node"
    bash set_capacity.sh bootstrap 0
  fi

fi
