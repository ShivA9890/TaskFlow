"""
Resolves user IDs to names and email addresses.

The event payloads carry IDs only, because the producer should not have to know
what a consumer will need. This reads identity-service's database directly,
which is a deliberate shortcut: adding an internal HTTP endpoint would be the
cleaner boundary, but this is a background worker with no user context and the
read is trivially simple.
"""

from dataclasses import dataclass
from uuid import UUID

import psycopg
from psycopg.rows import dict_row

from common.settings import settings


@dataclass(frozen=True)
class Person:
    id: UUID
    email: str
    name: str
    role: str


def _connect():
    return psycopg.connect(settings().identity_db_dsn, row_factory=dict_row)


def find(user_id: str | None) -> Person | None:
    if not user_id:
        return None
    with _connect() as conn, conn.cursor() as cur:
        cur.execute(
            "select id, email, name, role from users where id = %s", (user_id,)
        )
        row = cur.fetchone()
    return _to_person(row) if row else None


def admins_of(org_id: str) -> list[Person]:
    with _connect() as conn, conn.cursor() as cur:
        cur.execute(
            """
            select id, email, name, role
            from users
            where org_id = %s and role = 'ADMIN' and status = 'ACTIVE'
            order by created_at
            """,
            (org_id,),
        )
        rows = cur.fetchall()
    return [_to_person(row) for row in rows]


def org_name(org_id: str) -> str:
    with _connect() as conn, conn.cursor() as cur:
        cur.execute("select name from organizations where id = %s", (org_id,))
        row = cur.fetchone()
    return row["name"] if row else "your workspace"


def _to_person(row: dict) -> Person:
    return Person(
        id=row["id"], email=row["email"], name=row["name"], role=row["role"]
    )
