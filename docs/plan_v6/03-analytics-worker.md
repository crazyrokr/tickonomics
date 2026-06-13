# Track 3: Python Analytics Worker

**Phase:** Phase 0
**Can start:** Immediately (fully independent, different language ecosystem)
**Blocks:** Track 5 (computation engine uses analytics worker for ADF/Granger)
**Depends on:** Nothing (can start in parallel with Track 1)

---

## Objective

Implement a Python FastAPI analytics worker that provides statistical functions
not available in TA-Lib Java. This runs as a separate process alongside the Java
backend, communicating primarily via **Apache Arrow IPC** for high-throughput
time-series transfer, with REST/JSON fallback for small payloads.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Python Analytics Worker (FastAPI)                  │
│  Port: configurable (default 8001)                  │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  Transport Layer                          │      │
│  │  - Arrow IPC endpoint (primary)           │      │
│  │  - REST/JSON fallback (small payloads)    │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  OpenBB SDK Endpoints                     │      │
│  │  - ADF unit root test                     │      │
│  │  - Granger causality with AIC lag loop    │      │
│  │  - OLS regression (full stats)            │      │
│  │  - Sharpe/Sortino ratio                   │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  FinanceToolkit Endpoints                 │      │
│  │  - Bond duration/convexity/YTM            │      │
│  │  - VaR, CVaR, GARCH                      │      │
│  │  - Fama-French factor analysis            │      │
│  │  - Sharpe/Sortino validation              │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v4: Anomaly Detection (Proposal 01)      │      │
│  │  - PyTorch autoencoder                    │      │
│  │  - ILI component anomaly detection        │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v4: Regime Detection (Proposal 03)       │      │
│  │  - GARCH regime classification            │      │
│  │  - CNN-LSTM hybrid ensemble               │      │
│  │  - QED quartic potential model            │      │
│  │  - RAHF GARCH+NN framework (Proposal 04)  │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v4: Online Optimizer (Proposal 03)       │      │
│  │  - SGD weight delta computation           │      │
│  │  - Self-tuning ILI component weights      │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v4: Climate Model (Proposal 03)          │      │
│  │  - Multi-country trade simulation         │      │
│  │  - Energy shift dynamics                  │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v4: Drift-Diffusion (Proposal 01)        │      │
│  │  - Ito process simulation                 │      │
│  │  - Continuous-time drift monitoring       │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v5: Sobol Simulation (Proposal 05)       │      │
│  │  - Sobol quasi-random Monte Carlo         │      │
│  │  - Riemann Zeta discrete correction       │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v5: BSM Greeks / GEX (Proposal 06)       │      │
│  │  - Black-Scholes-Merton gamma calculation │      │
│  │  - Aggregate GEX service                  │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  v5: Liquidity Analytics (Proposal 07)    │      │
│  │  - Liquidity comovement PCA               │      │
│  │  - Amihud measure service                 │      │
│  │  - Strategic run transition probabilities │      │
│  └───────────────────────────────────────────┘      │
│                                                     │
│  Accepts: Arrow IPC (primary) or JSON payloads      │
│  Returns: Structured JSON results                   │
└─────────────────────────────────────────────────────┘
```

**Analysis Finding 2 (AIC Loop in Worker):** The AIC lag selection loop has been moved
entirely into this worker. Java sends data once with a `max_lag` parameter, and the worker
iterates over candidate lags internally using OpenBB SDK + statsmodels, returning the
optimal lag and full causality result in a single call. This eliminates the N*10 REST call
explosion.

---

## Directory Structure

```
analytics/
├── app/
│   ├── __init__.py
│   ├── main.py                     # FastAPI application
│   ├── transport/
│   │   ├── __init__.py
│   │   └── arrow_ipc.py            # Arrow IPC receive/send helpers (Finding 2)
│   ├── routers/
│   │   ├── __init__.py
│   │   ├── econometrics.py         # ADF, Granger (with AIC loop), OLS
│   │   ├── risk.py                 # VaR, CVaR, GARCH
│   │   ├── fixed_income.py         # Bond math
│   │   ├── performance.py          # Fama-French, Sharpe/Sortino
│   │   ├── health.py               # Health check endpoint
│   │   ├── anomaly.py              # v4: Anomaly detection endpoints
│   │   ├── regime.py               # v4: Regime detection endpoints (GARCH, CNN-LSTM, QED, RAHF)
│   │   ├── climate.py              # v4: Climate simulation endpoints
│   │   ├── drift.py                # v4: Drift-diffusion endpoints
│   │   ├── sobol.py                    # v5: Sobol simulation endpoints
│   │   ├── gex.py                      # v5: BSM Greeks and GEX endpoints
│   │   ├── liquidity.py                # v5: Liquidity analysis endpoints
│   │   ├── strategic.py                # v5: Strategic run analysis
│   │   └── backtest_robustness.py      # v5: Robustness scan endpoints
│   ├── services/
│   │   ├── __init__.py
│   │   ├── openbb_service.py       # OpenBB SDK wrapper
│   │   ├── finance_toolkit_service.py  # FinanceToolkit wrapper
│   │   ├── aic_service.py          # AIC lag selection loop (Finding 2)
│   │   ├── validation_service.py   # Cross-validation logic
│   │   ├── anomaly/
│   │   │   ├── __init__.py
│   │   │   └── autoencoder.py      # v4: PyTorch autoencoder (Proposal 01)
│   │   ├── regime/
│   │   │   ├── __init__.py
│   │   │   ├── garch_service.py    # v4: GARCH regime detection (Proposal 03)
│   │   │   ├── cnn_lstm_regime.py  # v4: CNN-LSTM ensemble (Proposal 03)
│   │   │   ├── qed_service.py      # v4: QED model (Proposal 03)
│   │   │   └── rahf_service.py     # v4: RAHF hybrid (Proposal 04)
│   │   ├── climate/
│   │   │   ├── __init__.py
│   │   │   └── climate_model.py    # v4: Climate-liquidity model (Proposal 03)
│   │   ├── diffusion/
│   │   │   ├── __init__.py
│   │   │   └── drift_diffusion_service.py  # v4: Ito process simulation (Proposal 01)
│   │   └── risk/
│   │       ├── __init__.py
│   │       └── online_optimizer.py  # v4: SGD weight optimizer (Proposal 03)
│   │   ├── sobol/
│   │   │   ├── __init__.py
│   │   │   └── sobol_simulation.py     # v5: Sobol quasi-random MC
│   │   ├── gex/
│   │   │   ├── __init__.py
│   │   │   ├── bsm_greeks.py           # v5: Black-Scholes-Merton Greeks
│   │   │   └── gex_aggregation.py      # v5: Aggregate GEX calculation
│   │   ├── liquidity/
│   │   │   ├── __init__.py
│   │   │   ├── comovement.py           # v5: PCA-based comovement factor
│   │   │   ├── amihud_measure.py       # v5: High-frequency price impact
│   │   │   └── strategic_runs.py       # v5: Strategic run detection
│   │   └── reproducibility/
│   │       ├── __init__.py
│   │       └── rds_scorer.py           # v5: Reproducibility Disclosure Score
│   ├── models/
│   │   ├── __init__.py
│   │   ├── requests.py             # Pydantic request models
│   │   └── responses.py            # Pydantic response models
│   └── config.py                   # Settings from env vars
├── tests/
│   ├── __init__.py
│   ├── test_econometrics.py
│   ├── test_aic_service.py         # AIC lag selection tests (Finding 2)
│   ├── test_arrow_transport.py     # Arrow IPC serialization tests
│   ├── test_risk.py
│   ├── test_fixed_income.py
│   ├── test_performance.py
│   ├── test_autoencoder.py         # v4: Autoencoder anomaly tests
│   ├── test_garch_service.py       # v4: GARCH regime tests
│   ├── test_cnn_lstm_regime.py     # v4: CNN-LSTM regime tests
│   ├── test_qed_service.py         # v4: QED model tests
│   ├── test_online_optimizer.py    # v4: Online optimizer tests
│   ├── test_climate_model.py       # v4: Climate model tests
│   ├── test_drift_diffusion.py     # v4: Drift-diffusion tests
│   ├── test_rahf_service.py        # v4: RAHF framework tests
│   ├── test_sobol_simulation.py        # v5
│   ├── test_bsm_greeks.py              # v5
│   ├── test_gex_aggregation.py         # v5
│   ├── test_comovement.py              # v5
│   ├── test_amihud_measure.py          # v5
│   ├── test_strategic_runs.py          # v5
│   ├── test_rds_scorer.py              # v5
│   └── test_backtest_robustness.py     # v5
├── requirements.txt
├── Dockerfile
└── README.md
```

---

## API Endpoints

### Econometrics (OpenBB SDK)

#### `POST /api/v1/econometrics/unit-root`

ADF unit root test for stationarity.

Request:

```json
{
  "data": [{"date": "2024-01-01", "value": 1.23}, ...],
  "column": "value",
  "regression": "c"
}
```

Response:

```json
{
  "adfstat": -3.45,
  "pvalue": 0.01,
  "usedlag": 2,
  "nobs": 250,
  "icbest": -1.23,
  "stationary": true
}
```

OpenBB SDK call: `obb.econometrics.unit_root(data, column, regression="c")`

#### `POST /api/v1/econometrics/causality`

Granger causality test with **AIC-based automatic lag selection** (Finding 2).

The AIC loop runs entirely in this worker. Java sends data once with `max_lag`, and
the worker iterates over candidate lags internally.

Request:

```json
{
  "data": [{"date": "2024-01-01", "x": 1.23, "y": 4.56}, ...],
  "y_column": "y",
  "x_column": "x",
  "max_lag": 10
}
```

Response:

```json
{
  "optimal_lag": 3,
  "aic_values": {"1": 120.5, "2": 115.3, "3": 108.7, "4": 110.2, "...": "..."},
  "test_result": {
    "ssr_ftest": {"f_test": 2.34, "p_value": 0.05, "count": 250, "lags": 3},
    "ssr_chi2test": {"f_test": 7.02, "p_value": 0.05, "count": 250, "lags": 3},
    "lrtest": {"f_test": 6.89, "p_value": 0.05, "count": 250, "lags": 3},
    "params_ftest": {"f_test": 2.34, "p_value": 0.05, "count": 250, "lags": 3}
  }
}
```

Internal implementation (`aic_service.py`):

1. For each candidate lag (1 to `max_lag`):
    - Call `obb.econometrics.causality(data, y_column, x_column, lag=candidate)`.
    - Extract residual sum of squares from the result.
    - Compute AIC = `n * ln(RSS/n) + 2 * k` where `k = 2 * lag + 1`.
2. Select lag minimizing AIC.
3. Return full test result at optimal lag plus all AIC values for audit.

#### `POST /api/v1/econometrics/ols`

Full OLS regression with statistics.

Request:

```json
{
  "data": [{"date": "2024-01-01", "y": 1.23, "x1": 4.56, "x2": 7.89}, ...],
  "y_column": "y",
  "x_columns": ["x1", "x2"]
}
```

Response:

```json
{
  "coefficients": {"x1": 0.5, "x2": -0.3},
  "r_squared": 0.75,
  "adjusted_r_squared": 0.74,
  "std_error": 0.12,
  "f_statistic": 45.6,
  "p_values": {"x1": 0.001, "x2": 0.05},
  "residuals_sum_sq": 12.3
}
```

OpenBB SDK call: `obb.econometrics.ols_regression(data, y_column, x_columns)`

Note: **NOT available via REST API** -- Python SDK only. The analytics worker wraps this
as a REST endpoint for the Java backend.

### Risk Metrics (FinanceToolkit)

#### `POST /api/v1/risk/value-at-risk`

Request:

```json
{
  "returns": [0.01, -0.02, 0.03, ...],
  "method": "historical",
  "confidence": 0.95
}
```

Response:

```json
{
  "var": -0.025,
  "method": "historical",
  "confidence": 0.95
}
```

#### `POST /api/v1/risk/conditional-var`

Request:

```json
{
  "returns": [0.01, -0.02, 0.03, ...],
  "confidence": 0.95
}
```

Response:

```json
{
  "cvar": -0.035,
  "confidence": 0.95
}
```

#### `POST /api/v1/risk/garch-volatility`

Request:

```json
{
  "returns": [0.01, -0.02, 0.03, ...]
}
```

Response:

```json
{
  "conditional_volatility": [0.015, 0.018, ...],
  "forecast_1d": 0.02
}
```

### Fixed Income (FinanceToolkit)

#### `POST /api/v1/fixed-income/bond-analytics`

Request:

```json
{
  "bond_prices": [98.5, 99.0, ...],
  "coupon_rate": 0.05,
  "face_value": 1000,
  "maturity_years": 10,
  "settlement_date": "2026-01-15"
}
```

Response:

```json
{
  "modified_duration": 7.5,
  "convexity": 75.3,
  "yield_to_maturity": 0.052,
  "dv01": 0.075
}
```

### Performance (FinanceToolkit + OpenBB)

#### `POST /api/v1/performance/fama-french`

Factor data is now auto-ingested via `FrenchFactorClient` into the `factor_returns` hypertable (v6 Phase 4). The 5-factor model (Rm-Rf, SMB, HML, RMW, CMA) is supported.

Request:

```json
{
  "portfolio_returns": [0.01, -0.02, ...],
  "start_date": "2024-01-01"
}
```

Response:

```json
{
  "alpha": 0.003,
  "beta_market": 1.1,
  "beta_smb": -0.2,
  "beta_hml": 0.15,
  "r_squared": 0.85,
  "factors": ["MKT", "SMB", "HML"]
}
```

#### `POST /api/v1/performance/sharpe-ratio`

Request:

```json
{
  "returns": [0.01, -0.02, ...],
  "risk_free_rate": 0.05
}
```

Response:

```json
{
  "sharpe_ratio": 1.25,
  "annualized_return": 0.12,
  "annualized_volatility": 0.08
}
```

### Health Check

#### `GET /health`

Response:

```json
{
  "status": "UP",
  "openbb_sdk_loaded": true,
  "finance_toolkit_loaded": true
}
```

### Anomaly Detection (v4 -- Proposal 01: Data Quality)

#### `POST /api/v1/anomaly/detect`

Detect anomalies in ILI component vectors using a trained PyTorch autoencoder.

Request:

```json
{
  "data_vector": [0.045, 0.012, 0.033, 0.008, 0.021],
  "threshold_multiplier": 3.0
}
```

Response:

```json
{
  "mse": 0.023,
  "threshold": 0.015,
  "is_anomaly": true,
  "is_suspect_anomaly": true
}
```

Implementation (`services/anomaly/autoencoder.py`):

- Trained autoencoder encodes the input vector to a latent representation and reconstructs it.
- Reconstruction MSE is computed against the original input.
- Anomaly flagged when MSE exceeds `threshold_multiplier * baseline_threshold`.
- `is_suspect_anomaly` is true when MSE is between 1.0x and `threshold_multiplier`x of the baseline.

#### `POST /api/v1/anomaly/train`

Train (or retrain) the autoencoder model on historical ILI component vectors.

Request:

```json
{
  "data": [[0.045, 0.012, 0.033], [0.048, 0.015, 0.030], ...],
  "epochs": 100,
  "latent_dim": 3
}
```

Response:

```json
{
  "training_mse": 0.005,
  "threshold": 0.015,
  "model_version": "autoencoder_v1"
}
```

Implementation:

- Autoencoder architecture: input layer (dim = number of ILI components) -> encoder -> latent
  (dim = `latent_dim`) -> decoder -> output layer.
- Trained on historical clean ILI component vectors using MSE loss.
- Threshold computed as mean training MSE + N standard deviations (configurable).
- Model weights stored for subsequent detection calls.

### Regime Detection (v4 -- Proposal 03: Regime Detection / Proposal 04: Weight Optimization)

#### `GET /api/v1/risk/garch-regime` (Proposal 03 -- PRIMARY)

Classify the current volatility regime using GARCH(1,1) conditional variance.

Response:

```json
{
  "regime": "NORMAL",
  "conditional_volatility": 0.018,
  "forecast_1d": 0.02,
  "percentile": 0.55
}
```

Implementation (`services/regime/garch_service.py`):

- Uses the `arch` library to fit a GARCH(1,1) model to historical return series.
- Conditional volatility is compared against historical percentile distribution.
- Regime classification:
    - `LOW_VOL`: conditional volatility < 25th percentile.
    - `NORMAL`: conditional volatility between 25th and 75th percentile.
    - `HIGH_VOL`: conditional volatility > 75th percentile.
- Forecast is a 1-step-ahead conditional volatility prediction.

#### `POST /api/v1/risk/weight-delta` (Proposal 03: Online Optimizer)

Compute weight deltas for ILI component weights using SGD-based online optimization.

Request:

```json
{
  "current_weights": {"rrp": 0.4, "spread": 0.4, "vol": 0.2},
  "signal_performance": [
    {"date": "2026-05-16", "rrp_signal": 0.85, "spread_signal": 0.72, "vol_signal": 0.60},
    ...
  ],
  "lookback_days": 5
}
```

Response:

```json
{
  "weight_deltas": {"rrp": 0.02, "spread": -0.01, "vol": -0.01},
  "learning_rate": 0.01
}
```

Implementation (`services/risk/online_optimizer.py`):

- SGD optimizer computes gradients from recent signal performance over `lookback_days`.
- Weight deltas are bounded to prevent large jumps in a single update.
- Learning rate is configurable, defaulting to 0.01.
- Output deltas are intended to be applied incrementally: `new_weight = current_weight + delta`.

#### `GET /api/v1/regime/hybrid` (Proposal 03 -- Alternative A)

CNN-LSTM hybrid ensemble for regime detection with confidence scoring.

Response:

```json
{
  "regime": "HIGH_VOL",
  "confidence": 0.87,
  "transition_probability": {
    "LOW_VOL": 0.05,
    "NORMAL": 0.15,
    "HIGH_VOL": 0.80
  }
}
```

Implementation (`services/regime/cnn_lstm_regime.py`):

- Input features: raw ILI component series (RRP, Spread, Vol).
- CNN layers extract spatial patterns from the input window.
- LSTM layers capture temporal dependencies across the sequence.
- Ensemble output provides regime classification with confidence and transition probabilities.
- Intended for A/B testing against GARCH regime engine.

#### `POST /api/v1/regime/qed` (Proposal 03 -- Alternative B)

Quartic potential model for metastability and crash probability estimation.

Request:

```json
{
  "capital_flow_proxies": {
    "repo_flows": [-2.3, -1.8, -0.5],
    "tbill_demand": [1.2, 0.8, 0.3],
    "sofr_volume": [15.2, 14.8, 13.5]
  },
  "current_state": {
    "potential_value": 0.85,
    "velocity": -0.12
  }
}
```

Response:

```json
{
  "regime": "METASTABLE",
  "crash_probability": 0.12,
  "barrier_distance": 1.5
}
```

Implementation (`services/regime/qed_service.py`):

- Models market state as a particle in a quartic potential well.
- Ito process simulation estimates barrier hitting-time probabilities.
- Capital flow proxies drive the potential shape and drift term.
- `METASTABLE` regime indicates the system is near a potential barrier but has not crossed it.
- Crash probability increases as `barrier_distance` decreases toward zero.

#### `POST /api/v1/regime/rahf` (Proposal 04 -- Alternative B)

RAHF (Regime-Aware Hybrid Framework) combining GARCH and neural network for regime classification.

Response:

```json
{
  "regime": "HIGH_VOL",
  "garch_component": {
    "regime": "HIGH_VOL",
    "conditional_volatility": 0.025,
    "percentile": 0.82
  },
  "nn_component": {
    "regime": "HIGH_VOL",
    "confidence": 0.91,
    "latent_features": [0.12, -0.34, 0.56]
  },
  "msfe": 0.02
}
```

Implementation (`services/regime/rahf_service.py`):

- GARCH component provides conditional volatility-based regime classification.
- Neural network component learns latent regime features from historical data.
- Combined via weighted ensemble; MSFE (Mean Squared Forecast Error) tracks hybrid accuracy.
- Target: MSFE improvement over pure GARCH must be statistically significant (p < 0.05).

### Climate Model (v4 -- Proposal 03: Complementary)

#### `POST /api/v1/climate/simulate`

Multi-country trade model simulation for energy shift dynamics and liquidity impact.

Request:

```json
{
  "adaptation_finance_increase": 0.1,
  "carbon_tax": 50
}
```

Response:

```json
{
  "projected_liquidity_impact": 0.03,
  "energy_mix_shift": {
    "fossil_reduction": 0.08,
    "renewable_increase": 0.06,
    "nuclear_stable": 0.02
  }
}
```

Implementation (`services/climate/climate_model.py`):

- Multi-country trade model simulates capital reallocation under climate policy scenarios.
- `adaptation_finance_increase` drives incremental green capital flows.
- `carbon_tax` modifies energy production cost structure.
- Projected liquidity impact estimates the effect on repo/funding market liquidity.
- Energy mix shift breakdown shows sector-level capital reallocation.

### Drift-Diffusion (v4 -- Proposal 01: Complementary)

#### `POST /api/v1/drift/simulate`

Ito process simulation for continuous-time drift monitoring and barrier hitting-time estimation.

Request:

```json
{
  "drift_params": {
    "mu": 0.001,
    "sigma": 0.02,
    "initial_value": 0.5
  },
  "barrier_level": 2.0,
  "time_horizon": "30d"
}
```

Response:

```json
{
  "hitting_probability": 0.15,
  "expected_time": "12d",
  "paths": [
    [0.5, 0.52, 0.48, 0.55, ...],
    [0.5, 0.49, 0.51, 0.53, ...]
  ]
}
```

Implementation (`services/diffusion/drift_diffusion_service.py`):

- Simulates Ito process: `dX = mu * dt + sigma * dW` via Euler-Maruyama discretization.
- Monte Carlo paths estimate the probability of hitting `barrier_level` within `time_horizon`.
- Expected hitting time computed from paths that cross the barrier.
- Path ensemble returned for visualization and downstream analysis.
- Complements autoencoder point-in-time anomaly detection with continuous drift monitoring.

### Sobol Simulation (v5 -- Proposal 05)

#### `POST /api/v1/simulate/sobol`

Sobol quasi-random Monte Carlo simulation for ILI stress testing.

Request:

```json
{
  "base_values": [0.045, 0.012, 0.033],
  "n_simulations": 1000,
  "perturbation_std": 0.01
}
```

Response:

```json
{
  "paths": [[0.044, 0.013, 0.032], ...],
  "extreme_cases": {"worst_ili": -2.5, "best_ili": 1.8},
  "coverage_metric": 0.95
}
```

Implementation (`services/sobol/sobol_simulation.py`):

- Uses `scipy.stats.qmc.Sobol` for quasi-random sequence generation.
- Perturbs historical ILI component paths using Sobol-generated noise.
- Better state-space coverage than standard pseudo-random MC.
- `coverage_metric` reports what fraction of the N-dimensional hypercube is covered.

#### `POST /api/v1/simulate/discrete-correction`

Broadie-Kou-Glasserman discrete monitoring correction using Riemann Zeta function.

Response:

```json
{
  "beta": 0.5826,
  "corrected_threshold": 1.0523,
  "original_threshold": 1.0,
  "sampling_interval": "1d"
}
```

### BSM Greeks / GEX (v5 -- Proposal 06)

#### `POST /api/v1/greeks/bsm`

Black-Scholes-Merton Greeks calculation for options chain data.

Request:

```json
{
  "spot_price": 450.0,
  "strike_price": 455.0,
  "time_to_expiry": 0.0833,
  "risk_free_rate": 0.05,
  "implied_volatility": 0.18,
  "option_type": "call"
}
```

Response:

```json
{
  "gamma": 0.042,
  "delta": 0.45,
  "theta": -0.03,
  "vega": 0.15,
  "rho": 0.02
}
```

Implementation (`services/gex/bsm_greeks.py`):

- Closed-form BSM formulas for Greeks calculation.
- `scipy.stats.norm` for cumulative distribution function.
- Applied to every strike in the options chain dataset.

#### `GET /api/v1/gex/aggregate`

Aggregate GEX (Gamma Exposure) across the options chain.

Response:

```json
{
  "symbol": "SPY",
  "net_gex": -125000000.0,
  "gamma_flip_price": 452.30,
  "total_call_gamma": 250000000.0,
  "total_put_gamma": -375000000.0
}
```

Implementation (`services/gex/gex_aggregation.py`):

- Summarizes net dollar-gamma exposure across the entire chain.
- Computes gamma flip price level (positive-to-negative gamma transition).
- Uses `scipy.sparse.linalg` for eigenvalue problems if spectral methods applied.

### Liquidity Analytics (v5 -- Proposal 07)

#### `GET /api/v1/analytics/comovement-factor`

Liquidity comovement factor via PCA on standardized spreads.

Response:

```json
{
  "comovement_factor": 0.72,
  "pc1_variance_explained": 0.55,
  "pc2_variance_explained": 0.17,
  "n_symbols": 10,
  "window_minutes": 5
}
```

Implementation (`services/liquidity/comovement.py`):

- PCA on 5-minute bucketed, first-differenced, standardized spreads.
- Factor = percentage of variance explained by first two principal components.
- Uses `sklearn.decomposition.PCA`.

#### `POST /api/v1/analytics/amihud`

High-frequency price impact metric (Amihud measure).

Request:

```json
{
  "returns": [0.01, -0.02, 0.03],
  "dollar_volumes": [5000000, 4500000, 6000000]
}
```

Response:

```json
{
  "amihud_measure": 0.0000033,
  "normalized_measure": 0.45
}
```

#### `GET /api/v1/econometrics/strategic-runs`

Strategic run transition probabilities between passive/aggressive states.

Response:

```json
{
  "transition_matrix": {
    "passive_to_passive": 0.7,
    "passive_to_aggressive": 0.3,
    "aggressive_to_passive": 0.4,
    "aggressive_to_aggressive": 0.6
  },
  "current_state": "passive",
  "run_count_24h": 3
}
```

### Backtesting Robustness (v5 -- Proposal 05)

#### `POST /api/v1/backtest/robustness-scan`

Batch execution of parameter permutations using Sobol sequences.

Request:

```json
{
  "parameter_space": {
    "rrp_weight": [0.2, 0.6],
    "spread_weight": [0.2, 0.6],
    "vol_weight": [0.1, 0.3],
    "buy_percentile": [2, 10],
    "sell_percentile": [90, 98],
    "cooldown_minutes": [60, 360]
  },
  "n_permutations": 100
}
```

Response:

```json
{
  "results": [
    {"params": {"rrp_weight": 0.35, ...}, "sharpe": 1.2, "max_drawdown": 0.15, ...},
    ...
  ],
  "heatmap_data": {...},
  "best_params": {"rrp_weight": 0.38, ...},
  "coverage": 0.92
}
```

### Reproducibility (v5 -- Proposal 05)

#### `POST /api/v1/reproducibility/score`

Compute Reproducibility Disclosure Score (RDS) for a model.

Request:

```json
{
  "model_name": "garch_regime_v2",
  "has_code": true,
  "code_versioned": true,
  "dataset_available": true,
  "hyperparams_documented": true,
  "results_reproducible": true
}
```

Response:

```json
{
  "rds_score": 2,
  "breakdown": {
    "code_availability": 1,
    "data_availability": 1,
    "reproducibility": 1
  }
}
```

Implementation (`services/reproducibility/rds_scorer.py`):

- 0-2 rubric: 0 = no disclosure, 1 = partial, 2 = full.
- Three dimensions: code availability, data availability, reproducibility.

### Sentiment Service -- FinBERT (v5 -- Proposal 09 PRIMARY)

**Endpoint:** `POST /api/v1/sentiment/analyze`

Request:

```json
{
  "texts": ["FOMC minutes indicate hawkish outlook", "Fed holds rates steady"],
  "model": "finbert",
  "include_certainty": true
}
```

Response:

```json
{
  "results": [
    {
      "text": "FOMC minutes indicate hawkish outlook",
      "score": -0.72,
      "label": "negative",
      "certainty": 0.91
    }
  ]
}
```

Implementation (`services/sentiment/finbert_service.py`):

- Model: `ProsusAI/finbert` from Hugging Face.
- Async news processing queue to avoid blocking Arrow IPC requests.
- Analyze FOMC minutes and Fed speakers for qualitative Z-score.

### Sentiment Service -- Lexicon SALI (v5 -- Proposal 09 ALTERNATIVE)

**Endpoint:** `POST /api/v1/sentiment/lexicon`

Request:

```json
{
  "texts": ["Markets rally on strong employment data"],
  "sources": ["title", "summary"]
}
```

Response:

```json
{
  "results": [
    {
      "text": "Markets rally on strong employment data",
      "polarity": 0.65,
      "subjectivity": 0.4,
      "source": "title"
    }
  ]
}
```

Implementation (`services/sentiment/lexicon_sentiment_service.py`):

- Dependencies: TextBlob, AFINN.
- Multi-source NLP: analyze titles, summaries, and text separately.
- Output: normalized scores in [-1, 1] range.

### Markov Stop Engine (v5 -- Proposal 08: Optimal Stops)

**Endpoint:** `POST /api/v1/stops/calibrate`

Request:

```json
{
  "trade_pnl_series": [0.5, -0.2, 1.1, 0.3, -0.8],
  "max_iterations": 1000
}
```

Response:

```json
{
  "optimal_stop_loss": -0.045,
  "optimal_take_profit": 0.078,
  "signal_drift": 0.012,
  "decay_intensity": 0.34,
  "converged": true,
  "iterations": 423
}
```

Implementation (`services/stops/markov_stop_engine.py`):

- Calibrates A1/A2 integral transforms from SSRN-2381830.
- Estimates signal drift (mu_1) and decay intensity (q).
- Outputs optimal stop-loss and take-profit thresholds.

### Diagnostic Service (v5 -- Proposal 11: Statistical Integrity)

**Endpoint:** `GET /api/v1/diagnostics/stats`

Response:

```json
{
  "qq_plot": {
    "sample_quantiles": [-2.1, -1.5, -0.8, 0.0, 0.9, 1.6, 2.3],
    "theoretical_quantiles": [-2.0, -1.5, -0.9, 0.0, 0.9, 1.5, 2.0]
  },
  "acf": {
    "raw_returns": [1.0, 0.02, -0.01, 0.03, -0.02],
    "absolute_returns": [1.0, 0.35, 0.28, 0.22, 0.18]
  },
  "convergence": {
    "cumulative_mean": [0.1, 0.05, 0.03, 0.02, 0.01],
    "cumulative_variance": [0.5, 0.3, 0.25, 0.22, 0.21]
  }
}
```

Implementation (`services/diagnostics/diagnostic_service.py`):

- QQ-plot: sample quantiles vs. Normal quantiles for ILI distribution.
- ACF: lag-N autocorrelations for raw and absolute returns.
- Convergence: cumulative mean/variance series for LLN verification.

### Volatility Forecaster (v5 -- Proposal 10: LSTM Volatility)

**Endpoint:** `POST /api/v1/analytics/volatility-forecast`

Request:

```json
{
  "symbol": "SPY",
  "horizon_days": 5,
  "model": "lstm"
}
```

Response:

```json
{
  "forecast": [0.18, 0.19, 0.21, 0.20, 0.19],
  "model": "lstm",
  "mae_vs_baseline": -0.003
}
```

Implementation (`services/ml/volatility_forecaster.py`):

- BiLSTM for short-term volatility regime prediction.
- Supplements GARCH regime detector as pluggable alternative.
- A/B testing candidate against GARCH forecasts.

### Benchmark Tournament Service (v5 -- Proposal 10: Multi-Model Benchmark)

**Endpoint:** `POST /api/v1/tournament/evaluate`

Request:

```json
{
  "symbols": ["SPY", "QQQ"],
  "start_date": "2024-01-01",
  "end_date": "2025-01-01"
}
```

Response:

```json
{
  "results": {
    "ili_rule_engine": {"sharpe": 0.72, "hit_rate": 0.58},
    "xgboost": {"sharpe": 0.65, "hit_rate": 0.55},
    "lstm": {"sharpe": 0.68, "hit_rate": 0.56}
  },
  "regime_breakdown": {
    "uptrend": {"best": "ili_rule_engine"},
    "sideways": {"best": "lstm"},
    "downtrend": {"best": "xgboost"}
  }
}
```

Implementation (`services/benchmark/tournament_service.py`):

- Runs ILI signal alongside XGBoost and LSTM predictions for identical timestamps.
- Regime-specific benchmarking: uptrend, sideways, downtrend.
- Dependencies: `xgboost`, `tensorflow` (or `keras`).

### Model Explainability Service (v5 -- Proposal 10: SHAP)

**Endpoint:** `POST /api/v1/explainability/feature-importance`

Request:

```json
{
  "model": "ili_signal",
  "features": {"rrp_zscore": -1.2, "spread_zscore": 0.8, "vol_zscore": 1.5}
}
```

Response:

```json
{
  "shap_values": {"rrp_zscore": 0.35, "spread_zscore": -0.12, "vol_zscore": 0.53},
  "top_drivers": ["vol_zscore", "rrp_zscore"]
}
```

Implementation (`services/explainability/model_explainability.py`):

- SHAP (SHapley Additive exPlanations) for feature attribution.
- Identifies which ILI component most influenced each signal.
- Dependency: `shap`.

### Q-World Bond Pricer (v5 -- Proposal 12: Q-World Integration)

**Endpoint:** `POST /api/v1/fixed-income/q-world-fair-value`

Request:

```json
{
  "instrument": "3M_TBILL",
  "current_yield": 4.25,
  "lookback_days": 252
}
```

Response:

```json
{
  "fair_value_yield": 4.18,
  "residual": 0.07,
  "residual_std": 0.03,
  "dislocated": false,
  "model": "CIR"
}
```

Implementation (`services/fixed_income/q_world_pricer.py`):

- CIR (Cox, Ingersoll, Ross) model for fair-value T-Bill yield.
- Residual (observed - fair) exceeding threshold validates DISLOCATED status.
- Independent mathematical validation of ProxyDivergenceGuard.

### T-Bill Analytical Greeks (v5 -- Proposal 12: Spectral Greeks)

**Endpoint:** `GET /api/v1/fixed-income/tbill-greeks`

Response:

```json
{
  "dv01": 0.0025,
  "convexity": 0.0001,
  "duration": 0.248,
  "rate_delta": -0.0025,
  "rate_gamma": 0.0001
}
```

Implementation (`services/fixed_income/tbill_greeks.py`):

- Analytical DV01 and convexity for 3-month T-Bill proxy.
- Feeds into MarketSensitivityLibrary and IntradayProxyService.
- No finite-difference approximation; closed-form solutions.

---

## Dependencies

```
# requirements.txt

# Core (v1)
fastapi>=0.115.0
uvicorn>=0.34.0
pydantic>=2.10.0
openbb>=4.7.1
financetoolkit>=2.0.0
pandas>=2.2.0
numpy>=2.0.0
pyarrow>=18.0.0

# v4 additions
torch>=2.4.0
arch>=7.0.0
scikit-learn>=1.5.0

# v5 additions
scipy>=1.14.0
scikit-learn>=1.5.0    # already present, ensure version

# v5 additions (Proposal 09)
transformers>=4.45.0
torch>=2.4.0           # already present, ensure version
textblob>=0.18.0

# v5 additions (Proposals 10, 11, 12)
xgboost>=2.1.0
shap>=0.46.0
```

---

## OpenBB SDK Reference

Note (v6): The Python analytics worker continues to use OpenBB SDK internally for econometric functions. The Java-side OpenBB sidecar has been removed; the Java ingestion layer now fetches data directly from free sources (Yahoo Finance, Finnhub, Alpha Vantage, DataHub) and writes to TimescaleDB, where the Python worker reads it.

### Version History

| Version | Date       | Change                                                          |
|:--------|:-----------|:----------------------------------------------------------------|
| v4.5.0  | 2025-10-08 | Hub retired. `fixedincome.sofr` -> `fixedincome.rate.sofr`.     |
| v4.6.0  | 2026-01-07 | Python 3.9 dropped. Account module removed.                     |
| v4.7.0  | 2026-03-09 | **Polygon provider removed.** Python 3.14 + Pandas 3.0 support. |
| v4.7.1  | 2026-03-09 | Latest stable.                                                  |

### OpenBB SDK Functions Used

| Function                          | Purpose                                      |
|:----------------------------------|:---------------------------------------------|
| `obb.econometrics.unit_root`      | ADF stationarity test                        |
| `obb.econometrics.causality`      | Granger causality (single lag only)          |
| `obb.econometrics.ols_regression` | Full OLS with statistics (SDK only, no REST) |
| `obb.quantitative.sharpe_ratio`   | Sharpe ratio                                 |
| `obb.quantitative.sortino_ratio`  | Sortino ratio                                |

### OpenBB SDK Causality Details

- Parameters: `data`, `y_column`, `x_column`, `lag: PositiveInt = 3`
- AIC support: **No** -- single lag only, no automatic selection.
- Returns: Dict of 4 test variants, each with `{F-test, P-value, Count, Lags}`.
- **AIC loop implemented in `aic_service.py`** (Finding 2): iterates OpenBB causality
  calls for each candidate lag, computes AIC, returns optimal result in a single response.

---

## FinanceToolkit Reference

### Modules Used

**FixedIncome:**

- `fi.get_sofr_rates()` -- SOFR, TGCR, BGCR, OBFR, EFFR
- `fi.get_treasury_rates()` -- 13W, 5Y, 10Y, 30Y
- `fi.get_bond_duration(type="modified")` -- Modified duration
- `fi.get_bond_convexity()` -- Convexity
- `fi.get_bond_yield_to_maturity()` -- YTM
- `fi.get_bond_dv01()` -- Dollar Value of 1bp

**Risk:**

- `risk.get_value_at_risk(method="historical", confidence=0.95)` -- VaR
- `risk.get_conditional_value_at_risk(confidence=0.95)` -- CVaR
- `risk.get_garch_volatility()` -- GARCH(1,1) volatility forecast

**Performance:**

- `perf.get_fama_french_model()` -- Fama-French 3/5 factor model
- `perf.get_sharpe_ratio(risk_free_rate=0.05)` -- Sharpe ratio
- `perf.get_sortino_ratio(risk_free_rate=0.05)` -- Sortino ratio
- `perf.get_treynor_ratio()` -- Treynor ratio

License: MIT -- fully permissive.

---

## v4 New Dependencies Reference

### PyTorch (`torch>=2.4.0`)

Used by:

- `services/anomaly/autoencoder.py` -- Autoencoder model for ILI component anomaly detection.
- `services/regime/cnn_lstm_regime.py` -- CNN-LSTM hybrid for regime classification.
- `services/regime/rahf_service.py` -- Neural network component of RAHF framework.

### ARCH (`arch>=7.0.0`)

Used by:

- `services/regime/garch_service.py` -- GARCH(1,1) fitting and conditional volatility forecasting.
- `services/regime/rahf_service.py` -- GARCH component of RAHF hybrid framework.

### scikit-learn (`scikit-learn>=1.5.0`)

Used by:

- `services/anomaly/autoencoder.py` -- Data preprocessing (StandardScaler, train/test split).
- `services/regime/cnn_lstm_regime.py` -- Feature engineering and evaluation metrics.

---

## Graceful Degradation

The Java backend (Track 5) must handle analytics worker unavailability:

- TA-Lib covers all **critical path** computations (correlation, beta, z-score).
- ADF/Granger degrade gracefully -- log warning, use last known stationarity result,
  fall back to default lag of 3 for Granger.
- Bond math, VaR, Fama-French are **supplementary** -- not on critical path.
- Circuit breaker opens after 5 failures, half-opens after 30s.
- Arrow IPC transport failure falls back to REST/JSON automatically.

### v4 Graceful Degradation Additions

- Autoencoder anomaly detection: if model weights are unavailable or detection fails,
  fall back to fixed-threshold z-score anomaly detection (v3 behavior).
- GARCH regime classification: if `arch` library fails to converge, fall back to
  static volatility thresholds.
- CNN-LSTM and QED regime detectors: **pluggable alternatives** -- failure falls back
  to GARCH regime engine (PRIMARY). No critical path dependency.
- Online optimizer: if SGD computation fails, weights remain unchanged (zero delta).
- Climate model: **supplementary** -- failure does not affect ILI computation.
- Drift-diffusion: **supplementary** -- failure does not affect critical path. Last known
  drift estimate retained.
- RAHF framework: if neural network component fails, GARCH-only classification is returned.

### v5 Graceful Degradation Additions

- Sobol simulation: if `scipy.stats.qmc` unavailable, falls back to standard pseudo-random Monte Carlo.
- BSM Greeks: if options data unavailable, GEX service returns null with warning. GEX-weighted regime detection falls
  back to standard regime detection.
- Comovement monitor: if PCA fails (insufficient symbols), returns comovement factor of 0.0 with degradation warning.
- Amihud measure: if dollar volume data unavailable, returns null. Computation engine uses default cost model.
- Strategic run analysis: if tick data insufficient, returns empty transition matrix.

---

## Validation

### v1/v2/v3 Validation (existing)

- [ ] All endpoints return correct Pydantic-typed responses.
- [ ] ADF test matches statsmodels `adfuller()` results.
- [ ] Granger causality with AIC lag selection returns optimal lag and full test results (Finding 2).
- [ ] AIC loop selects the lag minimizing AIC across all candidates.
- [ ] OLS regression matches statsmodels `OLS.fit()` results.
- [ ] VaR/CVaR validated against known distributions.
- [ ] GARCH volatility forecast is finite and positive.
- [ ] Fama-French factor loadings reasonable for test portfolios.
- [ ] Arrow IPC transport correctly serializes/deserializes time-series arrays (Finding 2).
- [ ] REST/JSON fallback works when Arrow IPC is unavailable.
- [ ] Health check returns correct status.
- [ ] OpenBB SDK loads and initializes correctly.
- [ ] FinanceToolkit initializes without errors.
- [ ] All tests pass with `pytest`.

### v4 Validation Additions

- [ ] Autoencoder detects corrupted samples (interchanged values) with > 95% accuracy.
- [ ] GARCH regime classification matches known volatility regimes.
- [ ] CNN-LSTM regime transitions detected faster than GARCH during rapid shifts.
- [ ] QED crash probability spikes during historical SOFR/T-Bill dislocation periods.
- [ ] Online optimizer produces weight deltas within +/- 5% per update.
- [ ] Climate model simulation produces measurable liquidity impact projections.
- [ ] Drift-diffusion barrier hitting-time results are statistically consistent.
- [ ] RAHF MSFE improvement over pure GARCH is statistically significant (p < 0.05).

### v5 Validation Additions

- [ ] Sobol simulation produces better hypercube coverage than pseudo-random MC (coverage > 0.9 for 1000 samples in 3D).
- [ ] Riemann Zeta beta constant equals 0.5826 within floating-point tolerance.
- [ ] Discrete monitoring correction adjusts thresholds correctly for 1-minute and 1-day sampling intervals.
- [ ] BSM Greeks match analytical values within tolerance for known option parameters.
- [ ] GEX aggregation correctly computes net gamma exposure from options chain data.
- [ ] Gamma flip price correctly identifies positive-to-negative gamma transition.
- [ ] Liquidity comovement factor is in [0, 1] range.
- [ ] Amihud measure is finite and positive for valid input data.
- [ ] Strategic run transition probabilities sum to 1.0 for each state.
- [ ] Robustness scan completes 100 parameter permutations and returns heatmap data.
- [ ] RDS scorer produces correct 0-2 score for each disclosure dimension.
- [ ] Reproducibility metadata stored with all required fields.

### v5 Validation Additions

- [ ] FinBERT correctly identifies contextual financial sentiment (hawkish vs dovish) in test corpus.
- [ ] FinBERT certainty scores correlate with signal accuracy (high certainty = higher hit rate).
- [ ] SALI lexicon scores normalized to [-1, 1] range.
- [ ] Async news processing queue does not block critical-path Arrow IPC requests.
- [ ] MarkovStopEngine calibration converges within 1000 iterations on historical trade data.

### v5.2 Validation Additions (Proposal 13 & 14: Quantitative Engine)

- [ ] EVT service fits GPD parameters correctly to simulated tail data.
- [ ] FDR service correctly adjusts p-value vectors via Benjamini-Hochberg.
- [ ] QR service computes conditional 5th/95th percentile bands accurately.
- [ ] SVAR/TVP-SVAR service computes impulse response functions with time-varying sensitivities.
- [ ] Transfer Entropy service quantifies synergy between sentiment and returns.
- [ ] Black-Scholes Greeks service matches reference values for test options.
- [ ] Nelson-Siegel interpolation service produces smooth yield curves.
- [ ] Cointegration service correctly identifies pairs with p < 0.05.

---

## Changelog

| Version | File                     | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|:--------|:-------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | `03-analytics-worker.md` | Initial plan: econometrics endpoints (ADF, Granger, OLS), risk metrics (VaR, CVaR, GARCH), fixed income, performance, health check. Arrow IPC transport. OpenBB SDK and FinanceToolkit references.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v2      | `03-analytics-worker.md` | External integration references (FINOS CDM, Perspective). No structural changes to analytics worker endpoints.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| v3      | `03-analytics-worker.md` | Improvement proposals: pre-computed KPI references, CDM adapter notes. Directory structure refined. Validation checklist extended.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v4      | `03-analytics-worker.md` | Major additions from consolidated proposals: (1) Autoencoder Anomaly Service with `/anomaly/detect` and `/anomaly/train` endpoints (Proposal 01). (2) GARCH Regime Service with `/risk/garch-regime` endpoint (Proposal 03 PRIMARY). (3) Online Optimizer Service with `/risk/weight-delta` endpoint (Proposal 03). (4) CNN-LSTM Regime Detector with `/regime/hybrid` endpoint (Proposal 03 Alternative A). (5) QED Service with `/regime/qed` endpoint (Proposal 03 Alternative B). (6) Climate Model Service with `/climate/simulate` endpoint (Proposal 03). (7) Drift-Diffusion Service with `/drift/simulate` endpoint (Proposal 01). (8) RAHF Framework with `/regime/rahf` endpoint (Proposal 04 Alternative B). New dependencies: torch, arch, scikit-learn. New directory structure: `services/anomaly/`, `services/regime/`, `services/climate/`, `services/diffusion/`, `services/risk/`. New routers: `anomaly.py`, `regime.py`, `climate.py`, `drift.py`. 8 new test files. Graceful degradation fallbacks for all v4 services. 8 new validation criteria. |
| v5      | `03-analytics-worker.md` | Added from Proposals 05, 06, 07: Sobol simulation service (`/simulate/sobol`, `/simulate/discrete-correction`), BSM Greeks service (`/greeks/bsm`, `/gex/aggregate`), Liquidity analytics (`/analytics/comovement-factor`, `/analytics/amihud`, `/econometrics/strategic-runs`), Backtesting robustness (`/backtest/robustness-scan`), Reproducibility scoring (`/reproducibility/score`). New routers: `sobol.py`, `gex.py`, `liquidity.py`, `strategic.py`, `backtest_robustness.py`. New services: `sobol/`, `gex/`, `liquidity/`, `reproducibility/`. New dependency: scipy. Graceful degradation fallbacks for all v5 services. 12 new validation criteria.                                                                                                                                                                                                                                                                                                                                                                                                         |
| v5      | `03-analytics-worker.md` | Added from Proposals 08, 09: FinBERT Sentiment Service (`/sentiment/analyze`), Lexicon SALI Service (`/sentiment/lexicon`), Markov Stop Engine (`/stops/calibrate`). New routers: `sentiment.py`, `stops.py`. New services: `sentiment/`, `stops/`. New dependencies: transformers, textblob. 5 new validation criteria.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| v5.1    | `03-analytics-worker.md` | Added from Proposals 10, 11, 12: Diagnostic Service (`/diagnostics/stats`), Volatility Forecaster (`/analytics/volatility-forecast`), Benchmark Tournament (`/tournament/evaluate`), SHAP Explainability (`/explainability/feature-importance`), Q-World Bond Pricer (`/fixed-income/q-world-fair-value`), T-Bill Analytical Greeks (`/fixed-income/tbill-greeks`). New routers: `diagnostics.py`, `tournament.py`, `explainability.py`, `fixed_income.py`. New services: `diagnostics/`, `ml/`, `benchmark/`, `explainability/`, `fixed_income/`. New dependencies: xgboost, shap. 7 new validation criteria.                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| v5.2    | `03-analytics-worker.md` | Added Proposal 13 & 14 (Quantitative Engine): Advanced statistical rigor (EVT, FDR, QR, SVAR) integrated with 151 strategies (Nelson-Siegel, Greeks fallback, cointegration).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| v6      | `03-analytics-worker.md` | Added note that Java-side OpenBB sidecar removed. Factor data auto-ingested from Ken French Data Library via `FrenchFactorClient` into `factor_returns` hypertable. No changes to Python worker internals.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
