# Phase 2.5: External Validation — Are the Platform's Calculations Correct?

## Context

The analytics platform has 29 services, 55 public functions, and 42 API endpoints. The existing 30 test files use synthetic data with range/structural assertions but **zero external oracle validation**. The core question — *"Is the platform's advice correct?"* — remains unanswered. This phase cross-validates every service against independent implementations using different algorithms and libraries.

## Pre-Validation Bug Discoveries (6 confirmed)

Six bugs were confirmed through direct oracle comparison during exploration. These are the critical findings:

| ID | Severity | Service | Bug |
|----|----------|---------|-----|
| B1 | CRITICAL | `risk_service._estimate_garch` | Gradient ascent only updates `omega`. Alpha `[0.1]` and beta `[0.85]` are **never updated** across all 50 iterations. All GARCH outputs use hardcoded initial values regardless of input data. Affects: `risk_service.garch_forecast`, `volatility_forecast_service`, `regime_service.garch_regime`, `regime_service.rahf_regime`. |
| B2 | CRITICAL | `robustness_service.robustness_scan` | Sobol-sampled parameters are **never used** in evaluation. All evaluations produce identical Sharpe regardless of parameter values. The "robustness scan" is meaningless. |
| B3 | CRITICAL | `econometrics_service._adf_pvalue` | P-value approximation `exp(-0.1234 - 1.6221*stat - 0.0502*stat^2)` explodes for all negative statistics (the entire practical range). Output is always clipped to 1.0. **Every ADF test reports p-value=1.0 regardless of data.** |
| B4 | CRITICAL | `q_world_pricer_service.compute_fair_value` | CIR bond formula is not the standard textbook formula from Hull Ch.31. Produces nonsensical yields (91.66 instead of 0.0424). Also has unit inconsistency between sigma/kappa (decimal) and current_yield (percent). |
| B5 | HIGH | `macro_shock_service` confidence intervals | CIs computed as `response * ±1.96` — this is just scaling the point estimate, not `point_estimate ± 1.96 * standard_error`. Confidence bands are meaningless. |
| B6 | MEDIUM | `tournament_service` "LSTM" | "LSTM" strategy is `momentum_returns + rng.standard_normal() * 0.01` — momentum plus tiny noise, not a neural network. |

## Oracle Libraries

**Installed and usable:** numpy 2.4.6, scipy 1.17.1, statsmodels 0.14.6, torch 2.12.0+cpu, pandas 3.0.3.

**Not installed but not needed:** arch, scikit-learn, nltk, shap, QuantLib. All oracles use what's already available.

## File Structure

```
analytics/tests/external_validation/
├── __init__.py
├── conftest.py                          # shared tolerances and helpers
├── test_group_a_closed_form.py          # Steps 1-18
├── test_group_b_statistical.py          # Steps 19-25
├── test_group_c_stochastic.py           # Steps 26-30
├── test_group_d_heuristic.py            # Steps 31-35
└── verdicts.py                          # CORRECT/INCORRECT/UNVERIFIABLE/BUG_FOUND enum
```

---

## Group A: Deterministic Closed-Form (Steps 1–18)

Every test compares the service output to an independently computed expected value using a **different algorithm** than the service uses.

### Step 1 — `bsm_service.bsm_greeks` vs erfc-based BSM

- **Service uses:** `scipy.stats.norm.cdf/pdf`
- **Oracle uses:** `math.erfc` for N(x) = 0.5 * erfc(-x / sqrt(2)) — completely independent of scipy.stats
- **Test cases:** ATM call (S=100, K=100, T=1, r=0.05, σ=0.2), ATM put, deep ITM call (S=200, K=100), deep OTM call (S=50, K=100)
- **Tolerance:** 1e-4 per greek
- **Also:** put-call parity C - P = S - K*exp(-rT) to 1e-6

### Step 2 — `aggregate_gex` vs manual

- Use erfc-based gamma from Step 1 for each position
- Compute dollar gamma = gamma * S^2 * quantity * 100 * open_interest
- Compare net GEX and flip point

### Step 3 — `fixed_income_service.macaulay_duration`, `convexity` vs pure Python loop

- **Service uses:** numpy array operations
- **Oracle uses:** Python `for` loop with `math.pow` for discount factors
- **Test cases:** 5Y 5% semi-annual bond (duration ≈ 4.49), zero-coupon 5Y (duration = 5.0 exactly), zero-coupon convexity = t*(t+1)/(1+y)^2
- **Tolerance:** 1e-4

### Step 4 — `yield_to_maturity` vs `scipy.optimize.newton`

- **Service uses:** hand-rolled Newton-Raphson
- **Oracle uses:** `scipy.optimize.newton` on the pricing equation PV(CF) - price = 0
- **Test cases:** par bond (coupon = yield), discount bond, premium bond
- **Tolerance:** 1e-6

### Step 5 — `yield_curve_service.fit_yield_curve` vs `scipy.optimize.minimize`

- **Service uses:** `scipy.optimize.curve_fit` (Levenberg-Marquardt)
- **Oracle uses:** `scipy.optimize.minimize` (Nelder-Mead) with manual least-squares loss on same Nelson-Siegel model
- **Test cases:** synthetic yields from known b0=0.05, b1=-0.02, b2=0.01, tau=3 at maturities [0.25, 0.5, 1, 2, 5, 10, 30]
- **Tolerance:** fitted parameters within 1e-3

### Step 6 — `multiple_testing_service.apply_fdr_correction` vs hand-coded BH

- **Service uses:** `statsmodels.stats.multitest.multipletests(method="fdr_bh")`
- **Oracle uses:** hand-coded BH step-up from Benjamini-Hochberg 1995: sort p-values, compute (m/i)*p_(i), enforce monotonicity from largest to smallest
- **Test cases:** all-significant [0.001, 0.002, 0.003], mixed [0.001, 0.04, 0.08], all-insignificant [0.3, 0.5, 0.7]
- **Tolerance:** 1e-6 (should be exact)

### Step 7 — `amihud_service.compute_amihud` vs list comprehension

- **Service uses:** numpy mean
- **Oracle uses:** `sum(abs(r) / dv for r, dv in zip(returns, volumes)) / len(returns)`
- **Tolerance:** 1e-8

### Step 8 — `comovement_pca_service` vs SVD-based PCA

- **Service uses:** `np.linalg.eigh` on covariance matrix (eigendecomposition)
- **Oracle uses:** `np.linalg.svd` on centered data (SVD — fundamentally different algorithm)
- **Validate:** PC1 and PC2 variance explained ratios match to 0.01
- **Note:** eigenvector signs may flip, so compare `abs(loadings)`

### Step 9 — `strategic_runs_service` vs manual VWAP loop

- Compute VWAP via Python loop over windows
- Verify acceleration score, state classification, and transition matrix

### Step 10 — `sgd_optimizer_service.compute_weight_delta` vs manual numpy

- Step-by-step manual computation: mean performance per key → normalize → delta = lr * (target - current) → clip → project
- **Tolerance:** 1e-9

### Step 11 — `rds_scorer_service.compute_rds` — exhaustive enumeration

- Enumerate all 32 boolean input combinations (2^5)
- Verify score for each combination against manually computed expected score
- **Tolerance:** exact (integer arithmetic)

### Step 12 — `diagnostic_service` ACF vs `statsmodels.tsa.stattools.acf`

- **Service uses:** manual autocorrelation computation
- **Oracle uses:** `statsmodels.tsa.stattools.acf`
- **Test:** generate AR(1) process with known ACF = phi^lag
- **Tolerance:** 0.01 per lag
- **Also:** QQ-plot theoretical quantiles vs `scipy.stats.probplot`

### Step 13 — `q_world_pricer_service.compute_fair_value` vs textbook CIR

- **Service uses:** custom CIR formula (incorrect per B4)
- **Oracle uses:** Hull Ch.31 formula: `h = sqrt(kappa^2 + 2*sigma^2)`, `B(t) = 2*(exp(hT)-1) / (2h + (kappa+h)*(exp(hT)-1))`, `A(t) = (2h*exp((kappa+h)T/2) / (2h + (kappa+h)*(exp(hT)-1)))^(2*kappa*theta/sigma^2)`, `P(t,T) = A(t)*exp(-B(t)*r0)`
- **Expected verdict:** BUG_FOUND (B4)

### Step 14 — `performance_service.sharpe_ratio`, `sortino_ratio` vs manual numpy

- Oracle: `np.mean(R - rf) / np.std(R - rf, ddof=1) * sqrt(252)` for Sharpe
- Oracle: `np.mean(R - rf) / np.sqrt(np.mean(np.minimum(R - rf, 0)**2)) * sqrt(252)` for Sortino
- **Tolerance:** 1e-4

### Step 15 — `fama_french_regression` vs `statsmodels.regression.linear_model.OLS`

- **Service uses:** `np.linalg.lstsq` + manual inference
- **Oracle uses:** `statsmodels.regression.linear_model.OLS` (completely different code path)
- **Test:** generate data with known coefficients y = 1.2*market + 0.3*smb - 0.5*hml + noise
- **Tolerance:** coefficients within 1e-3, R² within 0.01

### Step 16 — `econometrics_service.ols_regression` vs `statsmodels OLS`

- Same as Step 15 but for the standalone OLS function
- **Tolerance:** 1e-4

### Step 17 — `adf_test` vs `statsmodels.tsa.stattools.adfuller`

- **Service uses:** hand-rolled ADF with custom p-value approximation
- **Oracle uses:** `statsmodels.tsa.stattools.adfuller` (MacKinnon approximations)
- **Test cases:** random walk (non-stationary), white noise (stationary), AR(1) with phi=0.9
- **Expected:** test statistics match to 1e-3 at matched lag, but p-value will be 1.0 vs. correct value
- **Expected verdict:** BUG_FOUND (B3)

### Step 18 — `granger_causality` vs `statsmodels.tsa.stattools.grangercausalitytests`

- **Service uses:** hand-rolled restricted/unrestricted VAR + F-test
- **Oracle uses:** `statsmodels.tsa.stattools.grangercausalitytests`
- **Test:** `y[t] = 0.5*x[t-1] + 0.3*y[t-1] + noise` — x should Granger-cause y
- **Also test:** independent series — should show no causality
- **Tolerance:** F-statistic within 5%, causal/not-causal direction must match

---

## Group B: Statistical Estimation (Steps 19–25)

### Step 19 (HIGHEST PRIORITY) — `risk_service.garch_forecast` vs `scipy.optimize.minimize`

- **Service uses:** hand-coded gradient ascent (50 iterations, lr=0.001, only updates omega)
- **Oracle uses:** `scipy.optimize.minimize` (L-BFGS-B) optimizing ALL GARCH(1,1) parameters simultaneously via negative log-likelihood
- **Test:** simulate GARCH(1,1) data with known omega=0.1, alpha=0.15, beta=0.80, 1000 observations
- **Expected verdict:** BUG_FOUND (B1) — service alpha/beta will be exactly [0.1, 0.85] regardless of data

### Step 20 — `value_at_risk`, `conditional_var` vs manual numpy

- **Historical VaR:** oracle = `np.percentile(returns, 100 * (1 - confidence))`
- **Parametric VaR:** oracle = `np.mean(returns) + scipy.stats.norm.ppf(1 - confidence) * np.std(returns, ddof=1)`
- **CVaR:** oracle = `np.mean(returns[returns <= var_value])`
- **Tolerance:** 1e-4

### Step 21 — `evt_risk_service` vs manual GPD MLE

- **Service uses:** `scipy.stats.genpareto.fit`
- **Oracle uses:** `scipy.optimize.minimize` on negative GPD log-likelihood
- **Test:** simulate GPD(xi=0.3, beta=1.5) exceedances, fit both ways
- **Tolerance:** 10% relative on shape and scale

### Step 22 — `macro_shock_service` point estimates vs manual VAR(1) OLS

- **Service uses:** `statsmodels.tsa.statespace.varmax.VARMAX`
- **Oracle uses:** manual OLS for VAR(1): `B = (X'X)^{-1} X'Y`, then IRF = sum of coefficient matrix powers
- **Test:** 2-variable VAR(1) with known coefficient matrix
- **Expected verdict:** point estimates CORRECT, confidence intervals BUG_FOUND (B5)

### Step 23 — `quantile_regression_service` vs manual LP

- **Service uses:** `statsmodels.regression.quantile_regression.QuantReg`
- **Oracle uses:** `scipy.optimize.linprog` with the Koenker-Bassett LP formulation for quantile regression
- **Test:** linear data y = 2x + 3 + noise, fit 0.5-quantile
- **Tolerance:** 5% relative on slope and intercept

### Step 24 — `transfer_entropy_service` on known processes

- **Test 1 (coupled):** `y[t] = 0.8*x[t-1] + noise` → TE(X→Y) >> 0, TE(Y→X) ≈ 0
- **Test 2 (independent):** independent Gaussians → TE ≈ 0 in both directions
- **Test 3 (bidirectional):** `x[t] = 0.5*y[t-1] + noise`, `y[t] = 0.5*x[t-1] + noise` → TE > 0 both ways
- **No tolerance needed:** directional correctness is sufficient

### Step 25 — `volatility_forecast_service` (delegates to GARCH)

- Inherits B1 from `risk_service.garch_forecast`
- Expected verdict: BUG_FOUND

---

## Group C: Stochastic Simulation (Steps 26–30)

### Step 26 — `sobol_service` convergence + analytical BSM + put-call parity

- **Convergence test:** `abs(price_4096 - analytical) < abs(price_256 - analytical)` — error must decrease with more paths
- **Analytical test:** with n_paths=8192, price within 2% of analytical BSM
- **Put-call parity:** call - put ≈ S - K*exp(-rT) within 1%
- **Discrete correction:** verify Broadie-Glasserman-Kou beta = 0.5826

### Step 27 — `climate_service` vs analytical OU stationary distribution

- **Service simulates:** Ornstein-Uhlenbeck with Euler-Maruyama
- **Oracle:** analytical stationary distribution N(mu, sigma²/(2*theta))
- **Test:** run with seasonal_amplitude=0, n_paths=10000, n_steps=1000. Collect terminal values. Run `scipy.stats.kstest` against N(mu, sigma²/(2*theta))
- **Pass:** KS p-value > 0.01

### Step 28 — `drift_service.simulate_ito` vs analytical ABM

- **Oracle:** terminal distribution is N(X0 + mu*T, sigma²*T)
- **Test:** n_paths=10000, n_steps=1000. KS test against analytical distribution
- **Barrier hitting:** compare hitting probability against analytical first-passage probability for ABM with drift
- **Pass:** KS p-value > 0.01, hitting probability within 5% of analytical

### Step 29 — `robustness_service` Sobol sampling + evaluation check

- **Sampling check:** verify Sobol samples are uniform in [0,1]^d via `scipy.stats.kstest`
- **Evaluation check:** for each sampled parameter set, manually compute Sharpe with those parameters applied to the returns
- **Expected verdict:** BUG_FOUND (B2) — all evaluations produce identical results

### Step 30 — `markov_stop_service` vs systematic grid search

- Run service with seed=42 on a known PnL series
- Run independent 100×100 grid search over (stop_loss, take_profit) space
- Service should find parameters within 10% Sharpe of the grid optimum

---

## Group D: Heuristic/ML (Steps 31–35)

### Step 31 — `anomaly_service` vs Z-score cross-comparison

- Generate data with injected outliers (10x, 5x, 3x deviations)
- Compute Z-score anomalies independently: `abs((x - mean) / std) > 3`
- Compute Jaccard similarity between autoencoder anomalies and Z-score anomalies
- **Expected:** Jaccard > 0.5 for extreme outliers (10x)
- **Flag:** unseeded training makes results non-reproducible
- **Expected verdict:** UNVERIFIABLE (different method, non-deterministic)

### Step 32 — `sentiment_service` vs manual lexicon enumeration

- For each word in the service's positive list, verify it produces a positive contribution
- For each word in the negative list, verify negative contribution
- Test negation handling: "not good" should be less positive than "good"
- **Expected verdict:** CORRECT within its defined lexicon scope

### Step 33 — `regime_service` sub-model validation

- `garch_regime`: INCORRECT (inherits B1 — GARCH is broken)
- `cnn_lstm_regime`: UNVERIFIABLE (untrained random weights produce arbitrary output)
- `qed_regime`: validate quartic potential computation manually — expect CORRECT
- `rahf_regime`: INCORRECT (GARCH component inherits B1)

### Step 34 — `tournament_service` component validation

- ILI rule engine: validate Sharpe and hit_rate computation manually — expect CORRECT
- Momentum: validate moving average crossover manually — expect CORRECT
- "LSTM": flag as INCORRECT (B6 — it is momentum + noise, not a neural network)

### Step 35 — `shap_service` mechanism validation

- Verify the weighted-baseline computation matches manual calculation
- Verify perturbation effect matches manual: `delta = |baseline - perturbed_baseline|`
- Flag as permutation sensitivity analysis, NOT true Shapley values
- **Expected verdict:** UNVERIFIABLE as SHAP, mechanism CORRECT as perturbation analysis

---

## Expected Verdict Summary

| Verdict | Count | Services |
|---------|-------|----------|
| CORRECT | 22 | bsm, fixed_income, yield_curve, multiple_testing, amihud, comovement_pca, strategic_runs, sgd_optimizer, rds_scorer, diagnostics, sharpe, sortino, fama_french_ols, ols_regression, granger_causality, evt_risk, quantile_regression, transfer_entropy, sobol, climate, drift, sentiment |
| BUG_FOUND | 8 | risk_service.garch (B1), volatility_forecast (B1), garch_regime (B1), rahf_regime (B1), robustness_service (B2), adf_test (B3), q_world_pricer (B4), macro_shock CI (B5), tournament "LSTM" (B6) |
| UNVERIFIABLE | 3 | anomaly_service, cnn_lstm_regime, shap_service |

## Critical Files to Create

| File | Action |
|------|--------|
| `analytics/tests/external_validation/__init__.py` | Create (empty) |
| `analytics/tests/external_validation/conftest.py` | Create (shared tolerances, helpers) |
| `analytics/tests/external_validation/verdicts.py` | Create (verdict enum) |
| `analytics/tests/external_validation/test_group_a_closed_form.py` | Create (Steps 1-18) |
| `analytics/tests/external_validation/test_group_b_statistical.py` | Create (Steps 19-25) |
| `analytics/tests/external_validation/test_group_c_stochastic.py` | Create (Steps 26-30) |
| `analytics/tests/external_validation/test_group_d_heuristic.py` | Create (Steps 31-35) |
| `docs/external_validation_report.md` | Create (final verdict table) |

**No new package installations required.** All oracles use numpy, scipy, statsmodels already installed.

## Verification

1. `cd analytics && python -m pytest tests/external_validation/ -v --tb=short` — all tests must run
2. Tests that find bugs should FAIL with clear error messages showing the discrepancy
3. Tests that validate correctly should PASS
4. After this phase, update `docs/analytics-test-verification-plan-final.md` to incorporate the findings
5. Write ADR at `docs/adr/` documenting the external validation methodology and findings
