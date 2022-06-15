# vim: ts=4:sw=4:et:ft=hcl

output "name" {
  value = "${aws_key_pair.key.key_name}"
}

