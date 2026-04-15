# Project Plan: Funding Market Monitor (High-Performance Edition) — v5

## Objective

Create a high-performance application using Java 25 and Spring Boot 3.4+ to monitor funding
market metrics and correlate them with equity price movements via a sophisticated KPI system.

The system supports two interchangeable concurrency models:

- **Dual-Mode Architecture:** Virtual Threads + Spring MVC (blocking) **and** WebFlux (reactive),
  selectable at startup via a configuration flag.

### v5 Change Summary

This version introduces **OpenBB Platform** (v4.7.1+) as a mandatory external data gateway.
OpenBB runs as a sidecar process managed by the user — it is **not** a deliverable of this
project. The application consumes OpenBB's REST API for standardized data access across FRED,
Federal Reserve (NY Fed SOFR/TGCR/BGCR), and equity price providers, eliminating the need for
per-source ingestion clients for polled data sources.

**Key changes from v4:**

| Area                       | v4                                               | v5                                                                                                |
|:---------------------------|:-------------------------------------------------|:--------------------------------------------------------------------------------------------------|
| FRED ingestion             | Custom `FredClient`                              | `OpenBBIngestionClient` → OpenBB `obb.economy.*` (provider=`fred`)                                |
| NY Fed ingestion           | Custom `NyFedClient`                             | `OpenBBIngestionClient` → OpenBB `obb.fixedincome.rate.sofr` etc. (provider=`federal_reserve`)    |
| Equity aggregates          | Custom `PolygonClient` (REST)                    | `OpenBBIngestionClient` → OpenBB `obb.equity.price.historical` (provider=`fmp` or `intrinio`)     |
| T-Bill proxy               | Custom `IntradayProxyClient`                     | `OpenBBIngestionClient` → OpenBB `obb.fixedincome.government.treasury_rates` (field `month_3`)    |
| Polygon real-time ticks    | Custom `PolygonClient` (WS)                      | **Unchanged** — direct WebSocket still required (OpenBB is request-response only)                 |
| ADF stationarity test      | Custom `AdfTest`                                 | `OpenBBAnalyticsClient` → OpenBB `obb.econometrics.unit_root` or `obb.quantitative.unitroot_test` |
| OLS regression (Beta KPI)  | Custom implementation                            | `OpenBBAnalyticsClient` → OpenBB `obb.econometrics.ols_regression` (Python SDK only)              |
| Granger causality + AIC    | Custom `GrangerCausalityTest` + `AicLagSelector` | **Unchanged** — OpenBB `causality` lacks AIC lag selection                                        |
| Sharpe ratio (backtesting) | Custom implementation                            | `OpenBBAnalyticsClient` → OpenBB `obb.quantitative.sharpe_ratio` / `sortino_ratio`                |
| Per-source auth handling   | Per-client API key management                    | OpenBB manages all credentials centrally                                                          |
| Per-source schema handling | Per-client response parsing                      | OpenBB standardizes via Pydantic v2 models                                                        |

---

## System Architecture

### 1. Tech Stack

| Layer              | Technology                                      | Purpose                                                                 |
|:-------------------|:------------------------------------------------|:------------------------------------------------------------------------|
| **Language**       | Java 25 (LTS)                                   | Virtual Threads, Structured Concurrency, Scoped Values                  |
| **Framework**      | Spring Boot 3.4+                                | Auto-configuration, dependency injection, actuator                      |
| **Concurrency**    | Mode A: Virtual Threads + Spring MVC (blocking) | Simple blocking I/O with cheap threads                                  |
| **Concurrency**    | Mode B: WebFlux + Netty (reactive)              | Event-loop, Flux/Mono, backpressure-aware                               |
| **Data Gateway**   | OpenBB Platform 4.7.1+ (sidecar)                | Standardized access to FRED, Fed Reserve, equity prices, Treasury rates |
| **Frontend**       | Next.js 15, TailwindCSS, TypeScript             | Dashboard UI                                                            |
| **Charts**         | Lightweight Charts (TradingView)                | Price/KPI time-series panes                                             |
| **Charts**         | D3.js                                           | Liquidity heatmaps, correlation matrices                                |
| **Time-Series DB** | QuestDB                                         | Ingestion of ticks, rates, computed KPIs                                |
| **Relational DB**  | PostgreSQL                                      | Configuration, alert rules, signal history (append-only)                |
| **Resilience**     | Chronicle Queue                                 | Disk-backed overflow for high-volume ingestion                          |
| **Real-Time Feed** | Polygon.io (direct WebSocket)                   | Sub-second tick data — not routed through OpenBB                        |

#### OpenBB Sidecar Architecture

OpenBB Platform is a **user-managed prerequisite**, not a project deliverable. The user starts
the OpenBB FastAPI server before launching the Spring Boot application. The application
communicates with OpenBB exclusively via HTTP REST.

```
                    ┌──────────────────────────────────────┐
                    │       User-Managed Prerequisites     │
                    │                                      │
                    │  ┌────────────────────────────────┐  │
                    │  │   OpenBB Platform (sidecar)     │  │
                    │  │   FastAPI + Uvicorn             │  │
                    │  │   Port: configurable (def 8000) │  │
                    │  │                                  │  │
                    │  │   Installed extensions:          │  │
                    │  │   - openbb-fred                  │  │
                    │  │   - openbb-federal_reserve       │  │
                    │  │   - openbb-fmp                   │  │
                    │  │   - openbb-econometrics          │  │
                    │  │   - openbb-quantitative          │  │
                    │  │                                  │  │
                    │  │   Credentials (user config):     │  │
                    │  │   - FRED API key                 │  │
                    │  │   - FMP API key (or Intrinio)    │  │
                    │  │   - Polygon API key              │  │
                    │  └──────────────┬─────────────────┘  │
                    │                 │                     │
                    │  ┌──────────────▼─────────────────┐  │
                    │  │   QuestDB                       │  │
                    │  └────────────────────────────────┘  │
                    │  ┌────────────────────────────────┐  │
                    │  │   PostgreSQL                    │  │
                    │  └────────────────────────────────┘  │
                    └──────────────────────────────────────┘
                              │
                    ┌─────────▼─────────────────────────────┐
                    │     Spring Boot Application            │
                    │  ┌─────────────────────────────────┐   │
                    │  │ Shared Service Layer             │   │
                    │  │ (KPIProcessor, AlertManager,     │   │
                    │  │  CorrelationEngine, etc.)        │   │
                    │  └──────────┬──────────────────────┘   │
                    │             │                           │
                    │  ┌──────────┴──────────┐               │
                    │  │                     │               │
                    │  ┌▼──────────────┐ ┌───▼────────────┐  │
                    │  │ Mode A: MVC   │ │ Mode B: WebFlux│  │
                    │  │ Virtual       │ │ Netty          │  │
                    │  │ Threads       │ │ Event-Loop     │  │
                    │  └───────────────┘ └────────────────┘  │
                    └────────────────────────────────────────┘
```

**OpenBB REST Endpoints Used:**

| Application Need           | OpenBB Endpoint                                     | Provider            |
|:---------------------------|:----------------------------------------------------|:--------------------|
| FRED EFFR                  | `GET /api/v1/economy/fred_series?symbol=EFFR`       | `fred`              |
| FRED RRP                   | `GET /api/v1/economy/fred_series?symbol=RRPONTSYD`  | `fred`              |
| FRED TGA Balance           | `GET /api/v1/economy/fred_series?symbol=WTREGEN`    | `fred`              |
| FRED Balance Sheet         | `GET /api/v1/economy/fred_series?symbol=WALCL`      | `fred`              |
| FRED IORB                  | `GET /api/v1/economy/fred_series?symbol=IORB`       | `fred`              |
| SOFR                       | `GET /api/v1/fixedincome/rate/sofr`                 | `federal_reserve`   |
| TGCR                       | `GET /api/v1/fixedincome/rate/tgcr`                 | `federal_reserve`   |
| BGCR                       | `GET /api/v1/fixedincome/rate/bgcr`                 | `federal_reserve`   |
| Treasury rates (3m T-Bill) | `GET /api/v1/fixedincome/government/treasury_rates` | `federal_reserve`   |
| Equity historical prices   | `GET /api/v1/equity/price/historical`               | `fmp` or `intrinio` |
| ETF historical prices      | `GET /api/v1/equity/price/historical`               | `fmp`               |

**OpenBB Python SDK Endpoints Used** (via analytics worker, not REST):

| Application Need      | OpenBB SDK Call                                              | Notes                                                        |
|:----------------------|:-------------------------------------------------------------|:-------------------------------------------------------------|
| ADF stationarity test | `obb.econometrics.unit_root(data, column, regression)`       | Returns `adfstat`, `pvalue`, `usedlag`, `nobs`, `icbest`     |
| ADF + KPSS test       | `obb.quantitative.unitroot_test(data, target, fuller_reg)`   | Returns structured `adf` and `kpss` sub-objects              |
| OLS regression        | `obb.econometrics.ols_regression(data, y_column, x_columns)` | Returns statsmodel object — Python SDK only, not on REST API |
| Correlation matrix    | `obb.econometrics.correlation_matrix(data)`                  | Batch computation, not rolling-window                        |
| Sharpe ratio          | `obb.quantitative.sharpe_ratio(data, target)`                | For backtesting fitness evaluation                           |
| Sortino ratio         | `obb.quantitative.sortino_ratio(data, target)`               | For backtesting fitness evaluation                           |

**OpenBB Configuration** (user responsibility):

The user must create an OpenBB configuration file. Example:

```python
# openbb_config.py — user creates and manages this
from openbb import obb

# Credentials — set via environment variables or direct assignment
obb.user.credentials.fred_api_key = "${FRED_API_KEY}"
obb.user.credentials.fmp_api_key = "${FMP_API_KEY}"
obb.user.credentials.polygon_api_key = "${POLYGON_API_KEY}"
```

The application documents the required OpenBB setup in its README and provides a
health-check endpoint (`/actuator/health/openbb`) that verifies connectivity.

#### Database Boundary

| Store          | Data                                                                                                            | Retention                                        |
|:---------------|:----------------------------------------------------------------------------------------------------------------|:-------------------------------------------------|
| **QuestDB**    | Raw ticks, rate snapshots, ILI history, Z-score series, correlation outputs                                     | Tick data: 90 days; daily aggregates: indefinite |
| **PostgreSQL** | YAML config snapshots, user preferences, alert rules, signal log (append-only), backtest results, ingestion DLQ | Indefinite (with periodic archiving)             |

Schema migrations:

- **PostgreSQL:** Flyway.
- **QuestDB:** Custom `QuestDBMigrationRunner` with checksum validation and execution tracking,
  mirroring Flyway's behavior for time-series tables. Applied at startup, tracks applied
  migrations in a `__questdb_schema_history` table.

#### Dual-Mode Concurrency Design

The application starts in **one** of two modes, controlled by `monitor.concurrency-mode`
in `application.yml`. A shared service layer sits behind interfaces so that the ingestion,
computation, and API modules are agnostic to which mode is active.

```
                    ┌─────────────────────────────┐
                    │     Shared Service Layer     │
                    │  (KPIProcessor, AlertManager, │
                    │   CorrelationEngine, etc.)    │
                    └──────────┬──────────────────┘
                               │
               ┌───────────────┼───────────────┐
               │               │               │
    ┌──────────▼──────┐ ┌──────▼──────────────┐
    │   Mode A: MVC   │ │  Mode B: WebFlux    │
    │ Virtual Threads │ │   Netty Event-Loop  │
    │ @Async, Executor│ │  Flux/Mono, Scheduler│
    └─────────────────┘ └─────────────────────┘
```

**Mode A — Virtual Threads + Spring MVC (blocking):**

- `spring.threads.virtual.enabled=true` — all HTTP handlers run on virtual threads.
- Ingestion scheduled tasks use `@Scheduled` with a `SimpleAsyncTaskExecutor`
  configured for virtual threads.
- OpenBB REST calls use `java.net.http.HttpClient` with virtual threads.
- Polygon WebSocket ingestion uses `java.net.http.HttpClient` with
  virtual threads (one thread per WebSocket connection).
- Structured Concurrency (`java.util.concurrent.StructuredTaskScope`) for
  fan-out calls (e.g., fetch FRED + NY Fed + equity prices in parallel).
- When to use: teams familiar with blocking I/O, simpler stack traces, easier debugging.

**Mode B — WebFlux (reactive):**

- `spring-boot-starter-webflux` on Netty.
- Ingestion uses `Flux.interval()` + `WebClient` for OpenBB REST polling.
- Polygon WebSocket via `WebSocketClient` from `reactor-netty`.
- Parallel external calls use `Mono.zip()` for fan-out.
- `publishOn(Schedulers.boundedElastic())` for any blocking QuestDB/PostgreSQL driver calls.
- When to use: high connection density, backpressure-sensitive pipelines, teams experienced
  with reactive programming.

**Switching:** A single property in `application.yml`:

```yaml
monitor:
  concurrency-mode: virtual-threads  # or "webflux"
```

Spring profiles (`@Profile("virtual-threads")` / `@Profile("webflux")`) activate
the corresponding ingestion and controller implementations at startup.

**Cross-Stack Consistency Guard:**

To prevent behavioral drift between MVC and WebFlux modes, a mandatory **Cross-Stack
Consistency Test Suite** is implemented. It replays identical payloads through both
Mode A and Mode B ingestion paths and asserts bit-perfect equality in resulting ILI
scores, correlation coefficients, and signal states. This suite runs in CI on every
PR to catch floating-point or ordering divergences between the two modes.

---

### 2. Logic Layer and Algorithms

#### A. Internal Liquidity Index (ILI) — Tiered Normalization

A synthetic KPI calculated as a weighted composite of z-score-normalized components.

**Tiered Lookback Strategy:**
Different data frequencies require different lookback windows. A single fixed window
fails to capture both structural trends and acute stress:

| Component Type | Examples           | Lookback          | Rationale                                   |
|:---------------|:-------------------|:------------------|:--------------------------------------------|
| **Macro**      | RRP_Volume, WALCL  | 252 days (1 year) | Structural balance-sheet trends move slowly |
| **Flow**       | EFFR - IORB Spread | 60 days           | Medium-term liquidity cycles                |
| **Volatility** | SOFR_Volatility    | 20 days           | High-frequency stress detection             |

```
Z_rrp    = zscore(RRP_Volume, lookback=252d)
Z_spread = zscore(EFFR - IORB, lookback=60d)    # Note: IORB, not IOER
Z_vol    = zscore(SOFR_Volatility, lookback=20d)

ILI = w1 * Z_rrp + w2 * Z_spread - w3 * Z_vol
```

Constraints:

- `w1 + w2 + w3 = 1.0` (validated at startup).
- Default weights: `{ rrp: 0.4, spread: 0.4, vol: 0.2 }`.
- All weights must be in `[0.0, 1.0]`.
- If any component has fewer than 10 data points in its lookback window, ILI is not
  calculated and a `DATA_INSUFFICIENT` status is returned.

**Zero Variance Guard:**
In periods of stagnant rates (e.g., Fed funds rate pinned at target for months),
the standard deviation of a component may approach zero, causing the z-score to
produce `NaN` or `Infinity`. If `stddev < 0.0001` over the window, the z-score for
that component defaults to `0.0` (neutral) instead of `NaN`. This prevents the entire
ILI from becoming undefined during calm regimes.

Calibration plan: weights are initially set by domain judgment. A quarterly backtest
recalibrates weights by maximizing the Sharpe ratio of ILI-derived signals against
SPY returns.

Historical note: IOER was renamed to IORB (Interest on Reserve Balances) in July 2021.
The FRED series code is `IORB`. The codebase must use `IORB` consistently.

#### B. Correlation Engine — Dynamic Lag Analysis

Algorithm: Rolling Pearson Correlation on differenced (stationary) time series,
with statistical significance testing.

Preprocessing:

1. All raw series are converted to daily changes (first differences or log-returns)
   to ensure stationarity.
2. Stationarity is verified via OpenBB's ADF test endpoint (`obb.econometrics.unit_root`
   returns `adfstat` and `pvalue`). Applied at startup to the most recent 252-day window.
   If the ADF test fails (p > 0.05), the series is differenced again and a warning is logged.

Correlation calculation:

- Window size: configurable (default 20 trading days), exposed as
  `strategies.liquidity_pivot.correlation_window`.
- QuestDB query uses `SAMPLE BY` to align both series to a daily grid, then a
  rolling `CORR()` function computed in Java (QuestDB lacks a built-in rolling
  correlation aggregate — fetch the window and compute in the service layer).
- Output: for each trading day, produce `{ correlation, p_value, sample_size }`.

**AIC-based Dynamic Lag Selection:**
Instead of a fixed lag order for the Granger Causality Test, the engine uses
**AIC (Akaike Information Criterion)** to dynamically select the optimal lag order
(up to a configurable max, default 10 days) for each specific Symbol/Metric pair.
This avoids under-fitting (fixed lag too low) and over-fitting (fixed lag too high)
by letting the data determine the appropriate lag structure.

OpenBB's `obb.econometrics.causality` endpoint supports specifying a single `lag`
parameter (PositiveInt, default 3) but does **not** support AIC-based automatic
lag selection. Therefore, the AIC lag selection loop runs in Java: for each candidate
lag (1 to `granger_max_lag_order`), call the OpenBB causality endpoint, compute AIC
from the residual sum of squares and lag count, and select the lag minimizing AIC.

Lead-lag analysis:

- Apply Granger Causality Test (bivariate VAR model) with AIC-selected lag order
  to determine if changes in funding metrics statistically lead equity returns.
- Report `{ f_statistic, p_value, lag_order, direction }` where direction is
  `FUNDING_LEADS_EQUITY`, `EQUITY_LEADS_FUNDING`, or `NO_RELATIONSHIP`.

Significance threshold: only correlations with `p_value < 0.05` and `|correlation| > 0.3`
are surfaced to the dashboard. All others are flagged as `INSIGNIFICANT`.

#### C. Signal Generation (Adaptive Thresholding)

Replaces fixed Z-score thresholds with a percentile-rank system that adapts to the
current volatility regime.

Percentile rank method:

1. Compute ILI percentile rank over a rolling window (default 252 trading days).
2. Buy Signal: ILI percentile < 5% (extreme fear/liquidity squeeze).
3. Sell Signal: ILI percentile > 95% (excessive exuberance/liquidity peak).
4. Thresholds are configurable: `strategies.liquidity_pivot.buy_percentile` and
   `sell_percentile`.

Regime detection:

- Cluster the last 252 days of daily volatility (standard deviation of ILI changes)
  into 3 regimes using k-means (low/medium/high vol).
- Per-regime, maintain separate percentile thresholds. In a low-vol regime, tighten
  thresholds to 2%/98%; in a high-vol regime, widen to 10%/90%.

**Intraday Proxy Service:**
SOFR is published T+1 at 8:00 AM ET, leaving a gap during intraday trading hours.
To provide continuous signal coverage:

- The system uses **3-month Treasury Bill yields** as a real-time proxy for funding
  pressure between official SOFR updates.
- T-Bill data is fetched via OpenBB `obb.fixedincome.government.treasury_rates`
  (field `month_3`, provider `federal_reserve`). This endpoint returns daily data
  at no additional API key cost (Federal Reserve H.15 release).
- Proxy-adjusted ILI is calculated intraday using the T-Bill proxy in place of the
  SOFR-derived component.
- Signals generated using proxy data are flagged with status
  `SPECULATIVE_STALE_MACRO` to indicate reduced confidence. Once official SOFR is
  published, the signal is recalculated and the flag is cleared or confirmed.

Signal quality filters:

- Look-ahead bias prevention: the rolling window is offset by 1 day — today's ILI
  is calculated from data up to and including yesterday's close.
- Minimum volatility filter: if the ADR (Average Daily Range) of the ILI over the
  last 20 days is below a configurable threshold (`min_ili_adr`), suppress signals
  (the index is too flat to generate meaningful readings).
- Cooldown period: after a signal fires, no new signal of the same direction for
  `cooldown_period` (default 4 hours). If a stronger signal fires during cooldown
  (e.g., percentile drops from 4% to 1%), it is logged but not acted upon unless
  `allow_override_in_cooldown` is `true`.

Transaction cost modeling:

- Each signal includes an estimated cost: `estimated_slippage` (configurable bps) +
  `commission_per_share` (configurable).
- The signal's expected move must exceed `2 * estimated_cost` to be actionable.
- Non-actionable signals are logged with status `COST_EXCEEDS_EXPECTED_MOVE`.

Signal status enum:

- `ACTIONABLE` — passed all filters, cost-justified, fresh data.
- `SPECULATIVE_STALE_MACRO` — using intraday proxy, awaiting official SOFR.
- `COST_EXCEEDS_EXPECTED_MOVE` — signal direction valid but not economically viable.
- `COOLDOWN` — duplicate direction suppressed.
- `INSUFFICIENT_DATA` — not enough data points in lookback window.

---

### 3. KPI System

| KPI Name                   | Source                      | Calculation                                                                                                                                                                                                                                           | Output                                              |
|:---------------------------|:----------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------|
| **Liquidity Stress Index** | Composite                   | `(SOFR - Fed_Target_Upper) / (Fed_Target_Upper - Fed_Target_Lower)`, z-scored over 60 days. Adjusted by T-Bill proxy intraday. T-Bill data from OpenBB `treasury_rates.month_3`.                                                                      | Scalar: positive = stress                           |
| **Repo/Equity Beta**       | OpenBB OLS                  | OpenBB `obb.econometrics.ols_regression` wraps `statsmodels.OLS`. Window: 60d. Application extracts `beta`, `std_error`, `r_squared` from the statsmodel result object.                                                                               | `{ beta, std_error, r_squared, window }`            |
| **RRP Drain Velocity**     | OpenBB (FRED)               | `(RRP_t - RRP_t-N) / N`, N=20 days. Also compute acceleration (2nd derivative). RRP from OpenBB `obb.economy.fred_series(symbol="RRPONTSYD")`.                                                                                                        | `{ velocity_b_per_day, acceleration, period_days }` |
| **Systemic Risk Heatmap**  | OpenBB (Fed Reserve + FRED) | 4-axis z-scored matrix: (1) tri-party vs GCF spread, (2) SOFR 99th-25th pctl, (3) TGCR vs BGCR spread, (4) TGA balance change rate. SOFR/TGCR/BGCR from OpenBB `obb.fixedincome.rate.*`. TGA from OpenBB `obb.economy.fred_series(symbol="WTREGEN")`. | JSON matrix + quadrant labels                       |

---

### 4. Data Source Details

#### Primary Data Path: OpenBB Platform (User-Managed Sidecar)

| Source           | Data Points                | OpenBB Endpoint                                                   | Provider            | Frequency                |
|:-----------------|:---------------------------|:------------------------------------------------------------------|:--------------------|:-------------------------|
| **FRED**         | EFFR                       | `/api/v1/economy/fred_series?symbol=EFFR`                         | `fred`              | Daily                    |
| **FRED**         | RRP                        | `/api/v1/economy/fred_series?symbol=RRPONTSYD`                    | `fred`              | Daily                    |
| **FRED**         | TGA Balance                | `/api/v1/economy/fred_series?symbol=WTREGEN`                      | `fred`              | Daily                    |
| **FRED**         | Fed Balance Sheet          | `/api/v1/economy/fred_series?symbol=WALCL`                        | `fred`              | Weekly (Thu)             |
| **FRED**         | IORB                       | `/api/v1/economy/fred_series?symbol=IORB`                         | `fred`              | Daily                    |
| **Fed Reserve**  | SOFR (all pctl)            | `/api/v1/fixedincome/rate/sofr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET       |
| **Fed Reserve**  | TGCR                       | `/api/v1/fixedincome/rate/tgcr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET       |
| **Fed Reserve**  | BGCR                       | `/api/v1/fixedincome/rate/bgcr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET       |
| **Fed Reserve**  | Treasury rates (3m T-Bill) | `/api/v1/fixedincome/government/treasury_rates` (field `month_3`) | `federal_reserve`   | Daily, no API key needed |
| **FMP/Intrinio** | Equity aggregates (1d)     | `/api/v1/equity/price/historical`                                 | `fmp` or `intrinio` | Daily                    |

#### Direct Data Path: Polygon.io WebSocket (Application-Managed)

| Source      | Data Points     | Connection                             | Frequency                |
|:------------|:----------------|:---------------------------------------|:-------------------------|
| **Polygon** | Real-time ticks | `wss://socket.polygon.io/...` (direct) | Real-time (market hours) |

Polygon WebSocket is kept as a direct connection because:

1. OpenBB's REST API is request-response only — it cannot replace sub-second WebSocket streaming.
2. The OpenBB Polygon provider was removed in v4.7.0.
3. Tick data requires the lowest possible latency for accurate KPI computation.

#### Analytics Data Path: OpenBB Python SDK (Analytics Worker)

| Function           | OpenBB SDK Call                                              | Access Method                                     |
|:-------------------|:-------------------------------------------------------------|:--------------------------------------------------|
| ADF test           | `obb.econometrics.unit_root(data, column, regression)`       | Python worker process                             |
| ADF + KPSS test    | `obb.quantitative.unitroot_test(data, target, fuller_reg)`   | Python worker process                             |
| OLS regression     | `obb.econometrics.ols_regression(data, y_column, x_columns)` | Python worker process (not available on REST API) |
| Correlation matrix | `obb.econometrics.correlation_matrix(data)`                  | Python worker process                             |
| Sharpe ratio       | `obb.quantitative.sharpe_ratio(data, target)`                | Python worker process                             |
| Sortino ratio      | `obb.quantitative.sortino_ratio(data, target)`               | Python worker process                             |
| Granger causality  | `obb.econometrics.causality(data, y_column, x_column, lag)`  | Python worker process (no AIC — loop in Java)     |

The analytics worker is a small Python process (FastAPI or CLI) that accepts DataFrames
(via JSON or Arrow IPC) from the Java application, executes OpenBB SDK calls, and returns
structured JSON results. It is packaged as a Docker image alongside the main application.

#### API Tier Requirements

| Source                                | Minimum Tier               | Cost    | Rate Limit          |
|:--------------------------------------|:---------------------------|:--------|:--------------------|
| **OpenBB**                            | Free (open-source, AGPLv3) | $0      | N/A — local sidecar |
| **FRED** (via OpenBB)                 | Free (API key)             | $0      | 120 req/min         |
| **Federal Reserve** (via OpenBB)      | Free (no key)              | $0      | No documented limit |
| **FMP** (via OpenBB, for equity data) | Starter                    | $14/mo  | Unlimited REST      |
| **Polygon** (direct WebSocket)        | Starter (real-time WS)     | $199/mo | 5 WS connections    |
| **Polygon** (direct REST fallback)    | Basic (aggregates only)    | $49/mo  | Unlimited REST      |

#### OpenBB Setup Requirements (User Responsibility)

The user must:

1. Install OpenBB Platform: `pip install "openbb[fred,federal_reserve,fmp,econometrics,quantitative]"`
2. Configure API keys via environment variables or OpenBB config file
3. Start the REST API server: `uvicorn openbb_core.api.rest_api:app --host 0.0.0.0 --port 8000`
4. (For analytics worker) Start the Python analytics worker process
5. Keep OpenBB running alongside the Spring Boot application

The application's README provides a step-by-step setup guide and a `docker-compose.openbb.yml`
for convenience, but the OpenBB sidecar remains outside the project's build and release artifacts.

#### Missing Data Policy

| Scenario                           | Strategy                                                                                                                                                                 |
|:-----------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FRED/NY Fed weekend or holiday gap | Forward-fill up to 3 business days. After 3 days, mark `STALE`, suppress ILI/signal calculations.                                                                        |
| NY Fed T+1 publication lag         | SOFR used in ILI is always as-of yesterday. Between publications, T-Bill proxy (OpenBB `treasury_rates.month_3`) fills the gap. Dashboard labels data freshness.         |
| Polygon WebSocket disconnect       | Auto-reconnect with exponential backoff (1s, 2s, 4s, ... 60s max). If disconnected > 5 min, switch to REST aggregate polling via OpenBB (`obb.equity.price.historical`). |
| Outlier detection                  | Any rate move > 50 bps/day flagged `SUSPECT`, requires manual confirmation before ILI inclusion.                                                                         |
| QuestDB unreachable                | Buffer up to 1,000,000 events in-memory. If memory utilization > 80%, overflow to local **Chronicle Queue** (disk-backed). Replay on reconnect.                          |
| OpenBB sidecar unreachable         | Retry 3x with exponential backoff. If unreachable > 5 min, mark all OpenBB-sourced series as `UNAVAILABLE`. Polygon WebSocket continues independently. Log alert.        |
| FRED returns HTTP 503 (via OpenBB) | OpenBB retries internally. If persistent, mark series `UNAVAILABLE` for current cycle. Log alert.                                                                        |

#### Data Alignment Strategy

All series resampled to a common daily grid aligned to US equity market close (4:00 PM ET):

- Equity prices from OpenBB: last price before 4:00 PM ET (OpenBB standardizes across providers).
- FRED: daily close-aligned by definition (OpenBB returns as-is).
- NY Fed: SOFR mapped to the prior business day's grid position (OpenBB returns as-is).
- T-Bill proxy: daily yield from Federal Reserve H.15, mapped to current day's grid.
- Polygon ticks: last price before 4:00 PM ET (direct WebSocket, not through OpenBB).

---

### 5. Configuration System

YAML-based with schema validation and hot-reloading.

```yaml
monitor:
  concurrency-mode: virtual-threads    # "virtual-threads" or "webflux"

  openbb:
    base-url: "http://localhost:8000"   # OpenBB REST API sidecar
    analytics-worker-url: "http://localhost:8001"  # Python analytics worker
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  polygon:
    ws-url: "wss://socket.polygon.io/stocks"
    api-key: "${POLYGON_API_KEY}"
    reconnect-backoff-max: "60s"

  watchlists:
    - name: "Magnificent 7"
      symbols: [ "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA" ]
    - name: "Broad Market"
      symbols: [ "SPY", "QQQ", "IWM", "DIA" ]

  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      ili_lookback_days:
        macro: 252
        flow: 60
        volatility: 20
      ili_min_data_points: 10
      zero_variance_threshold: 0.0001
      buy_percentile: 5
      sell_percentile: 95
      regime_detection:
        enabled: true
        clusters: 3
        low_vol_thresholds: { buy: 2, sell: 98 }
        high_vol_thresholds: { buy: 10, sell: 90 }
      min_ili_adr: 0.15
      cooldown_period: "4h"
      allow_override_in_cooldown: false
      correlation_window_days: 20
      granger_max_lag_order: 10
      correlation_significance:
        min_p_value: 0.05
        min_abs_correlation: 0.3
      intraday_proxy:
        enabled: true
        source: "openbb_treasury_3m"
      cost_model:
        estimated_slippage_bps: 5
        commission_per_share: 0.005
        min_move_to_cost_ratio: 2.0

  ingestion:
    openbb_sync_interval: "6h"
    polygon_ws_enabled: true
    polygon_rest_fallback: true
    stale_threshold_days: 3
    outlier_bps_threshold: 50
    reconnect_backoff_max: "60s"
    buffer_capacity: 1000000
    chronicle_queue_path: "./data/overflow"

  retention:
    tick_data_days: 90
    daily_aggregates: indefinite

  observability:
    metrics_enabled: true
    log_format: json
    health_check_interval: "30s"
```

#### Schema Validation

All configuration bound to `@ConfigurationProperties` with `@Validated` + JSR 380:

- `ili_weights` values must sum to `1.0 +/- 0.001`.
- Each weight in `[0.0, 1.0]`.
- `buy_percentile < sell_percentile`.
- `concurrency-mode` must be `"virtual-threads"` or `"webflux"`.
- `symbols` lists non-empty, each symbol matching `[A-Z]{1,5}`.
- `buffer_capacity` must be > 0.
- `chronicle_queue_path` must be a writable directory (validated at startup).
- `openbb.base-url` must be a valid URL.
- `openbb.analytics-worker-url` must be a valid URL.

Invalid configuration fails fast at startup with a clear error message.

#### Hot-Reloading

- Hot-reloadable: `strategies.*`, `ingestion.*`, `watchlists.*`.
- Restart-required: `concurrency-mode`, `openbb.base-url`, database connection strings, retention policy.
- Implementation: Spring Cloud Config with native file backend + `@RefreshScope` on
  strategy beans. `FileSystemWatcher` detects YAML changes and triggers
  `/actuator/refresh` internally.
- Each reload logged to PostgreSQL with a before/after diff.

---

### 6. Error Handling and Resilience

| Pattern                    | Tool                                     | Scope                                                                                                                                                          |
|:---------------------------|:-----------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Circuit Breaker**        | Resilience4j                             | OpenBB sidecar endpoint, Polygon WebSocket, analytics worker. Opens after 5 consecutive failures, half-opens after 30s.                                        |
| **Retry**                  | Resilience4j                             | All external HTTP (OpenBB, analytics worker): 3 retries, exponential backoff (1s, 2s, 4s).                                                                     |
| **Dead Letter Queue**      | PostgreSQL table `ingestion_dlq`         | Failed events stored with full context for replay.                                                                                                             |
| **Graceful Degradation**   | Application logic                        | OpenBB unreachable: use last known values within stale threshold. Polygon WS down > 5 min: switch to OpenBB equity aggregates (`obb.equity.price.historical`). |
| **NaN/Infinity Guard**     | Utility class `CalculationGuard`         | Every KPI passes `Double.isFinite()`. Invalid results logged, replaced with `NaN`, downstream consumers skip.                                                  |
| **Zero Variance Guard**    | `NormalizationService`                   | Z-score defaults to `0.0` when `stddev < 0.0001`, preventing `NaN` during stagnant regimes.                                                                    |
| **Disk-Backed Overflow**   | Chronicle Queue                          | When in-memory buffer > 80% full, overflow events written to local disk. Prevents OOM during sustained QuestDB outages. Replay on reconnect.                   |
| **Backpressure** (WebFlux) | Reactor `onBackpressureBuffer(capacity)` | Buffer up to `buffer_capacity`, then drop oldest with `DROPPED_DUE_TO_BACKPRESSURE` log.                                                                       |
| **OpenBB Health Check**    | Spring Actuator                          | Periodic `GET /api/v1/system/about` to verify OpenBB sidecar is alive and responsive.                                                                          |

---

### 7. Observability

Metrics (Micrometer + Prometheus endpoint):

- `monitor.ingestion.events.total` — counter, tagged by source (`openbb`, `polygon-ws`).
- `monitor.ingestion.latency` — timer from event receipt to QuestDB write.
- `monitor.ingestion.openbb.latency` — timer for OpenBB REST round-trips.
- `monitor.ingestion.openbb.errors.total` — counter of OpenBB-sidecar call failures.
- `monitor.calculation.ili.duration` — timer per ILI cycle.
- `monitor.signal.generated.total` — counter, tagged by direction and status (including `SPECULATIVE_STALE_MACRO`).
- `monitor.datasource.health` — gauge (1=healthy, 0=circuit open).
- `monitor.buffer.utilization` — gauge of in-memory buffer fill %.
- `monitor.buffer.overflow.to_disk.total` — counter of events spilled to Chronicle Queue.
- `monitor.analytics.worker.latency` — timer for Python analytics worker calls.

Logging:

- Structured JSON via Logback + `logstash-logback-encoder`.
- Each entry: `correlationId`, `source`, `timestamp`, `level`, `message`.
- Correlation IDs assigned per data source batch.

Health Checks (Spring Actuator):

- `/actuator/health/openbb` — OpenBB sidecar connectivity (GET `/api/v1/system/about`).
- `/actuator/health/analytics-worker` — Python analytics worker connectivity.
- `/actuator/health/questdb` — connect + `SELECT 1`.
- `/actuator/health/postgresql` — standard DataSource health.
- `/actuator/health/polygon` — WS connected or last tick < 60s.
- `/actuator/health/chronicle` — overflow queue depth and disk space check.

Self-Monitoring:

- No signals in 30 days: log `SIGNAL_SILENCE_WARNING`.
- No ILI recalculation in > 2x interval: log `CALCULATION_STALL`.
- Chronicle Queue depth > 50% of disk quota: log `OVERFLOW_QUEUE_GROWING`.
- OpenBB sidecar unreachable > 5 min: log `OPENBB_SIDECAR_DOWN`.
- Dashboard shows system health panel with last-update timestamps.

---

### 8. Security

| Concern             | Implementation                                                                                                                                                                            |
|:--------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **API Keys**        | Environment variables or HashiCorp Vault, never in YAML. OpenBB credentials managed by user in OpenBB config, not by the application.                                                     |
| **Polygon API Key** | `${POLYGON_API_KEY}` in application config. Used only for direct WebSocket connection.                                                                                                    |
| **WebSocket Auth**  | Polygon API key in connection handshake (per Polygon protocol).                                                                                                                           |
| **Frontend Auth**   | OAuth2 + PKCE via Spring Security (Auth0/Keycloak). Optional for single-user local deploy.                                                                                                |
| **API Endpoints**   | All `/api/**` require valid JWT. WebSocket endpoints validate token in handshake.                                                                                                         |
| **DB Credentials**  | Spring Boot via environment variables. QuestDB HTTP auth with ILP authentication.                                                                                                         |
| **HTTPS**           | Enforced in prod via `server.ssl` or reverse proxy (nginx/Caddy).                                                                                                                         |
| **OpenBB Sidecar**  | Runs on localhost or internal network. No authentication required by default (OpenBB does not enforce auth on local REST API). In production, restrict network access via firewall rules. |

---

## Implementation Breakdown

### Phase 0: API Contracts and Project Scaffolding

- [ ] Define OpenAPI 3.1 spec for REST endpoints (`/api/v1/kpi/*`, `/api/v1/signals/*`, `/api/v1/config/*`).
- [ ] Define AsyncAPI spec for WebSocket endpoints (`/ws/prices`, `/ws/signals`).
- [ ] Generate TypeScript client types from OpenAPI spec for Next.js frontend.
- [ ] Create multi-module Maven/Gradle project:
  ```
  tickonomics/
  ├── api-contracts/         # OpenAPI + AsyncAPI specs
  ├── ingestion/             # OpenBB client, Polygon WebSocket client
  ├── computation/           # KPI calculation, correlation engine, signal generation
  ├── analytics/             # Python analytics worker (ADF, OLS, Sharpe, causality)
  ├── persistence/           # QuestDB + PostgreSQL repositories
  ├── web/                   # REST controllers, WebSocket handlers
  ├── webflux/               # WebFlux-specific implementations (Mode B)
  ├── app-mvc/               # Spring MVC + Virtual Threads entrypoint (Mode A)
  ├── app-webflux/           # WebFlux entrypoint (Mode B)
  ├── frontend/              # Next.js 15 application
  └── integration-tests/     # Cross-module integration tests
  ```
- [ ] Implement `OpenBBIngestionClient` — shared HTTP client for all OpenBB REST endpoints
  (FRED series, SOFR/TGCR/BGCR, Treasury rates, equity historical prices).
  Dual-mode: Mode A uses `RestClient` on virtual threads, Mode B uses `WebClient`.
- [ ] Implement `OpenBBAnalyticsClient` — HTTP client for the Python analytics worker
  (wraps OpenBB Python SDK calls for econometrics and quantitative functions).
- [ ] Configure Flyway for PostgreSQL migrations.
- [ ] Implement `QuestDBMigrationRunner` — custom migration runner with checksum validation
  and `__questdb_schema_history` tracking table.
- [ ] Implement Python analytics worker:
    - FastAPI application accepting JSON/Arrow IPC payloads.
    - Wraps `obb.econometrics.unit_root`, `obb.econometrics.ols_regression`,
      `obb.econometrics.causality`, `obb.econometrics.correlation_matrix`,
      `obb.quantitative.sharpe_ratio`, `obb.quantitative.sortino_ratio`,
      `obb.quantitative.unitroot_test`.
    - Returns structured JSON results extracted from statsmodel objects.
- [ ] Implement **Cross-Stack Consistency Test Suite** scaffold — test harness that replays
  identical payloads through both Mode A and Mode B paths and asserts equality.
- [ ] Set up CI/CD pipeline (GitHub Actions): build, test, lint, Docker build.

### Phase 1: Ingestion and Resilience Layer

- [ ] Implement `OpenBBIngestionClient` (dual-mode):
    - Mode A: `@Scheduled` + `RestClient` on virtual threads.
    - Mode B: `Flux.interval()` + `WebClient`.
    - Endpoints covered:
        - FRED series: EFFR, RRPONTSYD, WTREGEN, WALCL, IORB.
        - Fed Reserve: SOFR, TGCR, BGCR.
        - Treasury rates: `treasury_rates` with `month_3` field for T-Bill proxy.
        - Equity prices: `equity.price.historical` via FMP provider.
    - Deduplication: upsert into QuestDB with designated timestamp as unique key.
    - Provider parameter configurable (e.g., switch `fmp` to `intrinio` for equity data).
- [ ] Implement `PolygonWsClient` (dual-mode, direct connection):
    - Mode A: `java.net.http.HttpClient` WebSocket on virtual threads, blocking receive loop.
    - Mode B: `reactor-netty` `WebSocketClient`, `Flux<WebSocketFrame>` stream.
    - Auto-reconnect with exponential backoff (1s to 60s max).
    - Fallback to OpenBB equity aggregates (`obb.equity.price.historical`) after 5 min disconnection.
- [ ] Implement `DiskBackedIngestionBuffer`:
    - Tier 1: in-memory ring buffer (capacity from config, default 1,000,000 events).
    - Tier 2: Chronicle Queue disk-backed overflow when memory utilization > 80%.
    - Replay from Chronicle Queue into QuestDB on reconnect.
- [ ] Implement `DataQualityChecker` — staleness, outlier detection, schema validation.
- [ ] Implement circuit breakers (Resilience4j) for OpenBB sidecar, Polygon WebSocket, and analytics worker.
- [ ] Unit tests for each client with WireMock (OpenBB REST) and mocked WebSocket servers.
- [ ] Integration tests with embedded QuestDB (testcontainers) and PostgreSQL (testcontainers).
- [ ] Cross-Stack Consistency tests: replay identical ingestion payloads through both modes,
  verify identical QuestDB state.

### Phase 2: Analytical Engine

- [ ] Implement `NormalizationService` — z-score over tiered configurable windows
  (252d/60d/20d), handles edge cases:
  - Insufficient data: return `NaN` with `DATA_INSUFFICIENT` status.
  - Zero variance (`stddev < 0.0001`): return `0.0` instead of `NaN`.
- [ ] Implement `IliCalculator` — ILI formula with validated weights, tiered lookback windows,
  data-sufficiency check.
- [ ] Implement ADF stationarity check via `OpenBBAnalyticsClient`:
  - Calls `obb.econometrics.unit_root(data, column, regression="c")`.
  - Returns `adfstat` and `pvalue` — used to verify stationarity of differenced series.
  - If p > 0.05, series is differenced again and warning is logged.
- [ ] Implement `AicLagSelector` — selects optimal VAR lag order per symbol/metric pair
  using Akaike Information Criterion (max lag configurable, default 10).
  For each candidate lag (1..max), calls `OpenBBAnalyticsClient` →
  `obb.econometrics.causality(data, y_column, x_column, lag=N)`, computes AIC from
  residual sum of squares, and selects the lag minimizing AIC.
- [ ] Implement `CorrelationEngine`:
    - Rolling Pearson on differenced series, configurable window.
    - OLS regression for Repo/Equity Beta via `OpenBBAnalyticsClient` →
      `obb.econometrics.ols_regression(data, y_column, x_columns)`. Application extracts
      `beta`, `std_error`, `r_squared` from the statsmodel result wrapper.
    - Outputs `{ correlation, p_value, sample_size }` per day.
    - QuestDB fetches aligned window; computation orchestrated in Java, heavy lifting via OpenBB.
- [ ] Implement `GrangerCausalityTest`:
    - Uses `AicLagSelector` to determine optimal lag.
    - Calls `OpenBBAnalyticsClient` → `obb.econometrics.causality(data, y_column, x_column, lag=optimal)`.
    - Parses four test variants returned (ssr_ftest, ssr_chi2test, lrtest, params_ftest).
    - Outputs `{ f_statistic, p_value, lag_order, direction }`.
- [ ] Implement `KpiProcessor` — orchestrates ILI, all KPI calculations, persists to QuestDB.
- [ ] Implement `RegimeDetector` — k-means on ILI volatility, per-regime thresholds.
- [ ] Implement `IntradayProxyService`:
    - Substitutes T-Bill proxy into ILI when SOFR data is stale (before 8 AM ET publication).
    - T-Bill data fetched via `OpenBBIngestionClient` → `obb.fixedincome.government.treasury_rates`
      (field `month_3`, provider `federal_reserve`, daily granularity, no API key required).
    - Marks resulting signals as `SPECULATIVE_STALE_MACRO`.
    - Recalculates with official SOFR once published, clears or confirms flag.
- [ ] Implement `SignalGenerator`:
    - Percentile-rank thresholds, regime-aware.
    - Look-ahead bias prevention (1-day offset).
    - Minimum volatility filter.
    - Cooldown enforcement with optional override.
    - Transaction cost modeling.
    - Intraday proxy awareness with `SPECULATIVE_STALE_MACRO` flag.
    - Signal status: `ACTIONABLE`, `SPECULATIVE_STALE_MACRO`, `COST_EXCEEDS_EXPECTED_MOVE`,
      `COOLDOWN`, `INSUFFICIENT_DATA`.
- [ ] Implement `AlertManager` — evaluates signals against active config, dispatches
  alerts (WebSocket push, optionally email/webhook).
- [ ] Implement `CalculationGuard` — NaN/Infinity/overflow protection on all outputs.
- [ ] Unit tests (given-when-then):
    - Given SOFR spike, When correlation engine runs, Then beta and p_value update correctly.
    - Given zero-variance input, When z-score computed, Then result is `0.0` (not `NaN`).
    - Given < 10 data points, When ILI computed, Then status is `DATA_INSUFFICIENT`.
    - Given signal during cooldown, When override disabled, Then signal is `COOLDOWN`.
    - Given cost exceeds expected move, When signal evaluated, Then status is `COST_EXCEEDS_EXPECTED_MOVE`.
    - Given signal using T-Bill proxy, When SOFR not yet published, Then signal status is `SPECULATIVE_STALE_MACRO`.
    - Given AIC lag selection, When multiple lag orders tested, Then optimal lag minimizes AIC.
    - Given OpenBB analytics worker unreachable, When ADF test requested, Then fallback to Java-based ADF or mark
      `INSUFFICIENT_DATA`.
- [ ] Property-based tests (jqwik) for ILI: vary weights and inputs, verify output is
  bounded and finite for all valid inputs.
- [ ] Cross-Stack Consistency tests: verify identical ILI scores and signals from identical
  input data regardless of concurrency mode.

### Phase 3: Frontend and Visualization

- [ ] Next.js 15 dashboard with React Query (TanStack Query) for server state.
- [ ] Real-time updates via WebSocket to `/ws/signals` and `/ws/prices`.
- [ ] Multi-pane Lightweight Charts:
    - Top pane: equity price (candlestick).
    - Bottom pane: ILI overlay (line) with buy/sell signal markers.
    - Synchronized crosshair and time-scale.
    - Signal markers visually distinguish `ACTIONABLE` vs `SPECULATIVE_STALE_MACRO`.
- [ ] Correlation matrix (D3.js):
    - Heatmap: rolling correlation between each funding metric and each watched equity.
    - Color: red (negative) to white (zero) to green (positive).
    - Tooltip with p-value, sample size, and AIC-selected lag order.
- [ ] Liquidity Heatmap (D3.js):
    - 4-axis quadrant chart from Systemic Risk Heatmap KPI.
    - Animated transitions on data updates.
- [ ] KPI dashboard cards:
    - Current ILI with historical sparkline.
    - Liquidity Stress Index gauge.
    - Repo/Equity Beta table per watched symbol.
    - RRP Drain Velocity trend line.
- [ ] Data Freshness panel:
    - Shows which data sources are using official data vs. T-Bill proxy.
    - Timestamps and staleness indicators per series.
    - OpenBB sidecar connectivity status.
- [ ] System Health panel:
    - Last-update timestamps per data source.
    - Circuit breaker status indicators (OpenBB, Polygon WS, analytics worker).
    - Calculation staleness warnings.
    - Chronicle Queue overflow depth.
    - OpenBB sidecar health.
- [ ] Configuration editor (admin):
    - Edit strategy parameters with live validation.
    - Show diff before applying changes.
- [ ] Component tests with React Testing Library.
- [ ] E2E tests with Playwright.

### Phase 4: Backtesting Framework

- [ ] Implement `HistoricalDataReplay` — loads QuestDB historical data, replays through
  the computation pipeline at configurable speed.
- [ ] Implement `BacktestEngine`:
    - Takes strategy config and date range.
    - Replays day-by-day, generates signals, tracks simulated portfolio.
    - Outputs: Sharpe ratio, max drawdown, win rate, profit factor, signal frequency.
- [ ] Implement `WeightOptimizer`:
    - Bayesian optimization over ILI weight space (more sample-efficient than grid search).
    - Fitness evaluation uses `OpenBBAnalyticsClient` → `obb.quantitative.sharpe_ratio`
      and `obb.quantitative.sortino_ratio` for consistent Sharpe/Sortino computation.
    - Maximizes backtest Sharpe ratio.
    - Used for quarterly calibration.
- [ ] Backtest report page in frontend:
    - Equity curve, drawdown chart, signal timeline.
    - Comparison of multiple parameter sets.
- [ ] Tests: given known historical data and fixed parameters, verify deterministic
  backtest output matches a pre-computed reference.

### Phase 5: Deployment and Operations

- [ ] Containerization:
    - Multi-stage Dockerfile for backend (Java 25 JDK to JRE image).
    - Dockerfile for frontend (Node.js build to nginx static serving).
    - Dockerfile for analytics worker (Python + OpenBB SDK).
    - `docker-compose.yml` for local dev (backend + frontend + analytics worker + QuestDB +
      PostgreSQL + Chronicle Queue volume mount).
    - `docker-compose.openbb.yml` — reference compose file for running OpenBB sidecar
      (documented but not part of CI/CD pipeline — user-managed).
- [ ] Environment strategy:
    - dev: local docker-compose + user-managed OpenBB sidecar, Polygon free tier.
    - staging: cloud-deployed, real API keys, full data sync, OpenBB as sidecar container.
    - prod: HA PostgreSQL (managed), QuestDB with replication, nginx reverse proxy,
      OpenBB as sidecar container.
- [ ] CI/CD (GitHub Actions):
    - On PR: build, lint, unit tests, integration tests (testcontainers), cross-stack
      consistency tests.
    - On merge to main: build, test, Docker push (backend + frontend + analytics worker),
      deploy to staging.
    - Manual promotion: staging to prod.
    - OpenBB sidecar is NOT part of CI/CD — user deploys and manages independently.
- [ ] Data retention automation:
    - QuestDB partition drop cron for tick data older than `retention.tick_data_days`.
    - PostgreSQL archiving job for signal log entries older than 2 years.
- [ ] Runbook:
    - OpenBB sidecar setup and troubleshooting procedure.
    - Analytics worker deployment and health-check procedure.
    - Polygon WebSocket outage procedure.
    - QuestDB disk space alert procedure.
    - Chronicle Queue overflow recovery (drain queue to QuestDB).
    - ILI weight recalibration from backtest results.
    - Concurrency mode switch procedure.
    - OpenBB provider switch procedure (e.g., FMP → Intrinio for equity data).

---

## Validation Strategy (Continuous)

Testing is integrated into every phase, not deferred to the end.

### Unit Tests (per module, run on every commit)

- Structure: Given-When-Then, named `givenX_whenY_thenZ`.
- Coverage target: >= 85% line coverage on `computation` and `ingestion` modules.
- Edge cases: empty inputs, single data point, zero variance, `NaN`/`Infinity` in input,
  null/missing optional fields in API responses.
- False-positive prevention: every signal test includes a negative case. Randomized input
  tests verify no spurious signals under noise.
- OpenBB-specific: mock OpenBB REST responses using WireMock to test client resilience
  independently of the sidecar.

### Integration Tests (run on PR merge)

- Infrastructure: Testcontainers for QuestDB and PostgreSQL. WireMock for OpenBB REST API.
- End-to-end flow: inject Polygon tick via test WebSocket, verify QuestDB write, verify
  KPI recalculation, verify signal (or no-signal), verify WebSocket push to frontend.
- Dual-mode coverage: all integration tests run with both `virtual-threads` and `webflux`
  profiles.

### Cross-Stack Consistency Tests (run on every PR)

- Replay identical ingestion payloads through both Mode A and Mode B.
- Assert bit-perfect equality on: ILI scores, correlation coefficients, signal states.
- Catch floating-point divergences caused by ordering or threading differences.

### Load Tests (weekly or before release)

- Tool: Gatling or JMeter.
- Scenario: 1000+ ticks/sec sustained for 10 minutes.
- Metrics: CPU %, JVM heap, QuestDB write latency, end-to-end tick-to-dashboard latency,
  Chronicle Queue overflow rate, OpenBB REST call latency.
- Hardware baseline: 4 CPU cores, 8 GB JVM heap, NVMe SSD for QuestDB.
- Pass criteria: mean ingestion latency < 10ms (p99 < 50ms), CPU < 20% on logic layer,
  zero data loss (including Chronicle Queue overflow recovery verification).

### Property-Based Tests (every commit)

- Framework: jqwik.
- ILI invariant: for any valid weights and finite inputs, ILI is always finite.
- ILI invariant: for any input where zero-variance guard triggers, result is `0.0` (not `NaN`).
- Signal invariant: for any ILI value, at most one signal direction fires per evaluation.
- Correlation invariant: for any two finite series of equal length, correlation in `[-1, 1]`.
- AIC invariant: selected lag order is in `[1, max_lag_order]`.

---

## Architecture Design Record

All architectural decisions logged in `docs/adr/` using the ADR format:

| ADR     | Title                                                          | Status   |
|:--------|:---------------------------------------------------------------|:---------|
| ADR-001 | Dual-mode concurrency (Virtual Threads vs WebFlux)             | Proposed |
| ADR-002 | QuestDB Migration Runner for schema safety                     | Proposed |
| ADR-003 | QuestDB for time-series, PostgreSQL for relational             | Proposed |
| ADR-004 | Tiered Z-score normalization (252d/60d/20d) for ILI components | Proposed |
| ADR-005 | Percentile-rank thresholds over fixed Z-scores                 | Proposed |
| ADR-006 | AIC-based dynamic lag selection for Granger causality          | Proposed |
| ADR-007 | Circuit breakers for external API resilience                   | Proposed |
| ADR-008 | Spring Cloud Config for hot-reloading                          | Proposed |
| ADR-009 | Backtesting framework for strategy calibration                 | Proposed |
| ADR-010 | Chronicle Queue for disk-backed ingestion overflow             | Proposed |
| ADR-011 | Intraday T-Bill proxy for SOFR gap-filling                     | Proposed |
| ADR-012 | Cross-Stack Consistency Test Suite for dual-mode parity        | Proposed |
| ADR-013 | OpenBB Platform as user-managed data gateway sidecar           | Proposed |
| ADR-014 | Python analytics worker for OpenBB SDK econometrics calls      | Proposed |
| ADR-015 | Direct Polygon WebSocket bypassing OpenBB for real-time ticks  | Proposed |
| ADR-016 | Provider-agnostic equity data via OpenBB (FMP/Intrinio/Tiingo) | Proposed |

---

## Appendix A: OpenBB Platform Reference

### Verified Endpoint Capabilities (as of OpenBB v4.7.1)

#### `obb.econometrics.causality`

| Field          | Value                                                                                                                                                 |
|:---------------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| Parameters     | `data: list[Data]`, `y_column: str`, `x_column: str`, `lag: PositiveInt = 3`                                                                          |
| AIC support    | No — single lag only, no automatic selection                                                                                                          |
| Returns        | Dict of 4 test variants (ssr_ftest, ssr_chi2test, lrtest, params_ftest), each with `{"F-test": float, "P-value": float, "Count": int, "Lags": float}` |
| Bivariate only | Yes — one x_column, one y_column                                                                                                                      |
| REST API       | Available                                                                                                                                             |
| Underlying     | `statsmodels.tsa.stattools.grangercausalitytests`                                                                                                     |

#### `obb.econometrics.unit_root`

| Field      | Value                                                                            |
|:-----------|:---------------------------------------------------------------------------------|
| Parameters | `data: list[Data]`, `column: str`, `regression: Literal["c", "ct", "ctt"] = "c"` |
| Test       | ADF only (`statsmodels.tsa.stattools.adfuller`)                                  |
| Returns    | `adfstat: float`, `pvalue: float`, `usedlag: int`, `nobs: int`, `icbest: float`  |
| REST API   | Available                                                                        |

#### `obb.quantitative.unitroot_test`

| Field      | Value                                                                                                                             |
|:-----------|:----------------------------------------------------------------------------------------------------------------------------------|
| Parameters | `data: list[Data]`, `target: str`, `fuller_reg: Literal["c", "ct", "ctt", "nc"]`, `kpss_reg: Literal["c", "ct"]`                  |
| Tests      | ADF + KPSS                                                                                                                        |
| Returns    | Structured: `adf.statistic`, `adf.p_value`, `adf.nlags`, `adf.nobs`, `adf.icbest`, `kpss.statistic`, `kpss.p_value`, `kpss.nlags` |
| REST API   | Available                                                                                                                         |

#### `obb.econometrics.ols_regression`

| Field      | Value                                                                                   |
|:-----------|:----------------------------------------------------------------------------------------|
| Parameters | `data: list[Data]`, `y_column: str`, `x_columns: list[str]`                             |
| Returns    | statsmodel OLS result object (non-serializable)                                         |
| REST API   | **Not available** — Python SDK only. Must extract numeric results via analytics worker. |

#### `obb.fixedincome.government.treasury_rates`

| Field          | Value                                                                               |
|:---------------|:------------------------------------------------------------------------------------|
| Parameters     | `start_date: date`, `end_date: date`, `provider: Literal["federal_reserve", "fmp"]` |
| Granularity    | Daily (business days)                                                               |
| 3-month T-Bill | Field `month_3` — always populated by `federal_reserve` provider                    |
| All maturities | `week_4`, `month_1`, `month_2`, `month_3`, `month_6`, `year_1` through `year_30`    |
| API key        | Not required for `federal_reserve` provider                                         |
| Data source    | Federal Reserve H.15 release                                                        |
| Values         | Normalized percent (1% = 0.01)                                                      |

#### `obb.econometrics.correlation_matrix`

| Field      | Value                                                                                       |
|:-----------|:--------------------------------------------------------------------------------------------|
| Parameters | `data: list[Data]`                                                                          |
| Returns    | Correlation matrix as DataFrame                                                             |
| REST API   | Available                                                                                   |
| Note       | Batch computation — not rolling-window. Application must slice windows and call per-window. |

#### `obb.quantitative.sharpe_ratio` / `sortino_ratio`

| Field      | Value                                               |
|:-----------|:----------------------------------------------------|
| Parameters | `data: list[Data]`, `target: str`                   |
| Returns    | Scalar ratio value                                  |
| REST API   | Available                                           |
| Use case   | Backtesting fitness evaluation in `WeightOptimizer` |

### OpenBB Version History (Relevant Breaking Changes)

| Version | Date       | Change                                                                                                                           |
|:--------|:-----------|:---------------------------------------------------------------------------------------------------------------------------------|
| v4.1.2  | 2024-01-18 | Version demoed in the AlgoTrading101 article                                                                                     |
| v4.3.1  | 2024-08-08 | `equity.price.historical`: `adjusted`/`prepost` replaced by `adjustment`/`extended_hours`                                        |
| v4.3.3  | 2024-10-04 | Removed `obb.commodity.lbma_fixing`                                                                                              |
| v4.3.4  | 2024-10-25 | `economy.fred_search`: `is_release` (bool) replaced by `search_type` (enum)                                                      |
| v4.5.0  | 2025-10-08 | OpenBB Hub retired — credentials now local-only. `fixedincome.sofr` moved to `fixedincome.rate.sofr`. Multiple endpoint renames. |
| v4.6.0  | 2026-01-07 | Python 3.9 dropped. Account module removed.                                                                                      |
| v4.7.0  | 2026-03-09 | **Polygon provider removed.** Python 3.14 + Pandas 3.0 support added.                                                            |
| v4.7.1  | 2026-03-09 | Latest stable. PyPI-only patch.                                                                                                  |

### OpenBB Licensing Note

OpenBB Platform is licensed under **AGPLv3** (GNU Affero General Public License v3).
This requires that any application using OpenBB over a network must make its source code
available under the same license. Since OpenBB is a user-managed sidecar and not a
deliverable of this project, the application itself is not subject to AGPLv3 contagion.
The user runs OpenBB independently and the application only communicates with it via HTTP.
However, users should consult legal counsel if they plan to offer this as a commercial service.

([;GYi9jHS~()r2abMg IRB?KBJR_
!<ݣIU1

}.`sl?~ndQ(b}cdTQ}(}[&1>.g{=*zlrcXb_Ncǜ۝q=J=Q)]ŋ;G`}ٔfT˺Üe],B I{T w|w߃0FzR1wB+ J;J0;N}S^:
{}$Fsb*xh=pT<ОJ+дK!>BsQ:rf$pKDV4䜻 |^2*0ܷ
wx9ow\O0m7Saos¾#ʡǗ ߋ _9Ss9 5r>l4Ģc E4Oto5BtA4Hj?
B
\%O!wovR̥ v@PhvVۑv̜ݠjU 9<d%,MY[.d+9NVr
{T,����1~e:p^y+ԡN9{(qu'#'Xܟ_JȪ}'ɱ
e|?mW+ǻ1wݺ
Þbd}F|w\QSNEFc=f=U
-,~911?#\};iNnew>G
W뢜Ǭ={κ;%T_}zQۋ'{ؽ۹+o{W]ʋUUUUUUթVsޡp<~[!No<W 1.#F{>
&߶/YD߷{[ȏkyAjuBdwH!DO/iL9Q@M haC����32P,14&DXH2.9'XTB+gPo`aϭ24qg&Q\&a@	5:PVB84/xԙaHqW+i^l^3/3Dډ@MqiH']mQX#<śE'.ȩ$B@?GdI(x$t
d&'lЭ+h'N*uT:E١Y#Gۗ0Q	:d ŃW`;cAmgΊЈaPۼQɗhEtpӾ
bѐ,Z3,cΠ)0LTt3ds4 Lg+ elC5OXZ"BK䙅6tb(I'w$,BWLM Kŝ"8lg
`R+)1]ZH1dM4?#iU$(( cQ	-@䔽+Zq@@]Aq##\>۠YC8axe	yi:EmL'T.͵i(trYĂ	A`o/>ّG2,;ۂD
KVjVh 6RKD|A5D    <15!&ڔ vQQŨ:ӭN Gӽ/mc!%R!3#"""$I0&`Ϗ)¿k2)?b_V4=Ǧ#qxcz)Xϲ4`s{l<
:E唸mbܐD4{Ơ$EN̚9<k=5{A ݧ
hx!5F#<kDs=gEdaP9  ZÀ
$
*5xA@tiA/"fc@BaB]}^;p1@3߹l(QzS<y
Zd1lVaC6^$R1E!vap T	D( yZ-,?}G<k1H'DpnU%-H+j?&(FA\Da{"č9g3um/
gb&>@V9
dx }DpI`O8diDv	 l
sYӤ<pxYC\irg\Lc;4f$ r@d1spB
yjCDLyU;wV\tk}֮<!`!%5m7|QAW_w|y
MZiӒqK+X71xVF5!u[| `K
{*
Y'	18:/i;M88\3Z5~=G]0(?7rZRn#b
6-S& T_ӹ"Szp0+MJOlPO|9ϊ5r
yD@V6bN3!^Tq+Ơ\~2|E0\18\zHtm27@L({&?<\CXU!Q@MeZnxPo&9/]LXfҹ+|e@O5[c9vbA-"Km>J^Ԧdhϭ|/4nܣW኷^Wlb+]z90dK씥	9![Q5vn=vQ)O
ffD~`An$mBYoEASMBB9%IcX<Et`hyHBvG(ޕ
.'v7.8ήC^p<mCύX{҉"{UnW DC"_xkbiѢ	
j)	EkW<3b%a9*ÔPr{x>ج"?×(]{OrmNҤ
F0ETirXc-;\%
Ni[۸5Nz4o;9źP}p0CZljLL`tjy?#}Hbjo6?Xh;Fkvv4C	L2s8I04[qo(ݕU42CHA{h<(Ѧ%[َzUmn_XX~WaD_o.ҿEVCؚ}ӛm L|K2~CAӹzUr;B)xgi'-
$T<> lkж^+d]gnA-9Xr(Z,|!y?F.k3)2TJ[(Gi{j-׫zd/2Q)    \ZU8!_ֹLfu`)8	%iǛARTu]e@l|N/M6cuɼb[>>g_uݩ[[1u
{F0ǅ^H*c.vWhpVSQ,Ցy<Zcg*@CFq21b_2ySDz};t2wFx侶IsLC<RV/CC2dqU|=?ĥ@dQ%qGN^)N|CCY̾kPP<uiZ7$>32wⷿj@,x:
A
GW>}.&go%\St)*nEk}l-;5kP꫶ֈkJۃhcgy-֡ƽ 0yjUiZ\4ǇJR*tqNS
PĹ![ڳ=魳W"kn~SPωF:yI`M7FNh
]h}uqnBpd=iˣuK+# \ة2vW<Bh[_PhC>QwšuX[$Q!'gQƐ~:ә뇳K|;
Mw6+I^7Mnc3ZvcV_ i+8JΈal46wdmYNaK%#f
3KÛn:6=}_1gÁ $pkaAR;zOV
q;oUaCtzn*c9ǨWZ+ˊANBLgwݽG<!6
Ojv<M~<qml<GYB`ÿ ٖ%CR+k6/J!R2aU ?Gщ 0Te?5 Vcs7<@Lk 3e_c% }0PZ8 E4"S-?|E nYӈy<<ױV!=6q^!{&Qeq" ܉r.x
dWgJvahG\eDQ
L

		tOZFD0N

 3uf@W

̅ǘtM]`&k: }1a
Dt8
favIconUrl"Ochrome-extension://chphlpgkkbolifaimnlloiipkdnihall/images/extension-icon32.png"idN
favIconUrl"
https://cdn.shortpixel.ai/spai/q_lossy+ret_img+to_webp/algotrading101.com/learn/wp-content/uploads/2021/04/cropped-algotrading101-32x32.png"idN
.

nxsClientHistory





4

s} favIconUrl"Ochrome-extension://chphlpgkkbolifaimnlloiipkdnihall/images/extension-icon32.png"idN
favIconUrl"Bhttps://www.gstatic.com/images/branding/searchlogo/ico/favicon.ico"idN

nxsClientHistory

 

4
J4ph R$*DJ'RpQ6I#""\ luiaLj<BW"$FЙ0H'%A;
=3@&¹[~ɱMC8
6@EC(Y>U"1P舡LZLX:0$ Bu,!}EĄeWqj+C]9tUP)Hdlx:9J 2[}']Ǌ7+ рSJ囝>)S\Zb E @9D FQD KjВ2eN\L 1AL=;e̪tf
^hKg}X(eY
ٱ*=7a
"j0 ,W9@5% ː6 uXב t):8!u4)rO |1х1Cz#_y@=@ @D?X^LP =ݡIG
au NZ>H'%^E
+l jFdRUj> %7=U<-l
+ȸz00ĵ cuG"pnRusf6V3
KthP b`YKGBD+;qne<"&a|b(Qb6!bh<t"


ml[K$
5RmJF
O10ū
CJ8QIRRRdDVDX
QG`T@xrt'5͌9+op
삛4;EYFǛqlV|vDXRtbvEd2|&I-nAX-2Z0_X5_+=ofm[oRֵ]9[MbվuǱ:,DUzYzbUy~Ư3=F5ji8.;~紾V]=Vڿ|kEmyRmomT:_
mYeYet6KeR}Էx}qϺ[8u;ֳR뜭m]ڎ[m,N6qX_qu[}gǩi & _w~:wO{?4V{
z;8:VsN
<
> .cX
> Qj
> K̱#py}'z)rR>&xItAϱaf۟<^*rx6(Xҷ#
> ϣ!^sƮ-u3\\U@sǨ(A ̹gɒ5;;('>=j7j<А RSJʏK̇)[R]}!\n6(a7Gu1PQ^^V`?¢̩ P akZ\[*iÂe.'Wgy5BE2<Ϯ(Z%#h^ZFT@bЯgZN/vH
^aY6+ly!34S7F
Q|P'IUN*2Trq<^ͳHȌ
vcvܐcY[|F{&፯# 9bVg~v1o r3)OB4Avei4IEgK܊}WShܳC8&=9B$1[],p8V?fZ/4DHT0=mPd] l/%aӗ<T2 DO,{Y(kG0
1!`mZUh2ʤ1
> IO-9,8Y>a{C x'aȍX)[1/DЈƲʒ> %R
!3#""A$I
58R)    1ǧ!Q6`\ۋ4q.4_c)F2A
F%LaI<ص^H;|!c܆g6

ĀY@(2Jn#Ǿ@ D3JT	`MFEz;$ 3 k'mgNT>:`980T^f1O
pY#HM' AÃ5,(9P@(`?DL"r".+K[
Ee sOmgj
{^
1匘KE#*P=' B&q<2;
,98,ْbdkSu0gx{ еrm\fjmZ1kɚ2P@(ebWHdžP8)+So^Us!X )2w6c'N3PBh@NƐllIE[d(׾C$¶a0zme߄qH0d2XGL
>     $.' ",#(7),01444 '9=82<.342 

2!!22222222222222222222222222222222222222222222222222 
%&'()*456789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz 
$4%&'()*56