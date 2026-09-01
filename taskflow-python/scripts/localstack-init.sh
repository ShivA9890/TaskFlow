#!/usr/bin/env bash
set -euo pipefail

AWS="aws --endpoint-url=http://localhost:4566 --region ap-south-1"
ACCOUNT=000000000000
TOPIC_ARN="arn:aws:sns:ap-south-1:${ACCOUNT}:taskflow-events"

echo "Creating SNS topic..."
$AWS sns create-topic --name taskflow-events >/dev/null

echo "Creating SQS queues..."
for q in taskflow-notify taskflow-notify-dlq taskflow-activity taskflow-activity-dlq; do
  $AWS sqs create-queue --queue-name "$q" >/dev/null
done

echo "Wiring dead letter queues..."
for q in notify activity; do
  DLQ_ARN="arn:aws:sqs:ap-south-1:${ACCOUNT}:taskflow-${q}-dlq"
  $AWS sqs set-queue-attributes \
    --queue-url "http://localhost:4566/${ACCOUNT}/taskflow-${q}" \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\",\"VisibilityTimeout\":\"60\"}" >/dev/null
done

echo "Subscribing queues to the topic..."
for q in notify activity; do
  $AWS sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol sqs \
    --notification-endpoint "arn:aws:sqs:ap-south-1:${ACCOUNT}:taskflow-${q}" \
    --attributes '{"RawMessageDelivery":"true"}' >/dev/null
done

echo "Creating DynamoDB table..."
$AWS dynamodb create-table \
  --table-name taskflow-activity \
  --attribute-definitions \
      AttributeName=pk,AttributeType=S \
      AttributeName=sk,AttributeType=S \
      AttributeName=gsi1pk,AttributeType=S \
      AttributeName=gsi1sk,AttributeType=S \
  --key-schema \
      AttributeName=pk,KeyType=HASH \
      AttributeName=sk,KeyType=RANGE \
  --global-secondary-indexes \
      'IndexName=gsi1,KeySchema=[{AttributeName=gsi1pk,KeyType=HASH},{AttributeName=gsi1sk,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null 2>&1 || echo "  (table already exists)"

echo "Verifying sender address..."
$AWS ses verify-email-identity --email-address taskflow@example.com

echo "Done."