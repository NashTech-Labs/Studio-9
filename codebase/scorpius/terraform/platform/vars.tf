# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "environment" {}
variable "tf_bucket" {}
variable "account" {}
variable "access_cidr" {}
variable "deploy_cidr" { default = "" }
variable "deploy_sg" { default = "" }
variable "baile_access" {}
variable "s3_endpoint" {}
variable "artifacts_s3_bucket" {}
variable "download_ssh_keys" { default = "false" }
variable "ssh_keys_s3_bucket" { default = "" }
variable "main_user" {}
variable "salsa_version" {}
variable "baile_api_path" {}
variable "sql_server_api_path" {}
variable "baile_private_api_path" {}
variable "dcos_username" {}
variable "dcos_password" {}
variable "apps_access_key" { default = "" }
variable "apps_secret_key" { default = "" }
variable "private_deployment" { default = "false" }
variable "email_on" { default = "false" }
variable "smtp_aws_access_key_id" { default = "" }
variable "smtp_aws_secret_access_key" { default = "" }
variable "install_amazon_ssm_agent" { default = "true" }
variable "use_app_iam_user" { default = "false" }
variable "aws_ca_bundle" {}
variable "terraform_log_file" {}
variable "deployment_logs_s3_path" {}
variable "vulkan_file_path" { default = ""}
variable "customer_key" { default = ""}

variable "docker_tar_list" { default = "" }

# Bootstrap vars

variable "bootstrap_asg_desired_capacity" {}
variable "bootstrap_asg_min_size" {}
variable "bootstrap_asg_max_size" {}
variable "s3_prefix" {}
variable "cluster_name" {}
variable "dcos_stack_bucket" {}
variable "dcos_apps_bucket" {}
variable "docker_registry_url" { default = "https://index.docker.io/v1/" }
variable "docker_registry_auth_token" { default = "" }
variable "docker_email_login" { default = "" }
variable "license_keys_s3_bucket" { default = "" }

# Master vars

variable "master_asg_desired_capacity" {}
variable "master_asg_min_size" {}
variable "master_asg_max_size" {}

# Slave vars

variable "slave_asg_desired_capacity" {}
variable "slave_asg_min_size" {}
variable "slave_asg_max_size" {}

# GPU slave vars

variable "gpu_slave_asg_desired_capacity" {}
variable "gpu_slave_asg_min_size" {}
variable "gpu_slave_asg_max_size" {}

# GPU Jupyter slave vars

variable "gpu_jupyter_asg_desired_capacity" {}
variable "gpu_jupyter_asg_min_size" {}
variable "gpu_jupyter_asg_max_size" {}

# Slave Jupyter vars

variable "slave_jupyter_asg_desired_capacity" {}
variable "slave_jupyter_asg_min_size" {}
variable "slave_jupyter_asg_max_size" {}


# Public slave vars

variable "public_slave_asg_desired_capacity" {}
variable "public_slave_asg_min_size" {}
variable "public_slave_asg_max_size" {}

# Captain vars

variable "captain_asg_desired_capacity" {}
variable "captain_asg_min_size" {}
variable "captain_asg_max_size" {}

variable "tag_owner" {}
variable "tag_usage" {}

variable "argo_docker_image_version" {}
variable "aries_docker_image_version" {}
variable "baile_docker_image_version" {}
variable "baile_haproxy_docker_image_version" {}
variable "cortex_docker_image_version" {}
variable "cortex_task_docker_image_version" {}
variable "logstash_docker_image_version" {}
variable "orion_docker_image_version" {}
variable "job_master_docker_image_version" {}
variable "pegasus_docker_image_version" {}
variable "rmq_docker_image_version" {}
variable "scorpius_mongobackup_version" {}
variable "taurus_docker_image_version" {}
variable "um_docker_image_version" {}
variable "gemini_docker_image_version" {}
variable "sql_server_docker_image_version" {}
variable "jupyter_lab_docker_image_version" {}
variable "upload_datasets" { default = "false"}
variable "download_from_s3" { default = "true" }
variable "online_prediction" { default = "true" }
variable "only_public" { default = "false" }
variable "mongo_restore_date" { default = "" }
variable "percona_mongo_version" { default = "0.4.0-3.6.6" }
variable "elastic_version" { default = "2.3.0-5.6.5" }
variable "kibana_version" { default = "2.3.0-5.6.5" }
variable "marathon_lb_version" { default = "1.12.0" }
variable "include_redshift" { default = "true" }
variable "arn" { default = "aws"}

locals {
    deploy_cidr_blank = "${var.deploy_cidr == "" ? 0 : 1}"
    create_deploy_sgs = "${var.access_cidr == var.deploy_cidr ? 0 : 1}"
    create_internal_deploy_sgs = "${var.deploy_sg == "" ? 0 : 1}"
    create_public_baile_access = "${var.baile_access == "public" ? 1 : 0}"
    create_public_jupyter_access = "${var.baile_access == "public" ? 1 : 0}"
    baile_haproxy_dns_resolver = "${var.private_deployment == "false" ? "google" : "awsvpc"}"
    key_selector = "${var.use_app_iam_user == "true" ? 0 : 1}"
    include_redshift = "${var.include_redshift == "true" ? 1 : 0}"
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "platform"
        usage       = "${var.tag_usage}"
    }
    tags_asg = [
        {
            key   = "owner"
            value = "${var.tag_owner}"
            propagate_at_launch = "true"
        },
        {
            key   = "environment"
            value = "${var.environment}"
            propagate_at_launch = "true"
        },
        {
            key   = "layer"
            value = "platform"
            propagate_at_launch = "true"
        },
        {
            key   = "usage"
            value = "${var.tag_usage}"
            propagate_at_launch = "true"
        },
    ]
}
