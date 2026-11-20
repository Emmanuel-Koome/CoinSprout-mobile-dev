from conftest import signup


def test_otp_flow_and_me(client):
    headers, tokens = signup(client, "+254 712 345 678")
    assert tokens["isNewUser"] is True
    me = client.get("/api/v1/me", headers=headers).json()
    assert me["phoneMasked"].startswith("+2547") and "345" not in me["phoneMasked"]
    headers2, tokens2 = signup(client, "0712345678")          # same person, different format
    assert tokens2["isNewUser"] is False


def test_invalid_phone_rejected(client):
    assert client.post("/api/v1/auth/request-otp", json={"phone": "12345"}).status_code == 422


def test_wrong_otp_then_lockout(client):
    client.post("/api/v1/auth/request-otp", json={"phone": "0712345678"})
    for _ in range(5):
        assert client.post("/api/v1/auth/verify-otp", json={"phone": "0712345678", "otp": "000000"}).status_code == 400
    assert client.post("/api/v1/auth/verify-otp", json={"phone": "0712345678", "otp": "000000"}).status_code == 429


def test_otp_request_rate_limit(client):
    codes = [client.post("/api/v1/auth/request-otp", json={"phone": "0722000111"}).status_code for _ in range(4)]
    assert codes == [200, 200, 200, 429]


def test_otp_cannot_be_reused(client):
    otp = client.post("/api/v1/auth/request-otp", json={"phone": "0733000111"}).json()["devOtp"]
    assert client.post("/api/v1/auth/verify-otp", json={"phone": "0733000111", "otp": otp}).status_code == 200
    assert client.post("/api/v1/auth/verify-otp", json={"phone": "0733000111", "otp": otp}).status_code == 400


def test_protected_routes_need_token(client):
    assert client.get("/api/v1/portfolio").status_code == 401
    assert client.get("/api/v1/portfolio", headers={"Authorization": "Bearer junk"}).status_code == 401


def test_refresh_rotation_and_reuse_detection(client):
    _, tokens = signup(client)
    r1 = client.post("/api/v1/auth/refresh", json={"refreshToken": tokens["refreshToken"]})
    assert r1.status_code == 200
    replay = client.post("/api/v1/auth/refresh", json={"refreshToken": tokens["refreshToken"]})
    assert replay.status_code == 401
    # the whole token family is now revoked, including the freshly issued one
    assert client.post("/api/v1/auth/refresh", json={"refreshToken": r1.json()["refreshToken"]}).status_code == 401


def test_prod_refuses_default_secrets(monkeypatch):
    import pytest
    from app.config import Settings
    monkeypatch.setenv("COINSPROUT_ENV", "prod")
    with pytest.raises(RuntimeError):
        Settings().validate()
