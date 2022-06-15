# vim: ts=4:sw=4:et:ft=hcl

###############################################################################
# << Security group

resource "aws_security_group" "sg" {

    count       = "${var.sg_count}"
    vpc_id      = "${var.vpc_id}"
    name        = "${var.sg_name}"
    description = "${var.sg_description}"

    tags = "${merge(var.tags, map(
            "Name", var.sg_name)
    )}"
}

###############################################################################
# << Security group rules for ingress

## CIDR source rules
#
# Example:
# [
#   {
#       protocol   = "tcp"
#       from_port  = "80"
#       to_port    = "80"
#       cidr_block = "10.10.10.1, 10.10.10.2"
#       desc      = "Some description"
#   },
#   { ... }
# ]
#
#
resource "aws_security_group_rule" "ingress_rule_cidr" {
    count = "${length(var.ingress_rules_cidr) * var.sg_count}"

    security_group_id = "${aws_security_group.sg.id}"
    type              = "ingress"
    from_port         = "${lookup(var.ingress_rules_cidr[count.index], "from_port")}"
    to_port           = "${lookup(var.ingress_rules_cidr[count.index], "to_port")}"
    protocol          = "${lookup(var.ingress_rules_cidr[count.index], "protocol")}"
    cidr_blocks       = [ "${split(",", lookup(var.ingress_rules_cidr[count.index], "cidr_blocks"))}" ]
    description       = "${lookup(var.ingress_rules_cidr[count.index], "description", "_")}"
}

## Security Group ID
# Example:
# [
#   {
#       protocol  = "tcp"
#       from_port = "80"
#       to_port   = "80"
#       sg_id     = "sg-id12345"
#       desc      = "Some description"
#   },
#   { ... }
# ]
#
#

resource "aws_security_group_rule" "ingress_rule_sgid" {
    #count = "${length(var.ingress_rules_sgid)}"
    # https://github.com/hashicorp/terraform/issues/10857
    # TL;DR: list elements that are computed values restrain the ability to
    # calculate the length of the object
    # count = "${length(var.routes)}"
    count = "${var.ingress_rules_sgid_count * var.sg_count}"

    security_group_id        = "${aws_security_group.sg.id}"
    type                     = "ingress"
    from_port                = "${lookup(var.ingress_rules_sgid[count.index], "from_port")}"
    to_port                  = "${lookup(var.ingress_rules_sgid[count.index], "to_port")}"
    protocol                 = "${lookup(var.ingress_rules_sgid[count.index], "protocol")}"
    source_security_group_id = "${lookup(var.ingress_rules_sgid[count.index], "sg_id")}"
    description              = "${lookup(var.ingress_rules_sgid[count.index], "description", "_")}"
}

## Self
# Example:
# [
#   {
#       protocol  = "tcp"
#       from_port = "80"
#       to_port   = "80"
#       desc      = "Some description"
#   },
#   { ... }
# ]
#
#

resource "aws_security_group_rule" "ingress_rule_self" {
    #count = "${length(var.ingress_rules_self)}"
    # https://github.com/hashicorp/terraform/issues/10857
    # TL;DR: list elements that are computed values restrain the ability to
    # calculate the length of the object
    # count = "${length(var.routes)}"
    count = "${length(var.ingress_rules_self) * var.sg_count}"

    security_group_id        = "${aws_security_group.sg.id}"
    type                     = "ingress"
    from_port                = "${lookup(var.ingress_rules_self[count.index], "from_port")}"
    to_port                  = "${lookup(var.ingress_rules_self[count.index], "to_port")}"
    protocol                 = "${lookup(var.ingress_rules_self[count.index], "protocol")}"
    self                     = true
    description              = "${lookup(var.ingress_rules_self[count.index], "description", "_")}"
}

## Egress

resource "aws_security_group_rule" "egress_rule_cidr" {
    count = "${length(var.egress_rules_cidr) * var.sg_count}"

    security_group_id = "${aws_security_group.sg.id}"
    type              = "egress"
    from_port         = "${lookup(var.egress_rules_cidr[count.index], "from_port")}"
    to_port           = "${lookup(var.egress_rules_cidr[count.index], "to_port")}"
    protocol          = "${lookup(var.egress_rules_cidr[count.index], "protocol")}"
    cidr_blocks       = [ "${split(",", lookup(var.egress_rules_cidr[count.index], "cidr_blocks"))}" ]
    description       = "${lookup(var.egress_rules_cidr[count.index], "description", "_")}"
}

resource "aws_security_group_rule" "egress_rule_sgid" {
    #count = "${length(var.egress_rules_sgid)}"
    # https://github.com/hashicorp/terraform/issues/10857
    # TL;DR: list elements that are computed values restrain the ability to
    # calculate the length of the object
    # count = "${length(var.routes)}"
    count = "${var.egress_rules_sgid_count * var.sg_count}"

    security_group_id        = "${aws_security_group.sg.id}"
    type                     = "egress"
    from_port                = "${lookup(var.egress_rules_sgid[count.index], "from_port")}"
    to_port                  = "${lookup(var.egress_rules_sgid[count.index], "to_port")}"
    protocol                 = "${lookup(var.egress_rules_sgid[count.index], "protocol")}"
    source_security_group_id = "${lookup(var.egress_rules_sgid[count.index], "sg_id")}"
    description              = "${lookup(var.egress_rules_sgid[count.index], "description", "_")}"
}

resource "aws_security_group_rule" "egress_rule_self" {
    #count = "${length(var.egress_rules_self)}"
    # https://github.com/hashicorp/terraform/issues/10857
    # TL;DR: list elements that are computed values restrain the ability to
    # calculate the length of the object
    # count = "${length(var.routes)}"
    count = "${length(var.egress_rules_self) * var.sg_count}"

    security_group_id        = "${aws_security_group.sg.id}"
    type                     = "egress"
    from_port                = "${lookup(var.egress_rules_self[count.index], "from_port")}"
    to_port                  = "${lookup(var.egress_rules_self[count.index], "to_port")}"
    protocol                 = "${lookup(var.egress_rules_self[count.index], "protocol")}"
    self                     = true
    description              = "${lookup(var.egress_rules_self[count.index], "description", "_")}"
}
