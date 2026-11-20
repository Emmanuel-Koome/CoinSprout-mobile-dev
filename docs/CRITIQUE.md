# CoinSprout — Lecturer's Critique

*Reviewed as a combined submission for AI, Mobile Development and Research Methodology.
Every finding cites the original code. Status key: ✅ fixed in this revision · ⚠️ partly addressed · ❌ still open (needs you).*

---

## 0. Verdict in one page

**What is genuinely good.** The idea is well-chosen for the Kenyan context (M-Pesa round-ups, low-ticket
investing). The visual language is consistent and the package layout (`data / ui / viewmodel / navigation`) is
sensible. Using an SMS receiver, Retrofit, adaptive layouts and a repository is the right *shape* of a mobile
course project. You also wrote honest "illustrative" comments in places, which is better than hiding it.

**What would cost you the most marks.** The submission *looks* like a working fintech app but, underneath, almost
nothing is real:

1. **No backend existed.** Every Retrofit call was dead code; the "AI" was a three-row lookup table.
2. **No persistence.** "Room" was an in-memory fake, and `AppState` lived in `remember {}` so rotating the phone
   reset the app (`MainActivity.kt:48`).
3. **The UI made claims the code could not support** ("real NSE & CBK return data",
   "based on historical fund data", "+7% vs +4%"). In a finance app and a research study this is the most serious
   issue in the project (§6, §9).
4. **Money handling was unsafe** (`Double` currency, a regex that reads `Ksh1,250.00` as `1`, round-ups on money
   you *receive*, "saved" shown before any money moved).
5. **Zero tests** across ~2,600 lines.

None of these are hard to fix, and they are all fixed or addressed below. The big risk now is different: I could
compile and test the **backend** and the **pure-Kotlin domain layer** here, but **not the Compose UI** (no Android
SDK in my sandbox). Treat the UI as "carefully written, never built" until you press Run (see §11).

---

## 1. Functional bugs (things that were simply wrong)

| # | Finding (original) | Sev. | Status |
|---|---|---|---|
| 1.1 | **Two sources of truth for the risk category.** The quiz wrote `appState.riskCategory` (`RiskQuizScreen.kt:109`) but the dashboard read `repository.riskCategory`, which stayed `"Moderate"` forever. Take the quiz as "Aggressive" and the home screen still said Moderate. | Critical | ✅ One source: server recommendation → `SessionStore` → UI |
| 1.2 | **State lost on rotation.** `remember { AppState() }` (`MainActivity.kt:48`) and no `configChanges` in the manifest, so the whole app reset when the device rotated. | Critical | ✅ State lives in ViewModel + Room + prefs |
| 1.3 | **Amount regex truncates thousands.** `(?:Ksh\|KES)\s*([0-9]+(?:\.[0-9]{1,2})?)` on `Ksh1,250.00` captures `1` (`MpesaSmsReceiver.kt:32`). | Critical | ✅ New parser + 11 unit tests |
| 1.4 | **Any SMS containing "Ksh" was treated as spend** (`body.contains("Ksh")`, line 24), including *money you received*, balances, and promotional texts from anyone. | Critical | ✅ Sender must equal `MPESA`; message must start with a 10-char receipt + "Confirmed"; only PAID/AIRTIME earn round-ups |
| 1.5 | **No de-duplication.** The same SMS twice = two round-ups. | Major | ✅ Receipt code is the primary key locally and a unique constraint on the server |
| 1.6 | **`ApiClient.service as? CoinSproutAiService`** can never succeed (it is a different interface) and throws on first use (`AiRepository.kt:11`). Latent crash. | Major | ✅ Class removed |
| 1.7 | **Fixed OTP.** The OTP box was pre-filled with `"7241"` and "I already have an account" jumped straight to the dashboard (`SignUpScreen.kt:20`). That is not authentication. | Critical | ✅ Real OTP flow against the backend |
| 1.8 | **Dead code paths**: `AiRepository`, the Retrofit service, `CbkMarketRate` — none were called by any screen. | Major | ✅ Replaced by a wired repository |
| 1.9 | **Expired seed data**: goal deadlines `Dec 2024` / `Jun 2025`, dialog default `Dec 2025` (`GoalsScreen.kt:81`) — all in the past. Dates stored as display strings (`"Today, 7:30 AM"`) become wrong tomorrow. | Minor | ✅ Real dates, validated as future ISO dates; timestamps stored as epoch millis |
| 1.10 | Counter animation only handled upward changes; a falling balance would display a stale number. | Minor | ✅ Animation removed (decoration that hid a bug) |

---

## 2. Architecture and code quality

* **Duplicated allocation tables that disagree.** `RiskData.kt` says Moderate = 40/25/25/10; `AiRepository.kt:37`
  says 45/30/15/10. Conservative thresholds differ too (`score <= 5` vs `score <= 4`). Two "truths" for the
  same concept. ✅ The backend owns the model; the app shows what it is told.
* **Singleton with global mutable state** (`CoinSproutRepository.instance`) mutated from a `BroadcastReceiver`
  and read from Compose, with non-atomic read-modify-write on `StateFlow.value`. ✅ Replaced by Room (transactional)
  behind one repository; shared via a small `ServiceLocator` so the receiver, worker and ViewModels see the same data.
* **Presentation data in the domain model**: emoji picked inside the repository by substring-matching merchant names
  (`CoinSproutRepository.kt:132`). ✅ Removed.
* **No loading / empty / error states.** Every screen assumed data existed and the network always worked. ✅ A
  `Load<T>` type (`Idle / Loading / Ready / Failed`) forces each screen to handle all four.
* **Money as `Double`** (`pending.toInt()`, `>= 100.0`). `0.1 + 0.2 != 0.3`; a ledger must be exact. ✅ Integer cents
  everywhere (app, API, database), formatted in one place (`Money.kes`).
* **Duplicated layouts** in `PortfolioScreen` (wide vs narrow copy-pasted). ✅ Shared composable blocks.
* ❌ Still open: manual DI is fine at this size, but a team project would use Hilt; navigation routes are strings
  (use type-safe routes when you upgrade Navigation); no multi-module split.

---

## 3. Mobile engineering (lifecycle, background work, permissions)

* **A foreground service just to "monitor"** (`PortfolioSyncService`, started from `onCreate` inside a `try/catch`
  that swallowed failures). A `BroadcastReceiver` for SMS needs no service at all; an always-on service drains
  battery, shows a permanent notification, and `dataSync` foreground services are time-limited on newer Android.
  ✅ Removed; **WorkManager** syncs when there is work *and* a network.
* **Runtime permissions were never requested.** `RECEIVE_SMS`, `POST_NOTIFICATIONS` (Android 13+) and location were
  declared, but nothing asked the user. The sign-up "permission" switches were decorative (`SignUpScreen.kt:21-22,75-79`: local `remember` booleans defaulting to `true`). ✅ Requested in context:
  notifications on first dashboard visit, SMS from the Settings toggle, location only when the user taps
  "Find agents near me".
* **Work on the main thread**: the receiver mutated state synchronously. ✅ `goAsync()` + coroutine on `Dispatchers.IO`.
* **Receiver ignored the user's own switch** (`smsListenerEnabled`) and signed-out state. ✅ Both honoured.
* **Fake location feature.** `LocationHelper` + `MerchantMapComposable` showed a hard-coded list under a "GPS ON"
  banner. ✅ Real coarse-location lookup → backend `/agents/nearby` → distances + "open in Maps". Agent rows are still
  **demo data** (labelled as such); you need a real directory for production.
* **Forced night mode** via `UiModeManager.setApplicationNightMode(MODE_NIGHT_NO)` overrode the user's system choice.
  ✅ Removed; the app declares itself light-only. ❌ A proper dark theme is future work.
* ✅ `targetSdk` raised 34 → 35 with edge-to-edge handled. ⚠️ Check Google Play's *current* target-API
  requirement before publishing; it moves yearly.
* ✅ Added build flavours: **`demo`** (SMS capture on) and **`play`** (SMS permission and receiver stripped — see §5).

---

## 4. Security

| Finding | Status |
|---|---|
| Secrets/keys: nothing sensitive was in the app (good), but there was also no server to hold the **Daraja consumer secret** — which must *never* ship in an APK. STK Push must be initiated server-side. | ✅ Server initiates STK; app only displays the prompt |
| "STK Push" in the app just moved numbers between two variables and showed "+KES saved". No money moved. | ✅ Money state changes only on a confirmed Daraja callback, credited once (unique ledger constraint) |
| OTP: hashed (HMAC + pepper), 5-minute expiry, 5 attempts, 3 requests/10 min, single-use | ✅ (tested) |
| Tokens: 30-min access JWT; rotating refresh tokens with **reuse detection** (replay revokes the whole family) | ✅ (tested) |
| Callback authenticity: Daraja callbacks are unsigned, so the URL contains a secret path token; amounts are verified against the request and mismatches are *not* credited | ✅ (tested) |
| Idempotency: `Idempotency-Key` on payment starts; receipt-code uniqueness on purchases | ✅ (tested) |
| Production refuses to boot with placeholder secrets | ✅ (tested) |
| `allowBackup="true"` would have copied app data to cloud backups | ✅ `false` |
| Cleartext traffic | ✅ HTTPS-only in release; dev server allowed only in the *debug* source set |
| Spoofable SMS sender IDs: alphanumeric sender "MPESA" can be forged by a bad actor | ⚠️ Mitigated (receipt-code format, server dedupe, user can see everything) but not eliminable. A forged SMS can at worst create a *pending* round-up — real money only moves after the user approves an M-Pesa PIN prompt. |
| Tokens stored in plain app-private SharedPreferences | ❌ Wrap with an Android Keystore key before production |
| No certificate pinning, no rate limiting per IP, no WAF, SQLite in dev | ❌ Production checklist in `backend/README.md` |
| Concurrent STK requests rely on SQLite's write serialisation | ❌ On PostgreSQL add `SELECT … FOR UPDATE` on the user row |

---

## 5. Privacy and legal (please verify with a professional — I am not a lawyer)

* **Google Play SMS policy.** Play restricts `RECEIVE_SMS`/`READ_SMS` to apps that are the user's *default SMS handler*
  (plus a few narrow, application-based exceptions). A round-up app that silently reads M-Pesa texts will very
  likely be rejected, and the policy also prohibits deriving SMS data by other means (so a notification-listener
  workaround is not an escape). The `play` flavour therefore uses **manual entry**; the `demo` flavour keeps SMS
  for your course demo / side-loaded APK. Real production options: M-Pesa statement import, a partner/aggregator
  API, or Daraja callbacks if CoinSprout is itself the merchant.
* **Kenya Data Protection Act, 2019.** You collect phone numbers and financial behaviour. Expect obligations around
  registration with the ODPC, lawful basis/consent, purpose limitation, data minimisation, and data-subject rights.
  ✅ Built: purpose-limited SMS parsing (merchant/amount/receipt only; phone numbers stripped), coarse location used
  per-request and never stored, `GET /me/export` (portability), `DELETE /me` (erasure, with a Settings button),
  pseudonymised research export. ❌ Still needed: a real privacy notice and terms, retention policy,
  breach-response process, and a "Export my data" button in the UI (the endpoint exists).
* **Financial regulation.** Recommending allocations and moving client money into funds is regulated activity in most
  jurisdictions (in Kenya, look at the Capital Markets Authority and its regulatory sandbox). A coursework prototype
  is fine; a public launch needs licensing/partnering with a licensed manager. ❌ Not a code problem.
* ✅ The original requested `ACCESS_FINE_LOCATION` but never used it. Now only **coarse**, only on demand.

---

## 6. Financial correctness and honesty

* **Fabricated performance.** `CompareModesScreen.kt:26-27,60-65` hard-coded "AI +7% vs Fixed +4%" and labelled it
  "hypothetical 1-year return, based on historical fund data". There was no historical data. `PortfolioScreen.kt:175`
  claimed "real NSE & CBK return data". **This is the most serious issue in the project**: it is a misleading
  financial claim *and* it invalidates your research (§9). ✅ Removed. Yields now come from `/cbk-rates`, each row
  flagged **"Illustrative demo value"** until an admin loads real figures.
* **Only upside was shown.** Growth multipliers were all >1 (Aggressive 5-year ×2.05 ≈ 15.4 % a year) with no bad case.
  Also non-monotonic: Aggressive 6-month (1.012) was *below* Conservative (1.021). ✅ The forecast now returns
  **pessimistic / expected / optimistic** values, an inflation-adjusted figure, the assumptions, and a disclaimer.
  Unit trusts have a −15 % bad case. Projections are scenario illustrations, not predictions.
* **"Saved" before saving.** The UI said "+15 KES Saved!" the moment an SMS arrived. The money was not saved or invested.
  ✅ Three honest buckets: *invested* (confirmed by M-Pesa), *waiting* (round-ups), *awaiting approval* (STK sent).
* **Regressive round-up defaults.** A KES 50 or 100 increment on a KES 20 boda ride costs 150–400 % extra. For a
  low-income user that is harmful. ✅ Default KES 10, per-purchase cap (KES 50), daily cap (KES 200), whole-shilling
  maths (Daraja only accepts integers), and round-ups only on purchases — not on money sent, received or withdrawn.
* **M-Pesa fees ignored.** Many small payments cost more than they earn. ✅ Threshold chooser explains this and defaults
  to KES 100. ❌ A real fee model (tariff tables) is future work.
* **Auto-STK** was effectively always on. A payment prompt must never surprise the user. ✅ Opt-in, off by default.
* Education copy said T-bills give "safe guaranteed yields" and "tens of thousands" from KES 15 round-ups (the maths
  does not work: ~3 purchases/day × KES 7 ≈ KES 7,600/year). ✅ Corrected.

---

## 7. The "AI" claim (AI unit)

Be ready for this viva question: *"Where is the AI?"*

* **Original:** `when (score) { … }` returning one of three hard-coded allocations, plus invented "confidence" values
  (0.94, 0.96). No data, no training, no evaluation. Calling that "AI" would not survive a minute of questioning.
* **Now (honest label):** `backend/app/services/risk_model.py` is an **explainable expert-prior model**: a weighted
  suitability score → Gaussian-likelihood class probabilities → probability-weighted blend of the base portfolios →
  hard guard-rails (short horizon caps equity at 10 %, irregular income raises the cash buffer) → largest-remainder
  rounding. Confidence is the real max class probability. It is deterministic, unit-tested (allocations always sum
  to 100, monotonic in risk appetite, guard-rails hold) and reports `modelVersion: expert-prior-v1`.
* **It is still not machine learning.** I deliberately did not train a model on synthetic data — that would be
  circular. The defensible path, which fits your research unit: collect consented survey data (the pipeline now
  exists), define a target (e.g. user-confirmed suitable profile or a walk-forward backtest), fit and *evaluate*
  against the expert-prior baseline (accuracy/F1, calibration, fairness across income groups), and only then claim
  learning. ❌ That experiment is yours to run.
* **Risk profiling validity.** Three equal-weight, pre-selected questions is not a validated instrument. Published
  tools (e.g. the 13-item Grable–Lytton scale) separate *risk tolerance* from *capacity for loss*. ⚠️ I added a
  savings-based capacity term and removed pre-selected answers; a real study should validate the instrument.
* ⚠️ A "Moderate" label can come with more equity than the Moderate base portfolio (e.g. 36 % vs 25 %) because
  the allocation blends neighbouring categories. This is intended (continuous personalisation) but must be explained
  in your report, or you may prefer to snap to base portfolios.

---

## 8. HCI, UX and accessibility (relevant to your HCI ambitions)

* **Measured contrast failure.** `Sprout #6FB98F` on `Cream #EFE8D8` is **1.91 : 1** (on `Paper` 2.20 : 1). WCAG AA
  needs 4.5 : 1 for normal text. This colour was used for body-size text ("STEP 2 OF 2" in `RiskQuizScreen.kt:77`, "View all" in
  `DashboardScreen.kt:152`, "Tap to view growth simulator" at `:134`). ✅ Added `SproutDark #2B6E4A` (**5.01 : 1** on Cream, 5.77 : 1 on Paper) for text;
  `Sprout` is now only for fills and for text on `Forest` (5.29 : 1).
* **Touch targets.** Quiz chips used 8 dp vertical padding around small text (`RiskQuizScreen.kt:47`, roughly 32 dp tall). ✅ ≥ 48 dp everywhere, announced to TalkBack
  as radio buttons with selected state.
* **Colour as the only signal.** The donut conveyed the split by colour alone. ✅ Legend always prints percentages and
  the chart has a spoken description.
* **Emoji as icons without labels.** ✅ Decorative emoji hidden from TalkBack; meaningful ones labelled; back arrow labelled.
* **Pre-selected quiz answers** anchor responses (a known bias). ✅ Nothing pre-selected; submit disabled until answered.
* **No error recovery.** No validation on phone, OTP, amounts or dates; no offline message. ✅ Inline validation, humane
  server messages, "Retry" actions, and an offline-first design (an SMS received with no signal is not lost).
* **Hidden status and jargon.** "STK" means nothing to users. ✅ "M-Pesa prompt", plain-language status per purchase,
  "not synced yet" shown honestly.
* ❌ Still open for your HCI work: no user testing at all; no localisation (Kiswahili); no dynamic-type/large-font
  audit; no TalkBack walk-through recorded; fonts approximate the mockup (Fraunces/Manrope not bundled). A small
  usability study (5–8 participants, think-aloud, SUS score) would make this project stand out.

---

## 9. Research methodology (the biggest conceptual flaw)

*Updated after reading Chapters 1-3 — see [`RESEARCH_ALIGNMENT.md`](RESEARCH_ALIGNMENT.md).*

The original app embedded a user-toggled "AI mode" and a hard-coded +7 % vs +4 % chart. That could not answer your
research question: the treatment was fabricated (construct validity), users chose their own arm (self-selection),
there was no informed consent, and nothing was stored. It also contradicted your own Chapter 1, which says the
experiment is online, one session and **uses no real money**.

✅ **Resolved by design.** The experiment now lives in a separate study module (`/study`): no payments, no login, no phone
or M-Pesa data; consent and eligibility screening; baseline measured **before** exposure; stratified permuted-block
randomisation by year of study (every block of four contains each condition once); identical layout in all four conditions;
no performance claims; manipulation checks for both factors; attention check; withdrawal at any time; pseudonymised export.
The in-app survey and the server-side in-app experiment were **removed**, which also closes the earlier ethics concern about
randomising real-money allocations.

❌ Still yours: ethics approval, information sheet and debrief text (placeholders), validated item wording (drafts), a
decision on the "AI that learns from data" claim, and the sample-size note about the Holm adjustment (power is ≈ 73 %,
not 80 %, for the smaller-p outcome). All detailed in .

---

## 10. Testing, tooling and documentation

* **Tests: 0 → 98.** Backend: **77** pytest tests (round-up rules, model invariants, auth abuse cases, refresh-token
  reuse, STK lifecycle incl. duplicate and mismatched callbacks, idempotency, tenant isolation, daily caps,
  pagination, export/erasure, the study module's randomisation/consent/withdrawal, the analysis pipeline on simulated
  data with known effects, and the backtest's no-look-ahead guarantee). Kotlin: **21** JUnit tests for the SMS parser,
  round-up maths and money formatting, run against the real Kotlin compiler.
* ❌ No UI tests (Compose `createComposeRule`), no instrumentation tests for Room/Worker, no CI. Add GitHub Actions:
  `pytest` + `./gradlew testDemoDebugUnitTest` on every push.
* ✅ README rewritten. The old one described only the MVP and contradicted the code.
* ❌ No Alembic migrations (backend uses `create_all`), Room uses `fallbackToDestructiveMigration` (prototype-only),
  release build has minification off, no crash reporting/analytics, no app icon.

---

## 11. What I could and could not verify

| Layer | Verified how |
|---|---|
| Backend (FastAPI), study, analysis, backtest | 77 automated tests pass; also ran the live server and exercised sign-up → quiz → purchase → portfolio over HTTP |
| Kotlin domain (parser, round-up, money) | 21 tests pass under the real Kotlin 2.0.21 compiler |
| Pure-Kotlin models/DTOs | Type-checked by the compiler |
| **Android UI, ViewModels, Room, Retrofit wiring, Gradle** | **Not compiled.** I had no Android SDK. All files were scanned for syntax errors, but expect a few small compile fixes on first sync (imports, a Compose API signature). |
| Daraja (real M-Pesa) | Written to Safaricom's published M-Pesa Express contract; **not exercised against Safaricom**. Test in their sandbox first. |
| Rates, agents | Seed/demo data, clearly flagged in the API and UI |

---

## 12. Questions to rehearse before the viva

1. Where does *learning* happen in your AI? What is your evaluation metric and baseline?
2. Why is a user-selectable "AI mode" a threat to validity? How does your assignment fix it?
3. What happens if the same M-Pesa SMS arrives twice? If the STK callback arrives twice? If it arrives *after* a timeout?
4. Why can't the Daraja secret live in the app? Why is the callback URL secret?
5. Why store money as integer cents? What breaks with `Double`?
6. Which Play policy blocks SMS reading, and what is your fallback?
7. Which of your numbers on screen are real, and how does the user know?
8. How would a screen-reader user complete the quiz? What is your contrast ratio and how did you measure it?
9. Is it ethical to randomly give some users a fixed allocation? What does your ethics approval say?
10. What does your design do offline?
