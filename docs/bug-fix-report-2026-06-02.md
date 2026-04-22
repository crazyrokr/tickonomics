# Bug Fix Report — External Validation (2026-06-02)

## Objective

Verify all 6 bugs from the external validation report against source code and implement fixes.

## Bugs Verified & Fixed

### B1 — GARCH Gradient Ascent Only Updates Omega (CRITICAL)

**File**: `app/services/risk/risk_service.py`

**Root cause**: `_estimate_garch` computed the gradient only for `omega`. Alpha and beta were initialized to `[0.1]` and `[0.85]` and never updated by any gradient step.

**Fix**: Replaced hand-rolled gradient ascent with `scipy.optimize.minimize` (L-BFGS-B) on the full negative log-likelihood. Fixed sigma2 initialization to use unconditional variance `omega / (1 - sum(alpha) - sum(beta))` instead of just `omega`.

**Blast radius**: 4 services — `risk_service.garch_forecast`, `volatility_forecast_service.forecast_volatility`, `regime_service.garch_regime`, `regime_service.rahf_regime`.

---

### B2 — Sobol Parameters Never Used in Robustness Scan (CRITICAL)

**File**: `app/services/backtest/robustness_service.py`

**Root cause**: The loop constructed a `params` dict from Sobol samples but computed Sharpe/drawdown on a fixed `active_returns` slice without using `params`.

**Fix**: Sobol-sampled params now control the evaluation — `window` parameter varies the lookback window size, `threshold` parameter filters returns below a minimum absolute value.

---

### B3 — ADF P-Value Always Clips to 1.0 (CRITICAL)

**File**: `app/services/econometrics/econometrics_service.py`

**Root cause**: `_adf_pvalue` used `exp(-0.1234 - 1.6221*stat - 0.0502*stat^2)`. For negative statistics (the normal ADF case), this blows up and clips to 1.0, making the test always report "non-stationary".

**Fix**: Replaced with `statsmodels.tsa.stattools.mackinnonp` which implements the MacKinnon (1996) response surface approximation correctly.

---

### B4 — CIR Formula Does Not Match Hull Ch.31 (CRITICAL)

**File**: `app/services/fi/q_world_pricer_service.py`

**Root cause**: Used Vasicek B formula `(1 - exp(-kappa*T)) / kappa` instead of CIR B. Used a linear A expression instead of the exponential-based CIR A. Used `-(a - b*r0)/T` instead of `-(log(A) - B*r0)/T`. Produced yields of 56–92% instead of ~4%.

**Fix**: Replaced with textbook CIR bond pricing from Hull Ch.31 using correct B and A formulas with `h = sqrt(kappa^2 + 2*sigma^2)`.

---

### B5 — Confidence Intervals Use `response * ±1.96` (HIGH)

**File**: `app/services/statistical/macro_shock_service.py`

**Root cause**: CI bounds were `irf * 1.96` and `irf * -1.96` (multiplication) instead of `irf ± 1.96 * SE` (addition with standard error).

**Fix**: Replaced with residual bootstrap confidence intervals (200 replications, 2.5/97.5 percentiles). Falls back to asymptotic approximation if all bootstrap fits fail.

---

### B6 — "LSTM" is Momentum + Noise (MEDIUM)

**File**: `app/services/benchmark/tournament_service.py`

**Root cause**: The "LSTM" computation was `momentum_returns + N(0, 0.01)` — no LSTM cell, no weights, no training.

**Fix**: Renamed "lstm" to "momentum_noise" throughout the service and all test files to accurately describe the computation.

## Test Results

| Suite | Before | After |
|-------|--------|-------|
| External validation | 69 passed, 4 xfailed, 6 xpassed | **78 passed, 1 xpassed**, 0 xfailed |
| Full suite (unit + integration + external) | — | **377 passed, 3 skipped, 1 xpassed, 0 failures** |

The 1 remaining xpassed is `test_step31_autoencoder_vs_zscore_extreme_outliers` (UNVERIFIABLE — see below).

## Files Modified

### Source
- `app/services/risk/risk_service.py` — GARCH MLE estimation
- `app/services/backtest/robustness_service.py` — Sobol parameter application
- `app/services/econometrics/econometrics_service.py` — MacKinnon p-value
- `app/services/fi/q_world_pricer_service.py` — CIR bond pricing
- `app/services/statistical/macro_shock_service.py` — Bootstrap CIs
- `app/services/benchmark/tournament_service.py` — LSTM → momentum_noise rename

### Tests
- `tests/external_validation/test_group_a_closed_form.py` — Removed B3, B4 xfail markers
- `tests/external_validation/test_group_b_statistical.py` — Removed B1, B5 xfail markers; rewrote bug-documenting tests
- `tests/external_validation/test_group_c_stochastic.py` — Removed B2 xfail marker; rewrote test
- `tests/external_validation/test_group_d_heuristic.py` — Removed B1, B6 xfail markers; renamed lstm references
- `tests/test_tournament_service.py` — Updated lstm → momentum_noise

## Residual Risk

- **B5**: Bootstrap CIs add ~30s compute time per call. Consider a `fast_ci=True` flag for latency-sensitive paths.
- **B1**: `scipy.optimize.minimize` may not converge on pathological data. Guarded by `len(returns) < 50` check.
- **B4**: CIR parameters (kappa=0.5, theta=current_yield*0.98, sigma=0.01) are hardcoded. Consider parameterizing for different instruments.
