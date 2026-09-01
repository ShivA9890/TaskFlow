"""
Email rendering. One function per event, each returning who it goes to and what
it says. The recipient rules come straight from the spec:

    user.invited    -> the invitee
    task.assigned   -> the assignee
    task.completed  -> the admin, assignee in CC
    task.stalled    -> the assignee, admins in CC
"""

from dataclasses import dataclass, field
from datetime import datetime

from jinja2 import Environment

from common.settings import settings

from . import directory

_env = Environment(autoescape=False, trim_blocks=True, lstrip_blocks=True)


@dataclass
class Email:
    to: str
    subject: str
    body: str
    cc: list[str] = field(default_factory=list)


class SkipEvent(Exception):
    """Raised when an event has no valid recipient. Not an error — just nothing to send."""


def _pretty_date(iso: str | None) -> str:
    if not iso:
        return "no due date"
    try:
        return datetime.fromisoformat(iso.replace("Z", "+00:00")).strftime(
            "%d %b %Y, %H:%M UTC"
        )
    except ValueError:
        return iso


_INVITED = _env.from_string(
    """
Hello,

You have been invited to join {{ org_name }} on TaskFlow as {{ role|lower }}.

Set up your account here:
{{ accept_url }}

This link expires on {{ expires_at }}.
""".strip()
)

_ASSIGNED = _env.from_string(
    """
Hello {{ assignee_name }},

A task has been assigned to you.

  {{ title }}
  Severity: {{ severity }}
  Due: {{ due_date }}

Open your board: {{ board_url }}
""".strip()
)

_COMPLETED = _env.from_string(
    """
Hello {{ admin_name }},

{{ actor_name }} moved a task into a completed column.

  {{ title }}
  Severity: {{ severity }}

Open your board: {{ board_url }}
""".strip()
)

_STALLED = _env.from_string(
    """
Hello {{ assignee_name }},

This task is due soon and has not moved out of the first column.

  {{ title }}
  Severity: {{ severity }}
  Due: {{ due_date }}

Open your board: {{ board_url }}
""".strip()
)


def render(event: dict) -> Email:
    event_type = event.get("eventType")
    handlers = {
        "user.invited": _render_invited,
        "task.assigned": _render_assigned,
        "task.completed": _render_completed,
        "task.stalled": _render_stalled,
    }
    handler = handlers.get(event_type)
    if handler is None:
        raise SkipEvent(f"No email defined for {event_type}")
    return handler(event)


def _board_url(event: dict) -> str:
    board_id = event.get("boardId")
    base = settings().app_base_url
    return f"{base}/board/{board_id}" if board_id else f"{base}/board"


def _render_invited(event: dict) -> Email:
    email = event.get("email")
    if not email:
        raise SkipEvent("Invite event carries no email address")

    return Email(
        to=email,
        subject=f"You have been invited to {event.get('orgName', 'TaskFlow')}",
        body=_INVITED.render(
            org_name=event.get("orgName", "TaskFlow"),
            role=event.get("role", "member"),
            accept_url=event.get("acceptUrl", ""),
            expires_at=_pretty_date(event.get("expiresAt")),
        ),
    )


def _render_assigned(event: dict) -> Email:
    assignee = directory.find(event.get("assigneeId"))
    if assignee is None:
        raise SkipEvent("Assigned event has no resolvable assignee")

    return Email(
        to=assignee.email,
        subject=f"Assigned to you: {event.get('title', 'a task')}",
        body=_ASSIGNED.render(
            assignee_name=assignee.name,
            title=event.get("title", ""),
            severity=event.get("severity", "MEDIUM"),
            due_date=_pretty_date(event.get("dueDate")),
            board_url=_board_url(event),
        ),
    )


def _render_completed(event: dict) -> Email:
    org_id = event.get("orgId")
    admins = directory.admins_of(org_id) if org_id else []
    if not admins:
        raise SkipEvent("Completed event has no admin to notify")

    primary = admins[0]
    assignee = directory.find(event.get("assigneeId"))
    actor = directory.find(event.get("completedBy"))

    # Spec: goes to the admin, with the assignee in CC.
    cc = [assignee.email] if assignee and assignee.email != primary.email else []

    return Email(
        to=primary.email,
        cc=cc,
        subject=f"Completed: {event.get('title', 'a task')}",
        body=_COMPLETED.render(
            admin_name=primary.name,
            actor_name=actor.name if actor else "Someone",
            title=event.get("title", ""),
            severity=event.get("severity", "MEDIUM"),
            board_url=_board_url(event),
        ),
    )


def _render_stalled(event: dict) -> Email:
    assignee = directory.find(event.get("assigneeId"))
    if assignee is None:
        raise SkipEvent("Stalled event has no resolvable assignee")

    org_id = event.get("orgId")
    admins = directory.admins_of(org_id) if org_id else []

    # Spec: goes to the assignee, with admins in CC.
    cc = [a.email for a in admins if a.email != assignee.email]

    return Email(
        to=assignee.email,
        cc=cc,
        subject=f"Due soon with no progress: {event.get('title', 'a task')}",
        body=_STALLED.render(
            assignee_name=assignee.name,
            title=event.get("title", ""),
            severity=event.get("severity", "MEDIUM"),
            due_date=_pretty_date(event.get("dueDate")),
            board_url=_board_url(event),
        ),
    )
