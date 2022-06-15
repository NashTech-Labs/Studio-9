# vim: ts=4:sw=4:et:ft=hcl

variable "domain" {
    description = "Domain name"
    type = "string"
}

variable "comment" {
    description = "Domain comment"
    default     = ""
}

variable "vpc_id" {
    description = "VPC ID for private zones"
    default     = ""
}

variable "tags" {
    description = "Tags"
    type        = "map"
    default     = {}
}

