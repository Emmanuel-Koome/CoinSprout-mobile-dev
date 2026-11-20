"""STK Push lifecycle: start -> (callback | expiry). The server is the only place money state changes."""
from __future__ import annotations

from datetime import timedelta
from typing import Any

from fastapi import HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..config import settings
from ..models import LedgerEntry, StkRequest, Transaction, User
from ..util import as_utc, now_utc
from . import ledger
from .mpesa import MpesaError, get_provider

CANCELLED_CODES = {1032}   # request cancelled by user


def _reconcile_late_sweep(db: Session, req: StkRequest) -> None:
    """The prompt expired on our side but the customer paid anyway: attach the oldest pending round-ups
    until the paid amount is covered (best effort; the ledger credit is already correct)."""
    remaining = req.amount_cents
    pending = db.scalars(select(Transaction).where(Transaction.user_id == req.user_id, Transaction.status == "PENDING_ROUNDUP")
                         .order_by(Transaction.occurred_at)).all()
    for tx in pending:
        if tx.round_up_cents <= remaining:
            tx.status, tx.stk_request_id = "ROUTED_TO_PORTFOLIO", req.id
            remaining -= tx.round_up_cents


def _lock_pending_transactions(db: Session, user_id: str, stk_id: str) -> None:
    db.execute(update(Transaction).where(Transaction.user_id == user_id, Transaction.status == "PENDING_ROUNDUP")
               .values(status="STK_REQUESTED", stk_request_id=stk_id))


def _release_transactions(db: Session, stk_id: str) -> None:
    db.execute(update(Transaction).where(Transaction.stk_request_id == stk_id, Transaction.status == "STK_REQUESTED")
               .values(status="PENDING_ROUNDUP", stk_request_id=None))


def expire_stale(db: Session, user_id: str) -> None:
    cutoff = now_utc() - timedelta(seconds=settings.stk_expiry_seconds)
    stale = db.scalars(select(StkRequest).where(StkRequest.user_id == user_id, StkRequest.status == "PENDING",
                                               StkRequest.created_at < cutoff)).all()
    for req in stale:
        req.status, req.completed_at, req.result_desc = "EXPIRED", now_utc(), "No response from M-Pesa"
        _release_transactions(db, req.id)
    if stale:
        db.commit()


def _existing_by_key(db: Session, user_id: str, key: str | None) -> StkRequest | None:
    if not key:
        return None
    return db.scalar(select(StkRequest).where(StkRequest.user_id == user_id, StkRequest.idempotency_key == key))


def _dispatch(db: Session, user: User, req: StkRequest, description: str) -> StkRequest:
    try:
        result = get_provider().initiate_stk(phone=user.phone, amount_cents=req.amount_cents,
                                             reference="CoinSprout", description=description)
    except MpesaError as exc:
        req.status, req.result_desc, req.completed_at = "FAILED", str(exc)[:255], now_utc()
        _release_transactions(db, req.id)
        db.commit()
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"M-Pesa request failed: {exc}")
    req.merchant_request_id, req.checkout_request_id = result.merchant_request_id, result.checkout_request_id
    db.commit()
    return req


def start_sweep(db: Session, user: User, threshold_cents: int, idem_key: str | None) -> StkRequest:
    expire_stale(db, user.id)
    if (existing := _existing_by_key(db, user.id, idem_key)):
        return existing
    if db.scalar(select(StkRequest.id).where(StkRequest.user_id == user.id, StkRequest.status == "PENDING")):
        raise HTTPException(status.HTTP_409_CONFLICT, "A payment prompt is already waiting on your phone")
    pending = ledger.pending_cents(db, user.id)
    if pending < max(threshold_cents, 100):
        raise HTTPException(status.HTTP_409_CONFLICT, f"Pending round-ups ({ledger.kes(pending)}) are below your threshold")
    req = StkRequest(user_id=user.id, purpose="ROUNDUP_SWEEP", amount_cents=pending, phone=user.phone, idempotency_key=idem_key)
    db.add(req)
    db.flush()
    _lock_pending_transactions(db, user.id, req.id)
    return _dispatch(db, user, req, "Round-ups")


def start_deposit(db: Session, user: User, amount_cents: int, idem_key: str | None) -> StkRequest:
    expire_stale(db, user.id)
    if (existing := _existing_by_key(db, user.id, idem_key)):
        return existing
    if db.scalar(select(StkRequest.id).where(StkRequest.user_id == user.id, StkRequest.status == "PENDING")):
        raise HTTPException(status.HTTP_409_CONFLICT, "A payment prompt is already waiting on your phone")
    req = StkRequest(user_id=user.id, purpose="DEPOSIT", amount_cents=amount_cents, phone=user.phone, idempotency_key=idem_key)
    db.add(req)
    db.flush()
    return _dispatch(db, user, req, "Deposit")


def _metadata(callback: dict[str, Any]) -> dict[str, Any]:
    items = (callback.get("CallbackMetadata") or {}).get("Item") or []
    return {i.get("Name"): i.get("Value") for i in items if isinstance(i, dict)}


def process_callback(db: Session, payload: dict[str, Any]) -> None:
    """Idempotent: Daraja may deliver the same callback more than once."""
    cb = (payload.get("Body") or {}).get("stkCallback") or {}
    checkout_id = cb.get("CheckoutRequestID")
    req = db.scalar(select(StkRequest).where(StkRequest.checkout_request_id == checkout_id)) if checkout_id else None
    if req is None or req.status not in ("PENDING", "EXPIRED"):   # unknown or already settled -> ignore quietly
        return
    was_expired = req.status == "EXPIRED"
    code = cb.get("ResultCode")
    req.result_code, req.result_desc = code, str(cb.get("ResultDesc", ""))[:255]
    req.completed_at = now_utc()
    if code == 0:
        meta = _metadata(cb)
        paid = meta.get("Amount")
        if paid is not None and round(float(paid) * 100) != req.amount_cents:
            req.status = "MISMATCH"                      # never credit an unexpected amount automatically
            ledger.notify(db, req.user_id, "Payment needs review", "The amount received did not match the request.")
        else:
            req.status, req.receipt_number = "SUCCESS", (str(meta.get("MpesaReceiptNumber")) if meta.get("MpesaReceiptNumber") else None)
            db.add(LedgerEntry(user_id=req.user_id, kind=req.purpose, amount_cents=req.amount_cents, stk_request_id=req.id))
            if req.purpose == "ROUNDUP_SWEEP":
                if was_expired:
                    _reconcile_late_sweep(db, req)
                else:
                    db.execute(update(Transaction).where(Transaction.stk_request_id == req.id)
                               .values(status="ROUTED_TO_PORTFOLIO"))
                ledger.notify(db, req.user_id, "Round-ups invested", f"{ledger.kes(req.amount_cents)} moved into your portfolio.")
            else:
                ledger.notify(db, req.user_id, "Deposit received", f"{ledger.kes(req.amount_cents)} added to your portfolio.")
    else:
        req.status = "CANCELLED" if code in CANCELLED_CODES else "FAILED"
        _release_transactions(db, req.id)
        ledger.notify(db, req.user_id, "Payment not completed", "Your M-Pesa prompt was cancelled or failed. Nothing was charged.")
    db.commit()
