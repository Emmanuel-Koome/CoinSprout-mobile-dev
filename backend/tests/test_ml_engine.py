"""The runtime AI engine (app/ml/engine.py) against the artifacts produced by ml/train.py."""
import itertools

import pytest

from app.ml import engine as e

ALL_ANSWERS = list(itertools.product(range(1, 6), repeat=4))


def test_artifacts_load_and_agree():
    a = e.artifacts()
    assert a.model_version == e.MODEL_VERSION == "ml-v1"
    assert a.stats["assets"] == ["MMF", "Equity"]


def test_every_answer_combination_gives_a_valid_split():
    for ans in ALL_ANSWERS:
        rec = e.recommend(*ans)
        assert set(rec.allocation) == {"mmf", "equity"}
        assert sum(rec.allocation.values()) == 100 and min(rec.allocation.values()) >= 0
        assert rec.category in e.CATEGORIES and 0 < rec.confidence <= 1
        assert abs(sum(rec.probabilities.values()) - 1) < 0.01
        assert rec.reasoning.startswith("Your answers fit")


def test_extremes_and_monotonicity():
    assert e.recommend(1, 1, 1, 1).category == "Conservative"
    assert e.recommend(5, 5, 5, 5).category == "Aggressive"
    # raising any single answer never lowers the probability of Aggressive (the model is linear in each answer)
    for ans in ALL_ANSWERS:
        for j in range(4):
            if ans[j] < 5:
                up = list(ans); up[j] += 1
                assert e.classify(tuple(up))["Aggressive"] >= e.classify(ans)["Aggressive"] - 1e-12


def test_category_ordering_of_equity_share():
    eq = [e.allocation_for(c)[0]["equity"] for c in e.CATEGORIES]
    assert eq == sorted(eq)


def test_allocation_matches_the_training_run():
    table = e.artifacts().allocation["pointSplits"]
    for c in e.CATEGORIES:
        alloc, _ = e.allocation_for(c)
        assert alloc == {"mmf": table[c]["MMF"], "equity": table[c]["Equity"]}


def test_short_horizon_guardrail_caps_equity():
    rec = e.recommend(1, 5, 5, 5)          # very risk-tolerant, but needs the money within a year
    assert rec.allocation["equity"] <= 10 and "within a year" in rec.reasoning


def test_closed_form_optimiser():
    mu, cov = [0.10, 0.15], [[0.0001, 0.0], [0.0, 0.04]]
    # unconstrained optimum: (d/lam + var_m - c) / v = (0.05/2 + 0.0001) / 0.0401
    assert e.optimal_equity_share(mu, cov, 2.0, 0, 1) == pytest.approx((0.025 + 0.0001) / 0.0401)
    assert e.optimal_equity_share(mu, cov, 2.0, 0, 0.3) == 0.3          # clipped to the cap
    assert e.optimal_equity_share([0.10, 0.02], cov, 2.0, 0.1, 0.3) == 0.1   # equities worse: floor


def test_portfolio_stats():
    mmf_only = e.portfolio_stats({"mmf": 100, "equity": 0})
    mixed = e.portfolio_stats({"mmf": 50, "equity": 50})
    assert mmf_only[0] == pytest.approx(e.artifacts().stats["mu"][0] * 100, abs=0.01)
    assert mixed[1] > mmf_only[1]


def test_rejects_out_of_range_answers():
    with pytest.raises(ValueError):
        e.recommend(0, 3, 3, 3)
    with pytest.raises(ValueError):
        e.recommend(3, 3, 3, 6)
