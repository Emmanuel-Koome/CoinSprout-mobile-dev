# CoinSprout AI engine (training)

Merged from [`clive-strath/SemProject_MLEngine`](https://github.com/clive-strath/SemProject_MLEngine) at commit `b03f05d`.
The notebooks are kept unchanged in `notebooks/` for the report; `train.py` is the reproducible version the app uses.

| Step | What | Trained? | Output (in `backend/app/ml/artifacts/`) |
|---|---|---|---|
| 1 | Risk classifier: 4 quiz answers (1-5) → Conservative / Moderate / Aggressive. LR, RF and SVM compared; LR exported | **Yes** (simulated answers until pilot data exists) | `risk_classifier.json` |
| 2 | Monthly MMF rates + NASI index → expected return `mu`, covariance `cov`, 200 block-bootstrap resamples | Estimated from data | `asset_stats.json` |
| 3 | Mean-variance split MMF / NSE equity, bounds and λ per category, stability test | No (an optimiser) | `allocation_config.json` |

`training_report.json` records the model comparison, confusion matrix, data period, warnings and the stability test.
`ml/output/` (git-ignored) holds the bootstrap samples and monthly returns for charts in the report.

## Run

```bash
backend/.venv/Scripts/python ml/train.py                          # Windows; use backend/.venv/bin/python elsewhere
backend/.venv/Scripts/python ml/train.py --dividend-yield 0.06    # add a SOURCED NSE dividend yield (price index only otherwise)
backend/.venv/Scripts/python ml/train.py --pilot-csv pilot.csv    # real answers: time_horizon,risk_tolerance,income_stability,investment_experience,risk_category
```
Needs `pip install -r ml/requirements.txt`. The MMF data downloads from Hugging Face on first run and is cached in
`ml/data/cache/`. Re-run `pytest` in `backend/` afterwards: the engine tests check the new artifacts.

## The questionnaire (must match the app and `RiskProfileIn`)

| Feature | Question | 1 … 5 |
|---|---|---|
| `time_horizon` | When might you need this money? | within a year … not for 10+ years |
| `risk_tolerance` | If your investment dropped 20% in a month, you would… | withdraw everything … add more |
| `income_stability` | How steady is your income or allowance? | very irregular … very steady |
| `investment_experience` | How much have you invested before? | never … bought NSE shares |

## Differences from the notebooks (on purpose)

* `goal_type` was a decoy feature for testing the models and is not asked in the app.
* **Covariance:** the notebook applied Ledoit-Wolf to raw returns. Its target is one average variance, which inflated
  the MMF's volatility from 0.54% to 4.47% a year. `train.py` shrinks the correlation instead and keeps each asset's variance.
* Step 3 is solved in closed form (two assets) and checked against the notebook's SLSQP solver on every run.

## Known limitations (say these in the report)

* Classifier labels are **simulated** from a weighted score plus noise, so the model mostly re-learns that formula.
* NASI is a **price** index: with no dividends, equities averaged 2.2% a year against 10.5% for MMFs (2017-11 to 2024-12),
  so every category sits on its equity **floor** unless a sourced dividend yield is added.
* Only 79 common months of data. The MMF series averages fund houses, not individual money market funds.
* Educational use only. Not financial advice.
