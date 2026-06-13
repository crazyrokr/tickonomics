# Track 4: Ingestion & Resilience Layer

**Phase:** Phase 1
**Can start:** After Track 2 (database schema exists)
**Blocks:** Track 10 (Demo/Virtual Portfolio needs live ingestion)
**Depends on:** Tracks 1, 2

---

## Objective

Implement the data ingestion pipeline with Virtual Threads + Spring MVC, resilience patterns
(circuit breaker, retry, DLQ), and disk-backed overflow on dedicated storage. All ingestion
routes data into TimescaleDB via batched writes.

**Analysis findings applied (v1):**

- **Finding 1 (Virtual Threads Primary):** Single-mode implementation using Virtual Threads.
  No dual-mode parity. Client interfaces are defined without coupling to a specific HTTP
  library so WebFlux adapters can be added later in `webflux/` module.
- **Finding 5 (Direct FRED/NY Fed Clients):** Core ILI data sources fetched via direct
  lightweight Java HTTP clients. All paid data dependencies replaced with free alternatives
  (Yahoo Finance, Finnhub, Alpha Vantage, DataHub, Ken French, Fed RSS). No OpenBB sidecar.
- **Finding 7 (Chronicle Queue Placement):** Overflow queue mapped to dedicated storage
  (`tmpfs` or separate NVMe partition) to prevent I/O contention with TimescaleDB.

**Improvement proposals (v3):**

- **Proposal #2 (Wait-Free Ingestion):** `TimescaleDbWriter` uses off-heap, GC-friendly buffer
  patterns to prevent latency spikes during market events. Chronicle Queue configured with
  explicit memory-mapped file mode on the dedicated NVMe partition.
- **Proposal #5 (CDM Adapter):** Each ingestion client produces CDM-typed objects via the
  `CdmAdapter` layer defined in Track 1. Raw source types never leave the ingestion module.

**Consolidated proposal additions (v4):**

- **Proposal 01 (Data Quality — Anomaly Detection Sidecar):** Async autoencoder-based anomaly
  detection integrated into `DataQualityChecker`. Flags corrupted data as `SUSPECT_ANOMALY`
  without blocking the ingestion pipeline. Falls back to threshold checks when the analytics
  worker is unavailable.
- **Proposal 02 (Resilience — Idempotency, LKG Cache, Bulkheads, Tracing):** UUID idempotency
  keys on every external request and message. Last Known Good cache serves stale data with
  `X-Data-Age: STALE` header when circuit breakers open. Bulkhead executor isolation separates
  critical ingestion, high-volume WS, and computation into dedicated pools. OpenTelemetry
  distributed tracing for end-to-end latency visibility.
- **Proposal 03 (Regime Detection — Event-Based Time, Natural Disaster Shocks):**
  `EventBasedTimeConverter` maps raw ticks into directional change and overshoot events.
  `DisasterAlertClient` polls USGS and GDACS feeds for real-time natural disaster alerts
  that trigger `EXOGENOUS_SHOCK` regime overrides.

**Consolidated proposal additions (v5):**

- **Proposal 05 (Backtesting Robustness — De-Rounding, Periodicity):** `DeRoundingFilter`
  smooths volume spikes at round time marks (9:45, 10:00, 10:05) by interpolating with
  adjacent intervals. `TimePeriodicityFilter` detects and smooths 1-second granular trade/quote
  spikes driven by algorithmic loops, using time-weighted averaging of adjacent non-spike periods.
- **Proposal 06 (Risk Guardrails — AlgorithmicSanityGuard, Options Data, Global Safe Mode):**
  `AlgorithmicSanityGuard` detects extreme conditions (price > 10% movement in < 1s) and forces
  system into Manual Oversight state. Options OI/IV data ingestion via Yahoo Finance options endpoint.
  Global Safe Mode throttles `TimescaleDbWriter` and stops signal dispatch on correlated degradation.
- **Proposal 07 (Liquidity — ToxicityMonitor, Economic Calendar, OrderCancellationMonitor, OFI):**
  `ToxicityMonitor` computes per-venue toxicity scores from order-to-trade ratios and round-trip
  percentages. Economic calendar client polls high-impact news event schedules. `EquityWsClient`
  (Finnhub) extended to capture Order Book Depth for real-time OFI calculation. `OrderCancellationMonitor`
  tracks cancellation ratios per trader type estimate.

---

## Components to Implement

### 1. Direct FRED Client (Finding 5)

Lightweight Java HTTP client for FRED REST API — no OpenBB dependency.

**Implementation:**

- `@Scheduled` with `SimpleAsyncTaskExecutor` configured for virtual threads.
- Uses `java.net.http.HttpClient` with `StructuredTaskScope` for parallel series fetches.
- Direct FRED API:
  `https://api.stlouisfed.org/fred/series/observations?series_id={symbol}&api_key={key}&observation_start={date}&file_type=json`
- **Output mapped to CDM (v3):** Raw `FredObservation` passed through `FredCdmAdapter` to
  produce `CdmRateSnapshot`. Downstream components receive only CDM types.
- **Idempotency key (v4):** Every FRED request generates a UUID idempotency key. Key is
  propagated to `TimescaleDbWriter` for duplicate suppression during retries.

**Series to poll:**

| FRED Series ID | Data                         | Frequency    |
|:---------------|:-----------------------------|:-------------|
| `EFFR`         | Effective Federal Funds Rate | Daily        |
| `RRPONTSYD`    | Reverse Repo Facility        | Daily        |
| `WTREGEN`      | TGA Balance                  | Daily        |
| `WALCL`        | Fed Balance Sheet            | Weekly (Thu) |
| `IORB`         | Interest on Reserve Balances | Daily        |
| `DGS1MO`       | 1-Month Treasury              | Daily        |
| `DGS3MO`       | 3-Month Treasury              | Daily        |
| `DGS6MO`       | 6-Month Treasury              | Daily        |
| `DGS1`         | 1-Year Treasury               | Daily        |
| `DGS2`         | 2-Year Treasury               | Daily        |
| `DGS5`         | 5-Year Treasury               | Daily        |
| `DGS10`        | 10-Year Treasury              | Daily        |
| `DGS30`        | 30-Year Treasury              | Daily        |

### 2. Direct NY Fed Client (Finding 5)

Lightweight Java HTTP client for New York Fed API — no OpenBB dependency.

**Implementation:**

- Same scheduling approach as FRED client.
- NY Fed API: `https://markets.newyorkfed.org/api/{rate_type}/latest.json`
- Supported rate types: `sofr`, `tgcr`, `bgcr`
- **Output mapped to CDM (v3):** Raw `NyFedRateResponse` passed through `NyFedCdmAdapter`
  to produce `CdmRateSnapshot`.
- **Idempotency key (v4):** Every NY Fed request generates a UUID idempotency key.

**Series to poll:**

| Rate                       | Endpoint                     | Frequency           |
|:---------------------------|:-----------------------------|:--------------------|
| SOFR (all percentiles)     | `/api/sofr/latest.json`      | Daily (T+1 8 AM ET) |
| TGCR                       | `/api/tgcr/latest.json`      | Daily (T+1 8 AM ET) |
| BGCR                       | `/api/bgcr/latest.json`      | Daily (T+1 8 AM ET) |
| Treasury rates (3m T-Bill) | `/api/rates/all/latest.json` | Daily               |

### 3. Equity Price Clients — Yahoo Finance + Finnhub (v6 Free Source Migration)

All paid Polygon equity data replaced with free Yahoo Finance (primary) and Finnhub (fallback)
REST clients. No OpenBB sidecar dependency.

**Implementation:**

- `EquityPriceClient` interface with two implementations.
- `YahooFinanceClient` — primary: `RestClient` for Yahoo Finance v8 chart endpoint. No API key
  required (session cookie-based). Historical OHLCV (1m–1mo intervals) and real-time price quotes.
  Rate limit: ~2,000 req/hour (unofficial).
- `FinnhubEquityClient` — fallback: `RestClient` for Finnhub REST API (`/v1/quote`, `/stock/candle`).
  Free API key required. Rate limit: 60 calls/min. Activates when Yahoo circuit breaker opens.
- `EquityPriceScheduler` — `@Scheduled` with virtual threads, 6h polling interval for historical,
  configurable for intraday.
- Circuit breaker on Yahoo opens after 5 failures; Finnhub takes over automatically.

**CDM adapters:**

- `YahooEquityCdmAdapter` — maps Yahoo OHLCV response → `CdmTick`.
- `FinnhubEquityCdmAdapter` — maps Finnhub quote/candle → `CdmTick`.
- Downstream components receive only CDM types — no source-specific types leak.

**Symbols to poll:**

| Symbol | Type | Purpose                          |
|:-------|:-----|:---------------------------------|
| SPY    | ETF  | S&P 500 proxy                    |
| QQQ    | ETF  | Nasdaq 100 proxy                 |
| IWM    | ETF  | Russell 2000 proxy               |
| TLT    | ETF  | Long-duration Treasury proxy     |
| HYG    | ETF  | High-yield corporate bond proxy  |
| GLD    | ETF  | Gold proxy                       |

**Acceptance criteria:**

- [ ] `YahooFinanceClient` fetches OHLCV for all configured symbols.
- [ ] `FinnhubEquityClient` activates when Yahoo circuit breaker opens.
- [ ] Both clients produce `CdmTick` via adapters — no source-specific types leak.
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`.
- [ ] LKG cache serves stale data with `X-Data-Age: STALE` on source failure.

### 4. Equity WebSocket Client — Finnhub (v6 Free Source Migration)

Direct WebSocket to Finnhub free tier — replacing paid Polygon WebSocket.

**Implementation:**

- `EquityWsClient` interface (renamed from `PolygonWsClient` to be source-agnostic).
- `FinnhubWsClient` implementation — `java.net.http.HttpClient` WebSocket on virtual threads.
- Blocking message handler with virtual thread per message.

**Behavior:**

- Connect to `wss://ws.finnhub.io` with free API key handshake.
- Subscribe to symbols from configured watchlists.
- Auto-reconnect with exponential backoff (1s → 60s max).
- Fallback: if disconnected > 5 min, switch to equity aggregates via Yahoo/Finnhub REST.
- Ticks written to `tick_data` hypertable via `TimescaleDbWriter`.
- **Idempotency key (v4):** Each WebSocket message receives a UUID idempotency key derived
  from the Finnhub trade sequence to ensure exactly-once write semantics.

**Acceptance criteria:**

- [ ] `FinnhubWsClient` connects, subscribes, receives trades, writes to `tick_data`.
- [ ] Auto-reconnect works after disconnect (1s → 60s backoff).
- [ ] `EquityWsClient` interface is source-agnostic (no Finnhub types in method signatures).
- [ ] `WsTickCdmAdapter` produces `CdmTick` from Finnhub trade events.

### 5. TimescaleDB Writer

- Accumulates ticks in a thread-safe buffer.
- **Off-heap buffer (v3, Proposal #2):** Uses `ByteBuffer.allocateDirect()` for the primary
  ingestion ring buffer. Avoids GC pressure during high-frequency tick ingestion. Buffer
  allocated once at startup, reused via ring pointer — no per-tick allocations.
- Flushes via batched `INSERT`:
    - Configurable `batch-size` (default 500).
    - Configurable `flush-interval` (default 500ms).
    - Standard PostgreSQL JDBC with HikariCP connection pool.
- Handles connection pool management.
- Uses `INSERT ... ON CONFLICT DO NOTHING` for deduplication.
- **Idempotency-aware deduplication (v4):** `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`
  replaces the previous generic conflict handler. Every row written includes the UUID
  idempotency key from the originating request or WebSocket message. Prevents duplicate tick
  data from skewing ILI calculations during network retries.
- **GC pause monitoring:** `monitor.buffer.gc.pause_ms` gauge tracks STW pauses during flush.

### 6. Disk-Backed Ingestion Buffer (Finding 7)

- **Tier 1:** In-memory ring buffer (capacity from config, default 1M events). Off-heap allocated (v3).
- **Tier 2:** Chronicle Queue disk-backed overflow when memory > 80%.
- **Storage (Finding 7):** Chronicle Queue path mapped to dedicated storage:
    - Production: separate NVMe partition from TimescaleDB `data` directory.
    - Development: `tmpfs` mount for transient spikes.
    - Configuration: `chronicle_queue_storage_type: "dedicated_nvme"` or `"tmpfs"`.
- **Memory-mapped file mode (v3, Proposal #2):** Chronicle Queue configured with explicit
  `MAPPED_FILE` mode and OS-level page alignment. Ensures consistent write latency during
  ingestion spikes — no GC interference with queue operations.
- Replay via `TimescaleDbWriter` batched INSERT on reconnect.
- Configuration:
    - `monitor.ingestion.buffer_capacity`: 1000000
    - `monitor.ingestion.chronicle_queue_path`: `"./data/overflow"`
    - `monitor.ingestion.chronicle_queue_storage_type`: `"dedicated_nvme"`

### 7. Data Quality Checker

- **Staleness detection:** Mark series `STALE` if no new data > `stale_threshold_days` (default 3).
- **Outlier detection:** Any rate move > `outlier_bps_threshold` (default 50 bps/day) flagged `SUSPECT`.
- Requires manual confirmation before ILI inclusion.
- Missing data gap-filling via `time_bucket_gapfill()` + `locf()` (up to 3 business days).
- **Proxy Divergence Guard (Finding 3):** Monitor T-Bill/SOFR correlation — if
  divergence > 2 std deviations, flag ILI as `DISLOCATED`. See Track 5 for full logic.
- **Async anomaly detection integration (v4):** Sends current data vector to
  `AnomalyDetectionWorker` via non-blocking call. If the autoencoder reconstruction MSE
  exceeds the learned threshold, flags incoming data as `SUSPECT_ANOMALY`. The call must NOT
  block the ingestion pipeline — if the analytics worker is unavailable or the async call
  times out (configurable, default 5s), falls back to traditional threshold-based checks.

### 8. Circuit Breakers (Resilience4j)

| Target                         | Config                                       |
|:-------------------------------|:---------------------------------------------|
| FRED API (direct)              | Opens after 5 failures, half-opens after 30s |
| NY Fed API (direct)            | Opens after 5 failures, half-opens after 30s |
| Yahoo Finance REST              | Opens after 5 failures, half-opens after 30s |
| Finnhub REST (equity fallback)  | Opens after 5 failures, half-opens after 30s |
| Finnhub WebSocket               | Opens after 5 failures, half-opens after 30s |
| Analytics worker               | Opens after 5 failures, half-opens after 30s |
| USGS Earthquake API (v4)       | Opens after 5 failures, half-opens after 60s |
| GDACS RSS feed (v4)            | Opens after 5 failures, half-opens after 60s |
| Options API (Yahoo Finance) (v5)| Opens after 5 failures, half-opens after 30s |
| Economic Calendar API (v5)     | Opens after 5 failures, half-opens after 60s |
| Ken French Data Library (v6)    | Opens after 3 failures, half-opens after 60s |
| Alpha Vantage REST (v6)         | Opens after 5 failures, half-opens after 60s |
| DataHub CSV (v6)                | Opens after 3 failures, half-opens after 120s|
| Fed RSS (v6)                    | Opens after 5 failures, half-opens after 60s |

### 9. Retry Policy (Resilience4j)

- All external HTTP: 3 retries, exponential backoff (1s, 2s, 4s).
- Configurable via `monitor.yahoo-finance.retry-*`, `monitor.finnhub.retry-*`, and `monitor.fred.retry-*`.

---

## New Components (v4)

### 10. Anomaly Detection Sidecar (Proposal 01: Data Quality)

Async autoencoder-based anomaly detection integrated into the ingestion pipeline.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/anomaly/AnomalyDetectionWorker.java`

**Implementation:**

- During ingestion, `DataQualityChecker` sends the current data vector (RRP, SOFR/IORB spread,
  volatility metrics) to the analytics worker asynchronously.
- The Python analytics worker (Track 3) hosts a PyTorch autoencoder trained on historical ILI
  component vectors. It returns a reconstruction MSE.
- If MSE exceeds the learned threshold (e.g., 3x training MSE), flag incoming data as
  `SUSPECT_ANOMALY` before it enters the ILI computation pipeline.
- **Non-blocking requirement:** The call to `AnomalyDetectionWorker` must NOT block the
  ingestion pipeline. Implementation uses `CompletableFuture` with configurable timeout
  (default 5s). If the worker is unavailable or the call times out, proceed with traditional
  threshold checks (`outlier_bps_threshold`, `stale_threshold_days`).
- **Fallback behavior:** When `monitor.anomaly.fallback_to_threshold` is `true` (default),
  the system transparently degrades to threshold-based checks without logging errors on every
  tick. A single `ANOMALY_WORKER_FALLBACK` alert is logged when the worker becomes unreachable.

**Integration:**

- `DataQualityChecker` gains an async call to `AnomalyDetectionWorker`.
- `anomaly_score` column added to `rate_snapshots` and `ili_history` hypertables (Track 2).
- `is_suspect_anomaly` boolean flag stored alongside each data point.

### 11. Idempotency Key Integration (Proposal 02: Resilience — ADOPT)

Exactly-once delivery semantics for all ingestion writes.

**Implementation:**

- Every external API request (FRED, NY Fed, Yahoo, Finnhub) and every WebSocket message carries a
  UUID idempotency key.
- `TimescaleDbWriter` updated to use `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`
  on all write operations. The `idempotency_key` column is added to each ingestion target
  hypertable with a `UNIQUE` constraint (Track 2).
- `IngestionDLQ` schema updated to include `idempotency_key` column. When a message is
  retried from the DLQ, the original idempotency key is preserved to prevent double-writes.

**Rationale:**
Network retries during transient failures can produce duplicate tick data. Idempotency keys
ensure that even if the same FRED observation or Finnhub tick is processed multiple times,
only one row is written to TimescaleDB. This prevents duplicate data from skewing ILI
calculations and producing false signals.

### 12. Last Known Good (LKG) Cache (Proposal 02: Resilience)

In-memory cache for graceful degradation when data sources are unreachable.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/cache/LastKnownGoodCache.java`

**Implementation:**

- Maintains an in-memory cache of the last successfully fetched value for each critical data
  source (FRED, NY Fed, Yahoo Finance, Finnhub).
- Cache entry structure: `{ source_name, last_value, timestamp, staleness_status }`.
- When a circuit breaker opens for a data source, the LKG cache serves the last known good
  value instead of returning an error or empty result.
- All API responses served from the LKG cache include `X-Data-Age: STALE` header so that
  downstream consumers (dashboard, analytics) can make informed decisions about data freshness.
- **Staleness limit:** If cached data exceeds `max_staleness` (default 1 hour), the cache
  entry is marked `EXPIRED` and downstream systems receive an explicit error rather than
  potentially misleading stale data.
- **Eviction:** Cache entries are refreshed on every successful fetch. Entries expire after
  `max_staleness` to prevent indefinitely serving outdated values.

**Rationale:**
Prevents the dashboard and analytics engine from producing NaN or empty results when a data
source is momentarily unreachable. Users and downstream systems can inspect the `X-Data-Age`
header to determine whether to act on stale data. This is particularly important for the ILI
dashboard, which should display a degraded-but-visible indicator rather than a blank panel.

### 13. Bulkhead Executor Isolation (Proposal 02: Resilience)

Dedicated thread pools to prevent cascading starvation.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/bulkhead/IngestionBulkheadConfig.java`

**Implementation:**
Replace the single virtual thread pool with three dedicated executor services:

1. **Critical Ingestion** (FRED/NY Fed) — highest priority, smallest pool. These data sources
   feed the core ILI calculation and must never be starved.
2. **High-Volume Ingestion** (Finnhub WS) — large pool, tolerant of delays. WebSocket tick
   data is high-frequency but individually lower priority than ILI inputs.
3. **Computation Engine** (ILI/Signals) — medium pool, must not be starved. Signal generation
   runs on its own pool to avoid contention with ingestion threads.

**Configuration:**

```yaml
monitor:
  ingestion:
    bulkhead:
      critical-pool-size: 4
      high-volume-pool-size: 16
      computation-pool-size: 8
```

**Rationale:**
If the Finnhub WebSocket stream hangs due to network saturation or reconnection storms, the
ILI calculation and core FRED/NY Fed ingestion threads remain unaffected. Without bulkhead
isolation, a single misbehaving service (e.g., a slow WebSocket reconnection) can starve the
thread pool required for core KPI processing.

### 14. EventBasedTimeConverter (Proposal 03: Regime Detection)

Event-driven alternative to time-based tick aggregation.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/time/EventBasedTimeConverter.java`

**Implementation:**

- Maps raw tick data from Finnhub WebSocket into discrete events: directional changes and overshoots.
- A directional change event is emitted when price moves by a configured threshold (theta)
  from the last extremum. An overshoot event follows when price continues in the same
  direction beyond the initial directional change.
- Event metadata (threshold, duration, magnitude) is stored in the `market_events` hypertable
  (Track 2).
- Provides an event-driven alternative to fixed-time aggregation (e.g., 1-minute bars) that
  is scale-invariant and adaptive to market activity intensity.

**Rationale:**
During volatile events, time-based aggregation may obscure significant signal-generating data.
Intrinsic time makes strategies scale-invariant and adaptive to market-driven event intensity.
The `SurpriseIndicator` in Track 5 consumes these events to detect anomalous price trajectories.

### 15. DisasterAlertClient (Proposal 03: Natural Disaster Exogenous Shock)

Real-time natural disaster alert polling for exogenous shock detection.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/external/DisasterAlertClient.java`

**Implementation:**

- Polls real-time natural disaster alerts from two sources:
    - **USGS Earthquake API:** `https://earthquake.usgs.gov/fdsnws/event/1/query` — filters by
      minimum magnitude (default 5.0) and configurable geographic region.
    - **GDACS (Global Disaster Alert and Coordination System):**
      `https://www.gdacs.org/xml/rss.xml` — RSS feed parsed for earthquake, tsunami, and
      hurricane alerts with severity levels (Green/Orange/Red).
- **News sentiment trigger:** Uses the Finnhub news endpoint (`/api/v1/news?category=general`)
  to search for keywords: `Earthquake`, `Tsunami`, `Hurricane`. When a matching news cluster
  is detected alongside
  a disaster alert, the system triggers the `EXOGENOUS_SHOCK` regime override in Track 5.
- Scheduled polling at configurable interval (default 30s). Uses dedicated virtual thread
  from the critical ingestion pool.
- Alert events stored in `market_events` hypertable with type `DISASTER_ALERT`.
- Circuit breakers protect both USGS and GDACS endpoints independently.

**Configuration:**

```yaml
monitor:
  disaster:
    usgs_enabled: true
    usgs_url: "https://earthquake.usgs.gov/fdsnws/event/1/query"
    gdacs_enabled: true
    gdacs_url: "https://www.gdacs.org/xml/rss.xml"
    poll_interval: "30s"
    min_magnitude: 5.0
```

**Rationale:**
HFT algorithms react to earthquake early warning systems, often leading to rapid micro-crashes
or flight-to-quality movements. Natural disasters are primary drivers of flight-to-quality
events that should be detected as exogenous shocks independent of normal volatility regime
classification. Proactive `DISLOCATED` flagging before the 5-day correlation breaks down
provides a safety margin for the ILI computation.

### 16. OpenTelemetry Tracing Integration (Proposal 02: Resilience)

Distributed tracing for end-to-end latency visibility.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/tracing/IngestionTracingConfig.java`

**Implementation:**

- Add OpenTelemetry SDK to the ingestion module as a dependency.
- Configure OTLP exporter pointing to the tracing backend (Jaeger or Tempo).
- Pass trace IDs in headers/metadata when sending data via Arrow IPC to the Python analytics
  worker. The Python worker propagates the trace context for end-to-end span correlation.
- **Trace spans created for:**
    - FRED fetch (`ingestion.fred.fetch`)
    - NY Fed fetch (`ingestion.nyfed.fetch`)
    - Finnhub WS message processing (`ingestion.finnhub.message`)
    - TimescaleDB batch write (`ingestion.timescaledb.write`)
    - Data quality check (`ingestion.quality.check`)
    - Anomaly detection sidecar call (`ingestion.anomaly.detect`)
    - Disaster alert poll (`ingestion.disaster.poll`)

**Configuration:**

```yaml
monitor:
  tracing:
    enabled: true
    exporter: "otlp"
    endpoint: "http://localhost:4317"
```

**Rationale:**
Identifying which component in the Java-to-Python-to-Java chain is the bottleneck during
high-load market events requires distributed tracing. Standard log aggregation alone cannot
reconstruct the full request path from Finnhub tick ingestion through ILI computation to
signal generation.

### 17. DeRoundingFilter (Proposal 05: Human Bias Mitigation)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/filter/DeRoundingFilter.java`

Smoothing filter for volume spikes at round time marks caused by human programmer bias.

**Implementation:**

- Identifies volume/trade spikes within 30 seconds of round time marks (e.g., :00, :05, :10, :15, :20, :25, :30, :35, :
  40, :45, :50, :55 minute marks).
- For each spike window, replaces the observed value with a time-weighted average of adjacent non-round-mark intervals.
- Applied to intraday volume/trades data before aggregation into 5-minute ILI components.
- Broussard and Nikiforov (2013) show AT activity spikes at round marks introduce noise without providing better
  liquidity.

**Configuration:**

```yaml
monitor:
  ingestion:
    derounding:
      enabled: true
      window_seconds: 30
      round_mark_interval_minutes: 5
```

### 18. TimePeriodicityFilter (Proposal 05: Periodicity Filter)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/filter/TimePeriodicityFilter.java`

Filter for 1-second granular algorithmic loop artifacts.

**Implementation:**

- Detects trade/quote volumes within the 100ms "spike" window of round seconds.
- Adjusts spike values using a time-weighted average of adjacent non-spike periods (100ms before and after).
- "Cleans" high-frequency tick data before it is aggregated into 5-minute ILI components.
- Complements DeRoundingFilter: addresses second-granularity artifacts rather than minute-granularity round-mark spikes.

**Configuration:**

```yaml
monitor:
  ingestion:
    periodicity:
      enabled: true
      spike_window_ms: 100
      smoothing_method: "time_weighted_average"
```

### 19. AlgorithmicSanityGuard (Proposal 06: Auditability)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/guard/AlgorithmicSanityGuard.java`

Extreme condition detection for "fat finger" and runaway algorithm scenarios.

**Implementation:**

- Monitors incoming tick data for extreme conditions:
    - Price movement > `max_price_movement_pct` (default 10%) in < `max_price_movement_window` (default 1s).
    - Message rate > `max_message_rate` (default 10000/sec) sustained for > `burst_window` (default 5s).
- When triggered, forces system into "Manual Oversight" state:
    - All automated signal dispatch halted.
    - Requires manual operator acknowledgment to resume.
- Acts as a global kill-switch for algorithmic trading with configurable thresholds.
- Logs `ALGORITHMIC_SANITY_BREACH` alert with details of triggering condition.

**Configuration:**

```yaml
monitor:
  ingestion:
    sanity_guard:
      enabled: true
      max_price_movement_pct: 10
      max_price_movement_window: "1s"
      max_message_rate: 10000
      burst_window: "5s"
```

### 20. Options Data Client — Yahoo Finance (v6 Free Source Migration)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/options/OptionsDataClient.java`

Options chain data ingestion via Yahoo Finance options endpoint — replacing paid Polygon Options API.

**Implementation:**

- Polls options chain data (strikes, expiry dates, bid/ask, IV, OI, volume, Greeks) via Yahoo
  Finance options endpoint (`/v7/finance/options/{symbol}`).
- No API key required (same session as equity prices).
- Ingests ATM and OTM implied volatilities for BSM Greeks calculation.
- Data stored in `option_chain_snapshots` hypertable via `YahooOptionsCdmAdapter`.
- Separate circuit breaker from equity data sources.
- Scheduled polling at configurable interval (default 60s during market hours, disabled outside).

**Configuration:**

```yaml
monitor:
  ingestion:
    options:
      enabled: false
      symbols: ["SPY", "QQQ"]
      poll_interval: "60s"
      market_hours_only: true
```

**Acceptance criteria:**

- [ ] `YahooOptionsClient` fetches full options chain with Greeks and OI.
- [ ] Options data flows to `option_chain_snapshots` hypertable.
- [ ] ~15 min delay acceptable for GEX monitoring (daily regime detection).

### 21. ToxicityMonitor (Proposal 07: Trader Toxicity)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/quality/ToxicityMonitor.java`

Per-venue toxicity scoring from order flow patterns.

**Implementation:**

- Computes toxicity metrics from Finnhub trade data:
    - Order-to-trade ratio: estimated from message patterns and trade conditions.
    - Intraday round-trip trade percentage: detected from rapid buy-sell sequences.
- Applied per monitored venue/strategy.
- Scores classified as `HARMFUL`, `BENEFICIAL`, or `NEUTRAL`.
- Results stored in `toxicity_scores` table (Track 2).
- Feeds into `ToxicityAdjustedIli` in Track 5.

### 22. Economic Calendar Client (Proposal 07: Intraday Session Filter)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/external/EconomicCalendarClient.java`

High-impact news event schedule polling.

**Implementation:**

- Polls for high-impact news events via FRED's release schedule or a dedicated Economic Calendar API.
- Tags all `tick_data` and `rate_snapshots` with their relative position in the NY trading session:
    - `PRE_OPEN`, `DR_WINDOW` (9:30-10:30 AM EST), `POST_DR`.
- Event metadata stored in `market_events` hypertable with event_type `ECONOMIC_EVENT`.
- Session timestamps used by `SessionRangeService` in Track 5 for signal confidence adjustment.

**Configuration:**

```yaml
monitor:
  ingestion:
    economic_calendar:
      enabled: true
      source: "fred"
      poll_interval: "6h"
      high_impact_only: true
```

### 23. OrderCancellationMonitor (Proposal 07: Trader-Type Dynamics)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/quality/OrderCancellationMonitor.java`

Tracks cancellation ratios as a leading indicator of price discovery.

**Implementation:**

- Monitors the ratio of canceled orders to total orders per symbol.
- High cancellation rates indicate "fishing" for true prices.
- Usable as a leading indicator of increased volatility in the subsequent 10-minute window.
- Requires L2 order book data from Finnhub (if available) or estimated from trade condition patterns.

### 24. Finnhub News Client (v6 Free Source Migration — Phase 3)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/news/FinnhubNewsClient.java`

Market news ingestion via Finnhub free news API for sentiment analysis.

**Implementation:**

- `NewsIngestionClient` interface with `FinnhubNewsClient` implementation.
- Polls market news headlines and summaries via `https://finnhub.io/api/v1/news?category=general`.
- Free API key required (same key as equity client). Rate limit: 60 calls/min.
- Scheduled polling at 6h interval.
- `NewsArticleCdmAdapter` maps news articles to canonical news records.
- News articles stored in `sentiment_history` hypertable with `source_type: MARKET_NEWS`.
- Duplicate articles filtered by `text_hash` (idempotency).
- Feeds `SaliProcessor` (Track 5) for SALI lexicon scoring.

**Acceptance criteria:**

- [ ] `FinnhubNewsClient` fetches market news headlines at configured interval.
- [ ] News articles stored in `sentiment_history` with `source_type: MARKET_NEWS`.
- [ ] Duplicate articles filtered by `text_hash`.
- [ ] SALI lexicon scores general news headlines.

### 25. Federal Reserve RSS Client (v6 Free Source Migration — Phase 3)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/news/FedRSSClient.java`

FOMC statements and Fed speech ingestion via public RSS feeds.

**Implementation:**

- `FedRSSClient` implements `NewsIngestionClient` interface.
- Polls two public RSS feeds:
    - FOMC statements: `https://www.federalreserve.gov/feeds/press_monetary.xml`
    - Fed speeches: `https://www.federalreserve.gov/feeds/speeches.xml`
- No API key required (public RSS). Scheduled polling at 1h interval.
- Full text extraction from RSS entries for FinBERT hawkish/dovish classification.
- News articles stored in `sentiment_history` with `source_type: FOMC` or `FED_SPEAKER`.
- Feeds `SaliProcessor` (Track 5) for sentiment-augmented ILI.

**Acceptance criteria:**

- [ ] `FedRSSClient` parses FOMC statements and Fed speeches from RSS XML.
- [ ] Full text stored in `sentiment_history` with correct `source_type`.
- [ ] FinBERT receives Fed speech text for hawkish/dovish classification.

### 26. Ken French Factor Client (v6 Free Source Migration — Phase 4)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/factor/FrenchFactorClient.java`

Fama-French factor returns ingestion from Ken French Data Library (Dartmouth/Tuck).

**Implementation:**

- `FrenchFactorClient` — HTTP GET ZIP files, unzip, parse CSV.
- `FrenchFactorScheduler` — `@Scheduled` monthly poll (1st of month). Checks for new data rows
  since last import.
- `FrenchFactorCdmAdapter` — CSV row → `FactorReturn` CDM record (date, factor_set, rm_rf, smb,
  hml, rmw, cma, rf, mom).
- Fully public, no API key, no rate limit (static CSV files on CDN).
- Missing values (-99.99, -999) stored as NULL.
- Stores incremental rows in `factor_returns` hypertable (Track 2).

**Datasets to poll:**

| File | Factors | Use Case |
|:-----|:--------|:---------|
| `F-F_Research_Data_Factors_CSV.zip` | Rm-Rf, SMB, HML, RF | 3-factor regression |
| `F-F_Research_Data_5_Factors_2x3_CSV.zip` | Rm-Rf, SMB, HML, RMW, CMA, RF | 5-factor regression |
| `Momentum_Factor_CSV.zip` | Mom (UMD) | Tournament momentum benchmark |

**Consumers:** `fama_french_regression()` (analytics worker), tournament service, risk decomposition,
regime detection.

**Acceptance criteria:**

- [ ] `FrenchFactorClient` downloads and parses 3-factor, 5-factor, and momentum ZIP files.
- [ ] Incremental import detects new rows since last import (no duplicates).
- [ ] Factor returns stored in `factor_returns` hypertable with correct date alignment.
- [ ] Missing values (-99.99, -999) stored as NULL.
- [ ] `fama_french_regression()` supports 5-factor mode (Rm-Rf, SMB, HML, RMW, CMA).

### 27. Alpha Vantage Client (v6 Free Source Migration — Phase 6)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/alphavantage/AlphaVantageClient.java`

Deep historical equity OHLCV (20+ years) and commodity data via Alpha Vantage REST API.

**Implementation:**

- `AlphaVantageClient` — REST client with rate-limit-aware sequential calls (12.1s spacing, max
  5/min on free tier).
- `AlphaVantageScheduler` — `@Scheduled(cron = "0 0 6 * * *")` daily at 06:00 UTC.
- `AlphaVantageCdmAdapter` — adjusted daily OHLCV → `CdmTick`, commodity → `CdmTick`.
- Free API key required (signup at alphavantage.co).
- Incremental fetch: only new rows since last import (tracked per symbol in `data_import_tracker`).
- Initial full load: `outputsize=full` on first run per symbol (20+ year history).
- Resilience4j retry handles transient 429/5xx responses.
- Commodity subtypes: GOLD, OIL, NATURAL_GAS, COPPER via `InstrumentType.COMMODITY`.

**Acceptance criteria:**

- [ ] `AlphaVantageClient` fetches adjusted daily OHLCV for all configured symbols.
- [ ] Adjusted close reflects split and dividend corrections.
- [ ] Commodity daily prices fetched for gold, crude oil, natural gas, copper.
- [ ] Rate limiting enforced: no more than 1 call per 12.1s (≤5/min).
- [ ] Incremental mode fetches only new rows since last import.
- [ ] Initial full load fetches 20+ year history on first run per symbol.

### 28. DataHub Historical Backfill Client (v6 Free Source Migration — Phase 7)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/datahub/DataHubBackfillClient.java`

Deep-historical CSV backfill from DataHub Core Datasets (free, no auth, CDN-hosted).

**Implementation:**

- `DataHubBackfillClient` — HTTP GET CSV from stable r-link URLs with streaming CSV parser.
- `DataHubBackfillScheduler` — startup full-load trigger (if target tables empty) + `@Scheduled(cron)`
  monthly incremental check (1st of month, 02:00 UTC).
- Per-dataset CDM adapters:
    - `ShillerSp500CdmAdapter` → `index_snapshots` (price, dividend, earnings, CPI, CAPE since 1871).
    - `VixCdmAdapter` → `rate_snapshots` with `rate_type='VIX'` (daily since 1990).
    - `OilPriceCdmAdapter` → `rate_snapshots` with `rate_type='OIL_WTI'`/`'OIL_BRENT'` (daily since 1986).
    - `GoldPriceCdmAdapter` → `rate_snapshots` with `rate_type='GOLD'` (monthly since 1833).
- Fully public, no API key, no rate limit (static CSV files served via CDN).
- Incremental import detects new rows since `max(timestamp)` in target table.

**Datasets to backfill:**

| Dataset | Granularity | Coverage | Target Table |
|:--------|:------------|:---------|:-------------|
| S&P 500 (Shiller) | Monthly | 1871–present | `index_snapshots` |
| VIX (CBOE) | Daily | 1990–present | `rate_snapshots` |
| Oil (WTI) | Daily | 1986–present | `rate_snapshots` |
| Oil (Brent) | Daily | 1987–present | `rate_snapshots` |
| Gold | Monthly | 1833–present | `rate_snapshots` |

**Acceptance criteria:**

- [ ] `DataHubBackfillClient` fetches all 5 configured CSV files.
- [ ] S&P 500 Shiller data (1871–present) loaded into `index_snapshots` with CAPE.
- [ ] VIX daily data (1990–present) loaded into `rate_snapshots` with `rate_type='VIX'`.
- [ ] WTI/Brent daily loaded into `rate_snapshots` with correct rate types.
- [ ] Gold monthly (1833–present) loaded into `rate_snapshots` with `rate_type='GOLD'`.
- [ ] Startup detection: full load if empty, incremental otherwise.
- [ ] Incremental import detects new rows — no duplicates.

---

## Configuration

```yaml
monitor:
  fred:
    base-url: "https://api.stlouisfed.org/fred"
    api-key: "${FRED_API_KEY}"
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  nyfed:
    base-url: "https://markets.newyorkfed.org/api"
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  yahoo-finance:
    base-url: "https://query1.finance.yahoo.com"
    poll-interval-ms: 21600000          # 6 hours
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    include-options: true
    options-poll-interval-ms: 3600000   # 1 hour (market hours only)
    options-symbols: ["SPY", "QQQ"]
    connect-timeout: "5s"
    read-timeout: "30s"

  finnhub:
    api-key: "${FINNHUB_API_KEY}"
    ws-url: "wss://ws.finnhub.io"
    rest-url: "https://finnhub.io/api/v1"
    ws-enabled: true
    ws-reconnect-backoff-max: "60s"
    rest-poll-interval-ms: 300000       # 5 min fallback
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    news-enabled: true
    news-poll-interval-ms: 21600000     # 6 hours
    connect-timeout: "5s"
    read-timeout: "30s"

  fed-rss:
    enabled: true
    speeches-url: "https://www.federalreserve.gov/feeds/speeches.xml"
    fomc-url: "https://www.federalreserve.gov/feeds/press_monetary.xml"
    poll-interval-ms: 3600000           # 1 hour

  ken-french:
    enabled: true
    base-url: "https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp"
    poll-interval-ms: 86400000          # 24 hours
    poll-day-of-month: 1
    datasets:
      - name: "3-factor"
        file: "F-F_Research_Data_Factors_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RF"]
      - name: "5-factor"
        file: "F-F_Research_Data_5_Factors_2x3_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RMW", "CMA", "RF"]
      - name: "momentum"
        file: "Momentum_Factor_CSV.zip"
        factors: ["MOM"]
    connect-timeout: "10s"
    read-timeout: "60s"

  alpha-vantage:
    enabled: true
    api-key: "${ALPHAVANTAGE_API_KEY}"
    base-url: "https://www.alphavantage.co/query"
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    commodities: ["GOLD", "OIL", "NATURAL_GAS", "COPPER"]
    fetch-mode: "incremental"
    initial-full-load: true
    rate-limit-delay-ms: 12100
    connect-timeout: "10s"
    read-timeout: "30s"

  datahub:
    enabled: true
    base-url: "https://datahub.io"
    startup-full-load: true
    incremental-check-cron: "0 0 2 1 * *"
    connect-timeout: "10s"
    read-timeout: "60s"
    datasets:
      sp500-shiller:
        enabled: true
        csv-url: "/core/s-and-p-500/_r/-/data/data.csv"
        target-table: "index_snapshots"
      vix:
        enabled: true
        csv-url: "/core/finance-vix/_r/-/data/vix-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "VIX"
      oil-wti:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/wti-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_WTI"
      oil-brent:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/brent-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_BRENT"
      gold:
        enabled: true
        csv-url: "/core/gold-prices/_r/-/data/monthly.csv"
        target-table: "rate_snapshots"
        rate-type: "GOLD"

  timescaledb:
    ingestion:
      batch-size: 500
      flush-interval: "500ms"

  ingestion:
    fred_sync_interval: "6h"
    nyfed_sync_interval: "6h"
    stale_threshold_days: 3
    outlier_bps_threshold: 50
    reconnect_backoff_max: "60s"
    buffer_capacity: 1000000
    chronicle_queue_path: "./data/overflow"
    chronicle_queue_storage_type: "dedicated_nvme"
    idempotency_enabled: true
    bulkhead:
      critical-pool-size: 4
      high-volume-pool-size: 16
      computation-pool-size: 8
    lkg_cache:
      enabled: true
      max_staleness: "1h"

  anomaly:
    enabled: true
    async_timeout: "5s"
    fallback_to_threshold: true

  tracing:
    enabled: true
    exporter: "otlp"
    endpoint: "http://localhost:4317"

  disaster:
    usgs_enabled: true
    usgs_url: "https://earthquake.usgs.gov/fdsnws/event/1/query"
    gdacs_enabled: true
    gdacs_url: "https://www.gdacs.org/xml/rss.xml"
    poll_interval: "30s"
    min_magnitude: 5.0

  derounding:
    enabled: true
    window_seconds: 30
    round_mark_interval_minutes: 5

  periodicity:
    enabled: true
    spike_window_ms: 100
    smoothing_method: "time_weighted_average"

  sanity_guard:
    enabled: true
    max_price_movement_pct: 10
    max_price_movement_window: "1s"
    max_message_rate: 10000
    burst_window: "5s"

  options:
    enabled: false
    symbols: ["SPY", "QQQ"]
    poll_interval: "60s"
    market_hours_only: true

  economic_calendar:
    enabled: true
    source: "fred"
    poll_interval: "6h"
    high_impact_only: true
```

---

## Module Structure

```
ingestion/
├── src/main/java/com/tickonomics/ingestion/
│   ├── fred/
│   │   ├── FredClient.java                        # Direct FRED HTTP client (Finding 5)
│   │   └── FredSeriesConfig.java                  # Series ID to rate_type mapping
│   ├── nyfed/
│   │   └── NyFedClient.java                       # Direct NY Fed HTTP client (Finding 5)
│   ├── adapter/                                       # v3: CDM Adapters (Proposal #5)
│   │   ├── FredCdmAdapter.java                    # FredObservation → CdmRateSnapshot
│   │   ├── NyFedCdmAdapter.java                   # NyFedRateResponse → CdmRateSnapshot
│   │   ├── WsTickCdmAdapter.java                  # FinnhubTrade → CdmTick (v6)
│   │   ├── YahooEquityCdmAdapter.java             # Yahoo OHLCV → CdmTick (v6)
│   │   ├── FinnhubEquityCdmAdapter.java           # Finnhub quote → CdmTick (v6)
│   │   ├── YahooOptionsCdmAdapter.java            # Yahoo options → OptionsChainSnapshot (v6)
│   │   ├── NewsArticleCdmAdapter.java             # News/Fed text → canonical news record (v6)
│   │   ├── FrenchFactorCdmAdapter.java            # Ken French CSV → FactorReturn CDM (v6)
│   │   ├── AlphaVantageCdmAdapter.java            # AV adjusted daily → CdmTick (v6)
│   │   ├── ShillerSp500CdmAdapter.java            # Shiller CSV → IndexSnapshot CDM (v6)
│   │   ├── VixCdmAdapter.java                     # DataHub VIX CSV → CdmRateSnapshot (v6)
│   │   ├── OilPriceCdmAdapter.java                # DataHub WTI/Brent CSV → CdmRateSnapshot (v6)
│   │   └── GoldPriceCdmAdapter.java               # DataHub Gold CSV → CdmRateSnapshot (v6)
│   ├── equity/                                          # v6: Yahoo Finance + Finnhub equity REST
│   │   ├── EquityPriceClient.java                       # Interface
│   │   ├── YahooFinanceClient.java                      # Primary: Yahoo Finance REST
│   │   ├── FinnhubEquityClient.java                     # Fallback: Finnhub REST
│   │   └── EquityPriceScheduler.java                    # @Scheduled polling coordinator
│   ├── ws/                                              # v6: Finnhub WebSocket
│   │   ├── EquityWsClient.java                          # Interface (renamed from PolygonWsClient)
│   │   └── FinnhubWsClient.java                         # Replaces DefaultPolygonWsClient
│   ├── news/                                            # v6: News/sentiment ingestion
│   │   ├── NewsIngestionClient.java                     # Interface
│   │   ├── FinnhubNewsClient.java                       # Finnhub market news
│   │   └── FedRSSClient.java                            # Federal Reserve RSS feeds
│   ├── factor/                                          # v6: Ken French factor data
│   │   ├── FrenchFactorClient.java                      # ZIP CSV download + parse
│   │   └── FrenchFactorScheduler.java                   # @Scheduled monthly poll
│   ├── alphavantage/                                    # v6: Deep historical + commodity
│   │   ├── AlphaVantageClient.java                      # Rate-limited REST client
│   │   └── AlphaVantageScheduler.java                   # @Scheduled daily poll
│   ├── datahub/                                         # v6: Historical CSV backfill
│   │   ├── DataHubBackfillClient.java                   # CSV fetch + parse
│   │   └── DataHubBackfillScheduler.java                # Startup + monthly incremental
│   ├── filter/
│   │   ├── DeRoundingFilter.java            # v5: Round-mark volume smoothing (Proposal 05)
│   │   └── TimePeriodicityFilter.java       # v5: 1-second spike removal (Proposal 05)
│   ├── guard/
│   │   └── AlgorithmicSanityGuard.java      # v5: Extreme condition detection (Proposal 06)
│   ├── options/
│   │   └── OptionsDataClient.java           # v6: Yahoo Finance options endpoint
│   ├── buffer/
│   │   ├── IngestionBuffer.java                    # Interface
│   │   ├── InMemoryIngestionBuffer.java            # Tier 1
│   │   ├── ChronicleQueueOverflow.java             # Tier 2 (dedicated storage, Finding 7)
│   │   └── DiskBackedIngestionBuffer.java          # Composite
│   ├── writer/
│   │   └── TimescaleDbWriter.java                  # Batched INSERT, off-heap buffer (v3), idempotency keys (v4)
│   ├── quality/
│   │   ├── DataQualityChecker.java                 # Staleness, outliers, async anomaly detection (v4)
│   │   ├── ProxyDivergenceMonitor.java             # T-Bill/SOFR divergence (Finding 3)
│   │   ├── ToxicityMonitor.java             # v5: Toxicity scoring (Proposal 07)
│   │   └── OrderCancellationMonitor.java    # v5: Cancellation tracking (Proposal 07)
│   ├── anomaly/
│   │   └── AnomalyDetectionWorker.java             # Async anomaly detection sidecar (v4, Proposal 01)
│   ├── cache/
│   │   └── LastKnownGoodCache.java                 # LKG cache for source fallback (v4, Proposal 02)
│   ├── time/
│   │   └── EventBasedTimeConverter.java            # Intrinsic time conversion (v4, Proposal 03)
│   ├── external/
│   │   ├── DisasterAlertClient.java                # USGS/GDACS disaster polling (v4, Proposal 03)
│   │   └── EconomicCalendarClient.java      # v5: Economic calendar (Proposal 07)
│   ├── bulkhead/
│   │   └── IngestionBulkheadConfig.java            # Executor isolation config (v4, Proposal 02)
│   ├── tracing/
│   │   └── IngestionTracingConfig.java             # OpenTelemetry config (v4, Proposal 02)
│   ├── config/
│   │   └── IngestionConfig.java                    # @ConfigurationProperties
│   └── resilience/
│       └── CircuitBreakerConfig.java               # Resilience4j config
└── src/test/java/com/tickonomics/ingestion/
    ├── fred/
    │   └── FredClientTest.java                     # WireMock
    ├── nyfed/
    │   └── NyFedClient.java                        # WireMock
    ├── equity/                                      # v6
    │   ├── YahooFinanceClientTest.java              # WireMock
    │   └── FinnhubEquityClientTest.java             # WireMock
    ├── ws/                                          # v6
    │   └── FinnhubWsClientTest.java                 # Mock WebSocket
    ├── news/                                        # v6
    │   ├── FinnhubNewsClientTest.java               # WireMock
    │   └── FedRSSClientTest.java                    # Mock RSS
    ├── factor/                                      # v6
    │   └── FrenchFactorClientTest.java              # ZIP parse, CSV, incremental
    ├── alphavantage/                                # v6
    │   └── AlphaVantageClientTest.java              # WireMock (429, 401, success)
    └── datahub/                                     # v6
        └── DataHubBackfillClientTest.java           # CSV parse, incremental, schema drift
    ├── writer/
    │   └── TimescaleDbWriterTest.java              # Testcontainers
    ├── buffer/
    │   └── DiskBackedIngestionBufferTest.java
    ├── anomaly/
    │   └── AnomalyDetectionWorkerTest.java          # v4: Async timeout, fallback, SUSPECT_ANOMALY
    ├── cache/
    │   └── LastKnownGoodCacheTest.java              # v4: Stale serving, expiry, X-Data-Age
    ├── time/
    │   └── EventBasedTimeConverterTest.java          # v4: Directional change, overshoot events
    ├── external/
    │   └── DisasterAlertClientTest.java              # v4: USGS/GDACS parsing, min magnitude filter
    ├── bulkhead/
    │   └── IngestionBulkheadConfigTest.java          # v4: Pool isolation, saturation resistance
    └── tracing/
        └── IngestionTracingConfigTest.java            # v4: Span creation, context propagation
```

---

## Observability (Metrics)

- `monitor.ingestion.events.total` — counter, tagged by source (`fred`, `nyfed`, `yahoo-finance`, `finhub-ws`, `finnhub-rest`).
- `monitor.ingestion.latency` — timer from event receipt to TimescaleDB write.
- `monitor.ingestion.fred.latency` — timer for FRED API round-trips.
- `monitor.ingestion.nyfed.latency` — timer for NY Fed API round-trips.
- `monitor.ingestion.yahoo.latency` — timer for Yahoo Finance REST round-trips.
- `monitor.ingestion.finnhub.latency` — timer for Finnhub REST round-trips.
- `monitor.ingestion.errors.total` — counter of call failures, tagged by source.
- `monitor.ingestion.timescaledb.batch.size` — distribution summary of INSERT batch sizes.
- `monitor.datasource.health` — gauge (1=healthy, 0=circuit open), tagged by source.
- `monitor.buffer.utilization` — gauge of in-memory buffer fill %.
- `monitor.buffer.overflow.to_disk.total` — counter of events spilled to Chronicle Queue.
- `monitor.ingestion.proxy_divergence.total` — counter of proxy divergence events (Finding 3).

**v4 additions:**

- `monitor.ingestion.anomaly.detection.total` — counter of anomaly checks performed.
- `monitor.ingestion.anomaly.suspect.total` — counter of `SUSPECT_ANOMALY` flags raised.
- `monitor.ingestion.lkg.served.total` — counter of stale data served from LKG cache, tagged by source.
- `monitor.ingestion.disaster.alerts.total` — counter of disaster alerts received, tagged by source (`usgs`, `gdacs`).
- `monitor.ingestion.disaster.severity` — gauge of current maximum disaster alert severity (0=None, 1=Green, 2=Orange,
  3=Red).

**v5 additions:**

- `monitor.ingestion.derounding.smoothed.total` — counter of intervals smoothed by de-rounding filter.
- `monitor.ingestion.periodicity.smoothed.total` — counter of second-level spikes smoothed.
- `monitor.ingestion.sanity_guard.breaches.total` — counter of sanity guard breach events.
- `monitor.ingestion.options.events.total` — counter of options data snapshots ingested.
- `monitor.ingestion.toxicity.scores.total` — counter of toxicity score computations, tagged by class.
- `monitor.ingestion.economic_calendar.events.total` — counter of economic events ingested.

**v6 additions:**

- `monitor.ingestion.finnhub.ws.latency` — timer for Finnhub WebSocket message processing.
- `monitor.ingestion.french_factor.import.rows` — counter of factor return rows imported.
- `monitor.ingestion.alphavantage.import.rows` — counter of Alpha Vantage rows imported.
- `monitor.ingestion.datahub.backfill.rows` — counter of DataHub CSV rows imported.
- `monitor.ingestion.fed_rss.articles.total` — counter of Fed RSS articles ingested.
- `monitor.ingestion.finnhub.news.articles.total` — counter of Finnhub news articles ingested.

---

## Self-Monitoring Alerts

- Chronicle Queue depth > 50% of disk quota: log `OVERFLOW_QUEUE_GROWING`.
- FRED API unreachable > 5 min: log `FRED_API_DOWN`.
- NY Fed API unreachable > 5 min: log `NYFED_API_DOWN`.
- Yahoo Finance unreachable > 5 min: log `YAHOO_FINANCE_DOWN` (equity prices degraded, Finnhub fallback active).
- Finnhub REST unreachable > 5 min: log `FINNHUB_REST_DOWN`.
- No new ticks in > stale threshold: log `DATA_STALE`.
- Finnhub WS disconnected > 5 min: log `FINNHUB_WS_FALLBACK` (REST fallback active).
- T-Bill/SOFR divergence > 2 std deviations: log `PROXY_DIVERGENCE_DETECTED` (Finding 3).

**v4 additions:**

- Anomaly detection worker unreachable > 1 min: log `ANOMALY_WORKER_FALLBACK`.
- LKG cache serving stale data: log `LKG_STALE_DATA_SERVED` with source name and data age.
- Disaster alert received (severity WARNING or CRITICAL): log `DISASTER_ALERT_RECEIVED` with
  alert type, magnitude/severity, and geographic region.
- Bulkhead pool > 80% utilization: log `BULKHEAD_POOL_PRESSURE` with pool name
  (`critical-ingestion`, `high-volume-ingestion`, `computation-engine`).

**v5 additions:**

- DeRoundingFilter active (spike detected at round mark): log `DEROUNDING_SPIKE_DETECTED` with timestamp and symbol.
- AlgorithmicSanityGuard breach: log `ALGORITHMIC_SANITY_BREACH` with condition details.
- Options data source unreachable > 5 min: log `OPTIONS_DATA_DOWN`.
- Economic calendar source unreachable > 1 hour: log `ECONOMIC_CALENDAR_DOWN`.

**v6 additions:**

- Ken French Data Library unreachable > 1 hour: log `KEN_FRENCH_UNREACHABLE`.
- Alpha Vantage 429 rate limit hit: log `ALPHAVANTAGE_RATE_LIMITED`.
- DataHub CSV fetch failure: log `DATAHUB_BACKFILL_FAILED` with dataset name.
- Fed RSS feed parse error: log `FED_RSS_PARSE_ERROR`.

---

## Validation

- [ ] FRED client fetches all required series (EFFR, RRP, TGA, WALCL, IORB) with deduplication.
- [ ] NY Fed client fetches SOFR, TGCR, BGCR, Treasury rates with deduplication.
- [ ] YahooFinanceClient fetches equity/ETF OHLCV (primary).
- [ ] FinnhubEquityClient activates when Yahoo circuit breaker opens.
- [ ] Direct FRED/NY Fed clients work independently (Finding 5).
- [ ] Finnhub WS connects, subscribes, receives trades, writes to TimescaleDB.
- [ ] Auto-reconnect works after Finnhub WS disconnect (1s → 60s backoff).
- [ ] Fallback to Yahoo/Finnhub REST equity aggregates after 5 min WS disconnect.
- [ ] Batched INSERT flushes at correct batch-size and interval.
- [ ] Chronicle Queue overflow activates when memory > 80%.
- [ ] Chronicle Queue uses dedicated storage path (Finding 7).
- [ ] Overflow replays correctly on reconnect.
- [ ] Circuit breaker opens after 5 failures and half-opens after 30s for each source.
- [ ] Staleness detection marks series `STALE` after configured threshold.
- [ ] Outlier detection flags > 50 bps moves as `SUSPECT`.
- [ ] Proxy divergence monitor detects T-Bill/SOFR dislocation (Finding 3).
- [ ] Unit tests with WireMock (FRED, NY Fed, Yahoo, Finnhub) and mocked WebSocket servers.
- [ ] Integration tests with TimescaleDB testcontainers.
- [ ] Off-heap buffer allocated via `ByteBuffer.allocateDirect()` with no per-tick heap allocations (v3).
- [ ] GC pause gauge (`monitor.buffer.gc.pause_ms`) reports during flush cycles (v3).
- [ ] Chronicle Queue configured with `MAPPED_FILE` mode on dedicated NVMe partition (v3).
- [ ] FRED, NY Fed, Yahoo Finance, Finnhub clients produce CDM-typed output via adapters (v3).
- [ ] No source-specific raw types (`FredObservation`, `NyFedRateResponse`, `YahooQuote`, `FinnhubTrade`) visible outside ingestion module (v3).

**v4 additions:**

- [ ] Idempotency keys prevent duplicate writes during simulated network retries (Proposal 02).
- [ ] `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` correctly deduplicates rows in
  TimescaleDB when the same FRED observation or Finnhub tick is processed twice.
- [ ] `IngestionDLQ` preserves idempotency key across retry attempts.
- [ ] Anomaly detection sidecar flags corrupted data as `SUSPECT_ANOMALY` without blocking
  the ingestion pipeline (Proposal 01).
- [ ] Anomaly detection falls back to threshold checks when analytics worker is unavailable
  or async call exceeds `async_timeout` (Proposal 01).
- [ ] `ANOMALY_WORKER_FALLBACK` alert logged when worker is unreachable > 1 min (Proposal 01).
- [ ] LKG cache serves stale data with `X-Data-Age: STALE` header when circuit breaker is
  open for a data source (Proposal 02).
- [ ] LKG cache expires entries after `max_staleness` and serves explicit error instead of
  indefinitely stale data (Proposal 02).
- [ ] Bulkhead isolates critical ingestion (FRED/NY Fed) from Finnhub WS saturation —
  Finnhub pool at 100% does not affect FRED fetch latency (Proposal 02).
- [ ] `BULKHEAD_POOL_PRESSURE` alert logged when any pool exceeds 80% utilization (Proposal 02).
- [ ] EventBasedTimeConverter produces directional change and overshoot events from raw tick
  data (Proposal 03).
- [ ] Event metadata stored in `market_events` hypertable with correct event type classification
  (Proposal 03).
- [ ] DisasterAlertClient receives and processes USGS earthquake alerts above `min_magnitude`
  threshold (Proposal 03).
- [ ] DisasterAlertClient receives and processes GDACS alerts with correct severity classification
  (Proposal 03).
- [ ] Disaster alert triggers `EXOGENOUS_SHOCK` regime override in computation engine
  (Proposal 03, verified with Track 5 integration test).
- [ ] OpenTelemetry spans are created for FRED fetch, NY Fed fetch, Finnhub WS message,
  TimescaleDB write, quality check, anomaly detection, and disaster alert poll (Proposal 02).
- [ ] Trace IDs propagated via Arrow IPC metadata to Python analytics worker (Proposal 02).

**v5 additions:**

- [ ] DeRoundingFilter smooths volume spikes at configured round time marks (5-minute intervals).
- [ ] DeRoundingFilter preserves non-spike data unchanged.
- [ ] TimePeriodicityFilter removes 1-second granular spikes from tick data.
- [ ] TimePeriodicityFilter uses time-weighted averaging for spike replacement.
- [ ] AlgorithmicSanityGuard triggers on simulated "fat finger" (> 10% price move in < 1s).
- [ ] AlgorithmicSanityGuard forces system into Manual Oversight state.
- [ ] Manual Oversight state prevents all automated signal dispatch.
- [ ] Options data client fetches OI and IV from Yahoo Finance options endpoint (when enabled).
- [ ] ToxicityMonitor produces per-symbol toxicity scores with HARMFUL/BENEFICIAL/NEUTRAL classification.
- [ ] Economic calendar client fetches high-impact event schedule.
- [ ] Tick data tagged with session position (PRE_OPEN, DR_WINDOW, POST_DR).
- [ ] OrderCancellationMonitor tracks cancellation ratios per symbol.
- [ ] HighFrequencyAggregator applies realized kernel filters to OHLCV data (v5.2).

### v5.2 Validation Additions (Proposal 13 & 14: Quantitative Engine)

- [ ] `IntegrityPathLogger` tracks 'coding rules' for data transformation (v5.2).
- [ ] `HighFrequencyAggregator` applies realized kernel filters to OHLCV data (v5.2).
- [ ] Yahoo Finance options client fetches Greeks and yields for strategy inputs (v5.2/v6).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|:--------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Virtual Threads primary, direct FRED/NY Fed clients, Chronicle Queue on dedicated storage, Proxy Divergence Monitor.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| v3      | Added off-heap ring buffer in `TimescaleDbWriter` (Proposal #2). Added Chronicle Queue memory-mapped file mode. Added CDM adapter package (`adapter/`) with per-source adapters. Updated FRED, NY Fed clients to produce CDM-typed output. Added GC pause monitoring gauge.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| v4      | Added seven new components from consolidated proposals: (1) Anomaly Detection Sidecar — async autoencoder integration in `DataQualityChecker` with non-blocking fallback (Proposal 01). (2) Idempotency Key Integration — UUID keys on all external requests, `ON CONFLICT (idempotency_key) DO NOTHING` in `TimescaleDbWriter`, `idempotency_key` column in DLQ schema (Proposal 02). (3) Last Known Good Cache — in-memory fallback with `X-Data-Age: STALE` header and `max_staleness` expiry (Proposal 02). (4) Bulkhead Executor Isolation — three dedicated pools for critical ingestion, high-volume WS, and computation (Proposal 02). (5) EventBasedTimeConverter — intrinsic time mapping of raw ticks to directional change and overshoot events (Proposal 03). (6) DisasterAlertClient — USGS/GDACS real-time disaster alert polling with news sentiment trigger (Proposal 03). (7) OpenTelemetry Tracing — distributed tracing with OTLP exporter and Arrow IPC context propagation (Proposal 02). Added circuit breaker entries for USGS and GDACS endpoints. Added 5 new observability metrics and 4 new self-monitoring alerts. Added 17 new validation criteria. Added configuration sections for anomaly, tracing, disaster, bulkhead, LKG cache, and idempotency. Updated module structure with `anomaly/`, `cache/`, `time/`, `external/`, `bulkhead/`, `tracing/` packages. |
| v5      | `04-ingestion-layer.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Added 7 new components from three proposals: (1) DeRoundingFilter — smooths volume spikes at round time marks (Proposal 05). (2) TimePeriodicityFilter — removes 1-second algorithmic loop artifacts (Proposal 05). (3) AlgorithmicSanityGuard — extreme condition detection with Manual Oversight state (Proposal 06). (4) Options Data Client — Polygon Options API ingestion for GEX (Proposal 06). (5) ToxicityMonitor — per-venue toxicity scoring (Proposal 07). (6) Economic Calendar Client — high-impact news schedule polling (Proposal 07). (7) OrderCancellationMonitor — cancellation ratio tracking (Proposal 07). Added circuit breakers for Options API and Economic Calendar. Added 6 new observability metrics and 4 new alerts. Added 12 new validation criteria. Added configuration for derounding, periodicity, sanity_guard, options, and economic_calendar. Updated module structure with `filter/`, `guard/`, `options/` packages. |
| v5.1    | `04-ingestion-layer.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Added from Proposal 11: Fed Balance Sheet ingestion via existing `FredClient` (WALCL series). Weekly polling with circuit breaker. Writes to `fed_balance_sheet` hypertable. No new modules needed — extends existing FRED client with additional series mapping. 2 new validation criteria. |
| v5.2    | `04-ingestion-layer.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Added Proposal 13 & 14 (Quantitative Engine): `IntegrityPathLogger` for audit, realized kernel aggregation, and Polygon options/bonds ingestion. |
| v6      | Free data source migration: replaced Polygon WebSocket with Finnhub WebSocket (`EquityWsClient` + `FinnhubWsClient`). Replaced OpenBB sidecar with Yahoo Finance REST + Finnhub REST equity clients. Replaced Polygon Options with Yahoo Finance options endpoint. Added Sections 24-28: Finnhub News Client, Fed RSS Client, Ken French Factor Client, Alpha Vantage Client, DataHub Backfill Client. Added yield curve tenors to FRED client. Added 11 new CDM adapters. Updated module structure with `equity/`, `ws/`, `news/`, `factor/`, `alphavantage/`, `datahub/` packages. Added `factor_returns` and `index_snapshots` hypertables. Extended `rate_snapshots` with VIX, OIL_WTI, OIL_BRENT, GOLD. Updated config, metrics, alerts, and validation checklists. Removed all paid data dependencies. |

---

## Appendix: 02-ingestion-layer.md

> *Merged from `gaps/02-ingestion-layer.md` / `done/02-ingestion-layer.md` during plan_v6 consolidation.*

# Ingestion Layer — 7 Missing Components

**Plan ref:** `04-ingestion-layer.md`

| #   | Component                       | Plan Section  | Description                                                                                                 |
| --- | ------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------- |
| 1   | `DisasterAlertClient`           | §Component 15 | USGS Earthquake + GDACS RSS polling, EXOGENOUS_SHOCK regime trigger                                         |
| 2   | `AlgorithmicSanityGuard`        | §Component 19 | Price >10% in <1s or >10000 msg/sec detection, Manual Oversight trigger                                     |
| 3   | `OrderCancellationMonitor`      | §Component 23 | Cancellation ratios per symbol, volatility leading indicator                                                |
| 4   | `OpenBBClient` (Java)           | §Component 3  | REPLACED by YahooFinanceClient + FinnhubEquityClient (v6 free data source migration)                        |
| 5   | `EventBasedTimeConverter`       | §Component 14 | Maps raw ticks to directional change and overshoot events                                                   |
| 6   | `AnomalyDetectionWorker` (Java) | §Component 10 | Async autoencoder-based anomaly detection in Java                                                           |
| 7   | **Chronicle Queue integration** | §Component 6  | Off-heap ring buffer + disk-backed overflow (replaced by `TieredIngestionBuffer` with `FileOverflowBuffer`) |

> **Note:** Items 4, 6, and 7 may be intentional design simplifications — the analytics worker handles anomaly detection in Python, and `FileOverflowBuffer` replaces Chronicle Queue. `OpenBBClient` was replaced by YahooFinanceClient + FinnhubEquityClient in the v6 free data source migration.

---

## Appendix: FREE_DATA_SOURCES_INTEGRATION_PLAN.md

> *Merged from `gaps/FREE_DATA_SOURCES_INTEGRATION_PLAN.md` / `done/FREE_DATA_SOURCES_INTEGRATION_PLAN.md` during plan_v6 consolidation.*

# Free Data Sources Integration Plan

> **Archival note (v6):** This document's content has been fully integrated into plan_v6 numbered
> documents (00–11). It is retained here for historical reference and change attribution. The
> implementation phases, acceptance criteria, and validation checklists are now distributed across
> the relevant track documents.

**Version:** 1.3
**Date:** 2026-06-03
**Scope:** Replace paid data dependencies with free alternatives; fill existing data gaps identified in Plan V5.

---

## 1. Executive Summary

The Tickonomics platform currently relies on **two paid Polygon.io subscriptions** (equity ticks via WebSocket, options snapshots via REST) for market data, while the OpenBB sidecar referenced throughout Plan V5 has **no client code** and adds operational burden. This plan proposes replacing all paid dependencies with free data sources, eliminating the OpenBB dependency entirely, and filling six identified data gaps using freely available APIs.

**Goals:**

- Zero recurring data costs for development and demo deployment.
- Remove the OpenBB sidecar container (simplify operations).
- Maintain data quality parity with current paid sources for ILI computation and demo portfolio.
- All proposed sources have free tiers with sufficient rate limits for the platform's polling cadence.
- **v1.1 addition:** Ken French Data Library provides free Fama-French factor returns (Rm-Rf, SMB, HML, RMW, CMA, Mom) for the existing factor regression, tournament benchmarking, and risk decomposition.
- **v1.2 addition:** Alpha Vantage provides free deep historical equity OHLCV (20+ years, split/dividend adjusted), commodities (gold, oil, gas, copper, wheat), and fundamental data (earnings, balance sheets) via a once-daily REST poll. Fills the backtesting history gap and adds commodities as a new asset class.
- **v1.3 addition:** DataHub provides free, no-auth, deep-historical CSV datasets for S&P 500 (Shiller, 1871–present with CAPE), VIX (1990–present daily), oil prices (WTI/Brent, 1986–present daily), and gold (1833–present monthly). One-time backfill via stable CSV URLs. Fills the historical benchmark index, volatility index, and deep commodity history gaps for backtesting and regime detection.

---

## 2. Current Data Source Inventory

### 2.1 Implemented — Free (Retain As-Is)

| Source | Client | Data | Rate Limit | API Key |
|:-------|:-------|:-----|:-----------|:--------|
| FRED REST API | `FredClient.java` | EFFR, RRP, TGA, WALCL, IORB, economic calendar | 120 req/min (free key) | Free signup |
| NY Fed REST API | `NyFedClient.java` | SOFR, TGCR, BGCR, Treasury rates | No published limit | None |
| USGS Earthquake | `DisasterAlertClient.java` | Earthquake events (mag >= 5.0) | No published limit | None |
| FRED Releases API | `EconomicCalendarClient.java` | Economic release calendar | 120 req/min (shared with FRED) | Free signup |

### 2.2 Implemented — Paid (Replace)

| Source | Client | Data | Cost | Replacement |
|:-------|:-------|:-----|:-----|:------------|
| Polygon WebSocket | `DefaultPolygonWsClient.java` | Real-time equity ticks | $29–$199/mo | Yahoo Finance / Finnhub |
| Polygon Options REST | `OptionsDataClient.java` | Options chain snapshots with Greeks | $29–$199/mo | Yahoo Finance options |

### 2.3 Referenced but Not Implemented (Remove)

| Source | Plan Reference | Status | Decision |
|:-------|:---------------|:-------|:---------|
| OpenBB Platform sidecar | Track 4 §3, Track 5 | Docker compose overlay exists, no client code | **Remove entirely** |

### 2.4 Identified Data Gaps (Fill)

| Gap | Consumers | Priority |
|:----|:----------|:---------|
| Equity/ETF historical OHLCV prices | `PaperTradingEngine`, `CorrelationEngine`, backtesting, dashboard | P0 |
| Real-time equity price ticks | ILI correlation analysis, signal generation | P0 |
| Options chain data (OI, IV, Greeks) | GEX monitor, risk guardrails | P1 |
| News / FOMC text for sentiment analysis | `SentimentAnalyzer` (Proposal 09), BRI computation | P1 |
| Treasury yield curve (multi-tenor) | Monetary policy sensitivity panel, Q-world fair value | P2 |
| Fed speeches / FOMC statements | FinBERT hawkish/dovish detection | P2 |
| Fama-French factor returns (Rm-Rf, SMB, HML, RMW, CMA, Mom) | `fama_french_regression()`, tournament service, risk decomposition, regime detection | P1 |
| Historical equity benchmark index (S&P 500 level, dividend, earnings, CAPE) | `WalkForwardValidator`, `CrossModelValidator`, regime detection, alpha evaluation | P1 |
| Volatility index (VIX daily OHLC) | `RegimeService`, `EvtRiskService`, cross-asset correlation | P1 |
| Deep historical equity OHLCV (20+ years, adjusted) | `WalkForwardValidator`, `CrossModelValidator`, backtesting, external validation | P2 |
| Commodities (gold, oil, gas, copper, wheat) | Portfolio diversification analysis, inflation hedging, ILI commodity sensitivity | P2 |
| Fundamental data (earnings, balance sheet, cash flow) | Value factor signals, earnings surprise detection | P2 |

---

## 3. Proposed Free Data Sources

### 3.1 Equity/ETF Historical & Real-Time Prices — P0

**Primary: Yahoo Finance (yfinance)**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}` |
| Auth | None (session cookie-based, no API key) |
| Rate Limit | ~2,000 req/hour (unofficial, varies) |
| Data | Historical OHLCV (1m–1mo intervals), real-time price quotes |
| Coverage | US equities, ETFs, indices |
| Latency | ~15 min delayed; real-time via `/v8/finance/chart` with `interval=1m` |
| Stability | Yahoo occasionally changes endpoints; requires header spoofing |

**Fallback: Finnhub**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://finnhub.io/api/v1/quote` (real-time), `/stock/candle` (OHLCV) |
| Auth | Free API key (signup required) |
| Rate Limit | 60 calls/min (free tier) |
| Data | Real-time quotes, historical candles (1–D intervals) |
| Coverage | US equities, forex, crypto |
| Cost | Free tier sufficient for ~10 symbols at 6h polling |

**Integration approach:**

```
┌─────────────────────────────────────────────────┐
│  EquityPriceClient (interface)                  │
│  ├── YahooFinanceClient (primary)               │
│  │   └── RestClient + session cookie mgmt       │
│  └── FinnhubClient (fallback)                   │
│      └── RestClient + API key                   │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  EquityPriceCdmAdapter                          │
│  └── Maps Yahoo/Finnhub response → CdmTick      │
└─────────────────────────────────────────────────┘
```

**Why both:** Yahoo Finance has no official API key or SLA — it can break without notice. Finnhub provides a stable, key-authenticated fallback with clear rate limits. For the platform's polling cadence (6h for historical, configurable for intraday), the 60 req/min free tier is ample.

### 3.2 Real-Time Equity Ticks — P0

**Primary: Finnhub WebSocket**

| Attribute | Value |
|:----------|:------|
| Endpoint | `wss://ws.finnhub.io` |
| Auth | Free API key (same key as REST) |
| Rate Limit | No WebSocket-specific limit on free tier |
| Data | Real-time trades (price, volume, timestamp, conditions) |
| Coverage | US equities |
| Latency | Near real-time (~0.5s) |

**Integration approach:**

Replace `DefaultPolygonWsClient` with `FinnhubWsClient` implementing the same `PolygonWsClient` interface (renamed to `EquityWsClient`). The existing WebSocket infrastructure (reconnection backoff, circuit breaker, tick-to-CDM adapter) is reused — only the connection handshake and message parsing changes.

```java
// Reuse existing interface, rename to be source-agnostic
public interface EquityWsClient {
    void connect();
    void subscribe(String symbol);
    void disconnect();
    boolean isConnected();
}
```

**Why not Yahoo Finance WebSocket:** Yahoo does not offer a public WebSocket API. Finnhub's free WebSocket is the only zero-cost real-time equity feed with trade-level granularity.

### 3.3 Options Chain Data — P1

**Primary: Yahoo Finance Options**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://query1.finance.yahoo.com/v7/finance/options/{symbol}` |
| Auth | None (same session as equity prices) |
| Rate Limit | Shared with equity (within Yahoo's unofficial limit) |
| Data | Full options chain: strikes, expiry dates, bid/ask, IV, OI, volume, Greeks |
| Coverage | US-listed equity and ETF options |
| Latency | ~15 min delayed |

**Integration approach:**

Replace `OptionsDataClient`'s Polygon REST calls with Yahoo Finance options endpoint. The existing `OptionsChainSnapshot` model and CDM adapter remain unchanged — only the HTTP target and response parser change.

### 3.4 News / FOMC Text for Sentiment — P1

**Primary: Finnhub Market News**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://finnhub.io/api/v1/news?category=general` |
| Auth | Free API key |
| Rate Limit | 60 calls/min (shared with equity) |
| Data | Market news headlines, summaries, source, timestamp, URL |
| Coverage | General financial news, categorized by topic |

**Secondary: Federal Reserve Board RSS Feeds**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://www.federalreserve.gov/feeds/speeches.xml`, `https://www.federalreserve.gov/feeds/press_monetary.xml` |
| Auth | None |
| Rate Limit | None (public RSS) |
| Data | FOMC statements, Fed speeches, press releases (full text) |
| Coverage | All Federal Reserve communications |

**Integration approach:**

```
┌─────────────────────────────────────────────────────┐
│  NewsIngestionClient (interface)                    │
│  ├── FinnhubNewsClient                              │
│  │   └── REST polling, 6h interval for general news │
│  └── FedRSSClient                                   │
│      └── RSS polling, 1h interval for FOMC/speeches │
└─────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  NewsText stored in sentiment_history hypertable     │
│  ├── FinBERT processes FOMC speeches → hawkish/dove  │
│  └── SALI lexicon scores general news headlines      │
└─────────────────────────────────────────────────────┘
```

### 3.5 Treasury Yield Curve (Multi-Tenor) — P2

**Primary: FRED (Existing Client Extension)**

| Attribute | Value |
|:----------|:------|
| Endpoint | Existing `FredClient` — add series |
| Auth | Existing FRED API key |
| Rate Limit | 120 req/min (existing) |
| Data | Yields at 1M, 3M, 6M, 1Y, 2Y, 3Y, 5Y, 7Y, 10Y, 20Y, 30Y |

**Series to add:**

| FRED Series ID | Tenor | Frequency |
|:---------------|:------|:----------|
| `DGS1MO` | 1-Month | Daily |
| `DGS3MO` | 3-Month | Daily |
| `DGS6MO` | 6-Month | Daily |
| `DGS1` | 1-Year | Daily |
| `DGS2` | 2-Year | Daily |
| `DGS5` | 5-Year | Daily |
| `DGS10` | 10-Year | Daily |
| `DGS30` | 30-Year | Daily |

**Integration approach:**

No new client needed. Extend `FredClient.FredSeriesConfig` with additional series IDs. The existing FRED polling, CDM adapter, and `TimescaleDbWriter` pipeline handles these transparently. Data stored in `rate_snapshots` with `rate_type` values matching CDM enums (`TBILL_1M`, `TBILL_3M`, etc.).

### 3.6 Fed Speeches / FOMC Full Text — P2

**Primary: Federal Reserve RSS Feeds** (same as §3.4 secondary)

Covered by the `FedRSSClient` above. FOMC statements and Fed speeches are the primary input for FinBERT hawkish/dovish classification.

**Additional: SEC EDGAR Full-Text Search**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://efts.sec.gov/LATEST/search-index?q=...` or `https://data.sec.gov/submissions/CIK={cik}.json` |
| Auth | None (User-Agent header required) |
| Rate Limit | 10 req/sec (stated policy) |
| Data | 10-K, 10-Q filings, insider trading (Form 4), institutional holdings (13F) |
| Coverage | All US public company filings |

SEC EDGAR is a P2 stretch goal for future regulatory compliance reports and insider trading signals. Not required for initial free-tier deployment.

### 3.7 Fama-French Factor Returns — P1

**Primary: Ken French Data Library (Dartmouth / Tuck)**

| Attribute | Value |
|:----------|:------|
| Base URL | `https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp/` |
| Auth | None — fully public, no API key |
| Rate Limit | None (static CSV files, monthly updated) |
| Data | Factor returns (Rm-Rf, SMB, HML, RMW, CMA, RF, Mom, ST Rev, LT Rev) and sorted portfolios |
| Frequency | Monthly and Daily |
| Coverage | US (1926–present), Developed, Emerging markets |
| Format | ZIP-compressed CSV; missing values coded as -99.99 or -999 |

**Key datasets:**

| File | Factors / Portfolios | Use Case |
|:-----|:---------------------|:---------|
| `F-F_Research_Data_Factors_CSV.zip` | Rm-Rf, SMB, HML, RF | 3-factor regression (existing) |
| `F-F_Research_Data_5_Factors_2x3_CSV.zip` | Rm-Rf, SMB, HML, RMW, CMA, RF | 5-factor regression (upgrade) |
| `Momentum_Factor_CSV.zip` | Mom (UMD) | Tournament momentum benchmark |
| `F-F_ST_Reversal_Factor_CSV.zip` | ST Rev | Short-term reversal analysis |
| `F-F_LT_Reversal_Factor_CSV.zip` | LT Rev | Long-term reversal analysis |
| `6_Portfolios_Formed_on_Size_and_BM_2x3_CSV.zip` | Small/Big × Value/Neutral/Growth | External validation portfolios |
| `25_Portfolios_Formed_on_Size_and_BM_5x5_CSV.zip` | 5×5 Size × B/M | Granular benchmarking |

**Integration approach:**

```
┌────────────────────────────────────────────────────────┐
│  FrenchFactorClient                                    │
│  ├── Scheduled monthly poll (1st of month)             │
│  ├── HTTP GET *.zip → unzip → parse CSV                │
│  ├── Detects new data rows since last import           │
│  └── Stores incremental rows in factor_returns table   │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  FrenchFactorCdmAdapter                                │
│  └── CSV row → FactorReturn CDM record                 │
│      (date, factor_set, rm_rf, smb, hml, rmw, cma,    │
│       rf, mom)                                         │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  Consumers                                             │
│  ├── Performance service: 5-factor regression          │
│  ├── Tournament service: Mom factor as benchmark       │
│  ├── Risk service: factor-based risk decomposition     │
│  └── Regime detection: factor spreads as features      │
└────────────────────────────────────────────────────────┘
```

**Why this source:** The project's `fama_french_regression()` at `analytics/app/services/performance/performance_service.py:59` already supports 3-factor OLS but requires callers to manually supply `smb` and `hml` data — there is no automated factor data ingestion. The Ken French Data Library is the canonical source for this data: free, authoritative, updated monthly, and trivially parseable (plain CSV). It also enables upgrading from 3-factor to 5-factor (adding RMW, CMA), replacing synthetic momentum benchmarks in the tournament service with the actual Mom/UMD factor, and providing sorted portfolio returns for out-of-sample backtest validation.

**Why P1:** The existing 3-factor regression is incomplete without factor data. No other free source provides Fama-French factors. Integration effort is low (CSV parsing, no auth) and impact is high (completes a half-implemented feature).

### 3.8 Alpha Vantage Historical & Commodity Data — P2

**Primary: Alpha Vantage REST API**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol={symbol}&outputsize=full&apikey={key}` |
| Auth | Free API key (signup at https://www.alphavantage.co/support/#api-key) |
| Rate Limit | 5 req/min, 500 req/day (free); 75 req/min, 15,000 req/day ($49.99/mo "Fundamental" tier) |
| Data | Adjusted daily OHLCV (20+ years, split + dividend corrected), commodities, fundamental data |
| Coverage | US equities, global equities, commodities, forex, crypto, economic indicators |
| Latency | End-of-day (previous close available after market hours) |
| Stability | Official API with SLA on paid tiers; free tier is rate-limited but stable |

**Key endpoints for once-daily retrieval:**

| API Function | Data | Use Case |
|:-------------|:-----|:---------|
| `TIME_SERIES_DAILY_ADJUSTED` | 20+ years OHLCV with adjusted close, split coefficient, dividend amount | Backtesting, walk-forward validation, correlation analysis |
| `TIME_SERIES_WEEKLY` / `MONTHLY` | Weekly/monthly aggregated OHLCV | Long-term trend analysis |
| `COMMODITY_DAILY` (v3.0+) | Gold, crude oil, natural gas, copper, wheat, corn | Commodity sensitivity, inflation hedging analysis |
| `EARNINGS` | Quarterly EPS (actual vs estimated) | Earnings surprise factor |
| `BALANCE_SHEET` | Annual/quarterly balance sheet | Value factor signals |
| `INCOME_STATEMENT` | Revenue, net income, margins | Fundamental screening |
| `ECONOMIC_INDICATORS` | Real GDP, CPI, unemployment, treasury yields | Cross-check against FRED data |

**Integration approach:**

```
┌────────────────────────────────────────────────────────────┐
│  AlphaVantageClient                                        │
│  ├── @Scheduled daily poll (06:00 UTC, market closed)      │
│  ├── Fetches adjusted daily OHLCV for configured symbols   │
│  ├── Fetches commodity prices (gold, oil, gas, copper)     │
│  ├── Incremental: only new rows since last import date     │
│  ├── Resilience4j retry (3 attempts, exponential backoff)  │
│  └── Rate-limited: 1 req / 12s to stay within 5/min cap    │
└────────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│  AlphaVantageCdmAdapter                                    │
│  ├── AV adjusted daily → CdmTick (OHLCV)                  │
│  ├── AV commodity → CdmTick (commodity symbols)           │
│  └── AV fundamentals → FundamentalSnapshot CDM record     │
└────────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│  Consumers                                                 │
│  ├── WalkForwardValidator: 20-year backtest windows        │
│  ├── CrossModelValidator: out-of-sample validation         │
│  ├── CorrelationEngine: long-term equity correlations      │
│  ├── ClimateRiskGuard: commodity price sensitivity         │
│  └── Dashboard: historical price charts (20+ years)        │
└────────────────────────────────────────────────────────────┘
```

**Why Alpha Vantage over Yahoo Finance for historical data:**

| Factor | Yahoo Finance | Alpha Vantage |
|:-------|:-------------|:-------------|
| Adjusted close (splits + dividends) | Inconsistent adjustment method | Official split-coefficient + dividend fields |
| History depth | Varies by symbol | Consistent 20+ years for US equities |
| Commodity data | Limited | Gold, oil, gas, copper, wheat, corn (v3.0+) |
| Fundamental data | Not available via unofficial API | Earnings, balance sheet, income statement |
| API stability | No SLA, unofficial endpoints | Official API, versioned, documented |
| Rate limits | Unofficial (~2000/hr) | Clear limits (5/min free, 75/min paid) |
| Cost | Free | Free tier sufficient for once-daily use |

**Why P2:** Alpha Vantage fills the deep-history gap for backtesting that Yahoo Finance's 6-hour polling cannot address (Yahoo provides recent data; Alpha Vantage provides the full 20+ year adjusted series). It also adds commodities as an entirely new asset class. The free tier's 500 calls/day is ample for once-daily polling of ~10 symbols + 4 commodities (14 calls/day, 2.8% utilization). If backtesting demand grows, a paid tier unlock ($49.99/mo) removes all rate constraints.

**Free tier call budget:**

| Daily Call | Count | Cumulative |
|:-----------|:------|:-----------|
| Adjusted OHLCV (6 equity symbols, incremental) | 6 | 6 |
| Commodity daily (gold, oil, gas, copper) | 4 | 10 |
| Earnings (6 symbols, quarterly check) | 0–6 | 16 |
| Balance sheet (6 symbols, quarterly check) | 0–6 | 22 |
| **Total (typical day)** | **~14–22** | **2.8–4.4% of 500 daily cap** |

### 3.9 DataHub Historical Datasets — P1/P2

**Primary: DataHub Core Datasets (CSV)**

| Attribute | Value |
|:----------|:------|
| URL | `https://datahub.io/core/{dataset}/_r/-/data/{file}.csv` |
| Auth | None — fully public, no API key |
| Rate Limit | None (static CSV files served via CDN) |
| Format | CSV with stable r-link URLs; `datapackage.json` for machine-readable schema |
| License | ODC-PDDL-1.0 / Public Domain |
| Update Frequency | Monthly to ~2 months (varies by dataset) |
| Use Case | Historical backfill only (not a live streaming source) |

**Key datasets to integrate:**

| Dataset | CSV URL | Granularity | Coverage | Priority | Consumer Services |
|:--------|:--------|:------------|:---------|:---------|:------------------|
| S&P 500 (Shiller) | `core/s-and-p-500/_r/-/data/data.csv` | Monthly | 1871–present | P1 | Backtest, regime detection, CAPE analysis, alpha evaluation |
| VIX (CBOE) | `core/finance-vix/_r/-/data/vix-daily.csv` | Daily | 1990–present | P1 | Volatility regime detection, EVT risk, cross-asset correlation |
| Oil (WTI) | `core/oil-prices/_r/-/data/wti-daily.csv` | Daily | 1986–present | P2 | Macro shock IRF, energy-macro correlation, ILI sensitivity |
| Oil (Brent) | `core/oil-prices/_r/-/data/brent-daily.csv` | Daily | 1987–present | P2 | Cross-regime energy analysis, international benchmark |
| Gold | `core/gold-prices/_r/-/data/monthly.csv` | Monthly | 1833–present | P2 | Safe-haven correlation, inflation hedging, cross-asset analysis |

**Integration approach:**

```
┌──────────────────────────────────────────────────────────────┐
│  DataHubBackfillClient                                       │
│  ├── One-time full load on startup (if target table empty)   │
│  ├── Periodic incremental check (monthly, 1st of month)      │
│  ├── HTTP GET CSV from stable r-link URLs                    │
│  ├── Parse CSV → CDM records (per-dataset adapters)          │
│  ├── Detect new rows since max(timestamp) in target table    │
│  └── Write incremental rows via TimescaleDbWriter            │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  CDM Adapters (per dataset)                                  │
│  ├── ShillerSp500CdmAdapter → index_snapshots (SP500, CAPE)  │
│  ├── VixCdmAdapter          → rate_snapshots (VIX)           │
│  ├── OilPriceCdmAdapter     → rate_snapshots (OIL_WTI,       │
│  │                             OIL_BRENT)                     │
│  └── GoldPriceCdmAdapter    → rate_snapshots (GOLD)          │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  Consumers                                                   │
│  ├── WalkForwardValidator: S&P 500 as backtest benchmark     │
│  ├── RegimeService: VIX for volatility regime classification │
│  ├── MacroShockService: oil prices for energy shock IRFs     │
│  ├── EVT risk service: VIX for tail risk calibration         │
│  ├── CorrelationEngine: cross-asset (oil, gold, S&P, VIX)    │
│  └── Dashboard: historical price charts (100+ years)         │
└──────────────────────────────────────────────────────────────┘
```

**Why DataHub:**

1. **Zero-cost, zero-auth** — plain CSV with stable URLs, no API key, no rate limits. Just `HTTP GET`.
2. **Deep history** — S&P 500 back to 1871 (153+ years), gold to 1833 (190+ years), VIX to 1990 (35+ years). No other free source provides this depth.
3. **Authoritative provenance** — Robert Shiller (S&P 500), CBOE (VIX), EIA (oil), World Bank (gold). Same sources the project already trusts via FRED.
4. **Complements Alpha Vantage** — Alpha Vantage provides live daily updates (20+ years); DataHub provides the deep historical tail (100+ years for gold, 50+ years for S&P 500). Together they give full coverage from 1833 to present.
5. **Machine-readable schema** — each dataset has `datapackage.json` with field types and descriptions for auto-generated CDM adapter field mappings.

**Why P1 for S&P 500 + VIX, P2 for Oil + Gold:**

The S&P 500 Shiller dataset provides the CAPE ratio (cyclically adjusted P/E) which is directly referenced in regime detection logic and fills the "historical equity benchmark" gap entirely. VIX fills the "volatility index" gap — the project has no volatility index source at all, and VIX is the canonical "fear gauge" that drives regime classification. Oil and gold are important for macro analysis but are secondary to the core ILI + regime detection pipeline.

**Deferred DataHub datasets:**

| Dataset | Reason for Deferral | Trigger Condition |
|:--------|:--------------------|:------------------|
| Natural Gas (Henry Hub) | Secondary energy commodity; oil covers primary macro-energy channel | Energy analysis expands beyond WTI/Brent |
| S&P 500 Company Lists | Project focuses on macro/index-level signals, not individual stock screening | Universe expands to individual equity analysis |
| NYSE/NASDAQ Listings | Not applicable to current index-focused architecture | Individual stock coverage required |
| S&P 500 Companies Financials | Fundamental data available via Alpha Vantage with daily updates | Alpha Vantage fundamentals insufficient |

See `docs/plan_v5/deferred-items-implementation-plan.md` §6 for the high-level integration plan for deferred datasets.

---

## 4. Integration Architecture

### 4.1 Source Layer Diagram

```
                        ┌─────────────────────────────────────┐
                        │        CDM Adapter Layer             │
                        │  (existing: FredCdmAdapter,          │
                        │   NyFedCdmAdapter, etc.)             │
                        └──────────────┬──────────────────────┘
                                       │
      ┌──────────────┬─────────────┬───┴───┬──────────────┬────────────────┬───────────────┬───────────┐
      │              │             │       │              │                │               │           │
┌─────▼─────┐ ┌─────▼─────┐ ┌────▼────┐ ┌▼────────────┐ ┌▼─────────────┐ ┌▼───────────┐ ┌▼──────────────┐ ┌▼──────────┐
│   FRED    │ │  NY Fed   │ │ Yahoo   │ │  Finnhub    │ │  Fed RSS     │ │ Ken French │ │ Alpha Vantage │ │ DataHub   │
│  (FREE)   │ │  (FREE)   │ │ Finance │ │  (FREE)     │ │  (FREE)      │ │  (FREE)    │ │   (FREE)      │ │  (FREE)   │
│           │ │           │ │ (FREE)  │ │             │ │              │ │            │ │               │ │           │
│ Rates     │ │ SOFR/TGCR │ │ OHLCV   │ │ Real-time   │ │ FOMC text    │ │ 3/5-Factor │ │ Deep hist     │ │ S&P 500   │
│ Balance   │ │ BGCR      │ │ Options │ │ equity WS   │ │ Speeches     │ │ Momentum   │ │ OHLCV (adj)   │ │ (Shiller) │
│ sheet     │ │ Treasury  │ │ chain   │ │ News feed   │ │ Press        │ │ Reversal   │ │ Commodities   │ │ VIX       │
│ Yield     │ │ rates     │ │         │ │             │ │ releases     │ │ Portfolios │ │ Fundamentals  │ │ Oil/Gold  │
│ curve     │ │           │ │         │ │             │ │              │ │            │ │               │ │           │
└───────────┘ └───────────┘ └─────────┘ └─────────────┘ └──────────────┘ └────────────┘ └───────────────┘ └───────────┘

      │              │             │              │                │           │               │
      └──────────────┴─────────────┴──────────────┴────────────────┴───────────┴───────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │    Ingestion Pipeline (existing)     │
                        │  ┌────────────────────────────┐     │
                        │  │ DataQualityChecker         │     │
                        │  │ ProxyDivergenceGuard       │     │
                        │  │ TimescaleDbWriter          │     │
                        │  │ CircuitBreakers / Retry    │     │
                        │  └────────────────────────────┘     │
                        └─────────────────────────────────────┘
```

### 4.2 New Java Classes

All new classes follow existing patterns: `@Scheduled` polling, `RestClient` HTTP, CDM adapters, Resilience4j resilience.

```
ingestion/src/main/java/com/tickonomics/ingestion/
├── equity/
│   ├── EquityPriceClient.java              # Interface: fetch OHLCV + real-time quote
│   ├── YahooFinanceClient.java             # Primary: Yahoo Finance REST
│   ├── FinnhubEquityClient.java            # Fallback: Finnhub REST
│   └── EquityPriceScheduler.java           # @Scheduled polling coordinator
├── ws/
│   ├── EquityWsClient.java                 # Interface (renamed from PolygonWsClient)
│   └── FinnhubWsClient.java               # Replaces DefaultPolygonWsClient
├── news/
│   ├── NewsIngestionClient.java            # Interface: fetch news articles
│   ├── FinnhubNewsClient.java              # Finnhub market news
│   └── FedRSSClient.java                   # Federal Reserve RSS feeds
└── options/
    └── YahooOptionsClient.java             # Replaces Polygon OptionsDataClient
        (existing OptionsDataClient.java refactored)
├── factor/
│   ├── FrenchFactorClient.java             # Ken French CSV download + parse
│   └── FrenchFactorScheduler.java          # @Scheduled monthly poll (1st of month)
├── alphavantage/
│   ├── AlphaVantageClient.java             # Alpha Vantage REST (historical OHLCV, commodities)
│   └── AlphaVantageScheduler.java          # @Scheduled daily poll (06:00 UTC)
├── datahub/
│   ├── DataHubBackfillClient.java          # DataHub CSV fetch + parse (backfill mode)
│   └── DataHubBackfillScheduler.java       # Startup full-load + monthly incremental check

cdm/src/main/java/com/tickonomics/cdm/adapter/
├── YahooEquityCdmAdapter.java              # Yahoo OHLCV → CdmTick
├── FinnhubEquityCdmAdapter.java            # Finnhub quote → CdmTick
├── YahooOptionsCdmAdapter.java             # Yahoo options → OptionsChainSnapshot
├── NewsArticleCdmAdapter.java              # News/Fed text → canonical news record
├── FrenchFactorCdmAdapter.java             # Ken French CSV → FactorReturn CDM
├── AlphaVantageCdmAdapter.java             # AV adjusted daily → CdmTick / commodity tick
├── ShillerSp500CdmAdapter.java             # Shiller S&P 500 CSV → IndexSnapshot CDM
├── VixCdmAdapter.java                      # DataHub VIX CSV → CdmRateSnapshot
├── OilPriceCdmAdapter.java                 # DataHub WTI/Brent CSV → CdmRateSnapshot
└── GoldPriceCdmAdapter.java                # DataHub Gold CSV → CdmRateSnapshot
```

### 4.3 Configuration Changes

```yaml
monitor:
  # --- Existing FRED (unchanged) ---
  fred:
    base-url: "https://api.stlouisfed.org/fred"
    api-key: "${FRED_API_KEY}"
    poll-interval-ms: 300000
    # New: yield curve series
    yield-curve-enabled: true

  # --- Existing NY Fed (unchanged) ---
  nyfed:
    base-url: "https://markets.newyorkfed.org/api"
    poll-interval-ms: 300000

  # --- NEW: Yahoo Finance ---
  yahoo-finance:
    base-url: "https://query1.finance.yahoo.com"
    poll-interval-ms: 21600000          # 6 hours
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    include-options: true
    options-poll-interval-ms: 3600000   # 1 hour (market hours only)
    options-symbols: ["SPY", "QQQ"]
    connect-timeout: "5s"
    read-timeout: "30s"

  # --- NEW: Finnhub (primary WS, fallback REST) ---
  finnhub:
    api-key: "${FINNHUB_API_KEY}"
    ws-url: "wss://ws.finnhub.io"
    rest-url: "https://finnhub.io/api/v1"
    ws-enabled: true
    ws-reconnect-backoff-max: "60s"
    rest-poll-interval-ms: 300000       # 5 min fallback
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    news-enabled: true
    news-poll-interval-ms: 21600000     # 6 hours
    connect-timeout: "5s"
    read-timeout: "30s"

  # --- NEW: Federal Reserve RSS ---
  fed-rss:
    enabled: true
    speeches-url: "https://www.federalreserve.gov/feeds/speeches.xml"
    fomc-url: "https://www.federalreserve.gov/feeds/press_monetary.xml"
    poll-interval-ms: 3600000           # 1 hour

  # --- NEW: Ken French Data Library ---
  ken-french:
    enabled: true
    base-url: "https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp"
    poll-interval-ms: 86400000          # 24 hours (check for monthly updates)
    poll-day-of-month: 1                # Primary poll on 1st of each month
    datasets:
      - name: "3-factor"
        file: "F-F_Research_Data_Factors_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RF"]
      - name: "5-factor"
        file: "F-F_Research_Data_5_Factors_2x3_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RMW", "CMA", "RF"]
      - name: "momentum"
        file: "Momentum_Factor_CSV.zip"
        factors: ["MOM"]
    connect-timeout: "10s"
    read-timeout: "60s"                 # ZIP files can be large

  # --- NEW: Alpha Vantage (deep historical + commodities) ---
  alpha-vantage:
    enabled: true
    api-key: "${ALPHAVANTAGE_API_KEY}"
    base-url: "https://www.alphavantage.co/query"
    poll-cron: "0 0 6 * * *"            # 06:00 UTC daily (after US market close)
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    commodities: ["GOLD", "OIL", "NATURAL_GAS", "COPPER"]
    fetch-mode: "incremental"            # Only new rows since last import; "full" for initial load
    initial-full-load: true              # On first run, fetch full 20+ year history
    rate-limit-delay-ms: 12100           # 12.1s between calls (max 5/min → safe margin)
    connect-timeout: "10s"
    read-timeout: "30s"
    include-fundamentals: false          # Enable earnings/balance sheet (P3 stretch goal)

  # --- NEW: DataHub Historical Backfill ---
  datahub:
    enabled: true
    base-url: "https://datahub.io"
    startup-full-load: true              # Full load on first startup if tables empty
    incremental-check-cron: "0 0 2 1 * *" # 02:00 UTC on 1st of each month (check for updates)
    connect-timeout: "10s"
    read-timeout: "60s"                  # CSV files can be large
    datasets:
      sp500-shiller:
        enabled: true
        csv-url: "/core/s-and-p-500/_r/-/data/data.csv"
        target-table: "index_snapshots"
        description: "Monthly S&P 500 (Shiller): price, dividend, earnings, CPI, CAPE since 1871"
      vix:
        enabled: true
        csv-url: "/core/finance-vix/_r/-/data/vix-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "VIX"
        description: "Daily CBOE Volatility Index (OHLC) since 1990"
      oil-wti:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/wti-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_WTI"
        description: "Daily WTI spot price ($/bbl) since 1986"
      oil-brent:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/brent-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_BRENT"
        description: "Daily Brent spot price ($/bbl) since 1987"
      gold:
        enabled: true
        csv-url: "/core/gold-prices/_r/-/data/monthly.csv"
        target-table: "rate_snapshots"
        rate-type: "GOLD"
        description: "Monthly gold price ($/oz) since 1833"

  # --- REMOVED ---
  # polygon.ws-url          → replaced by finnhub.ws-url
  # polygon.api-key          → replaced by finnhub.api-key
  # openbb.base-url          → removed entirely
```

### 4.4 New CDM Enum Values

Extend `rate_type` CHECK constraint in `rate_snapshots`:

```sql
ALTER TABLE rate_snapshots DROP CONSTRAINT chk_rate_type_cdm;
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN (
        'SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
        'RRP', 'TGA', 'WALCL', 'TBILL_3M',
        -- New yield curve tenors
        'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
        'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y'
    ));
```

New `factor_returns` hypertable for Ken French data:

```sql
CREATE TABLE IF NOT EXISTS factor_returns (
    time            TIMESTAMPTZ     NOT NULL,
    factor_set      TEXT            NOT NULL,   -- '3FACTOR', '5FACTOR', 'MOMENTUM'
    frequency       TEXT            NOT NULL,   -- 'MONTHLY', 'DAILY'
    rm_rf           DOUBLE PRECISION,
    smb             DOUBLE PRECISION,
    hml             DOUBLE PRECISION,
    rmw             DOUBLE PRECISION,
    cma             DOUBLE PRECISION,
    rf              DOUBLE PRECISION,
    mom             DOUBLE PRECISION,
    st_rev          DOUBLE PRECISION,
    lt_rev          DOUBLE PRECISION,
    region          TEXT            NOT NULL DEFAULT 'US',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, factor_set, frequency, region)
);

SELECT create_hypertable('factor_returns', 'time', chunk_time_interval => INTERVAL '1 year');
ALTER TABLE factor_returns SET (compress_after = '2 years');
```

---

## 5. Implementation Phases

### Phase 1 — Replace Paid Equity Data (P0)

**Estimated effort:** 3–4 days
**Blocks:** Nothing (parallel with existing work)
**Depends on:** Existing ingestion infrastructure

| Step | Task | Files |
|:-----|:-----|:------|
| 1.1 | Create `EquityPriceClient` interface | `ingestion/.../equity/EquityPriceClient.java` |
| 1.2 | Implement `YahooFinanceClient` (historical OHLCV) | `ingestion/.../equity/YahooFinanceClient.java` |
| 1.3 | Implement `FinnhubEquityClient` (fallback) | `ingestion/.../equity/FinnhubEquityClient.java` |
| 1.4 | Create CDM adapters for Yahoo/Finnhub equity | `cdm/.../adapter/YahooEquityCdmAdapter.java`, `FinnhubEquityCdmAdapter.java` |
| 1.5 | Implement `EquityPriceScheduler` with circuit breaker | `ingestion/.../equity/EquityPriceScheduler.java` |
| 1.6 | Wire into existing `TimescaleDbWriter` pipeline | Update config, resilience beans |
| 1.7 | Write WireMock tests for both clients | `ingestion/src/test/...` |
| 1.8 | Integration test: Yahoo → CDM adapter → TimescaleDB | Testcontainers |

**Acceptance criteria:**
- [ ] `YahooFinanceClient` fetches OHLCV for all configured symbols
- [ ] `FinnhubEquityClient` activates when Yahoo circuit breaker opens
- [ ] Both clients produce `CdmTick` via adapters — no source-specific types leak
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] LKG cache serves stale data with `X-Data-Age: STALE` on source failure

### Phase 2 — Replace Paid WebSocket & Options (P0/P1)

**Estimated effort:** 2–3 days
**Depends on:** Phase 1

| Step | Task | Files |
|:-----|:-----|:------|
| 2.1 | Rename `PolygonWsClient` → `EquityWsClient` interface | `api-contracts/.../client/` |
| 2.2 | Implement `FinnhubWsClient` (real-time trades) | `ingestion/.../ws/FinnhubWsClient.java` |
| 2.3 | Update `PolygonTickCdmAdapter` → generic `WsTickCdmAdapter` | `cdm/.../adapter/` |
| 2.4 | Implement `YahooOptionsClient` replacing Polygon options | `ingestion/.../options/YahooOptionsClient.java` |
| 2.5 | Create `YahooOptionsCdmAdapter` | `cdm/.../adapter/YahooOptionsCdmAdapter.java` |
| 2.6 | Deprecate `DefaultPolygonWsClient` and old `OptionsDataClient` | Mark `@Deprecated` |
| 2.7 | Write tests for Finnhub WS (mock WebSocket server) | `ingestion/src/test/...` |
| 2.8 | Write tests for Yahoo options parsing | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FinnhubWsClient` connects, subscribes, receives trades, writes to `tick_data`
- [ ] Auto-reconnect works after disconnect (1s → 60s backoff)
- [ ] `YahooOptionsClient` fetches full options chain with Greeks and OI
- [ ] Options data flows to `option_chain_snapshots` hypertable
- [ ] No paid Polygon dependency on the runtime classpath (compile-only stub)

### Phase 3 — News & Sentiment Sources (P1)

**Estimated effort:** 2–3 days
**Depends on:** Phase 1 (news needs to flow into existing pipeline)

| Step | Task | Files |
|:-----|:-----|:------|
| 3.1 | Create `NewsIngestionClient` interface | `ingestion/.../news/NewsIngestionClient.java` |
| 3.2 | Implement `FinnhubNewsClient` (market news) | `ingestion/.../news/FinnhubNewsClient.java` |
| 3.3 | Implement `FedRSSClient` (FOMC/speeches) | `ingestion/.../news/FedRSSClient.java` |
| 3.4 | Create `NewsArticleCdmAdapter` | `cdm/.../adapter/NewsArticleCdmAdapter.java` |
| 3.5 | Write to `sentiment_history` hypertable | Update `TimescaleDbWriter` mapping |
| 3.6 | Wire to `SentimentAnalyzer` (Proposal 09) | `computation/.../sentiment/` |
| 3.7 | Write tests with WireMock (Finnhub) and mock RSS (Fed) | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FinnhubNewsClient` fetches market news headlines at configured interval
- [ ] `FedRSSClient` parses FOMC statements and Fed speeches from RSS
- [ ] News articles stored in `sentiment_history` with `source_type` classification
- [ ] FinBERT receives Fed speech text for hawkish/dovish classification
- [ ] SALI lexicon scores general news headlines

### Phase 4 — Ken French Factor Returns (P1)

**Estimated effort:** 2–3 days
**Depends on:** Existing ingestion infrastructure
**Blocks:** Phase 5 (yield curve) is independent; Phase 8 (cleanup) depends on all prior phases

| Step | Task | Files |
|:-----|:-----|:------|
| 4.1 | Create `FrenchFactorClient` — HTTP GET ZIP, unzip, parse CSV | `ingestion/.../factor/FrenchFactorClient.java` |
| 4.2 | Create `FrenchFactorScheduler` — `@Scheduled` monthly poll | `ingestion/.../factor/FrenchFactorScheduler.java` |
| 4.3 | Create `FrenchFactorCdmAdapter` — CSV row → `FactorReturn` CDM | `cdm/.../adapter/FrenchFactorCdmAdapter.java` |
| 4.4 | Add `FactorReturn` CDM model and `FactorSet` enum | `cdm/.../model/FactorReturn.java`, `cdm/.../FactorSet.java` |
| 4.5 | Flyway migration: create `factor_returns` hypertable | `persistence/.../db/migration/V20__create_factor_returns.sql` |
| 4.6 | Update `TimescaleDbWriter` to handle `FactorReturn` records | `persistence/.../TimescaleDbWriter.java` |
| 4.7 | Upgrade `fama_french_regression()` to support 5-factor model | `analytics/.../performance/performance_service.py` |
| 4.8 | Wire `FrenchFactorClient` config in `application.yml` | `app/src/main/resources/application.yml` |
| 4.9 | Wire Mom factor into tournament service as external benchmark | `analytics/.../benchmark/tournament_service.py` |
| 4.10 | Write tests: CSV parsing, CDM adapter, incremental import | `ingestion/src/test/...`, `analytics/tests/...` |

**Acceptance criteria:**
- [ ] `FrenchFactorClient` downloads and parses 3-factor, 5-factor, and momentum ZIP files
- [ ] Incremental import detects new rows since last import (no duplicates)
- [ ] Factor returns stored in `factor_returns` hypertable with correct date alignment
- [ ] Missing values (-99.99, -999) handled gracefully (stored as NULL)
- [ ] `fama_french_regression()` supports 5-factor mode (Rm-Rf, SMB, HML, RMW, CMA)
- [ ] Tournament service uses actual Mom/UMD factor instead of synthetic momentum
- [ ] Python analytics worker reads factor data from TimescaleDB for regressions
- [ ] WireMock tests cover: success, 404, corrupt ZIP, malformed CSV

### Phase 5 — Yield Curve Extension (P2)

**Estimated effort:** 1 day
**Depends on:** Nothing (extends existing FRED client)

| Step | Task | Files |
|:-----|:-----|:------|
| 5.1 | Add yield curve series IDs to `FredSeriesConfig` | `ingestion/.../fred/FredSeriesConfig.java` |
| 5.2 | Add CDM enum values for yield curve tenors | `cdm/.../RateType.java` |
| 5.3 | Flyway migration: extend `chk_rate_type_cdm` constraint | `persistence/.../db/migration/V19__add_yield_curve_enums.sql` |
| 5.4 | Update `MonetaryPolicySensitivityService` to consume yield curve data | `computation/.../` |
| 5.5 | Write tests verifying new series ingestion | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FredClient` fetches all 8 yield curve tenors in addition to existing series
- [ ] Data stored in `rate_snapshots` with CDM-aligned `rate_type` values
- [ ] Yield curve shape (2Y–10Y spread, 3M–10Y spread) computable from stored data
- [ ] `MonetaryPolicySensitivityService` renders yield curve panel on dashboard

### Phase 6 — Alpha Vantage Historical & Commodity Data (P2)

**Estimated effort:** 3–4 days
**Depends on:** Phase 1 (ingestion pipeline and CDM adapter patterns established)
**Blocks:** Phase 8 (cleanup)

| Step | Task | Files |
|:-----|:-----|:------|
| 6.1 | Create `AlphaVantageClient` — REST client with rate-limit-aware sequential calls | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.2 | Create `AlphaVantageScheduler` — `@Scheduled(cron)` daily at 06:00 UTC | `ingestion/.../alphavantage/AlphaVantageScheduler.java` |
| 6.3 | Create `AlphaVantageCdmAdapter` — adjusted daily OHLCV → `CdmTick`, commodity → `CdmTick` | `cdm/.../adapter/AlphaVantageCdmAdapter.java` |
| 6.4 | Create `AlphaVantageRawModels` — response DTOs for TIME_SERIES_DAILY_ADJUSTED, COMMODITY_DAILY | `ingestion/.../alphavantage/AlphaVantageRawModels.java` |
| 6.5 | Add `InstrumentType.COMMODITY` enum and commodity subtypes (`GOLD`, `OIL`, `NATURAL_GAS`, `COPPER`, `WHEAT`, `CORN`) to CDM | `cdm/.../enums/InstrumentType.java` |
| 6.6 | Implement incremental fetch logic — query `tick_data` for last timestamp per symbol, pass `startdate` parameter to Alpha Vantage | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.7 | Implement initial full-load mode — `outputsize=full` on first run per symbol (tracked in import metadata table) | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.8 | Add Resilience4j retry config for `alphaVantageApi` in `application.yml` | `app/src/main/resources/application.yml` |
| 6.9 | Flyway migration: add `commodity` to `InstrumentType` CHECK constraint; add `data_import_tracker` table for incremental state | `persistence/.../db/migration/V21__add_commodity_and_import_tracker.sql` |
| 6.10 | Wire `AlphaVantageCdmAdapter` output to existing `TimescaleDbWriter` | `ingestion/.../alphavantage/AlphaVantageScheduler.java` |
| 6.11 | Wire commodity data to `ClimateRiskGuard` for commodity price sensitivity | `computation/.../risk/` |
| 6.12 | Write unit tests: response parsing, CDM adapter, incremental logic, rate limiting | `ingestion/src/test/.../alphavantage/` |
| 6.13 | Write WireMock integration tests: success, 429 rate limit, invalid API key, malformed JSON, empty response | `ingestion/src/test/.../alphavantage/` |

**Acceptance criteria:**
- [ ] `AlphaVantageClient` fetches adjusted daily OHLCV for all configured symbols
- [ ] Adjusted close reflects split and dividend corrections (verified against known corporate actions)
- [ ] Commodity daily prices fetched for gold, crude oil, natural gas, copper
- [ ] Rate limiting enforced: no more than 1 call per 12.1s (≤5/min)
- [ ] Incremental mode fetches only new rows since last import (tracked per symbol in `data_import_tracker`)
- [ ] Initial full load fetches 20+ year history on first run per symbol
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] Commodity data identifiable by `InstrumentType.COMMODITY` subtype in CDM
- [ ] Resilience4j retry handles transient 429/5xx responses with exponential backoff
- [ ] WireMock tests cover: success, 429 rate limit, 401 invalid key, malformed JSON, empty series, split-adjusted verification

### Phase 7 — DataHub Historical Backfill (P1/P2)

**Estimated effort:** 2–3 days
**Depends on:** Phase 4 (ingestion pipeline patterns), Phase 6 (CDM adapter patterns for commodity/index types)
**Blocks:** Phase 8 (cleanup)

| Step | Task | Files |
|:-----|:-----|:------|
| 7.1 | Create `DataHubBackfillClient` — HTTP GET CSV from stable r-link URLs with streaming CSV parser | `ingestion/.../datahub/DataHubBackfillClient.java` |
| 7.2 | Create `DataHubBackfillScheduler` — startup full-load trigger + `@Scheduled(cron)` monthly incremental check | `ingestion/.../datahub/DataHubBackfillScheduler.java` |
| 7.3 | Create `ShillerSp500CdmAdapter` — Shiller CSV row → `IndexSnapshot` CDM record (price, dividend, earnings, CPI, CAPE) | `cdm/.../adapter/ShillerSp500CdmAdapter.java` |
| 7.4 | Create `VixCdmAdapter` — VIX CSV row → `CdmRateSnapshot` with `rate_type='VIX'` | `cdm/.../adapter/VixCdmAdapter.java` |
| 7.5 | Create `OilPriceCdmAdapter` — WTI/Brent CSV row → `CdmRateSnapshot` with `rate_type='OIL_WTI'`/`'OIL_BRENT'` | `cdm/.../adapter/OilPriceCdmAdapter.java` |
| 7.6 | Create `GoldPriceCdmAdapter` — Gold CSV row → `CdmRateSnapshot` with `rate_type='GOLD'` | `cdm/.../adapter/GoldPriceCdmAdapter.java` |
| 7.7 | Extend CDM `RateType` enum with `VIX`, `OIL_WTI`, `OIL_BRENT`, `GOLD` | `cdm/.../RateType.java` |
| 7.8 | Add `InstrumentType.INDEX` and `INDEX_SP500` to CDM | `cdm/.../enums/InstrumentType.java` |
| 7.9 | Flyway migration: extend `chk_rate_type_cdm` constraint for VIX/oil/gold; create `index_snapshots` hypertable | `persistence/.../db/migration/V22__datahub_backfill.sql` |
| 7.10 | Wire `DataHubBackfillClient` config in `application.yml` under `datahub.*` namespace | `app/src/main/resources/application.yml` |
| 7.11 | Wire Shiller data to backtesting services (`WalkForwardValidator`, `CrossModelValidator`) as benchmark index | `computation/.../backtest/` |
| 7.12 | Wire VIX to regime detection and EVT risk services as volatility input | `analytics/.../regime/`, `analytics/.../risk/` |
| 7.13 | Wire oil/gold to macro shock and cross-asset correlation services | `analytics/.../statistical/`, `analytics/.../econometrics/` |
| 7.14 | Write unit tests: CSV parsing for each dataset, CDM adapter field mapping, incremental import logic | `ingestion/src/test/.../datahub/` |
| 7.15 | Write WireMock integration tests: success, 404, empty CSV, malformed rows, schema changes | `ingestion/src/test/.../datahub/` |

**Acceptance criteria:**
- [ ] `DataHubBackfillClient` fetches all 5 configured CSV files from stable r-link URLs
- [ ] S&P 500 Shiller data (1871–present) loaded into `index_snapshots` with price, dividend, earnings, CPI, CAPE
- [ ] VIX daily data (1990–present) loaded into `rate_snapshots` with `rate_type='VIX'`
- [ ] WTI daily (1986–present) and Brent daily (1987–present) loaded into `rate_snapshots` with correct rate types
- [ ] Gold monthly (1833–present) loaded into `rate_snapshots` with `rate_type='GOLD'`
- [ ] Startup detection: if target table is empty, perform full load; otherwise incremental only
- [ ] Incremental import detects new rows since `max(timestamp)` — no duplicates
- [ ] Shiller PE10 values of 0.0 (1871–1880, insufficient trailing history) stored as NULL
- [ ] Gold pre-1960 monthly values (annual averages repeated per month) annotated in metadata column
- [ ] WireMock tests cover: success, 404, empty CSV, malformed rows, schema drift

**`index_snapshots` hypertable schema:**

```sql
CREATE TABLE IF NOT EXISTS index_snapshots (
    time            TIMESTAMPTZ     NOT NULL,
    index_type      TEXT            NOT NULL,   -- 'SP500_SHILLER'
    price           DOUBLE PRECISION NOT NULL,
    dividend        DOUBLE PRECISION,
    earnings        DOUBLE PRECISION,
    cpi             DOUBLE PRECISION,
    long_interest_rate DOUBLE PRECISION,
    real_price      DOUBLE PRECISION,
    real_dividend   DOUBLE PRECISION,
    real_earnings   DOUBLE PRECISION,
    cape            DOUBLE PRECISION,           -- NULL for 1871-1880 (insufficient history)
    source          TEXT            NOT NULL DEFAULT 'DATAHUB_SHILLER',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, index_type)
);

SELECT create_hypertable('index_snapshots', 'time', chunk_time_interval => INTERVAL '10 years');
ALTER TABLE index_snapshots SET (compress_after = '50 years');
```

**`rate_snapshots` CHECK constraint extension:**

```sql
ALTER TABLE rate_snapshots DROP CONSTRAINT chk_rate_type_cdm;
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN (
        'SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
        'RRP', 'TGA', 'WALCL', 'TBILL_3M',
        'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
        'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y',
        -- DataHub historical backfill types
        'VIX', 'OIL_WTI', 'OIL_BRENT', 'GOLD'
    ));
```

### Phase 8 — Cleanup & Removal (Final)

**Estimated effort:** 1 day
**Depends on:** Phases 1–7 complete and validated

| Step | Task |
|:-----|:-----|
| 8.1 | Remove `docker-compose.openbb.yml` overlay |
| 8.2 | Remove `FederationDataClient` interface (never implemented, superseded) |
| 8.3 | Remove `OpenBBAnalyticsClient` interface and references |
| 8.4 | Remove `openbb.*` configuration keys from `application.yml` |
| 8.5 | Delete `DefaultPolygonWsClient.java` (replaced by `FinnhubWsClient`) |
| 8.6 | Remove `POLYGON_API_KEY` from `.env.example` |
| 8.7 | Add `FRED_API_KEY`, `FINNHUB_API_KEY`, and `ALPHAVANTAGE_API_KEY` to `.env.example` |
| 8.8 | Update `application.yml` resilience4j: rename `polygonApi` → `finnhubApi` |
| 8.9 | Remove Polygon dependency from `ingestion/build.gradle` |
| 8.10 | Update documentation references to reflect new data sources |

---

## 6. Free Tier Rate Limit Analysis

### Projected Daily API Calls

| Source | Endpoint | Calls/Day | Free Tier Limit | Margin |
|:-------|:---------|:----------|:----------------|:-------|
| FRED | Series observations | ~120 (5 min × 13 series × 24h) | 172,800/day (120/min) | 99.9% headroom |
| NY Fed | Rate endpoints | ~144 (3 rates × 48 polls) | No published limit | N/A |
| Yahoo Finance | OHLCV + options | ~200 (6 symbols × 6h + 2 options × 24h) | ~48,000/day (est.) | 99.6% headroom |
| Finnhub REST | Quotes + news | ~300 (6 × 48 polls + news 6h) | 86,400/day (60/min) | 99.7% headroom |
| Finnhub WS | Real-time trades | Persistent connection | No WS limit (free) | N/A |
| Fed RSS | Speeches + FOMC | ~48 (2 feeds × 24 polls) | No published limit | N/A |
| USGS | Earthquake feed | ~2,880 (30s polls) | No published limit | N/A |
| Ken French | Factor CSV files | ~1 (monthly download, 3 ZIP files) | No limit (static files) | N/A |
| Alpha Vantage | Adjusted OHLCV + commodities | ~14–22 (once-daily poll) | 500/day (5/min) | 95.6–97.2% headroom |
| DataHub | Historical CSV backfill | ~5 (monthly incremental check, 5 CSV files) | No limit (static CDN files) | N/A |

**Total estimated daily calls: ~3,725** — well within all free tier limits.

### Finnhub Free Tier Constraints

| Feature | Free | Paid ($49/mo) |
|:--------|:-----|:--------------|
| US equity quotes | Yes (15 min delayed) | Real-time |
| WebSocket trades | Yes | Yes |
| Historical candles | Yes (1-day resolution) | 1-minute |
| Market news | Yes | Yes |
| Options data | No | Yes |
| Rate limit | 60/min | 600/min |

**Impact assessment:** The 15-minute quote delay on Finnhub REST is acceptable because:
1. Real-time trades via Finnhub WebSocket provide tick-level data for signal generation.
2. Historical OHLCV from Yahoo Finance provides the same daily/intraday bars that Polygon provided.
3. The ILI computation uses daily-aligned macro data, not tick-level equity feeds.

---

## 7. Risk Assessment

### High Risk

| Risk | Mitigation |
|:-----|:-----------|
| Yahoo Finance endpoint changes without notice | `FinnhubEquityClient` as automatic fallback; circuit breaker isolates Yahoo failures; endpoint versioned in config |
| Finnhub free tier changes or discontinues | Yahoo Finance as primary REST fallback; both sources have independent circuit breakers; Alpha Vantage provides a third independent equity source for historical data |
| Alpha Vantage free tier insufficient for growing symbol list | Current 14–22 calls/day uses only 4.4% of 500/day cap; paid tier ($49.99/mo) unlocks 15,000 calls/day if needed; rate-limit-aware scheduler enforces 12.1s spacing |
| Yahoo Finance rate limiting / IP blocking | Session rotation, polite polling (6h intervals), `User-Agent` header configuration, exponential backoff on 429 responses |

### Medium Risk

| Risk | Mitigation |
|:-----|:-----------|
| Finnhub WebSocket free tier has fewer symbols than Polygon | Limit to 6 core symbols (SPY, QQQ, IWM, TLT, HYG, GLD) — sufficient for ILI correlation and demo portfolio |
| Yahoo options data may have 15-min delay | Acceptable for GEX monitoring (daily regime detection, not intraday trading) |
| Fed RSS structure changes | Defensive XML parsing with fallback to raw text extraction |
| Alpha Vantage API response format changes | Response DTOs versioned; WireMock tests pin expected schema; Resilience4j retry on transient errors |
| DataHub CSV schema changes between updates | `datapackage.json` fetched alongside CSV for schema validation; `DataHubBackfillClient` validates field count/type before parsing; malformed rows skipped with warning log |

### Low Risk

| Risk | Mitigation |
|:-----|:-----------|
| FRED rate limit hit (120/min) | 13 series at 5-min intervals = ~2.6 req/min — 98% headroom |
| NY Fed API downtime | LKG cache serves stale data; existing resilience patterns handle this |

---

## 8. Environment Variables

```bash
# .env.example (updated)

# Required — free signup at https://fred.stlouisfed.org/docs/api/api_key/
FRED_API_KEY=

# Required — free signup at https://finnhub.io/register
FINNHUB_API_KEY=

# Required — free signup at https://www.alphavantage.co/support/#api-key
ALPHAVANTAGE_API_KEY=

# Removed:
# POLYGON_API_KEY=    (no longer needed)
```

---

## 9. Validation Checklist

### Phase 1 — Equity Prices

- [ ] `YahooFinanceClient` fetches OHLCV for SPY, QQQ, IWM, TLT, HYG, GLD
- [ ] Yahoo response parsed into `CdmTick` via `YahooEquityCdmAdapter`
- [ ] `FinnhubEquityClient` fetches real-time quotes for the same symbols
- [ ] Finnhub response parsed into `CdmTick` via `FinnhubEquityCdmAdapter`
- [ ] Circuit breaker on Yahoo opens after 5 failures; Finnhub takes over
- [ ] LKG cache serves last known price with `X-Data-Age: STALE` header
- [ ] Data written to `tick_data` hypertable with correct symbol, price, volume
- [ ] Continuous aggregates (`ohlcv_1min`, `ohlcv_1h`, `ohlcv_1d`) produce correct candles
- [ ] WireMock tests cover: success, 404, 429, timeout, malformed JSON

### Phase 2 — WebSocket & Options

- [ ] `FinnhubWsClient` connects to `wss://ws.finnhub.io` and authenticates
- [ ] Subscribes to configured symbols, receives trade events
- [ ] Trade events parsed into `CdmTick` with correct price, volume, timestamp
- [ ] Auto-reconnect works after disconnect with exponential backoff (1s → 60s)
- [ ] `YahooOptionsClient` fetches options chain for SPY and QQQ
- [ ] Options chain includes: strikes, expiry, bid, ask, IV, OI, volume, Greeks
- [ ] Data stored in `option_chain_snapshots` hypertable
- [ ] `DefaultPolygonWsClient` and old `OptionsDataClient` marked `@Deprecated`

### Phase 3 — News & Sentiment

- [ ] `FinnhubNewsClient` fetches market news articles
- [ ] `FedRSSClient` parses FOMC statements and Fed speeches from RSS XML
- [ ] News articles stored in `sentiment_history` with `source_type` classification
- [ ] FinBERT hawkish/dovish classification receives Fed speech text
- [ ] SALI lexicon scoring receives general news headlines
- [ ] Duplicate news articles filtered by `text_hash` (idempotency)

### Phase 4 — Ken French Factors

- [ ] `FrenchFactorClient` downloads and parses 3-factor, 5-factor, momentum ZIP files
- [ ] CSV rows parsed into `FactorReturn` CDM records with correct date alignment
- [ ] Missing values (-99.99, -999) stored as NULL, not as valid returns
- [ ] Incremental import detects new rows since last import (no duplicates)
- [ ] Factor returns stored in `factor_returns` hypertable with UNIQUE constraint enforced
- [ ] `fama_french_regression()` supports 5-factor mode with RMW, CMA regressors
- [ ] Tournament service uses Mom/UMD factor as benchmark (replaces synthetic momentum)
- [ ] WireMock tests cover: successful ZIP download, 404, corrupt ZIP, malformed CSV rows
- [ ] Python analytics tests verify 5-factor regression against known factor values

### Phase 5 — Yield Curve

- [ ] `FredClient` fetches 8 yield curve tenors (DGS1MO through DGS30)
- [ ] Data stored in `rate_snapshots` with CDM-aligned rate_type values
- [ ] 2Y–10Y spread and 3M–10Y spread computable from stored data
- [ ] Flyway migration V19 extends CHECK constraint without data loss

### Phase 6 — Alpha Vantage Historical & Commodity

- [ ] `AlphaVantageClient` fetches adjusted daily OHLCV for all configured symbols
- [ ] Adjusted close reflects split and dividend corrections (spot-check against known corporate actions)
- [ ] Commodity daily prices fetched for gold, crude oil, natural gas, copper
- [ ] Rate limiting enforced: scheduler spaces calls at 12.1s intervals (≤5/min)
- [ ] Incremental mode fetches only new rows since last import (tracked in `data_import_tracker`)
- [ ] Initial full load fetches 20+ year history on first run per symbol
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] Commodity data identifiable by `InstrumentType.COMMODITY` subtype in CDM
- [ ] WireMock tests cover: success, 429 rate limit, 401 invalid key, malformed JSON, empty series
- [ ] Flyway migration V21 adds commodity instrument types and `data_import_tracker` table

### Phase 7 — DataHub Historical Backfill

- [ ] `DataHubBackfillClient` fetches all 5 configured CSV files from stable r-link URLs
- [ ] S&P 500 Shiller data (1871–present) loaded into `index_snapshots` with price, dividend, earnings, CPI, CAPE
- [ ] VIX daily data (1990–present) loaded into `rate_snapshots` with `rate_type='VIX'`
- [ ] WTI daily (1986–present) and Brent daily (1987–present) loaded into `rate_snapshots`
- [ ] Gold monthly (1833–present) loaded into `rate_snapshots` with `rate_type='GOLD'`
- [ ] Startup full-load works when target tables are empty
- [ ] Incremental monthly check imports only new rows since `max(timestamp)` — no duplicates
- [ ] Shiller PE10 values of 0.0 (1871–1880) stored as NULL
- [ ] Gold pre-1960 annual-averaged rows annotated in metadata
- [ ] WireMock tests cover: success, 404, empty CSV, malformed rows, schema drift
- [ ] Backtesting services receive S&P 500 benchmark for walk-forward validation
- [ ] Regime detection receives VIX for volatility regime classification
- [ ] Macro shock service receives oil/gold for energy and safe-haven impulse responses
- [ ] Flyway migration V22 extends `chk_rate_type_cdm` and creates `index_snapshots` hypertable

### Phase 8 — Cleanup

- [ ] No Polygon import remains in `ingestion/build.gradle`
- [ ] No `openbb.*` configuration keys in `application.yml`
- [ ] No `FederationDataClient` or `OpenBBAnalyticsClient` interfaces remain
- [ ] `docker-compose.openbb.yml` removed from repository
- [ ] `.env.example` lists `FRED_API_KEY`, `FINNHUB_API_KEY`, and `ALPHAVANTAGE_API_KEY`
- [ ] All tests pass after cleanup

---

## 10. Dependency Impact

### Modules Modified

| Module | Change | Backward Compatible |
|:-------|:-------|:--------------------|
| `ingestion` | New equity, ws, news, options, factor, alphavantage, datahub clients | Yes (additive) |
| `cdm` | New adapters, extended `RateType` enum (`VIX`, `OIL_WTI`, `OIL_BRENT`, `GOLD`), `FactorReturn` model, `FactorSet` enum, `InstrumentType.COMMODITY` subtypes, `InstrumentType.INDEX`, `IndexSnapshot` model | Yes (additive) |
| `api-contracts` | `EquityWsClient` interface (renamed) | No (rename) |
| `persistence` | V19 for yield curve enums, V20 for `factor_returns`, V21 for commodity types + `data_import_tracker`, V22 for DataHub backfill (`index_snapshots`, extended `chk_rate_type_cdm`) | Yes (additive) |
| `app` | Updated `application.yml`, `.env.example` | No (config changes) |
| `web` | No changes (consumes computation layer) | Yes |
| `computation` | `MonetaryPolicySensitivityService` updated; `ClimateRiskGuard` consumes commodity data | Yes (additive) |
| `analytics` | `fama_french_regression()` upgraded to 5-factor; tournament service uses Mom benchmark | No (behavior change) |
| `frontend` | No changes | Yes |

### Docker Compose Changes

| Service | Action |
|:--------|:-------|
| `openbb` | Remove from `docker-compose.openbb.yml` |
| Main app | No changes (runs same Java process) |
| Analytics worker | No changes |

---

## Changelog

| Version | Change |
|:--------|:-------|
| 1.0 | Initial plan: 6 free data sources replacing 2 paid Polygon subscriptions and removing the unimplemented OpenBB sidecar. 5 implementation phases. |
| 1.1 | Added Ken French Data Library (§3.7) as P1 source for Fama-French factor returns. New Phase 4 (factor ingestion + 5-factor upgrade + Mom benchmark). Added `factor_returns` hypertable, `FrenchFactorClient`, `FrenchFactorCdmAdapter`. Renumbered Yield Curve → Phase 5, Cleanup → Phase 6. |
| 1.2 | Added Alpha Vantage (§3.8) as P2 source for deep historical equity OHLCV (20+ years, split/dividend adjusted), commodities (gold, oil, gas, copper), and fundamental data. New Phase 6 (Alpha Vantage ingestion with incremental fetch, rate-limit-aware scheduler, commodity CDM types). Renumbered Cleanup → Phase 7. Added `AlphaVantageClient`, `AlphaVantageCdmAdapter`, `AlphaVantageRawModels`, `data_import_tracker` table, `InstrumentType.COMMODITY` subtypes. Updated rate limit analysis (~14–22 calls/day, 4.4% of free tier cap). |
| 1.3 | Added DataHub (§3.9) as P1/P2 source for deep-historical CSV backfill: S&P 500 Shiller (1871–present, CAPE), VIX (1990–present daily), oil WTI/Brent (1986–present daily), gold (1833–present monthly). Zero-cost, zero-auth, stable CDN URLs. New Phase 7 (DataHub ingestion with startup full-load + monthly incremental, `index_snapshots` hypertable, extended `rate_snapshots` CHECK constraint for `VIX`/`OIL_WTI`/`OIL_BRENT`/`GOLD`). Renumbered Cleanup → Phase 8. Added `DataHubBackfillClient`, `DataHubBackfillScheduler`, `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter`, `GoldPriceCdmAdapter`, `index_snapshots` hypertable. Updated data gaps (added historical equity benchmark + VIX), source diagram, rate limit analysis. |

---

## Appendix: Deferred Items

> *Merged from `deferred-items-implementation-plan.md` during plan_v6 consolidation.*

## 1. OpenBBClient (Java)

**SUPERSEDED by v6 Free Data Source Migration.** The Java OpenBB client was never built and will never be built because the OpenBB sidecar is removed entirely. Equity price data is now fetched via `YahooFinanceClient` (primary) and `FinnhubEquityClient` (fallback). See `04-ingestion-layer.md` Section 3.

### Current State

- `docker-compose.openbb.yml` already defines the OpenBB Platform sidecar on port 8002
- The Python analytics worker can query OpenBB via REST
- FRED and NY Fed clients cover rate data; Polygon covers tick data
- No Java client exists to query OpenBB from the ingestion layer

### Trigger Condition

The equity/ETF universe grows beyond what Polygon provides (e.g., international equities, alternative data feeds). When more than 3 data sources require OpenBB as the primary path, implement the Java client.

### Specification

**Package:** `com.tickonomics.ingestion.openbb`

**Classes to create:**

| Class | Responsibility |
|-------|---------------|
| `OpenBBClient` | REST client for OpenBB Platform API |
| `OpenBBConfig` | `@ConfigurationProperties(prefix = "openbb")` record |
| `OpenBBHealthIndicator` | Actuator health indicator for OpenBB connectivity |

**OpenBBClient methods:**

```
List<EquityPrice> getEquityPrices(String symbol, String range, String interval)
List<EtfHolding> getEtfHoldings(String symbol)
Map<String, Object> getEconomicData(String seriesCode)
boolean isHealthy()
```

**OpenBBConfig fields:**

| Field | Default | Description |
|-------|---------|-------------|
| `baseUrl` | `http://localhost:8002` | OpenBB Platform URL |
| `apiKey` | (empty) | OpenBB API key if authentication enabled |
| `timeoutMs` | `5000` | HTTP request timeout |
| `retryAttempts` | `2` | Retry count on transient failures |
| `enabled` | `false` | Feature flag to activate OpenBB ingestion |

**Integration points:**

- `OpenBBClient` called from `TimescaleDbWriter` to persist equity prices
- `CdmAdapter` extended with `OpenBBCdmAdapter` for data normalization
- `AlphaSignal` enhanced with OpenBB-sourced fundamental data

### Dependencies

- `docker-compose.openbb.yml` already exists
- `com.tickonomics.ingestion.external` package pattern (similar to `FredClient`, `NyFedClient`)
- Resilience4j retry + bulkhead already configured in `application.yml`

### File Manifest

```
ingestion/src/main/java/com/tickonomics/ingestion/openbb/
  OpenBBClient.java
  OpenBBConfig.java
  OpenBBHealthIndicator.java
ingestion/src/test/java/com/tickonomics/ingestion/openbb/
  OpenBBClientTest.java
  OpenBBConfigTest.java
  OpenBBHealthIndicatorTest.java
app/src/main/resources/application.yml  (add openbb.* properties)
```

### Test Plan

- Unit: mock REST calls, verify response parsing, error handling, retry logic
- Integration: Testcontainers with mock OpenBB server
- Health: verify health indicator reports UP/DOWN correctly

---


## 2. AnomalyDetectionWorker (Java)

### Current State

- The Python analytics worker (`analytics/app/services/anomaly/anomaly_service.py`) performs autoencoder-based anomaly detection
- `RestClientAnalyticsWorkerClient` calls `/api/v1/anomaly/detect` from Java
- If the analytics worker is down, anomaly detection silently fails (no fallback)
- Latency: ~200ms per call due to HTTP round-trip + Python inference

### Trigger Condition

Anomaly detection latency requirement drops below 50ms (e.g., real-time toxic flow detection during high-frequency periods). Or the analytics worker becomes a single point of failure for critical detection.

### Specification

**Package:** `com.tickonomics.ingestion.anomaly`

**Classes to create:**

| Class | Responsibility |
|-------|---------------|
| `AnomalyDetectionWorker` | Async anomaly detection with Java-side fallback |
| `AnomalyDetectionConfig` | Configuration for thresholds and fallback behavior |
| `ThresholdBasedDetector` | Simple statistical fallback when ML unavailable |

**AnomalyDetectionWorker methods:**

```
AnomalyResult detect(double[] features)
CompletableFuture<AnomalyResult> detectAsync(double[] features)
void updateBaseline(double[] features)
```

**Record AnomalyResult:**

```
double score          // 0.0 to 1.0 anomaly probability
boolean isAnomaly     // score > threshold
String method         // "ML" or "THRESHOLD_FALLBACK"
long latencyMs
```

**ThresholdBasedDetector methods:**

```
AnomalyResult detect(double[] features)
  // Uses z-score: anomaly if any feature > 3 sigma from baseline
  // Maintains running mean/std with Welford's online algorithm
```

**AnomalyDetectionConfig fields:**

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `false` | Feature flag |
| `timeoutMs` | `5000` | Async detection timeout |
| `fallbackThreshold` | `3.0` | Z-score threshold for fallback |
| `baselineWindowSize` | `1000` | Rolling window for statistics |

**Integration flow:**

1. Primary: call Python analytics worker via `RestClientAnalyticsWorkerClient`
2. Fallback: use `ThresholdBasedDetector` if worker unavailable or timeout exceeded
3. Async: wrap in `CompletableFuture` with `StructuredTaskScope` for cancellation

### Dependencies

- `RestClientAnalyticsWorkerClient` already exists
- `TimescaleDbWriter` can consume `AnomalyResult` for storage
- Resilience4j retry on `analyticsWorker` already configured

### File Manifest

```
ingestion/src/main/java/com/tickonomics/ingestion/anomaly/
  AnomalyDetectionWorker.java
  AnomalyDetectionConfig.java
  ThresholdBasedDetector.java
ingestion/src/test/java/com/tickonomics/ingestion/anomaly/
  AnomalyDetectionWorkerTest.java
  AnomalyDetectionConfigTest.java
  ThresholdBasedDetectorTest.java
```

### Test Plan

- Unit: mock analytics client, verify fallback activation, z-score correctness
- Async: verify timeout triggers fallback within 5s
- Baseline: test Welford's algorithm convergence with known distributions
- Edge: empty features, single feature, all-zero features

---


## 3. Chronicle Queue Integration

### Current State

- `TieredIngestionBuffer` uses `InMemoryIngestionBuffer` + `FileOverflowBuffer`
- `FileOverflowBuffer` writes serialized events to disk when memory threshold exceeded
- Works well for current throughput (~5000 ticks/sec)
- No off-heap memory management or pre-allocated ring buffer

### Trigger Condition

Sustained ingestion exceeds 50,000 ticks/sec with sub-millisecond latency requirement. FileOverflowBuffer's disk I/O becomes the bottleneck.

### Specification

**Package:** `com.tickonomics.ingestion.buffer`

**Classes to create:**

| Class | Responsibility |
|-------|---------------|
| `ChronicleRingBuffer` | Off-heap ring buffer using Chronicle Queue |
| `ChronicleBufferConfig` | Configuration for paths, sizes, roll cycles |

**ChronicleRingBuffer methods:**

```
boolean offer(TickData event)              // non-blocking write
TickData poll()                             // non-blocking read
int size()                                  // approximate depth
boolean isFull()
void close()                                // release native resources
```

**ChronicleBufferConfig fields:**

| Field | Default | Description |
|-------|---------|-------------|
| `basePath` | `${java.io.tmpdir}/tickonomics/chronicle` | Directory for queue files |
| `rollCycle` | `MINUTELY` | How often new queue files created |
| `bufferSizeMb` | `256` | Pre-allocated off-heap size |
| `cleanupOnClose` | `true` | Delete queue files on graceful shutdown |

**Integration:**

- `ChronicleRingBuffer` replaces `InMemoryIngestionBuffer` as TieredIngestionBuffer's first tier
- `FileOverflowBuffer` remains as the second tier (spillover from Chronicle)
- Feature-flagged: `ingestion.buffer.chronicle.enabled=true` activates

### Dependencies

```groovy
// ingestion/build.gradle
implementation 'net.openhft:chronicle-queue:5.25ea'
implementation 'net.openhft:chronicle-bytes:2.25ea'
```

- Requires `libstdc++` on the host (bundled in Chronicle Queue JARs)
- Docker: dedicated volume mapped to `basePath` for persistence
- Native memory tracking: `-XX:NativeMemoryTracking=summary` JVM flag

### File Manifest

```
ingestion/src/main/java/com/tickonomics/ingestion/buffer/
  ChronicleRingBuffer.java
  ChronicleBufferConfig.java
ingestion/src/test/java/com/tickonomics/ingestion/buffer/
  ChronicleRingBufferTest.java
  ChronicleBufferConfigTest.java
docker-compose.yml                          (add chronicle volume)
build.gradle                                (add Chronicle dependencies)
app/src/main/resources/application.yml      (add chronicle config)
```

### Docker Changes

```yaml
# docker-compose.yml additions
services:
  backend:
    volumes:
      - chronicle_queue:/opt/tickonomics/chronicle

volumes:
  chronicle_queue:
    driver: local
```

### Test Plan

- Unit: single write/read cycle, ring wrap-around, back-pressure when full
- Performance: benchmark 100k writes/sec with `@Timeout(10)` and JMH
- Recovery: verify data survives process crash (restart and replay)
- Resource: verify off-heap memory released on `close()`
- Integration: test with TieredIngestionBuffer fallback chain

---


## 6. DataHub Deferred Datasets

### Current State

- DataHub (see plan_v6 numbered documents, primarily `04-ingestion-layer.md` Section 28) provides free, no-auth CSV datasets via stable CDN URLs
- Five core datasets are being integrated in Phase 7 of the free data sources plan: S&P 500 (Shiller), VIX, WTI/Brent oil, gold
- Four additional DataHub datasets are intentionally deferred because they do not align with the current project scope (macro/index-level signals, liquidity stress detection)

### Deferred Datasets

| Dataset | DataHub Path | Data | Granularity | Reason for Deferral |
|:--------|:-------------|:-----|:------------|:--------------------|
| Natural Gas (Henry Hub) | `core/natural-gas` | US natural gas spot prices | Monthly | Secondary energy commodity; WTI/Brent oil covers the primary macro-energy channel. Defer until energy analysis expands beyond crude oil |
| S&P 500 Company Lists | `core/s-and-p-500-companies` | Current S&P 500 constituent list (tickers, sectors, sub-industries) | Snapshot | Project focuses on macro/index-level signals, not individual stock screening. Defer until universe expands to individual equity analysis |
| NYSE/NASDAQ Listings | `core/nyse-other-listings`, `core/nasdaq-listings` | Listed companies on NYSE and NASDAQ exchanges | Snapshot | Not applicable to current index-focused architecture. Defer until individual stock coverage is required |
| S&P 500 Companies Financials | `core/s-and-p-500-companies-financials` | S&P 500 companies with price, market cap, earnings, P/E, P/B | Snapshot | Fundamental data available via Alpha Vantage with daily updates and deeper history. Defer if Alpha Vantage fundamentals prove insufficient |

### Trigger Condition

The project scope expands beyond core macro/index-level analysis into one or more of these domains:
- Individual equity analysis requiring company-level data
- Multi-commodity energy analysis requiring natural gas as an independent factor
- Fundamental screening requiring cross-sectional company data beyond what Alpha Vantage provides

### High-Level Integration Plan

When triggered, these datasets follow the same `DataHubBackfillClient` pattern established in Phase 7 of the free data sources plan:

**Natural Gas:**

1. Add `core/natural-gas/_r/-/data/*.csv` to `datahub.datasets` config in `application.yml`
2. Create `NaturalGasCdmAdapter` — CSV row → `CdmRateSnapshot` with `rate_type='NATURAL_GAS'`
3. Extend `chk_rate_type_cdm` CHECK constraint with `'NATURAL_GAS'`
4. Wire to `MacroShockService` for expanded energy impulse response functions
5. Estimated effort: 0.5 day

**S&P 500 Company Lists:**

1. Add `core/s-and-p-500-companies/_r/-/data/*.csv` to config
2. Create `Sp500ConstituentsCdmAdapter` — CSV row → `ConstituentSnapshot` CDM record
3. Create `sp500_constituents` hypertable (time, symbol, sector, sub_industry, added_date, removed_date)
4. Wire to universe selection logic for expanded equity coverage
5. Estimated effort: 1 day

**NYSE/NASDAQ Listings:**

1. Add both dataset CSV URLs to config
2. Create `ExchangeListingsCdmAdapter` — CSV row → `ListingSnapshot` CDM record
3. Create `exchange_listings` hypertable (time, symbol, exchange, name, etf_flag)
4. Wire to universe expansion and symbol resolution
5. Estimated effort: 1 day

**S&P 500 Companies Financials:**

1. Add `core/s-and-p-500-companies-financials/_r/-/data/*.csv` to config
2. Create `Sp500FinancialsCdmAdapter` — CSV row → `FundamentalSnapshot` CDM record
3. Create `sp500_fundamentals` hypertable (time, symbol, market_cap, pe_ratio, pb_ratio, dividend_yield)
4. Wire to value factor signals and cross-sectional analysis
5. Estimated effort: 0.5 day

### Dependencies

- Phase 7 of the free data sources plan must be complete (establishes `DataHubBackfillClient`, CSV parsing, and TimescaleDB write patterns) — see plan_v6 numbered documents (primarily `04-ingestion-layer.md` Section 28)
- CDM may need new model types (`ConstituentSnapshot`, `ListingSnapshot`, `FundamentalSnapshot`) if these do not fit existing tables

### File Manifest

```
ingestion/src/main/java/com/tickonomics/ingestion/datahub/
  DataHubBackfillClient.java              # (existing, extended with new dataset configs)
cdm/src/main/java/com/tickonomics/cdm/adapter/
  NaturalGasCdmAdapter.java               # (new, when natural gas triggered)
  Sp500ConstituentsCdmAdapter.java        # (new, when company lists triggered)
  ExchangeListingsCdmAdapter.java         # (new, when exchange listings triggered)
  Sp500FinancialsCdmAdapter.java          # (new, when financials triggered)
persistence/src/main/resources/db/migration/
  V23__datahub_deferred_datasets.sql      # (new hypertables + CHECK constraints)
app/src/main/resources/application.yml    # (add dataset entries under datahub.datasets.*)
```

### Test Plan

- Unit: CSV parsing for each new dataset, CDM adapter field mapping
- Integration: full-load + incremental import via `DataHubBackfillClient`
- Edge: empty CSV, schema changes, missing fields

---

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 2. Ingestion Layer — 7 Missing Components
**Plan ref:** `04-ingestion-layer.md`

| #   | Component                       | Plan Section  | Description                                                                                                 |
| --- | ------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------- |
| 1   | `DisasterAlertClient`           | §Component 15 | USGS Earthquake + GDACS RSS polling, EXOGENOUS_SHOCK regime trigger                                         |
| 2   | `AlgorithmicSanityGuard`        | §Component 19 | Price >10% in <1s or >10000 msg/sec detection, Manual Oversight trigger                                     |
| 3   | `OrderCancellationMonitor`      | §Component 23 | Cancellation ratios per symbol, volatility leading indicator                                                |
| 4   | `OpenBBClient` (Java)           | §Component 3  | REPLACED by YahooFinanceClient + FinnhubEquityClient (v6 free data source migration)                        |
| 5   | `EventBasedTimeConverter`       | §Component 14 | Maps raw ticks to directional change and overshoot events                                                   |
| 6   | `AnomalyDetectionWorker` (Java) | §Component 10 | Async autoencoder-based anomaly detection in Java                                                           |
| 7   | **Chronicle Queue integration** | §Component 6  | Off-heap ring buffer + disk-backed overflow (replaced by `TieredIngestionBuffer` with `FileOverflowBuffer`) |

> **Note:** Items 4, 6, and 7 may be intentional design simplifications — the analytics worker handles anomaly detection in Python, and `FileOverflowBuffer` replaces Chronicle Queue. `OpenBBClient` was replaced by YahooFinanceClient + FinnhubEquityClient in the v6 free data source migration.

---
