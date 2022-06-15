# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "tf_bucket" {}
variable "account" {}

# Redshift vars
variable "environment" {}

variable "tag_owner" {}
variable "tag_usage" {}

locals {
    restore_redshift = "${var.redshift_snapshot == "" ? 0 : 1}"
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "dns"
        usage       = "${var.tag_usage}"
    }
}

variable "redshift_family" {}
variable "redshift_database_name" {}
variable "redshift_master_username" {}
variable "redshift_node_type" {}
variable "redshift_cluster_type" {}
variable "redshift_number_of_nodes" {}
variable "redshift_encrypted" {}
variable "redshift_skip_final_snapshot" {}
variable "access_cidr" {}
variable "redshift_enhanced_vpc_routing" { default = true }
variable "redshift_snapshot" { default = "" }
variable "automated_snapshot_retention_period" { default = 7 }
variable "redshift_publicly_accessible" { default = false }