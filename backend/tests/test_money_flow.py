from conftest import signup, tx


def _pending(client, h):
    return client.get("/api/v1/portfolio", headers=h).json()


def test_transaction_round_up_and_idempotency(client, auth):
    r = client.post("/api/v1/transactions", json=tx(amount=8500), headers=auth).json()
    assert r["transaction"]["roundUpCents"] == 500 and r["duplicate"] is False
    again = client.post("/api/v1/transactions", json=tx(amount=8500), headers=auth).json()
    assert again["duplicate"] is True and again["pendingRoundUpCents"] == 500       # not counted twice


def test_non_spend_kinds_earn_nothing(client, auth):
    for i, kind in enumerate(["SENT", "RECEIVED", "WITHDRAW"]):
        r = client.post("/api/v1/transactions", json=tx(code=f"AB12CD34E{i}", kind=kind, amount=8500), headers=auth).json()
        assert r["transaction"]["roundUpCents"] == 0 and r["transaction"]["status"] == "SKIPPED"


def test_future_timestamp_rejected(client, auth):
    assert client.post("/api/v1/transactions", json=tx(at="2099-01-01T00:00:00Z"), headers=auth).status_code == 422


def test_daily_cap_limits_round_ups(client, auth):
    client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 10000, "dailyCapCents": 6000, "maxRoundUpCents": 5000}, headers=auth)
    a = client.post("/api/v1/transactions", json=tx(code="CAP0000001", amount=5000), headers=auth).json()
    b = client.post("/api/v1/transactions", json=tx(code="CAP0000002", amount=5000), headers=auth).json()
    assert a["transaction"]["roundUpCents"] == 5000
    assert b["transaction"]["roundUpCents"] == 1000          # only KES 10 of the daily KES 60 budget left


def test_settings_validation(client, auth):
    assert client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 700}, headers=auth).status_code == 422
    assert client.put("/api/v1/me/settings", json={"stkThresholdCents": 1}, headers=auth).status_code == 422


def _fill_to_threshold(client, auth):
    client.post("/api/v1/transactions", json=tx(code="FILL000001", amount=8500), headers=auth)   # +5
    client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 10000}, headers=auth)
    return client.post("/api/v1/transactions", json=tx(code="FILL000002", amount=5000), headers=auth).json()  # +50


def test_sweep_requires_threshold(client, auth):
    client.post("/api/v1/transactions", json=tx(amount=8500), headers=auth)
    assert client.post("/api/v1/stk/sweep", headers=auth).status_code == 409


def test_full_sweep_success_credits_exactly_once(client, auth):
    client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 10000, "maxRoundUpCents": 10000}, headers=auth)
    for i, amt in enumerate([100, 200, 300]):          # KES 1, 2, 3 -> round-ups of 99, 98, 97
        client.post("/api/v1/transactions", json=tx(code=f"SWP000000{i}", amount=amt), headers=auth)
    p = _pending(client, auth)
    pending = p["pendingRoundUpCents"]
    assert pending >= 10000
    stk = client.post("/api/v1/stk/sweep", headers=auth, json=None)
    assert stk.status_code == 202 and stk.json()["amountCents"] == pending
    # a second prompt while one is pending is refused
    assert client.post("/api/v1/stk/sweep", headers=auth).status_code == 409
    p = _pending(client, auth)
    assert p["inFlightCents"] == pending and p["pendingRoundUpCents"] == 0

    from app.db import SessionLocal
    from app.models import StkRequest
    with SessionLocal() as db:
        checkout = db.query(StkRequest).one().checkout_request_id
    assert client.post(f"/api/v1/dev/stk/{checkout}/simulate?outcome=success").status_code == 200
    assert client.post(f"/api/v1/dev/stk/{checkout}/simulate?outcome=success").status_code == 200   # duplicate callback
    p = _pending(client, auth)
    assert p["balanceCents"] == pending                   # credited once, not twice
    assert p["inFlightCents"] == 0 and p["pendingRoundUpCents"] == 0
    items = client.get("/api/v1/transactions", headers=auth).json()["items"]
    assert {t["status"] for t in items} == {"ROUTED_TO_PORTFOLIO"}


def test_cancelled_prompt_releases_round_ups(client, auth):
    client.put("/api/v1/me/settings", json={"roundUpIncrementCents": 10000, "maxRoundUpCents": 10000}, headers=auth)
    client.post("/api/v1/transactions", json=tx(code="CAN0000001", amount=100), headers=auth)    # KES 1 -> +99
    client.post("/api/v1/transactions", json=tx(code="CAN0000002", amount=100), headers=auth)
    pending = _pending(client, auth)["pendingRoundUpCents"]
    assert pending >= 10000
    sid = client.post("/api/v1/stk/sweep", headers=auth).json()["id"]
    from app.db import SessionLocal
    from app.models import StkRequest
    with SessionLocal() as db:
        checkout = db.query(StkRequest).one().checkout_request_id
    client.post(f"/api/v1/dev/stk/{checkout}/simulate?outcome=cancel")
    p = _pending(client, auth)
    assert p["balanceCents"] == 0 and p["pendingRoundUpCents"] == pending      # nothing lost, nothing credited
    assert client.get(f"/api/v1/stk/{sid}", headers=auth).json()["status"] == "CANCELLED"


def test_callback_with_wrong_secret_is_rejected(client):
    assert client.post("/api/v1/stk/callback/wrong", json={"Body": {}}).status_code == 404


def test_callback_amount_mismatch_never_credits(client, auth):
    dep = client.post("/api/v1/deposits", json={"amountCents": 50000}, headers=auth)
    assert dep.status_code == 202
    from app.db import SessionLocal
    from app.models import StkRequest
    with SessionLocal() as db:
        checkout = db.query(StkRequest).one().checkout_request_id
    body = {"Body": {"stkCallback": {"CheckoutRequestID": checkout, "ResultCode": 0, "ResultDesc": "ok",
            "CallbackMetadata": {"Item": [{"Name": "Amount", "Value": 1.0}, {"Name": "MpesaReceiptNumber", "Value": "X"}]}}}}
    assert client.post("/api/v1/stk/callback/dev-callback-secret", json=body).json()["ResultCode"] == 0
    p = _pending(client, auth)
    assert p["balanceCents"] == 0
    assert client.get(f"/api/v1/stk/{dep.json()['id']}", headers=auth).json()["status"] == "MISMATCH"


def test_deposit_validation_and_idempotency_key(client, auth):
    assert client.post("/api/v1/deposits", json={"amountCents": 150}, headers=auth).status_code == 422   # not whole KES
    h = {**auth, "Idempotency-Key": "abc123"}
    a = client.post("/api/v1/deposits", json={"amountCents": 20000}, headers=h).json()
    b = client.post("/api/v1/deposits", json={"amountCents": 20000}, headers=h).json()
    assert a["id"] == b["id"]


def test_users_cannot_see_each_others_data(client):
    a, _ = signup(client, "0711111111")
    b, _ = signup(client, "0722222222")
    client.post("/api/v1/transactions", json=tx(), headers=a)
    assert client.get("/api/v1/transactions", headers=b).json()["items"] == []
    g = client.post("/api/v1/goals", json={"title": "Fund", "targetCents": 100000}, headers=a).json()
    assert client.patch(f"/api/v1/goals/{g['id']}", json={"title": "x"}, headers=b).status_code == 404


def test_pagination(client, auth):
    for i in range(5):
        client.post("/api/v1/transactions", json=tx(code=f"PAGE00000{i}", at=f"2026-11-16T0{i + 1}:00:00+03:00"), headers=auth)
    p1 = client.get("/api/v1/transactions?limit=2", headers=auth).json()
    assert len(p1["items"]) == 2 and p1["nextCursor"]
    p2 = client.get(f"/api/v1/transactions?limit=2&cursor={p1['nextCursor']}", headers=auth).json()
    seen = {t["mpesaCode"] for t in p1["items"]} | {t["mpesaCode"] for t in p2["items"]}
    assert len(seen) == 4
