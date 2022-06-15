#!/bin/bash

# this script should be executed either on the local or any node
# prerequisite user should have AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY and docker login credentials
# usage: sh save_docker_tar.sh  registry_port DOCKER_SERVICES_FILE_PATH ARTIFACT_BUCKET_PATH ORGANIZATION
# default value of ORGANIZATION is deepcortex in case of, did not pass any value
# DOCKER_SERVICES_FILE should be in format service_name:tag . Mockup file is present in scripts/tools/dc_service_version.txt
# e.g. bash /opt/tools/save_docker_tar.sh "/home/ec2-user/dc_images.txt" "falcon-scorpius-assets-mda-prep-auto-gov/packages/docker/"


DOCKER_SERVICES_FILE_PATH=$1
ARTIFACT_BUCKET_PATH=$2
ORGANIZATION=$3

while IFS= read -r image
do
  organization="${ORGANIZATION:-deepcortex}"
  dc_service=$organization/$image

  echo "---------------Step:1 Docker image $dc_service pull--------------------------------------------------"
  docker pull $dc_service

  docker_tar_name=$(echo $dc_service | awk -F'/' '{ print $2 }' | awk -F':' '{ print $1 }')
  echo "--------------Step:2 SAVE Docker tar $docker_tar_name.tar ------------------------------------------- "
  docker save $dc_service > $docker_tar_name.tar

  echo "--------------Step:3 Push Docker tar $docker_tar_name.tar to $ARTIFACT_BUCKET_PATH location-----------"
  aws s3 cp $docker_tar_name.tar s3://$ARTIFACT_BUCKET_PATH

  echo "-------------STEP:4 Removing $dc_service and $docker_tar_name.tar from local--------------------------"
  docker rmi $dc_service
  rm $docker_tar_name.tar

done < "$DOCKER_SERVICES_FILE_PATH"
echo "-----------------All the services docker tars pushed to S3 successfully---------------------------------"
