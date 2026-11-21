# CoinSprout

Round your M-Pesa purchases up, collect the spare change, and invest it in Kenyan funds with one PIN approval.
Android app (Kotlin + Jetpack Compose) with a FastAPI backend.

> Prototype for coursework. Rates and agents are **demo data** (flagged as such in the API and UI). Nothing here is
> financial advice. See [`docs/CRITIQUE.md`](docs/CRITIQUE.md) for a frank review and the list of open items.

## How the money flows

```
SMS from MPESA ─► parse (domain/MpesaSmsParser) ─► Room (instant, works offline) ─► WorkManager sync
                                                                                         │
                          server recomputes the round-up (authoritative) ◄───────────────┘
                                                │
   user taps "Invest round-ups" ─► POST /stk/sweep ─► server asks Safaricom for an M-Pesa prompt
                                                │
   user enters PIN on the phone ─► Safaricom ─► POST /stk/callback/{secret} ─► ledger credited ONCE
                                                │
                                   app polls /stk/{id} ─► balance updates
```

Only the **server** ever changes money state, and only on a confirmed callback. The phone just displays things.

## Repository layout

```
app/                       Android app
  src/main/java/com/coinsprout/app/
    domain/                pure Kotlin: Money, RoundUpCalculator, MpesaSmsParser, IsoTime   (unit-tested)
    data/local/            Room database (transactions, goals, notifications)
    data/remote/           Retrofit API, DTOs, token-refresh authenticator
    data/prefs/            SessionStore (tokens, settings, last portfolio)
    data/repository/       offline-first repository
    sync/                  WorkManager SyncWorker
    receiver/              MpesaSmsReceiver (demo flavour only)
    viewmodel/             AuthViewModel, CoinSproutViewModel, Load<T>
    ui/                    screens, components, theme
  src/play/                manifest overlay that strips SMS permission for the Play build
  src/debug/               dev-only network security config
  src/test/                JUnit tests for the domain layer
backend/                   FastAPI service (see backend/README.md)
  app/study/ + app/static/study.html   the no-real-money online experiment (separate from the app)
  analysis/                H1/H2/E1/E2 pipeline + power check
  backtest/                walk-forward harness for Objective 4
  app/ml/                  runtime AI engine: trained risk classifier + MMF/NSE mean-variance allocator (JSON artifacts)
ml/                        AI engine training (from SemProject_MLEngine): notebooks, NASI data, train.py; see ml/README.md
docs/CRITIQUE.md           lecturer-style review of the original app
docs/RESEARCH_ALIGNMENT.md Chapters 1-3 vs the project: gaps, chapter issues, ready-to-paste Chapter 3 text
```

## Run it

### 1. Backend

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000        # docs at http://localhost:8000/docs
pytest                                           # 86 tests
```
Dev mode returns the OTP in the API response and logs it, and uses a mock M-Pesa provider.

### 2. Android

1. Open the project root in Android Studio (Koala or newer) and let Gradle sync.
2. Choose the **`demoDebug`** build variant (SMS capture on) or **`playDebug`** (manual entry only).
3. Run on an **emulator**: the debug build talks to `http://10.0.2.2:8000/` (your PC's localhost).
   For a **physical phone** put your PC's LAN IP in `app/build.gradle.kts` (`API_BASE_URL`) and in
   `app/src/debug/res/xml/network_security_config.xml`, and run uvicorn with `--host 0.0.0.0`.
4. Sign in with any Kenyan number. In debug builds the dev OTP appears under the code box ("tap to fill").
5. To see the full payment flow without Safaricom: add purchases until round-ups reach KES 100, tap **Invest
   round-ups**, then use the debug-only **Approve** button in the dialog.
6. Unit tests: `./gradlew testDemoDebugUnitTest`

### 3. Real M-Pesa (optional)

Create a Daraja sandbox app, set `MPESA_PROVIDER=daraja` plus the `DARAJA_*` variables in `backend/.env.example`,
and expose the server publicly (e.g. a tunnel) so Safaricom can reach `PUBLIC_BASE_URL/api/v1/stk/callback/<secret>`.

## What is verified

| | |
|---|---|
| Backend, study module, analysis, backtest | 86 automated tests; live server exercised end-to-end |
| Kotlin domain layer | 21 JUnit tests run under the Kotlin compiler |
| Android UI / Room / Retrofit wiring | **Written but not compiled in the authoring environment** (no Android SDK). Expect to fix a few small compile errors on first sync and please report them |
| Daraja integration | Follows Safaricom's published contract; **not run against Safaricom** |

## The research study (separate from the app)

The randomised experiment from Chapters 1-3 is **not** inside the money-moving app. Participants open `/study` on the
backend (no login, no payments, no phone or M-Pesa data). See `docs/RESEARCH_ALIGNMENT.md`.

## Known limitations

Plain-text token storage, no Alembic migrations, no dark theme, no Kiswahili, demo rates/agents, SMS capture is not
Play-compliant (hence the flavours). Full list with reasoning in `docs/CRITIQUE.md`.
