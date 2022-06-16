#!/bin/bash

source $(dirname $0)/common-scripts.sh

travis_ssh_path="s3://${CONFIG_S3_BUCKET}/configurations/ssh/travis-ci-key"
aws s3 cp $travis_ssh_path $(dirname $0)/travis-ci-key
chmod 600 $(dirname $0)/travis-ci-key

function deployRestApi() {
    apps=$@

    if [[ $apps == *"${APP_NAME}"* ]]; then
        dcos marathon app update ${APP_NAME} < "$(dirname $0)/marathon.json"
    else
        dcos marathon app add "$(dirname $0)/marathon.json"
    fi
}

# Install DC/OS CLI

curl https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.12/dcos -o dcos
sudo mv dcos /usr/local/bin
sudo chmod +x /usr/local/bin/dcos
sudo ssh -i $(dirname $0)/travis-ci-key -o StrictHostKeyChecking=no -f -L 80:"$DCOS_MASTER":80 ec2-user@"$GATEWAY" sleep 10
dcos cluster setup http://localhost --username=${DCOS_USERNAME} --no-check

# Deploy to DC/OS

runningApps=$(dcos marathon app list)

deployRestApi $runningApps
