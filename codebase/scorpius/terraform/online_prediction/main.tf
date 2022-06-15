# vim: ts=4:sw=4:et:ft=hcl

terraform {
    required_version = ">= 0.10.7"
    backend "s3" {}
}

#########################################################

# Retrieve IAM data
data "terraform_remote_state" "iam" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/iam/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# Retrieve s3 data
data "terraform_remote_state" "s3_buckets" {
  backend = "s3"
  config {
    bucket = "${var.tf_bucket}"
    key    = "${var.aws_region}/${var.environment}/s3_buckets/terraform.tfstate"
    region = "${var.aws_region}"
  }
}

# SNS

resource "aws_sns_topic" "online_prediction" {
  name = "online-prediction-${var.tag_owner}-${var.environment}"
}

resource "aws_sns_topic_policy" "online_prediction" {
  arn = "${aws_sns_topic.online_prediction.arn}"

  policy = <<EOF
{
    "Version": "2008-10-17",
    "Id": "s3-publish-to-sns",
    "Statement": [{
            "Effect": "Allow",
            "Principal": { "AWS" : "*" },
            "Action": [ "SNS:Publish" ],
            "Resource": "${aws_sns_topic.online_prediction.arn}",
            "Condition": {
                "ArnLike": {
                    "aws:SourceArn": "${data.terraform_remote_state.s3_buckets.apps_s3_bucket_arn}"
                }
            }
    }]
  }
EOF
}

resource "aws_s3_bucket_notification" "online_prediction" {
  bucket = "${data.terraform_remote_state.s3_buckets.apps_s3_bucket}"

  topic {
    topic_arn     = "${aws_sns_topic.online_prediction.arn}"
    events        = ["s3:ObjectCreated:*"]
    filter_prefix = "images/"
  }
}

# SQS

resource "aws_sqs_queue" "online_prediction" {
  name = "online-prediction-${var.tag_owner}-${var.environment}"
  receive_wait_time_seconds = 20
  visibility_timeout_seconds = 300
}

resource "aws_sqs_queue_policy" "online_prediction" {
  queue_url = "${aws_sqs_queue.online_prediction.id}"

  policy = <<EOF
{
    "Version":"2012-10-17",
    "Statement":[
      {
        "Effect":"Allow",
        "Principal": { "AWS": "*" },
        "Action":"sqs:SendMessage",
        "Resource":"${aws_sqs_queue.online_prediction.arn}",
        "Condition":{
          "ArnEquals":{
            "aws:SourceArn":"${aws_sns_topic.online_prediction.arn}"
          }
        }
      }
    ]
  }
EOF
}

resource "aws_sns_topic_subscription" "user_updates_sqs_target" {
  topic_arn = "${aws_sns_topic.online_prediction.arn}"
  protocol  = "sqs"
  endpoint  = "${aws_sqs_queue.online_prediction.arn}"
}

# Add IAM policy to app user for S3, SQS, SNS

resource "aws_iam_user_policy" "online_prediction" {
  name = "${var.environment}-app-user-op-policy"
  user = "${data.terraform_remote_state.iam.app_user_name}"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "sns:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${aws_sns_topic.online_prediction.arn}"
      ]
    },
    {
      "Action": [
        "sqs:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "${aws_sqs_queue.online_prediction.arn}"
      ]
    },
    {
      "Action": [
        "sqs:ListQueues"
      ],
      "Effect": "Allow",
      "Resource": "*"
    }
  ]
}
EOF
}
