"""CoinSprout AI engine: reproducible training.

Runs the three notebooks in ml/notebooks/ as one script and writes the small JSON artifacts the backend loads at
runtime (backend/app/ml/artifacts/). The backend never imports scikit-learn: inference is plain arithmetic.

    Step 1  Risk classifier      questionnaire (4 answers, 1-5) -> Conservative / Moderate / Aggressive
    Step 2  Returns and risk     MMF rates + NASI index -> expected returns (mu) and covariance (cov) for 2 assets
    Step 3  Allocation engine    category + mu + cov -> MMF / Equity split (mean-variance optimisation, not ML)

Usage (from the repo root):
    python ml/train.py                         # writes backend/app/ml/artifacts/*.json and ml/output/*
    python ml/train.py --dividend-yield 0.06   # add a SOURCED dividend yield to the price-only NASI index

Changes vs the notebooks (deliberate):
  * The questionnaire has 4 items. `goal_type` was only a decoy to test the models and is not asked in the app.
  * Step 1 still compares LR / RF / SVM, but always exports Logistic Regression (explainable, tiny, portable) and
    records the comparison in training_report.json.
  * Step 3's allocator is also solved in closed form (2 assets) and checked against the notebook's SLSQP solver.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.optimize import minimize
from sklearn.covariance import LedoitWolf
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC

ROOT = Path(__file__).resolve().parent.parent
ML = ROOT / "ml"
DEFAULT_ARTIFACTS = ROOT / "backend" / "app" / "ml" / "artifacts"
OUTPUT = ML / "output"
CACHE = ML / "data" / "cache"

SEED = 42
MODEL_VERSION = "ml-v1"
FEATURES = ["time_horizon", "risk_tolerance", "income_stability", "investment_experience"]
CLASSES = ["Conservative", "Moderate", "Aggressive"]
ASSETS = ["MMF", "Equity"]          # keep this order everywhere
EQ = ASSETS.index("Equity")

# Step 3 policy choices (NOT learned). Conservative's equity cap is below Aggressive's floor, so the ordering holds.
ALLOC_CONFIG = {
    "Conservative": {"lam": 12.0, "eq": [0.00, 0.15]},
    "Moderate":     {"lam": 5.0,  "eq": [0.10, 0.35]},
    "Aggressive":   {"lam": 1.5,  "eq": [0.25, 0.60]},
}
SPLIT_STEP_PCT = 5
SHORT_HORIZON_EQUITY_CAP = 0.10     # guard-rail: money needed within a year (time_horizon == 1)

HF_REPO = "ToKnow-ai/Kenyan-Collective-Investment-Schemes-Dataset"
HF_CONFIG = "Processed Investment Schemes Records"


def log(*a):
    print(*a, flush=True)


# --------------------------------------------------------------------------------------------- Step 1: classifier
def make_synthetic_users(n: int, noise: float, rng: np.random.Generator) -> pd.DataFrame:
    """Same generator as the notebook minus the decoy. Labels come from a weighted score + noise, so this is a
    proof of concept: replace with real pilot data (same column names + `risk_category`) when you have it."""
    df = pd.DataFrame({f: rng.integers(1, 6, n) for f in FEATURES})
    score = (0.25 * df.time_horizon + 0.35 * df.risk_tolerance
             + 0.20 * df.income_stability + 0.20 * df.investment_experience)
    df["risk_category"] = pd.cut(score + rng.normal(0, noise, n), bins=[-np.inf, 2.2, 3.4, np.inf], labels=CLASSES).astype(str)
    return df


def train_classifier(df: pd.DataFrame, data_source: str) -> tuple[dict, dict]:
    X, y = df[FEATURES], df["risk_category"]
    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=SEED, stratify=y)
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    models = {
        "Logistic Regression": (Pipeline([("scale", StandardScaler()), ("clf", LogisticRegression(max_iter=2000, class_weight="balanced"))]),
                                {"clf__C": [0.01, 0.1, 1, 10, 100]}),
        "Random Forest": (Pipeline([("scale", StandardScaler()), ("clf", RandomForestClassifier(random_state=SEED, class_weight="balanced"))]),
                          {"clf__n_estimators": [100, 300], "clf__max_depth": [3, 5, 8, None], "clf__min_samples_leaf": [1, 5, 10]}),
        "SVM": (Pipeline([("scale", StandardScaler()), ("clf", SVC(class_weight="balanced"))]),
                {"clf__C": [0.1, 1, 10, 100], "clf__gamma": ["scale", 0.01, 0.1, 1], "clf__kernel": ["rbf", "linear"]}),
    }
    comparison, fitted = [], {}
    for name, (pipe, grid) in models.items():
        search = GridSearchCV(pipe, grid, cv=cv, scoring="f1_macro", n_jobs=-1).fit(X_tr, y_tr)
        pred = search.best_estimator_.predict(X_te)
        fitted[name] = search.best_estimator_
        comparison.append({"model": name, "cvMacroF1": round(search.best_score_, 3),
                           "testMacroF1": round(f1_score(y_te, pred, average="macro"), 3),
                           "testAccuracy": round(accuracy_score(y_te, pred), 3),
                           "bestParams": {k: (v if isinstance(v, (int, float, str)) or v is None else str(v)) for k, v in search.best_params_.items()}})
        log(f"  {name:20s} CV F1 {comparison[-1]['cvMacroF1']:.3f}  test F1 {comparison[-1]['testMacroF1']:.3f}")

    pipe = fitted["Logistic Regression"]
    scaler, lr = pipe.named_steps["scale"], pipe.named_steps["clf"]
    # Store classes in CLASSES order so the runtime never depends on sklearn's alphabetical order.
    order = [list(lr.classes_).index(c) for c in CLASSES]
    artifact = {
        "modelVersion": MODEL_VERSION,
        "type": "multinomial-logistic-regression",
        "features": FEATURES,
        "featureRange": [1, 5],
        "classes": CLASSES,
        "scalerMean": scaler.mean_.tolist(),
        "scalerScale": scaler.scale_.tolist(),
        "coef": lr.coef_[order].tolist(),
        "intercept": lr.intercept_[order].tolist(),
        "trainingData": data_source,
    }
    pred = pipe.predict(X_te)
    report = {"comparison": comparison, "exported": "Logistic Regression",
              "confusionMatrix": {"labels": CLASSES, "matrix": confusion_matrix(y_te, pred, labels=CLASSES).tolist()},
              "classBalance": y.value_counts().to_dict(), "nRows": len(df), "dataSource": data_source}

    # Parity: the exported numbers must reproduce sklearn exactly (this is what the backend will compute).
    z = (X_te.values - scaler.mean_) / scaler.scale_
    logits = z @ np.array(artifact["coef"]).T + np.array(artifact["intercept"])
    ours = np.array(CLASSES)[logits.argmax(1)]
    assert (ours == pred).all(), "exported LR does not match sklearn predictions"
    return artifact, report


# ------------------------------------------------------------------------------------------ Step 2: mu and cov
def load_mmf_raw() -> pd.DataFrame:
    """Hugging Face dataset, cached locally (ml/data/cache is git-ignored: third-party data, re-downloadable)."""
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / "mmf_records.csv"
    if cached.exists():
        return pd.read_csv(cached)
    from datasets import load_dataset
    ds = load_dataset(HF_REPO, HF_CONFIG)
    df = ds[list(ds.keys())[0]].to_pandas()
    df.to_csv(cached, index=False)
    return df


def monthly_series(df, date_col, val_col, how="mean"):
    d = df[[date_col, val_col]].copy()
    d[date_col] = pd.to_datetime(d[date_col], errors="coerce")
    d[val_col] = pd.to_numeric(d[val_col], errors="coerce")
    d = d.dropna()
    d["m"] = d[date_col].dt.to_period("M")
    g = d.sort_values(date_col).groupby("m")[val_col]
    return g.mean() if how == "mean" else g.last()


def shrunk_cov(X: np.ndarray) -> np.ndarray:
    """Annualised covariance with Ledoit-Wolf shrinkage applied to the CORRELATION, not the raw covariance.
    The notebook ran LW on raw returns; LW's target is a scaled identity (one average variance), so with a smooth
    MMF (~0.5% vol) next to equities (~19% vol) it inflated the MMF's volatility about 8x. Standardising first keeps
    each asset's own variance and only shrinks the (noisy) correlation."""
    sd = X.std(axis=0, ddof=1)
    corr = LedoitWolf().fit(X / sd).covariance_
    d = np.sqrt(np.diag(corr))
    corr = corr / np.outer(d, d)
    return np.outer(sd, sd) * corr * 12


def block_bootstrap_indices(n, block, rng):
    starts = rng.integers(0, n, int(np.ceil(n / block)))
    return np.concatenate([(s + np.arange(block)) % n for s in starts])[:n]


def estimate_asset_stats(dividend_yield: float, rng: np.random.Generator, B=200, block=6) -> tuple[dict, dict, pd.DataFrame]:
    warnings = []
    mmf_raw = load_mmf_raw()
    mmf = mmf_raw[mmf_raw["entry_type"] == 1].copy()          # 1 = effective annual rate (%), 0 = fund size
    v = pd.to_numeric(mmf["entry_value"], errors="coerce")
    mmf["entry_value"] = v / 100 if v.dropna().median() > 1 else v
    mmf = mmf[(mmf["entry_value"] > 0) & (mmf["entry_value"] < 0.30)]
    mmf_rate = monthly_series(mmf, "entry_date", "entry_value")
    mmf_ret = (1 + mmf_rate) ** (1 / 12) - 1

    nse_raw = pd.read_csv(ML / "data" / "NASI_daily_2008_2025.csv")
    nse_level = monthly_series(nse_raw, "Date", "^NASI", how="last")
    full = pd.period_range(nse_level.index.min(), nse_level.index.max(), freq="M")
    nse_full = nse_level.reindex(full)
    equity_ret = (nse_full.pct_change(fill_method=None) + dividend_yield / 12).dropna()   # only consecutive months

    returns = pd.concat({"MMF": mmf_ret, "Equity": equity_ret}, axis=1).dropna()[ASSETS]
    mu = returns.mean() * 12
    cov = shrunk_cov(returns.values)

    if len(returns) < 60:
        warnings.append(f"Only {len(returns)} common months: estimates are unstable.")
    if mu["Equity"] <= mu["MMF"]:
        warnings.append("Equities have a LOWER expected return than the MMF over this period, so the optimiser puts every "
                        "category on its equity FLOOR. NASI is price-only (no dividends); consider --dividend-yield with a cited source.")

    X = returns.values
    boot_mu, boot_cov = [], []
    for _ in range(B):
        s = X[block_bootstrap_indices(len(X), block, rng)]
        boot_mu.append(s.mean(0) * 12)
        boot_cov.append(shrunk_cov(s))
    boot_mu, boot_cov = np.array(boot_mu), np.array(boot_cov)

    stats = {
        "modelVersion": MODEL_VERSION,
        "assets": ASSETS,
        "period": [str(returns.index.min()), str(returns.index.max())],
        "nMonths": int(len(returns)),
        "equityDividendYieldAssumed": dividend_yield,
        "mu": [round(float(x), 6) for x in mu.values],
        "cov": np.round(cov, 8).tolist(),
        "muCi95": {"low": np.round(np.percentile(boot_mu, 2.5, 0), 6).tolist(),
                   "high": np.round(np.percentile(boot_mu, 97.5, 0), 6).tolist()},
        "sources": {"MMF": f"huggingface:{HF_REPO} ({HF_CONFIG}), effective annual rates averaged across schemes",
                    "Equity": "NSE All Share Index (NASI) month-end levels, price-only, ml/data/NASI_daily_2008_2025.csv"},
        "warnings": warnings,
    }
    boot = {"bootstrapMu": boot_mu.round(6).tolist(), "bootstrapCov": boot_cov.round(8).tolist()}
    return stats, boot, returns


# ------------------------------------------------------------------------------------------ Step 3: allocation
def allocate_closed_form(mu, cov, lam, eq_lo, eq_hi) -> float:
    """Equity share e maximising mu.w - lam/2 w'Cw for w = (1-e, e), clipped to [eq_lo, eq_hi].
    Mirrored exactly in backend/app/ml/engine.py."""
    d = mu[EQ] - mu[1 - EQ]
    var_m, var_e, c = cov[1 - EQ][1 - EQ], cov[EQ][EQ], cov[0][1]
    v = var_m + var_e - 2 * c
    e = eq_hi if v <= 1e-12 else (d / lam + var_m - c) / v
    return min(eq_hi, max(eq_lo, e))


def allocate_slsqp(mu, cov, lam, eq_lo, eq_hi) -> float:
    """The notebook's numerical solver, kept only to verify the closed form."""
    mu, cov = np.asarray(mu), np.asarray(cov)
    bounds = [(0.0, 1.0)] * 2
    bounds[EQ] = (eq_lo, eq_hi)
    w0 = np.array([np.mean(b) for b in bounds]); w0 /= w0.sum()
    res = minimize(lambda w: -(mu @ w - 0.5 * lam * w @ cov @ w), w0, jac=lambda w: -(mu - lam * cov @ w),
                   bounds=bounds, method="SLSQP", constraints=[{"type": "eq", "fun": lambda w: w.sum() - 1}],
                   options={"maxiter": 500, "ftol": 1e-12})
    w = np.clip(res.x, 0, None)
    return float(w[EQ] / w.sum())


def split_pct(equity_share: float) -> dict:
    eq = int(round(equity_share * 100 / SPLIT_STEP_PCT) * SPLIT_STEP_PCT)
    return {"MMF": 100 - eq, "Equity": eq}


def build_allocation(stats: dict, boot: dict) -> tuple[dict, dict]:
    mu, cov = stats["mu"], stats["cov"]
    table, parity, stability = {}, {}, {}
    for cat, cfg in ALLOC_CONFIG.items():
        e = allocate_closed_form(mu, cov, cfg["lam"], *cfg["eq"])
        e_ref = allocate_slsqp(mu, cov, cfg["lam"], *cfg["eq"])
        parity[cat] = abs(e - e_ref)
        assert parity[cat] < 1e-4, f"closed form disagrees with SLSQP for {cat}: {e} vs {e_ref}"
        w = np.array([1 - e, e]) if EQ == 1 else np.array([e, 1 - e])
        table[cat] = {**split_pct(e), "expectedReturnPct": round(float(np.dot(mu, w)) * 100, 2),
                      "volatilityPct": round(float(np.sqrt(w @ np.array(cov) @ w)) * 100, 2),
                      "onEquityFloor": bool(e <= cfg["eq"][0] + 1e-9), "onEquityCap": bool(e >= cfg["eq"][1] - 1e-9)}
        shares = np.array([allocate_closed_form(m, c, cfg["lam"], *cfg["eq"]) for m, c in zip(boot["bootstrapMu"], boot["bootstrapCov"])])
        shift = np.abs(shares - e) * 100        # with 2 assets, % of the portfolio that moves = |equity change|
        stability[cat] = {"equityMeanPct": round(float(shares.mean() * 100), 1), "equityStdPct": round(float(shares.std() * 100), 1),
                          "equityP5Pct": round(float(np.percentile(shares, 5) * 100), 1),
                          "equityP95Pct": round(float(np.percentile(shares, 95) * 100), 1),
                          "medianShiftPct": round(float(np.median(shift)), 1), "p95ShiftPct": round(float(np.percentile(shift, 95)), 1)}
    eqs = [table[c]["Equity"] for c in CLASSES]
    assert eqs == sorted(eqs), f"equity share not ordered Conservative <= Moderate <= Aggressive: {eqs}"
    config = {"modelVersion": MODEL_VERSION, "covarianceMethod": "Ledoit-Wolf shrinkage of the correlation, sample variances kept", "method": "mean-variance (Markowitz), 2 assets, closed form with bounds",
              "assets": ASSETS, "categories": ALLOC_CONFIG, "splitStepPct": SPLIT_STEP_PCT,
              "guardrails": {"shortHorizonEquityCap": SHORT_HORIZON_EQUITY_CAP},
              "pointSplits": table, "disclaimer": "Educational use only. Not financial advice."}
    return config, {"stability": stability, "closedFormVsSlsqpMaxAbsDiff": max(parity.values())}


# ----------------------------------------------------------------------------------------------------- main
def sha16(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=DEFAULT_ARTIFACTS)
    ap.add_argument("--dividend-yield", type=float, default=0.0, help="annual, e.g. 0.06; cite a source in the report")
    ap.add_argument("--pilot-csv", type=Path, help="real questionnaire data: columns %s + risk_category" % FEATURES)
    ap.add_argument("--n", type=int, default=1000, help="synthetic users (ignored with --pilot-csv)")
    ap.add_argument("--noise", type=float, default=0.3)
    args = ap.parse_args()
    rng = np.random.default_rng(SEED)

    log("Step 1: risk classifier")
    if args.pilot_csv:
        users = pd.read_csv(args.pilot_csv)
        missing = [c for c in FEATURES + ["risk_category"] if c not in users.columns]
        if missing:
            sys.exit(f"--pilot-csv is missing columns {missing}")
        source = f"pilot:{args.pilot_csv.name} sha={sha16(args.pilot_csv)}"
    else:
        users = make_synthetic_users(args.n, args.noise, rng)
        source = f"synthetic(n={args.n}, noise={args.noise}, seed={SEED})"
    classifier, clf_report = train_classifier(users, source)

    log("Step 2: returns and covariance")
    stats, boot, returns = estimate_asset_stats(args.dividend_yield, rng)
    log(f"  period {stats['period'][0]}..{stats['period'][1]} ({stats['nMonths']} months)  "
        f"mu MMF {stats['mu'][0]*100:.2f}%  Equity {stats['mu'][1]*100:.2f}%")
    for w in stats["warnings"]:
        log("  WARNING:", w)

    log("Step 3: allocation")
    alloc, alloc_report = build_allocation(stats, boot)
    for c in CLASSES:
        s = alloc["pointSplits"][c]
        log(f"  {c:13s} MMF {s['MMF']:3d}%  Equity {s['Equity']:3d}%  exp {s['expectedReturnPct']:.2f}%  vol {s['volatilityPct']:.2f}%"
            + ("  (on equity floor)" if s["onEquityFloor"] else ""))

    args.out.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, obj in (("risk_classifier.json", classifier), ("asset_stats.json", stats), ("allocation_config.json", alloc)):
        (args.out / name).write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")
    (OUTPUT / "bootstrap.json").write_text(json.dumps(boot), encoding="utf-8")
    returns.to_csv(OUTPUT / "monthly_returns.csv")
    report = {"modelVersion": MODEL_VERSION, "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
              "nasiSha16": sha16(ML / "data" / "NASI_daily_2008_2025.csv"),
              "classifier": clf_report, "assetStats": {k: stats[k] for k in ("period", "nMonths", "mu", "muCi95", "warnings")},
              "allocation": alloc_report}
    (args.out / "training_report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    log(f"Wrote artifacts to {os.path.relpath(args.out, ROOT)} and {os.path.relpath(OUTPUT, ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
