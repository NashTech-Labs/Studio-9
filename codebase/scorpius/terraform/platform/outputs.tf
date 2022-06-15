# vim: ts=4:sw=4:et:ft=hcl

output "master_elb_url" {
  value = "${module.master_elb.elb_dns_name}"
}

output "baile_elb_url" {
  value = "${module.baile_elb.elb_dns_name}"
}

output "jupyter_elb_url" {
  value = "${module.jupyter_elb.elb_dns_name}"
}

output "bootstrap_elb_id" {
  value = "${module.bootstrap_elb.elb_id}"
}

output "master_elb_id" {
  value = "${module.master_elb_internal.elb_id}"
}

output "baile_elb_id" {
  value = "${module.baile_elb.elb_id}"
}

output "jupyter_elb_id" {
  value = "${module.jupyter_elb.elb_id}"
}

output "bootstrap_asg_name" {
  value = "${module.bootstrap_asg.asg_name}"
}

output "master_asg_name" {
  value = "${module.master_asg.asg_name}"
}

output "slave_asg_name" {
  value = "${module.slave_asg.asg_name}"
}

output "public_slave_asg_name" {
  value = "${module.public_slave_asg.asg_name}"
}

output "gpu_slave_asg_name" {
  value = "${module.gpu_slave_asg.asg_name}"
}

output "captain_asg_name" {
  value = "${module.captain_asg.asg_name}"
}

output "gpu_jupyter_asg_name" {
  value = "${module.gpu_jupyter_asg.asg_name}"
}
