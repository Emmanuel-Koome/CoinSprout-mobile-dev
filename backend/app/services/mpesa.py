"""M-Pesa Express (STK Push) providers.

`MockMpesaProvider` is the default so the whole flow can be demoed and tested without Safaricom.
`DarajaProvider` follows Safaricom's published M-Pesa Express contract (OAuth token -> /mpesa/stkpush/v1/processrequest,
password = base64(shortcode + passkey + timestamp)). It has NOT been exercised against Safaricom from this
codebase - test it in the Daraja sandbox before relying on it.
"""
from __future__ import annotations

import base64
import time
import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Protocol

import httpx

from ..config import settings
from ..util import EAT


class MpesaError(RuntimeError):
    pass


@dataclass(frozen=True)
class StkInitResult:
    merchant_request_id: str
    checkout_request_id: str
    customer_message: str


class MpesaProvider(Protocol):
    def initiate_stk(self, *, phone: str, amount_cents: int, reference: str, description: str) -> StkInitResult: ...


class MockMpesaProvider:
    def initiate_stk(self, *, phone: str, amount_cents: int, reference: str, description: str) -> StkInitResult:
        return StkInitResult(f"mock-mr-{uuid.uuid4().hex[:12]}", f"ws_CO_{uuid.uuid4().hex[:20]}",
                             "Mock STK push sent (use the dev simulate endpoint to complete it).")


class DarajaProvider:
    def __init__(self) -> None:
        self._token: str | None = None
        self._token_expiry = 0.0

    def _access_token(self) -> str:
        if self._token and time.time() < self._token_expiry - 30:
            return self._token
        r = httpx.get(f"{settings.daraja_base_url}/oauth/v1/generate", params={"grant_type": "client_credentials"},
                      auth=(settings.daraja_consumer_key, settings.daraja_consumer_secret), timeout=15)
        if r.status_code != 200:
            raise MpesaError(f"Daraja auth failed ({r.status_code})")
        data = r.json()
        self._token = data["access_token"]
        self._token_expiry = time.time() + int(data.get("expires_in", 3599))
        return self._token

    def initiate_stk(self, *, phone: str, amount_cents: int, reference: str, description: str) -> StkInitResult:
        if amount_cents % 100 != 0 or amount_cents < 100:
            raise MpesaError("M-Pesa Express amounts must be whole shillings (>= KES 1)")
        timestamp = datetime.now(EAT).strftime("%Y%m%d%H%M%S")
        password = base64.b64encode(f"{settings.daraja_shortcode}{settings.daraja_passkey}{timestamp}".encode()).decode()
        msisdn = phone.lstrip("+")
        payload = {
            "BusinessShortCode": settings.daraja_shortcode,
            "Password": password,
            "Timestamp": timestamp,
            "TransactionType": "CustomerPayBillOnline",
            "Amount": amount_cents // 100,
            "PartyA": msisdn,
            "PartyB": settings.daraja_shortcode,
            "PhoneNumber": msisdn,
            "CallBackURL": f"{settings.public_base_url}/api/v1/stk/callback/{settings.stk_callback_secret}",
            "AccountReference": reference[:12],
            "TransactionDesc": description[:13],
        }
        try:
            r = httpx.post(f"{settings.daraja_base_url}/mpesa/stkpush/v1/processrequest", json=payload,
                           headers={"Authorization": f"Bearer {self._access_token()}"}, timeout=20)
        except httpx.HTTPError as exc:
            raise MpesaError(f"Could not reach Daraja: {exc}") from exc
        body = r.json() if r.content else {}
        if r.status_code != 200 or str(body.get("ResponseCode")) != "0":
            raise MpesaError(body.get("errorMessage") or body.get("ResponseDescription") or f"Daraja error {r.status_code}")
        return StkInitResult(body["MerchantRequestID"], body["CheckoutRequestID"], body.get("CustomerMessage", ""))


_provider: MpesaProvider | None = None


def get_provider() -> MpesaProvider:
    global _provider
    if _provider is None:
        _provider = DarajaProvider() if settings.mpesa_provider == "daraja" else MockMpesaProvider()
    return _provider
