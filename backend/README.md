# CoinSprout API

FastAPI + SQLAlchemy 2 + SQLite (swap `DATABASE_URL` for PostgreSQL in production). Python 3.11+.

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env            # then export the variables (or use your process manager)
uvicorn app.main:app --reload   # Swagger UI: /docs (dev only)
pytest                          # 86 tests
```

## Conventions

* JSON is **camelCase**. Money is always **integer cents** (`amountCents`, `balanceCents`); never floats.
* Times are ISO-8601 UTC (`2026-11-16T04:30:00Z`). Client timestamps without an offset are assumed Nairobi time.
* Auth: `Authorization: Bearer <access token>` (30 min). Refresh tokens rotate; replaying an old one revokes the family.
* Errors: `{"detail": "human readable text"}` (or a list of `{msg}` for validation errors).
* Payment-starting calls accept an `Idempotency-Key` header.

## Endpoints (`/api/v1`)

| Area | Method & path | Notes |
|---|---|---|
| Auth | `POST /auth/request-otp` · `/auth/verify-otp` · `/auth/refresh` · `/auth/logout` | OTP hashed, 5 min, 5 tries, 3 requests/10 min. Dev returns `devOtp`. |
| Me | `GET /me` · `DELETE /me` · `GET /me/export` | erasure and portability |
| Settings | `GET/PUT /me/settings` | increment (KES 10/20/50/100), threshold (100/200/500), caps, toggles |
| Profile | `PUT /me/risk-profile` | stores quiz answers, returns what **this user is shown** (honours experiment arm) |
| Rates | `GET /cbk-rates[/{key}]` · `PUT /admin/rates/{key}` | `illustrative:true` until an admin loads real data |
| AI | `POST /ai/optimize-allocation` · `POST /ai/forecast-growth` · `GET /ai/spending-insights` | forecast returns pessimistic/expected/optimistic + assumptions |
| Purchases | `POST /transactions` · `GET /transactions?limit&cursor` | server recomputes round-up; idempotent on `mpesaCode` |
| Portfolio | `GET /portfolio` | confirmed balance, pending round-ups, in-flight, recommendation |
| Payments | `POST /deposits` · `POST /stk/sweep` · `GET /stk/{id}` · `POST /stk/callback/{secret}` | server-initiated M-Pesa Express |
| Goals | `GET/POST /goals` · `PATCH/DELETE /goals/{id}` · `POST /goals/{id}/contribute` | contribution earmarks *confirmed* balance only |
| Alerts | `GET /notifications` · `POST /notifications/read-all` | |
| Agents | `GET /agents/nearby?lat&lng&radiusKm` | coordinates never stored |
| Study | `GET /study` (page) · `/api/v1/study/{instrument,config,debrief,screen}` · `/study/{id}/{baseline,assign,outcomes}` · `DELETE /study/{id}` · admin: `/admin/study/{progress,export.csv}` | no-real-money experiment: stratified permuted blocks by year of study, anonymous screening log, pseudonymised export |
| Dev only | `POST /dev/payments/{id}/simulate?outcome=success\|cancel\|insufficient` | mounted only when `COINSPROUT_ENV=dev` |

### 60-second demo

```bash
B=http://localhost:8000/api/v1
OTP=$(curl -s -X POST $B/auth/request-otp -H 'content-type: application/json' -d '{"phone":"0712345678"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["devOtp"])')
T=$(curl -s -X POST $B/auth/verify-otp -H 'content-type: application/json' -d "{\"phone\":\"0712345678\",\"otp\":\"$OTP\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
H="Authorization: Bearer $T"
curl -s -X PUT $B/me/risk-profile -H "$H" -H 'content-type: application/json' -d '{"riskHorizon":3,"riskTolerance":2,"incomeStability":2}'
curl -s -X POST $B/transactions -H "$H" -H 'content-type: application/json' \
  -d '{"mpesaCode":"QJ12ABC345","merchantName":"Mandazi Vendor","kind":"PAID","amountCents":8500,"occurredAt":"2026-11-16T07:30:00+03:00"}'
```

## Design decisions worth defending

* **Whole-shilling round-ups.** Daraja accepts integer amounts only; a KES 85.50 purchase is treated as 86.
* **Only PAID and AIRTIME purchases earn round-ups.** Not sends, withdrawals or received money.
* **Per-purchase cap (KES 50) and daily cap (KES 200)** so small purchases are never over-charged.
* **Ledger is append-only and unique per STK request**, so a duplicate callback cannot double-credit.
* **Amount mismatch ⇒ `MISMATCH`, no credit**, plus a notification. A late success after our timeout still credits
  (the customer paid) and best-effort re-attaches the oldest pending round-ups.
* **The "AI" is an explainable expert-prior model** (`services/risk_model.py`), labelled `expert-prior-v1`.
  It is not trained ML; see `docs/CRITIQUE.md` §7.
* **The experiment is not in the app.** It runs in the standalone study module with stratified permuted-block randomisation (see `docs/RESEARCH_ALIGNMENT.md`).

## Production checklist (not done)

- [ ] PostgreSQL + Alembic migrations (`create_all` is dev-only); `SELECT … FOR UPDATE` around payment starts
- [ ] Real SMS gateway in `services/sms.py` (prod deliberately raises until configured)
- [ ] Daraja: test in sandbox, then production shortcode; restrict callback to Safaricom IPs at the proxy
- [ ] TLS termination, per-IP rate limiting, structured logging without PII, backups, monitoring
- [ ] Load real CBK/fund-manager yields via `PUT /admin/rates/{key}` (or a scheduled ingestion job)
- [ ] Privacy notice, retention policy, ODPC registration (Kenya DPA 2019), ethics approval for the study
- [ ] Study server: disable or truncate access logs (IP addresses), set STUDY_ACCESS_CODE, freeze STUDY_EQUITY_VOL_RATIO
