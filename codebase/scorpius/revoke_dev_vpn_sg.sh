#!/bin/bash

export AWS_DEFAULT_REGION=$(awk -F\" '/^aws_region/{print $2}'  "environments/$CONFIG.tfvars")

if [[ -z "$AWS_PROFILE" ]] && ([[ -z "$AWS_ACCESS_KEY_ID" ]] || [[ -z "$AWS_SECRET_ACCESS_KEY" ]]);then
  echo "AWS_PROFILE or access keys (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY) are not set"
fi

function getSgByName () {
    local sg_name
    sg_name="$1"

    SG=$(aws ec2 describe-security-groups --filter Name=tag-key,Values=Name Name=tag-value,Values=${sg_name} --query 'SecurityGroups[*].{ID:GroupId}' | grep -o 'sg-[a-z,0-9]*')

    echo "$SG"
}

ENVIRONMENT=$(awk -F\" '/^environment/{print $2}'  "environments/$CONFIG.tfvars")
OWNER=$(awk -F\" '/^tag_owner/{print $2}'  "environments/$CONFIG.tfvars")
INCLUDE_REDSHIFT=$(awk -F\" '/^include_redshift/{print $2}'  "environments/$CONFIG.tfvars")

vpn_sg="${1:-openVPN-dev}"

DC_VPN_SG="$(getSgByName $vpn_sg)"
echo "openVPN sg group: $DC_VPN_SG"

DCOS_SG="$(getSgByName dcos-stack-$OWNER-$ENVIRONMENT)"
echo "dcos-stack sg group: $DCOS_SG"

aws ec2 revoke-security-group-ingress --group-id ${DCOS_SG} --protocol all --port all --source-group ${DC_VPN_SG}
echo "Access to DC/OS machines from Dev VPN has been revoked"

BAILE_LB_SG="$(getSgByName baile-elb-in-$OWNER-$ENVIRONMENT)"
echo "baile-LB sg group: $BAILE_LB_SG"

aws ec2 revoke-security-group-ingress --group-id ${BAILE_LB_SG} --protocol all --port all --source-group ${DC_VPN_SG}
echo "Access to DeepCortex URL from Dev VPN has been revoked"

UM_LB_SG="$(getSgByName um-service-elb-in-$OWNER-$ENVIRONMENT)"
echo "um-LB sg group: $UM_LB_SG"

aws ec2 revoke-security-group-ingress --group-id ${UM_LB_SG} --protocol all --port all --source-group ${DC_VPN_SG}
echo "Access to the DeepCortex UI from Dev VPN has been revoked."

DCOS_LB_SG="$(getSgByName master-elb-in-$OWNER-$ENVIRONMENT)"
echo "DCOS-LB sg group: $DCOS_LB_SG"

aws ec2 revoke-security-group-ingress --group-id ${DCOS_LB_SG} --protocol all --port all --source-group ${DC_VPN_SG}
echo "Access to the DC/OS UI from Dev VPN has been revoked."

if [[ $INCLUDE_REDSHIFT = "true" ]];then
    REDSHIFT_SG="$(getSgByName redshift-$OWNER-$ENVIRONMENT)"
    echo "redshift sg group: $REDSHIFT_SG"

    aws ec2 revoke-security-group-ingress --group-id ${REDSHIFT_SG} --protocol tcp --port 5439 --source-group ${DC_VPN_SG}
    echo "Access to redshift from Dev VPN has been revoked."
fi