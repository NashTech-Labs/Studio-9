# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "environment" {}
variable "tag_owner" {}
variable "tag_usage" {}
variable "tf_bucket" {}
variable "dcos_stack_bucket" {}
variable "dcos_apps_bucket" {}
variable "access_cidr" {}
variable "deploy_cidr" {}
variable "baile_access" {}
variable "s3_endpoint" {}
variable "arn" { default = "aws"}
variable "deployment_logs_retention_period" { default = "7" }
variable "mongo_backup_retention_period" { default = "7" }

locals {
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "platform"
        usage       = "${var.tag_usage}"
    }
}
