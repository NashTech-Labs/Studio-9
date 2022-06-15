[![Build Status](https://travis-ci.com/deepcortex/scorpius.svg?token=pvwDNvw6P8fj9zJxpA1p&branch=master)](https://travis-ci.com/deepcortex/scorpius)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/159b57f655704fa58920eb425104697a)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/scorpius&amp;utm_campaign=Badge_Grade)

#  scorpius
Automated deployment for DeepCortex platform

Prior to deployment you must make sure you have already accepted the Terms and Services for the base image you plan to use if it's from the AWS marketplace and reguires such an agreement. You can do this by spinning up a temporary machine using this AMI from within the AWS console. This process will ask you to accept the Terms and Services. (note: this can currently only be done through the console and not the cli or other tool)

### Configuration

Before running any deployment scripts be sure to do the following:
1. Copy the environments/template.tfvars file and create your own.
2. Fill in all available configurations.
3. Export the following environment variables:
    * CONFIG - the name of the config file to use (under the environments dir e.g. deepcortex-settings).
    * AWS_ACCESS_KEY_ID - the access key that should be used to deploy in AWS.
    * AWS_SECRET_ACCESS_KEY - the secret key that should be used to deploy in AWS.
    * DCOS_USERNAME - the username you'd like to use to login to the DC/OS cluster
    * DCOS_PASSWORD - the password you'd like to use to login to the DC/OS cluster (avoid special characters used in bash such as "#", ";", "$", etc.)
    * DOCKER_REGISTRY_AUTH_TOKEN - the token for docker authentication
    * DOCKER_EMAIL_LOGIN - the login email for docker
4. If you are importing IAM resources and not creating them through terraform you will also need to import access keys from the app user:
    * APPS_AWS_ACCESS_KEY_ID - the access key for the app user.
    * APPS_AWS_SECRET_ACCESS_KEY - the secret key for the app user.

### Deployment Scripts

1. build.sh - used to build DeepCortex
    * -b: shutdown boostrap - can be set to true destroy bootstrap node after the cluster deploys
    * -g: gpu on start - can be set to false to exclude spinning up a gpu node after the cluster deploys
    * -m: deploy mode - can be set to simple to exclude download of DC/OS cli and extra output
    * -s: stacks - a list of comma separated values to overwrite which terraform stacks to build
    * -p: packer - can be set to false to exclude packer builds
    * -t: s3 type - can be set to existing to import existing S3 buckets
2. destroy.sh - used to destroy DeepCortex
    * -s: stacks - a list of comma separated values to overwrite which terraform stacks to build
    * -d: can be set to true to delete s3 buckets
3. suspend_dc.sh - used to suspend the DC/OS cluster
    * -s: stacks - a list of comma separated values to overwrite which asgs to shutdown
4. resume_dc.sh - used to resume the DC/OS cluster
    * -b: shutdown boostrap - can be set to true destroy bootstrap node after the cluster deploys
    * -g: gpu on start - can be set to false to exclude gpu from restart
    * -s: stacks - a list of comma separated values to overwrite which asgs to resume

### Packer Scripts

1. Build only certain packer image for dc/os cluster: ./packer.sh $AMI_NAME $CONFIG e.g ./packer.sh

### Docker deployment

1. Create a folder called environments on your computer.
2. Place the template.tfvars file under this "environmnets" directory.
3. Fill in the necessary variables in template.tfvars.
4. Export the following variables in your terminal.
    * CONFIG - the name of the config file to use (should be template).
    * AWS_ACCESS_KEY_ID - the access key that should be used to deploy in AWS.
    * AWS_SECRET_ACCESS_KEY - the secret key that should be used to deploy in AWS.
    * DCOS_USERNAME - the username you'd like to use to login to the DC/OS cluster
    * DCOS_PASSWORD - the password you'd like to use to login to the DC/OS cluster (avoid special characters used in bash such as "#", ";", "$", etc.)
    * DOCKER_REGISTRY_AUTH_TOKEN - the token for docker authentication
    * DOCKER_EMAIL_LOGIN - the login email for docker
5. If you are importing IAM resources and not creating them through terraform you will also need to import access keys from the app user:
    * APPS_AWS_ACCESS_KEY_ID - the access key for the app user.
    * APPS_AWS_SECRET_ACCESS_KEY - the secret key for the app user.
6. Run "docker pull deepcortex/scorpius-deploymnet:TAG" repalcing tag with the correct image tag.
7. Run the following docker command replacing /path/to/environments with the path to the environments directory you created in step 1 and TAG with the correct version of the docker image.

    ```bash
   docker run \
     -v /path/to/environments:/opt/deploy/environments \
     -v /path/to/.ssh:/root/.ssh \
     -e CONFIG=${CONFIG} \
     -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
     -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
     -e DCOS_USERNAME=${DCOS_USERNAME} \
     -e DCOS_PASSWORD=${DCOS_PASSWORD} \
     -e DOCKER_EMAIL_LOGIN=${DOCKER_EMAIL_LOGIN} \
     -e DOCKER_REGISTRY_AUTH_TOKEN=${DOCKER_REGISTRY_AUTH_TOKEN} \
     deepcortex/scorpius-deployment:TAG
    ```

    If you are importing IAM resources rather than creating them you must supply the additional app access keys as well.

    ```bash
    docker run \
      -v /path/to/environments:/opt/deploy/environments \
      -v /path/to/.ssh:/root/.ssh \
      -e CONFIG=${CONFIG} \
      -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
      -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
      -e DCOS_USERNAME=${DCOS_USERNAME} \
      -e DCOS_PASSWORD=${DCOS_PASSWORD} \
      -e DOCKER_EMAIL_LOGIN=${DOCKER_EMAIL_LOGIN} \
      -e DOCKER_REGISTRY_AUTH_TOKEN=${DOCKER_REGISTRY_AUTH_TOKEN} \
      -e APPS_AWS_ACCESS_KEY_ID=${APPS_AWS_ACCESS_KEY_ID} \
      -e APPS_AWS_SECRET_ACCESS_KEY=${APPS_AWS_SECRET_ACCESS_KEY} \
      deepcortex/scorpius-deployment:TAG
    ```
8. To add endpoints.json for C2S:
    * Create a directory on your local machine called "extra_files" and place the endpoints.json file in that directory.
    * Add "-v /path/to/extra_files:/opt/deploy/ansible/roles/captain/files/extra_files" to the docker command.
9. Once your terminal output states the deployment it complete you can access the DeepCortex UI.

### Manual deploying the scripts without docker.

#### Dependencies:

  * Terraform >=0.10.7 https://releases.hashicorp.com/terraform/0.10.7/
  * Packer =1.0.4 https://releases.hashicorp.com/packer/?_ga=2.152781114.2069873712.1510704531-228023890.1505205072
  * AWS Cli https://aws.amazon.com/cli/

  * Create your ~/.aws/config and ~/.aws/credentials

  * Make sure you exported AWS_PROFILE

#### BUILDING NEW ENVIRONMENT

##### Build all<config> <aws_access_key_id> <aws_secret_access_key> <customer_key> <dcos_username> <dcos_password>

Everything can be built at once, including all terraform and packer scripts by running build.sh.

You must export the following varible to run build.sh
* CONFIG
* AWS_PROFILE or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
* DCOS_USERNAME
* DCOS_PASSWORD
* DOCKER_REGISTRY_AUTH_TOKEN
* DOCKER_EMAIL_LOGIN

##### Packer:

Building the environment requires first building the AMIs that terraform will use. We do it with Packer.

In the environments/CONFIG.tfvars file for your environment/region/cloud you will need to update the following variables:

 - packer_base_ami
 - main_user
 - aws_region

Running packer:

Run the following command to build all DC/OS Cluster AMIs in parallel:
```
./packer.sh all CONFIG
```

Or, to build a specific one:
```
./packer.sh captain CONFIG
./packer.sh bootstrap CONFIG
./packer.sh master CONFIG
./packer.sh slave CONFIG
./packer.sh public-slave CONFIG
./packer.sh gpu-slave CONFIG
./packer.sh gpu-jupyter CONFIG
./packer.sh slave-jupyter CONFIG

```

once we have the AMIs ready, we can continue with Terraform.

##### Terraform:

First of all we need a bucket where Terraform will store all the state files for each stack.

On environments/CONFIG.tfvars file for your environment/region/cloud you will need to update the following variables:

 - bucket

Creating the bucket and updating the terraform code will be done by running the terraform_init_backend.sh script.

IMPORTANT: Update environments/CONFIG.tfvars file for your environment/region/cloud using environments/integration.tfvars as example.

SSH key MUST be provided via configuration file, we cannot retrieve AWS generated keys.

Running terraform:

We've built a terraform.sh script (wrapper) to handle configuration files and all the terraform code. Run the following commands in the presented order to build the environment.

Building IAM:

./terraform.sh init CONFIG iam (initializes the state file)
./terraform.sh plan CONFIG iam (check the code will run and generates output file)
./terraform.sh apply CONFIG iam (applies output file generated in the previous command)

Building VPC:

./terraform.sh init CONFIG vpc (initializes the state file)
./terraform.sh plan CONFIG vpc (check the code will run and generates output file)
./terraform.sh apply CONFIG vpc (applies output file generated in the previous command)

Building Redshift:
./terraform.sh init CONFIG redshift (initializes the state file)
./terraform.sh plan CONFIG redshift (check the code will run and generates output file)
./terraform.sh apply CONFIG redshift (applies output file generated in the previous command)

Building Platform (DC/OS):

./terraform.sh init CONFIG platform (initializes the state file)
./terraform.sh plan CONFIG platform (check the code will run and generates output file)
./terraform.sh apply CONFIG platform (applies output file generated in the previous command)

Apply will return master_elb_url which currently is public for testing purposes, you will be able to access to the DC/OS dashboard vía https://master_elb_url

Destroying everything(run backwards):

./terraform.sh plan-destroy CONFIG STACK (check the code will run and generates output file)
./terraform.sh apply CONFIG STACK (applies output file generated in the previous command)
