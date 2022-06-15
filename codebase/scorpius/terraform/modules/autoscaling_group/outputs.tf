# vim: ts=4:sw=4:et:ft=hcl

output "asg_name" {
  value = "${join("", aws_autoscaling_group.asg.*.name)}"
}
