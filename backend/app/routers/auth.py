from __future__ import annotations

import hmac
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..models import OtpCode, RefreshToken, User, UserSettings
from ..schemas import OtpRequest, OtpRequested, OtpVerify, RefreshRequest, TokenPair
from ..security import create_access_token, hash_otp, hash_refresh, new_otp, new_refresh_token
from ..services import sms
from ..util import as_utc, normalize_phone, now_utc

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


def _issue(db: Session, user_id: str, is_new: bool) -> TokenPair:
    raw = new_refresh_token()
    db.add(RefreshToken(user_id=user_id, token_hash=hash_refresh(raw), expires_at=now_utc() + timedelta(days=settings.refresh_token_days)))
    db.commit()
    return TokenPair(access_token=create_access_token(user_id), refresh_token=raw,
                     expires_in_seconds=settings.access_token_minutes * 60, is_new_user=is_new)


@router.post("/request-otp", response_model=OtpRequested)
def request_otp(body: OtpRequest, db: Session = Depends(get_db)):
    phone = normalize_phone(body.phone)
    if phone is None:
        raise HTTPException(422, "Enter a valid Kenyan mobile number")
    recent = db.scalar(select(func.count()).select_from(OtpCode).where(OtpCode.phone == phone, OtpCode.created_at >= now_utc() - timedelta(minutes=10)))
    if (recent or 0) >= settings.otp_max_requests_per_10min:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Too many code requests. Try again in a few minutes.")
    code = new_otp()
    db.add(OtpCode(phone=phone, code_hash=hash_otp(phone, code), expires_at=now_utc() + timedelta(seconds=settings.otp_ttl_seconds)))
    db.commit()
    sms.send(phone, f"Your CoinSprout code is {code}. It expires in {settings.otp_ttl_seconds // 60} minutes. Never share it.")
    return OtpRequested(expires_in_seconds=settings.otp_ttl_seconds, dev_otp=code if settings.is_dev else None)


@router.post("/verify-otp", response_model=TokenPair)
def verify_otp(body: OtpVerify, db: Session = Depends(get_db)):
    phone = normalize_phone(body.phone)
    if phone is None:
        raise HTTPException(422, "Enter a valid Kenyan mobile number")
    otp = db.scalar(select(OtpCode).where(OtpCode.phone == phone, OtpCode.consumed.is_(False)).order_by(OtpCode.created_at.desc()))
    if otp is None or as_utc(otp.expires_at) < now_utc():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "That code has expired. Request a new one.")
    if otp.attempts >= settings.otp_max_attempts:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Too many wrong attempts. Request a new code.")
    otp.attempts += 1
    if not hmac.compare_digest(otp.code_hash, hash_otp(phone, body.otp)):
        db.commit()
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Incorrect code")
    otp.consumed = True
    user = db.scalar(select(User).where(User.phone == phone))
    is_new = user is None
    if user is None:
        user = User(phone=phone)
        db.add(user)
        db.flush()
        db.add(UserSettings(user_id=user.id))
    return _issue(db, user.id, is_new)


@router.post("/refresh", response_model=TokenPair)
def refresh(body: RefreshRequest, db: Session = Depends(get_db)):
    row = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_refresh(body.refresh_token)))
    if row is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid refresh token")
    if row.revoked:   # a rotated-out token being replayed means it may have been stolen: kill the whole family
        db.execute(update(RefreshToken).where(RefreshToken.user_id == row.user_id).values(revoked=True))
        db.commit()
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token reuse detected. Please sign in again.")
    if as_utc(row.expires_at) < now_utc():
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Session expired")
    row.revoked = True
    return _issue(db, row.user_id, False)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(body: RefreshRequest, db: Session = Depends(get_db)):
    db.execute(update(RefreshToken).where(RefreshToken.token_hash == hash_refresh(body.refresh_token)).values(revoked=True))
    db.commit()
