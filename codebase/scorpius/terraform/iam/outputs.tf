# vim: ts=4:sw=4:et:ft=hcl

output "bastion_instance_profile_name" {
    value = "${aws_iam_instance_profile.bastion_instance_profile.*.name}"
}

output "nat_instance_profile_name" {
    value = "${aws_iam_instance_profile.nat_instance_profile.*.name}"
}

output "bootstrap_instance_profile_name" {
    value = "${aws_iam_instance_profile.bootstrap_instance_profile.*.name}"
}
output "master_instance_profile_name" {
    value = "${aws_iam_instance_profile.master_instance_profile.*.name}"
}
output "slave_instance_profile_name" {
    value = "${aws_iam_instance_profile.slave_instance_profile.*.name}"
}
output "captain_instance_profile_name" {
    value = "${aws_iam_instance_profile.captain_instance_profile.*.name}"
}
output "deepcortex_main_instance_profile_name" {
    value = "${aws_iam_instance_profile.deepcortex_main_instance_profile.*.name}"
}
output "deepcortex_main_role_arn" {
    value = "${aws_iam_role.deepcortex_main_role.*.arn}"
}
output "app_access_key" {
    value = "${aws_iam_access_key.app.*.id}"
}
output "app_secret_key" {
    value = "${aws_iam_access_key.app.*.secret}"
}
output "app_user_name" {
    value = "${aws_iam_user.app.*.name}"
}

output "deepcortex_jump_role_arn" {
    value = "${aws_iam_role.deepcortex_jump_role.arn}"
}
