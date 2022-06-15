# vim: ts=4:sw=4:et:ft=hcl

resource "aws_cloudwatch_metric_alarm" "bootstrap_asg_low_running_nodes" {
  alarm_name                = "bootstrap_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.bootstrap_asg_desired_capacity}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.bootstrap_asg_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Bootstrap asg"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "master_asg_internal_low_running_nodes" {
  alarm_name                = "master_asg_internal_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.master_asg_desired_capacity}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.master_asg_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Master asg"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "public_slave_asg_low_running_nodes" {
  alarm_name                = "public_slave_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.public_slave_asg_desired_capacity}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.public_slave_asg_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Public Slave ASG"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "slave_asg_low_running_nodes" {
  alarm_name                = "slave_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.slave_asg_desired_capacity}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.slave_asg_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Slave ASG"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "gpu_slave_asg_low_running_nodes" {
  alarm_name                = "gpu_slave_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.gpu_slave_asg_desired_capacity}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.gpu_slave_asg_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on GPU Slave ASG"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "bastion_asg_low_running_nodes" {
  alarm_name                = "bastion_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = 1

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.vpc.asg_bastion_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Bastion ASG"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "nat_asg_low_running_nodes" {
  alarm_name                = "nat_asg_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "GroupInServiceInstances"
  namespace                 = "AWS/AutoScaling"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${length(var.azs)}"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.vpc.asg_nat_name}"
  }

  alarm_description         = "This metric monitors amount of nodes on Nat ASG"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}
