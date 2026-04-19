# Triangulation Matrix

> Cross-reference of routers, services, and test files.

## Full Matrix

| # | Router? | Service File | Service Functions | Test File | Status |
|---|:-------:|--------------|-------------------|-----------|--------|
| 1 | ✅ GET `/health` | (inline) | N/A | N/A | Complete (no service) |
| 2 | ✅ `/api/v1/econometrics` | `econometrics/econometrics_service.py` | `adf_test`, `granger_causality`, `ols_regression` | `test_econometrics_service.py` | ✅ Complete |
| 3 | ✅ `/api/v1/fixed-income` | `fixed_income/fixed_income_service.py` | `macaulay_duration`, `convexity`, `yield_to_maturity` | `test_fixed_income_service.py` | ✅ Complete |
| 4 | ✅ `/api/v1/fixed-income` | `fi/q_world_pricer_service.py` | `compute_fair_value`, `compute_tbill_greeks` | `test_q_world_pricer_service.py` | ⚠️ Wired but missing `fi/__init__.py` |
| 5 | ✅ `/api/v1/performance` | `performance/performance_service.py` | `sharpe_ratio`, `sortino_ratio`, `fama_french_regression` | `test_performance_service.py` | ✅ Complete |
| 6 | ✅ `/api/v1/risk` | `risk/risk_service.py` | `value_at_risk`, `conditional_var`, `garch_forecast` | `test_risk_service.py` | ✅ Complete |
| 7 | ✅ `/api/v1/statistical/evt` | `statistical/evt_risk_service.py` | `EvtRiskService.fit_tail_distribution`, `EvtRiskService.simulate_tail_paths` | `test_evt_risk_service.py` | ✅ Complete |
| 8 | ✅ `/api/v1/statistical/fdr` | `statistical/multiple_testing_service.py` | `apply_fdr_correction` | `test_multiple_testing_service.py` | ✅ Complete |
| 9 | ✅ `/api/v1/statistical/yield-curve` | `statistical/yield_curve_service.py` | `nelson_siegel`, `fit_yield_curve`, `interpolate_yield_curve` | `test_yield_curve_service.py` | ✅ Complete |
| 10 | ✅ `/api/v1/statistical/macro` | `statistical/macro_shock_service.py` | `compute_impulse_response` | `test_macro_shock_service.py` | ✅ Complete |
| 11 | ✅ `/api/v1/statistical/qr` | `statistical/quantile_regression_service.py` | `calculate_quantile_bands` | `test_quantile_regression_service.py` | ✅ Complete |
| 12 | ✅ `/api/v1/statistical/entropy` | `statistical/transfer_entropy_service.py` | `compute_transfer_entropy` | `test_transfer_entropy_service.py` | ✅ Complete |
| 13 | ✅ `/api/v1/anomaly` | `anomaly/anomaly_service.py` | `train_autoencoder`, `detect_anomalies` | `test_anomaly_service.py` | ✅ Complete |
| 14 | ✅ `/api/v1/regime` | `regime/regime_service.py` | `garch_regime`, `cnn_lstm_regime`, `qed_regime`, `rahf_regime` | `test_regime_service.py` | ✅ Complete |
| 15 | ✅ `/api/v1/climate` | `climate/climate_service.py` | `simulate_climate` | `test_climate_service.py` | ✅ Complete |
| 16 | ✅ `/api/v1/drift` | `drift/drift_service.py` | `simulate_ito`, `barrier_hitting_probability` | `test_drift_service.py` | ✅ Complete |
| 17 | ✅ `/api/v1/optimizer` | `optimizer/sgd_optimizer_service.py` | `compute_weight_delta` | `test_sgd_optimizer_service.py` | ✅ Complete |
| 18 | ✅ `/api/v1/simulate` | `simulation/sobol_service.py` | `sobol_simulate`, `discrete_correction` | `test_sobol_service.py` | ✅ Complete |
| 19 | ✅ `/api/v1/greeks` | `greeks/bsm_service.py` | `bsm_greeks`, `aggregate_gex` | `test_bsm_service.py` | ✅ Complete |
| 20 | ✅ `/api/v1/liquidity` | `liquidity/amihud_service.py` | `compute_amihud` | `test_liquidity_services.py` | ✅ Complete |
| 21 | ✅ `/api/v1/liquidity` | `liquidity/comovement_pca_service.py` | `compute_comovement_factor` | `test_liquidity_services.py` | ✅ Complete |
| 22 | ✅ `/api/v1/liquidity` | `liquidity/strategic_runs_service.py` | `detect_strategic_runs` | `test_liquidity_services.py` | ✅ Complete |
| 23 | ✅ `/api/v1/backtest` | `backtest/robustness_service.py` | `robustness_scan` | `test_robustness_service.py` | ✅ Complete |
| 24 | ✅ `/api/v1/reproducibility` | `reproducibility/rds_scorer_service.py` | `compute_rds` | `test_rds_scorer_service.py` | ✅ Complete |
| 25 | ✅ `/api/v1/sentiment` | `sentiment/sentiment_service.py` | `analyze_sentiment`, `analyze_lexicon` | `test_sentiment_service.py` | ✅ Complete |
| 26 | ✅ `/api/v1/stops` | `stops/markov_stop_service.py` | `calibrate_stops` | `test_stops_service.py` | ✅ Complete |
| 27 | ✅ `/api/v1/diagnostics` | `diagnostics/diagnostic_service.py` | `compute_diagnostics` | `test_diagnostics_service.py` | ✅ Complete |
| 28 | ✅ `/api/v1/analytics` | `ml/volatility_forecast_service.py` | `forecast_volatility` | `test_volatility_forecast_service.py` | ✅ Complete |
| 29 | ✅ `/api/v1/tournament` | `benchmark/tournament_service.py` | `evaluate_tournament` | `test_tournament_service.py` | ✅ Complete |
| 30 | ✅ `/api/v1/explainability` | `explainability/shap_service.py` | `compute_feature_importance` | `test_explainability_service.py` | ✅ Complete |

## Structural Gaps

| Gap ID | Type | Details |
|--------|------|---------|
| G1 | Missing `__init__.py` | `app/services/fi/__init__.py` does not exist, but service is importable and wired via `fixed_income` router |

## Summary

- **Router entries:** 27 (26 with `include_router` + health)
- **Service files:** 29 (across 23 directories)
- **Public functions:** 48
- **Test files:** 27
- **Structural gaps:** 1 (missing `__init__.py` for `fi/` package — no functional impact)
- **Orphaned services:** 0 (plan's assumption about `q_world_pricer_service.py` was incorrect — it is wired into `fixed_income` router)
