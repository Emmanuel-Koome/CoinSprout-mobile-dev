from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Goal, User
from ..schemas import ContributeIn, GoalIn, GoalOut, GoalUpdate
from ..security import current_user
from ..services import ledger

router = APIRouter(prefix="/api/v1/goals", tags=["goals"])


def _out(g: Goal) -> GoalOut:
    return GoalOut(id=g.id, title=g.title, emoji=g.emoji, target_cents=g.target_cents, saved_cents=g.saved_cents,
                   deadline=g.deadline, progress=round(min(1.0, g.saved_cents / g.target_cents), 4))


def _get(db: Session, user: User, goal_id: str) -> Goal:
    g = db.scalar(select(Goal).where(Goal.id == goal_id, Goal.user_id == user.id))
    if g is None:
        raise HTTPException(404, "Goal not found")
    return g


@router.get("", response_model=list[GoalOut])
def list_goals(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return [_out(g) for g in db.scalars(select(Goal).where(Goal.user_id == user.id).order_by(Goal.created_at)).all()]


@router.post("", response_model=GoalOut, status_code=201)
def create_goal(body: GoalIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if body.deadline and body.deadline < date.today():
        raise HTTPException(422, "Deadline must be in the future")
    g = Goal(user_id=user.id, title=body.title.strip(), emoji=body.emoji, target_cents=body.target_cents, deadline=body.deadline)
    db.add(g)
    db.commit()
    return _out(g)


@router.patch("/{goal_id}", response_model=GoalOut)
def update_goal(goal_id: str, body: GoalUpdate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    g = _get(db, user, goal_id)
    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(g, k, v)
    if g.target_cents < g.saved_cents:
        raise HTTPException(422, "Target cannot be below what is already saved")
    db.commit()
    return _out(g)


@router.post("/{goal_id}/contribute", response_model=GoalOut)
def contribute(goal_id: str, body: ContributeIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Earmark part of the *confirmed* portfolio balance for a goal (no new money moves)."""
    g = _get(db, user, goal_id)
    if body.amount_cents > ledger.unallocated_cents(db, user.id):
        raise HTTPException(409, "Not enough unallocated savings")
    g.saved_cents = min(g.target_cents, g.saved_cents + body.amount_cents)
    db.commit()
    return _out(g)


@router.delete("/{goal_id}", status_code=204)
def delete_goal(goal_id: str, user: User = Depends(current_user), db: Session = Depends(get_db)):
    db.delete(_get(db, user, goal_id))     # earmarked amount returns to unallocated automatically
    db.commit()
    return Response(status_code=204)
