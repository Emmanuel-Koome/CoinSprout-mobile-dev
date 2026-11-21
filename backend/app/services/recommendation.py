"""What the live app shows. The randomised experiment lives in the separate, no-real-money study module
(app/study); the money-moving app never varies what users see for research purposes."""
from __future__ import annotations

from sqlalchemy.orm import Session

from ..ml import engine
from ..models import User
from ..schemas import Allocation, Recommendation


def presented_recommendation(db: Session, user: User) -> Recommendation | None:
    if user.risk_category is None or user.alloc_mmf is None or user.alloc_equity is None:
        return None
    alloc = {"mmf": user.alloc_mmf, "equity": user.alloc_equity}
    expected, _ = engine.portfolio_stats(alloc)
    # ai_label stays False for now: the classifier is trained on SIMULATED answers (see ml/train.py). Flip it once it
    # is retrained on real pilot data and docs/RESEARCH_ALIGNMENT.md documents that.
    return Recommendation(category=user.risk_category, allocation=Allocation(**alloc), personalized=True, ai_label=False,
                          reasoning=user.model_reasoning or "", confidence_score=user.model_confidence or 0.0,
                          expected_annual_yield_pct=expected)
