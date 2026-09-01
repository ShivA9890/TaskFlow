"""
Consumes the notify queue and sends one email per event.

SQS delivers at least once, so a handler must tolerate seeing the same message
twice. Sending is not idempotent, so the ordering matters: send first, then
delete. That biases towards a rare duplicate email over a silently lost one,
which is the right trade for a notification.
"""

import json
import signal

from botocore.exceptions import ClientError

from common.aws import client
from common.logging import configure
from common.settings import settings

from .templates import SkipEvent, render
from .transport import build as build_transport

log = configure("notification-service")

_running = True


def _stop(signum, frame) -> None:
    global _running
    log.info("Shutdown requested, finishing current batch")
    _running = False


def handle_message(body: str, transport) -> None:
    event = json.loads(body)
    event_type = event.get("eventType", "unknown")

    try:
        email = render(event)
    except SkipEvent as exc:
        # Nothing to send. Log and let the caller delete the message, otherwise
        # it retries five times and lands in the DLQ for no reason.
        log.info(
            "Skipped event",
            extra={"extra_fields": {"eventType": event_type, "reason": str(exc)}},
        )
        return

    transport.send(email)
    log.info(
        "Email sent",
        extra={
            "extra_fields": {
                "eventType": event_type,
                "to": email.to,
                "ccCount": len(email.cc),
            }
        },
    )


def run_once(sqs, transport) -> int:
    cfg = settings()
    response = sqs.receive_message(
        QueueUrl=cfg.notify_queue_url,
        MaxNumberOfMessages=10,
        # Long polling: one call waits for work instead of returning empty
        # immediately. Fewer requests, lower latency, lower cost.
        WaitTimeSeconds=cfg.sqs_wait_seconds,
    )
    messages = response.get("Messages", [])

    for message in messages:
        try:
            handle_message(message["Body"], transport)
        except Exception:
            # Leave the message on the queue. After maxReceiveCount it moves to
            # the DLQ, where it can be inspected instead of disappearing.
            log.exception("Handler failed, leaving message for retry")
            continue

        try:
            sqs.delete_message(
                QueueUrl=cfg.notify_queue_url, ReceiptHandle=message["ReceiptHandle"]
            )
        except ClientError:
            log.exception("Could not delete message after handling")

    return len(messages)


def main() -> None:
    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)

    sqs = client("sqs")
    transport = build_transport()
    log.info("Notification service started")

    while _running:
        run_once(sqs, transport)

    log.info("Notification service stopped")


if __name__ == "__main__":
    main()
