"""Database schema. All money is stored as integer cents (KES x 100) - never floats."""
from __future__ import annotations

import uuid
from datetime import date, datetime, timezone

from sqlalchemy import JSON, Boolean, Date, DateTime, Float, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


def _uuid() -> str:
    return str(uuid.uuid4())


def _now() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    phone: Mapped[str] = mapped_column(String(16), unique=True, index=True)  # canonical +2547XXXXXXXX
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    # Risk profile (quiz answers 1..5) and the stored recommendation (app/ml/engine.py)
    time_horizon: Mapped[int | None] = mapped_column(Integer, nullable=True)
    risk_tolerance: Mapped[int | None] = mapped_column(Integer, nullable=True)
    income_stability: Mapped[int | None] = mapped_column(Integer, nullable=True)
    investment_experience: Mapped[int | None] = mapped_column(Integer, nullable=True)
    risk_category: Mapped[str | None] = mapped_column(String(16), nullable=True)
    model_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    model_reasoning: Mapped[str | None] = mapped_column(Text, nullable=True)
    model_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    alloc_mmf: Mapped[int | None] = mapped_column(Integer, nullable=True)      # money market fund, %
    alloc_equity: Mapped[int | None] = mapped_column(Integer, nullable=True)   # NSE equities, %



class UserSettings(Base):
    __tablename__ = "user_settings"
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    round_up_increment_cents: Mapped[int] = mapped_column(Integer, default=1000)   # KES 10
    max_round_up_cents: Mapped[int] = mapped_column(Integer, default=5000)         # per purchase, KES 50
    daily_cap_cents: Mapped[int] = mapped_column(Integer, default=20000)           # KES 200
    stk_threshold_cents: Mapped[int] = mapped_column(Integer, default=10000)       # KES 100
    sms_listener_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    auto_stk_enabled: Mapped[bool] = mapped_column(Boolean, default=False)         # opt-in, not opt-out


class OtpCode(Base):
    __tablename__ = "otp_codes"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    phone: Mapped[str] = mapped_column(String(16), index=True)
    code_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    consumed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)


class Transaction(Base):
    __tablename__ = "transactions"
    __table_args__ = (UniqueConstraint("user_id", "mpesa_code", name="uq_tx_user_code"),
                      Index("ix_tx_user_time", "user_id", "occurred_at"))
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    mpesa_code: Mapped[str] = mapped_column(String(16))  # M-Pesa receipt, e.g. QJ12ABC345 - the idempotency key
    merchant_name: Mapped[str] = mapped_column(String(120))
    kind: Mapped[str] = mapped_column(String(16))        # PAID | AIRTIME | SENT | WITHDRAW | RECEIVED | OTHER
    amount_cents: Mapped[int] = mapped_column(Integer)
    round_up_cents: Mapped[int] = mapped_column(Integer, default=0)
    status: Mapped[str] = mapped_column(String(24), default="PENDING_ROUNDUP")  # + STK_REQUESTED, ROUTED_TO_PORTFOLIO, SKIPPED
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    stk_request_id: Mapped[str | None] = mapped_column(ForeignKey("stk_requests.id"), nullable=True)


class StkRequest(Base):
    __tablename__ = "stk_requests"
    __table_args__ = (UniqueConstraint("user_id", "idempotency_key", name="uq_stk_idem"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    purpose: Mapped[str] = mapped_column(String(16))      # ROUNDUP_SWEEP | DEPOSIT
    amount_cents: Mapped[int] = mapped_column(Integer)
    phone: Mapped[str] = mapped_column(String(16))
    status: Mapped[str] = mapped_column(String(16), default="PENDING")  # PENDING SUCCESS FAILED CANCELLED EXPIRED MISMATCH
    merchant_request_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    checkout_request_id: Mapped[str | None] = mapped_column(String(64), unique=True, nullable=True)
    receipt_number: Mapped[str | None] = mapped_column(String(32), nullable=True)
    result_code: Mapped[int | None] = mapped_column(Integer, nullable=True)
    result_desc: Mapped[str | None] = mapped_column(String(255), nullable=True)
    idempotency_key: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class LedgerEntry(Base):
    """Append-only record of money that actually reached the portfolio (confirmed by M-Pesa)."""
    __tablename__ = "ledger_entries"
    __table_args__ = (UniqueConstraint("stk_request_id", name="uq_ledger_stk"),)  # credit at most once per STK
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(16))  # DEPOSIT | ROUNDUP_SWEEP
    amount_cents: Mapped[int] = mapped_column(Integer)
    stk_request_id: Mapped[str] = mapped_column(ForeignKey("stk_requests.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class Goal(Base):
    __tablename__ = "goals"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(60))
    emoji: Mapped[str] = mapped_column(String(8), default="🎯")
    target_cents: Mapped[int] = mapped_column(Integer)
    saved_cents: Mapped[int] = mapped_column(Integer, default=0)
    deadline: Mapped[date | None] = mapped_column(Date, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)


class Notification(Base):
    __tablename__ = "notifications"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    message: Mapped[str] = mapped_column(String(300))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False)


class MarketRate(Base):
    __tablename__ = "market_rates"
    id: Mapped[str] = mapped_column(String(32), primary_key=True)   # slug e.g. "mmf"
    key: Mapped[str] = mapped_column(String(16), unique=True)       # mmf | equity
    asset_class: Mapped[str] = mapped_column(String(64))
    annual_yield_rate: Mapped[float] = mapped_column(Float)         # percent p.a.
    effective_date: Mapped[date] = mapped_column(Date)
    provider_name: Mapped[str] = mapped_column(String(80))
    risk_score: Mapped[int] = mapped_column(Integer)                # 1 (low) .. 3 (high)
    illustrative: Mapped[bool] = mapped_column(Boolean, default=True)  # True until an admin loads real data



class Agent(Base):
    """M-Pesa / bank agent points. Seed rows are DEMO data (illustrative=True) until a real directory is loaded."""
    __tablename__ = "agents"
    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    name: Mapped[str] = mapped_column(String(80))
    agent_number: Mapped[str] = mapped_column(String(16))
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    illustrative: Mapped[bool] = mapped_column(Boolean, default=True)


class StudyParticipant(Base):
    """Research participant. Deliberately NOT linked to User: no phone number, no e-mail, no M-Pesa data."""
    __tablename__ = "study_participants"
    __table_args__ = (UniqueConstraint("year_of_study", "stratum_seq", name="uq_study_stratum_seq"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)   # unguessable; acts as the session token
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    consented_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    year_of_study: Mapped[int] = mapped_column(Integer)                            # randomisation stratum
    baseline: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    literacy_score: Mapped[int | None] = mapped_column(Integer, nullable=True)
    algo_trust_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    fintech_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    stratum_seq: Mapped[int | None] = mapped_column(Integer, nullable=True)
    personalized: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    ai_label: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    same_as_generic: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    assigned_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    outcomes: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    trust_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    adoption_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    fit_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    perceived_personalisation_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    manipulation_ai_noticed: Mapped[str | None] = mapped_column(String(8), nullable=True)
    attention_passed: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class StudyScreening(Base):
    """Anonymous screening log, so the final report can show a participant-flow diagram (screened -> eligible -> usable)."""
    __tablename__ = "study_screenings"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    eligible: Mapped[bool] = mapped_column(Boolean)
    reason: Mapped[str | None] = mapped_column(String(32), nullable=True)
