# vim: ts=4:sw=4:et:ft=hcl

resource "aws_sns_topic" "alerts" {
  name = "${var.tag_owner}-${var.environment}-Alerts"
}
