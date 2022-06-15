# vim: ts=4:sw=4:et:ft=hcl


output "asg_nat_name" {
  value = "${module.asg_nat.asg_name}"
}
