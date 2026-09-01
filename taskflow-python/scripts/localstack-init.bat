@echo off
set AWS_CMD=aws --endpoint-url=http://localhost:4566 --region ap-south-1
set ACCOUNT=000000000000
set TOPIC_ARN=arn:aws:sns:ap-south-1:%ACCOUNT%:taskflow-events

echo Creating SNS topic...
%AWS_CMD% sns create-topic --name taskflow-events >nul

echo Creating SQS queues...
for %%q in (taskflow-notify taskflow-notify-dlq taskflow-activity taskflow-activity-dlq) do (
    %AWS_CMD% sqs create-queue --queue-name %%q >nul
)

echo Wiring dead letter queues...
>notify-attrs.json echo {"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-south-1:%ACCOUNT%:taskflow-notify-dlq\",\"maxReceiveCount\":\"5\"}","VisibilityTimeout":"60"}
%AWS_CMD% sqs set-queue-attributes --queue-url http://localhost:4566/%ACCOUNT%/taskflow-notify --attributes file://notify-attrs.json >nul

>activity-attrs.json echo {"RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:ap-south-1:%ACCOUNT%:taskflow-activity-dlq\",\"maxReceiveCount\":\"5\"}","VisibilityTimeout":"60"}
%AWS_CMD% sqs set-queue-attributes --queue-url http://localhost:4566/%ACCOUNT%/taskflow-activity --attributes file://activity-attrs.json >nul

del notify-attrs.json
del activity-attrs.json

echo Subscribing queues to the topic...
%AWS_CMD% sns subscribe --topic-arn %TOPIC_ARN% --protocol sqs --notification-endpoint arn:aws:sqs:ap-south-1:%ACCOUNT%:taskflow-notify --attributes "RawMessageDelivery=true" >nul
%AWS_CMD% sns subscribe --topic-arn %TOPIC_ARN% --protocol sqs --notification-endpoint arn:aws:sqs:ap-south-1:%ACCOUNT%:taskflow-activity --attributes "RawMessageDelivery=true" >nul

echo Creating DynamoDB table...
%AWS_CMD% dynamodb create-table ^
  --table-name taskflow-activity ^
  --attribute-definitions ^
      AttributeName=pk,AttributeType=S ^
      AttributeName=sk,AttributeType=S ^
      AttributeName=gsi1pk,AttributeType=S ^
      AttributeName=gsi1sk,AttributeType=S ^
  --key-schema ^
      AttributeName=pk,KeyType=HASH ^
      AttributeName=sk,KeyType=RANGE ^
  --global-secondary-indexes ^
      "IndexName=gsi1,KeySchema=[{AttributeName=gsi1pk,KeyType=HASH},{AttributeName=gsi1sk,KeyType=RANGE}],Projection={ProjectionType=ALL}" ^
  --billing-mode PAY_PER_REQUEST >nul 2>&1
if %errorlevel% neq 0 echo   (table already exists)

echo Verifying sender address...
%AWS_CMD% ses verify-email-identity --email-address taskflow@example.com

echo Done.