from __future__ import annotations

from collections import defaultdict
from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Transaction
from ..util import now_utc
from .ledger import kes
from .roundup import ELIGIBLE_KINDS


def build_insights(db: Session, user_id: str) -> list[dict[str, str]]:
    since = now_utc() - timedelta(days=30)
    txs = db.scalars(select(Transaction).where(Transaction.user_id == user_id, Transaction.occurred_at >= since,
                                               Transaction.kind.in_(ELIGIBLE_KINDS))).all()
    if not txs:
        return [{"id": "empty", "message": "No purchases tracked yet. Once you pay with M-Pesa, your round-ups appear here."}]
    spent = sum(t.amount_cents for t in txs)
    saved = sum(t.round_up_cents for t in txs)
    out = [{"id": "summary", "message": f"In the last 30 days you made {len(txs)} purchases ({kes(spent)}) and set aside {kes(saved)} in round-ups."}]
    by_merchant: dict[str, int] = defaultdict(int)
    for t in txs:
        by_merchant[t.merchant_name] += t.round_up_cents
    top, top_cents = max(by_merchant.items(), key=lambda kv: kv[1])
    if top_cents > 0:
        out.append({"id": "top-merchant", "message": f"{top} contributes the most to your round-ups ({kes(top_cents)})."})
    if spent and saved / spent > 0.25:
        out.append({"id": "high-share", "message": f"Round-ups equal {saved / spent:.0%} of what you spend. A smaller increment may feel lighter."})
    out.append({"id": "pace", "message": f"At this pace you would set aside about {kes(saved * 12)} over a year, before any investment returns."})
    return out
