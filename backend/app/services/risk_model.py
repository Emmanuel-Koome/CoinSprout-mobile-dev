"""Risk-profile -> allocation model.

HONEST LABEL: this is an *explainable expert-prior model*, not a trained ML model. It is a Gaussian-likelihood
classifier over a weighted suitability score, with probability-weighted blending of the base portfolios and
hard guard-rails. Weights are set by hand from suitability principles. The research design in this project
(survey + backtest) is how those weights should eventually be learned and validated.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

MODEL_VERSION = "expert-prior-v1"
CATEGORIES = ("Conservative", "Moderate", "Aggressive")
ASSETS = ("mmf", "tbill", "unit", "cash")

BASE_ALLOCATIONS: dict[str, dict[str, float]] = {
    "Conservative": {"mmf": 70, "tbill": 20, "unit": 5, "cash": 5},
    "Moderate":     {"mmf": 40, "tbill": 25, "unit": 25, "cash": 10},
    "Aggressive":   {"mmf": 15, "tbill": 15, "unit": 60, "cash": 10},
}
DEFAULT_CATEGORY = "Moderate"  # control-arm / fallback allocation

_MU = {"Conservative": -0.75, "Moderate": 0.0, "Aggressive": 0.75}
_SIGMA = 0.45
_W_HORIZON, _W_TOLERANCE, _W_INCOME = 0.40, 0.40, 0.20


@dataclass(frozen=True)
class Recommendation:
    category: str
    allocation: dict[str, int]
    confidence: float
    reasoning: str
    model_version: str = MODEL_VERSION
    tilt_pp: float = 0.0          # percentage points moved out of (-) or into (+) equities by the data-driven tilt


def suitability_score(horizon: int, tolerance: int, income: int, savings_cents: int) -> float:
    s = _W_HORIZON * (horizon - 2) + _W_TOLERANCE * (tolerance - 2) + _W_INCOME * (income - 2)
    if savings_cents < 500_000:        # under KES 5,000: little capacity to absorb a loss
        s -= 0.15
    elif savings_cents >= 5_000_000:   # KES 50,000+: more capacity
        s += 0.10
    return max(-1.2, min(1.2, s))


def category_probabilities(score: float) -> dict[str, float]:
    raw = {c: math.exp(-((score - _MU[c]) ** 2) / (2 * _SIGMA ** 2)) for c in CATEGORIES}
    total = sum(raw.values())
    return {c: v / total for c, v in raw.items()}


def _blend(probs: dict[str, float]) -> dict[str, float]:
    return {a: sum(probs[c] * BASE_ALLOCATIONS[c][a] for c in CATEGORIES) for a in ASSETS}


TILT_MIN, TILT_MAX = 0.75, 1.15   # equity weight may shrink by at most 25% or grow by at most 15% (relative)


def apply_vol_tilt(a: dict[str, float], equity_vol_ratio: float) -> tuple[dict[str, float], float]:
    """DATA-DRIVEN COMPONENT. `equity_vol_ratio` = long-run equity volatility / recent equity volatility, both
    estimated ONLY from data available at the decision date (see backtest/walk_forward.py). Recent turbulence
    (ratio < 1) trims the equity sleeve; unusual calm (ratio > 1) adds a little. The difference is taken from /
    given to money-market and T-bills pro rata, cash is untouched, and the change is bounded. Transparent
    volatility scaling - an estimate from data, not a deep-learning model."""
    factor = max(TILT_MIN, min(TILT_MAX, equity_vol_ratio))
    out = dict(a)
    delta = out["unit"] * factor - out["unit"]
    pool = out["mmf"] + out["tbill"]
    if pool <= 0:
        return out, 0.0
    delta = max(delta, -pool) if delta < 0 else min(delta, pool)
    out["unit"] += delta
    out["mmf"] -= delta * out["mmf"] / pool
    out["tbill"] -= delta * out["tbill"] / pool
    return out, delta


def _apply_guardrails(a: dict[str, float], horizon: int, income: int) -> tuple[dict[str, float], list[str]]:
    a, notes = dict(a), []
    if horizon == 1 and a["unit"] > 10:
        excess = a["unit"] - 10
        a["unit"] -= excess
        a["mmf"] += excess
        notes.append("Because you may need this money within a year, equity unit trusts are capped at 10%.")
    min_cash = 10.0 if income == 1 else 5.0
    if a["cash"] < min_cash:
        need = min_cash - a["cash"]
        for src in ("unit", "tbill", "mmf"):
            take = min(need, a[src])
            a[src] -= take
            a["cash"] += take
            need -= take
            if need <= 1e-9:
                break
        if income == 1:
            notes.append("Your income is irregular, so we keep a larger cash buffer.")
    return a, notes


def _largest_remainder(a: dict[str, float]) -> dict[str, int]:
    floors = {k: int(math.floor(v)) for k, v in a.items()}
    shortfall = 100 - sum(floors.values())
    for k in sorted(a, key=lambda k: a[k] - floors[k], reverse=True)[:shortfall]:
        floors[k] += 1
    return floors


def recommend(horizon: int, tolerance: int, income: int, savings_cents: int,
              equity_vol_ratio: float | None = None) -> Recommendation:
    probs = category_probabilities(suitability_score(horizon, tolerance, income, savings_cents))
    blended = _blend(probs)
    tilt = 0.0
    if equity_vol_ratio is not None:
        blended, tilt = apply_vol_tilt(blended, equity_vol_ratio)
    blended, notes = _apply_guardrails(blended, horizon, income)
    if abs(tilt) >= 0.5:
        notes.append(f"Recent market swings were {'larger' if tilt < 0 else 'smaller'} than usual, so the share in "
                     f"unit trusts was nudged {'down' if tilt < 0 else 'up'} by about {abs(tilt):.0f} points.")
    category = max(probs, key=probs.get)
    horizon_txt = {1: "a short time horizon", 2: "a medium time horizon", 3: "a long time horizon"}[horizon]
    tolerance_txt = {1: "low comfort with losses", 2: "moderate comfort with losses", 3: "high comfort with losses"}[tolerance]
    reasoning = " ".join([f"Your answers suggest {horizon_txt} and {tolerance_txt}, which fits a {category} profile."] + notes)
    return Recommendation(category, _largest_remainder(blended), round(probs[category], 2), reasoning, tilt_pp=round(tilt, 2))


def fixed_default() -> Recommendation:
    """Control arm: the same split every user gets (no personalisation)."""
    alloc = {k: int(v) for k, v in BASE_ALLOCATIONS[DEFAULT_CATEGORY].items()}
    return Recommendation(DEFAULT_CATEGORY, alloc, 1.0, "A standard balanced split used as a starting point.", "fixed-default-v1")


def indicative_yield_and_risk(allocation: dict[str, int], rates: dict[str, tuple[float, int]]) -> tuple[float, float]:
    """rates: key -> (annual_yield_percent, risk_score). Cash earns 0 and has risk 0."""
    y = sum(allocation[k] / 100 * rates[k][0] for k in ("mmf", "tbill", "unit") if k in rates)
    r = sum(allocation[k] / 100 * rates[k][1] for k in ("mmf", "tbill", "unit") if k in rates)
    return round(y, 2), round(r, 2)


# Reference profile used to build the study's GENERIC recommendation: the engine's own output for a mid-point
# respondent, frozen before data collection. Using the engine (rather than a hand-typed table) keeps the
# "AI was involved" label truthful in the generic arm too.
REFERENCE_PROFILE = (2, 2, 2, 500_000)


def reference_recommendation(equity_vol_ratio: float | None = None) -> Recommendation:
    h, t, i, sv = REFERENCE_PROFILE
    return recommend(h, t, i, sv, equity_vol_ratio)
