# vim: ts=4:sw=4:et:ft=hcl

output "vpc_id" {
  value = "${module.vpc.vpc_id}"
}

output "vpce_id" {
  value = "${module.vpc.vpce_id}"
}

output "vpc_cidr" {
  value = "${var.vpc_cidr}"
}

output "public_subnet_ids" {
  value = [ "${module.vpc.public_subnet_ids}" ]
}

output "private_subnet_ids" {
  value = [ "${module.vpc.private_subnet_ids}" ]
}

output "private_egress_subnet_ids" {
  value = [ "${module.vpc.private_egress_subnet_ids}" ]
}

output "route_tables_public" {
    value = "${module.vpc.route_tables_public}"
}

output "route_tables_private" {
    value = "${module.vpc.route_tables_private}"
}

output "route_tables_private_egress" {
    value = "${module.vpc.route_tables_private_egress}"
}