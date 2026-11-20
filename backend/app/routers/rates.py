from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import MarketRate
from ..schemas import RateOut

router = APIRouter(prefix="/api/v1/cbk-rates", tags=["rates"])


@router.get("", response_model=list[RateOut])
def list_rates(db: Session = Depends(get_db)):
    return db.scalars(select(MarketRate).order_by(MarketRate.risk_score, MarketRate.key)).all()


@router.get("/{asset}", response_model=RateOut)
def get_rate(asset: str, db: Session = Depends(get_db)):
    rate = db.scalar(select(MarketRate).where((MarketRate.id == asset) | (MarketRate.key == asset)))
    if rate is None:
        raise HTTPException(404, "Unknown asset class")
    return rate
