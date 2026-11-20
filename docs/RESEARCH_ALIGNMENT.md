# Chapters 1–3 vs. the CoinSprout project

*What the three chapters require, what was missing, what I added, and what you still have to decide.
Status key: ✅ built and tested · ⚠️ built, needs your decision · ❌ not code — your text/ethics/data.*

---

## 1. Bottom line

Your chapters describe **a one-session online experiment with no real money**, plus a **supporting walk-forward
backtest**. Before this pass, the project contradicted that design in three ways:

| Chapters say | Project did | Now |
|---|---|---|
| Illustration only, **no real money** (Ch1 §1.7) | Randomised the experiment *inside the live M-Pesa app* | ✅ Experiment removed from the app. New standalone **study module** (`/study`) with no payments, no login, no phone/M-Pesa data |
| Randomise by **year of study** (Ch1 §1.7, Ch3) | Arms assigned by simple minimisation, not stratified | ✅ **Stratified permuted blocks of 4** per year of study (every block of 4 contains each condition once) |
| AI label is justified because the engine "**uses a component that learns from data**" (Ch1 §1.7) | Engine was a hand-set rule table; nothing learned from data | ⚠️ Added a transparent **data-derived component** (volatility tilt) — but see §3.1, you must decide if that satisfies your claim |
| Walk-forward backtest on 208 NSE days (Obj. 4) | No backtest code | ✅ `backtest/walk_forward.py`, no look-ahead (tested) |
| Holm, mediation, E1, E2 (Ch1 §1.6, Ch3) | No analysis code | ✅ `analysis/study_analysis.py`, validated on simulated data with known effects |

Everything below was tested: **77 backend tests** (incl. 13 for the study module, 7 for analysis, 8 for the backtest) + 21 Kotlin tests.

---

## 2. Requirement → implementation map

| Chapter requirement | Where | Notes |
|---|---|---|
| Eligibility: ≥18, enrolled, used mobile money in last 30 days (Ch1 §1.7) | `POST /study/screen` | Failures are logged **anonymously** by reason → participant-flow diagram (`/admin/study/progress`) |
| Informed consent, voluntary, withdrawable (Ch3 ethics) | consent checkbox in `/study`; `DELETE /study/{id}` | Info sheet text is a **DRAFT placeholder** — replace with the ethics-approved sheet |
| No M-Pesa credentials / transactions collected (Ch1) | `StudyParticipant` has no phone/user/e-mail/IP columns | A test asserts this schema property |
| Baseline *before* stimulus: literacy, trust in algorithms, fintech experience, instruments used (Obj. 3) | `POST /study/{id}/baseline` | Assignment is refused until baseline is done, and baseline is **locked** after exposure (so the trust-in-algorithms scale can't be contaminated) |
| 2×2: personalised vs generic × AI label vs none (Ch1) | `POST /study/{id}/assign` | Idempotent: a page refresh can't re-randomise |
| Allocation **fixed in advance**, generic split frozen (Ch1 §1.6) | `GET /study/config` → `instrumentHash`, generic allocation | Publish this JSON in your pre-registration/appendix **before** data collection |
| Identical screen layout in all groups; no performance claims (Ch1 §1.7) | `build_stimulus()` | Same 5 fields in every condition; test enforces it. No yields, projections or returns appear |
| Outcomes: trust, adoption intention, **perceived fit** (Ch1 §1.6) | `POST /study/{id}/outcomes` | Scale means computed server-side with reverse-scoring |
| Manipulation check (AI label) + attention checks (Ch3 instrument 7) | outcomes payload | Added a **personalisation manipulation check** too (perceived-personalisation scale) — Ch2 stresses personalisation ≠ perceived fit, so you should verify the personalisation manipulation actually registered |
| Exclusions: failed attention checks, incomplete (Ch1 §1.7) | `usable` column | Manipulation-check failures are **not** auto-excluded (flagged for sensitivity analysis) |
| 480 usable (120/cell); recruit ≈ 690 (Ch1 §1.7) | `GET /admin/study/progress` | Live counts per year × cell vs targets |
| H1 + Holm over 2 outcomes | `analysis.study_analysis.h1` | OLS (HC3) with year of study; Holm via statsmodels; Cohen's d + CI; Mann–Whitney as robustness |
| H2 mediation "pattern consistent with" (Ch1, Ch2 §2.2.7) | `h2` | **Parallel mediation** (trust and perceived fit), 5,000-resample percentile bootstrap; output text says "not proof" |
| E1 interactions, exploratory | `e1` | AI-label × literacy and × trust-in-algorithms; Holm over the 4 exploratory tests |
| E2 logistic: participation in formal instruments | `e2` | Uses everyone who finished the baseline, not just completers |
| Walk-forward backtest, **consistency not outperformance** (Obj. 4, Ch2 §2.2.3) | `backtest/walk_forward.py` | Constraint compliance, risk-ordering, allocation stability, turnover; performance is reported as **descriptive only** and labelled so |
| Report period, assumptions, costs, missing data (Ch2 §2.2.3) | backtest output | `data_handling`, `cost_assumption_bps_one_way`, `limitations`, period metadata |
| Pilot / reliability (Cronbach's α) | `reliability()` | Reverse-aware; run it on your pilot export |

---

## 3. Issues found in the chapters themselves

### 3.1 Critical: the "learns from data" claim vs. the engine
Ch1 §1.7 says the AI label is included *because the engine uses a component that learns from data*. When I started, it
did not. Now there is one data-derived signal: the ratio of long-run to recent NSE volatility, computed only from data
available at decision time, which trims or adds up to 25 % / 15 % (relative) of the equity share. Be honest about its
size: it is **volatility scaling — an estimate from data, not machine learning in the usual sense**, and the
risk-category weights are still expert-set. Your examiners may or may not accept that as an "AI" component.
Decide **before data collection** and write it into 3.3, because a participant told "AI was involved" must be told
something true. Options:

* **A.** Keep the tilt, describe it precisely ("a data-driven component estimates recent market volatility and adjusts
  the share in equities"), and ask your supervisor to confirm that justifies the label.
* **B.** Add a genuinely learned component (e.g. regime classification, shrinkage covariance, or learning the
  suitability weights from pilot data) — more work, stronger claim.
* **C.** Reword the label to what is true ("computer-generated"), and say so in the limitations. Note this changes
  the construct you are testing (algorithm vs AI — Dietvorst/Logg vs your title).

The generic arm is also produced by the **same engine** (for a fixed mid-point profile), so "AI was involved" is true in
both labelled arms. A hand-typed generic table labelled "AI-generated" would be misleading.

### 3.2 Sample size and the Holm adjustment (computed, `python -m analysis.power`)
| | Power for d = 0.26, 240 vs 240 | n/group for 80 % | Recruit at 30 % loss | Min. detectable d @ 80 % |
|---|---|---|---|---|
| Single test, α = .05 (**what Ch1/Ch3 state**) | **81.1 %** ✔ | 233 | 666 | 0.256 |
| Holm step 1 for **two outcomes**, α = .025 | **72.6 %** | 282 | **807** | 0.282 |

Your 80 % claim is correct for *one* test, but H1 is pre-specified with Holm across **two** outcomes, where the
smaller-p test is judged at α = .025. Either (a) state that 80 % power is per outcome at α = .05 and that the family-wise
design has ≈ 73 % power, (b) plan for ≈ 565 usable / ≈ 810 recruited, or (c) pre-specify that the two outcomes are
combined into one primary composite. Also: 480 / 0.7 = 685.7, so "around 690" is right.
In simulation, the mediation test (indirect effect through trust) had 72–80 % power at n = 480 **only if the true effect of
personalisation on trust is d ≈ 0.26**; if it is smaller, mediation will be underpowered. Say so under Limitation 2.
E1 interactions: a 2×2 interaction contrast needs roughly d ≈ 0.5 at n = 120/cell — Limitation 7 is right to call them
exploratory.

### 3.3 Wording inconsistencies to fix
1. **Ch1 §1.4** heading reads "The study has specific objectives:" (number missing) and lists **four**; **§1.5** says the RQs are
   "based on the first four specific objectives. The fifth objective is a supporting technical check". There is no fifth
   objective: the backtest *is* objective 4, and the four RQs map to objectives 1, 1, 2, 3 ("first three").
2. **Ch1 Limitation 3** says "the study will use an allocation that is fixed in advance" — personalised allocations differ
   per participant. Say what is fixed: *the allocation **rules**, engine settings and the generic split*.
3. **Ch2 reference to Ochieng & Kamau (2025)** shortens the headline to "…drops 100 shares". The published headline is
   "Retail investors win big as Nairobi bourse drops 100 **minimum** shares" (as in your Chapter 1 reference list).
4. **Ch3 placeholders** still to fill: survey platform, Likert points, literacy item source/scoring, trust-in-algorithms scale
   source, engine description (§4 below), analysis software, recruitment channels, sampling-frame source, ethics approval,
   debrief text.
5. Ch1 describes personalisation as using "goals, time horizon, available funds or stated risk preferences" (Ch2 §2.2.4).
   The study collects goal, horizon, risk preference and income stability — **not funds** (the study asks nothing about money
   held). Remove "available funds" or add the question.

### 3.4 Facts I checked and facts I did not
✅ Verified by search: NSE moved to single-share trading in **August 2025** (sources differ on the effective day, 1 vs 8 August;
"August 2025" is safe). Safaricom launched **Ziidi Trader** in M-Pesa on **10 Feb 2026** and Reuters covered it that day.
⚠️ **Not verified**: FinAccess 2024 figures (84.8 %, 52.6 % daily, 18.3 % financially healthy, 83.7 % in 2021), the Anyanzwa (2022)
CDSC account numbers, Mohamed (2026) *FinTech* 5(1) article 11, and the other journal citations. Check each against the source
before submission.

---

## 4. Ready-to-paste text for Chapter 3 (accurate to the code)

**Allocation engine (instrument item 9).** *"The engine takes three self-reported inputs: time horizon (under one year, one to
three years, over three years), risk preference (three levels) and income stability (three levels). These are combined into a
suitability score (weights 0.40, 0.40 and 0.20). The score is converted into probabilities of belonging to three risk categories
(Conservative, Moderate, Aggressive) using a Gaussian likelihood centred on −0.75, 0 and +0.75 (σ = 0.45). The recommended
allocation is the probability-weighted blend of three base portfolios (money market fund / Treasury bills / equity unit trust /
cash: Conservative 70/20/5/5, Moderate 40/25/25/10, Aggressive 15/15/60/10). A data-driven component estimates the ratio of
long-run to recent equity volatility from NSE data available at the decision date and scales the equity share by a factor
bounded between 0.75 and 1.15, with the difference taken from or given to the money market fund and Treasury bills pro rata.
Guard-rails then apply: equity is capped at 10 % when the horizon is under one year, and cash is at least 5 % (10 % when income
is irregular). Weights are rounded to whole percentages summing to 100. The engine does not forecast returns, and no return
projections are shown to participants. All parameters, the generic split (41/23/27/9, the engine's output for a mid-point
respondent) and the frozen volatility ratio are fixed and published before data collection (engine version expert-prior-v1)."*
— adjust the last volatility sentence to match whichever option in §3.1 you choose.

**Randomisation.** *"Participants are randomly assigned, after completing the baseline measures, to one of four conditions using
stratified permuted-block randomisation: within each year of study, each consecutive block of four participants contains each
condition once in random order."*

**Backtest (measures).** *"At weekly decision dates after a 60-day warm-up, the engine receives only data available up to that
date. We report (i) constraint compliance (weights sum to 100, non-negative, cash buffer, short-horizon equity cap, tilt bounds);
(ii) risk ordering (equity weight non-decreasing from cautious to growth profiles on every date); (iii) allocation stability
(mean and maximum weekly change and turnover); and (iv) descriptive realised return, volatility and maximum drawdown for the
period following each decision, for the engine with and without the volatility component. Costs are set to X bps one-way
(sensitivity at Y bps); missing yield values are carried forward for at most five days and rows with missing index values are
dropped and counted. These results describe the reasonableness of outputs over this window only and make no claim of
outperformance."* (A test proves that altering future data cannot alter earlier allocations.)

**Literacy (item 3).** *"Three items adapted to Kenyan shillings from the 'Big Three' financial literacy questions (Lusardi &
Mitchell, 2014) on compound interest, inflation and risk diversification. Correct = 1, incorrect or 'don't know' = 0; range 0–3."*

**Analysis software.** *"Python 3 with pandas, SciPy and statsmodels; scripts are archived with the data."*

---

## 5. Decisions only you can make

| Decision | My default | Why it matters |
|---|---|---|
| Likert points | **7** (`STUDY_LIKERT_POINTS`) | Much of the technology-acceptance literature uses 7-point scales (e.g. Davis, 1989); check the scales you adapt. 5 is shorter on a phone |
| Own page (`/study`) vs Qualtrics/REDCap | own page built | Own hosting means *you* secure it and defend it to ethics; Strathmore may license Qualtrics. The instrument and logic are in `study/instrument.py` if you port it |
| Item wording | **DRAFT** | Replace each scale with the validated items you cite, then pilot (target α ≥ .70 per scale) |
| Access code | off | Set `STUDY_ACCESS_CODE` to restrict to invited students (convenience sampling + spam control) |
| Frozen volatility ratio | unset (no tilt) | `python -m backtest.walk_forward data.csv --latest-ratio`, then set `STUDY_EQUITY_VOL_RATIO` |
| Ethics approval, information sheet, debrief | placeholders | ❌ Needed before any real participant. Also check any research-licence requirement with your supervisor |
| Personalised = generic for some participants | flagged (`same_as_generic`) | A mid-point respondent receives the generic split. Pre-specify whether to analyse them as-is (intention-to-treat) and report a sensitivity analysis |
| Participant recruitment & data protection | no contact data stored | Hosting logs record IP addresses by default: turn off or truncate access logs on the study server |

## 6. Run it

```bash
cd backend && pip install -r requirements.txt -r requirements-analysis.txt
pytest                                               # 77 tests
export STUDY_ACCESS_CODE=SU2026 ADMIN_API_KEY=...    # optional invite code, admin key
uvicorn app.main:app --port 8000                     # participants open http://HOST:8000/study
curl -H "X-Admin-Key: $ADMIN_API_KEY" localhost:8000/api/v1/study/config            # freeze + publish this
curl -H "X-Admin-Key: $ADMIN_API_KEY" localhost:8000/api/v1/admin/study/progress    # recruitment dashboard
curl -H "X-Admin-Key: $ADMIN_API_KEY" localhost:8000/api/v1/admin/study/export.csv > export.csv
python -m analysis.study_analysis export.csv --boot 5000 > results.json
python -m backtest.walk_forward nse_208_days.csv --out backtest.json
```
