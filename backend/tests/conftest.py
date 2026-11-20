import os
import tempfile

_tmp = tempfile.mkdtemp()
os.environ.update({"COINSPROUT_ENV": "dev", "DATABASE_URL": f"sqlite:///{_tmp}/test.db", "MPESA_PROVIDER": "mock"})

import pytest
from fastapi.testclient import TestClient

from app.db import Base, SessionLocal, engine
from app.main import app
from app.services.agents import seed_agents_if_empty
from app.services.rates import seed_if_empty


@pytest.fixture()
def client():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        seed_if_empty(db)
        seed_agents_if_empty(db)
    with TestClient(app) as c:
        yield c


def signup(client, phone="0712345678"):
    """Returns (auth headers, token response)."""
    otp = client.post("/api/v1/auth/request-otp", json={"phone": phone}).json()["devOtp"]
    tokens = client.post("/api/v1/auth/verify-otp", json={"phone": phone, "otp": otp}).json()
    return {"Authorization": f"Bearer {tokens['accessToken']}"}, tokens


@pytest.fixture()
def auth(client):
    return signup(client)[0]


def tx(code="QJ12ABC345", amount=8500, kind="PAID", merchant="Mandazi Vendor", at="2026-11-16T07:30:00+03:00"):
    return {"mpesaCode": code, "merchantName": merchant, "kind": kind, "amountCents": amount, "occurredAt": at}
