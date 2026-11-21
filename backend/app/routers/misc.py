from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import MarketRate, Notification, StkRequest, User
from ..schemas import NotificationOut, RateOut, RateUpdate
from ..security import current_user, require_admin
from ..services import stk

notifications = APIRouter(prefix="/api/v1/notifications", tags=["notifications"])
admin = APIRouter(prefix="/api/v1/admin", tags=["admin"], dependencies=[Depends(require_admin)])
dev = APIRouter(prefix="/api/v1/dev", tags=["dev"])


@notifications.get("", response_model=list[NotificationOut])
def list_notifications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return db.scalars(select(Notification).where(Notification.user_id == user.id).order_by(Notification.created_at.desc()).limit(50)).all()


@notifications.post("/read-all", status_code=204)
def read_all(user: User = Depends(current_user), db: Session = Depends(get_db)):
    db.execute(update(Notification).where(Notification.user_id == user.id).values(is_read=True))
    db.commit()
    return Response(status_code=204)




@admin.put("/rates/{key}", response_model=RateOut)
def update_rate(key: str, body: RateUpdate, db: Session = Depends(get_db)):
    rate = db.scalar(select(MarketRate).where(MarketRate.key == key))
    if rate is None:
        raise HTTPException(404, "Unknown asset key (mmf | equity)")
    rate.annual_yield_rate, rate.effective_date, rate.illustrative = body.annual_yield_rate, body.effective_date, body.illustrative
    if body.provider_name:
        rate.provider_name = body.provider_name
    db.commit()
    return rate




@dev.post("/payments/{stk_id}/simulate")
def simulate_by_payment_id(stk_id: str, outcome: str = Query("success", pattern="^(success|cancel|insufficient)$"),
                           db: Session = Depends(get_db)):
    """DEV ONLY: lets a debug build of the app complete a mock M-Pesa prompt (it only knows the payment id)."""
    req = db.get(StkRequest, stk_id)
    if req is None or not req.checkout_request_id:
        raise HTTPException(404, "Unknown payment")
    return simulate(req.checkout_request_id, outcome, db)


@dev.post("/stk/{checkout_request_id}/simulate")
def simulate(checkout_request_id: str, outcome: str = Query("success", pattern="^(success|cancel|insufficient)$"),
             db: Session = Depends(get_db)):
    """DEV ONLY: stands in for Safaricom's callback so the full flow can be demoed without a Daraja account."""
    req = db.scalar(select(StkRequest).where(StkRequest.checkout_request_id == checkout_request_id))
    if req is None:
        raise HTTPException(404, "Unknown checkout request")
    cb: dict[str, Any] = {"MerchantRequestID": req.merchant_request_id, "CheckoutRequestID": checkout_request_id}
    if outcome == "success":
        cb |= {"ResultCode": 0, "ResultDesc": "The service request is processed successfully.",
               "CallbackMetadata": {"Item": [{"Name": "Amount", "Value": req.amount_cents / 100},
                                              {"Name": "MpesaReceiptNumber", "Value": "SIM" + checkout_request_id[-7:].upper()},
                                              {"Name": "PhoneNumber", "Value": int(req.phone.lstrip("+"))}]}}
    elif outcome == "cancel":
        cb |= {"ResultCode": 1032, "ResultDesc": "Request cancelled by user"}
    else:
        cb |= {"ResultCode": 1, "ResultDesc": "The balance is insufficient for the transaction"}
    stk.process_callback(db, {"Body": {"stkCallback": cb}})
    return {"status": "processed", "outcome": outcome}
