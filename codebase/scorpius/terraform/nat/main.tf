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

# Retrieve BASE data

data "terraform_remote_state" "base" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/base/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

#########################################################

module "sg_nat" {
    source = "../../terraform/modules/security_group"

    vpc_id = "${data.terraform_remote_state.vpc.vpc_id}"

    sg_name = "nat-instance"
    sg_description = "NAT instances SG"

    ingress_rules_sgid_count = 2
    ingress_rules_sgid = [
        {
            protocol    = "tcp"
            from_port   = "22"
            to_port     = "22"
            sg_id       = "${data.terraform_remote_state.base.sg_bastion_id}"
            description = "SSH access from bastion"
        },
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "65536"
            sg_id       = "${data.terraform_remote_state.base.sg_private_egress_subnet_id}"
            description = "Allow all traffic from Private egress subnet"
        },
    ]

    ingress_rules_cidr = [
        {
            protocol    = "all"
            from_port   = "0"
            to_port     = "65536"
            cidr_blocks = "${join(",", var.private_subnets_egress)}"
            description = "Allow all traffic from Private egress subnet"
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

data "template_file" "nat_instance_userdata" {
    template = "${file("../../terraform/templates/nat_instance_userdata.tpl")}"
    vars {
        azs         = "${join(",", var.azs)}"
        rts         = "${join(",", data.terraform_remote_state.vpc.route_tables_private_egress)}"
        egress_cidr = "${join(",", var.private_subnets_egress)}"
        vpc_cidr    = "${var.vpc_cidr}"
    }
}

module "asg_nat" {
    source = "../../terraform/modules/autoscaling_group"

    lc_ami_id          = "${var.nat_ami_id}"
    lc_name_prefix     = "${var.environment}-nat-"
    lc_instance_type   = "t2.small"
    lc_ebs_optimized   = "false"
    lc_key_name        = "${data.terraform_remote_state.base.devops_key_name}"
    lc_security_groups = [ "${module.sg_nat.id}" ]
    lc_user_data       = "${data.template_file.nat_instance_userdata.rendered}"
    lc_iam_instance_profile = "${data.terraform_remote_state.iam.nat_instance_profile_name}"

    asg_name             = "${var.tag_owner}-${var.environment}-nat-asg"
    asg_subnet_ids       = "${data.terraform_remote_state.vpc.public_subnet_ids}"
    asg_desired_capacity = "${length(var.azs)}"
    asg_min_size         = "${length(var.azs)}"
    asg_max_size         = "${length(var.azs)}"

    tags_asg = "${local.tags_asg}"
    asg_name_tag = "${var.tag_owner}-${var.environment}-nat-asg"
    instance_name_tag = "${var.tag_owner}-${var.environment}-nat"
    instance_role_tag = "nat"

}
