from __future__ import annotations

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import and_, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Transaction, User, UserSettings
from ..schemas import TransactionCreated, TransactionIn, TransactionOut, TransactionPage
from ..security import current_user
from ..services import ledger
from ..services.roundup import ELIGIBLE_KINDS, apply_daily_cap, compute_round_up_cents, round_up_used_today_cents
from ..util import as_utc, now_utc, to_utc_from_client

router = APIRouter(prefix="/api/v1/transactions", tags=["transactions"])


def _result(db: Session, user: User, tx: Transaction, s: UserSettings, duplicate: bool, before: int | None = None) -> TransactionCreated:
    pending = ledger.pending_cents(db, user.id)
    crossed = (not duplicate) and pending >= s.stk_threshold_cents and (before is not None and before < s.stk_threshold_cents)
    return TransactionCreated(transaction=TransactionOut.model_validate(tx), duplicate=duplicate, pending_round_up_cents=pending,
                              threshold_reached=pending >= s.stk_threshold_cents, auto_stk_due=crossed and s.auto_stk_enabled)


@router.post("", response_model=TransactionCreated)
def create_transaction(body: TransactionIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    s = db.get(UserSettings, user.id)
    existing = db.scalar(select(Transaction).where(Transaction.user_id == user.id, Transaction.mpesa_code == body.mpesa_code))
    if existing:
        return _result(db, user, existing, s, duplicate=True)
    occurred = to_utc_from_client(body.occurred_at)
    if occurred > now_utc() + timedelta(minutes=5):
        raise HTTPException(422, "Transaction time is in the future")

    round_up, status_ = 0, "SKIPPED"
    if body.kind in ELIGIBLE_KINDS:
        round_up = compute_round_up_cents(body.amount_cents, s.round_up_increment_cents, s.max_round_up_cents)
        round_up = apply_daily_cap(round_up, round_up_used_today_cents(db, user.id, occurred), s.daily_cap_cents)
        status_ = "PENDING_ROUNDUP" if round_up > 0 else "SKIPPED"

    before = ledger.pending_cents(db, user.id)
    tx = Transaction(user_id=user.id, mpesa_code=body.mpesa_code, merchant_name=body.merchant_name, kind=body.kind,
                     amount_cents=body.amount_cents, round_up_cents=round_up, status=status_, occurred_at=occurred)
    db.add(tx)
    try:
        db.flush()
    except IntegrityError:           # concurrent duplicate delivery of the same SMS
        db.rollback()
        existing = db.scalar(select(Transaction).where(Transaction.user_id == user.id, Transaction.mpesa_code == body.mpesa_code))
        return _result(db, user, existing, s, duplicate=True)
    after = before + round_up
    if before < s.stk_threshold_cents <= after:
        ledger.notify(db, user.id, "Ready to invest", f"Your round-ups reached {ledger.kes(after)}.")
    db.commit()
    return _result(db, user, tx, s, duplicate=False, before=before)


@router.get("", response_model=TransactionPage)
def list_transactions(limit: int = Query(50, ge=1, le=200), cursor: str | None = None,
                      user: User = Depends(current_user), db: Session = Depends(get_db)):
    q = select(Transaction).where(Transaction.user_id == user.id)
    if cursor:
        try:
            ts, tid = cursor.split("|", 1)
            c_time = datetime.fromisoformat(ts)
        except ValueError:
            raise HTTPException(422, "Bad cursor")
        q = q.where(or_(Transaction.occurred_at < c_time, and_(Transaction.occurred_at == c_time, Transaction.id < tid)))
    rows = db.scalars(q.order_by(Transaction.occurred_at.desc(), Transaction.id.desc()).limit(limit + 1)).all()
    page, more = rows[:limit], len(rows) > limit
    next_cursor = f"{as_utc(page[-1].occurred_at).replace(tzinfo=None).isoformat()}|{page[-1].id}" if more and page else None
    return TransactionPage(items=[TransactionOut.model_validate(t) for t in page], next_cursor=next_cursor)
