#!usr/bin/env bash

#script executed on the master node
#usage: sh populate-local-registry.sh artifact_s3_bucket docker_tar_list registry_port

set -e

exec &> >(tee /dev/stderr | logger -s -t[MASTER][populate-local-registry.sh] 2>> /var/log/populate-local-registry.log)

ARTIFACTS_S3_BUCKET=$1
DOCKER_TAR_LIST=$2
REGISTRY_PORT=$3

s3_URL=${ARTIFACTS_S3_BUCKET}/packages/docker-tars

if [ -z "$3" ]
then
	REGISTRY_PORT=80
fi

echo "Waiting for docker registry to finish deploying"
until $(curl --output /dev/null --silent --head --fail http://docker-registry.marathon.l4lb.thisdcos.directory:80/); do sleep 30; done

docker_tar_name_arr=($(aws s3 ls $s3_URL/ | awk {'print $4'} | tr '\n' ' '))
IFS=',' read -a docker_list <<< ${DOCKER_TAR_LIST}

for docker_tar_file in "${docker_list[@]}";
do
	skip=;
	for docker_tar_name in "${docker_tar_name_arr[@]}";
	do
		[[ $docker_tar_file == $docker_tar_name ]] && { skip=1;
		break; };
	done;
	[[ -n $skip ]] || docker_tar_not_available_s3+=("$docker_tar_file");
done

size_docker_tar_not_available_s3=${#docker_tar_not_available_s3[@]};

if [ "${size_docker_tar_not_available_s3}" == 0 ]
then
	for docker_tar_name in "${docker_list[@]}"
	do
		echo "Fetching and deploying $docker_tar_name"
		aws s3 cp s3://$s3_URL/$docker_tar_name .
		image=$(docker load --input $docker_tar_name | grep "Loaded image:" | awk {'print $3'})
		echo "Docker image $image loaded"
		docker tag $image docker-registry.marathon.l4lb.thisdcos.directory:$REGISTRY_PORT/$image
		docker push docker-registry.marathon.l4lb.thisdcos.directory:$REGISTRY_PORT/$image
		echo "Docker image $image pushed to docker registry"
		docker rmi $image docker-registry.marathon.l4lb.thisdcos.directory:$REGISTRY_PORT/$image
		rm $docker_tar_name
	done
	echo "Local docker registry is now populated"
else
	echo "these docker tar name ${docker_tar_not_available_s3[@]} are not available on S3"
	echo "terminating the deployment"
	exit 1
fi

echo "removing cronjobs...."
log_remover="/tmp/scorpius.log"
sudo crontab -l | grep -v "$log_remover" | sudo crontab -