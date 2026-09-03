"""
Test configuration.

The environment is set at import time, before anything imports common.settings.
That matters because settings() is lru_cached and reads .env — without this,
tests on a developer machine would talk to LocalStack instead of the mock.
"""

import os

os.environ["AWS_ENDPOINT_URL"] = ""          # empty -> boto3 uses real endpoints
os.environ["AWS_REGION"] = "ap-south-1"
os.environ["AWS_ACCESS_KEY_ID"] = "testing"
os.environ["AWS_SECRET_ACCESS_KEY"] = "testing"
os.environ["AWS_SECURITY_TOKEN"] = "testing"
os.environ["AWS_SESSION_TOKEN"] = "testing"
os.environ["ACTIVITY_TABLE"] = "taskflow-activity-test"
os.environ["MAIL_FROM"] = "taskflow@example.com"
os.environ["APP_BASE_URL"] = "http://localhost:5173"

import pytest  # noqa: E402
from moto import mock_aws  # noqa: E402


@pytest.fixture
def dynamodb_table():
    """A real DynamoDB API backed by moto, with the production key layout."""
    with mock_aws():
        import boto3

        client = boto3.client("dynamodb", region_name="ap-south-1")
        client.create_table(
            TableName="taskflow-activity-test",
            KeySchema=[
                {"AttributeName": "pk", "KeyType": "HASH"},
                {"AttributeName": "sk", "KeyType": "RANGE"},
            ],
            AttributeDefinitions=[
                {"AttributeName": "pk", "AttributeType": "S"},
                {"AttributeName": "sk", "AttributeType": "S"},
                {"AttributeName": "gsi1pk", "AttributeType": "S"},
                {"AttributeName": "gsi1sk", "AttributeType": "S"},
            ],
            GlobalSecondaryIndexes=[
                {
                    "IndexName": "gsi1",
                    "KeySchema": [
                        {"AttributeName": "gsi1pk", "KeyType": "HASH"},
                        {"AttributeName": "gsi1sk", "KeyType": "RANGE"},
                    ],
                    "Projection": {"ProjectionType": "ALL"},
                }
            ],
            BillingMode="PAY_PER_REQUEST",
        )
        yield
