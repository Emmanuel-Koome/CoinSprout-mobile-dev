from __future__ import annotations

import re
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

EAT = ZoneInfo("Africa/Nairobi")  # UTC+3, no DST

_PHONE_RE = re.compile(r"^(?:\+?254|0)?([17]\d{8})$")


def normalize_phone(raw: str) -> str | None:
    """Return canonical +254XXXXXXXXX for Kenyan mobile numbers, or None if invalid."""
    cleaned = re.sub(r"[\s\-()]", "", raw or "")
    m = _PHONE_RE.match(cleaned)
    return f"+254{m.group(1)}" if m else None


def mask_phone(phone: str) -> str:
    return f"{phone[:5]}****{phone[-3:]}" if len(phone) >= 10 else "***"


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def as_utc(dt: datetime) -> datetime:
    """SQLite returns naive datetimes; everything we store is UTC, so tag it."""
    return dt.replace(tzinfo=timezone.utc) if dt.tzinfo is None else dt.astimezone(timezone.utc)


def to_utc_from_client(dt: datetime) -> datetime:
    """Client timestamps with an offset are converted; naive ones are assumed to be Nairobi local time."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=EAT)
    return dt.astimezone(timezone.utc)
