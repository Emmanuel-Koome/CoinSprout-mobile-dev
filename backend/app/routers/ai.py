from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import User
from ..schemas import Allocation, AllocationResponse, GrowthForecastIn, GrowthForecastOut, InsightOut, RiskProfileIn
from ..security import current_user
from ..services import forecast as fc, risk_model
from ..services.insights import build_insights
from ..services.rates import rates_by_key, yield_and_risk

router = APIRouter(prefix="/api/v1/ai", tags=["ai"])


@router.post("/optimize-allocation", response_model=AllocationResponse)
def optimize_allocation(body: RiskProfileIn, _: User = Depends(current_user), db: Session = Depends(get_db)):
    rec = risk_model.recommend(body.risk_horizon, body.risk_tolerance, body.income_stability, body.current_savings_cents)
    y, r = risk_model.indicative_yield_and_risk(rec.allocation, yield_and_risk(rates_by_key(db)))
    return AllocationResponse(recommended_category=rec.category, allocation=Allocation(**rec.allocation),
                              confidence_score=rec.confidence, ai_reasoning=rec.reasoning,
                              expected_annual_yield_pct=y, risk_index=r, model_version=rec.model_version)


@router.post("/forecast-growth", response_model=GrowthForecastOut)
def forecast_growth(body: GrowthForecastIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if body.risk_category:
        alloc = {k: int(v) for k, v in risk_model.BASE_ALLOCATIONS[body.risk_category].items()}
    elif user.alloc_mmf is not None:
        alloc = {"mmf": user.alloc_mmf, "tbill": user.alloc_tbill, "unit": user.alloc_unit, "cash": user.alloc_cash}
    else:
        alloc = risk_model.fixed_default().allocation
    yields = {k: r.annual_yield_rate for k, r in rates_by_key(db).items()}
    return fc.forecast(body.principal_cents, body.monthly_contribution_cents, alloc, yields, body.months_ahead, body.inflation_pct)


@router.get("/spending-insights", response_model=list[InsightOut])
def spending_insights(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return build_insights(db, user.id)
