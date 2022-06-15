# vim: ts=4:sw=4:et:ft=hcl

resource "aws_elb" "elb" {
    name = "${var.elb_name}"
    subnets = [ "${var.subnets}" ]
    internal = "${var.elb_is_internal}"
    security_groups = [ "${var.elb_security_group}" ]

    listener {
      instance_port     = 80
      instance_protocol = "http"
      lb_port           = 80
      lb_protocol       = "http"
    }

    listener {
      instance_port     = 443
      instance_protocol = "tcp"
      lb_port           = 443
      lb_protocol       = "tcp"
    }

    listener {
      instance_port     = 8181
      instance_protocol = "http"
      lb_port           = 8181
      lb_protocol       = "http"
    }

    health_check {
        healthy_threshold = "${var.healthy_threshold}"
        unhealthy_threshold = "${var.unhealthy_threshold}"
        timeout = "${var.timeout}"
        target = "${var.health_check_target}"
        interval = "${var.interval}"
    }

    cross_zone_load_balancing   = "${var.cross_zone_load_balancing}"
    idle_timeout                = "${var.idle_timeout}"
    connection_draining         = "${var.connection_draining}"
    connection_draining_timeout = "${var.connection_draining_timeout}"

    tags = "${merge(var.tags, map(
        "Name", format("%s-%s-%s-elb",
                var.elb_name,
                var.tags["owner"],
                var.tags["environment"]
            )
        )
    )}"
}

data "aws_elb_hosted_zone_id" "main" {}

# This data source provides a helper var to obtain the domain name,
# which would otherwise be needed as an additional parameter.
data "aws_route53_zone" "domain" {
    count = "${length(var.dns_records) > 0 ? 1 : 0}"
    zone_id = "${var.dns_zone_id}"
}

resource "aws_route53_record" "elb" {
    count = "${length(var.dns_records)}"

    zone_id = "${var.dns_zone_id}"
    name    = "${format("%s.%s", element(var.dns_records, count.index), data.aws_route53_zone.domain.name)}"
    type    = "A"

    alias {
        name    = "${aws_elb.elb.dns_name}"
        zone_id = "${data.aws_elb_hosted_zone_id.main.id}"
        evaluate_target_health = "${var.dns_eval_target_health}"
    }

    #set_identifier = "primary"
    #
    #failover_routing_policy {
    #    type = "${var.dns_failover_type}"
    #}

}
