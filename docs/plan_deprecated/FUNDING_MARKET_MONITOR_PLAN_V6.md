# Project Plan: Funding Market Monitor (High-Performance Edition) — v6

## Objective

Create a high-performance application using Java 25 and Spring Boot 3.4+ to monitor funding
market metrics and correlate them with equity price movements via a sophisticated KPI system.

The system supports two interchangeable concurrency models:

- **Dual-Mode Architecture:** Virtual Threads + Spring MVC (blocking) **and** WebFlux (reactive),
  selectable at startup via a configuration flag.

### v6 Change Summary

This version introduces three architectural improvements over v5:

**1. TimescaleDB replaces QuestDB + PostgreSQL (single-database architecture)**

| Area                   | v5                                                    | v6                                                             |
|:-----------------------|:------------------------------------------------------|:---------------------------------------------------------------|
| Time-series store      | QuestDB (custom engine, ILP protocol)                 | TimescaleDB hypertables (PostgreSQL extension)                 |
| Relational store       | PostgreSQL (separate instance)                        | Same TimescaleDB instance (regular PostgreSQL tables)          |
| Databases to operate   | 2 (QuestDB + PostgreSQL)                              | **1** (TimescaleDB)                                            |
| Schema migrations      | Flyway (PostgreSQL) + custom `QuestDBMigrationRunner` | **Flyway only** (single target)                                |
| Tick→OHLCV aggregation | Application-side (QuestDB `SAMPLE BY`)                | **TimescaleDB continuous aggregates** with `candlestick_agg()` |
| Retention              | Manual partition drop cron                            | **`add_retention_policy()`** automatic background jobs         |
| Compression            | None                                                  | **TimescaleDB columnstore** (~90% on historical data)          |
| JDBC driver            | Custom/ILP for QuestDB + pgjdbc for PostgreSQL        | **Standard PostgreSQL JDBC** (single connection pool)          |
| Cross-data JOINs       | Impossible (separate databases)                       | **Native SQL JOINs** (same database)                           |
| OHLCV functions        | None built-in                                         | **`candlestick_agg()`, `vwap()`, `time_bucket()`**             |

**2. TA-Lib Java for in-process statistical computations**

| Area                        | v5                                      | v6                                                      |
|:----------------------------|:----------------------------------------|:--------------------------------------------------------|
| Rolling mean/std/var        | Custom Java                             | **TA-Lib `TA_SMA`, `TA_STDDEV`, `TA_VAR`**              |
| Rolling Pearson correlation | OpenBB analytics worker (cross-process) | **TA-Lib `TA_CORREL`** (in-process)                     |
| Rolling beta (OLS)          | OpenBB analytics worker (cross-process) | **TA-Lib `TA_BETA`** (in-process)                       |
| Rolling linear regression   | OpenBB analytics worker (cross-process) | **TA-Lib `TA_LINEARREG_SLOPE/INTERCEPT`** (in-process)  |
| Bollinger Bands (regime)    | Not implemented                         | **TA-Lib `TA_BBANDS`** (available for regime detection) |
| RSI, MACD, ATR              | Not implemented                         | **TA-Lib** (available for future signal enrichment)     |
| Z-score                     | Custom from SMA + STDDEV                | **Composed from TA-Lib primitives**                     |

TA-Lib provides a **pure Java port** (auto-generated from C, no JNI, no native binaries) under
**BSD-3 license**. All 200+ indicators run in-process with zero cross-language overhead.

**3. FinanceToolkit in analytics worker (complementary to OpenBB)**

| Area                                 | v5                        | v6                                                  |
|:-------------------------------------|:--------------------------|:----------------------------------------------------|
| Bond math (duration, convexity, YTM) | Not available             | **FinanceToolkit `FixedIncome` module**             |
| Risk metrics (VaR, CVaR, GARCH)      | Not available             | **FinanceToolkit `Risk` module**                    |
| Fama-French factor analysis          | Not available             | **FinanceToolkit `Performance` module**             |
| Sharpe/Sortino ratio                 | OpenBB `obb.quantitative` | **TA-Lib-based + FinanceToolkit** (dual validation) |
| 150+ financial ratios                | Not available             | **FinanceToolkit** (for future equity screening)    |

FinanceToolkit (MIT license) is added to the Python analytics worker alongside OpenBB SDK.
It provides fixed income analytics, risk metrics, and factor analysis that neither OpenBB
nor TA-Lib cover.

---

## System Architecture

### 1. Tech Stack

| Layer                  | Technology                                      | Purpose                                                                 |
|:-----------------------|:------------------------------------------------|:------------------------------------------------------------------------|
| **Language**           | Java 25 (LTS)                                   | Virtual Threads, Structured Concurrency, Scoped Values                  |
| **Framework**          | Spring Boot 3.4+                                | Auto-configuration, dependency injection, actuator                      |
| **Concurrency**        | Mode A: Virtual Threads + Spring MVC (blocking) | Simple blocking I/O with cheap threads                                  |
| **Concurrency**        | Mode B: WebFlux + Netty (reactive)              | Event-loop, Flux/Mono, backpressure-aware                               |
| **Technical Analysis** | TA-Lib Java (BSD-3)                             | In-process rolling stats, correlation, beta, OLS, 200+ indicators       |
| **Data Gateway**       | OpenBB Platform 4.7.1+ (sidecar, user-managed)  | Standardized access to FRED, Fed Reserve, equity prices, Treasury rates |
| **Analytics Engine**   | FinanceToolkit 2.x (analytics worker)           | Bond math, VaR/CVaR, GARCH, Fama-French factors                         |
| **Database**           | TimescaleDB (PostgreSQL extension)              | Single database for time-series AND relational data                     |
| **Frontend**           | Next.js 15, TailwindCSS, TypeScript             | Dashboard UI                                                            |
| **Charts**             | Lightweight Charts (TradingView)                | Price/KPI time-series panes                                             |
| **Charts**             | D3.js                                           | Liquidity heatmaps, correlation matrices                                |
| **Resilience**         | Chronicle Queue                                 | Disk-backed overflow for high-volume ingestion                          |
| **Real-Time Feed**     | Polygon.io (direct WebSocket)                   | Sub-second tick data — not routed through OpenBB                        |

#### Architecture Diagram

```
                ┌──────────────────────────────────────────────┐
                │         User-Managed Prerequisites           │
                │                                              │
                │  ┌────────────────────────────────────────┐  │
                │  │   OpenBB Platform (sidecar)             │  │
                │  │   FastAPI + Uvicorn                     │  │
                │  │   Port: configurable (def 8000)         │  │
                │  │                                          │  │
                │  │   Extensions:                            │  │
                │  │   - openbb-fred                          │  │
                │  │   - openbb-federal_reserve               │  │
                │  │   - openbb-fmp                           │  │
                │  │   - openbb-econometrics                  │  │
                │  │   - openbb-quantitative                  │  │
                │  │                                          │  │
                │  │   Credentials (user config):             │  │
                │  │   - FRED API key                         │  │
                │  │   - FMP API key (or Intrinio)            │  │
                │  │   - Polygon API key                      │  │
                │  └────────────────────────────────────────┘  │
                └──────────────────────────────────────────────┘
                                    │
                                    │ REST
                ┌───────────────────▼───────────────────────────┐
                │         Spring Boot Application                │
                │                                               │
                │  ┌─────────────────────────────────────────┐  │
                │  │  In-Process Analytics (TA-Lib Java)     │  │
                │  │  TA_SMA, TA_STDDEV, TA_CORREL, TA_BETA  │  │
                │  │  TA_LINEARREG_*, TA_BBANDS, TA_RSI      │  │
                │  └─────────────────────────────────────────┘  │
                │                                               │
                │  ┌─────────────────────────────────────────┐  │
                │  │  Shared Service Layer                    │  │
                │  │  (KPIProcessor, AlertManager,            │  │
                │  │   CorrelationEngine, SignalGenerator)    │  │
                │  └──────────────┬──────────────────────────┘  │
                │                 │                              │
                │     ┌───────────┴───────────┐                 │
                │     │                       │                 │
                │  ┌──▼──────────────┐ ┌──────▼──────────────┐  │
                │  │  Mode A: MVC    │ │  Mode B: WebFlux    │  │
                │  │  Virtual        │ │  Netty Event-Loop   │  │
                │  │  Threads        │ │  Flux/Mono          │  │
                │  └─────────────────┘ └─────────────────────┘  │
                │                                                │
                │  ┌─────────────────────────────────────────┐  │
                │  │  Python Analytics Worker                │  │
                │  │  (ADF, Granger causality via OpenBB SDK) │  │
                │  │  (Bond math, VaR, Fama-French via       │  │
                │  │   FinanceToolkit)                        │  │
                │  └─────────────────────────────────────────┘  │
                └───────────────┬───────────────────────────────┘
                                │ JDBC (batched INSERT / COPY)
                ┌───────────────▼───────────────────────────────┐
                │         TimescaleDB (PostgreSQL)               │
                │                                                │
                │  Hypertables:          Regular tables:         │
                │  - tick_data           - config_snapshots      │
                │  - rate_snapshots      - alert_rules           │
                │  - ili_history         - signal_log            │
                │  - zscore_series       - backtest_results      │
                │  - correlation_outputs - ingestion_dlq         │
                │                                                │
                │  Continuous Aggregates:                        │
                │  - ohlcv_1min, ohlcv_1h, ohlcv_1d             │
                │  - daily_kpi_summary                           │
                │                                                │
                │  Retention: tick_data 90d, auto-compress       │
                └────────────────────────────────────────────────┘
```

#### OpenBB REST Endpoints Used

| Application Need           | OpenBB Endpoint                                                       | Provider            |
|:---------------------------|:----------------------------------------------------------------------|:--------------------|
| FRED EFFR                  | `GET /api/v1/economy/fred_series?symbol=EFFR`                         | `fred`              |
| FRED RRP                   | `GET /api/v1/economy/fred_series?symbol=RRPONTSYD`                    | `fred`              |
| FRED TGA Balance           | `GET /api/v1/economy/fred_series?symbol=WTREGEN`                      | `fred`              |
| FRED Balance Sheet         | `GET /api/v1/economy/fred_series?symbol=WALCL`                        | `fred`              |
| FRED IORB                  | `GET /api/v1/economy/fred_series?symbol=IORB`                         | `fred`              |
| SOFR                       | `GET /api/v1/fixedincome/rate/sofr`                                   | `federal_reserve`   |
| TGCR                       | `GET /api/v1/fixedincome/rate/tgcr`                                   | `federal_reserve`   |
| BGCR                       | `GET /api/v1/fixedincome/rate/bgcr`                                   | `federal_reserve`   |
| Treasury rates (3m T-Bill) | `GET /api/v1/fixedincome/government/treasury_rates` (field `month_3`) | `federal_reserve`   |
| Equity historical prices   | `GET /api/v1/equity/price/historical`                                 | `fmp` or `intrinio` |
| ETF historical prices      | `GET /api/v1/equity/price.historical`                                 | `fmp`               |

#### TA-Lib Java Functions Used (In-Process)

| Function                 | Application Use                                                        |
|:-------------------------|:-----------------------------------------------------------------------|
| `TA_SMA`                 | Rolling mean for z-score normalization and ILI smoothing               |
| `TA_STDDEV`              | Rolling standard deviation for z-score normalization                   |
| `TA_VAR`                 | Rolling variance for volatility analysis                               |
| `TA_CORREL`              | Rolling Pearson correlation between funding metrics and equity returns |
| `TA_BETA`                | Rolling beta (OLS coefficient) for Repo/Equity Beta KPI                |
| `TA_LINEARREG_SLOPE`     | OLS regression slope for trend detection                               |
| `TA_LINEARREG_INTERCEPT` | OLS regression intercept                                               |
| `TA_BBANDS`              | Bollinger Bands for volatility regime detection                        |
| `TA_RSI`                 | Relative Strength Index — available for future signal enrichment       |
| `TA_MACD`                | MACD — available for future signal enrichment                          |
| `TA_ATR`                 | Average True Range — available for volatility measurement              |
| `TA_ROC`                 | Rate of Change — for differencing and return calculation               |
| `TA_CMO`                 | Chande Momentum Oscillator — available for momentum filtering          |

#### Python Analytics Worker Endpoints (Cross-Process)

Functions that cannot run in-process via TA-Lib are delegated to the analytics worker:

| Function                                        | Library                             | Why Not TA-Lib                                      |
|:------------------------------------------------|:------------------------------------|:----------------------------------------------------|
| ADF unit root test                              | `obb.econometrics.unit_root`        | TA-Lib has no stationarity test                     |
| Granger causality (with AIC lag loop)           | `obb.econometrics.causality`        | TA-Lib has no causality test                        |
| OLS with full statistics (r_squared, std_error) | `obb.econometrics.ols_regression`   | TA-Lib gives slope/intercept but not full OLS stats |
| Bond duration/convexity/YTM                     | FinanceToolkit `FixedIncome`        | TA-Lib has no bond math                             |
| VaR, CVaR, GARCH                                | FinanceToolkit `Risk`               | TA-Lib has no risk metrics                          |
| Fama-French factor correlations                 | FinanceToolkit `Performance`        | TA-Lib has no factor models                         |
| Sharpe/Sortino ratio                            | `obb.quantitative` + FinanceToolkit | Cross-validation against TA-Lib-based calculation   |

#### Database Boundary

**Single database: TimescaleDB (PostgreSQL + time-series extension)**

| Data Type                         | Storage                                                    | Retention                   |
|:----------------------------------|:-----------------------------------------------------------|:----------------------------|
| Tick data                         | Hypertable `tick_data` (chunk interval: 1 day)             | 90 days, then auto-compress |
| Rate snapshots (SOFR, EFFR, etc.) | Hypertable `rate_snapshots`                                | Indefinite                  |
| OHLCV candles (1min, 1h, 1d)      | Continuous aggregates `ohlcv_1min`, `ohlcv_1h`, `ohlcv_1d` | Automatic                   |
| ILI history                       | Hypertable `ili_history`                                   | Indefinite                  |
| Z-score series                    | Hypertable `zscore_series`                                 | Indefinite                  |
| Correlation outputs               | Hypertable `correlation_outputs`                           | Indefinite                  |
| Config snapshots                  | Regular table `config_snapshots`                           | Indefinite                  |
| Alert rules                       | Regular table `alert_rules`                                | Indefinite                  |
| Signal log (append-only)          | Regular table `signal_log`                                 | 2 years, then archive       |
| Backtest results                  | Regular table `backtest_results`                           | Indefinite                  |
| Ingestion DLQ                     | Regular table `ingestion_dlq`                              | 90 days                     |

Schema migrations: **Flyway only** — single target, standard PostgreSQL migration path.

**TimescaleDB-specific features used:**

| Feature                            | Usage                                                                |
|:-----------------------------------|:---------------------------------------------------------------------|
| Hypertables                        | All time-series tables partitioned by time                           |
| Continuous Aggregates              | `ohlcv_1min`, `ohlcv_1h`, `ohlcv_1d` auto-refreshed from `tick_data` |
| `candlestick_agg()`                | Financial aggregate for OHLCV construction                           |
| `time_bucket()`                    | Time-based grouping (replaces QuestDB `SAMPLE BY`)                   |
| `first()` / `last()`               | Time-series ordering functions                                       |
| `time_bucket_gapfill()` + `locf()` | Gap-filling for missing business days (replaces custom forward-fill) |
| Columnstore compression            | Auto-compress tick data older than 7 days (~90% reduction)           |
| Retention policies                 | Auto-drop tick chunks older than 90 days                             |
| SkipScan                           | Fast "latest price per symbol" queries                               |

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
- TimescaleDB writes use standard JDBC with batched multi-row INSERT.
- TA-Lib computations execute synchronously on virtual threads (in-process, no overhead).
- Structured Concurrency (`java.util.concurrent.StructuredTaskScope`) for
  fan-out calls (e.g., fetch FRED + NY Fed + equity prices in parallel).

**Mode B — WebFlux (reactive):**

- `spring-boot-starter-webflux` on Netty.
- Ingestion uses `Flux.interval()` + `WebClient` for OpenBB REST polling.
- Polygon WebSocket via `WebSocketClient` from `reactor-netty`.
- TimescaleDB writes use R2DBC or `Schedulers.boundedElastic()` + JDBC batching.
- TA-Lib computations wrapped in `Mono.fromCallable()` (CPU-bound, runs on bounded elastic).
- Parallel external calls use `Mono.zip()` for fan-out.

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
Z-scores are computed using TA-Lib primitives: `(value - TA_SMA) / TA_STDDEV`.

**Tiered Lookback Strategy:**
Different data frequencies require different lookback windows. A single fixed window
fails to capture both structural trends and acute stress:

| Component Type | Examples           | Lookback          | Rationale                                   |
|:---------------|:-------------------|:------------------|:--------------------------------------------|
| **Macro**      | RRP_Volume, WALCL  | 252 days (1 year) | Structural balance-sheet trends move slowly |
| **Flow**       | EFFR - IORB Spread | 60 days           | Medium-term liquidity cycles                |
| **Volatility** | SOFR_Volatility    | 20 days           | High-frequency stress detection             |

```
Z_rrp    = (RRP_Volume - TA_SMA(RRP_Volume, 252)) / TA_STDDEV(RRP_Volume, 252)
Z_spread = (Spread - TA_SMA(Spread, 60)) / TA_STDDEV(Spread, 60)    # IORB, not IOER
Z_vol    = (SOFR_Vol - TA_SMA(SOFR_Vol, 20)) / TA_STDDEV(SOFR_Vol, 20)

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
produce `NaN` or `Infinity`. If `TA_STDDEV` returns a value < 0.0001 over the window,
the z-score for that component defaults to `0.0` (neutral) instead of `NaN`.

Calibration plan: weights are initially set by domain judgment. A quarterly backtest
recalibrates weights by maximizing the Sharpe ratio of ILI-derived signals against
SPY returns.

Historical note: IOER was renamed to IORB (Interest on Reserve Balances) in July 2021.
The FRED series code is `IORB`. The codebase must use `IORB` consistently.

#### B. Correlation Engine — Dynamic Lag Analysis

Algorithm: Rolling Pearson Correlation on differenced (stationary) time series,
with statistical significance testing.

Preprocessing:

1. All raw series are converted to daily changes using `TA_ROC` (rate of change)
   to ensure stationarity.
2. Stationarity is verified via the analytics worker calling OpenBB's ADF test
   (`obb.econometrics.unit_root` returns `adfstat` and `pvalue`). Applied at startup
   to the most recent 252-day window. If the ADF test fails (p > 0.05), the series
   is differenced again and a warning is logged.

Correlation calculation:

- Window size: configurable (default 20 trading days).
- `TA_CORREL` computes rolling Pearson correlation in-process — no cross-process call.
- `TA_BETA` computes rolling OLS beta in-process for the Repo/Equity Beta KPI.
- Data aligned via TimescaleDB `time_bucket()` and `time_bucket_gapfill()`.
- Output: for each trading day, produce `{ correlation, p_value, sample_size }`.

**AIC-based Dynamic Lag Selection:**
Instead of a fixed lag order for the Granger Causality Test, the engine uses
**AIC (Akaike Information Criterion)** to dynamically select the optimal lag order
(up to a configurable max, default 10 days) for each specific Symbol/Metric pair.

OpenBB's `obb.econometrics.causality` supports specifying a single `lag` parameter
but does **not** support AIC-based automatic lag selection. The AIC lag selection loop
runs in Java: for each candidate lag (1 to `granger_max_lag_order`), call the analytics
worker → OpenBB causality endpoint, compute AIC from the residual sum of squares and
lag count, and select the lag minimizing AIC.

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

- `TA_BBANDS` (Bollinger Bands) applied to ILI series to detect volatility regimes.
- Cluster the last 252 days of daily volatility (`TA_STDDEV` of ILI changes)
  into 3 regimes using k-means (low/medium/high vol).
- Per-regime, maintain separate percentile thresholds. In a low-vol regime, tighten
  thresholds to 2%/98%; in a high-vol regime, widen to 10%/90%.

**Intraday Proxy Service:**
SOFR is published T+1 at 8:00 AM ET, leaving a gap during intraday trading hours.
To provide continuous signal coverage:

- The system uses **3-month Treasury Bill yields** as a real-time proxy for funding
  pressure between official SOFR updates.
- T-Bill data is fetched via OpenBB `obb.fixedincome.government.treasury_rates`
  (field `month_3`, provider `federal_reserve`). Daily data, no API key cost.
- Proxy-adjusted ILI is calculated intraday using the T-Bill proxy in place of the
  SOFR-derived component.
- Signals generated using proxy data are flagged with status
  `SPECULATIVE_STALE_MACRO`. Once official SOFR is published, recalculated and
  the flag is cleared or confirmed.

Signal quality filters:

- Look-ahead bias prevention: the rolling window is offset by 1 day — today's ILI
  is calculated from data up to and including yesterday's close.
- Minimum volatility filter: if the ADR (Average Daily Range) of the ILI over the
  last 20 days is below a configurable threshold (`min_ili_adr`), suppress signals.
- Cooldown period: after a signal fires, no new signal of the same direction for
  `cooldown_period` (default 4 hours).

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

| KPI Name                   | Source                               | Calculation                                                                                                                                                                     | Output                                              |
|:---------------------------|:-------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------|
| **Liquidity Stress Index** | Composite + TA-Lib                   | `(SOFR - Fed_Target_Upper) / (Fed_Target_Upper - Fed_Target_Lower)`, z-scored over 60 days via `TA_SMA` / `TA_STDDEV`. Adjusted by T-Bill proxy intraday.                       | Scalar: positive = stress                           |
| **Repo/Equity Beta**       | TA-Lib                               | `TA_BETA(stock_return, repo_rate_change, window=60d)` for rolling beta. Full OLS statistics (r_squared, std_error) from analytics worker via `obb.econometrics.ols_regression`. | `{ beta, std_error, r_squared, window }`            |
| **RRP Drain Velocity**     | OpenBB (FRED)                        | `(RRP_t - RRP_t-N) / N`, N=20 days. 2nd derivative for acceleration. `TA_LINEARREG_SLOPE` for trend.                                                                            | `{ velocity_b_per_day, acceleration, period_days }` |
| **Systemic Risk Heatmap**  | OpenBB (Fed Reserve + FRED) + TA-Lib | 4-axis z-scored matrix via `TA_SMA`/`TA_STDDEV`: (1) tri-party vs GCF spread, (2) SOFR 99th-25th pctl, (3) TGCR vs BGCR spread, (4) TGA balance change rate.                    | JSON matrix + quadrant labels                       |
| **Volatility Regime**      | TA-Lib                               | `TA_BBANDS` on ILI series. Bandwidth = (upper - lower) / middle. Regime classification: low (< 0.5x median bandwidth), normal, high (> 2x median bandwidth).                    | `{ regime, bandwidth, percentile }`                 |

---

### 4. Data Source Details

#### Primary Data Path: OpenBB Platform (User-Managed Sidecar)

| Source           | Data Points                | OpenBB Endpoint                                                   | Provider            | Frequency          |
|:-----------------|:---------------------------|:------------------------------------------------------------------|:--------------------|:-------------------|
| **FRED**         | EFFR                       | `/api/v1/economy/fred_series?symbol=EFFR`                         | `fred`              | Daily              |
| **FRED**         | RRP                        | `/api/v1/economy/fred_series?symbol=RRPONTSYD`                    | `fred`              | Daily              |
| **FRED**         | TGA Balance                | `/api/v1/economy/fred_series?symbol=WTREGEN`                      | `fred`              | Daily              |
| **FRED**         | Fed Balance Sheet          | `/api/v1/economy/fred_series?symbol=WALCL`                        | `fred`              | Weekly (Thu)       |
| **FRED**         | IORB                       | `/api/v1/economy/fred_series?symbol=IORB`                         | `fred`              | Daily              |
| **Fed Reserve**  | SOFR (all pctl)            | `/api/v1/fixedincome/rate/sofr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET |
| **Fed Reserve**  | TGCR                       | `/api/v1/fixedincome/rate/tgcr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET |
| **Fed Reserve**  | BGCR                       | `/api/v1/fixedincome/rate/bgcr`                                   | `federal_reserve`   | Daily, T+1 8 AM ET |
| **Fed Reserve**  | Treasury rates (3m T-Bill) | `/api/v1/fixedincome/government/treasury_rates` (field `month_3`) | `federal_reserve`   | Daily, no API key  |
| **FMP/Intrinio** | Equity aggregates (1d)     | `/api/v1/equity/price/historical`                                 | `fmp` or `intrinio` | Daily              |

#### Direct Data Path: Polygon.io WebSocket (Application-Managed)

| Source      | Data Points     | Connection                             | Frequency                |
|:------------|:----------------|:---------------------------------------|:-------------------------|
| **Polygon** | Real-time ticks | `wss://socket.polygon.io/...` (direct) | Real-time (market hours) |

#### In-Process Computation: TA-Lib Java

| Function         | Computation                 | Overhead          |
|:-----------------|:----------------------------|:------------------|
| `TA_SMA`         | Rolling mean                | Zero — in-process |
| `TA_STDDEV`      | Rolling standard deviation  | Zero — in-process |
| `TA_CORREL`      | Rolling Pearson correlation | Zero — in-process |
| `TA_BETA`        | Rolling OLS beta            | Zero — in-process |
| `TA_LINEARREG_*` | Rolling linear regression   | Zero — in-process |
| `TA_BBANDS`      | Bollinger Bands             | Zero — in-process |
| `TA_ROC`         | Rate of change              | Zero — in-process |

#### Cross-Process Computation: Python Analytics Worker

| Function                        | Library                                    | Why Cross-Process                 |
|:--------------------------------|:-------------------------------------------|:----------------------------------|
| ADF unit root test              | OpenBB `obb.econometrics.unit_root`        | No TA-Lib equivalent              |
| Granger causality (AIC loop)    | OpenBB `obb.econometrics.causality`        | No TA-Lib equivalent              |
| Full OLS statistics             | OpenBB `obb.econometrics.ols_regression`   | TA-Lib gives slope/intercept only |
| Bond duration, convexity, YTM   | FinanceToolkit `FixedIncome`               | No TA-Lib/Java equivalent         |
| VaR, CVaR, GARCH                | FinanceToolkit `Risk`                      | No TA-Lib/Java equivalent         |
| Fama-French factor correlations | FinanceToolkit `Performance`               | No TA-Lib/Java equivalent         |
| Sharpe/Sortino validation       | FinanceToolkit + OpenBB `obb.quantitative` | Cross-validation                  |

#### API Tier Requirements

| Source                           | Minimum Tier            | Cost    | Rate Limit             |
|:---------------------------------|:------------------------|:--------|:-----------------------|
| **OpenBB**                       | Free (AGPLv3)           | $0      | N/A — local sidecar    |
| **FRED** (via OpenBB)            | Free (API key)          | $0      | 120 req/min            |
| **Federal Reserve** (via OpenBB) | Free (no key)           | $0      | No documented limit    |
| **FMP** (via OpenBB)             | Starter                 | $14/mo  | Unlimited REST         |
| **Polygon** (direct WebSocket)   | Starter (real-time WS)  | $199/mo | 5 WS connections       |
| **TA-Lib**                       | Free (BSD-3)            | $0      | N/A — in-process       |
| **FinanceToolkit**               | Free (MIT)              | $0      | N/A — analytics worker |
| **TimescaleDB**                  | Free (Apache 2.0 + TSL) | $0      | N/A — self-hosted      |

#### Missing Data Policy

| Scenario                           | Strategy                                                                                                                                                     |
|:-----------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FRED/NY Fed weekend or holiday gap | TimescaleDB `time_bucket_gapfill()` with `locf()` (last observation carried forward) up to 3 business days. After 3 days, mark `STALE`.                      |
| NY Fed T+1 publication lag         | SOFR used in ILI is always as-of yesterday. Between publications, T-Bill proxy fills the gap.                                                                |
| Polygon WebSocket disconnect       | Auto-reconnect with exponential backoff (1s to 60s max). If disconnected > 5 min, switch to OpenBB equity aggregates.                                        |
| Outlier detection                  | Any rate move > 50 bps/day flagged `SUSPECT`, requires manual confirmation before ILI inclusion.                                                             |
| TimescaleDB unreachable            | Buffer up to 1,000,000 events in-memory. If memory > 80%, overflow to Chronicle Queue. Replay via batched INSERT on reconnect.                               |
| OpenBB sidecar unreachable         | Retry 3x with exponential backoff. If down > 5 min, mark OpenBB-sourced series `UNAVAILABLE`.                                                                |
| Analytics worker unreachable       | TA-Lib covers all critical path computations (correlation, beta, z-score). ADF/Granger degrade gracefully — log warning, use last known stationarity result. |

#### Data Alignment Strategy

All series aligned via TimescaleDB `time_bucket()` to a common daily grid at US equity market close (4:00 PM ET):

- Equity prices from OpenBB: last price before 4:00 PM ET.
- FRED: daily close-aligned by definition.
- NY Fed: SOFR mapped to the prior business day's grid position.
- T-Bill proxy: daily yield from Federal Reserve H.15.
- Polygon ticks: aggregated by continuous aggregates into OHLCV candles, last close before 4:00 PM ET.

---

### 5. Configuration System

YAML-based with schema validation and hot-reloading.

```yaml
monitor:
  concurrency-mode: virtual-threads    # "virtual-threads" or "webflux"

  openbb:
    base-url: "http://localhost:8000"
    analytics-worker-url: "http://localhost:8001"
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  polygon:
    ws-url: "wss://socket.polygon.io/stocks"
    api-key: "${POLYGON_API_KEY}"
    reconnect-backoff-max: "60s"

  talib:
    unstable-period: 0    # TA-Lib unstable period (0 = include all data)

  timescaledb:
    tick-chunk-interval: "1 day"
    compression-after: "7 days"
    retention:
      tick-data: "90 days"
      signal-log: "730 days"    # 2 years
    continuous-aggregates:
      ohlcv-1min:
        refresh-interval: "1 minute"
        start-offset: "3 hours"
        end-offset: "1 minute"
      ohlcv-1h:
        refresh-interval: "1 hour"
        start-offset: "7 days"
        end-offset: "1 hour"
      ohlcv-1d:
        refresh-interval: "1 day"
        start-offset: "infinite"
        end-offset: "1 day"
    ingestion:
      batch-size: 500           # ticks per batched INSERT
      flush-interval: "500ms"   # max wait before flushing partial batch

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
- `chronicle_queue_path` must be a writable directory.
- `openbb.base-url` must be a valid URL.
- `openbb.analytics-worker-url` must be a valid URL.
- `timescaledb.ingestion.batch-size` must be in `[1, 10000]`.
- `timescaledb.tick-chunk-interval` must be a valid PostgreSQL interval.

Invalid configuration fails fast at startup with a clear error message.

#### Hot-Reloading

- Hot-reloadable: `strategies.*`, `ingestion.*`, `watchlists.*`.
- Restart-required: `concurrency-mode`, `openbb.base-url`, `timescaledb.*`, `talib.*`.
- Implementation: Spring Cloud Config with native file backend + `@RefreshScope`.
- Each reload logged to TimescaleDB `config_snapshots` with a before/after diff.

---

### 6. Error Handling and Resilience

| Pattern                    | Tool                                      | Scope                                                                                                                             |
|:---------------------------|:------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------|
| **Circuit Breaker**        | Resilience4j                              | OpenBB sidecar, Polygon WebSocket, analytics worker. Opens after 5 failures, half-opens after 30s.                                |
| **Retry**                  | Resilience4j                              | All external HTTP: 3 retries, exponential backoff (1s, 2s, 4s).                                                                   |
| **Dead Letter Queue**      | TimescaleDB table `ingestion_dlq`         | Failed events stored with full context for replay.                                                                                |
| **Graceful Degradation**   | Application logic                         | OpenBB unreachable → last known values. Polygon WS down → OpenBB aggregates. Analytics worker down → TA-Lib covers critical path. |
| **NaN/Infinity Guard**     | `CalculationGuard`                        | Every KPI passes `Double.isFinite()`. Invalid results logged, replaced with `NaN`.                                                |
| **Zero Variance Guard**    | `NormalizationService` (uses `TA_STDDEV`) | Z-score defaults to `0.0` when `stddev < 0.0001`.                                                                                 |
| **Disk-Backed Overflow**   | Chronicle Queue                           | In-memory buffer > 80% → overflow to disk. Replay via batched INSERT on reconnect.                                                |
| **Backpressure** (WebFlux) | Reactor `onBackpressureBuffer(capacity)`  | Buffer up to `buffer_capacity`, then drop oldest.                                                                                 |
| **Batched Ingestion**      | JDBC batch INSERT                         | Ticks accumulated and flushed in batches of `batch-size` every `flush-interval`. Prevents single-row INSERT overhead.             |

---

### 7. Observability

Metrics (Micrometer + Prometheus endpoint):

- `monitor.ingestion.events.total` — counter, tagged by source (`openbb`, `polygon-ws`).
- `monitor.ingestion.latency` — timer from event receipt to TimescaleDB write.
- `monitor.ingestion.openbb.latency` — timer for OpenBB REST round-trips.
- `monitor.ingestion.openbb.errors.total` — counter of OpenBB call failures.
- `monitor.ingestion.timescaledb.batch.size` — distribution summary of INSERT batch sizes.
- `monitor.calculation.ili.duration` — timer per ILI cycle.
- `monitor.calculation.talib.duration` — timer for TA-Lib function calls.
- `monitor.signal.generated.total` — counter, tagged by direction and status.
- `monitor.datasource.health` — gauge (1=healthy, 0=circuit open).
- `monitor.buffer.utilization` — gauge of in-memory buffer fill %.
- `monitor.buffer.overflow.to_disk.total` — counter of events spilled to Chronicle Queue.
- `monitor.analytics.worker.latency` — timer for Python analytics worker calls.

Health Checks (Spring Actuator):

- `/actuator/health/openbb` — OpenBB sidecar connectivity.
- `/actuator/health/analytics-worker` — Python analytics worker connectivity.
- `/actuator/health/timescaledb` — standard DataSource health + hypertable status.
- `/actuator/health/polygon` — WS connected or last tick < 60s.
- `/actuator/health/chronicle` — overflow queue depth and disk space.

Self-Monitoring:

- No signals in 30 days: log `SIGNAL_SILENCE_WARNING`.
- No ILI recalculation in > 2x interval: log `CALCULATION_STALL`.
- Chronicle Queue depth > 50% of disk quota: log `OVERFLOW_QUEUE_GROWING`.
- OpenBB sidecar unreachable > 5 min: log `OPENBB_SIDECAR_DOWN`.
- Analytics worker unreachable > 5 min: log `ANALYTICS_WORKER_DOWN`.
- TimescaleDB compression lag > 2 days: log `COMPRESSION_LAG`.

---

### 8. Security

| Concern              | Implementation                                                                               |
|:---------------------|:---------------------------------------------------------------------------------------------|
| **API Keys**         | Environment variables or HashiCorp Vault, never in YAML. OpenBB credentials managed by user. |
| **Polygon API Key**  | `${POLYGON_API_KEY}` in application config. Direct WebSocket only.                           |
| **WebSocket Auth**   | Polygon API key in connection handshake.                                                     |
| **Frontend Auth**    | OAuth2 + PKCE via Spring Security (Auth0/Keycloak). Optional for single-user local deploy.   |
| **API Endpoints**    | All `/api/**` require valid JWT. WebSocket endpoints validate token.                         |
| **DB Credentials**   | Spring Boot via environment variables. Standard PostgreSQL authentication.                   |
| **HTTPS**            | Enforced in prod via `server.ssl` or reverse proxy.                                          |
| **OpenBB Sidecar**   | Localhost or internal network. Restrict via firewall in production.                          |
| **Analytics Worker** | Localhost only. No external access.                                                          |

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
  ├── analytics/             # Python analytics worker (ADF, Granger, FinanceToolkit)
  ├── persistence/           # TimescaleDB repositories (single database)
  ├── web/                   # REST controllers, WebSocket handlers
  ├── webflux/               # WebFlux-specific implementations (Mode B)
  ├── app-mvc/               # Spring MVC + Virtual Threads entrypoint (Mode A)
  ├── app-webflux/           # WebFlux entrypoint (Mode B)
  ├── frontend/              # Next.js 15 application
  └── integration-tests/     # Cross-module integration tests
  ```
- [ ] Add TA-Lib Java source as a Gradle/Maven dependency (pure Java JAR, no native binaries).
- [ ] Implement `TalibAdapter` — thin wrapper adapting TA-Lib's array-based API to the
  project's domain types (handles array allocation, lookback periods, unstable periods).
- [ ] Implement `OpenBBIngestionClient` — shared HTTP client for all OpenBB REST endpoints.
  Dual-mode: Mode A uses `RestClient` on virtual threads, Mode B uses `WebClient`.
- [ ] Implement `OpenBBAnalyticsClient` — HTTP client for the Python analytics worker.
- [ ] Configure Flyway for TimescaleDB migrations (single target, including hypertable
  creation, continuous aggregate definitions, retention policies, compression policies).
- [ ] Implement Python analytics worker:
    - FastAPI application accepting JSON/Arrow IPC payloads.
    - OpenBB SDK: `obb.econometrics.unit_root`, `obb.econometrics.ols_regression`,
      `obb.econometrics.causality`, `obb.econometrics.correlation_matrix`,
      `obb.quantitative.sharpe_ratio`, `obb.quantitative.sortino_ratio`.
    - FinanceToolkit: `FixedIncome` (duration, convexity, YTM), `Risk` (VaR, CVaR, GARCH),
      `Performance` (Fama-French factors).
    - Returns structured JSON results.
- [ ] Implement **Cross-Stack Consistency Test Suite** scaffold.
- [ ] Set up CI/CD pipeline (GitHub Actions): build, test, lint, Docker build.

### Phase 1: Ingestion and Resilience Layer

- [ ] Implement `OpenBBIngestionClient` (dual-mode):
    - Mode A: `@Scheduled` + `RestClient` on virtual threads.
    - Mode B: `Flux.interval()` + `WebClient`.
    - Endpoints: FRED series, SOFR/TGCR/BGCR, Treasury rates, equity prices.
    - Deduplication: `INSERT ... ON CONFLICT DO NOTHING` on TimescaleDB hypertables.
    - Provider parameter configurable.
- [ ] Implement `PolygonWsClient` (dual-mode, direct connection):
    - Mode A: `java.net.http.HttpClient` WebSocket on virtual threads.
    - Mode B: `reactor-netty` `WebSocketClient`.
    - Auto-reconnect with exponential backoff (1s to 60s max).
    - Fallback to OpenBB equity aggregates after 5 min disconnection.
- [ ] Implement `TimescaleDbWriter`:
    - Accumulates ticks in a thread-safe buffer.
    - Flushes via batched `INSERT` (configurable `batch-size`, default 500) every
      `flush-interval` (default 500ms) using standard PostgreSQL JDBC.
    - Handles connection pool management via HikariCP.
- [ ] Implement `DiskBackedIngestionBuffer`:
    - Tier 1: in-memory ring buffer (capacity from config).
    - Tier 2: Chronicle Queue disk-backed overflow when memory > 80%.
    - Replay via `TimescaleDbWriter` batched INSERT on reconnect.
- [ ] Implement `DataQualityChecker` — staleness, outlier detection.
- [ ] Implement circuit breakers for OpenBB sidecar, Polygon WebSocket, analytics worker.
- [ ] Unit tests with WireMock (OpenBB REST) and mocked WebSocket servers.
- [ ] Integration tests with TimescaleDB (testcontainers).
- [ ] Cross-Stack Consistency tests.

### Phase 2: Analytical Engine

- [ ] Implement `TalibAdapter` — wraps TA-Lib functions for domain use:
    - `computeSma(double[] data, int period)` → `double[]`
    - `computeStdDev(double[] data, int period)` → `double[]`
    - `computeCorrel(double[] x, double[] y, int period)` → `double[]`
    - `computeBeta(double[] market, double[] benchmark, int period)` → `double[]`
    - `computeLinearRegSlope(double[] data, int period)` → `double[]`
    - `computeBollingerBands(double[] data, int period, double devUp, double devDown)` → `BBandsResult[]`
    - `computeRoc(double[] data, int period)` → `double[]`
    - Handles lookback period, array allocation, `TA_SetUnstablePeriod`.
- [ ] Implement `NormalizationService` — z-score over tiered configurable windows
  (252d/60d/20d) using `TalibAdapter.computeSma` and `TalibAdapter.computeStdDev`.
  Edge cases:
  - Insufficient data: return `NaN` with `DATA_INSUFFICIENT` status.
  - Zero variance (`TA_STDDEV` < 0.0001): return `0.0` instead of `NaN`.
- [ ] Implement `IliCalculator` — ILI formula with validated weights, tiered lookback windows,
  data-sufficiency check. Uses `NormalizationService` internally.
- [ ] Implement ADF stationarity check via `OpenBBAnalyticsClient`:
  - Calls `obb.econometrics.unit_root(data, column, regression="c")`.
  - Returns `adfstat` and `pvalue`. If p > 0.05, differenced again with warning.
- [ ] Implement `AicLagSelector` — AIC lag selection loop calling analytics worker
  → `obb.econometrics.causality` for each candidate lag.
- [ ] Implement `CorrelationEngine`:
    - `TA_CORREL` for rolling Pearson correlation (in-process).
    - `TA_BETA` for rolling Repo/Equity Beta (in-process).
    - Full OLS statistics from analytics worker when needed.
    - Data fetched from TimescaleDB via `time_bucket()` aligned queries.
- [ ] Implement `GrangerCausalityTest`:
    - Uses `AicLagSelector` to determine optimal lag.
    - Calls analytics worker → `obb.econometrics.causality(data, y, x, lag=optimal)`.
    - Outputs `{ f_statistic, p_value, lag_order, direction }`.
- [ ] Implement `KpiProcessor` — orchestrates ILI, all KPI calculations, persists to TimescaleDB.
- [ ] Implement `RegimeDetector` — `TA_BBANDS` bandwidth + k-means on ILI volatility.
- [ ] Implement `IntradayProxyService`:
    - T-Bill proxy from OpenBB `treasury_rates.month_3`.
    - Marks signals `SPECULATIVE_STALE_MACRO`.
    - Recalculates with official SOFR once published.
- [ ] Implement `SignalGenerator`:
    - Percentile-rank thresholds, regime-aware.
    - Look-ahead bias prevention (1-day offset).
    - Minimum volatility filter.
    - Cooldown enforcement with optional override.
    - Transaction cost modeling.
    - Intraday proxy awareness.
- [ ] Implement `AlertManager` — evaluates signals, dispatches alerts.
- [ ] Implement `CalculationGuard` — NaN/Infinity/overflow protection.
- [ ] Unit tests (given-when-then):
    - Given SOFR spike, When correlation engine runs (`TA_CORREL`), Then beta and p_value update.
    - Given zero-variance input, When z-score computed, Then result is `0.0` (not `NaN`).
    - Given < 10 data points, When ILI computed, Then status is `DATA_INSUFFICIENT`.
    - Given signal during cooldown, When override disabled, Then signal is `COOLDOWN`.
    - Given cost exceeds expected move, When signal evaluated, Then `COST_EXCEEDS_EXPECTED_MOVE`.
    - Given signal using T-Bill proxy, When SOFR not yet published, Then `SPECULATIVE_STALE_MACRO`.
    - Given AIC lag selection, When multiple lags tested, Then optimal lag minimizes AIC.
    - Given analytics worker unreachable, When ADF requested, Then fallback gracefully.
    - Given TA-Lib `TA_CORREL` output, When compared to analytics worker OLS, Then results agree within tolerance.
    - Given `TA_BBANDS` bandwidth, When regime detected, Then classification matches k-means.
- [ ] Property-based tests (jqwik) for ILI: vary weights and inputs, verify finite output.
- [ ] Cross-Stack Consistency tests.

### Phase 3: Frontend and Visualization

- [ ] Next.js 15 dashboard with React Query (TanStack Query).
- [ ] Real-time updates via WebSocket to `/ws/signals` and `/ws/prices`.
- [ ] Multi-pane Lightweight Charts:
    - Top pane: equity price (candlestick from TimescaleDB continuous aggregates).
    - Bottom pane: ILI overlay with buy/sell signal markers.
    - Signal markers distinguish `ACTIONABLE` vs `SPECULATIVE_STALE_MACRO`.
- [ ] Correlation matrix (D3.js):
    - Heatmap: rolling correlation (`TA_CORREL`) between funding metrics and equities.
    - Tooltip with p-value, sample size, AIC-selected lag order.
- [ ] Liquidity Heatmap (D3.js): 4-axis quadrant from Systemic Risk Heatmap KPI.
- [ ] KPI dashboard cards:
    - Current ILI with historical sparkline.
    - Liquidity Stress Index gauge.
    - Repo/Equity Beta table per watched symbol.
    - RRP Drain Velocity trend line.
    - Volatility Regime indicator (from `TA_BBANDS`).
- [ ] Data Freshness panel:
    - Official data vs. T-Bill proxy indicators.
    - OpenBB sidecar connectivity status.
- [ ] System Health panel:
    - TimescaleDB health, compression status, continuous aggregate lag.
    - Circuit breaker status (OpenBB, Polygon WS, analytics worker).
    - Chronicle Queue overflow depth.
- [ ] Configuration editor (admin) with live validation and diff.
- [ ] Component tests with React Testing Library.
- [ ] E2E tests with Playwright.

### Phase 4: Backtesting Framework

- [ ] Implement `HistoricalDataReplay` — loads TimescaleDB historical data, replays through
  computation pipeline at configurable speed.
- [ ] Implement `BacktestEngine`:
    - Takes strategy config and date range.
    - Replays day-by-day, generates signals, tracks simulated portfolio.
    - Outputs: Sharpe ratio, max drawdown, win rate, profit factor, signal frequency.
    - Max drawdown computed via `TA_MAX` / `TA_MIN` on cumulative return series.
- [ ] Implement `WeightOptimizer`:
    - Bayesian optimization over ILI weight space.
    - Fitness evaluation: Sharpe ratio from `TalibAdapter` + cross-validated via
      FinanceToolkit `sharpe_ratio` and OpenBB `obb.quantitative.sharpe_ratio`.
    - Maximizes backtest Sharpe ratio.
- [ ] Backtest report page: equity curve, drawdown chart, signal timeline.
- [ ] Tests: given known data and fixed parameters, verify deterministic output.

### Phase 5: Deployment and Operations

- [ ] Containerization:
    - Multi-stage Dockerfile for backend (Java 25 JDK to JRE image, includes TA-Lib JAR).
    - Dockerfile for frontend (Node.js build to nginx static serving).
    - Dockerfile for analytics worker (Python + OpenBB SDK + FinanceToolkit).
    - `docker-compose.yml` for local dev (backend + frontend + analytics worker +
      TimescaleDB + Chronicle Queue volume mount).
    - `docker-compose.openbb.yml` — reference compose for OpenBB sidecar (user-managed).
- [ ] Environment strategy:
    - dev: local docker-compose + user-managed OpenBB sidecar, Polygon free tier.
    - staging: cloud-deployed, real API keys, TimescaleDB cloud or self-hosted.
    - prod: HA TimescaleDB (managed or replicated), nginx reverse proxy, OpenBB sidecar.
- [ ] CI/CD (GitHub Actions):
    - On PR: build, lint, unit tests, integration tests (TimescaleDB testcontainers),
      cross-stack consistency tests.
    - On merge to main: build, test, Docker push, deploy to staging.
    - Manual promotion: staging to prod.
- [ ] Data retention automation:
    - TimescaleDB `add_retention_policy('tick_data', drop_after => INTERVAL '90 days')`.
    - TimescaleDB `add_retention_policy('signal_log', drop_after => INTERVAL '730 days')`.
    - Compression policy: `add_compression_policy('tick_data', compress_after => INTERVAL '7 days')`.
- [ ] Runbook:
    - OpenBB sidecar setup and troubleshooting.
    - Analytics worker deployment and health-check.
    - Polygon WebSocket outage procedure.
    - TimescaleDB disk space and compression monitoring.
    - Chronicle Queue overflow recovery (drain to TimescaleDB via batched INSERT).
    - ILI weight recalibration from backtest results.
    - Concurrency mode switch procedure.
    - TimescaleDB continuous aggregate refresh troubleshooting.
    - TA-Lib adapter integration guide.

---

## Validation Strategy (Continuous)

### Unit Tests (per module, run on every commit)

- Structure: Given-When-Then, named `givenX_whenY_thenZ`.
- Coverage target: >= 85% on `computation` and `ingestion` modules.
- TA-Lib adapter tests: verify `TA_CORREL`, `TA_BETA`, `TA_SMA`, `TA_STDDEV` against
  known mathematical results (hand-computed or NumPy-validated).
- Edge cases: empty arrays, single-element arrays, constant arrays (zero variance),
  `NaN`/`Infinity` in input.

### Integration Tests (run on PR merge)

- Infrastructure: TimescaleDB testcontainers. WireMock for OpenBB REST.
- End-to-end: inject Polygon tick → TimescaleDB write → continuous aggregate →
  KPI recalculation → signal → WebSocket push.
- Dual-mode: all tests run with both `virtual-threads` and `webflux` profiles.

### Cross-Stack Consistency Tests (run on every PR)

- Replay identical payloads through both modes.
- Assert bit-perfect equality on ILI, correlation, signal states.

### Load Tests (weekly or before release)

- Tool: Gatling or JMeter.
- Scenario: 1000+ ticks/sec sustained for 10 minutes.
- Metrics: CPU %, JVM heap, TimescaleDB INSERT latency, end-to-end latency,
  Chronicle Queue overflow rate.
- Hardware baseline: 4 CPU cores, 8 GB JVM heap, NVMe SSD.
- Pass criteria: mean ingestion latency < 10ms (p99 < 50ms), CPU < 20% on logic layer,
  zero data loss.

### Property-Based Tests (every commit)

- Framework: jqwik.
- ILI invariant: for any valid weights and finite inputs, ILI is always finite.
- Zero-variance invariant: result is `0.0` (not `NaN`).
- Signal invariant: at most one direction per evaluation.
- Correlation invariant: `TA_CORREL` output in `[-1, 1]` for any finite equal-length series.
- AIC invariant: selected lag order in `[1, max_lag_order]`.

---

## Architecture Design Record

All architectural decisions logged in `docs/adr/` using the ADR format:

| ADR     | Title                                                                           | Status   |
|:--------|:--------------------------------------------------------------------------------|:---------|
| ADR-001 | Dual-mode concurrency (Virtual Threads vs WebFlux)                              | Proposed |
| ADR-002 | TimescaleDB as single database replacing QuestDB + PostgreSQL                   | Proposed |
| ADR-003 | Tiered Z-score normalization (252d/60d/20d) for ILI components                  | Proposed |
| ADR-004 | Percentile-rank thresholds over fixed Z-scores                                  | Proposed |
| ADR-005 | AIC-based dynamic lag selection for Granger causality                           | Proposed |
| ADR-006 | Circuit breakers for external API resilience                                    | Proposed |
| ADR-007 | Spring Cloud Config for hot-reloading                                           | Proposed |
| ADR-008 | Backtesting framework for strategy calibration                                  | Proposed |
| ADR-009 | Chronicle Queue for disk-backed ingestion overflow                              | Proposed |
| ADR-010 | Intraday T-Bill proxy for SOFR gap-filling                                      | Proposed |
| ADR-011 | Cross-Stack Consistency Test Suite for dual-mode parity                         | Proposed |
| ADR-012 | OpenBB Platform as user-managed data gateway sidecar                            | Proposed |
| ADR-013 | Python analytics worker for OpenBB SDK + FinanceToolkit                         | Proposed |
| ADR-014 | Direct Polygon WebSocket bypassing OpenBB for real-time ticks                   | Proposed |
| ADR-015 | Provider-agnostic equity data via OpenBB (FMP/Intrinio/Tiingo)                  | Proposed |
| ADR-016 | TA-Lib Java for in-process rolling statistics and technical indicators          | Proposed |
| ADR-017 | TimescaleDB continuous aggregates for automatic OHLCV generation                | Proposed |
| ADR-018 | FinanceToolkit for bond math, risk metrics, and Fama-French analysis            | Proposed |
| ADR-019 | Graceful degradation: TA-Lib covers critical path when analytics worker is down | Proposed |

---

## Appendix A: OpenBB Platform Reference

### Verified Endpoint Capabilities (as of OpenBB v4.7.1)

#### `obb.econometrics.causality`

| Field       | Value                                                                                                                                                 |
|:------------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| Parameters  | `data: list[Data]`, `y_column: str`, `x_column: str`, `lag: PositiveInt = 3`                                                                          |
| AIC support | No — single lag only, no automatic selection                                                                                                          |
| Returns     | Dict of 4 test variants (ssr_ftest, ssr_chi2test, lrtest, params_ftest), each with `{"F-test": float, "P-value": float, "Count": int, "Lags": float}` |
| REST API    | Available                                                                                                                                             |

#### `obb.econometrics.unit_root`

| Field      | Value                                                                            |
|:-----------|:---------------------------------------------------------------------------------|
| Parameters | `data: list[Data]`, `column: str`, `regression: Literal["c", "ct", "ctt"] = "c"` |
| Returns    | `adfstat: float`, `pvalue: float`, `usedlag: int`, `nobs: int`, `icbest: float`  |
| REST API   | Available                                                                        |

#### `obb.econometrics.ols_regression`

| Field      | Value                                                       |
|:-----------|:------------------------------------------------------------|
| Parameters | `data: list[Data]`, `y_column: str`, `x_columns: list[str]` |
| Returns    | statsmodel OLS result object (non-serializable)             |
| REST API   | **Not available** — Python SDK only.                        |

#### `obb.fixedincome.government.treasury_rates`

| Field          | Value                                                                               |
|:---------------|:------------------------------------------------------------------------------------|
| Parameters     | `start_date: date`, `end_date: date`, `provider: Literal["federal_reserve", "fmp"]` |
| Granularity    | Daily (business days)                                                               |
| 3-month T-Bill | Field `month_3` — always populated by `federal_reserve` provider                    |
| API key        | Not required for `federal_reserve` provider                                         |

### OpenBB Version History

| Version | Date       | Change                                                            |
|:--------|:-----------|:------------------------------------------------------------------|
| v4.5.0  | 2025-10-08 | OpenBB Hub retired. `fixedincome.sofr` → `fixedincome.rate.sofr`. |
| v4.6.0  | 2026-01-07 | Python 3.9 dropped. Account module removed.                       |
| v4.7.0  | 2026-03-09 | **Polygon provider removed.** Python 3.14 + Pandas 3.0 support.   |
| v4.7.1  | 2026-03-09 | Latest stable.                                                    |

---

## Appendix B: TA-Lib Java Reference

### Key Functions Used

| Function                 | Signature (simplified)                                                                                 | Output                          | Lookback     |
|:-------------------------|:-------------------------------------------------------------------------------------------------------|:--------------------------------|:-------------|
| `TA_SMA`                 | `(int startIdx, int endIdx, double[] in, int optInPeriod)`                                             | `double[] out`                  | `period - 1` |
| `TA_STDDEV`              | `(int startIdx, int endIdx, double[] in, int optInPeriod, double optInNbDev)`                          | `double[] out`                  | `period - 1` |
| `TA_VAR`                 | `(int startIdx, int endIdx, double[] in, int optInPeriod, double optInNbDev)`                          | `double[] out`                  | `period - 1` |
| `TA_CORREL`              | `(int startIdx, int endIdx, double[] inReal0, double[] inReal1, int optInPeriod)`                      | `double[] out`                  | `period - 1` |
| `TA_BETA`                | `(int startIdx, int endIdx, double[] inReal0, double[] inReal1, int optInPeriod)`                      | `double[] out`                  | `period - 1` |
| `TA_LINEARREG_SLOPE`     | `(int startIdx, int endIdx, double[] in, int optInPeriod)`                                             | `double[] out`                  | `period - 1` |
| `TA_LINEARREG_INTERCEPT` | `(int startIdx, int endIdx, double[] in, int optInPeriod)`                                             | `double[] out`                  | `period - 1` |
| `TA_BBANDS`              | `(int startIdx, int endIdx, double[] in, int optInPeriod, double optInNbUp, double optInNbDn, MAType)` | `double[] upper, middle, lower` | `period - 1` |
| `TA_ROC`                 | `(int startIdx, int endIdx, double[] in, int optInPeriod)`                                             | `double[] out`                  | `period - 1` |
| `TA_RSI`                 | `(int startIdx, int endIdx, double[] in, int optInPeriod)`                                             | `double[] out`                  | `period`     |
| `TA_ATR`                 | `(int startIdx, int endIdx, double[] high, double[] low, double[] close, int optInPeriod)`             | `double[] out`                  | `period`     |

### Integration Notes

- TA-Lib Java is pure Java (auto-generated from C source), no JNI, no native binaries.
- All functions operate on `double[]` arrays with `startIdx`/`endIdx` range.
- Each function provides a `*_Lookback(int period)` method to determine valid output range.
- `TA_SetUnstablePeriod(int unstablePeriod)` configures how many initial samples to skip.
- BSD-3 license — fully permissive for commercial use.

---

## Appendix C: FinanceToolkit Reference

### Modules Used in Analytics Worker

#### `FixedIncome` Module

```python
from financetoolkit import FixedIncome

fi = FixedIncome(start_date="2020-01-01")

# SOFR rates
fi.get_sofr_rates()          # SOFR, TGCR, BGCR, OBFR, EFFR

# Treasury rates
fi.get_treasury_rates()      # 13W, 5Y, 10Y, 30Y

# Bond analytics
fi.get_bond_duration(type="modified")    # Modified duration
fi.get_bond_convexity()                  # Convexity
fi.get_bond_yield_to_maturity()          # YTM
fi.get_bond_dv01()                       # Dollar Value of 1bp
```

#### `Risk` Module

```python
from financetoolkit import Risk

risk = Risk(start_date="2020-01-01")

risk.get_value_at_risk(method="historical", confidence=0.95)
risk.get_conditional_value_at_risk(confidence=0.95)
risk.get_garch_volatility()     # GARCH(1,1) volatility forecast
```

#### `Performance` Module

```python
from financetoolkit import Performance

perf = Performance(start_date="2020-01-01")

perf.get_fama_french_model()    # Fama-French 3/5 factor model
perf.get_sharpe_ratio(risk_free_rate=0.05)
perf.get_sortino_ratio(risk_free_rate=0.05)
perf.get_treynor_ratio()
```

### License

MIT License — fully permissive, no restrictions.

---

## Appendix D: TimescaleDB Reference

### Hypertable and Continuous Aggregate Setup

```sql
-- Tick data hypertable
CREATE TABLE tick_data (
    time        TIMESTAMPTZ NOT NULL,
    symbol      TEXT NOT NULL,
    price       DOUBLE PRECISION,
    volume      BIGINT,
    conditions  INTEGER[]
);
SELECT create_hypertable('tick_data', 'time', chunk_time_interval => INTERVAL '1 day');

-- Continuous aggregate: 1-minute OHLCV candles
CREATE MATERIALIZED VIEW ohlcv_1min
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', time) AS minute,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY minute, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1min',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute');

-- Compression policy
ALTER TABLE tick_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('tick_data', compress_after => INTERVAL '7 days');

-- Retention policy
SELECT add_retention_policy('tick_data', drop_after => INTERVAL '90 days');
```

### Key TimescaleDB Functions

| Function                                                       | Purpose                                           |
|:---------------------------------------------------------------|:--------------------------------------------------|
| `time_bucket(interval, time)`                                  | Group timestamps into fixed-width buckets         |
| `time_bucket_gapfill(interval, time, start, end)`              | Same as time_bucket but fills gaps                |
| `locf(value)`                                                  | Last observation carried forward (within gapfill) |
| `candlestick_agg(time, price, volume)`                         | Aggregate into OHLCV candlestick                  |
| `open(candle)`, `high(candle)`, `low(candle)`, `close(candle)` | Extract OHLCV components                          |
| `vwap(candle)`                                                 | Volume-weighted average price                     |
| `first(value, time)`, `last(value, time)`                      | Time-series ordering aggregates                   |

### License

TimescaleDB Community Edition (TSL):

- Free for self-hosted/on-prem use.
- Includes hypertables, continuous aggregates, columnstore compression, retention policies.
- Cannot be sold as a managed service (not relevant for this project's use case).
- Core engine is Apache 2.0.
