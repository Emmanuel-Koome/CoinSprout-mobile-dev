import numpy as np
import pytest

from backtest import synthetic, walk_forward as wf


def test_runs_on_208_days_and_all_hard_constraints_hold():
    res = wf.run(synthetic.make(208))
    assert res["period"]["trading_days"] == 208 and res["period"]["rebalances"] >= 25
    assert res["constraint_compliance"]["violations"] == 0 and res["constraint_compliance"]["compliance_rate"] == 1.0
    assert res["risk_ordering"]["dates_where_equity_weight_not_monotone"] == 0
    assert "not a test of outperformance" in res["scope"].lower() and "descriptive_performance_NOT_A_TEST" in res


def test_NO_LOOKAHEAD_changing_the_future_cannot_change_past_allocations():
    base = synthetic.make(208, seed=11)
    cutoff = 150
    shocked = base.copy()
    rng = np.random.default_rng(0)
    shocked.loc[cutoff + 1:, "nse_index"] = base.loc[cutoff + 1:, "nse_index"].to_numpy() * np.exp(rng.normal(0, 0.15, 208 - cutoff - 1).cumsum())
    shocked.loc[cutoff + 1:, "tbill_yield"] = 40.0
    a = _allocs(base, cutoff); b = _allocs(shocked, cutoff)
    assert a == b and len(a) > 10


def _allocs(df, cutoff):
    """Re-run the decision logic exactly as run() does, collecting allocations for decision days <= cutoff."""
    from app.services import risk_model as rm
    px = df["nse_index"].to_numpy(float); rets = np.full(len(px), np.nan); rets[1:] = np.diff(np.log(px))
    out = []
    for i in range(60, cutoff + 1, 5):
        ratio = wf._vol_ratio(rets, i, 60)
        out.append(tuple(rm.recommend(*wf.PROFILES["middle"], 500_000, ratio).allocation.items()))
    return out


def test_engine_reacts_to_turbulence_in_the_right_direction_and_within_bounds():
    res = wf.run(synthetic.make(208, storm_from=120, storm_to=150))
    s = res["allocation_stability"]["middle"]
    assert s["max_abs_change_pp"]["unit"] > 0                         # the tilt does something
    assert res["equity_vol_ratio"]["min"] < 0.75                      # storm drives ratio below the lower clamp
    assert s["max_abs_change_pp"]["unit"] <= 10                       # but a weekly move is small


def test_calm_market_gives_stable_allocations():
    res = wf.run(synthetic.make(208, storm_from=0, storm_to=0))
    assert res["allocation_stability"]["middle"]["mean_turnover_pct_per_rebalance"] < 1.5


def test_costs_reduce_tilted_returns_and_missing_data_is_reported(tmp_path):
    df = synthetic.make(208)
    p = tmp_path / "d.csv"; df.to_csv(p, index=False)
    d, meta = wf.load_prices(str(p))
    assert meta["rows_dropped_missing"] == 0
    free = wf.run(d, wf.Config(cost_bps=0))["descriptive_performance_NOT_A_TEST"]["middle"]["engine_with_tilt"]["total_return_pct"]
    costly = wf.run(d, wf.Config(cost_bps=200))["descriptive_performance_NOT_A_TEST"]["middle"]["engine_with_tilt"]["total_return_pct"]
    assert costly < free
    df.loc[10:12, "mmf_yield"] = np.nan; df.loc[20, "nse_index"] = np.nan
    df.to_csv(p, index=False)
    _, meta2 = wf.load_prices(str(p))
    assert meta2["rows_dropped_missing"] == 1 and meta2["rows_in"] == 208      # yield gaps forward-filled; missing index row dropped


def test_too_little_data_is_rejected_with_a_clear_error():
    with pytest.raises(ValueError):
        wf.run(synthetic.make(70))


def test_markdown_summary_renders():
    md = wf.to_markdown(wf.run(synthetic.make(208)))
    assert "Constraint compliance: 100.0%" in md


def test_latest_ratio_matches_the_value_the_walk_forward_would_use_on_that_day():
    df = synthetic.make(208)
    px = df["nse_index"].to_numpy(float); rets = np.full(len(px), np.nan); rets[1:] = np.diff(np.log(px))
    assert wf.latest_ratio(df) == round(wf._vol_ratio(rets, len(px) - 1, 60), 4)
    assert wf.latest_ratio(df.iloc[:150]) != wf.latest_ratio(df)         # depends only on the data supplied
