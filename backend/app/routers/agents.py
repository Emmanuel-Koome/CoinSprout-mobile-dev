from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Agent, User
from ..schemas import AgentOut
from ..security import current_user
from ..services.agents import haversine_km

router = APIRouter(prefix="/api/v1/agents", tags=["agents"])


@router.get("/nearby", response_model=list[AgentOut])
def nearby(lat: float = Query(ge=-90, le=90), lng: float = Query(ge=-180, le=180), radius_km: float = Query(10, gt=0, le=50, alias="radiusKm"),
           limit: int = Query(10, ge=1, le=25), _: User = Depends(current_user), db: Session = Depends(get_db)):
    """The caller's coordinates are used for this request only and are never stored or logged."""
    rows = [(haversine_km(lat, lng, a.latitude, a.longitude), a) for a in db.scalars(select(Agent)).all()]
    rows = sorted((r for r in rows if r[0] <= radius_km), key=lambda r: r[0])[:limit]
    return [AgentOut(id=a.id, name=a.name, agent_number=a.agent_number, latitude=a.latitude, longitude=a.longitude,
                     distance_km=round(d, 2), illustrative=a.illustrative) for d, a in rows]
