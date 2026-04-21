# ADR-001: v4/v5 Analytics Worker Services

**Date:** 2026-05-29
**Status:** Implemented

## Context

Track 3 (Python Analytics Worker) had core v1 services done (econometrics, risk, fixed income, performance, statistical) but all v4/v5 advanced analytics services were pending. The computation engine (Track 5) depends on several endpoints for adaptive ILI calculation, regime detection, and Greeks computation.

## Decision

Implemented 10 service groups across v4 and v5 as independent FastAPI routers under `/api/v1/`:

### v4 Services
1. **Anomaly Detection** (`/api/v1/anomaly`) — PyTorch autoencoder with train/detect endpoints.
2. **Regime Detection** (`/api/v1/regime`) — GARCH regime, CNN-LSTM hybrid, QED quartic potential, RAHF harmonic ensemble.
3. **Climate Model** (`/api/v1/climate`) — Euler-Maruyama SDE with mean-reversion + seasonal forcing.
4. **Drift-Diffusion** (`/api/v1/drift`) — Arithmetic Brownian motion + barrier hitting probability.
5. **Online Optimizer** (`/api/v1/optimizer`) — Constrained SGD weight-delta for ILI components.

### v5 Services
6. **Sobol Monte Carlo** (`/api/v1/simulate`) — Quasi-random MC option pricing + discrete monitoring correction.
7. **BSM Greeks / GEX** (`/api/v1/greeks`) — Closed-form option Greeks + gamma exposure aggregation.
8. **Liquidity Analytics** (`/api/v1/liquidity`) — Comovement PCA, Amihud illiquidity, strategic run detection.
9. **Backtest Robustness** (`/api/v1/backtest`) — Sobol-sampled parameter sweep with Sharpe/drawdown metrics.
10. **Reproducibility Scoring** (`/api/v1/reproducibility`) — RDS rubric scoring across code/data/reproducibility dimensions.

## Structure

Each service follows the existing pattern: flat functions in `services/<module>/<service>.py`, Pydantic request models inline in routers, pytest tests with Given-When-Then docstrings.

## Dependencies Added

- `torch>=2.2.0` for autoencoder and CNN-LSTM regime model

## Test Results

123 total tests passing (67 new + 56 existing).
