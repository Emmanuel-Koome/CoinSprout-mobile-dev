from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Goal, LedgerEntry, Notification, Transaction


def _sum(db: Session, col, *where) -> int:
    return int(db.scalar(select(func.coalesce(func.sum(col), 0)).where(*where)) or 0)


def balance_cents(db: Session, user_id: str) -> int:
    return _sum(db, LedgerEntry.amount_cents, LedgerEntry.user_id == user_id)


def pending_cents(db: Session, user_id: str) -> int:
    return _sum(db, Transaction.round_up_cents, Transaction.user_id == user_id, Transaction.status == "PENDING_ROUNDUP")


def in_flight_cents(db: Session, user_id: str) -> int:
    return _sum(db, Transaction.round_up_cents, Transaction.user_id == user_id, Transaction.status == "STK_REQUESTED")


def unallocated_cents(db: Session, user_id: str) -> int:
    return balance_cents(db, user_id) - _sum(db, Goal.saved_cents, Goal.user_id == user_id)


def notify(db: Session, user_id: str, title: str, message: str) -> None:
    db.add(Notification(user_id=user_id, title=title[:120], message=message[:300]))


def kes(cents: int) -> str:
    return f"KES {cents / 100:,.0f}" if cents % 100 == 0 else f"KES {cents / 100:,.2f}"
