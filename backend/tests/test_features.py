from conftest import signup, tx


def test_goals_crud_and_contribution_rules(client, auth):
    g = client.post("/api/v1/goals", json={"title": "Emergency Fund", "targetCents": 5_000_000, "deadline": "2099-12-31"}, headers=auth)
    assert g.status_code == 201
    gid = g.json()["id"]
    assert client.post(f"/api/v1/goals/{gid}/contribute", json={"amountCents": 100}, headers=auth).status_code == 409  # nothing invested yet
    assert client.post("/api/v1/goals", json={"title": "Old", "targetCents": 20000, "deadline": "2001-01-01"}, headers=auth).status_code == 422
    assert client.delete(f"/api/v1/goals/{gid}", headers=auth).status_code == 204


def test_goal_contribution_uses_confirmed_balance_only(client, auth):
    dep = client.post("/api/v1/deposits", json={"amountCents": 50000}, headers=auth).json()
    from app.db import SessionLocal
    from app.models import StkRequest
    with SessionLocal() as db:
        checkout = db.query(StkRequest).one().checkout_request_id
    client.post(f"/api/v1/dev/stk/{checkout}/simulate?outcome=success")
    gid = client.post("/api/v1/goals", json={"title": "G", "targetCents": 100000}, headers=auth).json()["id"]
    ok = client.post(f"/api/v1/goals/{gid}/contribute", json={"amountCents": 30000}, headers=auth)
    assert ok.status_code == 200 and ok.json()["progress"] == 0.3
    assert client.post(f"/api/v1/goals/{gid}/contribute", json={"amountCents": 30000}, headers=auth).status_code == 409
    assert client.get("/api/v1/portfolio", headers=auth).json()["unallocatedCents"] == 20000


def test_rates_are_public_and_flagged_illustrative(client):
    rates = client.get("/api/v1/cbk-rates").json()
    assert {r["key"] for r in rates} == {"mmf", "tbill", "unit"} and all(r["illustrative"] for r in rates)
    assert client.get("/api/v1/cbk-rates/tbill").status_code == 200
    assert client.get("/api/v1/cbk-rates/nope").status_code == 404


def test_admin_rate_update_needs_key_and_clears_flag(client):
    body = {"annualYieldRate": 9.5, "effectiveDate": "2026-11-10", "providerName": "CBK weekly auction"}
    assert client.put("/api/v1/admin/rates/tbill", json=body).status_code == 403
    r = client.put("/api/v1/admin/rates/tbill", json=body, headers={"X-Admin-Key": "dev-admin-key"})
    assert r.status_code == 200 and r.json()["illustrative"] is False


def test_risk_profile_and_ai_endpoints(client, auth):
    rec = client.put("/api/v1/me/risk-profile", json={"riskHorizon": 3, "riskTolerance": 3, "incomeStability": 3,
                                                      "currentSavingsCents": 9_000_000}, headers=auth).json()
    assert sum(rec["allocation"].values()) == 100 and rec["expectedAnnualYieldPct"] > 0
    ai = client.post("/api/v1/ai/optimize-allocation", json={"riskHorizon": 1, "riskTolerance": 1, "incomeStability": 1}, headers=auth).json()
    assert ai["recommendedCategory"] == "Conservative" and ai["modelVersion"]
    f = client.post("/api/v1/ai/forecast-growth", json={"principalCents": 1_000_000, "monthlyContributionCents": 50000, "monthsAhead": 24}, headers=auth).json()
    assert f["pessimisticValueCents"] < f["projectedValueCents"] < f["optimisticValueCents"] and f["assumptions"]
    assert client.post("/api/v1/ai/optimize-allocation", json={"riskHorizon": 9, "riskTolerance": 1, "incomeStability": 1}, headers=auth).status_code == 422


def test_insights(client, auth):
    assert client.get("/api/v1/ai/spending-insights", headers=auth).json()[0]["id"] == "empty"
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc).isoformat()
    client.post("/api/v1/transactions", json=tx(at=now), headers=auth)
    ids = [i["id"] for i in client.get("/api/v1/ai/spending-insights", headers=auth).json()]
    assert "summary" in ids and "pace" in ids


def test_export_and_erasure(client, auth):
    client.post("/api/v1/transactions", json=tx(), headers=auth)
    data = client.get("/api/v1/me/export", headers=auth).json()
    assert len(data["transactions"]) == 1
    assert client.delete("/api/v1/me", headers=auth).status_code == 204
    assert client.get("/api/v1/me", headers=auth).status_code == 401


def test_notifications(client, auth):
    client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 10000, "maxRoundUpCents": 10000}, headers=auth)
    client.post("/api/v1/transactions", json=tx(code="NOTIF00001", amount=100), headers=auth)
    r = client.post("/api/v1/transactions", json=tx(code="NOTIF00002", amount=100), headers=auth).json()
    assert r["thresholdReached"] is True
    notes = client.get("/api/v1/notifications", headers=auth).json()
    assert any(n["title"] == "Ready to invest" for n in notes)
    assert client.post("/api/v1/notifications/read-all", headers=auth).status_code == 204


def test_nearby_agents_sorted_by_distance_and_auth_required(client, auth):
    assert client.get("/api/v1/agents/nearby?lat=-1.2864&lng=36.8172").status_code == 401
    r = client.get("/api/v1/agents/nearby?lat=-1.2864&lng=36.8172&radiusKm=3", headers=auth).json()
    assert r and r[0]["distanceKm"] <= r[-1]["distanceKm"] and all(a["distanceKm"] <= 3 for a in r)
    assert client.get("/api/v1/agents/nearby?lat=0&lng=0&radiusKm=1", headers=auth).json() == []     # nothing near the equator
    assert client.get("/api/v1/agents/nearby?lat=99&lng=0", headers=auth).status_code == 422


def test_dev_simulate_by_payment_id(client, auth):
    dep = client.post("/api/v1/deposits", json={"amountCents": 20000}, headers=auth).json()
    assert client.post(f"/api/v1/dev/payments/{dep['id']}/simulate?outcome=success").status_code == 200
    assert client.get("/api/v1/portfolio", headers=auth).json()["balanceCents"] == 20000
    assert client.post("/api/v1/dev/payments/nope/simulate").status_code == 404
