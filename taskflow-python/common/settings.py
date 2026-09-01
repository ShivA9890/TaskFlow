from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All configuration comes from the environment. Nothing is hardcoded per env."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Set locally to point at LocalStack. Absent in AWS, where boto3 resolves
    # the real endpoints and reads credentials from the pod's service account.
    aws_endpoint_url: str | None = None
    aws_region: str = "ap-south-1"

    events_topic_arn: str = ""
    notify_queue_url: str = ""
    activity_queue_url: str = ""
    activity_table: str = "taskflow-activity"

    identity_jwks_url: str = ""
    identity_issuer: str = ""
    task_service_url: str = ""
    internal_service_token: str = ""

    identity_db_dsn: str = ""
    tasks_db_dsn: str = ""

    mail_from: str = "taskflow@example.com"
    app_base_url: str = "http://localhost:5173"
    mail_transport: str = "console"  # "console" or "ses"

    outbox_poll_seconds: float = 2.0
    outbox_batch_size: int = 25
    sqs_wait_seconds: int = 20


@lru_cache
def settings() -> Settings:
    return Settings()
