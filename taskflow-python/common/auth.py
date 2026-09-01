import time
from dataclasses import dataclass
from uuid import UUID

import httpx
from jose import jwt
from jose.exceptions import JWTError

from .settings import settings


@dataclass(frozen=True)
class TokenClaims:
    user_id: UUID
    org_id: UUID
    role: str
    team_ids: list[UUID]

    @property
    def is_admin(self) -> bool:
        return self.role == "ADMIN"


class AuthError(Exception):
    pass


class JwksCache:
    """
    Caches identity-service's public keys. Same model as task-service: verify
    signatures locally, never call identity-service on a request path.
    """

    def __init__(self, ttl_seconds: int = 600) -> None:
        self._ttl = ttl_seconds
        self._keys: dict | None = None
        self._fetched_at: float = 0.0

    def keys(self) -> dict:
        if self._keys is None or time.time() - self._fetched_at > self._ttl:
            self._keys = self._fetch()
            self._fetched_at = time.time()
        return self._keys

    def _fetch(self) -> dict:
        try:
            response = httpx.get(settings().identity_jwks_url, timeout=5.0)
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as exc:
            raise AuthError("Could not reach the identity service.") from exc

    def invalidate(self) -> None:
        self._keys = None


_jwks = JwksCache()


def verify_token(token: str) -> TokenClaims:
    payload: dict | None = None

    for attempt in (1, 2):
        try:
            payload = jwt.decode(
                token,
                _jwks.keys(),
                algorithms=["RS256"],
                issuer=settings().identity_issuer,
                options={"verify_aud": False},
            )
            break
        except JWTError as exc:
            # A rotated signing key is indistinguishable from a bad signature.
            # Refetch once before deciding the token is invalid.
            if attempt == 1:
                _jwks.invalidate()
                continue
            raise AuthError("Sign in to continue.") from exc

    try:
        return TokenClaims(
            user_id=UUID(payload["sub"]),
            org_id=UUID(payload["orgId"]),
            role=payload.get("role", "MEMBER"),
            team_ids=[UUID(t) for t in payload.get("teamIds", [])],
        )
    except (KeyError, ValueError, TypeError) as exc:
        raise AuthError("Sign in to continue.") from exc
