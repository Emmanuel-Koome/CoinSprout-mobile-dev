from __future__ import annotations

import math

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Agent

DEMO_AGENTS = [
    ("ag-1", "Safaricom Shop Kimathi St", "254001", -1.2851, 36.8210),
    ("ag-2", "KCB Bank Agent Junction", "254109", -1.2875, 36.8192),
    ("ag-3", "Naivas Supermarket Agent", "254332", -1.2890, 36.8155),
    ("ag-4", "Co-op Bank Express Agent", "254884", -1.2840, 36.8230),
    ("ag-5", "Strathmore University Agent", "255120", -1.3098, 36.8123),
    ("ag-6", "Westlands Sarit Agent", "255777", -1.2635, 36.8030),
]


def seed_agents_if_empty(db: Session) -> None:
    if db.scalar(select(Agent.id).limit(1)) is None:
        db.add_all([Agent(id=i, name=n, agent_number=num, latitude=la, longitude=lo, illustrative=True) for i, n, num, la, lo in DEMO_AGENTS])
        db.commit()


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))
