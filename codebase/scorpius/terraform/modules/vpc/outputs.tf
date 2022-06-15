# vim: ts=4:sw=4:et:ft=hcl

output "vpc_id" {
    value = "${aws_vpc.vpc.id}"
}

output "vpce_id" {
    value = "${aws_vpc_endpoint.s3.id}"
}

output "vpc_igw" {
    value = "${aws_internet_gateway.igw.id}"
}

output "public_subnet_ids" {
    value = [ "${aws_subnet.public.*.id}" ]
}

output "private_subnet_ids" {
    value = [ "${aws_subnet.private.*.id}" ]
}

output "private_egress_subnet_ids" {
    value = [ "${aws_subnet.private_egress.*.id}" ]
}

output "route_tables_public" {
    value = "${aws_route_table.public.*.id}"
}

output "route_tables_private" {
    value = "${aws_route_table.private.*.id}"
}

output "route_tables_private_egress" {
    value = "${aws_route_table.private_egress.*.id}"
}

