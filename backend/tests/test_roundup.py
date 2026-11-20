import pytest

from app.services.roundup import apply_daily_cap, compute_round_up_cents


@pytest.mark.parametrize("amount,inc,cap,expected", [
    (8500, 1000, 5000, 500),     # KES 85 -> 90
    (8500, 5000, 5000, 1500),    # KES 85 -> 100
    (10000, 5000, 5000, 0),      # already a multiple
    (8550, 1000, 5000, 400),     # KES 85.50 treated as 86 -> 90
    (500, 10000, 5000, 5000),    # KES 5 with KES 100 increment is capped (would be 95)
    (0, 1000, 5000, 0),
    (-100, 1000, 5000, 0),
])
def test_round_up(amount, inc, cap, expected):
    assert compute_round_up_cents(amount, inc, cap) == expected


def test_round_up_is_always_whole_shillings():
    for amount in range(1, 20000, 37):
        assert compute_round_up_cents(amount, 1000, 5000) % 100 == 0


def test_daily_cap():
    assert apply_daily_cap(5000, 18000, 20000) == 2000
    assert apply_daily_cap(5000, 20000, 20000) == 0
    assert apply_daily_cap(500, 0, 20000) == 500
