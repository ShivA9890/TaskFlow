"""
Sending. Console transport prints to stdout for local work; SES transport is the
real thing. Chosen by MAIL_TRANSPORT, so the same image works in both places.
"""

from abc import ABC, abstractmethod

from common.aws import client
from common.logging import configure
from common.settings import settings

from .templates import Email

log = configure("notification-transport")


class Transport(ABC):
    @abstractmethod
    def send(self, email: Email) -> None: ...


class ConsoleTransport(Transport):
    def send(self, email: Email) -> None:
        cc = f"  cc: {', '.join(email.cc)}\n" if email.cc else ""
        print(
            f"\n{'=' * 62}\n"
            f"  to: {email.to}\n"
            f"{cc}"
            f"  subject: {email.subject}\n"
            f"{'-' * 62}\n"
            f"{email.body}\n"
            f"{'=' * 62}\n",
            flush=True,
        )


class SesTransport(Transport):
    def send(self, email: Email) -> None:
        client("ses").send_email(
            Source=settings().mail_from,
            Destination={"ToAddresses": [email.to], "CcAddresses": email.cc},
            Message={
                "Subject": {"Data": email.subject, "Charset": "UTF-8"},
                "Body": {"Text": {"Data": email.body, "Charset": "UTF-8"}},
            },
        )


def build() -> Transport:
    if settings().mail_transport.lower() == "ses":
        log.info("Using SES transport")
        return SesTransport()
    log.info("Using console transport")
    return ConsoleTransport()
