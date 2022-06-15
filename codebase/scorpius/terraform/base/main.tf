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

# Retrieve VPC data

data "terraform_remote_state" "vpc" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/vpc/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

#########################################################

module "devops_key" {
    source = "../../terraform/modules/key_pair"

    key_name   = "devops-${var.tag_owner}-${var.environment}"
    public_key = "${var.ssh_public_key}"
}

module "sg_bastion" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "ssh-bastion-${var.tag_owner}-${var.environment}"
    sg_description = "some description"

    ingress_rules_cidr = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
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

module "sg_public_subnet" {
    source = "../../terraform/modules/security_group"

    sg_count = "${local.create_public_sg}"
    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "public-subnet-${var.tag_owner}-${var.environment}"
    sg_description = "Public subnets SG"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id       = "${module.sg_bastion.id}"
            description = "SSH access from bastion"
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

module "sg_private_subnet" {
    source = "../../terraform/modules/security_group"

    sg_count = "${local.create_private_sg}"
    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "private-subnet-${var.tag_owner}-${var.environment}"
    sg_description = "Private subnets SG"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id       = "${module.sg_bastion.id}"
            description = "SSH access from bastion"
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

module "sg_private_egress_subnet" {
    source = "../../terraform/modules/security_group"

    sg_count = "${local.create_private_egress_sg}"
    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "private-egress-subnet-${var.tag_owner}-${var.environment}"
    sg_description = "Private Egress subnets SG"

    ingress_rules_sgid_count = 1
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id       = "${module.sg_bastion.id}"
            description = "SSH access from bastion"
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

data "template_file" "bastion_userdata" {
  template = "${file("../../terraform/templates/bastion_userdata.tpl")}"

  vars {
    aws_region = "${var.aws_region}"
    environment = "${var.environment}"
    download_ssh_keys = "${var.download_ssh_keys}"
    ssh_keys_s3_bucket = "${var.ssh_keys_s3_bucket}"
    main_user = "${var.main_user}"
    s3_endpoint = "${var.s3_endpoint}"
    artifacts_s3_bucket = "${var.artifacts_s3_bucket}"
    private_deployment = "${var.private_deployment}"
    install_amazon_ssm_agent = "${var.install_amazon_ssm_agent}"
    install_aws_cli = "${var.install_aws_cli}"
    aws_ca_bundle = "${var.aws_ca_bundle}"
  }
}

module "asg_bastion" {
    source = "../../terraform/modules/autoscaling_group"

    lc_ami_id          = "${var.bastion_ami_id}"
    lc_name_prefix     = "${var.tag_owner}-${var.environment}-bastion"
    lc_instance_type   = "t2.small"
    lc_ebs_optimized   = "false"
    lc_key_name        = "${module.devops_key.name}"
    lc_security_groups = [ "${module.sg_bastion.id}" ]
    lc_user_data       = "${data.template_file.bastion_userdata.rendered}"
    lc_iam_instance_profile = "${element(compact(concat(data.terraform_remote_state.iam.bastion_instance_profile_name, data.terraform_remote_state.iam.deepcortex_main_instance_profile_name)),0)}"

    asg_name             = "${var.tag_owner}-${var.environment}-bastion-asg"
    asg_subnet_ids       = "${data.terraform_remote_state.vpc.public_subnet_ids}"
    asg_desired_capacity = 1
    asg_min_size         = 1
    asg_max_size         = 1

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-bastion-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-bastion"
    instance_role_tag = "bastion"
}
