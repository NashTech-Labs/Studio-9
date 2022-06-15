# vim: ts=4:sw=4:et:ft=hcl

output "redshift_master_username" {
  value = "${var.redshift_master_username}"
}
output "redshift_master_password" {
  value = "${random_string.redshift_password.result}"
}
output "redshift_database" {
  value = "${var.redshift_database_name}"
}
output "redshift_url" {
  value = "${element(split(":", element(compact(concat(aws_redshift_cluster.redshift-clstr.*.endpoint, aws_redshift_cluster.redshift-clstr-restore.*.endpoint)),0)),0)}"
}
output "redshift_cluster_identifier" {
  value = "${element(compact(concat(aws_redshift_cluster.redshift-clstr.*.cluster_identifier, aws_redshift_cluster.redshift-clstr-restore.*.cluster_identifier)),0)}"
}
output "sg_redshift_id" {
  value = "${module.redshift-clstr-sg.id}"
}
