# vim: ts=4:sw=4:et:ft=hcl

###############################################################################
# Obtain latest AMI

data "aws_ami" "ami" {
    most_recent = true

    #filter {
    #    name   = "owner-alias"
    #    values = [ "${var.ami_owner_alias}" ]
    #}

    filter {
        name   = "name"
        values = [ "${var.ami_name}" ]
    }

    #filter {
    #    name   = "architecture"
    #    values = [ "${var.ami_architecture}" ]
    #}

}


###############################################################################
# Launch configuration

resource "aws_launch_configuration" "lc" {
    count                = "${var.asg_count}"
    name_prefix          = "${var.lc_name_prefix}"
    image_id             = "${var.lc_ami_id != "" ? var.lc_ami_id : data.aws_ami.ami.id}"
    instance_type        = "${var.lc_instance_type}"
    key_name             = "${var.lc_key_name}"
    security_groups      = [ "${var.lc_security_groups}" ]
    user_data            = "${var.lc_user_data}"
    iam_instance_profile = "${var.lc_iam_instance_profile}"

    enable_monitoring = "${var.lc_monitoring}"
    ebs_optimized     = "${var.lc_ebs_optimized}"

    #root_block_device      = "${lc_root_block_device}"
    #ebs_block_device       = "${lc_ebs_block_devices}"
    #ephemeral_block_device = "${lc_ephemeral_block_devices}"

    lifecycle {
        create_before_destroy = true
    }
}

###############################################################################
# Autoscaling group

resource "aws_autoscaling_group" "asg" {
    count                = "${var.asg_count}"
    name                      = "${var.asg_name}"
    vpc_zone_identifier       = [ "${var.asg_subnet_ids}" ]
    desired_capacity          = "${var.asg_desired_capacity}"
    min_size                  = "${var.asg_min_size}"
    max_size                  = "${var.asg_max_size}"
    default_cooldown          = "${var.asg_default_cooldown}"
    health_check_grace_period = "${var.asg_health_check_grace_period}"
    health_check_type         = "${var.asg_check_type}"
    force_delete              = "${var.asg_force_delete}"
    launch_configuration      = "${aws_launch_configuration.lc.name}"
    load_balancers            = [ "${var.asg_load_balancers}" ]
    #target_group_arns         = [ "${var.asg_target_groups}" ]
    enabled_metrics           = [ "${var.asg_enabled_metrics}" ]


    tags = ["${concat(
        list(map("key", "Name", "value", var.asg_name_tag, "propagate_at_launch", false),),
        list(map("key", "Name", "value", var.instance_name_tag, "propagate_at_launch", true),),
        list(map("key", "Role", "value", var.instance_role_tag, "propagate_at_launch", true),),
        var.tags_asg)
    }"]
}
