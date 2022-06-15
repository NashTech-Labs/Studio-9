# vim: ts=4:sw=4:et:ft=hcl

output "id" {
  value = "${join("", aws_security_group.sg.*.id)}"
}

