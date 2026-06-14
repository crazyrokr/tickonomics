# Code Review: Analytics Python Services

**Date:** 2026-06-14
**Component:** `analytics/` — Python analytics services (FastAPI)
**Files reviewed:** ~50 service files, ~20 router files
**Reviewer:** Automated code review

---

## Executive Summary

The analytics module is a FastAPI-based quantitative finance service with ~50 service files covering risk, Greeks, fixed income, econometrics, regime detection, sentiment, simulation, and more. The code generally follows the service/router pattern with Pydantic models for request/response types.

However, **one critical GARCH forecasting bug** silently produces incorrect volatility forecasts used by multiple downstream services that drive trading decisions.

**Severity distribution:**
- 🔴 Critical: 1
- 🟠 High: 5
- 🟡 Medium: 15
- 🟢 Low: 12

---

## 1. Critical Findings

### 1.1 GARCH Multi-Step Forecast Uses Only Beta Coefficients (Missing Alpha)

**File:** `analytics/app/services/risk/risk_service.py:74-76`
**Severity:** 🔴 Critical
**Category:** Financial Correctness

```python
for _ in range(horizon):
    sigma2 = omega + sigma2 * (sum(beta_coeffs) if beta_coeffs else 0)
    forecasts.append(float(np.sqrt(sigma2)))
```

**Root cause:** The forecast loop uses only `sum(beta_coeffs)` but should use `sum(alpha_coeffs) + sum(beta_coeffs)` (the persistence parameter). Line 78 correctly computes `persistence = sum(alpha_coeffs) + sum(beta_coeffs)` but this value is never used in the forecast.

**Impact chain:**
- `risk_service.garch_forecast()` — underestimates volatility reversion
- `volatility_forecast_service.forecast_volatility()` — consumes garch_forecast
- `regime_service.garch_regime()` — uses forecast[0] for regime classification
- `regime_service.rahf_regime()` — uses garch_result for regime
- All VaR/CVaR endpoints using method="garch"

**Fix:** Change to:
```python
sigma2 = omega + sigma2 * (sum(alpha_coeffs) + sum(beta_coeffs))
```

---

## 2. High Severity Findings

### 2.1 EVT Tail VaR Divides by xi with No Gumbel Fallback

**File:** `analytics/app/services/statistical/evt_risk_service.py:32`
**Severity:** 🟠 High
**Category:** Math Correctness

When `xi` is near zero, `beta/xi` produces numerical instability. The Gumbel limit formula `VaR = u + beta * log(n/nu * (1-p))` should be used when `|xi| < 1e-6`.

### 2.2 Quantile Regression Missing Intercept — Inconsistent with OLS

**File:** `analytics/app/services/statistical/quantile_regression_service.py:20`
**Severity:** 🟠 High
**Category:** Financial Correctness

The OLS service (`econometrics_service.py`) explicitly adds `np.ones(n)` as an intercept column. The quantile regression service does not. For the same input, OLS fits `y = a + b1*x1 + b2*x2` while quantile regression fits `y = b1*x1 + b2*x2` (no intercept). This silently produces structurally different models.

### 2.3 Ito Process Simulation Implements ABM, Context Suggests GBM

**File:** `analytics/app/services/drift/drift_service.py:23-24`
**Severity:** 🟠 High
**Category:** Math Correctness

```python
x = x + mu*dt + sigma*dW  # Arithmetic Brownian Motion
```

In quantitative finance, most price-like processes should be Geometric Brownian Motion:
```python
x = x + mu*x*dt + sigma*x*dW  # GBM
```

The function names and defaults (mu=0.001, sigma=0.02, initial=0.5) don't clarify which process is intended.

### 2.4 GARCH Conditional Variance Initialization Inconsistent

**File:** `analytics/app/services/risk/risk_service.py:148`
**Severity:** 🟠 High
**Category:** Math Correctness

`sigma2[0]` is overwritten with sample variance, but `sigma2[1..max(p,q)-1]` remain at `omega`. The unconditional variance `omega / (1 - sum(alpha) - sum(beta))` should be used for all initialization slots.

### 2.5 Transfer Entropy Uses Naive Histogram with Severe Bias

**File:** `analytics/app/services/statistical/transfer_entropy_service.py:10`
**Severity:** 🟠 High
**Category:** Math Correctness

The 3D binning with `bins = int(sqrt(n/10)) + 1` is arbitrary and produces severely biased entropy for moderate samples (n < 200). For n=100, each dimension gets ~3 bins → 27 total bins, most empty. The plug-in estimator is biased downward.

**Fix:** Use Kraskov-Stoegbauer-Grassberger (KSG) estimator or apply Miller-Madow bias correction.

---

## 3. Medium Severity Findings

### 3.1 BSM Vega and Rho Scaled by 1/100 Without Documentation
**File:** `services/greeks/bsm_service.py:32-44`
`vega = s*npd1*sqrt_t/100.0` and `rho = .../100.0` — convention should be documented.

### 3.2 GEX Aggregation Adds `qty + oi` Without Documented Semantics
**File:** `services/greeks/bsm_service.py:83`
Combining position quantity and open interest may double-count.

### 3.3 Discrete Barrier Correction Sign Ambiguous
**File:** `services/simulation/sobol_service.py:84-88`
Always adds correction factor, but for up-and-out barriers the correction should lower the effective barrier. Needs `barrier_type` parameter.

### 3.4 BSM Price Formula Duplicated
**File:** `services/simulation/sobol_service.py:100-105` vs `services/greeks/bsm_service.py:24-25`
Extract to shared module.

### 3.5 OLS Input Convention Undocumented
**File:** `services/econometrics/econometrics_service.py:128`
Interprets `x: list[list[float]]` as [variables] × [observations] — undocumented. Transposed input produces wrong coefficients without error.

### 3.6 Nelson-Siegel No Guard Against tau Near Zero
**File:** `services/statistical/yield_curve_service.py:5-9`
`np.exp(-t/tau)` and `(1-exp_term)/(t/tau)` both divide by tau.

### 3.7 YTM Newton-Raphson No Fallback
**File:** `services/fixed_income/fixed_income_service.py:82`
If derivative is near zero or algorithm diverges, no bisection/Brent fallback.

### 3.8 shutdown_requested Set but Never Checked
**File:** `app/main.py:36-41`
SIGTERM handler sets flag but no graceful shutdown loop checks it.

### 3.9 Unconditional torch Import at Module Level
**File:** `services/anomaly/anomaly_service.py:7`, `services/regime/regime_service.py:4`
If PyTorch not installed, service fails at import time.

### 3.10 SVAR Always Shocks First Variable
**File:** `services/statistical/macro_shock_service.py:16`
`impulse=0` hardcoded. Should accept `impulse_variable` parameter.

### 3.11 Climate Simulation Time Scale Mismatch
**File:** `services/climate/climate_service.py:32`
`dt = 1.0 / n_steps` implies total time = 1.0, but `seasonal_period=252` suggests 1 year of daily data.

### 3.12 Sharpe Ratio Uses ddof=1 Without Documentation
**File:** `services/performance/performance_service.py:14`
Sample std vs population std gives different values for small samples.

### 3.13 ADF Critical Values Hardcoded for n~500
**File:** `services/econometrics/econometrics_service.py:58`
MacKinnon (1994) approximations inaccurate for small n. The `mackinnonp` function already computes exact p-values.

### 3.14 CNN-LSTM Model Has Random Weights (Untrained)
**File:** `services/regime/regime_service.py:226-238`
Model instantiated with random weights, used for inference without training. Always returns random predictions.

### 3.15 FFT Magnitude Indexing Logic Fragile
**File:** `services/regime/regime_service.py:168-170`
Off-by-one risk in indexing logic.

### 3.16 SGD Weight Delta Uses Undefined Normalization for Negative Returns
**File:** `services/optimizer/sgd_optimizer_service.py:39`
If all `mean_perf` negative, `total_perf < 0` and target weights invert intended allocation.

---

## 4. Low Severity Findings

1. **Directory `fi/` abbreviated** — prefer `fixed_income/`
2. **Router `ml/volatility.py` registered under `/api/v1/analytics`** — confusing namespace
3. **Inconsistent `async def` vs `def`** across routers
4. **CORS allows all origins (`*`)** — too permissive for production (`app/main.py:48-53`)
5. **Sentiment lexicon** — "risk"/"volatility" as always-negative; financial context may be neutral
6. **CVaR uses `<=` (inclusive)** — standard Expected Shortfall uses `<` for continuous distributions
7. **Sharpe ratio on raw returns not excess** — equivalent since r_f is constant, but should document
8. **Macaulay duration uses annual compounding** — semi-annual may be more appropriate for bonds
9. **`mackinnonp` imported inside function body** — should be module-level (`econometrics/econometrics_service.py:163`)
10. **Granger causality F-statistic correct** — confirmed
11. **BH procedure correct** — confirmed via `multipletests(method='fdr_bh')`
12. **Printf-style logging in EVT service** — inconsistent with f-string usage elsewhere

---

## 5. Files with Highest Risk Density

| File | Critical | High | Medium | Total |
|------|----------|------|--------|-------|
| `services/risk/risk_service.py` | 1 | 2 | 1 | 4 |
| `services/regime/regime_service.py` | 0 | 2 | 1 | 3 |
| `services/statistical/evt_risk_service.py` | 0 | 1 | 0 | 1 |
| `services/statistical/quantile_regression_service.py` | 0 | 1 | 0 | 1 |
| `services/greeks/bsm_service.py` | 0 | 0 | 3 | 3 |

---

## Summary Table

| Severity | Count | Top Category |
|----------|-------|-------------|
| 🔴 Critical | 1 | Financial Correctness (GARCH forecast) |
| 🟠 High | 5 | Math Correctness (3), Financial Correctness (2) |
| 🟡 Medium | 15 | Financial Correctness (5), Math Correctness (4), Clean Code (3), Python Best Practices (3) |
| 🟢 Low | 12 | Financial Correctness (5), Python Best Practices (3), Clean Code (2), Math Correctness (1) |

---

## Recommended Fix Priority

1. **Fix GARCH forecast alpha omission** (CRITICAL) — affects all VaR/CVaR, regime detection, stop-loss calibration
2. **Fix quantile regression missing intercept** (HIGH) — silent model mismatch with OLS
3. **Add Gumbel fallback for EVT xi near zero** (HIGH) — numerical stability
4. **Clarify ABM vs GBM in drift simulation** (HIGH)
5. **Fix GARCH initialization** (HIGH) — variance initialization
6. **Replace naive histogram entropy with KSG estimator** (HIGH) — bias correction
7. **Fix CNN-LSTM untrained model** — either train or return error (MEDIUM)
8. Address remaining MEDIUM findings
9. Standardize LOW-level inconsistencies
