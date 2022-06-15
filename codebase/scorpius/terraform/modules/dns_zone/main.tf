# vim: ts=4:sw=4:et:ft=hcl

###############################################################################
# << Public zone

resource "aws_route53_zone" "zone" {

    name    = "${var.domain}"
    comment = "${var.comment}"
    vpc_id  = "${var.vpc_id}"

    tags = "${merge(var.tags, map("type", (length(var.vpc_id) == 0 ? "public" : "private")))}"

}

