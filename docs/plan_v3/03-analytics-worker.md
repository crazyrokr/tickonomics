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
│   │   └── health.py               # Health check endpoint
│   ├── services/
│   │   ├── __init__.py
│   │   ├── openbb_service.py       # OpenBB SDK wrapper
│   │   ├── finance_toolkit_service.py  # FinanceToolkit wrapper
│   │   ├── aic_service.py          # AIC lag selection loop (Finding 2)
│   │   └── validation_service.py   # Cross-validation logic
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
│   └── test_performance.py
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

Note: **NOT available via REST API** — Python SDK only. The analytics worker wraps this
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

---

## Dependencies

```
# requirements.txt
fastapi>=0.115.0
uvicorn>=0.34.0
pydantic>=2.10.0
openbb>=4.7.1
financetoolkit>=2.0.0
pandas>=2.2.0
numpy>=2.0.0
pyarrow>=18.0.0
```

---

## OpenBB SDK Reference

### Version History

| Version | Date       | Change                                                          |
|:--------|:-----------|:----------------------------------------------------------------|
| v4.5.0  | 2025-10-08 | Hub retired. `fixedincome.sofr` → `fixedincome.rate.sofr`.      |
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
- AIC support: **No** — single lag only, no automatic selection.
- Returns: Dict of 4 test variants, each with `{F-test, P-value, Count, Lags}`.
- **AIC loop implemented in `aic_service.py`** (Finding 2): iterates OpenBB causality
  calls for each candidate lag, computes AIC, returns optimal result in a single response.

---

## FinanceToolkit Reference

### Modules Used

**FixedIncome:**

- `fi.get_sofr_rates()` — SOFR, TGCR, BGCR, OBFR, EFFR
- `fi.get_treasury_rates()` — 13W, 5Y, 10Y, 30Y
- `fi.get_bond_duration(type="modified")` — Modified duration
- `fi.get_bond_convexity()` — Convexity
- `fi.get_bond_yield_to_maturity()` — YTM
- `fi.get_bond_dv01()` — Dollar Value of 1bp

**Risk:**

- `risk.get_value_at_risk(method="historical", confidence=0.95)` — VaR
- `risk.get_conditional_value_at_risk(confidence=0.95)` — CVaR
- `risk.get_garch_volatility()` — GARCH(1,1) volatility forecast

**Performance:**

- `perf.get_fama_french_model()` — Fama-French 3/5 factor model
- `perf.get_sharpe_ratio(risk_free_rate=0.05)` — Sharpe ratio
- `perf.get_sortino_ratio(risk_free_rate=0.05)` — Sortino ratio
- `perf.get_treynor_ratio()` — Treynor ratio

License: MIT — fully permissive.

---

## Graceful Degradation

The Java backend (Track 5) must handle analytics worker unavailability:

- TA-Lib covers all **critical path** computations (correlation, beta, z-score).
- ADF/Granger degrade gracefully — log warning, use last known stationarity result,
  fall back to default lag of 3 for Granger.
- Bond math, VaR, Fama-French are **supplementary** — not on critical path.
- Circuit breaker opens after 5 failures, half-opens after 30s.
- Arrow IPC transport failure falls back to REST/JSON automatically.

---

## Validation

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
