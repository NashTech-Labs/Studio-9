# vim: ts=4:sw=4:et:ft=hcl

variable "aws_region" {}
variable "tag_owner" {}
variable "tag_usage" {}

locals {
    tags = {
        owner       = "${var.tag_owner}"
        environment = "${var.environment}"
        layer       = "dns"
        usage       = "${var.tag_usage}"
    }
}
