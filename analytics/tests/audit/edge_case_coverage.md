# Edge-Case Coverage Matrix

> Assessment of each test file across five edge-case categories.

| Test File | Insufficient Data | Boundary Values | Invalid Input | False-Positive Guard | Math Invariant |
|-----------|:--:|:--:|:--:|:--:|:--:|
| test_anomaly_service.py | ✅ | ❌ | ✅ | ✅ | ❌ |
| test_bsm_service.py | ❌ | ❌ | ✅ | ❌ | ✅ |
| test_climate_service.py | ❌ | ❌ | ❌ | ❌ | ✅ |
| test_diagnostics_service.py | ✅ | ❌ | ❌ | ❌ | ✅ |
| test_drift_service.py | ❌ | ❌ | ❌ | ❌ | ✅ |
| test_econometrics_service.py | ✅ | ❌ | ✅ | ❌ | ✅ |
| test_evt_risk_service.py | ✅ | ❌ | ❌ | ❌ | ❌ |
| test_explainability_service.py | ❌ | ❌ | ✅ | ❌ | ❌ |
| test_fixed_income_service.py | ✅ | ❌ | ✅ | ❌ | ✅ |
| test_liquidity_services.py | ✅ | ❌ | ✅ | ❌ | ✅ |
| test_macro_shock_service.py | ✅ | ❌ | ❌ | ❌ | ❌ |
| test_multiple_testing_service.py | ✅ | ❌ | ❌ | ❌ | ❌ |
| test_performance_service.py | ✅ | ❌ | ❌ | ❌ | ❌ |
| test_quantile_regression_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |
| test_q_world_pricer_service.py | ❌ | ❌ | ✅ | ❌ | ❌ |
| test_rds_scorer_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |
| test_regime_service.py | ✅ | ❌ | ❌ | ❌ | ✅ |
| test_risk_service.py | ✅ | ❌ | ❌ | ❌ | ✅ |
| test_robustness_service.py | ✅ | ❌ | ✅ | ❌ | ❌ |
| test_sentiment_service.py | ✅ | ❌ | ✅ | ❌ | ✅ |
| test_sgd_optimizer_service.py | ❌ | ❌ | ✅ | ✅ | ✅ |
| test_sobol_service.py | ❌ | ❌ | ✅ | ❌ | ❌ |
| test_stops_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |
| test_tournament_service.py | ✅ | ❌ | ❌ | ❌ | ❌ |
| test_transfer_entropy_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |
| test_volatility_forecast_service.py | ✅ | ❌ | ✅ | ❌ | ❌ |
| test_yield_curve_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |

## Category Counts

| Category | Present | Absent |
|----------|:-------:|:------:|
| Insufficient Data | 16 | 11 |
| Boundary Values | 0 | 27 |
| Invalid Input | 11 | 16 |
| False-Positive Guard | 2 | 25 |
| Math Invariant | 13 | 14 |

## Summary

- **No file covers all 5 categories**
- **Boundary values** are completely missing across all 27 files
- **False-positive guards** are present in only 2 files (anomaly, sgd_optimizer)
- 11 files have 3+ missing categories — secondary remediation targets
