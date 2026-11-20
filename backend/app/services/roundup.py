"""Round-up rules. Pure functions (no DB) so they are trivial to unit-test.

Design decisions (document these in the report):
* Money is integer cents. M-Pesa Express only accepts whole shillings, so round-ups are whole shillings:
  the purchase is first rounded UP to the next whole shilling, then to the next multiple of the user's increment.
* Only PAID (till/paybill) and AIRTIME purchases earn round-ups. Sends to people, withdrawals and
  received money do not - rounding up money sent to family or income would be wrong and surprising.
* A per-purchase cap and a per-day cap protect users from over-saving (a KES 5 purchase must not cost KES 95 extra).
"""
from __future__ import annotations

from datetime import datetime, timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Transaction
from ..util import EAT

ELIGIBLE_KINDS = frozenset({"PAID", "AIRTIME"})
VALID_INCREMENTS_CENTS = (1000, 2000, 5000, 10000)      # KES 10 / 20 / 50 / 100
VALID_THRESHOLDS_CENTS = (10000, 20000, 50000)          # KES 100 / 200 / 500


def compute_round_up_cents(amount_cents: int, increment_cents: int, max_cents: int) -> int:
    if amount_cents <= 0 or increment_cents < 100:
        return 0
    whole_kes = -(-amount_cents // 100)                  # ceil to whole shillings
    inc_kes = increment_cents // 100
    remainder = whole_kes % inc_kes
    if remainder == 0:
        return 0
    round_up = (inc_kes - remainder) * 100
    return min(round_up, (max_cents // 100) * 100)


def nairobi_day_bounds_utc(moment_utc: datetime) -> tuple[datetime, datetime]:
    local = moment_utc.astimezone(EAT)
    start_local = local.replace(hour=0, minute=0, second=0, microsecond=0)
    return start_local.astimezone(moment_utc.tzinfo), (start_local + timedelta(days=1)).astimezone(moment_utc.tzinfo)


def round_up_used_today_cents(db: Session, user_id: str, moment_utc: datetime) -> int:
    start, end = nairobi_day_bounds_utc(moment_utc)
    total = db.scalar(
        select(func.coalesce(func.sum(Transaction.round_up_cents), 0)).where(
            Transaction.user_id == user_id,
            Transaction.occurred_at >= start,
            Transaction.occurred_at < end,
        )
    )
    return int(total or 0)


def apply_daily_cap(round_up_cents: int, used_today_cents: int, daily_cap_cents: int) -> int:
    return max(0, min(round_up_cents, daily_cap_cents - used_today_cents))
