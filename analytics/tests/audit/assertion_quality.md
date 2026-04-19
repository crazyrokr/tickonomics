# Assertion Quality Audit

> Classification of every assert statement in all 27 test files using the T0–T4 tier framework.

## Tier Definitions

| Tier | Label | Definition |
|------|-------|------------|
| T0 | Smoke | Key presence, status codes, non-negativity, type checks |
| T1 | Structural | Output shape, length, dimension, key count |
| T2 | Relative | Comparing two outputs from same function under different inputs |
| T3 | Invariant | Mathematical property that must always hold |
| T4 | Known-answer | Comparison to independently computed expected value |

## Per-File Results

| Test File | Total | T0 | T1 | T2 | T3 | T4 | Highest | T2+% |
|-----------|------:|---:|---:|---:|---:|---:|:-------:|-----:|
| test_anomaly_service.py | 24 | 8 | 7 | 0 | 0 | 9 | T4 | 38% |
| test_bsm_service.py | 24 | 9 | 5 | 0 | 1 | 9 | T4 | 42% |
| test_climate_service.py | 16 | 7 | 5 | 0 | 1 | 3 | T4 | 25% |
| test_diagnostics_service.py | 12 | 5 | 4 | 0 | 1 | 2 | T4 | 25% |
| test_drift_service.py | 13 | 4 | 2 | 2 | 2 | 3 | T4 | 54% |
| test_econometrics_service.py | 27 | 7 | 9 | 0 | 1 | 10 | T4 | 41% |
| test_evt_risk_service.py | 12 | 5 | 2 | 1 | 0 | 4 | T4 | 42% |
| test_explainability_service.py | 7 | 1 | 3 | 0 | 0 | 3 | T4 | 43% |
| test_fixed_income_service.py | 17 | 4 | 3 | 2 | 3 | 5 | T4 | 59% |
| test_liquidity_services.py | 18 | 6 | 4 | 2 | 1 | 5 | T4 | 44% |
| test_macro_shock_service.py | 9 | 2 | 5 | 0 | 0 | 2 | T4 | 22% |
| test_multiple_testing_service.py | 11 | 3 | 4 | 0 | 0 | 4 | T4 | 36% |
| test_performance_service.py | 24 | 8 | 6 | 0 | 0 | 10 | T4 | 42% |
| test_quantile_regression_service.py | 8 | 2 | 4 | 0 | 0 | 2 | T4 | 25% |
| test_q_world_pricer_service.py | 16 | 4 | 2 | 1 | 0 | 9 | T4 | 63% |
| test_rds_scorer_service.py | 15 | 1 | 5 | 0 | 0 | 9 | T4 | 60% |
| test_regime_service.py | 27 | 9 | 7 | 0 | 2 | 9 | T4 | 41% |
| test_risk_service.py | 22 | 8 | 5 | 0 | 2 | 7 | T4 | 41% |
| test_robustness_service.py | 11 | 3 | 4 | 1 | 0 | 3 | T4 | 36% |
| test_sentiment_service.py | 18 | 5 | 3 | 0 | 2 | 8 | T4 | 56% |
| test_sgd_optimizer_service.py | 17 | 3 | 2 | 1 | 2 | 9 | T4 | 71% |
| test_sobol_service.py | 17 | 5 | 3 | 1 | 0 | 8 | T4 | 53% |
| test_stops_service.py | 9 | 4 | 0 | 0 | 0 | 5 | T4 | 56% |
| test_tournament_service.py | 9 | 3 | 5 | 0 | 0 | 1 | T4 | 11% |
| test_transfer_entropy_service.py | 7 | 3 | 0 | 0 | 0 | 4 | T4 | 57% |
| test_volatility_forecast_service.py | 8 | 4 | 2 | 0 | 0 | 2 | T4 | 25% |
| test_yield_curve_service.py | 8 | 2 | 3 | 0 | 0 | 3 | T4 | 38% |

## Summary

- **Total asserts across all files:** 408
- **T2+ (relative/invariant/known-answer):** 167 (41%)
- **All files reach at least T4** — no file is critically weak
- **Lowest T2+%:** test_tournament_service.py (11%), test_macro_shock_service.py (22%)

## GWT Compliance (Step 1.7)

| File | # Given | # Then | Code-Level GWT? |
|------|--------:|-------:|:---------------:|
| test_evt_risk_service.py | >0 | >0 | ✅ Yes |
| test_macro_shock_service.py | >0 | >0 | ✅ Yes |
| test_multiple_testing_service.py | >0 | >0 | ✅ Yes |
| test_quantile_regression_service.py | >0 | >0 | ✅ Yes |
| test_transfer_entropy_service.py | >0 | >0 | ✅ Yes |
| test_yield_curve_service.py | >0 | >0 | ✅ Yes |
| All other 21 files | 0 | 0 | ❌ No |

**Compliant:** 6/27 files
**Non-compliant:** 21/27 files
