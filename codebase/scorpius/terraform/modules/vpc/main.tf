# vim: ts=4:sw=4:et:ft=hcl

resource "aws_vpc" "vpc" {
    cidr_block           = "${var.vpc_cidr}"
    enable_dns_hostnames = "${var.vpc_dns_hostnames}"
    enable_dns_support   = "${var.vpc_dns_support}"
    instance_tenancy     = "default"

    tags = "${merge(var.tags, map(
            "Name", format("%s-%s-vpc", var.tags["owner"], var.tags["environment"])
        )
    )}"
}

resource "aws_internet_gateway" "igw" {
    depends_on = [ "aws_vpc.vpc" ]

    vpc_id = "${aws_vpc.vpc.id}"

    tags = "${merge(var.tags, map(
            "Name", format("%s-%s-igw", var.tags["owner"], var.tags["environment"])
        )
    )}"
}


###############################################################################
# << DHCP Options

resource "aws_vpc_dhcp_options" "dhcp_opts" {
    depends_on = [ "aws_vpc.vpc" ]

    domain_name         = "${var.vpc_dhcp_opts_domain_name}"
    domain_name_servers = "${var.vpc_dhcp_opts_domain_name_servers}"

    tags = "${merge(var.tags, map(
           "Name", format("%s-%s-dhcp-options-set", var.tags["owner"], var.tags["environment"])
        )
    )}"

}

resource "aws_vpc_dhcp_options_association" "dhcp_assoc" {
    depends_on = [ "aws_vpc.vpc" ]

    vpc_id          = "${aws_vpc.vpc.id}"
    dhcp_options_id = "${aws_vpc_dhcp_options.dhcp_opts.id}"
}

###############################################################################
# << Route Tables

## Public route table

resource "aws_route_table" "public" {
    count = "${length(var.public_subnets) > 0 ? 1 : 0}"
    depends_on = [ "aws_vpc.vpc" ]

    vpc_id = "${aws_vpc.vpc.id}"

    tags = "${merge(var.tags, map(
            "Name", format("public-rt-%s-%s",
                var.tags["owner"],
                var.tags["environment"]
            )
        )
    )}"
}

## Private route tables

resource "aws_route_table" "private" {
    count = "${length(var.private_subnets) > 0 ? 1 : 0}"
    depends_on = [ "aws_vpc.vpc" ]

    vpc_id = "${aws_vpc.vpc.id}"

    tags = "${merge(var.tags, map(
            "Name", format("private-rt-%s-%s",
                var.tags["owner"],
                var.tags["environment"]
            )
        )
    )}"
}

resource "aws_route_table" "private_egress" {
    depends_on = [ "aws_vpc.vpc" ]
    count = "${length(var.private_egress_subnets) > 0 ? 1 : 0}"

    vpc_id = "${aws_vpc.vpc.id}"

    tags = "${merge(var.tags, map(
            "Name", format("private-egress-rt-%s-%s",
                var.tags["owner"],
                var.tags["environment"]
            )
        )
    )}"
}
# Private routes will be handled on a different module

## Public default route

resource "aws_route" "igw" {
    depends_on = [ "aws_route_table.public" ]

    route_table_id         = "${aws_route_table.public.id}"
    destination_cidr_block = "0.0.0.0/0"
    gateway_id             = "${aws_internet_gateway.igw.id}"
}

###############################################################################
# << Subnets

# Get current region (defined by provider configuration)
data "aws_region" "current" {}

## Public subnets

resource "aws_subnet" "public" {
    count      = "${length(var.public_subnets)}"
    depends_on = [ "aws_route_table.public" ]

    vpc_id                  = "${aws_vpc.vpc.id}"
    cidr_block              = "${element(var.public_subnets, count.index)}"
    availability_zone       = "${format("%s%s",
                                    replace(data.aws_region.current.name, "/[0-9]$/", ""),
                                    element(var.azs, count.index)
                                )}"
    map_public_ip_on_launch = "true"

    tags = "${merge(var.tags, map(
            "Name", format("subnet-public-%s", element(var.azs, count.index))
        )
    )}"
}

resource "aws_subnet" "private" {
    count      = "${length(var.private_subnets)}"
    depends_on = [ "aws_route_table.private" ]

    vpc_id                  = "${aws_vpc.vpc.id}"
    cidr_block              = "${element(var.private_subnets, count.index)}"
    availability_zone       = "${format("%s%s",
                                    replace(data.aws_region.current.name, "/[0-9]$/", ""),
                                    element(var.azs, count.index)
                                )}"
    map_public_ip_on_launch = "false"

    tags = "${merge(var.tags, map(
            "Name", format("subnet-private-%s", element(var.azs, count.index))
        )
    )}"
}

resource "aws_subnet" "private_egress" {
    count      = "${length(var.private_egress_subnets)}"
    depends_on = [ "aws_route_table.private_egress" ]

    vpc_id                  = "${aws_vpc.vpc.id}"
    cidr_block              = "${element(var.private_egress_subnets, count.index)}"
    availability_zone       = "${format("%s%s",
                                    replace(data.aws_region.current.name, "/[0-9]$/", ""),
                                    element(var.azs, count.index)
                                )}"
    map_public_ip_on_launch = "false"

    tags = "${merge(var.tags, map(
            "Name", format("subnet-private-egress-%s",
                element(var.azs, count.index)
            )
        )
    )}"
}

###############################################################################
# << VPC endpoints

## S3

# Obtain vpc endpoint service name
data "aws_vpc_endpoint_service" "s3" {
	service = "s3"
}

resource "aws_vpc_endpoint" "s3" {
	vpc_id       = "${aws_vpc.vpc.id}"
	service_name = "${data.aws_vpc_endpoint_service.s3.service_name}"
}

resource "aws_vpc_endpoint_route_table_association" "s3_public" {
    depends_on = [ "aws_route_table.public" ]
    count = "${length(var.public_subnets) > 0 ? 1 : 0}"

    vpc_endpoint_id = "${aws_vpc_endpoint.s3.id}"
    route_table_id  = "${aws_route_table.public.id}"
}

resource "aws_vpc_endpoint_route_table_association" "s3_private" {
    depends_on = [ "aws_route_table.private" ]
    count = "${length(var.private_subnets) > 0 ? 1 : 0}"

    vpc_endpoint_id = "${aws_vpc_endpoint.s3.id}"
    route_table_id  = "${aws_route_table.private.id}"
}

resource "aws_vpc_endpoint_route_table_association" "s3_private_egress" {
    depends_on = [ "aws_route_table.private_egress" ]
    count = "${length(var.private_egress_subnets) > 0 ? 1 : 0}"

    vpc_endpoint_id = "${aws_vpc_endpoint.s3.id}"
    route_table_id  = "${aws_route_table.private_egress.id}"
}

###############################################################################
# << Route Tables Association

resource "aws_route_table_association" "public" {
    count = "${length(var.public_subnets)}"

    subnet_id      = "${element(aws_subnet.public.*.id, count.index)}"
    route_table_id = "${aws_route_table.public.id}"
}

resource "aws_route_table_association" "private" {
    count = "${length(var.private_subnets)}"

    subnet_id      = "${element(aws_subnet.private.*.id, count.index)}"
    route_table_id = "${aws_route_table.private.id}"
}

resource "aws_route_table_association" "private_egress" {
    count = "${length(var.private_egress_subnets)}"

    subnet_id      = "${element(aws_subnet.private_egress.*.id, count.index)}"
    route_table_id = "${aws_route_table.private_egress.id}"
}

###############################################################################

