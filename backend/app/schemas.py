"""API contracts. JSON is camelCase (matches the Android DTOs); money is always integer *Cents."""
from __future__ import annotations

from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, PlainSerializer, field_validator
from pydantic.alias_generators import to_camel

from .util import as_utc

UtcDt = Annotated[datetime, PlainSerializer(lambda d: as_utc(d).isoformat().replace("+00:00", "Z"), return_type=str)]


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


# ---- auth
class OtpRequest(ApiModel):
    phone: str

class OtpRequested(ApiModel):
    status: str = "sent"
    expires_in_seconds: int
    dev_otp: str | None = None   # only populated when COINSPROUT_ENV=dev

class OtpVerify(ApiModel):
    phone: str
    otp: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")

class RefreshRequest(ApiModel):
    refresh_token: str

class TokenPair(ApiModel):
    access_token: str
    refresh_token: str
    expires_in_seconds: int
    is_new_user: bool = False


# ---- profile / settings
class Allocation(ApiModel):
    """Percent split across the two assets the AI engine models. Always sums to 100."""
    mmf: int      # money market fund
    equity: int   # NSE equities

class RiskProfileIn(ApiModel):
    """The 4-question risk quiz. Every answer is 1 (lowest) .. 5 (highest)."""
    time_horizon: int = Field(ge=1, le=5)
    risk_tolerance: int = Field(ge=1, le=5)
    income_stability: int = Field(ge=1, le=5)
    investment_experience: int = Field(ge=1, le=5)

class AllocationResponse(ApiModel):
    recommended_category: str
    allocation: Allocation
    confidence_score: float
    category_probabilities: dict[str, float]
    ai_reasoning: str
    expected_annual_return_pct: float      # historical estimate from ml/train.py, not a promise
    expected_annual_volatility_pct: float
    model_version: str
    disclaimer: str

class SettingsOut(ApiModel):
    round_up_increment_cents: int
    max_round_up_cents: int
    daily_cap_cents: int
    stk_threshold_cents: int
    sms_listener_enabled: bool
    auto_stk_enabled: bool

class SettingsIn(ApiModel):
    round_up_increment_cents: int | None = None
    max_round_up_cents: int | None = Field(default=None, ge=100, le=100_000)
    daily_cap_cents: int | None = Field(default=None, ge=100, le=500_000)
    stk_threshold_cents: int | None = None
    sms_listener_enabled: bool | None = None
    auto_stk_enabled: bool | None = None

class MeOut(ApiModel):
    id: str
    phone_masked: str
    created_at: UtcDt
    risk_category: str | None


# ---- rates / ai
class RateOut(ApiModel):
    id: str
    key: str
    asset_class: str
    annual_yield_rate: float
    effective_date: date
    provider_name: str
    risk_score: int
    illustrative: bool

class RateUpdate(ApiModel):
    annual_yield_rate: float = Field(ge=0, le=100)
    effective_date: date
    provider_name: str | None = None
    illustrative: bool = False

class GrowthForecastIn(ApiModel):
    principal_cents: int = Field(ge=0, le=10_000_000_000)
    monthly_contribution_cents: int = Field(default=0, ge=0, le=1_000_000_000)
    months_ahead: int = Field(ge=1, le=600)
    inflation_pct: float = Field(default=5.0, ge=0, le=50)
    risk_category: Literal["Conservative", "Moderate", "Aggressive"] | None = None  # default: user's stored allocation

class GrowthForecastOut(ApiModel):
    months_ahead: int
    total_contributed_cents: int
    pessimistic_value_cents: int
    projected_value_cents: int
    optimistic_value_cents: int
    expected_annual_return_pct: float
    pessimistic_annual_return_pct: float
    optimistic_annual_return_pct: float
    inflation_adjusted_value_cents: int
    assumed_inflation_pct: float
    assumptions: list[str]
    disclaimer: str

class InsightOut(ApiModel):
    id: str
    message: str


# ---- transactions
class TransactionIn(ApiModel):
    mpesa_code: str = Field(min_length=8, max_length=16, pattern=r"^[A-Z0-9]+$")
    merchant_name: str = Field(min_length=1, max_length=120)
    kind: Literal["PAID", "AIRTIME", "SENT", "WITHDRAW", "RECEIVED", "OTHER"]
    amount_cents: int = Field(gt=0, le=100_000_000)
    occurred_at: datetime

    @field_validator("merchant_name")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = " ".join(v.split())
        if not v:
            raise ValueError("merchant name is blank")
        return v

class TransactionOut(ApiModel):
    id: str
    mpesa_code: str
    merchant_name: str
    kind: str
    amount_cents: int
    round_up_cents: int
    status: str
    occurred_at: UtcDt

class TransactionCreated(ApiModel):
    transaction: TransactionOut
    duplicate: bool
    pending_round_up_cents: int
    threshold_reached: bool
    auto_stk_due: bool

class TransactionPage(ApiModel):
    items: list[TransactionOut]
    next_cursor: str | None


# ---- portfolio / payments
class Recommendation(ApiModel):
    category: str
    allocation: Allocation
    personalized: bool
    ai_label: bool
    reasoning: str
    confidence_score: float
    expected_annual_yield_pct: float

class PortfolioOut(ApiModel):
    balance_cents: int
    pending_round_up_cents: int
    in_flight_cents: int
    unallocated_cents: int
    recommendation: Recommendation | None
    as_of: UtcDt

class DepositIn(ApiModel):
    amount_cents: int = Field(ge=100, le=15_000_000)   # KES 1 .. 150,000 (M-Pesa per-transaction ceiling)

    @field_validator("amount_cents")
    @classmethod
    def _whole_shillings(cls, v: int) -> int:
        if v % 100:
            raise ValueError("amount must be whole shillings")
        return v

class StkOut(ApiModel):
    id: str
    purpose: str
    amount_cents: int
    status: str
    customer_message: str | None = None
    receipt_number: str | None = None
    result_desc: str | None = None
    created_at: UtcDt
    completed_at: UtcDt | None = None


# ---- goals / notifications
class GoalIn(ApiModel):
    title: str = Field(min_length=1, max_length=60)
    emoji: str = Field(default="🎯", max_length=8)
    target_cents: int = Field(ge=10_000, le=100_000_000_00)
    deadline: date | None = None

class GoalUpdate(ApiModel):
    title: str | None = Field(default=None, min_length=1, max_length=60)
    emoji: str | None = Field(default=None, max_length=8)
    target_cents: int | None = Field(default=None, ge=10_000, le=100_000_000_00)
    deadline: date | None = None

class GoalOut(ApiModel):
    id: str
    title: str
    emoji: str
    target_cents: int
    saved_cents: int
    deadline: date | None
    progress: float

class ContributeIn(ApiModel):
    amount_cents: int = Field(gt=0, le=100_000_000_00)

class NotificationOut(ApiModel):
    id: str
    title: str
    message: str
    created_at: UtcDt
    is_read: bool



class AgentOut(ApiModel):
    id: str
    name: str
    agent_number: str
    latitude: float
    longitude: float
    distance_km: float
    illustrative: bool
