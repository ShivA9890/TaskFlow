
"""
Drains the outbox tables in both Postgres databases into SNS.

The Java services write an event row inside the same transaction as the state
change that caused it, so the two commit together. This process ships those
rows onward. If it dies mid-batch, unpublished rows are simply picked up on the
next pass — nothing is lost, and nothing needs a distributed transaction.
"""

import json
import signal
import time

import psycopg

from common.aws import client
from common.logging import configure
from common.settings import settings

log = configure("outbox-poller")

SOURCES = [
    ("identity", "identity_db_dsn"),
    ("tasks", "tasks_db_dsn"),
]

_running = True


def _stop(signum, frame) -> None:
    """Finish the current batch, then exit. Matters for SIGTERM from Kubernetes."""
    global _running
    log.info("Shutdown requested, finishing current batch")
    _running = False


def publish_batch(source: str, dsn: str, sns, topic_arn: str) -> int:
    cfg = settings()
    published = 0

    with psycopg.connect(dsn) as conn:
        with conn.cursor() as cur:
            # SKIP LOCKED lets several replicas run at once without any two
            # picking up the same row.
            cur.execute(
                """
                select id, event_type, payload
                from outbox
                where published_at is null
                order by created_at
                limit %s
                for update skip locked
                """,
                (cfg.outbox_batch_size,),
            )
            rows = cur.fetchall()

            for event_id, event_type, payload in rows:
                body = payload if isinstance(payload, str) else json.dumps(payload)
                try:
                    sns.publish(
                        TopicArn=topic_arn,
                        Message=body,
                        # SNS filter policies read message attributes, not the
                        # body. Both carry eventType so consumers can use either.
                        MessageAttributes={
                            "eventType": {"DataType": "String", "StringValue": event_type},
                            "source": {"DataType": "String", "StringValue": source},
                        },
                    )
                except Exception:
                    # Leave published_at null and retry on the next pass rather
                    # than failing the whole batch.
                    log.exception(
                        "Publish failed, will retry",
                        extra={"extra_fields": {"eventId": str(event_id)}},
                    )
                    continue

                cur.execute(
                    "update outbox set published_at = now() where id = %s", (event_id,)
                )
                published += 1
                log.info(
                    "Published event",
                    extra={"extra_fields": {"eventType": event_type, "source": source}},
                )

        conn.commit()

    return published
# just a comment

def run_once() -> int:
    cfg = settings()
    sns = client("sns")
    total = 0
    for source, dsn_attr in SOURCES:
        dsn = getattr(cfg, dsn_attr)
        if not dsn:
            continue
        try:
            total += publish_batch(source, dsn, sns, cfg.events_topic_arn)
        except Exception:
            log.exception("Batch failed", extra={"extra_fields": {"source": source}})
    return total


def main() -> None:
    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)

    log.info("Outbox poller started")
    while _running:
        published = run_once()
        if published == 0:
            time.sleep(settings().outbox_poll_seconds)
    log.info("Outbox poller stopped")


if __name__ == "__main__":
    main()