# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "environment" {}
variable "tf_bucket" {}
variable "account" {}
variable "access_cidr" {}
variable "deploy_cidr" {}
variable "azs" {
    description = "Array AVZs. Must match number of public and or private subnets"
    type    = "list"
}

# Bootstrap vars

variable "bootstrap_asg_desired_capacity" {}

# Master vars

variable "master_asg_desired_capacity" {}

# Slave vars

variable "slave_asg_desired_capacity" {}

# GPU slave vars

variable "gpu_slave_asg_desired_capacity" {}

# Public slave vars

variable "public_slave_asg_desired_capacity" {}

variable "tag_owner" {}
variable "tag_usage" {}
variable "tag_layer" {
    default = "monitoring"
}

locals {
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "${var.tag_layer}"
        usage       = "${var.tag_usage}"
    }
}
