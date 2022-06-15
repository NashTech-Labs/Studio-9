#!/bin/bash

# to create profile use: aws configure --profile <profile_name>

[[ "" == "$region" ]] && region=us-east-1
[[ "" == "$s3_bucket_name" ]] && s3_bucket_name=online-prediction-input-default.dev.deepcortex.ai
[[ "" == "$sns_topic_name" ]] && sns_topic_name=online-prediction-default
[[ "" == "$sqs_queue_name" ]] && sqs_queue_name=$sns_topic_name
[[ "" == "$aws_cli_profile" ]] && aws_cli_profile=deepcortex-dev
[[ "" == "$base_prefix" ]] && base_prefix=images

echo "Setup SNS topic"

sns_topic_arn=$(aws sns create-topic --profile=$aws_cli_profile  \
  --region "$region" \
  --name "$sns_topic_name" \
  --output text \
  --query 'TopicArn')
echo sns_topic_arn=$sns_topic_arn

aws sns set-topic-attributes --profile=$aws_cli_profile \
  --topic-arn "$sns_topic_arn" \
  --attribute-name Policy \
  --attribute-value '{
      "Version": "2008-10-17",
      "Id": "s3-publish-to-sns",
      "Statement": [{
              "Effect": "Allow",
              "Principal": { "AWS" : "*" },
              "Action": [ "SNS:Publish" ],
              "Resource": "'$sns_topic_arn'",
              "Condition": {
                  "ArnLike": {
                      "aws:SourceArn": "arn:aws:s3:*:*:'$s3_bucket_name'"
                  }
              }
      }]
  }'

aws s3api put-bucket-notification-configuration --profile=$aws_cli_profile \
  --region "$region" \
  --bucket "$s3_bucket_name" \
  --notification-configuration '{
    "TopicConfigurations": [
        {
          "Events": [ "s3:ObjectCreated:*" ],
          "Filter": {
            "Key": {
                "FilterRules": [ {
                    "Name": "prefix",
                    "Value": "'$base_prefix'"
                } ]
            }
          },
          "TopicArn": "'$sns_topic_arn'"
        }
    ]
  }'

echo "Setup SNS topic Done"

echo "Setup SQS queue"

  sqs_queue_url=$(aws sqs create-queue --profile=$aws_cli_profile \
  --queue-name $sqs_queue_name \
  --attributes 'ReceiveMessageWaitTimeSeconds=20,VisibilityTimeout=300'  \
  --output text \
  --query 'QueueUrl')
echo sqs_queue_url=$sqs_queue_url

sqs_queue_arn=$(aws sqs get-queue-attributes --profile=$aws_cli_profile \
  --queue-url "$sqs_queue_url" \
  --attribute-names QueueArn \
  --output text \
  --query 'Attributes.QueueArn')
echo sqs_queue_arn=$sqs_queue_arn

sqs_policy='{
    "Version":"2012-10-17",
    "Statement":[
      {
        "Effect":"Allow",
        "Principal": { "AWS": "*" },
        "Action":"sqs:SendMessage",
        "Resource":"'$sqs_queue_arn'",
        "Condition":{
          "ArnEquals":{
            "aws:SourceArn":"'$sns_topic_arn'"
          }
        }
      }
    ]
  }'
sqs_policy_escaped=$(echo $sqs_policy | perl -pe 's/"/\\"/g')
sqs_attributes='{"Policy":"'$sqs_policy_escaped'"}'
aws sqs set-queue-attributes --profile=$aws_cli_profile \
  --queue-url "$sqs_queue_url" \
  --attributes "$sqs_attributes"

aws sns subscribe --profile=$aws_cli_profile \
  --topic-arn "$sns_topic_arn" \
  --protocol sqs \
  --notification-endpoint "$sqs_queue_arn"

echo "Setup SQS queue Done"

echo "Done"