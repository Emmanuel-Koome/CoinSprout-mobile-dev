"""Walk-forward check of the allocation engine (Objective 4 / Chapter 3, 'Backtest').

PURPOSE (as written in Chapters 1-3): describe whether the engine's outputs are CONSISTENT and REASONABLE on
historical data. It is NOT evidence that the engine beats a benchmark, and with ~208 trading days it cannot be.

Method: at each rebalance date the engine sees ONLY data up to and including that date (expanding / trailing
windows - no look-ahead). The resulting allocation is then held for the next `step` trading days and evaluated on
that following period. A test proves that changing future data cannot change past allocations.

Input CSV columns:  date, nse_index (level), tbill_yield (% p.a.), mmf_yield (% p.a.)
    python -m backtest.walk_forward data.csv --out results.json [--cost-bps 0]
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass

import numpy as np
import pandas as pd

from app.services import risk_model as rm

PROFILES = {  # name: (horizon, tolerance, income)
    "cautious_short": (1, 1, 1), "cautious_long": (3, 1, 3), "middle": (2, 2, 2),
    "growth_long": (3, 3, 3), "tolerant_but_short_horizon": (1, 3, 3), "growth_irregular_income": (3, 3, 1),
}
ORDER_CHAIN = ["cautious_short", "middle", "growth_long"]      # equity weight must be non-decreasing along this chain
ASSETS = ("mmf", "tbill", "unit", "cash")
TRADING_DAYS = 252
NEUTRAL_SAVINGS = 500_000


@dataclass
class Config:
    warmup: int = 60          # days of history required before the first decision
    lookback: int = 60        # window for 'recent' volatility
    step: int = 5             # rebalance every 5 trading days (weekly)
    cost_bps: float = 0.0     # one-way cost applied to turnover at each rebalance (run a sensitivity at e.g. 25)
    ffill_limit: int = 5      # max consecutive missing yield days to carry forward


def load_prices(path: str, ffill_limit: int = 5) -> tuple[pd.DataFrame, dict]:
    df = pd.read_csv(path, parse_dates=["date"]).sort_values("date").reset_index(drop=True)
    n0 = len(df)
    df[["tbill_yield", "mmf_yield"]] = df[["tbill_yield", "mmf_yield"]].ffill(limit=ffill_limit)
    df = df.dropna(subset=["nse_index", "tbill_yield", "mmf_yield"]).reset_index(drop=True)
    return df, {"rows_in": n0, "rows_used": len(df), "rows_dropped_missing": n0 - len(df), "yield_ffill_limit_days": ffill_limit}


def _vol_ratio(rets: np.ndarray, i: int, lookback: int) -> float:
    """long-run vol / recent vol using ONLY rets[1..i] (rets[t] = return from day t-1 to t)."""
    recent = rets[i - lookback + 1: i + 1].std(ddof=1)
    longrun = rets[1: i + 1].std(ddof=1)
    return float(longrun / recent) if recent > 0 else 1.0


def latest_ratio(df: pd.DataFrame, lookback: int = 60) -> float:
    """The equity volatility ratio as of the LAST row of `df`. Compute it once at the pre-registration freeze date and
    set STUDY_EQUITY_VOL_RATIO to this value so every participant sees an engine configured identically."""
    px = df["nse_index"].to_numpy(float)
    rets = np.full(len(px), np.nan); rets[1:] = np.diff(np.log(px))
    return round(_vol_ratio(rets, len(px) - 1, lookback), 4)


def _period_return(w: dict[str, int], rets: np.ndarray, i: int, step: int, tb: float, mm: float) -> float:
    eq = float(np.exp(rets[i + 1: i + step + 1].sum()) - 1)
    acc = lambda y: (1 + y / 100 / TRADING_DAYS) ** step - 1       # yield known at decision date, accrued daily
    return (w["unit"] * eq + w["tbill"] * acc(tb) + w["mmf"] * acc(mm)) / 100.0


def _compliance(name: str, rec: rm.Recommendation, static: rm.Recommendation) -> list[str]:
    h, _, inc = PROFILES[name]
    a, v = rec.allocation, []
    if sum(a.values()) != 100: v.append("sum_not_100")
    if min(a.values()) < 0: v.append("negative_weight")
    if a["cash"] < (10 if inc == 1 else 5) - 1: v.append("cash_buffer")
    if h == 1 and a["unit"] > 10: v.append("short_horizon_equity_cap")
    lo, hi = 0.75 * static.allocation["unit"] - 2, 1.15 * static.allocation["unit"] + 2      # +-2pp slack for integer rounding
    if not lo <= a["unit"] <= hi: v.append("tilt_out_of_bounds")
    return v


def _max_drawdown(curve: np.ndarray) -> float:
    peak = np.maximum.accumulate(curve)
    return float(((curve - peak) / peak).min())


def run(df: pd.DataFrame, cfg: Config = Config(), meta: dict | None = None) -> dict:
    n = len(df)
    px = df["nse_index"].to_numpy(float)
    rets = np.full(n, np.nan); rets[1:] = np.diff(np.log(px))
    dates = df["date"].dt.strftime("%Y-%m-%d").tolist()
    idxs = [i for i in range(cfg.warmup, n - 1, cfg.step) if i + 1 < n]
    if len(idxs) < 3:
        raise ValueError("Not enough data for a walk-forward run: need more than warmup + 3 rebalance periods")

    alloc_hist = {p: [] for p in PROFILES}; static_hist = {p: [] for p in PROFILES}
    violations: list[dict] = []; ordering_breaks = 0; lookahead_free = True
    per_period = {p: {"tilt": [], "static": []} for p in PROFILES}; ratios = []
    prev_w = {p: None for p in PROFILES}

    for i in idxs:
        step = min(cfg.step, n - 1 - i)
        ratio = _vol_ratio(rets, i, cfg.lookback)            # <- uses data up to day i only
        ratios.append(ratio)
        tb, mm = float(df.loc[i, "tbill_yield"]), float(df.loc[i, "mmf_yield"])
        recs = {}
        for name, (h, t, inc) in PROFILES.items():
            rec = rm.recommend(h, t, inc, NEUTRAL_SAVINGS, ratio); st = rm.recommend(h, t, inc, NEUTRAL_SAVINGS, None)
            recs[name] = rec
            for v in _compliance(name, rec, st):
                violations.append({"date": dates[i], "profile": name, "rule": v})
            alloc_hist[name].append(rec.allocation); static_hist[name].append(st.allocation)
            gross = _period_return(rec.allocation, rets, i, step, tb, mm)
            turnover = 0.0 if prev_w[name] is None else 0.5 * sum(abs(rec.allocation[k] - prev_w[name][k]) for k in ASSETS) / 100
            per_period[name]["tilt"].append(gross - turnover * 2 * cfg.cost_bps / 1e4)      # buy + sell legs
            per_period[name]["static"].append(_period_return(st.allocation, rets, i, step, tb, mm))
            prev_w[name] = rec.allocation
        eq = [recs[p].allocation["unit"] for p in ORDER_CHAIN]
        if not all(eq[k] <= eq[k + 1] for k in range(len(eq) - 1)):
            ordering_breaks += 1

    stability = {}
    for name, hist in alloc_hist.items():
        arr = np.array([[a[k] for k in ASSETS] for a in hist], float)
        d = np.abs(np.diff(arr, axis=0))
        stability[name] = {"mean_abs_change_pp": {k: round(float(d[:, j].mean()), 3) for j, k in enumerate(ASSETS)},
                           "max_abs_change_pp": {k: float(d[:, j].max()) for j, k in enumerate(ASSETS)},
                           "mean_turnover_pct_per_rebalance": round(float(0.5 * d.sum(axis=1).mean()), 3),
                           "max_turnover_pct_per_rebalance": round(float(0.5 * d.sum(axis=1).max()), 3),
                           "share_of_rebalances_with_any_change": round(float((d.sum(axis=1) > 0).mean()), 3)}

    def perf(r: list[float]) -> dict:
        r = np.array(r); curve = np.cumprod(1 + r)
        return {"total_return_pct": round(float((curve[-1] - 1) * 100), 2), "annualised_vol_pct": round(float(r.std(ddof=1) * np.sqrt(TRADING_DAYS / cfg.step) * 100), 2),
                "max_drawdown_pct": round(_max_drawdown(curve) * 100, 2)}

    descriptive = {p: {"engine_with_tilt": perf(v["tilt"]), "static_without_tilt": perf(v["static"])} for p, v in per_period.items()}
    n_checks = len(idxs) * len(PROFILES)
    return {
        "scope": "DESCRIPTIVE consistency/reasonableness check. Not a test of outperformance (Chapters 1-3).",
        "period": {"first_decision": dates[idxs[0]], "last_decision": dates[idxs[-1]], "data_start": dates[0], "data_end": dates[-1],
                   "trading_days": n, "rebalances": len(idxs), "step_days": cfg.step, "warmup_days": cfg.warmup, "lookback_days": cfg.lookback},
        "data_handling": meta or {}, "cost_assumption_bps_one_way": cfg.cost_bps,
        "equity_vol_ratio": {"min": round(min(ratios), 3), "max": round(max(ratios), 3), "mean": round(float(np.mean(ratios)), 3)},
        "constraint_compliance": {"checks": n_checks, "violations": len(violations), "compliance_rate": round(1 - len(violations) / n_checks, 4), "violation_detail": violations[:20]},
        "risk_ordering": {"dates_checked": len(idxs), "dates_where_equity_weight_not_monotone": ordering_breaks},
        "allocation_stability": stability, "descriptive_performance_NOT_A_TEST": descriptive,
        "limitations": ["Short sample (see trading_days); results describe this window only.",
                        "Equity return = index price return (no dividends); yields accrue deterministically; no taxes or M-Pesa fees.",
                        "Engine inputs besides volatility ratio are fixed rules, so allocation changes are driven by one data-derived signal.",
                        "Overfitting risk (Bailey et al., 2017): do not tune cfg or the tilt bounds on this data and then re-report it."],
    }


def to_markdown(res: dict) -> str:
    c, o = res["constraint_compliance"], res["risk_ordering"]
    L = [f"# Walk-forward check ({res['period']['data_start']} to {res['period']['data_end']}, {res['period']['trading_days']} trading days)",
         f"- Rebalances: {res['period']['rebalances']} (every {res['period']['step_days']} days after {res['period']['warmup_days']}-day warm-up)",
         f"- Constraint compliance: {c['compliance_rate']:.1%} ({c['violations']} violations in {c['checks']} checks)",
         f"- Risk-ordering breaks: {o['dates_where_equity_weight_not_monotone']} of {o['dates_checked']} dates",
         "", "| Profile | mean turnover %/rebalance | max turnover % | mean |Δ unit| pp |", "|---|---|---|---|"]
    for p, s in res["allocation_stability"].items():
        L.append(f"| {p} | {s['mean_turnover_pct_per_rebalance']} | {s['max_turnover_pct_per_rebalance']} | {s['mean_abs_change_pp']['unit']} |")
    L += ["", res["scope"]]
    return "\n".join(L)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("csv"); ap.add_argument("--out", default="backtest_results.json"); ap.add_argument("--cost-bps", type=float, default=0.0)
    ap.add_argument("--step", type=int, default=5)
    ap.add_argument("--latest-ratio", action="store_true", help="print the equity volatility ratio at the last date and exit")
    a = ap.parse_args()
    df, meta = load_prices(a.csv)
    if a.latest_ratio:
        print(latest_ratio(df)); raise SystemExit(0)
    res = run(df, Config(cost_bps=a.cost_bps, step=a.step), meta)
    json.dump(res, open(a.out, "w"), indent=2)
    print(to_markdown(res))
