# Project Plan: Funding Market Monitor (High-Performance Edition) — v2

## Objective

Create a high-performance application using Java 25 and Spring Boot 3.4+ to monitor funding
market metrics and correlate them with equity price movements via a sophisticated KPI system.

The system supports two interchangeable concurrency models:

- **Dual-Mode Architecture:** Virtual Threads + Spring MVC (blocking) **and** WebFlux (reactive),
  selectable at startup via a configuration flag.

---

## System Architecture

### 1. Tech Stack

| Layer              | Technology                                      | Purpose                                                  |
|:-------------------|:------------------------------------------------|:---------------------------------------------------------|
| **Language**       | Java 25 (LTS)                                   | Virtual Threads, Structured Concurrency, Scoped Values   |
| **Framework**      | Spring Boot 3.4+                                | Auto-configuration, dependency injection, actuator       |
| **Concurrency**    | Mode A: Virtual Threads + Spring MVC (blocking) | Simple blocking I/O with cheap threads                   |
| **Concurrency**    | Mode B: WebFlux + Netty (reactive)              | Event-loop, Flux/Mono, backpressure-aware                |
| **Frontend**       | Next.js 15, TailwindCSS, TypeScript             | Dashboard UI                                             |
| **Charts**         | Lightweight Charts (TradingView)                | Price/KPI time-series panes                              |
| **Charts**         | D3.js                                           | Liquidity heatmaps, correlation matrices                 |
| **Time-Series DB** | QuestDB                                         | Ingestion of ticks, rates, computed KPIs                 |
| **Relational DB**  | PostgreSQL                                      | Configuration, alert rules, signal history (append-only) |
| **Data Feeds**     | FRED, NY Fed, Polygon.io                        | External market and macro data                           |

#### Database Boundary

| Store          | Data                                                                                             | Retention                                        |
|:---------------|:-------------------------------------------------------------------------------------------------|:-------------------------------------------------|
| **QuestDB**    | Raw ticks, rate snapshots, ILI history, Z-score series, correlation outputs                      | Tick data: 90 days; daily aggregates: indefinite |
| **PostgreSQL** | YAML config snapshots, user preferences, alert rules, signal log (append-only), backtest results | Indefinite (with periodic archiving)             |

Schema migrations: Flyway for PostgreSQL, versioned SQL scripts for QuestDB (applied at startup).

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
- WebSocket ingestion of Polygon data uses `java.net.http.HttpClient` with
  virtual threads (one thread per WebSocket connection).
- Structured Concurrency (`java.util.concurrent.StructuredTaskScope`) for
  fan-out calls (e.g., fetch FRED + NY Fed + Polygon in parallel).
- When to use: teams familiar with blocking I/O, simpler stack traces, easier debugging.

**Mode B — WebFlux (reactive):**

- `spring-boot-starter-webflux` on Netty.
- Ingestion uses `Flux.interval()` + `WebClient` for FRED/NY Fed polling.
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

---

### 2. Logic Layer and Algorithms

#### A. Internal Liquidity Index (ILI)

A synthetic KPI calculated as a weighted composite of z-score-normalized components.

Normalization requirement: each input is converted to a z-score over a configurable
lookback window (default 60 trading days) before weighting. This prevents one component
from dominating due to unit scale differences.

```
Z_rrp    = zscore(RRP_Volume, lookback=60d)
Z_spread = zscore(EFFR - IORB, lookback=60d)    # Note: IORB, not IOER
Z_vol    = zscore(SOFR_Volatility, lookback=20d)

ILI = w1 * Z_rrp + w2 * Z_spread - w3 * Z_vol
```

Constraints:

- `w1 + w2 + w3 = 1.0` (validated at startup).
- Default weights: `{ rrp: 0.4, spread: 0.4, vol: 0.2 }`.
- All weights must be in `[0.0, 1.0]`.
- If any component has fewer than 10 data points in the lookback window, ILI is not
  calculated and a `DATA_INSUFFICIENT` status is returned.

Calibration plan: weights are initially set by domain judgment. A quarterly backtest
recalibrates weights by maximizing the Sharpe ratio of ILI-derived signals against
SPY returns.

Historical note: IOER was renamed to IORB (Interest on Reserve Balances) in July 2021.
The FRED series code is `IORB`. The codebase must use `IORB` consistently.

#### B. Correlation Engine (Lead-Lag Analysis)

Algorithm: Rolling Pearson Correlation on differenced (stationary) time series,
with statistical significance testing.

Preprocessing:

1. All raw series are converted to daily changes (first differences or log-returns)
   to ensure stationarity.
2. An Augmented Dickey-Fuller (ADF) test is applied at startup to the most recent
   252-day window. If the ADF test fails (p > 0.05), the series is differenced again
   and a warning is logged.

Correlation calculation:

- Window size: configurable (default 20 trading days), exposed as
  `strategies.liquidity_pivot.correlation_window`.
- QuestDB query uses `SAMPLE BY` to align both series to a daily grid, then a
  rolling `CORR()` function computed in Java (QuestDB lacks a built-in rolling
  correlation aggregate — fetch the window and compute in the service layer).
- Output: for each trading day, produce `{ correlation, p_value, sample_size }`.

Lead-lag analysis:

- Apply Granger Causality Test (bivariate VAR model) with configurable lag order
  (default 5 days) to determine if changes in funding metrics statistically lead
  equity returns.
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

---

### 3. KPI System

| KPI Name                   | Source     | Calculation                                                                                                                        | Output                                              |
|:---------------------------|:-----------|:-----------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------|
| **Liquidity Stress Index** | Composite  | `(SOFR - Fed_Target_Upper) / (Fed_Target_Upper - Fed_Target_Lower)`, z-scored over 60 days                                         | Scalar: positive = stress                           |
| **Repo/Equity Beta**       | Analytical | Rolling OLS: `stock_return = alpha + beta * repo_rate_change + e`. Window: 60d                                                     | `{ beta, std_error, r_squared, window }`            |
| **RRP Drain Velocity**     | FRED       | `(RRP_t - RRP_t-N) / N`, N=20 days. Also compute acceleration (2nd derivative)                                                     | `{ velocity_b_per_day, acceleration, period_days }` |
| **Systemic Risk Heatmap**  | NY Fed     | 4-axis z-scored matrix: (1) tri-party vs GCF spread, (2) SOFR 99th-25th pctl, (3) TGCR vs BGCR spread, (4) TGA balance change rate | JSON matrix + quadrant labels                       |

---

### 4. Data Source Details

| Source      | Data Points           | Frequency          | Series / Endpoint              | Known Gaps                  |
|:------------|:----------------------|:-------------------|:-------------------------------|:----------------------------|
| **FRED**    | EFFR                  | Daily              | `EFFR`                         | Weekends, Fed holidays      |
| **FRED**    | RRP                   | Daily              | `RRPONTSYD`                    | Weekends, Fed holidays      |
| **FRED**    | TGA Balance           | Daily              | `WTREGEN`                      | Occasional 1-day delays     |
| **FRED**    | Fed Balance Sheet     | Weekly (Thu)       | `WALCL`                        | Thursday only               |
| **FRED**    | IORB                  | Daily              | `IORB`                         | Weekends, Fed holidays      |
| **NY Fed**  | SOFR (all pctl)       | Daily, T+1 8 AM ET | `/api/v2/sofr`                 | T+1 lag                     |
| **NY Fed**  | TGCR, BGCR            | Daily, T+1 8 AM ET | `/api/v2/tgcr`, `/api/v2/bgcr` | T+1 lag                     |
| **Polygon** | Tick data             | Real-time WS       | `wss://socket.polygon.io/...`  | Market hours only           |
| **Polygon** | Aggregates (1m/1h/1d) | Polled             | `/v2/aggs/ticker/...`          | 15-min delayed on free tier |

#### API Tier Requirements

| Source      | Minimum Tier            | Cost    | Rate Limit          |
|:------------|:------------------------|:--------|:--------------------|
| **FRED**    | Free (API key)          | $0      | 120 req/min         |
| **NY Fed**  | Free (no key)           | $0      | No documented limit |
| **Polygon** | Starter (real-time WS)  | $199/mo | 5 WS connections    |
| **Polygon** | Basic (aggregates only) | $49/mo  | Unlimited REST      |

#### Missing Data Policy

| Scenario                           | Strategy                                                                                                                      |
|:-----------------------------------|:------------------------------------------------------------------------------------------------------------------------------|
| FRED/NY Fed weekend or holiday gap | Forward-fill up to 3 business days. After 3 days, mark `STALE`, suppress ILI/signal calculations.                             |
| NY Fed T+1 publication lag         | SOFR used in ILI is always as-of yesterday. Dashboard labels it clearly.                                                      |
| Polygon WebSocket disconnect       | Auto-reconnect with exponential backoff (1s, 2s, 4s, ... 60s max). If disconnected > 5 min, switch to REST aggregate polling. |
| Outlier detection                  | Any rate move > 50 bps/day flagged `SUSPECT`, requires manual confirmation before ILI inclusion.                              |
| QuestDB unreachable                | Buffer last 10,000 events in-memory ring buffer. Replay on reconnect.                                                         |
| FRED returns HTTP 503              | Retry 3x exponential backoff, then mark series `UNAVAILABLE` for current cycle. Log alert.                                    |

#### Data Alignment Strategy

All series resampled to a common daily grid aligned to US equity market close (4:00 PM ET):

- Polygon tick/aggregates: last price before 4:00 PM ET.
- FRED: daily close-aligned by definition.
- NY Fed: SOFR mapped to the prior business day's grid position.

---

### 5. Configuration System

YAML-based with schema validation and hot-reloading.

```yaml
monitor:
  concurrency-mode: virtual-threads    # "virtual-threads" or "webflux"

  watchlists:
    - name: "Magnificent 7"
      symbols: [ "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA" ]
    - name: "Broad Market"
      symbols: [ "SPY", "QQQ", "IWM", "DIA" ]

  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      ili_lookback_days: 60
      ili_min_data_points: 10
      z_threshold: 2.0
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
      granger_lag_order: 5
      correlation_significance:
        min_p_value: 0.05
        min_abs_correlation: 0.3
      cost_model:
        estimated_slippage_bps: 5
        commission_per_share: 0.005
        min_move_to_cost_ratio: 2.0

  ingestion:
    fred_sync_interval: "12h"
    nyfed_sync_interval: "6h"
    polygon_ws_enabled: true
    polygon_tier: "starter"
    stale_threshold_days: 3
    outlier_bps_threshold: 50
    reconnect_backoff_max: "60s"
    buffer_capacity: 10000

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

Invalid configuration fails fast at startup with a clear error message.

#### Hot-Reloading

- Hot-reloadable: `strategies.*`, `ingestion.*`, `watchlists.*`.
- Restart-required: `concurrency-mode`, database connection strings, retention policy.
- Implementation: Spring Cloud Config with native file backend + `@RefreshScope` on
  strategy beans. `FileSystemWatcher` detects YAML changes and triggers
  `/actuator/refresh` internally.
- Each reload logged to PostgreSQL with a before/after diff.

---

### 6. Error Handling and Resilience

| Pattern                    | Tool                                     | Scope                                                                                                         |
|:---------------------------|:-----------------------------------------|:--------------------------------------------------------------------------------------------------------------|
| **Circuit Breaker**        | Resilience4j                             | Each external API. Opens after 5 consecutive failures, half-opens after 30s.                                  |
| **Retry**                  | Resilience4j                             | All external HTTP: 3 retries, exponential backoff (1s, 2s, 4s).                                               |
| **Dead Letter Queue**      | PostgreSQL table `ingestion_dlq`         | Failed events stored with full context for replay.                                                            |
| **Graceful Degradation**   | Application logic                        | Polygon WS down > 5 min: switch to REST polling. FRED down: use last known values within stale threshold.     |
| **NaN/Infinity Guard**     | Utility class `CalculationGuard`         | Every KPI passes `Double.isFinite()`. Invalid results logged, replaced with `NaN`, downstream consumers skip. |
| **Backpressure** (WebFlux) | Reactor `onBackpressureBuffer(capacity)` | Buffer up to `buffer_capacity`, then drop oldest with `DROPPED_DUE_TO_BACKPRESSURE` log.                      |

---

### 7. Observability

Metrics (Micrometer + Prometheus endpoint):

- `monitor.ingestion.events.total` — counter, tagged by source.
- `monitor.ingestion.latency` — timer from event receipt to QuestDB write.
- `monitor.calculation.ili.duration` — timer per ILI cycle.
- `monitor.signal.generated.total` — counter, tagged by direction and status.
- `monitor.datasource.health` — gauge (1=healthy, 0=circuit open).
- `monitor.buffer.utilization` — gauge of in-memory buffer fill %.

Logging:

- Structured JSON via Logback + `logstash-logback-encoder`.
- Each entry: `correlationId`, `source`, `timestamp`, `level`, `message`.
- Correlation IDs assigned per data source batch.

Health Checks (Spring Actuator):

- `/actuator/health/questdb` — connect + `SELECT 1`.
- `/actuator/health/postgresql` — standard DataSource health.
- `/actuator/health/fred` — last sync < stale_threshold.
- `/actuator/health/polygon` — WS connected or last tick < 60s.

Self-Monitoring:

- No signals in 30 days: log `SIGNAL_SILENCE_WARNING`.
- No ILI recalculation in > 2x interval: log `CALCULATION_STALL`.
- Dashboard shows system health panel with last-update timestamps.

---

### 8. Security

| Concern            | Implementation                                                                               |
|:-------------------|:---------------------------------------------------------------------------------------------|
| **API Keys**       | Environment variables or HashiCorp Vault, never in YAML. Referenced as `${POLYGON_API_KEY}`. |
| **WebSocket Auth** | Polygon API key in connection handshake (per Polygon protocol).                              |
| **Frontend Auth**  | OAuth2 + PKCE via Spring Security (Auth0/Keycloak). Optional for single-user local deploy.   |
| **API Endpoints**  | All `/api/**` require valid JWT. WebSocket endpoints validate token in handshake.            |
| **DB Credentials** | Spring Boot via environment variables. QuestDB HTTP auth with ILP authentication.            |
| **HTTPS**          | Enforced in prod via `server.ssl` or reverse proxy (nginx/Caddy).                            |

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
  ├── ingestion/             # Data source clients (FRED, NY Fed, Polygon)
  ├── computation/           # KPI calculation, correlation engine, signal generation
  ├── persistence/           # QuestDB + PostgreSQL repositories
  ├── web/                   # REST controllers, WebSocket handlers
  ├── webflux/               # WebFlux-specific implementations (Mode B)
  ├── app-mvc/               # Spring MVC + Virtual Threads entrypoint (Mode A)
  ├── app-webflux/           # WebFlux entrypoint (Mode B)
  ├── frontend/              # Next.js 15 application
  └── integration-tests/     # Cross-module integration tests
  ```
- [ ] Configure Flyway for PostgreSQL migrations.
- [ ] Configure QuestDB schema init scripts.
- [ ] Set up CI/CD pipeline (GitHub Actions): build, test, lint, Docker build.

### Phase 1: Reactive Ingestion Layer

- [ ] Implement `FredClient` (shared interface):
    - Mode A: `@Scheduled` + `RestClient` on virtual threads.
    - Mode B: `Flux.interval()` + `WebClient`.
    - Deduplication: upsert into QuestDB with designated timestamp as unique key.
- [ ] Implement `NyFedClient` (shared interface):
    - Same dual-mode pattern as `FredClient`.
    - Map SOFR to prior business day's grid position.
- [ ] Implement `PolygonClient` (shared interface):
    - Mode A: `java.net.http.HttpClient` WebSocket on virtual threads, blocking receive loop.
    - Mode B: `reactor-netty` `WebSocketClient`, `Flux<WebSocketFrame>` stream.
    - Auto-reconnect with exponential backoff (1s to 60s max).
    - Fallback to REST aggregate polling after 5 min disconnection.
- [ ] Implement `IngestionBuffer` — in-memory ring buffer (capacity from config) for
  resilience during QuestDB outages.
- [ ] Implement `DataQualityChecker` — staleness, outlier detection, schema validation.
- [ ] Implement circuit breakers (Resilience4j) for each external source.
- [ ] Unit tests for each client with WireMock (HTTP) and mocked WebSocket servers.
- [ ] Integration tests with embedded QuestDB (testcontainers) and PostgreSQL (testcontainers).

### Phase 2: Analytical Engine

- [ ] Implement `NormalizationService` — z-score over configurable windows, handles edge
  cases (insufficient data, zero variance returns `NaN`).
- [ ] Implement `IliCalculator` — ILI formula with validated weights, lookback window,
  data-sufficiency check.
- [ ] Implement `AdfTest` — Augmented Dickey-Fuller for stationarity verification.
- [ ] Implement `CorrelationEngine`:
    - Rolling Pearson on differenced series, configurable window.
    - Outputs `{ correlation, p_value, sample_size }` per day.
    - QuestDB fetches aligned window; computation in Java.
- [ ] Implement `GrangerCausalityTest`:
    - Bivariate VAR model, configurable lag order.
    - Outputs `{ f_statistic, p_value, lag_order, direction }`.
- [ ] Implement `KpiProcessor` — orchestrates ILI, all KPI calculations, persists to QuestDB.
- [ ] Implement `RegimeDetector` — k-means on ILI volatility, per-regime thresholds.
- [ ] Implement `SignalGenerator`:
    - Percentile-rank thresholds, regime-aware.
    - Look-ahead bias prevention (1-day offset).
    - Minimum volatility filter.
    - Cooldown enforcement with optional override.
    - Transaction cost modeling.
    - Signal status: `ACTIONABLE`, `COST_EXCEEDS_EXPECTED_MOVE`, `COOLDOWN`, `INSUFFICIENT_DATA`.
- [ ] Implement `AlertManager` — evaluates signals against active config, dispatches
  alerts (WebSocket push, optionally email/webhook).
- [ ] Implement `CalculationGuard` — NaN/Infinity/overflow protection on all outputs.
- [ ] Unit tests (given-when-then):
    - Given SOFR spike, When correlation engine runs, Then beta and p_value update correctly.
    - Given zero-variance input, When z-score computed, Then result is NaN.
    - Given < 10 data points, When ILI computed, Then status is DATA_INSUFFICIENT.
    - Given signal during cooldown, When override disabled, Then signal is COOLDOWN.
    - Given cost exceeds expected move, When signal evaluated, Then status is COST_EXCEEDS_EXPECTED_MOVE.
- [ ] Property-based tests (jqwik) for ILI: vary weights and inputs, verify output is
  bounded and finite for all valid inputs.

### Phase 3: Frontend and Visualization

- [ ] Next.js 15 dashboard with React Query (TanStack Query) for server state.
- [ ] Real-time updates via WebSocket to `/ws/signals` and `/ws/prices`.
- [ ] Multi-pane Lightweight Charts:
    - Top pane: equity price (candlestick).
    - Bottom pane: ILI overlay (line) with buy/sell signal markers.
    - Synchronized crosshair and time-scale.
- [ ] Correlation matrix (D3.js):
    - Heatmap: rolling correlation between each funding metric and each watched equity.
    - Color: red (negative) to white (zero) to green (positive).
    - Tooltip with p-value and sample size.
- [ ] Liquidity Heatmap (D3.js):
    - 4-axis quadrant chart from Systemic Risk Heatmap KPI.
    - Animated transitions on data updates.
- [ ] KPI dashboard cards:
    - Current ILI with historical sparkline.
    - Liquidity Stress Index gauge.
    - Repo/Equity Beta table per watched symbol.
    - RRP Drain Velocity trend line.
- [ ] System Health panel:
    - Last-update timestamps per data source.
    - Circuit breaker status indicators.
    - Calculation staleness warnings.
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
    - Grid search or Bayesian optimization over ILI weight space.
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
    - `docker-compose.yml` for local dev (backend + frontend + QuestDB + PostgreSQL).
- [ ] Environment strategy:
    - dev: local docker-compose, Polygon free tier.
    - staging: cloud-deployed, real API keys, full data sync.
    - prod: HA PostgreSQL (managed), QuestDB with replication, nginx reverse proxy.
- [ ] CI/CD (GitHub Actions):
    - On PR: build, lint, unit tests, integration tests (testcontainers).
    - On merge to main: build, test, Docker push, deploy to staging.
    - Manual promotion: staging to prod.
- [ ] Data retention automation:
    - QuestDB partition drop cron for tick data older than `retention.tick_data_days`.
    - PostgreSQL archiving job for signal log entries older than 2 years.
- [ ] Runbook:
    - Polygon WebSocket outage procedure.
    - QuestDB disk space alert procedure.
    - ILI weight recalibration from backtest results.
    - Concurrency mode switch procedure.

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

### Integration Tests (run on PR merge)

- Infrastructure: Testcontainers for QuestDB and PostgreSQL. WireMock for FRED/NY Fed.
- End-to-end flow: inject Polygon tick via test WebSocket, verify QuestDB write, verify
  KPI recalculation, verify signal (or no-signal), verify WebSocket push to frontend.
- Dual-mode coverage: all integration tests run with both `virtual-threads` and `webflux`
  profiles.

### Load Tests (weekly or before release)

- Tool: Gatling or JMeter.
- Scenario: 1000+ ticks/sec sustained for 10 minutes.
- Metrics: CPU %, JVM heap, QuestDB write latency, end-to-end tick-to-dashboard latency.
- Hardware baseline: 4 CPU cores, 8 GB JVM heap, NVMe SSD for QuestDB.
- Pass criteria: mean ingestion latency < 10ms (p99 < 50ms), CPU < 20% on logic layer,
  zero data loss.

### Property-Based Tests (every commit)

- Framework: jqwik.
- ILI invariant: for any valid weights and finite inputs, ILI is always finite.
- Signal invariant: for any ILI value, at most one signal direction fires per evaluation.
- Correlation invariant: for any two finite series of equal length, correlation in `[-1, 1]`.

---

## Architecture Design Record

All architectural decisions logged in `docs/adr/` using the ADR format:

| ADR     | Title                                              | Status   |
|:--------|:---------------------------------------------------|:---------|
| ADR-001 | Dual-mode concurrency (Virtual Threads vs WebFlux) | Proposed |
| ADR-002 | QuestDB for time-series, PostgreSQL for relational | Proposed |
| ADR-003 | Z-score normalization for ILI components           | Proposed |
| ADR-004 | Percentile-rank thresholds over fixed Z-scores     | Proposed |
| ADR-005 | Granger causality for lead-lag analysis            | Proposed |
| ADR-006 | Circuit breakers for external API resilience       | Proposed |
| ADR-007 | Spring Cloud Config for hot-reloading              | Proposed |
| ADR-008 | Backtesting framework for strategy calibration     | Proposed |
