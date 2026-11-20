from __future__ import annotations

import hmac
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..models import StkRequest, User, UserSettings
from ..schemas import DepositIn, PortfolioOut, StkOut
from ..security import current_user
from ..services import ledger, stk
from ..services.recommendation import presented_recommendation
from ..util import now_utc

router = APIRouter(prefix="/api/v1", tags=["payments"])
_PROMPT = "Check your phone and enter your M-Pesa PIN to approve."


def _out(req: StkRequest, message: str | None = None) -> StkOut:
    return StkOut(id=req.id, purpose=req.purpose, amount_cents=req.amount_cents, status=req.status,
                  customer_message=message or (_PROMPT if req.status == "PENDING" else None),
                  receipt_number=req.receipt_number, result_desc=req.result_desc,
                  created_at=req.created_at, completed_at=req.completed_at)


@router.get("/portfolio", response_model=PortfolioOut)
def portfolio(user: User = Depends(current_user), db: Session = Depends(get_db)):
    stk.expire_stale(db, user.id)
    return PortfolioOut(balance_cents=ledger.balance_cents(db, user.id), pending_round_up_cents=ledger.pending_cents(db, user.id),
                        in_flight_cents=ledger.in_flight_cents(db, user.id), unallocated_cents=ledger.unallocated_cents(db, user.id),
                        recommendation=presented_recommendation(db, user), as_of=now_utc())


@router.post("/deposits", response_model=StkOut, status_code=202)
def deposit(body: DepositIn, user: User = Depends(current_user), db: Session = Depends(get_db),
            idempotency_key: str | None = Header(default=None, alias="Idempotency-Key", max_length=64)):
    return _out(stk.start_deposit(db, user, body.amount_cents, idempotency_key))


@router.post("/stk/sweep", response_model=StkOut, status_code=202)
def sweep(user: User = Depends(current_user), db: Session = Depends(get_db),
          idempotency_key: str | None = Header(default=None, alias="Idempotency-Key", max_length=64)):
    s = db.get(UserSettings, user.id)
    return _out(stk.start_sweep(db, user, s.stk_threshold_cents, idempotency_key))


@router.get("/stk/{stk_id}", response_model=StkOut)
def stk_status(stk_id: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    stk.expire_stale(db, user.id)
    req = db.scalar(select(StkRequest).where(StkRequest.id == stk_id, StkRequest.user_id == user.id))
    if req is None:
        raise HTTPException(404, "Payment not found")
    return _out(req)


@router.post("/stk/callback/{secret}")
def stk_callback(secret: str, payload: dict[str, Any], db: Session = Depends(get_db)):
    """Called by Safaricom. The secret path segment is our authentication (Daraja callbacks are not signed)."""
    if not hmac.compare_digest(secret, settings.stk_callback_secret):
        raise HTTPException(404, "Not found")
    stk.process_callback(db, payload)
    return {"ResultCode": 0, "ResultDesc": "Accepted"}   # always ack, or Safaricom retries
