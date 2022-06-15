# vim: ts=4:sw=4:et:ft=hcl

output "devops_key_name" {
  value = "${module.devops_key.name}"
}

output "sg_public_subnet_id" {
  value = "${element(compact(concat(list(local.create_public_sg == 0 ? "N/A" : ""), list(module.sg_public_subnet.id))), 0)}"
}

output "sg_private_subnet_id" {
  value = "${element(compact(concat(list(local.create_private_sg == 0 ? "N/A" : ""), list(module.sg_private_subnet.id))), 0)}"
}

output "sg_private_egress_subnet_id" {
  value = "${element(compact(concat(list(local.create_private_egress_sg == 0 ? "N/A" : ""), list(module.sg_private_egress_subnet.id))), 0)}"
}

output "sg_bastion_id" {
  value = "${module.sg_bastion.id}"
}

output "asg_bastion_name" {
  value = "${module.asg_bastion.asg_name}"
}
