#!/bin/bash

AZS_STR=${azs}
RTS_STR=${rts}
EGRESS_SUBNETS_STR=${egress_cidr}
VPC_CIDR=${vpc_cidr}

PACKAGES="jq"

yum install -y $${PACKAGES}
yum install -y amazon-ssm-agent

META_DATA="http://169.254.169.254/latest"
INSTANCE_ID=$(curl -s $${META_DATA}/meta-data/instance-id)
IPADDR=$(curl -s $${META_DATA}/meta-data/local-ipv4)
REGION=$(curl -s $${META_DATA}/dynamic/instance-identity/document | jq -r ".region")
AZ=$(curl -s $${META_DATA}/meta-data/placement/availability-zone)

# Build two arrays for AZs and RTs and
# Backup current value of IFS and define a new attribute
# separator to be used by read
BIFS=$${IFS}
IFS=$','
read -r -a AZS            <<< "$${AZS_STR}"
read -r -a RTS            <<< "$${RTS_STR}"
read -r -a EGRESS_SUBNETS <<< "$${EGRESS_SUBNETS_STR}"
IFS=$${BIFS}

ENI_ID=$(
  aws ec2 describe-network-interfaces \
    --region $${REGION} \
    --filters Name=private-ip-address,Values=$${IPADDR} |\
  jq -r ".NetworkInterfaces[].NetworkInterfaceId" \
)

aws ec2 modify-instance-attribute \
  --region $${REGION} \
  --instance-id $${INSTANCE_ID} \
  --no-source-dest-check

# Hash RT IDs based on AZs
declare -A AZ_RT
for i in $( seq 0 $[ $${#AZS[@]} - 1 ]); do
  k="$${REGION%-*}-$${AZS[$${i}]}"
  AZ_RT[$${k}]=$${RTS[$${i}]}
done

aws ec2 replace-route \
  --region $${REGION} \
  --destination-cidr-block 0.0.0.0/0 \
  --network-interface-id $${ENI_ID} \
  --route-table-id $${AZ_RT[$${AZ}]} \
  ||\
aws ec2 create-route \
  --region $${REGION} \
  --destination-cidr-block 0.0.0.0/0 \
  --network-interface-id $${ENI_ID} \
  --route-table-id $${AZ_RT[$${AZ}]}


# Forwarding
sysctl -w net.ipv4.ip_forward=1

for subnet_cidr in $${EGRESS_SUBNETS[@]}; do
  iptables        -A FORWARD     --src $${subnet_cidr} -j ACCEPT
  iptables -t nat -A POSTROUTING --src $${subnet_cidr} ! --dst $${VPC_CIDR} -j MASQUERADE
done

