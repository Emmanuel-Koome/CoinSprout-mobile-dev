from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..db import get_db
from ..ml import engine
from ..models import User
from ..schemas import Allocation, AllocationResponse, GrowthForecastIn, GrowthForecastOut, InsightOut, RiskProfileIn
from ..security import current_user
from ..services import forecast as fc
from ..services.insights import build_insights

router = APIRouter(prefix="/api/v1/ai", tags=["ai"])


@router.post("/optimize-allocation", response_model=AllocationResponse)
def optimize_allocation(body: RiskProfileIn, _: User = Depends(current_user)):
    """What-if: runs the AI engine on a set of quiz answers without storing anything."""
    rec = engine.recommend(body.time_horizon, body.risk_tolerance, body.income_stability, body.investment_experience)
    return AllocationResponse(recommended_category=rec.category, allocation=Allocation(**rec.allocation),
                              confidence_score=rec.confidence, category_probabilities=rec.probabilities,
                              ai_reasoning=rec.reasoning, expected_annual_return_pct=rec.expected_return_pct,
                              expected_annual_volatility_pct=rec.volatility_pct, model_version=rec.model_version,
                              disclaimer=engine.DISCLAIMER)


@router.post("/forecast-growth", response_model=GrowthForecastOut)
def forecast_growth(body: GrowthForecastIn, user: User = Depends(current_user)):
    if body.risk_category:
        alloc, _ = engine.allocation_for(body.risk_category)
    elif user.alloc_mmf is not None and user.alloc_equity is not None:
        alloc = {"mmf": user.alloc_mmf, "equity": user.alloc_equity}
    else:
        alloc, _ = engine.allocation_for(fc.DEFAULT_CATEGORY)
    return fc.forecast(body.principal_cents, body.monthly_contribution_cents, alloc, body.months_ahead, body.inflation_pct)


@router.get("/spending-insights", response_model=list[InsightOut])
def spending_insights(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return build_insights(db, user.id)
