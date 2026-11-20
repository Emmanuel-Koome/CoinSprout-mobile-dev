"""Scenario projection of a portfolio. Deliberately shows a *range* (including a bad case), never a single
upbeat number. All scenario parameters are ASSUMPTIONS and are returned to the client so they can be displayed."""
from __future__ import annotations

# asset -> (pessimistic_multiplier_or_fixed, optimistic_multiplier_or_fixed); ("x", m) scales the published yield,
# ("=", pct) fixes the annual return in percent.
SCENARIOS = {
    "mmf":   (("x", 0.70), ("x", 1.10)),
    "tbill": (("x", 0.80), ("x", 1.05)),
    "unit":  (("=", -15.0), ("=", 25.0)),
    "cash":  (("=", 0.0), ("=", 0.0)),
}
ASSUMPTIONS = [
    "Expected case uses the currently published yield of each asset class, held constant.",
    "Money market and T-bill yields can fall; the pessimistic case scales them down 30% and 20%.",
    "Unit trusts can lose value: pessimistic case is -15% a year, optimistic +25% a year.",
    "Cash earns nothing. Fees, taxes and M-Pesa charges are not modelled.",
    "This is an illustration of a range of outcomes, not a prediction or a guarantee.",
]
DISCLAIMER = "Investments can lose value. Past performance does not guarantee future results."


def _asset_rate(asset: str, case: str, yield_pct: float) -> float:
    if asset == "cash":
        return 0.0
    if case == "expected":
        return yield_pct
    kind, val = SCENARIOS[asset][0 if case == "pessimistic" else 1]
    return yield_pct * val if kind == "x" else val


def portfolio_rate(allocation: dict[str, int], yields_pct: dict[str, float], case: str) -> float:
    """Annual portfolio return in percent for a scenario."""
    return sum(allocation[a] / 100 * _asset_rate(a, case, yields_pct.get(a, 0.0)) for a in allocation)


def future_value_cents(principal_cents: int, monthly_cents: int, annual_pct: float, months: int) -> int:
    r = annual_pct / 100 / 12
    if abs(r) < 1e-12:
        fv = principal_cents + monthly_cents * months
    else:
        growth = (1 + r) ** months
        fv = principal_cents * growth + monthly_cents * ((growth - 1) / r)
    return int(round(fv))


def forecast(principal_cents: int, monthly_cents: int, allocation: dict[str, int], yields_pct: dict[str, float],
             months: int, inflation_pct: float = 5.0) -> dict:
    rates = {c: portfolio_rate(allocation, yields_pct, c) for c in ("pessimistic", "expected", "optimistic")}
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
        "assumptions": ASSUMPTIONS,
        "disclaimer": DISCLAIMER,
    }
