# ADR-001: v4/v5 Analytics Worker Services

**Date:** 2026-05-30
**Status:** Implemented

## Context

Track 3 (Python Analytics Worker) had core v1 services done (econometrics, risk, fixed income, performance, statistical) but all v4/v5 advanced analytics services were pending. The computation engine (Track 5) depends on several endpoints for adaptive ILI calculation, regime detection, and Greeks computation.

## Decision

Implemented 18 service groups across v4 and v5 as independent FastAPI routers under `/api/v1/`.

### v4 Services (5 groups)
1. **Anomaly Detection** (`/api/v1/anomaly`) — PyTorch autoencoder with train/detect.
2. **Regime Detection** (`/api/v1/regime`) — GARCH, CNN-LSTM, QED quartic, RAHF harmonic.
3. **Climate Model** (`/api/v1/climate`) — Euler-Maruyama SDE with mean-reversion + seasonal forcing.
4. **Drift-Diffusion** (`/api/v1/drift`) — Arithmetic BM + barrier hitting probability.
5. **Online Optimizer** (`/api/v1/optimizer`) — Constrained SGD weight-delta.

### v5 Services (13 groups)
6. **Sobol MC** (`/api/v1/simulate`) — Quasi-random MC option pricing + discrete correction.
7. **BSM Greeks / GEX** (`/api/v1/greeks`) — Closed-form Greeks + gamma exposure aggregation.
8. **Liquidity** (`/api/v1/liquidity`) — Comovement PCA, Amihud, strategic run detection.
9. **Backtest Robustness** (`/api/v1/backtest`) — Sobol parameter sweep.
10. **Reproducibility** (`/api/v1/reproducibility`) — RDS 0-2 rubric scoring.
11. **Sentiment** (`/api/v1/sentiment`) — Lexicon-based with negation/intensifier support.
12. **Markov Stops** (`/api/v1/stops`) — Stop-loss/take-profit calibration.
13. **Diagnostics** (`/api/v1/diagnostics`) — QQ-plot, ACF, convergence analysis.
14. **Volatility Forecast** (`/api/v1/analytics/volatility-forecast`) — Multi-horizon GARCH forecast.
15. **Benchmark Tournament** (`/api/v1/tournament`) — Multi-model signal comparison.
16. **SHAP Explainability** (`/api/v1/explainability`) — Feature importance via permutation attribution.
17. **Q-World Bond Pricer** (`/api/v1/fixed-income/q-world-fair-value`) — CIR model fair-value.
18. **T-Bill Greeks** (`/api/v1/fixed-income/tbill-greeks`) — Analytical DV01/convexity.

## Dependencies Added

- `torch>=2.2.0` for autoencoder and CNN-LSTM regime model

## Test Results

151 total tests passing (95 new + 56 existing). 27 test files covering all services.
