"""CoinSprout AI engine (runtime). Loads the JSON artifacts written by ml/train.py and does inference in plain
Python, so the API needs no scikit-learn / numpy / scipy.

    1. Risk classifier (TRAINED): multinomial logistic regression over 4 questionnaire answers (1..5)
    2. Allocation (NOT trained): 2-asset mean-variance optimisation, closed form, with per-category bounds
       using expected returns and covariance ESTIMATED from MMF rates and the NSE All Share Index
    3. Guard-rail: money needed within a year caps equities

Assets: "mmf" (money market fund) and "equity" (NSE equities). Educational use only, not financial advice.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

ARTIFACTS = Path(__file__).resolve().parent / "artifacts"
FEATURES = ("time_horizon", "risk_tolerance", "income_stability", "investment_experience")
CATEGORIES = ("Conservative", "Moderate", "Aggressive")
ASSET_KEYS = ("mmf", "equity")          # API keys, in the same order as the artifacts' ["MMF", "Equity"]
DISCLAIMER = "Educational use only. Not financial advice."

# Plain-language phrases for the explanation: (low answer, high answer).
_PHRASES = {
    "time_horizon": ("a short time horizon", "a long time horizon"),
    "risk_tolerance": ("a preference for avoiding losses", "comfort with market ups and downs"),
    "income_stability": ("an irregular income", "a steady income"),
    "investment_experience": ("limited investing experience", "your investing experience"),
}


@dataclass(frozen=True)
class Artifacts:
    classifier: dict
    stats: dict
    allocation: dict

    @property
    def model_version(self) -> str:
        return self.classifier["modelVersion"]


@lru_cache(maxsize=1)
def artifacts() -> Artifacts:
    def load(name: str) -> dict:
        return json.loads((ARTIFACTS / name).read_text(encoding="utf-8"))
    a = Artifacts(load("risk_classifier.json"), load("asset_stats.json"), load("allocation_config.json"))
    c = a.classifier
    assert tuple(c["features"]) == FEATURES and tuple(c["classes"]) == CATEGORIES, "classifier artifact mismatch"
    assert len(c["coef"]) == 3 and all(len(r) == 4 for r in c["coef"]), "classifier artifact has the wrong shape"
    assert a.stats["assets"] == ["MMF", "Equity"] and len(a.stats["mu"]) == 2, "asset_stats artifact mismatch"
    assert {a.classifier["modelVersion"], a.stats["modelVersion"], a.allocation["modelVersion"]} == {c["modelVersion"]}, \
        "artifacts come from different training runs; re-run ml/train.py"
    return a


MODEL_VERSION = artifacts().model_version


@dataclass(frozen=True)
class MlRecommendation:
    category: str
    allocation: dict[str, int]                 # {"mmf": 75, "equity": 25}
    confidence: float
    probabilities: dict[str, float]
    reasoning: str
    expected_return_pct: float
    volatility_pct: float
    model_version: str = MODEL_VERSION
    notes: list[str] = field(default_factory=list)


# --------------------------------------------------------------------------------------------- 1. classifier
def _standardise(answers: tuple[int, ...]) -> list[float]:
    c = artifacts().classifier
    return [(x - m) / s for x, m, s in zip(answers, c["scalerMean"], c["scalerScale"])]


def classify(answers: tuple[int, int, int, int]) -> dict[str, float]:
    """Class probabilities (softmax of the logistic-regression logits)."""
    c = artifacts().classifier
    z = _standardise(answers)
    logits = [b + sum(w * x for w, x in zip(row, z)) for row, b in zip(c["coef"], c["intercept"])]
    top = max(logits)
    exp = [math.exp(v - top) for v in logits]
    total = sum(exp)
    return {cat: e / total for cat, e in zip(CATEGORIES, exp)}


def _drivers(answers: tuple[int, ...]) -> tuple[str | None, str | None]:
    """Which answer pushed most toward growth and which held back most, measured along the
    Aggressive-vs-Conservative direction of the model (works for every category, including Moderate)."""
    c = artifacts().classifier
    z = _standardise(answers)
    i_c, i_a = CATEGORIES.index("Conservative"), CATEGORIES.index("Aggressive")
    contrib = {f: (c["coef"][i_a][j] - c["coef"][i_c][j]) * z[j] for j, f in enumerate(FEATURES)}
    up = max(contrib, key=contrib.get)
    down = min(contrib, key=contrib.get)
    return (_PHRASES[up][1] if contrib[up] > 0.25 else None,
            _PHRASES[down][0] if contrib[down] < -0.25 else None)


# --------------------------------------------------------------------------------------------- 2. allocation
def optimal_equity_share(mu: list[float], cov: list[list[float]], lam: float, lo: float, hi: float) -> float:
    """Equity share e maximising mu.w - lam/2 w'Cw for w = (1-e, e), clipped to [lo, hi].
    Same formula as ml/train.py (which verifies it against the notebook's SLSQP solver)."""
    d = mu[1] - mu[0]
    var_m, var_e, c = cov[0][0], cov[1][1], cov[0][1]
    v = var_m + var_e - 2 * c
    e = hi if v <= 1e-12 else (d / lam + var_m - c) / v
    return min(hi, max(lo, e))


def portfolio_stats(allocation: dict[str, int]) -> tuple[float, float]:
    """(expected annual return %, annual volatility %) from the estimated mu and cov."""
    s = artifacts().stats
    w = [allocation["mmf"] / 100, allocation["equity"] / 100]
    exp = sum(wi * m for wi, m in zip(w, s["mu"]))
    var = sum(w[i] * w[j] * s["cov"][i][j] for i in range(2) for j in range(2))
    return round(exp * 100, 2), round(math.sqrt(max(var, 0.0)) * 100, 2)


def _to_split(equity_share: float) -> dict[str, int]:
    step = artifacts().allocation["splitStepPct"]
    eq = int(round(equity_share * 100 / step) * step)
    return {"mmf": 100 - eq, "equity": eq}


def allocation_for(category: str, time_horizon: int | None = None) -> tuple[dict[str, int], list[str]]:
    a = artifacts()
    cfg = a.allocation["categories"][category]
    lo, hi = cfg["eq"]
    e = optimal_equity_share(a.stats["mu"], a.stats["cov"], cfg["lam"], lo, hi)
    notes = []
    cap = a.allocation["guardrails"]["shortHorizonEquityCap"]
    if time_horizon == 1 and e > cap:
        e = cap
        notes.append(f"Because you may need this money within a year, NSE equities are capped at {round(cap * 100)}%.")
    elif e <= lo + 1e-9 and lo > 0 and a.stats["mu"][1] <= a.stats["mu"][0]:
        p0, p1 = (p[:4] for p in a.stats["period"])
        notes.append(f"Between {p0} and {p1} NSE share prices grew less than money-market rates, so equities are kept "
                     f"at the minimum for your profile.")
    return _to_split(e), notes


# --------------------------------------------------------------------------------------------- public API
def recommend(time_horizon: int, risk_tolerance: int, income_stability: int, investment_experience: int) -> MlRecommendation:
    answers = (time_horizon, risk_tolerance, income_stability, investment_experience)
    if not all(isinstance(x, int) and 1 <= x <= 5 for x in answers):
        raise ValueError("answers must be integers from 1 to 5")
    probs = classify(answers)
    category = max(probs, key=probs.get)
    alloc, notes = allocation_for(category, time_horizon)
    exp, vol = portfolio_stats(alloc)

    up, down = _drivers(answers)
    article = "an" if category[0] in "AEIOU" else "a"
    parts = [f"Your answers fit {article} {category} profile ({min(probs[category], 0.99):.0%} match)."]
    if up and down:
        parts.append(f"{up[0].upper() + up[1:]} pushed you toward growth, while {down} pulled you toward caution.")
    elif up:
        parts.append(f"The biggest factor was {up}.")
    elif down:
        parts.append(f"The biggest factor was {down}.")
    parts.append(f"We suggest {alloc['mmf']}% in a money market fund and {alloc['equity']}% in NSE equities.")
    reasoning = " ".join(parts + notes)
    return MlRecommendation(category, alloc, round(probs[category], 2), {k: round(v, 3) for k, v in probs.items()},
                            reasoning, exp, vol, notes=notes)
