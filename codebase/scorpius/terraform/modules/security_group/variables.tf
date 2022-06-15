# vim: ts=4:sw=4:et:ft=hcl

variable "vpc_id" {
  description = "VPC ID"
}

variable "sg_name" {
  description = "Security Group name"
}

variable "sg_description" {
  description = "Security Group description"
  default     = "Security group"
}

variable "sg_count" { default = 1 }

variable "tags" {
  description = "Tag maps"
  type        = "map"
  default     = {}
}

variable "ingress_rules_cidr" {
  description = "List of maps containing rules and source as CIDR blocks"
  type        = "list"
  default     = []
}

# https://github.com/hashicorp/terraform/issues/10857
# TL;DR: list elements that are computed values restrain the ability to
# calculate the length of the object
# count = "${length(var.routes)}"
variable "ingress_rules_sgid_count" {
    default = 0
}

variable "ingress_rules_sgid" {
  description = "List of maps containing rules and source as Security Group IDs"
  type        = "list"
  default     = []
}

variable "ingress_rules_self" {
  description = "List of maps containing rules and source as Self"
  type        = "list"
  default     = []
}

variable "egress_rules_cidr" {
  description = "List of maps containing rules and source as CIDR blocks"
  type        = "list"
  default     = []
}

# https://github.com/hashicorp/terraform/issues/10857
# TL;DR: list elements that are computed values restrain the ability to
# calculate the length of the object
# count = "${length(var.routes)}"
variable "egress_rules_sgid_count" {
    default = 0
}

variable "egress_rules_sgid" {
  description = "List of maps containing rules and source as Security Group IDs"
  type        = "list"
  default     = []
}

variable "egress_rules_self" {
  description = "List of maps containing rules and source as Self"
  type        = "list"
  default     = []
}
