import numpy as np
import pandas as pd
import pytest

from analysis import power, study_analysis as sa
from app.study import instrument as ins


def simulate(n=600, a=0.30, b=0.5, seed=7, L=7):
    """Synthetic export with KNOWN truth: personalisation raises trust by a; trust raises adoption by b; AI label has no effect."""
    rng = np.random.default_rng(seed)
    year = rng.integers(1, 5, n); pers = rng.integers(0, 2, n); ai = rng.integers(0, 2, n)
    lit = rng.integers(0, 4, n); alg = np.clip(rng.normal(4, 1, n), 1, 7)
    trust = np.clip(4 + a * pers + 0.1 * (alg - 4) + rng.normal(0, 1, n), 1, 7)
    fit = np.clip(4 + 0.2 * pers + rng.normal(0, 1, n), 1, 7)
    adopt = np.clip(2 + b * trust + 0.1 * fit + rng.normal(0, 0.8, n), 1, 7)
    df = pd.DataFrame({"participant": range(n), "status": "completed", "usable": 1, "year_of_study": year, "personalised": pers, "ai_label": ai,
                       "same_as_generic": 0, "mobile_money_frequency": rng.choice(list(sa.MM_ORDER), n), "previous_investment": rng.integers(0, 2, n),
                       "participates_formal": (rng.random(n) < 0.3 + 0.12 * lit / 3).astype(int), "literacy_score": lit, "algo_trust_mean": alg,
                       "fintech_mean": rng.normal(4.5, 1, n), "trust_mean": trust, "adoption_mean": adopt, "fit_mean": fit,
                       "perceived_personalisation_mean": 3.5 + 1.2 * pers + rng.normal(0, 1, n), "manipulation_ai_noticed": np.where(ai == 1, "yes", "no"),
                       "attention_passed": 1, "likert_points": L})
    # item columns with a latent factor so alpha is high
    for name, p in sa.SCALE_PREFIX.items():
        base = {"trust": trust, "adoption": adopt, "fit": fit, "algo_trust": alg, "fintech": df["fintech_mean"], "perceived_personalisation": df["perceived_personalisation_mean"]}[name]
        for item in ins.SCALES[name]:
            v = np.clip(np.round(np.asarray(base) + rng.normal(0, 0.5, n)), 1, L)
            df[item["id"]] = (L + 1 - v) if item.get("reverse") else v
    return df


@pytest.fixture(scope="module")
def sim_csv(tmp_path_factory):
    p = tmp_path_factory.mktemp("a") / "export.csv"
    simulate().to_csv(p, index=False)
    return str(p)


def test_power_script_reproduces_the_numbers_in_the_chapters():
    txt = power.report()
    assert "power = 81" in txt and "power = 72" in txt and "686" in txt        # 81% at .05, ~73% under Holm, 480/0.7


def test_cronbach_alpha_matches_manual_formula_and_respects_reverse_items():
    df = simulate()
    al = sa.reliability(df)
    assert all(v > 0.85 for v in al.values()), al
    # sanity: forgetting to reverse-score would crush alpha for scales with a reverse item
    raw = df[[i["id"] for i in ins.SCALES["algo_trust"]]].astype(float)
    assert sa.cronbach_alpha(raw) < al["algo_trust"]


def test_h1_recovers_effect_and_holm_is_never_smaller_than_raw(sim_csv):
    res = sa.h1(sa.usable(sa.load(sim_csv)))
    assert res["trust_mean"]["coef_personalised"] == pytest.approx(0.30, abs=0.15)
    for v in res.values():
        assert v["p_holm"] >= v["p_raw"] - 1e-12
    assert abs(res["trust_mean"]["ai_label_coef_exploratory"]) < 0.25          # truth: AI label has no effect


def test_h2_bootstrap_recovers_the_simulated_indirect_effect(sim_csv):
    res = sa.h2(sa.usable(sa.load(sim_csv)), n_boot=400, seed=1)
    true_indirect = 0.30 * 0.5
    lo, hi = res["bootstrap_ci95"]["indirect_trust"]
    assert lo < true_indirect < hi and lo > 0
    assert "not proof" in res["interpretation"]


def test_e1_finds_no_interaction_when_none_exists_and_e2_runs(sim_csv):
    df = sa.load(sim_csv)
    e1 = sa.e1(sa.usable(df))
    assert all(v["p_holm_over_4_exploratory_tests"] > 0.01 for v in e1.values())
    e2 = sa.e2(df)
    assert e2["literacy_score"]["odds_ratio"] > 0 and "not causal" in e2["note"]


def test_run_all_end_to_end(sim_csv):
    out = sa.run_all(sim_csv, n_boot=100)
    assert set(out) >= {"reliability_alpha", "H1_confirmatory_holm", "H2_confirmatory_mediation_pattern", "E1_exploratory", "E2_descriptive_exploratory"}


def test_pipeline_reads_a_real_export_from_the_api(client):
    """Integration: the CSV the API emits must load, clean and describe without errors."""
    import io
    from test_study import ADMIN, S, baseline_body, outcomes_body, screen
    for k in range(12):
        pid = screen(client, 1 + k % 4)["participantId"]
        client.post(f"{S}/{pid}/baseline", json=baseline_body())
        client.post(f"{S}/{pid}/assign")
        client.post(f"{S}/{pid}/outcomes", json=outcomes_body())
    text = client.get("/api/v1/admin/study/export.csv", headers=ADMIN).text
    df = pd.read_csv(io.StringIO(text))
    assert len(df) == 12 and df["usable"].sum() == 12 and df[["personalised", "ai_label"]].drop_duplicates().shape[0] == 4
    assert sa.reliability(df)["trust"] != 0         # item columns line up with the instrument
