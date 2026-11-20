from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import MarketRate

# Illustrative starting values (NOT live market data). An admin replaces them via PUT /api/v1/admin/rates/{key},
# which flips `illustrative` to false. Clients display the flag so users are never shown made-up yields as real.
SEED = [
    MarketRate(id="mmf", key="mmf", asset_class="Money Market Fund", annual_yield_rate=11.85,
               effective_date=date(2026, 3, 1), provider_name="Illustrative seed data", risk_score=1, illustrative=True),
    MarketRate(id="tbill-91", key="tbill", asset_class="Treasury Bills (91-day)", annual_yield_rate=15.42,
               effective_date=date(2026, 3, 1), provider_name="Illustrative seed data", risk_score=1, illustrative=True),
    MarketRate(id="unit-trust", key="unit", asset_class="Equity Unit Trust", annual_yield_rate=12.10,
               effective_date=date(2026, 3, 1), provider_name="Illustrative seed data", risk_score=3, illustrative=True),
]


def seed_if_empty(db: Session) -> None:
    if db.scalar(select(MarketRate.id).limit(1)) is None:
        db.add_all([MarketRate(**{c.name: getattr(r, c.name) for c in MarketRate.__table__.columns}) for r in SEED])
        db.commit()


def rates_by_key(db: Session) -> dict[str, MarketRate]:
    return {r.key: r for r in db.scalars(select(MarketRate)).all()}


def yield_and_risk(rates: dict[str, MarketRate]) -> dict[str, tuple[float, int]]:
    return {k: (r.annual_yield_rate, r.risk_score) for k, r in rates.items()}
