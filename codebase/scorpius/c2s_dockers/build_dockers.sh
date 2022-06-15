#!/bin/bash

parse_args()
{
  while getopts ":d:i:s:t:" opt "$@"; do
    case "$opt" in
      d) DESTINATION_S3_PATH="$OPTARG" ;;
      i) set -f
         IFS=,
         IMAGES=($OPTARG) ;;
      s) SOURCE_S3_PATH="$OPTARG" ;;
      t) IMAGE_TYPE="$OPTARG" ;;
      :) error "option -$OPTARG requires an argument." ;;
      \?) error "unknown option: -$OPTARG" ;;
    esac
  done
}

parse_args "$@"

DOCKER_VERSION=${DOCKER_VERSION:-"old"}
AWS_CA_BUNDLE=${AWS_CA_BUNDLE:-"/etc/pki/tls/certs/ca-bundle.crt"}

for i in "${IMAGES[@]}"; do
    echo "Downloading image s3://$SOURCE_S3_PATH/$i.tar"
    aws s3 cp "s3://$SOURCE_S3_PATH/$i.tar" .
    echo "Loading docker image"
    export IMAGE=$(docker load --input $i.tar | grep "Loaded image:" | awk {'print $3'})
    echo "Loaded image $IMAGE"
    rm $i.tar
    echo "Building C2S image from $IMAGE"
    cd $IMAGE_TYPE
    if [[ $DOCKER_VERSION = "old" ]];then
      envsubst '${IMAGE}' < Dockerfile_old > Dockerfile
    else
      cp Dockerfile_new Dockerfile
    fi
    docker build --build-arg IMAGE=$IMAGE --build-arg AWS_CA_BUNDLE=$AWS_CA_BUNDLE --build-arg APP_NAME=$i -t $IMAGE-test .
    echo "Saving new C2S image to $i.tar"
    docker save $IMAGE > $i.tar
    echo "Uploading $i.tar to s3://$DESTINATION_S3_PATH/$i.tar"
    aws s3 cp $i.tar "s3://$DESTINATION_S3_PATH/$i.tar"
    rm $i.tar
    rm Dockerfile
done
