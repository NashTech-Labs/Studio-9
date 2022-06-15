# vim: ts=4:sw=4:et:ft=hcl

terraform {
    required_version = ">= 0.10.7"
    backend "s3" {}
}

#########################################################
# Retrieve IAM data
data "terraform_remote_state" "iam" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/iam/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# Retrieve S3 data
data "terraform_remote_state" "s3_buckets" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/s3_buckets/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# Retrieve VPC data
data "terraform_remote_state" "vpc" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/vpc/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# Retrieve BASE data
data "terraform_remote_state" "base" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/base/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# Retrieve Redshift data
/*data "terraform_remote_state" "redshift" {
    backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/redshift/terraform.tfstate"
    region = "${var.aws_region}"
  }
  defaults {
    redshift_master_username = "deepcortex"
    redshift_master_password = "password"
    redshift_url = "localhost"
  }
}*/

###################################################################

# Security Groups

module "dcos_stack_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "dcos-stack-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_self = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

/*resource "aws_security_group_rule" "redshift_ingress_rule_sgid" {
    count = "${local.include_redshift}"

    security_group_id        = "${data.terraform_remote_state.redshift.sg_redshift_id}"
    type                     = "ingress"
    from_port                = "5439"
    to_port                  = "5439"
    protocol                 = "tcp"
    source_security_group_id = "${module.dcos_stack_sg.id}"
    description              = "Access for redshift from DC/OS"
}*/

#########################################################
# Bootstrap
module "bootstrap_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "bootstrap-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 2
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8080"
            to_port     = "8080"
            sg_id = "${module.bootstrap_elb_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

module "bootstrap_elb_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "bootstrap-elb-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "8080"
            to_port     = "8080"
            sg_id       = "${module.dcos_stack_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

data "template_file" "bootstrap_userdata" {
  template = "${file("../../terraform/templates/bootstrap_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    cluster_name = "${var.cluster_name}"
    s3_bucket = "${data.terraform_remote_state.s3_buckets.stack_s3_bucket}"
    s3_prefix = "${var.environment}-${var.s3_prefix}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    customer_key = "${var.customer_key}"
    main_user = "${var.main_user}"
    num_masters = "${var.master_asg_desired_capacity}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    masters_elb = "${module.master_elb_internal.elb_dns_name}"
    aws_region = "${var.aws_region}"
    dns_ip = "${cidrhost(data.terraform_remote_state.vpc.vpc_cidr, 2)}"
    dcos_username = "${var.dcos_username}"
    dcos_password = "${var.dcos_password}"
    docker_registry_url = "${var.docker_registry_url}"
    docker_registry_auth_token = "${var.docker_registry_auth_token}"
    docker_email_login = "${var.docker_email_login}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
    license_keys_s3_bucket = "${var.license_keys_s3_bucket}"
  }

  depends_on = [
    "module.bootstrap_elb",
    "module.master_elb_internal"
  ]
}

module "bootstrap_elb" {
  source              = "../../terraform/modules/elb"
  elb_name            = "${var.tag_owner}-${var.environment}-bootstrap-elb"
  elb_is_internal     = "true"
  elb_security_group  = "${module.bootstrap_elb_sg.id}"
  subnets             = [ "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}" ]
  frontend_port       = "8080"
  frontend_protocol   = "http"
  backend_port        = "8080"
  backend_protocol    = "http"
  health_check_target = "TCP:8080"

  tags                = "${local.tags}"
}

module "bootstrap_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "bootstrap-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-bootstrap-"
    lc_instance_type        = "t2.medium"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.bootstrap_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.bootstrap_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.bootstrap_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-bootstrap-asg"
    asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"
    asg_desired_capacity    = "${var.bootstrap_asg_desired_capacity}"
    asg_min_size            = "${var.bootstrap_asg_min_size}"
    asg_max_size            = "${var.bootstrap_asg_max_size}"
    asg_load_balancers      = [ "${module.bootstrap_elb.elb_id}" ]

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-bootstrap-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-bootstrap"
    instance_role_tag = "bootstrap"
}

#########################################################
# Master
module "master_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "master-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 13
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "443"
            to_port     = "443"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "443"
            to_port     = "443"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "5050"
            to_port     = "5050"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "5050"
            to_port     = "5050"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "2181"
            to_port     = "2181"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "2181"
            to_port     = "2181"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8080"
            to_port     = "8080"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8080"
            to_port     = "8080"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8181"
            to_port     = "8181"
            sg_id = "${module.master_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8181"
            to_port     = "8181"
            sg_id = "${module.master_elb_internal_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

module "master_elb_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "master-elb-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_cidr = [
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            cidr_blocks = "${var.access_cidr}"
        },
        {
            protocol    = "tcp"
            from_port   = "443"
            to_port     = "443"
            cidr_blocks = "${var.access_cidr}"
        },
        {
            protocol    = "tcp"
            from_port   = "8181"
            to_port     = "8181"
            cidr_blocks = "${var.access_cidr}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

resource "aws_security_group_rule" "master_elb_deploy_ingress_rule_cidr" {
    count = "${local.create_deploy_sgs * local.deploy_cidr_blank}"

    security_group_id = "${module.master_elb_sg.id}"
    type              = "ingress"
    from_port         = "80"
    to_port           = "80"
    protocol          = "tcp"
    cidr_blocks       = ["${var.deploy_cidr}"]
    description       = "Access for master elb from deploy cidr"
}

module "master_elb_internal_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "master-elb-internal"
    sg_description = "some description"

    sg_name = "master-elb-in-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 6
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "443"
            to_port     = "443"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "5050"
            to_port     = "5050"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "2181"
            to_port     = "2181"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8080"
            to_port     = "8080"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "8181"
            to_port     = "8181"
            sg_id        = "${module.dcos_stack_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

resource "aws_security_group_rule" "master_elb_in_deploy_ingress_rule_cidr" {
    count = "${local.create_internal_deploy_sgs}"

    security_group_id        = "${module.master_elb_internal_sg.id}"
    type                     = "ingress"
    from_port                = "80"
    to_port                  = "80"
    protocol                 = "tcp"
    source_security_group_id = "${var.deploy_sg}"
    description              = "Access for master elb int from deploy sg"
}

data "template_file" "master_userdata" {
  template = "${file("../../terraform/templates/master_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

module "master_elb" {
  source              = "../../terraform/modules/elb_external_masters"
  elb_name            = "${var.tag_owner}-${var.environment}-master-elb"
  elb_is_internal     = "false"
  elb_security_group  = "${module.master_elb_sg.id}"
  subnets             = [ "${data.terraform_remote_state.vpc.public_subnet_ids}" ]
  health_check_target = "TCP:5050"

  tags                = "${local.tags}"
}

module "master_elb_internal" {
  source              = "../../terraform/modules/elb_internal_masters"
  elb_name            = "${var.tag_owner}-${var.environment}-master-elb-in"
  elb_security_group  = "${module.master_elb_internal_sg.id}"
  subnets             = [ "${data.terraform_remote_state.vpc.public_subnet_ids}" ]
  health_check_target = "TCP:5050"

  tags                = "${local.tags}"
}

module "master_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "master-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-master-"
    lc_instance_type        = "m4.2xlarge"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.master_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.master_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.master_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-master-asg"
    asg_subnet_ids          = "${data.terraform_remote_state.vpc.public_subnet_ids}"
    asg_desired_capacity    = "${var.master_asg_desired_capacity}"
    asg_min_size            = "${var.master_asg_min_size}"
    asg_max_size            = "${var.master_asg_max_size}"
    asg_load_balancers      = [ "${module.master_elb.elb_id}", "${module.master_elb_internal.elb_id}" ]

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-master-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-master"
    instance_role_tag = "master"
}

#########################################################
# Slaves
module "slave_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "slave-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 3
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${module.captain_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "27017"
            to_port     = "27017"
            sg_id = "${module.captain_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

data "template_file" "slave_userdata" {
  template = "${file("../../terraform/templates/private_slave_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

module "slave_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "slave-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-slave-"
    lc_instance_type        = "m4.4xlarge"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.slave_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.slave_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.slave_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-slave-asg"
    asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"
    asg_desired_capacity    = "${var.slave_asg_desired_capacity}"
    asg_min_size            = "${var.slave_asg_min_size}"
    asg_max_size            = "${var.slave_asg_max_size}"

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-slave-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-slave"
    instance_role_tag = "slave"
}

#GPU Slave

data "template_file" "gpu_slave_userdata" {
  template = "${file("../../terraform/templates/gpu_slave_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    private_deployment = "${var.private_deployment}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
    vulkan_file_path = "${var.vulkan_file_path}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

module "gpu_slave_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "gpu-slave-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-gpu-slave-"
    lc_instance_type        = "p2.xlarge"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.slave_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.gpu_slave_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.slave_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-gpu-slave-asg"
    asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"
    asg_desired_capacity    = "${var.gpu_slave_asg_desired_capacity}"
    asg_min_size            = "${var.gpu_slave_asg_min_size}"
    asg_max_size            = "${var.gpu_slave_asg_max_size}"

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-gpu-slave-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-gpu-slave"
    instance_role_tag = "gpu-slave"
}

########################################################
# Jupyter ELB
module "jupyter_elb_sg" {
  source = "../../terraform/modules/security_group"

  vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

  sg_name = "jupyter-elb-${var.tag_owner}-${var.environment}"
  sg_description = "some description"

  ingress_rules_cidr = [
    {
      protocol    = "tcp"
      from_port   = "80"
      to_port     = "80"
      cidr_blocks = "${var.access_cidr}"
    },
  ]

  egress_rules_cidr = [
    {
      protocol    = "all"
      from_port   = "0"
      to_port     = "0"
      cidr_blocks = "0.0.0.0/0"
    },
  ]

  tags = "${local.tags}"
}

resource "aws_security_group_rule" "jupyter_elb_deploy_ingress_rule_cidr" {
  count = "${local.create_deploy_sgs * local.deploy_cidr_blank}"

  security_group_id = "${module.jupyter_elb_sg.id}"
  type              = "ingress"
  from_port         = "80"
  to_port           = "80"
  protocol          = "tcp"
  cidr_blocks       = ["${var.deploy_cidr}"]
  description       = "Access for jupyter from deploy cidr"
}

resource "aws_security_group_rule" "jupyter_elb_public_ingress_rule_cidr" {
  count = "${local.create_public_jupyter_access}"

  security_group_id = "${module.jupyter_elb_sg.id}"
  type              = "ingress"
  from_port         = "80"
  to_port           = "80"
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  description       = "Access for jupyter from public internet"
}

module "jupyter_elb" {
  source              = "../../terraform/modules/elb"
  elb_name            = "${var.tag_owner}-${var.environment}-jupyter-elb"
  elb_is_internal     = "false"
  elb_security_group  = "${module.jupyter_elb_sg.id}"
  subnets             = [ "${data.terraform_remote_state.vpc.public_subnet_ids}" ]
  frontend_port       = "80"
  frontend_protocol   = "tcp"
  backend_port        = "80"
  backend_protocol    = "tcp"
  health_check_target = "TCP:80"

  tags                = "${local.tags}"
}

#########################################################
# Baile ELB
module "baile_elb_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "baile-elb-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_cidr = [
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            cidr_blocks = "${var.access_cidr}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]

    tags = "${local.tags}"
}

module "baile_elb_internal_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "baile-elb-in-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id       = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]

    tags = "${local.tags}"
}

resource "aws_security_group_rule" "baile_elb_deploy_ingress_rule_cidr" {
    count = "${local.create_deploy_sgs * local.deploy_cidr_blank}"

    security_group_id = "${module.baile_elb_sg.id}"
    type              = "ingress"
    from_port         = "80"
    to_port           = "80"
    protocol          = "tcp"
    cidr_blocks       = ["${var.deploy_cidr}"]
    description       = "Access for baile from deploy cidr"
}

resource "aws_security_group_rule" "baile_elb_public_ingress_rule_cidr" {
    count = "${local.create_public_baile_access}"

    security_group_id = "${module.baile_elb_sg.id}"
    type              = "ingress"
    from_port         = "80"
    to_port           = "80"
    protocol          = "tcp"
    cidr_blocks       = ["0.0.0.0/0"]
    description       = "Access for baile from public internet"
}

module "baile_elb" {
  source              = "../../terraform/modules/elb"
  elb_name            = "${var.tag_owner}-${var.environment}-baile-elb"
  elb_is_internal     = "false"
  elb_security_group  = "${module.baile_elb_sg.id}"
  subnets             = [ "${data.terraform_remote_state.vpc.public_subnet_ids}" ]
  frontend_port       = "80"
  frontend_protocol   = "tcp"
  backend_port        = "80"
  backend_protocol    = "tcp"
  health_check_target = "TCP:80"

  tags                = "${local.tags}"
}

module "baile_elb_internal" {
  source              = "../../terraform/modules/elb"
  elb_name            = "${var.tag_owner}-${var.environment}-baile-elb-in"
  elb_is_internal     = "true"
  elb_security_group  = "${module.baile_elb_internal_sg.id}"
  subnets             = [ "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}" ]
  frontend_port       = "80"
  frontend_protocol   = "tcp"
  backend_port        = "80"
  backend_protocol    = "tcp"
  health_check_target = "TCP:80"

  tags                = "${local.tags}"
}

resource "aws_security_group_rule" "baile_elb_in_deploy_ingress_rule_cidr" {
    count = "${local.create_internal_deploy_sgs}"

    security_group_id        = "${module.baile_elb_internal_sg.id}"
    type                     = "ingress"
    from_port                = "80"
    to_port                  = "80"
    protocol                 = "tcp"
    source_security_group_id = "${var.deploy_sg}"
    description              = "Access for baile elb int from deploy sg"
}

#########################################################
# Public slaves
module "public_slave_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "public-slave-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 5
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${module.captain_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id = "${module.baile_elb_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id = "${module.baile_elb_internal_sg.id}"
        },
        {
            protocol    = "tcp"
            from_port   = "80"
            to_port     = "80"
            sg_id = "${module.jupyter_elb_sg.id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

data "template_file" "public_slave_userdata" {
  template = "${file("../../terraform/templates/public_slave_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

module "public_slave_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "public-slave-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-public-slave-"
    lc_instance_type        = "t2.medium"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.public_slave_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.public_slave_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.slave_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-public-slave-asg"
    asg_subnet_ids          = "${data.terraform_remote_state.vpc.public_subnet_ids}"
    asg_desired_capacity    = "${var.public_slave_asg_desired_capacity}"
    asg_min_size            = "${var.public_slave_asg_min_size}"
    asg_max_size            = "${var.public_slave_asg_max_size}"
    asg_load_balancers      = [ "${module.baile_elb.elb_id}", "${module.baile_elb_internal.elb_id}", "${module.jupyter_elb.elb_id}" ]

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-public-slave-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-public-slave"
    instance_role_tag = "public-slave"
}

#########################################################
# Captain
module "captain_sg" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "captain-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id = "${data.terraform_remote_state.base.sg_bastion_id}"
        },
    ]

    egress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "0"
            cidr_blocks = "0.0.0.0/0"
        },
    ]
    tags = "${local.tags}"
}

data "template_file" "captain_userdata" {
  template = "${file("../../terraform/templates/captain_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    dcos_username = "${var.dcos_username}"
    dcos_password = "${var.dcos_password}"
    dcos_master_url = "${module.master_elb_internal.elb_dns_name}"
    dcos_apps_bucket = "${data.terraform_remote_state.s3_buckets.apps_s3_bucket}"
    dcos_apps_bucket_domain = "${data.terraform_remote_state.s3_buckets.apps_s3_bucket}.${var.s3_endpoint}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    online_prediction_sqs_queue = "online-prediction-${var.tag_owner}-${var.environment}"
    aws_region = "${var.aws_region}"
    baile_lb_url = "${module.baile_elb.elb_dns_name}"
    baile_internal_lb_url = "${module.baile_elb_internal.elb_dns_name}"
    #redshift_user = "${data.terraform_remote_state.redshift.redshift_master_username}"
    #redshift_password = "${data.terraform_remote_state.redshift.redshift_master_password}"
    #redshift_host = "${data.terraform_remote_state.redshift.redshift_url}"
    #redshift_database = "${data.terraform_remote_state.redshift.redshift_database}"
    base_jupyter_lab_domain = "${module.jupyter_elb.elb_dns_name}"
    dcos_nodes = "${var.slave_asg_desired_capacity + var.public_slave_asg_desired_capacity + var.gpu_slave_asg_desired_capacity + var.gpu_jupyter_asg_desired_capacity + var.slave_jupyter_asg_desired_capacity}"
    master_instance_name = "${var.tag_owner}-${var.environment}-master"
    apps_aws_access_key = "${element(concat(compact(concat(list(var.apps_access_key), data.terraform_remote_state.iam.app_access_key)), list("")), local.key_selector)}"
    apps_aws_secret_key = "${element(concat(compact(concat(list(var.apps_secret_key), data.terraform_remote_state.iam.app_secret_key)), list("")), local.key_selector)}"
    rabbit_password = "${random_string.rabbit_password.result}"
    aries_http_search_user_password = "${random_string.aries_http_search_user_password.result}"
    aries_http_command_user_password = "${random_string.aries_http_command_user_password.result}"
    argo_http_auth_user_password = "${random_string.argo_http_auth_user_password.result}"
    baile_http_auth_user_password = "${random_string.baile_http_auth_user_password.result}"
    online_prediction_password = "${random_string.online_prediction_password.result}"
    online_prediction_stream_id = "${uuid()}"
    cortex_http_search_user_password = "${random_string.cortex_http_search_user_password.result}"
    orion_http_search_user_password = "${random_string.orion_http_search_user_password.result}"
    pegasus_http_auth_user_password = "${random_string.pegasus_http_auth_user_password.result}"
    mongodb_app_password = "${random_string.mongodb_app_password.result}"
    mongodb_rootadmin_password = "${random_string.mongodb_rootadmin_password.result}"
    mongodb_useradmin_password = "${random_string.mongodb_useradmin_password.result}"
    mongodb_clusteradmin_password = "${random_string.mongodb_clusteradmin_password.result}"
    mongodb_clustermonitor_password = "${random_string.mongodb_clustermonitor_password.result}"
    mongodb_backup_password = "${random_string.mongodb_backup_password.result}"
    gemini_http_auth_password = "${random_string.gemini_http_auth_password.result}"
    argo_docker_image_version = "${var.argo_docker_image_version}"
    aries_docker_image_version = "${var.aries_docker_image_version}"
    baile_docker_image_version = "${var.baile_docker_image_version}"
    baile_haproxy_docker_image_version = "${var.baile_haproxy_docker_image_version}"
    cortex_docker_image_version = "${var.cortex_docker_image_version}"
    cortex_task_docker_image_version = "${var.cortex_task_docker_image_version}"
    logstash_docker_image_version = "${var.logstash_docker_image_version}"
    orion_docker_image_version = "${var.orion_docker_image_version}"
    job_master_docker_image_version = "${var.job_master_docker_image_version}"
    pegasus_docker_image_version = "${var.pegasus_docker_image_version}"
    rmq_docker_image_version = "${var.rmq_docker_image_version}"
    scorpius_mongobackup_version = "${var.scorpius_mongobackup_version}"
    taurus_docker_image_version = "${var.taurus_docker_image_version}"
    um_docker_image_version = "${var.um_docker_image_version}"
    jupyter_lab_docker_image_version = "${var.jupyter_lab_docker_image_version}"
    gemini_docker_image_version = "${var.gemini_docker_image_version}"
    sql_server_docker_image_version = "${var.sql_server_docker_image_version}"
    salsa_version = "${var.salsa_version}"
    baile_api_path = "${var.baile_api_path}"
    baile_private_api_path = "${var.baile_private_api_path}"
    sql_server_api_path = "${var.sql_server_api_path}"
    baile_haproxy_vpc_dns = "${cidrhost(data.terraform_remote_state.vpc.vpc_cidr, 2)}"
    baile_haproxy_dns_resolver = "${local.baile_haproxy_dns_resolver}"
    upload_datasets = "${var.upload_datasets}"
    download_from_s3 = "${var.download_from_s3}"
    online_prediction = "${var.online_prediction}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    email_on = "${var.email_on}"
    smtp_aws_access_key_id = "${var.smtp_aws_access_key_id}"
    smtp_aws_secret_access_key = "${var.smtp_aws_secret_access_key}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
    percona_mongo_version = "${var.percona_mongo_version}"
    elastic_version = "${var.elastic_version}"
    kibana_version = "${var.kibana_version}"
    marathon_lb_version = "${var.marathon_lb_version}"
    terraform_log_file = "${var.terraform_log_file}"
    deployment_logs_s3_path = "${var.deployment_logs_s3_path}"
    dcos_apps_bucket = "${var.dcos_apps_bucket}"
    mongo_restore_date = "${var.mongo_restore_date}"
    docker_tar_list = "${var.docker_tar_list}"
    main_iam_role = "${element(data.terraform_remote_state.iam.deepcortex_main_role_arn, 0)}"
    jump_role= "${data.terraform_remote_state.iam.deepcortex_jump_role_arn}"
    aws_arn_partition = "${var.arn}"
  }

  depends_on = [
    "module.master_elb_internal"
  ]
}

module "captain_asg" {
    source = "../../terraform/modules/autoscaling_group"

    ami_name                = "captain-${var.tag_owner}-${var.environment}-*"
    lc_name_prefix          = "${var.environment}-captain-"
    lc_instance_type        = "t2.medium"
    lc_ebs_optimized        = "false"
    lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups      = [ "${module.captain_sg.id}", "${module.dcos_stack_sg.id}" ]
    lc_user_data            = "${data.template_file.captain_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.captain_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name                = "${var.tag_owner}-${var.environment}-captain-asg"

    # hack for having conditional for two lists of differnt length
    asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"

    asg_desired_capacity    = "${var.captain_asg_desired_capacity}"
    asg_min_size            = "${var.captain_asg_min_size}"
    asg_max_size            = "${var.captain_asg_max_size}"

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-captain-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-captain"
    instance_role_tag = "captain"
}

# GPU for Jupyter Service

data "template_file" "gpu_jupyter_service_userdata" {
  template = "${file("../../terraform/templates/gpu_jupyter_service_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    private_deployment = "${var.private_deployment}"
    vulkan_file_path = "${var.vulkan_file_path}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

# Auto scaling group for jupyter service

module "gpu_jupyter_asg" {
  source = "../../terraform/modules/autoscaling_group"

  ami_name                = "gpu-slave-jupyter-service-${var.tag_owner}-${var.environment}-*"
  lc_name_prefix          = "${var.environment}-gpu-slave-jupyter-service"
  lc_instance_type        = "p2.xlarge"
  lc_ebs_optimized        = "false"
  lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
  lc_security_groups      = [ "${module.slave_sg.id}", "${module.dcos_stack_sg.id}" ]
  lc_user_data            = "${data.template_file.gpu_jupyter_service_userdata.rendered}"

  asg_name                = "${var.tag_owner}-${var.environment}-gpu-jupyter-asg"
  asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"
  asg_desired_capacity    = "${var.gpu_jupyter_asg_desired_capacity}"
  asg_min_size            = "${var.gpu_jupyter_asg_min_size}"
  asg_max_size            = "${var.gpu_jupyter_asg_max_size}"

  tags_asg = "${local.tags_asg}"
  asg_name_tag = "${var.tag_owner}-${var.environment}-gpu-jupyter-asg"
  instance_name_tag = "${var.tag_owner}-${var.environment}-gpu-jupyter"
  instance_role_tag = "gpu-jupyter-service"
}

# Regular Slave for Jupyter Service

data "template_file" "slave_jupyter_service_userdata" {
  template = "${file("../../terraform/templates/private_slave_jupyter_userdata.tpl")}"

  vars {
    environment = "${var.environment}"
    aws_region = "${var.aws_region}"
    bootstrap_dns = "${module.bootstrap_elb.elb_dns_name}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
  }

  depends_on = [
    "module.bootstrap_elb"
  ]
}

# Auto scaling group for jupyter service

module "slave_jupyter_asg" {
  source = "../../terraform/modules/autoscaling_group"

  ami_name                = "slave-jupyter-${var.tag_owner}-${var.environment}-*"
  lc_name_prefix          = "${var.environment}-slave-jupyter-"
  lc_instance_type        = "m4.4xlarge"
  lc_ebs_optimized        = "false"
  lc_key_name             = "${data.terraform_remote_state.base.devops_key_name}"
  lc_security_groups      = [ "${module.slave_sg.id}", "${module.dcos_stack_sg.id}" ]
  lc_user_data            = "${data.template_file.slave_jupyter_service_userdata.rendered}"

  asg_name                = "${var.tag_owner}-${var.environment}-slave-jupyter-asg"
  asg_subnet_ids          = "${slice(concat(data.terraform_remote_state.vpc.public_subnet_ids, data.terraform_remote_state.vpc.private_egress_subnet_ids), var.only_public == "true" ? 0 : length(data.terraform_remote_state.vpc.public_subnet_ids), var.only_public == "true" ? length(data.terraform_remote_state.vpc.public_subnet_ids) : length(data.terraform_remote_state.vpc.public_subnet_ids) + length(data.terraform_remote_state.vpc.private_egress_subnet_ids))}"
  asg_desired_capacity    = "${var.slave_jupyter_asg_desired_capacity}"
  asg_min_size            = "${var.slave_jupyter_asg_min_size}"
  asg_max_size            = "${var.slave_jupyter_asg_max_size}"

  tags_asg = "${local.tags_asg}"
  asg_name_tag = "${var.tag_owner}-${var.environment}-slave-jupyter-asg"
  instance_name_tag = "${var.tag_owner}-${var.environment}-slave-jupyter"
  instance_role_tag = "slave-jupyter"
}


