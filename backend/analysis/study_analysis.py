"""Analysis pipeline for the Chapter 3 plan. Input: the CSV from GET /api/v1/admin/study/export.csv.

    python -m analysis.study_analysis export.csv [--boot 5000] [--seed 20261118]

Implements: cleaning -> reliability (Cronbach alpha) -> descriptives (Objective 3 / RQ4) -> H1 with Holm ->
H2 parallel mediation (trust + perceived fit) with percentile bootstrap -> E1 interactions -> E2 logistic regression.
Every inferential result is labelled confirmatory or exploratory, as pre-specified in Chapter 1.
"""
from __future__ import annotations

import argparse
import json

import numpy as np
import pandas as pd
import statsmodels.formula.api as smf
from scipy import stats
from statsmodels.stats.multitest import multipletests

from app.study import instrument as ins

MM_ORDER = {"rarely": 1, "monthly": 2, "weekly": 3, "daily": 4}
SCALE_PREFIX = {"algo_trust": "at", "fintech": "ft", "trust": "tr", "adoption": "ad", "fit": "fi", "perceived_personalisation": "pp"}


def load(path: str) -> pd.DataFrame:
    return pd.read_csv(path)


def usable(df: pd.DataFrame) -> pd.DataFrame:
    """Pre-specified exclusions: incomplete submissions and failed attention check. (Manipulation-check failures are
    NOT excluded; run the sensitivity analysis in `sensitivity_manipulation` and report both.)"""
    return df[(df["usable"] == 1)].copy()


def cronbach_alpha(items: pd.DataFrame) -> float:
    items = items.dropna()
    k = items.shape[1]
    if k < 2 or len(items) < 3 or items.sum(axis=1).var(ddof=1) == 0:
        return float("nan")
    return float(k / (k - 1) * (1 - items.var(ddof=1).sum() / items.sum(axis=1).var(ddof=1)))


def reliability(df: pd.DataFrame) -> dict:
    L = int(df["likert_points"].iloc[0])
    out = {}
    for name, prefix in SCALE_PREFIX.items():
        cols = [i["id"] for i in ins.SCALES[name]]
        rev = ins.reverse_flags(name)
        items = df[cols].astype(float).copy()
        for c, r in zip(cols, rev):
            if r:
                items[c] = (L + 1) - items[c]
        out[name] = round(cronbach_alpha(items), 3)
    return out


def cohens_d(a: pd.Series, b: pd.Series) -> dict:
    a, b = a.dropna(), b.dropna()
    n1, n2 = len(a), len(b)
    sp = np.sqrt(((n1 - 1) * a.var(ddof=1) + (n2 - 1) * b.var(ddof=1)) / (n1 + n2 - 2))
    d = (a.mean() - b.mean()) / sp
    se = np.sqrt((n1 + n2) / (n1 * n2) + d ** 2 / (2 * (n1 + n2)))
    return {"d": round(float(d), 3), "ci95": [round(float(d - 1.96 * se), 3), round(float(d + 1.96 * se), 3)]}


def descriptives(df_all: pd.DataFrame, df_use: pd.DataFrame) -> dict:
    res = {"n_baseline": int(len(df_all)), "n_usable": int(len(df_use)),
           "literacy_mean_sd": [round(df_all["literacy_score"].mean(), 2), round(df_all["literacy_score"].std(), 2)],
           "previous_investment_pct": round(100 * df_all["previous_investment"].mean(), 1),
           "participates_formal_pct": round(100 * df_all["participates_formal"].mean(), 1),
           "mobile_money_frequency": df_all["mobile_money_frequency"].value_counts().to_dict(),
           "year_of_study": df_all["year_of_study"].value_counts().sort_index().to_dict()}
    g = df_use.groupby(["personalised", "ai_label"])[["trust_mean", "adoption_mean", "fit_mean"]].agg(["mean", "std", "count"]).round(3)
    res["cells"] = {f"personalised={a},ai_label={b}": {f"{m}_{s}": float(v) for (m, s), v in row.items()} for (a, b), row in g.iterrows()}
    return res


def h1(df: pd.DataFrame) -> dict:
    """CONFIRMATORY. Outcome ~ personalised + ai_label + year-of-study; Holm across the two outcomes."""
    rows, pvals = {}, []
    for outcome in ("trust_mean", "adoption_mean"):
        m = smf.ols(f"{outcome} ~ personalised + ai_label + C(year_of_study)", data=df).fit(cov_type="HC3")
        raw = float(m.pvalues["personalised"])
        pers, gen = df[df.personalised == 1][outcome], df[df.personalised == 0][outcome]
        rows[outcome] = {"coef_personalised": round(float(m.params["personalised"]), 3), "ci95": [round(float(x), 3) for x in m.conf_int().loc["personalised"]],
                         "p_raw": raw, **{"effect_size": cohens_d(pers, gen)},
                         "mann_whitney_p": float(stats.mannwhitneyu(pers, gen, alternative="two-sided").pvalue),
                         "ai_label_coef_exploratory": round(float(m.params["ai_label"]), 3), "ai_label_p_exploratory": float(m.pvalues["ai_label"])}
        pvals.append(raw)
    adj = multipletests(pvals, method="holm")[1]
    for (k, v), pa in zip(rows.items(), adj):
        v["p_holm"] = float(pa)
    return rows


def _ols(X: np.ndarray, y: np.ndarray) -> np.ndarray:
    return np.linalg.lstsq(X, y, rcond=None)[0]


def _design(d: pd.DataFrame, extra: list[str]) -> np.ndarray:
    years = pd.get_dummies(d["year_of_study"], prefix="y", drop_first=True).astype(float)
    cols = [np.ones(len(d)), d["personalised"].values.astype(float), d["ai_label"].values.astype(float)]
    cols += [d[c].values.astype(float) for c in extra]
    return np.column_stack(cols + [years[c].values for c in years.columns])


def _paths(d: pd.DataFrame) -> dict:
    a_t = _ols(_design(d, []), d["trust_mean"].values)[1]
    a_f = _ols(_design(d, []), d["fit_mean"].values)[1]
    b = _ols(_design(d, ["trust_mean", "fit_mean"]), d["adoption_mean"].values)
    c_total = _ols(_design(d, []), d["adoption_mean"].values)[1]
    return {"a_trust": a_t, "a_fit": a_f, "b_trust": b[3], "b_fit": b[4], "direct": b[1], "total": c_total,
            "indirect_trust": a_t * b[3], "indirect_fit": a_f * b[4]}


def h2(df: pd.DataFrame, n_boot: int = 5000, seed: int = 20261118) -> dict:
    """CONFIRMATORY (association) + mediation PATTERN. Parallel mediation: personalisation -> {trust, perceived fit}
    -> adoption intention, controlling for AI label and year. Percentile bootstrap CIs. Trust and intention are
    measured in the same session, so a significant indirect effect is 'consistent with' mediation, not proof."""
    est = _paths(df)
    rng = np.random.default_rng(seed)
    draws = {k: [] for k in ("indirect_trust", "indirect_fit")}
    idx = np.arange(len(df))
    for _ in range(n_boot):
        s = df.iloc[rng.choice(idx, len(idx), replace=True)]
        if s["year_of_study"].nunique() < df["year_of_study"].nunique():
            continue
        p = _paths(s)
        for k in draws:
            draws[k].append(p[k])
    ci = {k: [round(float(np.percentile(v, 2.5)), 3), round(float(np.percentile(v, 97.5)), 3)] for k, v in draws.items()}
    assoc = smf.ols("adoption_mean ~ trust_mean + personalised + ai_label + C(year_of_study)", data=df).fit(cov_type="HC3")
    return {"paths": {k: round(float(v), 3) for k, v in est.items()}, "bootstrap_ci95": ci, "n_boot_used": len(draws["indirect_trust"]),
            "trust_to_adoption_p": float(assoc.pvalues["trust_mean"]), "trust_to_adoption_coef": round(float(assoc.params["trust_mean"]), 3),
            "interpretation": "A CI excluding 0 is a pattern consistent with mediation, not proof (same-session measurement)."}


def e1(df: pd.DataFrame) -> dict:
    """EXPLORATORY. Does the AI-label effect on trust / adoption depend on financial literacy or trust in algorithms?"""
    d = df.copy()
    d["lit_c"] = d["literacy_score"] - d["literacy_score"].mean()
    d["alg_c"] = d["algo_trust_mean"] - d["algo_trust_mean"].mean()
    out, raw = {}, []
    for outcome in ("trust_mean", "adoption_mean"):
        m = smf.ols(f"{outcome} ~ ai_label * lit_c + ai_label * alg_c + personalised + C(year_of_study)", data=d).fit(cov_type="HC3")
        for term in ("ai_label:lit_c", "ai_label:alg_c"):
            out[f"{outcome}|{term}"] = {"coef": round(float(m.params[term]), 3), "p_raw": float(m.pvalues[term])}
            raw.append(float(m.pvalues[term]))
    for (k, v), pa in zip(out.items(), multipletests(raw, method="holm")[1]):
        v["p_holm_over_4_exploratory_tests"] = float(pa)
    return out


def e2(df_all: pd.DataFrame) -> dict:
    """DESCRIPTIVE/EXPLORATORY. Logistic regression: any formal instrument ~ literacy + fintech experience + mobile-money frequency."""
    d = df_all.copy()
    d["mm_freq"] = d["mobile_money_frequency"].map(MM_ORDER)
    m = smf.logit("participates_formal ~ literacy_score + fintech_mean + mm_freq", data=d).fit(disp=0)
    ci = np.exp(m.conf_int())
    return {t: {"odds_ratio": round(float(np.exp(m.params[t])), 3), "ci95": [round(float(ci.loc[t, 0]), 3), round(float(ci.loc[t, 1]), 3)], "p": float(m.pvalues[t])}
            for t in ("literacy_score", "fintech_mean", "mm_freq")} | {"n": int(m.nobs), "note": "Associations within the sample, not causal effects."}


def manipulation_checks(df: pd.DataFrame) -> dict:
    ai = df.groupby("ai_label")["manipulation_ai_noticed"].value_counts(normalize=True).unstack(fill_value=0).round(3)
    pp = df.groupby("personalised")["perceived_personalisation_mean"].agg(["mean", "std", "count"]).round(3)
    return {"ai_label_noticed_share_by_arm": ai.to_dict("index"), "perceived_personalisation_by_arm": pp.to_dict("index"),
            "same_as_generic_among_personalised": int(df[(df.personalised == 1)]["same_as_generic"].sum())}


def run_all(path: str, n_boot: int = 5000, seed: int = 20261118) -> dict:
    df_all = load(path)
    df = usable(df_all)
    return {"reliability_alpha": reliability(df), "descriptives": descriptives(df_all[df_all["participates_formal"].notna()], df), "manipulation_checks": manipulation_checks(df),
            "H1_confirmatory_holm": h1(df), "H2_confirmatory_mediation_pattern": h2(df, n_boot, seed), "E1_exploratory": e1(df), "E2_descriptive_exploratory": e2(df_all[df_all["participates_formal"].notna()])}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--boot", type=int, default=5000)
    ap.add_argument("--seed", type=int, default=20261118)
    a = ap.parse_args()
    print(json.dumps(run_all(a.csv, a.boot, a.seed), indent=2, default=float))
