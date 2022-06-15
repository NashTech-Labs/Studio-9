# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "environment" {}
variable "download_ssh_keys" { default = "false" }
variable "ssh_keys_s3_bucket" { default = "" }
variable "main_user" {}
variable "only_public" { default = true }
variable "create_vpc" { default = true }
variable "ssh_public_key" {}
variable "bastion_ami_id" {}
variable "access_cidr" {}
variable "s3_endpoint" {}
variable "artifacts_s3_bucket" {}
variable "private_deployment" { default = "false" }
variable "install_amazon_ssm_agent" { default = "true" }
variable "install_aws_cli" { default = "true" }
variable "aws_ca_bundle" {}

variable "tf_bucket" {}

variable "vpc_cidr" {}
variable "azs" {
    description = "Array AVZs. Must match number of public and or private subnets"
    type    = "list"
}

variable "public_subnets" {
    description = "Array of public subnet CIDR. Must match number of AVZs"
    type        = "list"
}

variable "private_subnets" {
    description = "Array of private subnet CIDR. Must match number of AVZs"
    type    = "list"
}

variable "private_subnets_egress" {
    description = "Array of private egress subnet CIDR. Must match number of AVZs"
    type    = "list"
}

variable "tag_owner" {}
variable "tag_usage" {}

locals {
    create_vpc = "${var.create_vpc == "true" ? 1 : 0}"
    private_sg = "${var.only_public == "true" ? 0 : 1}"
    private_egress_sg = "${var.only_public == "true" ? 0 : 1}"
    create_public_sg = "${local.create_vpc}"
    create_private_sg = "${local.private_sg * local.create_vpc}"
    create_private_egress_sg = "${local.private_egress_sg * local.create_vpc}"
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "vpc"
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
            value = "vpc"
            propagate_at_launch = "true"
        },
        {
            key   = "usage"
            value = "${var.tag_usage}"
            propagate_at_launch = "true"
        },
    ]
}
