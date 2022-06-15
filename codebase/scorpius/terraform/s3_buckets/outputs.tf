# vim: ts=4:sw=4:et:ft=hcl

output "apps_s3_bucket" {
  value = "${aws_s3_bucket.dcos_apps_bucket.id}"
}

output "apps_s3_bucket_arn" {
  value = "${aws_s3_bucket.dcos_apps_bucket.arn}"
}

output "stack_s3_bucket" {
  value = "${aws_s3_bucket.dcos_stack_bucket.id}"
}

output "stack_s3_bucket_arn" {
  value = "${aws_s3_bucket.dcos_stack_bucket.arn}"
}