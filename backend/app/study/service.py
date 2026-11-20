from __future__ import annotations

import secrets

from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..config import settings
from ..models import StudyParticipant
from ..services import risk_model
from . import instrument as ins

CONDITIONS = [(True, True), (True, False), (False, True), (False, False)]   # (personalised, ai_label)
NEUTRAL_SAVINGS_CENTS = 500_000     # the study does not ask about savings; this value triggers no capacity adjustment


def _ratio() -> float | None:
    raw = settings.study_equity_vol_ratio.strip()
    return float(raw) if raw else None


def score_likert(values: list[int], reverse: list[bool], points: int, name: str) -> float:
    if len(values) != len(reverse):
        raise HTTPException(422, f"'{name}' needs {len(reverse)} answers")
    if any((not isinstance(v, int)) or v < 1 or v > points for v in values):
        raise HTTPException(422, f"'{name}' answers must be whole numbers from 1 to {points}")
    return round(sum((points + 1 - v) if r else v for v, r in zip(values, reverse)) / len(values), 4)


def score_literacy(answers: list[str]) -> int:
    if len(answers) != len(ins.LITERACY):
        raise HTTPException(422, f"literacy needs {len(ins.LITERACY)} answers")
    valid = [{o[0] for o in q["options"]} for q in ins.LITERACY]
    if any(a not in v for a, v in zip(answers, valid)):
        raise HTTPException(422, "invalid literacy option")
    return sum(1 for a, q in zip(answers, ins.LITERACY) if a == q["correct"])     # "don't know" counts as incorrect


def assign_condition(db: Session, p: StudyParticipant) -> None:
    """Stratified permuted-block randomisation: within each year of study, every consecutive block of 4
    participants contains each of the 4 conditions exactly once, in random order (Chapter 3, 3.4.1)."""
    for _ in range(6):
        max_seq = db.scalar(select(func.max(StudyParticipant.stratum_seq)).where(StudyParticipant.year_of_study == p.year_of_study))
        seq = 0 if max_seq is None else max_seq + 1
        start = (seq // 4) * 4
        used = {(r.personalized, r.ai_label) for r in db.scalars(select(StudyParticipant).where(
            StudyParticipant.year_of_study == p.year_of_study, StudyParticipant.stratum_seq >= start, StudyParticipant.stratum_seq < seq))}
        remaining = [c for c in CONDITIONS if c not in used] or CONDITIONS
        p.stratum_seq = seq
        p.personalized, p.ai_label = secrets.choice(remaining)
        try:
            db.flush()
            return
        except IntegrityError:           # two participants raced for the same sequence number: retry
            db.rollback()
            db.refresh(p)
    raise HTTPException(503, "Could not assign a condition, please retry")


def build_stimulus(p: StudyParticipant) -> dict:
    ratio = _ratio()
    ref = risk_model.reference_recommendation(ratio)
    b = p.baseline or {}
    if p.personalized:
        rec = risk_model.recommend(b["timeHorizon"], b["riskPreference"], b["incomeStability"], NEUTRAL_SAVINGS_CENTS, ratio)
        why = (f"Based on your goal ({ins.GOALS[b['goal']]}), your time horizon ({ins.HORIZONS[b['timeHorizon']]}) "
               f"and how you feel about ups and downs ({ins.TOLERANCES[b['riskPreference']]}).")
    else:
        rec = ref
        why = "A standard split suggested for savers in general."
    p.same_as_generic = bool(p.personalized and rec.allocation == ref.allocation)
    # IDENTICAL layout in all four conditions; only the allocation, the 'why' sentence and the AI line differ.
    return {"headline": "Suggested split for your savings", "allocation": rec.allocation, "whyText": why,
            "aiLabelText": ins.AI_LABEL_TEXT if p.ai_label else None, "disclaimer": ins.DISCLAIMER}


def config_snapshot() -> dict:
    ratio = _ratio()
    ref = risk_model.reference_recommendation(ratio)
    return {"studyVersion": "study-v1", "instrumentStatus": ins.STATUS, "instrumentHash": ins.instrument_hash(),
            "engineVersion": risk_model.MODEL_VERSION, "genericAllocation": ref.allocation,
            "referenceProfile": {"horizon": 2, "tolerance": 2, "income": 2}, "equityVolRatioFrozen": ratio,
            "likertPoints": settings.study_likert_points, "randomisation": "stratified (year of study) permuted blocks of 4",
            "targets": {"usablePerCell": 120, "usableTotal": 480, "recruit": 690}}
