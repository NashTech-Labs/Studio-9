# vim: ts=4:sw=4:et:ft=hcl

output "elb_id" {
  value = "${aws_elb.elb.id}"
}
output "elb_name" {
  value = "${aws_elb.elb.name}"
}
output "elb_dns_name" {
  value = "${aws_elb.elb.dns_name}"
}
