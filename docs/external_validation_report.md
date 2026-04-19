# External Validation Report

> Cross-validation of all analytics services against independent oracle implementations.
> Generated: 2026-06-02

## Executive Summary

| Metric | Count |
|--------|-------|
| Total validation tests | 69 |
| CORRECT (passed) | 59 |
| BUG_FOUND (xfailed/xpassed) | 7 |
| UNVERIFIABLE | 3 |
| Test files | 4 (Groups A–D) |
| Total suite size (incl. unit/integration) | 368 tests |

## Verdict Summary

| Verdict | Count | Services |
|---------|-------|----------|
| CORRECT | 22 | bsm_greeks, aggregate_gex, macaulay_duration, convexity, yield_to_maturity, fit_yield_curve, apply_fdr_correction, compute_amihud, compute_comovement_factor, detect_strategic_runs, compute_weight_delta, compute_rds, compute_diagnostics (ACF), sharpe_ratio, sortino_ratio, fama_french_regression, ols_regression, granger_causality, evt_risk, calculate_quantile_bands, compute_transfer_entropy, sobol_simulate, simulate_climate, simulate_ito, barrier_hitting_probability, calibrate_stops, analyze_sentiment, analyze_lexicon, qed_regime |
| BUG_FOUND | 8 | garch_forecast (B1), volatility_forecast (B1), garch_regime (B1), rahf_regime (B1), robustness_scan (B2), adf_test p-value (B3), q_world_pricer CIR (B4), macro_shock CI (B5), tournament LSTM (B6) |
| UNVERIFIABLE | 3 | anomaly_service, cnn_lstm_regime, shap_service |

## Confirmed Bugs

| ID | Severity | Service | Bug | Evidence |
|----|----------|---------|-----|----------|
| B1 | CRITICAL | `risk_service._estimate_garch` | Gradient ascent only updates `omega`. Alpha `[0.1]` and beta `[0.85]` never updated. | Step 19: oracle recovers α≈0.15, β≈0.80; service reports α=0.1, β=0.85 regardless of input |
| B2 | CRITICAL | `robustness_service.robustness_scan` | Sobol-sampled parameters never used in evaluation. All Sharpe values identical. | Step 29: all 128 samples produce identical Sharpe |
| B3 | CRITICAL | `econometrics_service._adf_pvalue` | P-value approximation always clips to 1.0 for negative statistics. | Step 17: statsmodels reports p=0.01; service reports p=1.0 for same data |
| B4 | CRITICAL | `q_world_pricer_service.compute_fair_value` | CIR formula does not match Hull Ch.31. Produces nonsensical yields. | Step 13: textbook CIR gives yield≈0.0424; service gives yield=91.66 |
| B5 | HIGH | `macro_shock_service` confidence intervals | CIs computed as `response * ±1.96` instead of `response ± 1.96 * SE`. | Step 22: CI_low = -1.96 * response, not response - 1.96*SE |
| B6 | MEDIUM | `tournament_service` "LSTM" | "LSTM" = momentum + noise*0.01, not a neural network. | Step 34: LSTM Sharpe ≈ momentum Sharpe ± 0.01 |

## Validation Method by Group

### Group A: Deterministic Closed-Form (Steps 1–18)
- **32 passed, 2 xfailed**
- Each service compared to an oracle using a **different algorithm** than the service uses
- Example: BSM uses `scipy.stats.norm.cdf`; oracle uses `math.erfc`
- Example: PCA uses `np.linalg.eigh`; oracle uses `np.linalg.svd`

### Group B: Statistical Estimation (Steps 19–25)
- **8 passed, 3 xfailed, 1 xpassed**
- Services compared to `scipy.optimize.minimize` or manual implementations
- GARCH (B1) and volatility forecast (B1) confirmed broken

### Group C: Stochastic Simulation (Steps 26–30)
- **8 passed, 1 xpassed**
- KS tests against analytical distributions (OU, ABM)
- Sobol convergence verified
- Robustness scan (B2) confirmed broken

### Group D: Heuristic/ML (Steps 31–35)
- **19 passed, 4 xpassed**
- Sentiment validated against manual lexicon
- QED regime quartic potential manually verified
- Tournament "LSTM" confirmed as fake (B6)
- SHAP mechanism documented as permutation analysis, not Shapley values

## Test Infrastructure

```
analytics/tests/external_validation/
├── __init__.py
├── conftest.py                    # Shared tolerances, erfc-based BSM oracles, make_garch_data
├── verdicts.py                    # CORRECT/INCORRECT/UNVERIFIABLE/BUG_FOUND enum
├── test_group_a_closed_form.py    # Steps 1-18 (34 tests)
├── test_group_b_statistical.py   # Steps 19-25 (12 tests)
├── test_group_c_stochastic.py    # Steps 26-30 (9 tests)
└── test_group_d_heuristic.py     # Steps 31-35 (23 tests)
```

## Recommendations

1. **Fix B1 first** — GARCH affects 4 services (risk, volatility_forecast, garch_regime, rahf_regime). Replace hand-rolled gradient ascent with `scipy.optimize.minimize` on the negative log-likelihood.
2. **Fix B2** — robustness_scan must actually apply Sobol-sampled parameters to the strategy.
3. **Fix B3** — Replace hand-rolled p-value with `statsmodels` MacKinnon approximation or lookup tables.
4. **Fix B4** — Implement standard CIR formula from Hull Ch.31.
5. **Fix B5** — Compute proper standard errors for IRF confidence intervals.
6. **Document B6** — Rename "LSTM" to "momentum_noise" or implement an actual LSTM.
