from app.services import forecast as fc, risk_model as rm


def test_allocation_always_sums_to_100_and_is_non_negative():
    for h in (1, 2, 3):
        for t in (1, 2, 3):
            for i in (1, 2, 3):
                for savings in (0, 1_000_000, 9_000_000):
                    rec = rm.recommend(h, t, i, savings)
                    assert sum(rec.allocation.values()) == 100
                    assert all(v >= 0 for v in rec.allocation.values())
                    assert rec.allocation["cash"] >= 5
                    assert 0 < rec.confidence <= 1


def test_monotonic_in_risk_appetite():
    low = rm.recommend(1, 1, 1, 0).allocation["unit"]
    high = rm.recommend(3, 3, 3, 9_000_000).allocation["unit"]
    assert high > low


def test_short_horizon_guardrail_caps_equity():
    rec = rm.recommend(1, 3, 3, 9_000_000)       # tolerant but needs the money within a year
    assert rec.allocation["unit"] <= 10


def test_irregular_income_keeps_bigger_cash_buffer():
    assert rm.recommend(3, 3, 1, 9_000_000).allocation["cash"] >= 10


def test_forecast_range_is_ordered_and_includes_a_loss_case():
    alloc = rm.BASE_ALLOCATIONS["Aggressive"]
    alloc = {k: int(v) for k, v in alloc.items()}
    out = fc.forecast(1_000_000, 0, alloc, {"mmf": 11.85, "tbill": 15.42, "unit": 12.10}, 12)
    assert out["pessimisticValueCents"] < out["projectedValueCents"] < out["optimisticValueCents"]
    assert out["pessimisticAnnualReturnPct"] < out["expectedAnnualReturnPct"]
    assert out["inflationAdjustedValueCents"] < out["projectedValueCents"]


def test_future_value_matches_closed_form():
    assert fc.future_value_cents(100_000, 0, 12.0, 12) == round(100_000 * (1.01 ** 12))
    assert fc.future_value_cents(0, 10_000, 0.0, 6) == 60_000


def test_vol_tilt_is_bounded_direction_correct_and_keeps_invariants():
    base = rm.recommend(2, 2, 2, 500_000)
    calm = rm.recommend(2, 2, 2, 500_000, equity_vol_ratio=5.0)       # absurdly calm -> capped at +15% relative
    storm = rm.recommend(2, 2, 2, 500_000, equity_vol_ratio=0.01)     # absurdly turbulent -> capped at -25% relative
    assert storm.allocation["unit"] < base.allocation["unit"] < calm.allocation["unit"]
    assert storm.allocation["unit"] >= round(base.allocation["unit"] * 0.75) - 1
    assert calm.allocation["unit"] <= round(base.allocation["unit"] * 1.15) + 1
    for rec in (base, calm, storm):
        assert sum(rec.allocation.values()) == 100 and all(v >= 0 for v in rec.allocation.values())
    assert storm.tilt_pp < 0 < calm.tilt_pp and "nudged down" in storm.reasoning


def test_tilt_never_breaks_short_horizon_guardrail():
    assert rm.recommend(1, 3, 3, 9_000_000, equity_vol_ratio=5.0).allocation["unit"] <= 10


def test_reference_recommendation_is_deterministic():
    assert rm.reference_recommendation().allocation == rm.reference_recommendation().allocation
