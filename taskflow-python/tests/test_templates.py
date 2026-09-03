"""
Email recipient rules.

These are the rules from the spec, and they are the reason this service exists.
No AWS or database needed: templates.render() takes a dict and returns an Email,
so directory lookups are the only thing to stub.
"""

from uuid import uuid4

import pytest

from notification_service import directory, templates
from notification_service.directory import Person

ADMIN = Person(id=uuid4(), email="admin@taskflow.dev", name="Asha Rao", role="ADMIN")
ASSIGNEE = Person(id=uuid4(), email="dev1@taskflow.dev", name="Rohit Menon", role="MEMBER")
ORG_ID = str(uuid4())


@pytest.fixture
def stub_directory(monkeypatch):
    monkeypatch.setattr(directory, "admins_of", lambda org_id: [ADMIN])
    monkeypatch.setattr(
        directory,
        "find",
        lambda user_id: ASSIGNEE if user_id == str(ASSIGNEE.id) else None,
    )


def test_invite_goes_to_the_invitee():
    email = templates.render({
        "eventType": "user.invited",
        "email": "newdev@taskflow.dev",
        "orgName": "Northwind Labs",
        "role": "MEMBER",
        "acceptUrl": "http://localhost:5173/accept-invite?token=abc",
        "expiresAt": "2026-09-10T12:00:00+00:00",
    })

    assert email.to == "newdev@taskflow.dev"
    assert email.cc == []
    assert "accept-invite?token=abc" in email.body


def test_assigned_goes_to_the_assignee_with_no_cc(stub_directory):
    email = templates.render({
        "eventType": "task.assigned",
        "assigneeId": str(ASSIGNEE.id),
        "title": "Rotate RDS credentials",
        "severity": "CRITICAL",
        "dueDate": "2026-09-05T17:00:00+00:00",
    })

    assert email.to == ASSIGNEE.email
    assert email.cc == []
    assert "Rohit Menon" in email.body
    assert "CRITICAL" in email.body


def test_completed_goes_to_the_admin_with_the_assignee_in_cc(stub_directory):
    email = templates.render({
        "eventType": "task.completed",
        "orgId": ORG_ID,
        "assigneeId": str(ASSIGNEE.id),
        "completedBy": str(ASSIGNEE.id),
        "title": "Rotate RDS credentials",
        "severity": "CRITICAL",
    })

    assert email.to == ADMIN.email
    assert email.cc == [ASSIGNEE.email]


def test_stalled_goes_to_the_assignee_with_the_admin_in_cc(stub_directory):
    email = templates.render({
        "eventType": "task.stalled",
        "orgId": ORG_ID,
        "assigneeId": str(ASSIGNEE.id),
        "title": "Rotate RDS credentials",
        "severity": "CRITICAL",
        "dueDate": "2026-09-05T17:00:00+00:00",
    })

    assert email.to == ASSIGNEE.email
    assert email.cc == [ADMIN.email]


def test_an_unresolvable_assignee_is_skipped_not_failed(stub_directory):
    # A deleted assignee is not an error. Raising something else here would send
    # the message to the DLQ five times over and make DLQ alarms meaningless.
    with pytest.raises(templates.SkipEvent):
        templates.render({
            "eventType": "task.assigned",
            "assigneeId": str(uuid4()),
            "title": "Orphaned task",
        })


def test_an_unknown_event_type_is_skipped():
    with pytest.raises(templates.SkipEvent):
        templates.render({"eventType": "task.something.new"})
