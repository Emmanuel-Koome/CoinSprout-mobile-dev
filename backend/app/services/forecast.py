"""Scenario projection of a portfolio. Deliberately shows a *range* (including a bad case), never a single upbeat
number. Expected returns and volatility come from the AI engine's estimates (ml/train.py: MMF rates and the NSE All
Share Index); every assumption is returned to the client so it can be displayed."""
from __future__ import annotations

import math

from ..ml import engine

DEFAULT_CATEGORY = "Moderate"
Z_90 = 1.645   # pessimistic / optimistic = 5th / 95th percentile of the average annual return over the horizon
DISCLAIMER = "Investments can lose value. Past performance does not guarantee future results."


def assumptions() -> list[str]:
    s = engine.artifacts().stats
    p0, p1 = s["period"]
    div = s["equityDividendYieldAssumed"]
    equity_basis = f"plus an assumed {div * 100:.1f}% dividend yield" if div else "price only, dividends not included"
    return [
        f"Expected case uses average historical returns from {p0} to {p1}: money market fund rates and NSE All Share "
        f"Index prices ({equity_basis}).",
        "The bad and good cases are the 5th and 95th percentile of the average yearly return over your chosen period, "
        "based on how much this mix has moved historically. Longer periods narrow the range.",
        "Returns are held constant. Fees, taxes and M-Pesa charges are not modelled.",
        "This is an illustration of a range of outcomes, not a prediction or a guarantee.",
    ]


def scenario_rates(allocation: dict[str, int], months: int) -> dict[str, float]:
    """Annual portfolio return in percent for each scenario."""
    expected, vol = engine.portfolio_stats(allocation)
    spread = Z_90 * vol / math.sqrt(max(months / 12, 1.0))
    return {"pessimistic": expected - spread, "expected": expected, "optimistic": expected + spread}


def future_value_cents(principal_cents: int, monthly_cents: int, annual_pct: float, months: int) -> int:
    r = annual_pct / 100 / 12
    if abs(r) < 1e-12:
        fv = principal_cents + monthly_cents * months
    else:
        growth = (1 + r) ** months
        fv = principal_cents * growth + monthly_cents * ((growth - 1) / r)
    return int(round(fv))


def forecast(principal_cents: int, monthly_cents: int, allocation: dict[str, int], months: int,
             inflation_pct: float = 5.0) -> dict:
    rates = scenario_rates(allocation, months)
    values = {c: future_value_cents(principal_cents, monthly_cents, rates[c], months) for c in rates}
    deflator = (1 + inflation_pct / 100) ** (months / 12)
    return {
        "monthsAhead": months,
        "totalContributedCents": principal_cents + monthly_cents * months,
        "pessimisticValueCents": values["pessimistic"],
        "projectedValueCents": values["expected"],
        "optimisticValueCents": values["optimistic"],
        "expectedAnnualReturnPct": round(rates["expected"], 2),
        "pessimisticAnnualReturnPct": round(rates["pessimistic"], 2),
        "optimisticAnnualReturnPct": round(rates["optimistic"], 2),
        "inflationAdjustedValueCents": int(round(values["expected"] / deflator)),
        "assumedInflationPct": inflation_pct,
        "assumptions": assumptions(),
        "disclaimer": DISCLAIMER,
    }
