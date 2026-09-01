from typing import Any
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from common.auth import AuthError, TokenClaims, verify_token
from common.logging import configure

from . import store

log = configure("activity-service")
app = FastAPI(title="activity-service", docs_url="/docs")


def current_user(request: Request) -> TokenClaims:
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Sign in to continue.")
    try:
        return verify_token(header.removeprefix("Bearer "))
    except AuthError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


class CommentRequest(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class CustomFieldsRequest(BaseModel):
    fields: dict[str, Any]


@app.exception_handler(HTTPException)
async def http_error(_: Request, exc: HTTPException) -> JSONResponse:
    """Matches the {"message": ...} shape the frontend and Java services use."""
    return JSONResponse(status_code=exc.status_code, content={"message": exc.detail})


@app.get("/actuator/health")
def health() -> dict:
    return {"status": "UP"}


@app.get("/api/v1/tasks/{task_id}/activity")
def get_activity(task_id: UUID, user: TokenClaims = Depends(current_user)) -> list[dict]:
    return store.list_activity(str(task_id))


@app.get("/api/v1/tasks/{task_id}/comments")
def get_comments(task_id: UUID, user: TokenClaims = Depends(current_user)) -> list[dict]:
    return store.list_comments(str(task_id))


@app.post("/api/v1/tasks/{task_id}/comments", status_code=201)
def post_comment(
    task_id: UUID,
    request: CommentRequest,
    user: TokenClaims = Depends(current_user),
) -> dict:
    comment = store.add_comment(
        task_id=str(task_id),
        org_id=str(user.org_id),
        author_id=str(user.user_id),
        body=request.body.strip(),
    )
    store.record_activity(
        task_id=str(task_id),
        org_id=str(user.org_id),
        actor_id=str(user.user_id),
        action="comment.added",
        detail={"commentId": comment["comment_id"]},
    )
    return comment


@app.get("/api/v1/tasks/{task_id}/custom-fields")
def get_fields(task_id: UUID, user: TokenClaims = Depends(current_user)) -> dict:
    return store.get_custom_fields(str(task_id))


@app.put("/api/v1/tasks/{task_id}/custom-fields")
def put_fields(
    task_id: UUID,
    request: CustomFieldsRequest,
    user: TokenClaims = Depends(current_user),
) -> dict:
    if not user.is_admin:
        raise HTTPException(status_code=403, detail="Only admins can change custom fields.")
    return store.put_custom_fields(
        task_id=str(task_id), org_id=str(user.org_id), fields=request.fields
    )


@app.get("/api/v1/me/activity")
def my_feed(user: TokenClaims = Depends(current_user)) -> list[dict]:
    return store.user_feed(str(user.user_id))