"""What the live app shows. The randomised experiment lives in the separate, no-real-money study module
(app/study); the money-moving app never varies what users see for research purposes."""
from __future__ import annotations

from sqlalchemy.orm import Session

from ..models import User
from ..schemas import Allocation, Recommendation
from . import risk_model
from .rates import rates_by_key, yield_and_risk


def presented_recommendation(db: Session, user: User) -> Recommendation | None:
    if user.risk_category is None:
        return None
    alloc = {"mmf": user.alloc_mmf, "tbill": user.alloc_tbill, "unit": user.alloc_unit, "cash": user.alloc_cash}
    y, _ = risk_model.indicative_yield_and_risk(alloc, yield_and_risk(rates_by_key(db)))
    # ai_label stays False until the engine has a documented data-driven component (see docs/RESEARCH_ALIGNMENT.md).
    return Recommendation(category=user.risk_category, allocation=Allocation(**alloc), personalized=True, ai_label=False,
                          reasoning=user.model_reasoning or "", confidence_score=user.model_confidence or 0.0,
                          expected_annual_yield_pct=y)
