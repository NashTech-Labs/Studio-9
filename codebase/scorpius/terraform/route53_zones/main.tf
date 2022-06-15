# vim: ts=4:sw=4:et:ft=hcl
#
# Only public zones should be defined here.
# Private zones depend on VPC ID, so they should be
# defined along the VPC resources.
#

terraform {
    required_version = ">= 0.10.7"
    backend "s3" {}
}

module "zone_public_deepcortex_com" {
    source = "../../terraform/modules/dns_zone"
    domain = "public.deepcortex.com"
    tags   = "${local.tags}"
}

#module "zone_private_deepcortex_com" {
#    source = "../../terraform/modules/dns_zone"
#    domain = "private.deepcortex.com"
#    vpc_id = "vpc-391eae50"
#    tags   = "${local.tags}"
#}
