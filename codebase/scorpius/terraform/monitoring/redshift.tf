# vim: ts=4:sw=4:et:ft=hcl

resource "aws_cloudwatch_metric_alarm" "redshift_cpu_greater_than" {
  alarm_name          = "redshift_cpu_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/Redshift"
  period              = "120"
  statistic           = "Average"
  threshold           = "70"

  dimensions {
    ClusterIdentifier = "${data.terraform_remote_state.redshift.redshift_cluster_identifier}"
  }

  alarm_description         = "This metric monitors Redshift CPU Utilization"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}

resource "aws_cloudwatch_metric_alarm" "redshift_disk_greater_than" {
  alarm_name          = "redshift_disk_greater_than"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "PercentageDiskSpaceUsed"
  namespace           = "AWS/Redshift"
  period              = "120"
  statistic           = "Average"
  threshold           = "70"

  dimensions {
    ClusterIdentifier = "${data.terraform_remote_state.redshift.redshift_cluster_identifier}"
  }

  alarm_description         = "This metric monitors Redshift Disk Utilization"
  alarm_actions             = [ "${aws_sns_topic.alerts.arn}" ]
  ok_actions                = [ "${aws_sns_topic.alerts.arn}" ]
}
