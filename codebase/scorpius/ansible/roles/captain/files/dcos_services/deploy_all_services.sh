#!/bin/bash

exec &> >(tee /dev/stderr | logger -s -t[CAPTAIN][deploy_all_services.sh] 2>> /var/log/deploy_all_services.log)

cd /opt/dcos_services/

if [[ $AUTO_YES != "true" ]]; then
  while true; do
    read -n 1 -p "Would you like to run deployment step-wise? [y/n or q to exit] " yn
    echo ""
    case $yn in
        [Yy]* ) echo "Running step-wise"; AUTO_YES=false && break;;
        [Nn]* ) echo "Running in auto mode"; AUTO_YES=true && break;;
        [Qq]* ) exit 0;;
        * ) echo "Please provide a yes or no answer."
    esac
  done
fi

if [[ $AUTO_YES != "true" ]]; then
  while true; do
    read -n 1 -p "Please enter the step number you would like to start from: " user_int
    echo ""
    STEP_NUMBER=${user_int:-1} && break
  done
fi

STEP=${STEP_NUMBER:-1}

if [[ $STEP -le 1 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step 1: Load front-end files? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 1"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    # Upload front end files to S3
    aws s3 ls "s3://${AWS_S3_BUCKET}/static-content/dev/"
    if [[ $? -ne 0 ]]; then
      if [ $DOWNLOAD_FROM_S3 = "true" ]; then
        echo "Downloading front-end files from S3 Bucket"
        curl -O "https://s3.amazonaws.com/artifacts.dev.knoldususa.ai/deployment_downloads/${SALSA_VERSION}/front-end.tar.gz"
      fi
      tar -xvf front-end.tar.gz
      echo "Extracting front-end files"
      aws s3 sync front-end "s3://${AWS_S3_BUCKET}/static-content/dev/"
      echo "Synchronizing front-end files with $AWS_S3_BUCKET"
    fi

    # Upload Datasets to S3
    aws s3 ls "s3://${AWS_S3_BUCKET}/Datasets/"
    if [[ $? -ne 0 ]]; then
      if [ $UPLOAD_DATASETS = "true" ]; then
        if [ $DOWNLOAD_FROM_S3 = "true" ]; then
          curl -O "https://s3.amazonaws.com/artifacts.dev.knoldususa.ai/deployment_downloads/Datasets.tar.gz"
          echo "Downloading datasets files from S3 Bucket"
        fi
        tar -xvf Datasets.tar.gz
        echo "Extracting Dataset files"
        aws s3 sync Datasets "s3://${AWS_S3_BUCKET}/Datasets"
        rm -rf Datasets
        rm -f Datasets.tar.gz
        echo "Removing Datasets files"
      fi
    fi

  done
fi

# Wait for master node to become online
echo "Waiting for the DC/OS Master to be connected"
until $(curl --output /dev/null --silent --head --fail http://$DCOS_MASTER); do sleep 60; done
echo "DC/OS Master node successfully connected"

if [[ $STEP -le 2 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step 2: Configure DC/OS cli? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 2"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    # Configure the DC/OS cli
    until dcos node; do
      bash setup_dcos_cli.sh
      echo "DC/OS CLI setup and configuration is in progess"
      sleep 10
    done
    echo "DC/OS CLI successfully set up"

    # Wait for all nodes to become online
    echo "Waiting for all $DCOS_NODES DC/OS nodes to connect to the cluster"
    until [[ $(dcos node | grep agent | wc -l) == $DCOS_NODES ]]; do sleep 30; done

  done
fi

if [[ "$PRIVATE_DEPLOYMENT" = "true" ]]; then
  if [[ $STEP -le 3 ]];then
    for _ in once; do
      if [[ $AUTO_YES != "true" ]]; then
        while true; do
          read -n 1 -p "Run step 3: Start local DC/OS universe? [y/n or q to exit] " yn
          echo ""
          case $yn in
              [Yy]* ) echo "Running step 3"; break;;
              [Nn]* ) break 2;;
              [Qq]* ) exit 0;;
              * ) echo "Please provide a yes or no answer."
          esac
        done
      fi

      # set local universe repo
      echo "Starting local DC/OS universe"
      dcos package repo remove Universe || true
      dcos package repo add Universe http://master.mesos:8082/repo
      echo "Local universe repo setup for private deployment"
    done
  fi
fi

if [[ $STEP -le 4 ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step 4: Configure DC/OS enterprise cli for (dcos security, dcos backup, dcos license)? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step 4"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    #echo "Adding Bootstrap Registry"
    #dcos package repo add "Bootstrap Registry" https://registry.component.thisdcos.directory/repo
    #dcos package install dcos-enterprise-cli --yes
    #echo "enterprise cli is installed"

  done
fi

# Retrieve slave node IPs
echo "$(aws ec2 describe-instances --filters "Name=tag:Role,Values=slave" "Name=tag:environment,Values=$ENVIRONMENT" --query "Reservations[].Instances[].PrivateIpAddress" | jq -r '.[]')" > mongo_hosts.txt
export DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

echo "$DCOS_MASTER_PRIVATE_IP master.mesos" >> /etc/hosts

if [[ "$PRIVATE_DEPLOYMENT" = "true" ]]; then
  if [[ $STEP -le 5 ]];then
    for _ in once; do
      if [[ $AUTO_YES != "true" ]]; then
        while true; do
          read -n 1 -p "Run step 5: Start private docker registry and load images? [y/n or q to exit] " yn
          echo ""
          case $yn in
              [Yy]* ) echo "Running step 5"; break;;
              [Nn]* ) break 2;;
              [Qq]* ) exit 0;;
              * ) echo "Please provide a yes or no answer."
          esac
        done
      fi

      # Deploy docker registry
      echo "Deploying local docker registry"
      bash deploy_service.sh docker-registry/marathon.json docker-registry/env_vars.sh

      # Populate registry
      echo "Populating docker registry"
      sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no deployer@$DCOS_MASTER_PRIVATE_IP "sudo /bin/bash -l /opt/populate-local-registry.sh $ARTIFACTS_S3_BUCKET $DOCKER_TAR_LIST"

    done
  fi
fi

export MONGODB_KEY=$(openssl rand -base64 756 | tr '\n' ' ')

[[ "$PRIVATE_DEPLOYMENT" = "true" ]] && CURRENT_STEP=6 || CURRENT_STEP=4

if [[ $STEP -le $CURRENT_STEP ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step $CURRENT_STEP: Setup Secret Store and Start base services? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step $CURRENT_STEP"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    #Secret store implementation for all the services
    echo "Setting Up Secret Store"
    bash setup_secret_store.sh
    # Deploy frameworks from DC/OS universe + rabbitMQ
    dcos package install marathon-lb --package-version=$MARATHON_LB_VERSION --yes
    bash deploy_catalog_service.sh percona-server-mongodb mongodb/options.json mongodb/env_vars.sh $PERCONA_MONGO_VERSION
    echo "percona mongodb: $PERCONA_MONGO_VERSION deployed from universe"
    dcos package install elastic --options=elasticsearch/options.json --package-version=$ELASTIC_VERSION --yes
    echo "elastic search: $ELASTIC_VERSION deployed from universe"
    dcos package install kibana --options=kibana/options.json --package-version=$KIBANA_VERSION --yes
    echo "kibana: $KIBANA_VERSION deployed from universe"
    bash deploy_service.sh rabbitmq/marathon.json rabbitmq/env_vars.sh
    echo "custom rabbitMQ image deployed"
    dcos package install --cli elastic --yes
    echo "cli for elastic search installed"
    dcos package install --cli percona-server-mongodb --package-version=$PERCONA_MONGO_VERSION --yes
    echo "cli for percona-server-mongodb installed"

    echo "Waiting for services to finish deploying"
    while $(dcos marathon deployment list | grep -q scale); do sleep 30; done
    echo "Waiting for services and databases to come online"
    sleep 180

  done
fi


[[ "$PRIVATE_DEPLOYMENT" = "true" ]] && CURRENT_STEP=7 || CURRENT_STEP=5

if [[ $STEP -le $CURRENT_STEP ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]]; then
      while true; do
        read -n 1 -p "Run step $CURRENT_STEP: Initialize base services? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step $CURRENT_STEP"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    # Initialization and migration

    echo "Initializing ElasticSearch"
    bash elasticsearch/scripts/elasticsearch_init.sh
    echo "ElasticSearch initialized successfully"

    echo "Initializing rabbitMQ"
    bash rabbitmq/rabbitmq_init.sh
    echo "RabbitMQ initialized successfully"

    echo "Waiting for all mongo nodes to connect"
    until [[ $(dcos percona-server-mongodb pod status | grep "RUNNING" | wc -l) == 4 ]]; do sleep 30; done
    sleep 30

    dcos percona-server-mongodb user reload-system

    sleep 20

    echo "Adding admin user to mongo"
    envsubst < mongodb/admin_user.json > admin_user.json
    dcos percona-server-mongodb user add admin admin_user.json
    echo "Admin user added successfully"

    sleep 20

    echo "Adding app user to mongo"
    envsubst < mongodb/app_user.json > app_user.json
    dcos percona-server-mongodb user add admin app_user.json
    echo "App user added successfully"

    export PATH="/usr/local/lib/npm/bin:$PATH"

    sleep 30

    if [[ ! -z "${MONGO_RESTORE_DATE}" ]]; then
      echo "Restoring mongo from backup"
      bash ../tools/mongo_restore.sh baile "${MONGO_RESTORE_DATE}"
      bash ../tools/mongo_restore.sh um-service "${MONGO_RESTORE_DATE}"
      echo "MongoDB successfully restored"
    fi

    echo "Initializing mongo migration for baile and um-service"
    bash mongodb/mongo_init.sh
    bash deploy_job.sh mongodb/baile/migrations/mongo_migration-marathon.json mongodb/baile/migrations/env-migration_vars.sh
    dcos job run baile-mongo-migration
    echo "MongoDB migration runs successfully"

    # Deploy mongodb backup job
    echo "Configuring backup job for mongo"
    bash deploy_job.sh mongodb/marathon.json mongodb/env_vars.sh
    echo "Mongo backups started successfully"

  done
fi

[[ "$PRIVATE_DEPLOYMENT" = "true" ]] && CURRENT_STEP=8 || CURRENT_STEP=6

if [[ $STEP -le $CURRENT_STEP ]];then
  for _ in once; do
    if [[ $AUTO_YES != "true" ]];then
      while true; do
        read -n 1 -p "Run step $CURRENT_STEP: Start custom DeepCortex applications? [y/n or q to exit] " yn
        echo ""
        case $yn in
            [Yy]* ) echo "Running step $CURRENT_STEP"; break;;
            [Nn]* ) break 2;;
            [Qq]* ) exit 0;;
            * ) echo "Please provide a yes or no answer."
        esac
      done
    fi

    # Deploy custom services and frameworks
    echo "deploying aries service..."
    bash deploy_service.sh aries-api-rest/marathon.json aries-api-rest/env_vars.sh
    echo "aries service deployed"
    echo "deploying baile service..."
    bash deploy_service.sh baile/marathon.json baile/env_vars.sh
    echo "baile service deployed"
    echo "deploying baile-haproxy..."
    bash deploy_service.sh baile-haproxy/marathon.json baile-haproxy/env_vars.sh
    echo "baile-haproxy service deployed"
    echo "deploying cortex service..."
    bash deploy_service.sh cortex-api-rest/marathon.json cortex-api-rest/env_vars.sh
    echo "cortex service deployed"
    echo "deploying logstash server..."
    bash deploy_service.sh logstash/marathon.json logstash/env_vars.sh
    echo "logstash service deployed"
    echo "deploying orion service..."
    bash deploy_service.sh orion-api-rest/marathon.json orion-api-rest/env_vars.sh
    echo "orion service deployed"
    echo "deploying um service..."
    bash deploy_service.sh um-service/marathon.json um-service/env_vars.sh
    echo "um service deployed"
    echo "deploying gemini service..."
    bash deploy_service.sh gemini/marathon.json gemini/env_vars.sh
    echo "gemini service deployed"
    bash deploy_service.sh sql-server/marathon.json sql-server/env_vars.sh
    echo "sql-server service deployed"

  done
fi

[[ "$PRIVATE_DEPLOYMENT" = "true" ]] && CURRENT_STEP=9 || CURRENT_STEP=7

# Deploy online prediction
if [ $ONLINE_PREDICTION = "true" ]; then
  if [[ $STEP -le $CURRENT_STEP ]];then
    for _ in once; do
      if [[ $AUTO_YES != "true" ]]; then
        while true; do
          read -n 1 -p "Run step $CURRENT_STEP: Start online prediction services? [y/n or q to exit] " yn
          echo ""
          case $yn in
              [Yy]* ) echo "Running step $CURRENT_STEP"; break;;
              [Nn]* ) break 2;;
              [Qq]* ) exit 0;;
              * ) echo "Please provide a yes or no answer."
          esac
        done
      fi

      # Stop postgresql servers if running
      service postgresql stop

      # Initialize redshift
      echo "Initializing redshift"
      bash postgres/postgres_init.sh
      echo "Redshift initialized successfully"

      # Deploy custom services

      echo "deploying argo service..."
      bash deploy_service.sh argo-api-rest/marathon.json argo-api-rest/env_vars.sh
      echo "argo service deployed"

      echo "deploying pegasus service..."
      bash deploy_service.sh pegasus-api-rest/marathon.json pegasus-api-rest/env_vars.sh
      echo "pegasus service deployed"

      echo "deploying taurus service..."
      bash deploy_service.sh taurus/marathon.json taurus/env_vars.sh
      echo "taurus service deployed"

    done
  fi
fi

if [[ "$PRIVATE_DEPLOYMENT" = "true" ]]; then
  sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no deployer@$DCOS_MASTER_PRIVATE_IP "sudo systemctl stop dcos-local-universe-registry"
  sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no deployer@$DCOS_MASTER_PRIVATE_IP "sudo systemctl stop dcos-local-universe-http"
fi

# remove cronjobs for captain node
log_remover="/tmp/scorpius.log"
echo "removing cronjobs...."
sudo crontab -l | grep -v "$log_remover" | sudo crontab -

# remove cronjob for master node if not private deployment
if [[ "$PRIVATE_DEPLOYMENT" = "false" ]]; then
  sudo ssh -i /opt/private_key -o StrictHostKeyChecking=no deployer@$DCOS_MASTER_PRIVATE_IP /bin/bash << EOF
log_remover="/tmp/scorpius.log";
echo "removing cronjobs....";
sudo crontab -l | grep -v "$log_remover" | sudo crontab -
EOF
fi
