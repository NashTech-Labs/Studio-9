# vim: ts=4:sw=4:et:ft=hcl

resource "aws_cloudwatch_metric_alarm" "bootstrap_cpu_greater_than" {
  alarm_name          = "bootstrap_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.bootstrap_asg_name}"
  }

  alarm_description         = "This metric monitors EC2 CPU Utilization for Bootstrap instance"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "master_cpu_greater_than" {
  alarm_name          = "master_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.master_asg_name}"
  }

  alarm_description         = "This metric monitors EC2 CPU Utilization for Master instance"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "slave_cpu_greater_than" {
  alarm_name          = "slave_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.slave_asg_name}"
  }

  alarm_description         = "This metric monitors EC2 CPU Utilization for Slave instances"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "public_slave_cpu_greater_than" {
  alarm_name          = "public_slave_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.public_slave_asg_name}"
  }

  alarm_description         = "This metric monitors EC2 CPU Utilization for Public Slave instances"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "captain_cpu_greater_than" {
  alarm_name          = "captain_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"

  dimensions {
    AutoScalingGroupName = "${data.terraform_remote_state.platform.captain_asg_name}"
  }

  alarm_description         = "This metric monitors EC2 CPU Utilization for Captain instance"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}