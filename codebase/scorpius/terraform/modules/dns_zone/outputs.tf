# vim: ts=4:sw=4:et:ft=hcl

output "zone_id" {
  value = "${aws_route53_zone.zone.zone_id}"
}

output "name_servers" {
  value = "${aws_route53_zone.zone.name_servers}"
}

output "domain" {
  value = "${var.domain}"
}
