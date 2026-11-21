from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import (Goal, LedgerEntry, Notification, RefreshToken, StkRequest, Transaction, User, UserSettings)
from ..schemas import MeOut, Recommendation, RiskProfileIn, SettingsIn, SettingsOut
from ..ml import engine
from ..security import current_user
from ..services.recommendation import presented_recommendation
from ..services.roundup import VALID_INCREMENTS_CENTS, VALID_THRESHOLDS_CENTS
from ..util import mask_phone

router = APIRouter(prefix="/api/v1/me", tags=["me"])


@router.get("", response_model=MeOut)
def me(user: User = Depends(current_user)):
    return MeOut(id=user.id, phone_masked=mask_phone(user.phone), created_at=user.created_at,
                 risk_category=user.risk_category)


@router.get("/settings", response_model=SettingsOut)
def get_settings(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return db.get(UserSettings, user.id)


@router.put("/settings", response_model=SettingsOut)
def put_settings(body: SettingsIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    s = db.get(UserSettings, user.id)
    data = body.model_dump(exclude_unset=True)
    if (v := data.get("round_up_increment_cents")) is not None and v not in VALID_INCREMENTS_CENTS:
        raise HTTPException(422, f"Increment must be one of {[c // 100 for c in VALID_INCREMENTS_CENTS]} KES")
    if (v := data.get("stk_threshold_cents")) is not None and v not in VALID_THRESHOLDS_CENTS:
        raise HTTPException(422, f"Threshold must be one of {[c // 100 for c in VALID_THRESHOLDS_CENTS]} KES")
    for field in ("max_round_up_cents", "daily_cap_cents"):
        if data.get(field) is not None and data[field] % 100:
            raise HTTPException(422, f"{field} must be whole shillings")
    for k, v in data.items():
        if v is not None:
            setattr(s, k, v)
    db.commit()
    return s


@router.put("/risk-profile", response_model=Recommendation)
def put_risk_profile(body: RiskProfileIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Stores the quiz answers and the model output, and returns the recommendation shown to the user."""
    rec = engine.recommend(body.time_horizon, body.risk_tolerance, body.income_stability, body.investment_experience)
    user.time_horizon, user.risk_tolerance = body.time_horizon, body.risk_tolerance
    user.income_stability, user.investment_experience = body.income_stability, body.investment_experience
    user.risk_category, user.model_confidence, user.model_reasoning = rec.category, rec.confidence, rec.reasoning
    user.model_version = rec.model_version
    user.alloc_mmf, user.alloc_equity = rec.allocation["mmf"], rec.allocation["equity"]
    db.commit()
    return presented_recommendation(db, user)


@router.get("/export")
def export_my_data(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Data-portability: everything we hold about the caller, as JSON."""
    def rows(model):
        return [{c.name: (str(getattr(r, c.name)) if getattr(r, c.name) is not None else None) for c in model.__table__.columns}
                for r in db.scalars(select(model).where(model.user_id == user.id)).all()]
    profile = {c.name: str(getattr(user, c.name)) if getattr(user, c.name) is not None else None for c in User.__table__.columns}
    return {"profile": profile, "settings": rows(UserSettings), "transactions": rows(Transaction), "goals": rows(Goal),
            "payments": rows(StkRequest), "ledger": rows(LedgerEntry), "notifications": rows(Notification)}


@router.delete("", status_code=status.HTTP_204_NO_CONTENT)
def delete_account(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Right to erasure: removes the account and all linked rows."""
    for model in (LedgerEntry, Transaction, StkRequest, Goal, Notification, RefreshToken, UserSettings):
        db.execute(delete(model).where(model.user_id == user.id))
    db.execute(delete(User).where(User.id == user.id))
    db.commit()
