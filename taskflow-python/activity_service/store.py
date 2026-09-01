"""
DynamoDB access for the activity table.

Key layout:
    pk = TASK#<task_id>
    sk = ACT#<timestamp>#<id>      activity entries
         CMT#<timestamp>#<id>      comments
         SEEN#<id>                 idempotency markers
         FIELDS                    custom fields

    gsi1pk = USER#<user_id>        per-user feed
    gsi1sk = ACT#<timestamp>

Sort keys are timestamp-prefixed, so querying with ScanIndexForward=False
returns newest-first without sorting in the application.
"""

import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from boto3.dynamodb.conditions import Key
from botocore.exceptions import ClientError

from common.aws import resource
from common.settings import settings

ACTIVITY_TTL_DAYS = 90


def _table():
    return resource("dynamodb").Table(settings().activity_table)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _ttl() -> int:
    return int((datetime.now(timezone.utc) + timedelta(days=ACTIVITY_TTL_DAYS)).timestamp())


def record_activity(
    *,
    task_id: str,
    org_id: str,
    actor_id: str | None,
    action: str,
    detail: dict[str, Any] | None = None,
    idempotency_key: str | None = None,
) -> dict:
    """
    Append an activity entry.

    SQS delivers at least once, so callers pass an idempotency_key derived from
    the message. A conditional write on a marker item makes a redelivery a no-op
    instead of a duplicate row.
    """
    timestamp = _now()
    entry_id = idempotency_key or str(uuid.uuid4())
    table = _table()

    try:
        table.put_item(
            Item={"pk": f"TASK#{task_id}", "sk": f"SEEN#{entry_id}", "expires_at": _ttl()},
            ConditionExpression="attribute_not_exists(pk) AND attribute_not_exists(sk)",
        )
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "ConditionalCheckFailedException":
            return {"deduplicated": True, "entry_id": entry_id}
        raise

    item: dict[str, Any] = {
        "pk": f"TASK#{task_id}",
        "sk": f"ACT#{timestamp}#{entry_id}",
        "entry_id": entry_id,
        "task_id": task_id,
        "org_id": org_id,
        "actor_id": actor_id,
        "action": action,
        "detail": detail or {},
        "created_at": timestamp,
        "expires_at": _ttl(),
    }
    if actor_id:
        item["gsi1pk"] = f"USER#{actor_id}"
        item["gsi1sk"] = f"ACT#{timestamp}"

    table.put_item(Item=item)
    return item


def add_comment(*, task_id: str, org_id: str, author_id: str, body: str) -> dict:
    timestamp = _now()
    comment_id = str(uuid.uuid4())
    item = {
        "pk": f"TASK#{task_id}",
        "sk": f"CMT#{timestamp}#{comment_id}",
        "comment_id": comment_id,
        "task_id": task_id,
        "org_id": org_id,
        "author_id": author_id,
        "body": body,
        "created_at": timestamp,
        "gsi1pk": f"USER#{author_id}",
        "gsi1sk": f"ACT#{timestamp}",
    }
    _table().put_item(Item=item)
    return item


def _list_by_prefix(task_id: str, prefix: str, limit: int) -> list[dict]:
    response = _table().query(
        KeyConditionExpression=Key("pk").eq(f"TASK#{task_id}") & Key("sk").begins_with(prefix),
        ScanIndexForward=False,
        Limit=limit,
    )
    return response.get("Items", [])


def list_activity(task_id: str, limit: int = 50) -> list[dict]:
    return _list_by_prefix(task_id, "ACT#", limit)


def list_comments(task_id: str, limit: int = 50) -> list[dict]:
    return _list_by_prefix(task_id, "CMT#", limit)


def user_feed(user_id: str, limit: int = 50) -> list[dict]:
    response = _table().query(
        IndexName="gsi1",
        KeyConditionExpression=Key("gsi1pk").eq(f"USER#{user_id}"),
        ScanIndexForward=False,
        Limit=limit,
    )
    return response.get("Items", [])


def put_custom_fields(*, task_id: str, org_id: str, fields: dict[str, Any]) -> dict:
    item = {
        "pk": f"TASK#{task_id}",
        "sk": "FIELDS",
        "task_id": task_id,
        "org_id": org_id,
        "fields": fields,
        "updated_at": _now(),
    }
    _table().put_item(Item=item)
    return item


def get_custom_fields(task_id: str) -> dict[str, Any]:
    response = _table().get_item(Key={"pk": f"TASK#{task_id}", "sk": "FIELDS"})
    return response.get("Item", {}).get("fields", {})
