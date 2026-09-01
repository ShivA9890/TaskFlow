"""
Finds tasks that are due soon and have not moved, then publishes task.stalled.

Runs as either a Lambda handler or a plain process, so the same module works as
an EventBridge-triggered Lambda or a Kubernetes CronJob. Pick one — this exists
so the choice is yours, not so you build both.

Deliberately does not query the database. It asks task-service, which keeps the
tasks database credentials in exactly one place and the "what counts as stalled"
rule in exactly one place too.
"""

import json
import os
from datetime import datetime, timezone

import httpx

from common.aws import client
from common.logging import configure
from common.settings import settings

log = configure("reminder-job")


def fetch_stalled() -> list[dict]:
    cfg = settings()
    response = httpx.get(
        f"{cfg.task_service_url}/api/v1/internal/tasks/stalled",
        headers={"X-Internal-Token": cfg.internal_service_token},
        timeout=15.0,
    )
    response.raise_for_status()
    return response.json()


def publish(tasks: list[dict]) -> int:
    if not tasks:
        return 0

    cfg = settings()
    sns = client("sns")
    sent = 0

    for task in tasks:
        payload = {
            "eventType": "task.stalled",
            "taskId": task["id"],
            "orgId": task["orgId"],
            "title": task["title"],
            "assigneeId": task["assigneeId"],
            "severity": task["severity"],
            "dueDate": task["dueDate"],
            "detectedAt": datetime.now(timezone.utc).isoformat(),
        }
        try:
            sns.publish(
                TopicArn=cfg.events_topic_arn,
                Message=json.dumps(payload),
                MessageAttributes={
                    "eventType": {"DataType": "String", "StringValue": "task.stalled"},
                    "source": {"DataType": "String", "StringValue": "reminder-job"},
                },
            )
            sent += 1
        except Exception:
            log.exception(
                "Publish failed",
                extra={"extra_fields": {"taskId": task["id"]}},
            )

    return sent


def run() -> dict:
    try:
        tasks = fetch_stalled()
    except httpx.HTTPError:
        log.exception("Could not reach task-service")
        raise

    sent = publish(tasks)
    log.info(
        "Reminder run complete",
        extra={"extra_fields": {"found": len(tasks), "published": sent}},
    )
    return {"found": len(tasks), "published": sent}


def lambda_handler(event, context) -> dict:
    """Entry point for AWS Lambda, triggered by an EventBridge schedule."""
    return run()


def main() -> None:
    """Entry point for a Kubernetes CronJob or a manual run."""
    result = run()
    print(json.dumps(result), flush=True)
    # Non-zero on nothing-found would make a CronJob look broken, so always exit 0.
    raise SystemExit(0)


if __name__ == "__main__":
    main()
