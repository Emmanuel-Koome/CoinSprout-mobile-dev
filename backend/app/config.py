"""Central configuration, read from environment variables.

Settings are read once per process. Tests set the environment *before* importing the app.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field

_INSECURE_DEFAULTS = {"dev-jwt-secret-change-me-0123456789abcdef", "dev-admin-key", "dev-otp-pepper", "dev-callback-secret"}


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    env: str = field(default_factory=lambda: os.getenv("COINSPROUT_ENV", "dev"))
    database_url: str = field(default_factory=lambda: os.getenv("DATABASE_URL", "sqlite:///./coinsprout.db"))
    jwt_secret: str = field(default_factory=lambda: os.getenv("JWT_SECRET", "dev-jwt-secret-change-me-0123456789abcdef"))
    admin_api_key: str = field(default_factory=lambda: os.getenv("ADMIN_API_KEY", "dev-admin-key"))
    otp_pepper: str = field(default_factory=lambda: os.getenv("OTP_PEPPER", "dev-otp-pepper"))
    stk_callback_secret: str = field(default_factory=lambda: os.getenv("STK_CALLBACK_SECRET", "dev-callback-secret"))
    public_base_url: str = field(default_factory=lambda: os.getenv("PUBLIC_BASE_URL", "http://localhost:8000"))
    mpesa_provider: str = field(default_factory=lambda: os.getenv("MPESA_PROVIDER", "mock"))
    study_access_code: str = field(default_factory=lambda: os.getenv("STUDY_ACCESS_CODE", ""))     # optional invite code
    study_likert_points: int = field(default_factory=lambda: int(os.getenv("STUDY_LIKERT_POINTS", "7")))
    study_equity_vol_ratio: str = field(default_factory=lambda: os.getenv("STUDY_EQUITY_VOL_RATIO", ""))  # frozen before data collection; empty = no tilt

    access_token_minutes: int = 30
    refresh_token_days: int = 30
    otp_ttl_seconds: int = 300
    otp_max_attempts: int = 5
    otp_max_requests_per_10min: int = 3
    stk_expiry_seconds: int = 180

    daraja_base_url: str = field(default_factory=lambda: os.getenv("DARAJA_BASE_URL", "https://sandbox.safaricom.co.ke"))
    daraja_consumer_key: str = field(default_factory=lambda: os.getenv("DARAJA_CONSUMER_KEY", ""))
    daraja_consumer_secret: str = field(default_factory=lambda: os.getenv("DARAJA_CONSUMER_SECRET", ""))
    daraja_shortcode: str = field(default_factory=lambda: os.getenv("DARAJA_SHORTCODE", "174379"))
    daraja_passkey: str = field(default_factory=lambda: os.getenv("DARAJA_PASSKEY", ""))

    @property
    def is_dev(self) -> bool:
        return self.env == "dev"

    def validate(self) -> None:
        """Refuse to boot in production with placeholder secrets."""
        if self.is_dev:
            return
        bad = [n for n, v in {
            "JWT_SECRET": self.jwt_secret, "ADMIN_API_KEY": self.admin_api_key,
            "OTP_PEPPER": self.otp_pepper, "STK_CALLBACK_SECRET": self.stk_callback_secret,
        }.items() if v in _INSECURE_DEFAULTS or len(v) < 16]
        if bad:
            raise RuntimeError(f"Insecure or missing secrets for production: {', '.join(bad)}")
        if self.mpesa_provider == "daraja" and not (self.daraja_consumer_key and self.daraja_consumer_secret and self.daraja_passkey):
            raise RuntimeError("MPESA_PROVIDER=daraja requires DARAJA_CONSUMER_KEY/SECRET and DARAJA_PASSKEY")


settings = Settings()
