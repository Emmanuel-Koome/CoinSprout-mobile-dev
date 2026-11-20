from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import timedelta

import jwt
from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .config import settings
from .db import get_db
from .models import User
from .util import now_utc

_bearer = HTTPBearer(auto_error=False)
_ALG = "HS256"


def hash_otp(phone: str, code: str) -> str:
    return hmac.new(settings.otp_pepper.encode(), f"{phone}:{code}".encode(), hashlib.sha256).hexdigest()


def new_otp() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


def hash_refresh(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def new_refresh_token() -> str:
    return secrets.token_urlsafe(32)


def create_access_token(user_id: str) -> str:
    now = now_utc()
    return jwt.encode({"sub": user_id, "typ": "access", "iat": now,
                       "exp": now + timedelta(minutes=settings.access_token_minutes)}, settings.jwt_secret, algorithm=_ALG)


def current_user(creds: HTTPAuthorizationCredentials | None = Depends(_bearer), db: Session = Depends(get_db)) -> User:
    unauthorized = HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token", headers={"WWW-Authenticate": "Bearer"})
    if creds is None:
        raise unauthorized
    try:
        payload = jwt.decode(creds.credentials, settings.jwt_secret, algorithms=[_ALG])
    except jwt.PyJWTError:
        raise unauthorized
    if payload.get("typ") != "access":
        raise unauthorized
    user = db.get(User, payload.get("sub"))
    if user is None:
        raise unauthorized
    return user


def require_admin(x_admin_key: str | None = Header(default=None)) -> None:
    if not x_admin_key or not hmac.compare_digest(x_admin_key, settings.admin_api_key):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Admin key required")
