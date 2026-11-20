from __future__ import annotations

import csv
import hashlib
import hmac
import io
from collections import Counter, defaultdict
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import settings
from ..db import get_db
from ..models import StudyParticipant, StudyScreening
from ..schemas import ApiModel
from ..security import require_admin
from ..util import as_utc, now_utc
from . import instrument as ins
from .service import (assign_condition, build_stimulus, config_snapshot, score_likert, score_literacy)

router = APIRouter(prefix="/api/v1/study", tags=["study"])
admin = APIRouter(prefix="/api/v1/admin/study", tags=["study-admin"], dependencies=[Depends(require_admin)])

REASONS = {"no_consent": "You need to agree to take part to continue.", "under_18": "You must be at least 18 to take part.",
           "not_enrolled": "This study is only for current Strathmore University undergraduates.",
           "no_recent_mobile_money": "This study is for students who have used mobile money in the last 30 days.",
           "bad_code": "That access code is not valid."}


class ScreenIn(ApiModel):
    consent: bool
    age_18_or_over: bool
    enrolled_strathmore_undergraduate: bool
    used_mobile_money_last_30_days: bool
    year_of_study: int = Field(ge=1, le=6)
    access_code: str | None = None


class BaselineIn(ApiModel):
    mobile_money_frequency: Literal["daily", "weekly", "monthly", "rarely"]
    fintech_experience: list[int]
    instruments_used: list[str]
    previous_investment: bool
    literacy: list[str]
    algo_trust: list[int]
    goal: Literal["emergency_fund", "school_fees", "start_business", "buy_asset", "general_savings"]
    time_horizon: int = Field(ge=1, le=3)
    risk_preference: int = Field(ge=1, le=3)
    income_stability: int = Field(ge=1, le=3)


class OutcomesIn(ApiModel):
    trust: list[int]
    adoption: list[int]
    fit: list[int]
    perceived_personalisation: list[int]
    manipulation_ai_noticed: Literal["yes", "no", "unsure"]
    attention_response: int


def _get(db: Session, pid: str) -> StudyParticipant:
    p = db.get(StudyParticipant, pid)
    if p is None:
        raise HTTPException(404, "Session not found. It may have been withdrawn.")
    return p


def _screen_log(db: Session, eligible: bool, reason: str | None) -> None:
    db.add(StudyScreening(eligible=eligible, reason=reason))
    db.commit()


@router.get("/instrument")
def get_instrument():
    return ins.instrument_payload()


@router.get("/config")
def get_config():
    """Frozen settings. Publish this (and its instrumentHash) in the pre-registration / appendix BEFORE data collection."""
    return config_snapshot()


@router.get("/debrief")
def get_debrief():
    return {"text": ins.DEBRIEF}


@router.post("/screen")
def screen(body: ScreenIn, db: Session = Depends(get_db)):
    L = settings.study_likert_points  # noqa: F841 - keeps config validated at import time
    checks = [(body.consent, "no_consent"), (body.age_18_or_over, "under_18"),
              (body.enrolled_strathmore_undergraduate, "not_enrolled"), (body.used_mobile_money_last_30_days, "no_recent_mobile_money")]
    if settings.study_access_code and not hmac.compare_digest(body.access_code or "", settings.study_access_code):
        checks.insert(0, (False, "bad_code"))
    for ok, reason in checks:
        if not ok:
            _screen_log(db, False, reason)
            return {"eligible": False, "reason": reason, "message": REASONS[reason]}
    p = StudyParticipant(year_of_study=body.year_of_study)
    db.add(p)
    db.add(StudyScreening(eligible=True))
    db.commit()
    return {"eligible": True, "participantId": p.id}


@router.post("/{pid}/baseline")
def baseline(pid: str, body: BaselineIn, db: Session = Depends(get_db)):
    p = _get(db, pid)
    if p.assigned_at is not None:
        raise HTTPException(409, "Baseline cannot be changed after the recommendation has been shown")
    L = settings.study_likert_points
    allowed = set(ins.FORMAL_INSTRUMENTS + ins.INFORMAL_INSTRUMENTS)
    if not set(body.instruments_used) <= allowed:
        raise HTTPException(422, "unknown financial instrument")
    p.literacy_score = score_literacy(body.literacy)
    p.algo_trust_mean = score_likert(body.algo_trust, ins.reverse_flags("algo_trust"), L, "algo_trust")
    p.fintech_mean = score_likert(body.fintech_experience, ins.reverse_flags("fintech"), L, "fintech")
    used = sorted(set(body.instruments_used))
    p.baseline = {"mobileMoneyFrequency": body.mobile_money_frequency, "instrumentsUsed": used,
                  "formalCount": sum(i in ins.FORMAL_INSTRUMENTS for i in used), "informalCount": sum(i in ins.INFORMAL_INSTRUMENTS for i in used),
                  "previousInvestment": body.previous_investment, "literacy": body.literacy, "algoTrust": body.algo_trust,
                  "fintech": body.fintech_experience, "goal": body.goal, "timeHorizon": body.time_horizon,
                  "riskPreference": body.risk_preference, "incomeStability": body.income_stability}
    db.commit()
    return {"status": "baseline_saved"}


@router.post("/{pid}/assign")
def assign(pid: str, db: Session = Depends(get_db)):
    """Randomisation happens HERE - after the baseline is complete - so the trust-in-algorithms scale can never be
    influenced by the stimulus (Chapter 3, instrument item 4). Idempotent: a refresh returns the same condition."""
    p = _get(db, pid)
    if p.baseline is None:
        raise HTTPException(409, "Complete the background questions first")
    if p.assigned_at is None:
        assign_condition(db, p)
        p.assigned_at = now_utc()
        stimulus = build_stimulus(p)
        db.commit()
    else:
        stimulus = build_stimulus(p)
    return {"stimulus": stimulus}


@router.post("/{pid}/outcomes")
def outcomes(pid: str, body: OutcomesIn, db: Session = Depends(get_db)):
    p = _get(db, pid)
    if p.assigned_at is None:
        raise HTTPException(409, "No recommendation has been shown yet")
    if p.completed_at is not None:
        raise HTTPException(409, "Already submitted")
    L = settings.study_likert_points
    if body.attention_response < 1 or body.attention_response > L:
        raise HTTPException(422, f"attention response must be 1-{L}")
    p.trust_mean = score_likert(body.trust, ins.reverse_flags("trust"), L, "trust")
    p.adoption_mean = score_likert(body.adoption, ins.reverse_flags("adoption"), L, "adoption")
    p.fit_mean = score_likert(body.fit, ins.reverse_flags("fit"), L, "fit")
    p.perceived_personalisation_mean = score_likert(body.perceived_personalisation, ins.reverse_flags("perceived_personalisation"), L, "perceived_personalisation")
    p.manipulation_ai_noticed = body.manipulation_ai_noticed
    p.attention_passed = body.attention_response == L - 1
    p.outcomes = {"trust": body.trust, "adoption": body.adoption, "fit": body.fit,
                  "perceivedPersonalisation": body.perceived_personalisation, "attention": body.attention_response}
    p.completed_at = now_utc()
    db.commit()
    return {"status": "completed"}


@router.delete("/{pid}", status_code=204)
def withdraw(pid: str, db: Session = Depends(get_db)):
    """Withdrawal at any time (Chapter 3, 3.6). Deletes every stored answer for this participant."""
    p = db.get(StudyParticipant, pid)
    if p is not None:
        db.delete(p)
        db.commit()
    return Response(status_code=204)


# ----------------------------------------------------------------------------------------------- admin
def _pseudo(pid: str) -> str:
    return hashlib.sha256(f"{pid}{settings.otp_pepper}".encode()).hexdigest()[:12]


def _usable(p: StudyParticipant) -> bool:
    return bool(p.completed_at and p.attention_passed)


@admin.get("/progress")
def progress(db: Session = Depends(get_db)):
    people = db.scalars(select(StudyParticipant)).all()
    cells: dict[str, dict] = defaultdict(lambda: {"assigned": 0, "completed": 0, "usable": 0})
    for p in people:
        if p.personalized is None:
            continue
        key = f"year{p.year_of_study}|{'personalised' if p.personalized else 'generic'}|{'AI-label' if p.ai_label else 'no-label'}"
        cells[key]["assigned"] += 1
        cells[key]["completed"] += bool(p.completed_at)
        cells[key]["usable"] += _usable(p)
    arm = Counter()
    for p in people:
        if _usable(p):
            arm[f"{'personalised' if p.personalized else 'generic'}|{'AI-label' if p.ai_label else 'no-label'}"] += 1
    screen = Counter("eligible" if s.eligible else f"excluded:{s.reason}" for s in db.scalars(select(StudyScreening)))
    usable = sum(_usable(p) for p in people)
    return {"screening": dict(screen), "started": len(people), "baselineDone": sum(p.baseline is not None for p in people),
            "assigned": sum(p.personalized is not None for p in people), "completed": sum(bool(p.completed_at) for p in people),
            "usable": usable, "usableTarget": 480, "recruitTarget": 690, "usablePerArm": dict(arm), "perArmTarget": 120, "cells": dict(cells)}


@admin.get("/export.csv")
def export_csv(include_all: bool = False, db: Session = Depends(get_db)):
    """One row per participant who finished the baseline (E2 needs them even if they never finished the experiment).
    Pseudonymised: random participant code, no timestamps beyond duration, no IP, no contact details."""
    rows = [p for p in db.scalars(select(StudyParticipant).order_by(StudyParticipant.created_at)) if p.baseline is not None or include_all]
    L = settings.study_likert_points
    item_cols = [(n, i["id"]) for n in ("algo_trust", "fintech", "trust", "adoption", "fit", "perceived_personalisation") for i in ins.SCALES[n]]
    header = (["participant", "status", "usable", "year_of_study", "personalised", "ai_label", "same_as_generic", "stratum_seq",
               "mobile_money_frequency", "previous_investment", "formal_count", "informal_count", "participates_formal",
               "literacy_score", "algo_trust_mean", "fintech_mean", "goal", "time_horizon", "risk_preference", "income_stability",
               "trust_mean", "adoption_mean", "fit_mean", "perceived_personalisation_mean", "manipulation_ai_noticed",
               "attention_passed", "duration_seconds", "likert_points"] + [c[1] for c in item_cols])
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(header)
    for p in rows:
        b, o = p.baseline or {}, p.outcomes or {}
        status = "completed" if p.completed_at else ("assigned" if p.assigned_at else ("baseline" if p.baseline else "screened"))
        dur = int((as_utc(p.completed_at) - as_utc(p.created_at)).total_seconds()) if p.completed_at else ""
        src = {"algo_trust": b.get("algoTrust", []), "fintech": b.get("fintech", []), "trust": o.get("trust", []), "adoption": o.get("adoption", []),
               "fit": o.get("fit", []), "perceived_personalisation": o.get("perceivedPersonalisation", [])}
        items = []
        for n in ("algo_trust", "fintech", "trust", "adoption", "fit", "perceived_personalisation"):
            vals = src[n] or [""] * len(ins.SCALES[n])
            items += vals
        w.writerow([_pseudo(p.id), status, int(_usable(p)), p.year_of_study, "" if p.personalized is None else int(p.personalized),
                    "" if p.ai_label is None else int(p.ai_label), "" if p.same_as_generic is None else int(p.same_as_generic), p.stratum_seq if p.stratum_seq is not None else "",
                    b.get("mobileMoneyFrequency", ""), "" if "previousInvestment" not in b else int(b["previousInvestment"]),
                    b.get("formalCount", ""), b.get("informalCount", ""), "" if "formalCount" not in b else int(b["formalCount"] > 0),
                    p.literacy_score if p.literacy_score is not None else "", p.algo_trust_mean if p.algo_trust_mean is not None else "",
                    p.fintech_mean if p.fintech_mean is not None else "", b.get("goal", ""), b.get("timeHorizon", ""), b.get("riskPreference", ""), b.get("incomeStability", ""),
                    p.trust_mean if p.trust_mean is not None else "", p.adoption_mean if p.adoption_mean is not None else "", p.fit_mean if p.fit_mean is not None else "",
                    p.perceived_personalisation_mean if p.perceived_personalisation_mean is not None else "", p.manipulation_ai_noticed or "",
                    "" if p.attention_passed is None else int(p.attention_passed), dur, L] + items)
    return Response(buf.getvalue(), media_type="text/csv")
