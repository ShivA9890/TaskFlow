"""
DynamoDB layer. Uses moto, so this exercises the real key layout, the real GSI,
and the real conditional write.
"""

from uuid import uuid4

from activity_service import store

TASK_ID = str(uuid4())
ORG_ID = str(uuid4())
USER_ID = str(uuid4())


def test_activity_is_recorded_and_read_back(dynamodb_table):
    store.record_activity(
        task_id=TASK_ID, org_id=ORG_ID, actor_id=USER_ID, action="task.moved"
    )

    entries = store.list_activity(TASK_ID)
    assert len(entries) == 1
    assert entries[0]["action"] == "task.moved"


def test_the_same_idempotency_key_records_once(dynamodb_table):
    # SQS delivers at least once, so a redelivered message must not duplicate
    # the activity row. The conditional write on the SEEN# marker is what
    # guarantees that.
    for _ in range(3):
        store.record_activity(
            task_id=TASK_ID,
            org_id=ORG_ID,
            actor_id=USER_ID,
            action="task.completed",
            idempotency_key="sqs-message-123",
        )

    assert len(store.list_activity(TASK_ID)) == 1


def test_comments_and_activity_do_not_bleed_into_each_other(dynamodb_table):
    # Both live under the same partition key, separated only by sort-key prefix.
    store.add_comment(
        task_id=TASK_ID, org_id=ORG_ID, author_id=USER_ID, body="Blocked on rotation."
    )
    store.record_activity(
        task_id=TASK_ID, org_id=ORG_ID, actor_id=USER_ID, action="task.moved"
    )

    comments = store.list_comments(TASK_ID)
    activity = store.list_activity(TASK_ID)

    assert len(comments) == 1
    assert comments[0]["body"] == "Blocked on rotation."
    assert len(activity) == 1
    assert activity[0]["action"] == "task.moved"


def test_the_user_feed_reads_through_the_gsi(dynamodb_table):
    other_task = str(uuid4())
    store.record_activity(
        task_id=TASK_ID, org_id=ORG_ID, actor_id=USER_ID, action="task.moved"
    )
    store.record_activity(
        task_id=other_task, org_id=ORG_ID, actor_id=USER_ID, action="task.completed"
    )

    feed = store.user_feed(USER_ID)
    assert len(feed) == 2


def test_custom_fields_round_trip(dynamodb_table):
    store.put_custom_fields(
        task_id=TASK_ID, org_id=ORG_ID, fields={"sprint": "24.3", "storyPoints": 5}
    )

    fields = store.get_custom_fields(TASK_ID)
    assert fields["sprint"] == "24.3"


def test_missing_custom_fields_returns_empty_not_an_error(dynamodb_table):
    assert store.get_custom_fields(str(uuid4())) == {}

