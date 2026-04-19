# Gap Prioritization

> Ranked remediation list combining assertion quality, edge-case coverage, and business criticality.

## Methodology

- **Primary targets:** Files where `T2+ < 30%` (relatively weak assertion depth)
- **Secondary targets:** Files with 3+ missing edge-case categories
- **Tertiary targets:** Non-GWT-compliant files
- **Priority score:** `criticality × (5 - edge_gaps)` where edge_gaps = number of ❌ in edge-case matrix

## Business Criticality

| Domain | Criticality | Rationale |
|--------|:-----------:|-----------|
| risk (VaR, CVaR, GARCH) | 5 | Direct capital-at-risk computation |
| greeks (BSM) | 5 | Options pricing — financial accuracy mandatory |
| fixed_income (duration, convexity, YTM) | 5 | Bond pricing — regulatory accuracy |
| statistical/evt_risk | 5 | Tail risk — regulatory capital |
| statistical/multiple_testing (FDR) | 4 | Research integrity — false discovery control |
| statistical/yield_curve | 4 | Curve fitting — pricing input |
| liquidity (Amihud, PCA, runs) | 4 | Market microstructure — execution quality |
| performance (Sharpe, Sortino, FF) | 4 | Strategy evaluation — investment decisions |
| simulation (Sobol) | 3 | Monte Carlo — numerical approximation |
| optimizer (SGD) | 3 | Portfolio construction — weight allocation |
| statistical/quantile_regression | 3 | Risk modeling |
| statistical/transfer_entropy | 3 | Information flow analysis |
| statistical/macro_shock | 3 | Stress testing |
| regime (GARCH, CNN-LSTM, QED, RAHF) | 3 | Market state detection |
| stops (Markov) | 2 | Trade execution |
| backtest (robustness) | 2 | Strategy validation |
| econometrics (ADF, Granger, OLS) | 2 | Research tooling |
| anomaly (autoencoder) | 2 | Detection tooling |
| sentiment | 2 | Signal input |
| climate | 1 | Research simulation |
| drift (Ito) | 1 | Research simulation |
| reproducibility (RDS) | 1 | Scoring heuristic |
| diagnostics | 1 | Summary statistics |
| explainability (SHAP) | 1 | Feature importance |
| benchmark (tournament) | 1 | Strategy comparison |
| volatility_forecast | 2 | ML-based forecasting |
| fi/q_world_pricer | 3 | Fixed income pricing |

## Ranked Gap List

| Rank | Test File | Criticality | T2+% | Edge Gaps | Priority | Remediation |
|------|-----------|:-----------:|-----:|:---------:|:--------:|-------------|
| 1 | test_yield_curve_service.py | 4 | 38% | 5 | 20 | Add T3 invariant + T4 known-answer tests |
| 2 | test_quantile_regression_service.py | 3 | 25% | 5 | 15 | Add T3 monotonicity + boundary tests |
| 3 | test_climate_service.py | 1 | 25% | 4 | 4 | Add boundary + invalid input tests |
| 4 | test_diagnostics_service.py | 1 | 25% | 3 | 3 | Add boundary + false-positive tests |
| 5 | test_volatility_forecast_service.py | 2 | 25% | 3 | 6 | Add boundary + invariant tests |
| 6 | test_tournament_service.py | 1 | 11% | 4 | 4 | Add T3 ranking invariant + more T4 |
| 7 | test_macro_shock_service.py | 3 | 22% | 3 | 9 | Add T4 known-answer VAR test |
| 8 | test_stops_service.py | 2 | 56% | 5 | 10 | Add boundary + false-positive tests |
| 9 | test_transfer_entropy_service.py | 3 | 57% | 5 | 15 | Add boundary + false-positive tests |
| 10 | test_rds_scorer_service.py | 1 | 60% | 5 | 5 | Add boundary + invalid input tests |
| 11 | test_bsm_service.py | 5 | 42% | 3 | 15 | Add boundary + false-positive tests |
| 12 | test_explainability_service.py | 1 | 43% | 3 | 3 | Add boundary + insufficient data tests |
| 13 | test_anomaly_service.py | 2 | 38% | 2 | 4 | Add boundary + math invariant tests |
| 14 | test_fixed_income_service.py | 5 | 59% | 2 | 10 | Add boundary + false-positive tests |
| 15 | test_liquidity_services.py | 4 | 44% | 2 | 8 | Add boundary + false-positive tests |
| 16 | test_risk_service.py | 5 | 41% | 2 | 10 | Add boundary + false-positive tests |
| 17 | test_econometrics_service.py | 2 | 41% | 2 | 4 | Add boundary + false-positive tests |
| 18 | test_sobol_service.py | 3 | 53% | 3 | 9 | Add boundary + false-positive tests |
| 19 | test_regime_service.py | 3 | 41% | 2 | 6 | Add boundary + false-positive tests |
| 20 | test_performance_service.py | 4 | 42% | 3 | 12 | Add boundary + false-positive tests |
| 21 | test_sentiment_service.py | 2 | 56% | 2 | 4 | Add boundary + false-positive tests |
| 22 | test_robustness_service.py | 2 | 36% | 2 | 4 | Add boundary + false-positive tests |
| 23 | test_q_world_pricer_service.py | 3 | 63% | 3 | 9 | Add boundary + false-positive tests |
| 24 | test_drift_service.py | 1 | 54% | 3 | 3 | Add boundary + invalid input tests |
| 25 | test_multiple_testing_service.py | 4 | 36% | 3 | 12 | Add boundary + false-positive tests |
| 26 | test_sgd_optimizer_service.py | 3 | 71% | 2 | 6 | Add boundary + invalid input tests |
| 27 | test_evt_risk_service.py | 5 | 42% | 3 | 15 | Add boundary + false-positive tests |

## GWT Compliance Gaps (21 files)

All files except these 6 need GWT remediation:
- ✅ test_evt_risk_service.py
- ✅ test_macro_shock_service.py
- ✅ test_multiple_testing_service.py
- ✅ test_quantile_regression_service.py
- ✅ test_transfer_entropy_service.py
- ✅ test_yield_curve_service.py
