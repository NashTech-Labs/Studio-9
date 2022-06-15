# vim: ts=4:sw=4:et:ft=hcl

resource "aws_cloudwatch_metric_alarm" "bootstrap_elb_low_running_nodes" {
  alarm_name                = "bootstrap_elb_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "HealthyHostCount"
  namespace                 = "AWS/ELB"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.bootstrap_asg_desired_capacity}"

  dimensions {
    LoadBalancerName = "${data.terraform_remote_state.platform.bootstrap_elb_id}"
  }

  alarm_description         = "This metric monitors amount of nodes on Bootstrap ELB"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "master_elb_internal_low_running_nodes" {
  alarm_name                = "master_elb_internal_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "HealthyHostCount"
  namespace                 = "AWS/ELB"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.master_asg_desired_capacity}"

  dimensions {
    LoadBalancerName = "${data.terraform_remote_state.platform.master_elb_id}"
  }

  alarm_description         = "This metric monitors amount of nodes on Master ELB"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "baile_elb_low_running_nodes" {
  alarm_name                = "baile_elb_low_running_nodes"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "2"
  metric_name               = "HealthyHostCount"
  namespace                 = "AWS/ELB"
  period                    = "60"
  statistic                 = "Average"
  threshold                 = "${var.public_slave_asg_desired_capacity}"

  dimensions {
    LoadBalancerName = "${data.terraform_remote_state.platform.baile_elb_id}"
  }

  alarm_description         = "This metric monitors amount of nodes on Baile ELB"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}
