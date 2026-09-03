import boto3
from botocore.config import Config

from .settings import settings

# Retries with backoff. SNS and SQS throttle under load and the default of
# 3 attempts is thin for a background worker.
_BOTO_CONFIG = Config(
    retries={"max_attempts": 8, "mode": "standard"},
    connect_timeout=5,
    read_timeout=35,  # must exceed the SQS long-poll wait
)


def client(service: str):
    """
    The only place an AWS endpoint is decided.

    Locally AWS_ENDPOINT_URL points at LocalStack. In EKS it is unset, so boto3
    resolves the real endpoints and picks up credentials from IRSA. There is no
    branch on environment anywhere else in the codebase.
    """
    cfg = settings()
    return boto3.client(
        service,
        region_name=cfg.aws_region,
        endpoint_url=cfg.aws_endpoint_url or None,
        config=_BOTO_CONFIG,
    )


def resource(service: str):
    cfg = settings()
    return boto3.resource(
        service,
        region_name=cfg.aws_region,
        endpoint_url=cfg.aws_endpoint_url or None,
        config=_BOTO_CONFIG,
        # want to run so new change
    )
