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
  lightweight Java HTTP clients — no OpenBB dependency on the critical ingestion path.
  OpenBB sidecar retained only for equity prices (long-tail data).
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
  system into Manual Oversight state. Options OI/IV data ingestion via Polygon Options API.
  Global Safe Mode throttles `TimescaleDbWriter` and stops signal dispatch on correlated degradation.
- **Proposal 07 (Liquidity — ToxicityMonitor, Economic Calendar, OrderCancellationMonitor, OFI):**
  `ToxicityMonitor` computes per-venue toxicity scores from order-to-trade ratios and round-trip
  percentages. Economic calendar client polls high-impact news event schedules. `PolygonWsClient`
  extended to capture Order Book Depth for real-time OFI calculation. `OrderCancellationMonitor`
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

### 3. OpenBB Client (Equity Prices Only, Finding 5)

OpenBB sidecar used **only** for equity/ETF price data (long-tail, not on critical ILI path).

**Implementation:**

- `@Scheduled` with virtual threads.
- `RestClient` for HTTP calls.
- Fallback: direct FMP/Intrinio API if OpenBB sidecar is unavailable.

**Endpoints to poll:**

| Data          | OpenBB Endpoint                   | Provider            | Frequency |
|:--------------|:----------------------------------|:--------------------|:----------|
| Equity prices | `/api/v1/equity/price/historical` | `fmp` or `intrinio` | Daily     |
| ETF prices    | `/api/v1/equity/price.historical` | `fmp`               | Daily     |

### 4. Polygon WebSocket Client

Direct WebSocket to Polygon.io — not routed through OpenBB.

**Implementation:**

- `java.net.http.HttpClient` WebSocket on virtual threads.
- Blocking message handler with virtual thread per message.

**Behavior:**

- Connect to `wss://socket.polygon.io/stocks` with API key handshake.
- Subscribe to symbols from configured watchlists.
- Auto-reconnect with exponential backoff (1s → 60s max).
- Fallback: if disconnected > 5 min, switch to equity aggregates via direct API.
- Ticks written to `tick_data` hypertable via `TimescaleDbWriter`.
- **Idempotency key (v4):** Each WebSocket message receives a UUID idempotency key derived
  from the Polygon sequence number to ensure exactly-once write semantics.

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
| OpenBB sidecar (equity prices) | Opens after 5 failures, half-opens after 30s |
| Polygon WebSocket              | Opens after 5 failures, half-opens after 30s |
| Analytics worker               | Opens after 5 failures, half-opens after 30s |
| USGS Earthquake API (v4)       | Opens after 5 failures, half-opens after 60s |
| GDACS RSS feed (v4)            | Opens after 5 failures, half-opens after 60s |
| Options API (Polygon) (v5)     | Opens after 5 failures, half-opens after 30s |
| Economic Calendar API (v5)     | Opens after 5 failures, half-opens after 60s |

### 9. Retry Policy (Resilience4j)

- All external HTTP: 3 retries, exponential backoff (1s, 2s, 4s).
- Configurable via `monitor.openbb.retry-*` and `monitor.fred.retry-*`.

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

- Every external API request (FRED, NY Fed, Polygon) and every WebSocket message carries a
  UUID idempotency key.
- `TimescaleDbWriter` updated to use `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING`
  on all write operations. The `idempotency_key` column is added to each ingestion target
  hypertable with a `UNIQUE` constraint (Track 2).
- `IngestionDLQ` schema updated to include `idempotency_key` column. When a message is
  retried from the DLQ, the original idempotency key is preserved to prevent double-writes.

**Rationale:**
Network retries during transient failures can produce duplicate tick data. Idempotency keys
ensure that even if the same FRED observation or Polygon tick is processed multiple times,
only one row is written to TimescaleDB. This prevents duplicate data from skewing ILI
calculations and producing false signals.

### 12. Last Known Good (LKG) Cache (Proposal 02: Resilience)

In-memory cache for graceful degradation when data sources are unreachable.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/cache/LastKnownGoodCache.java`

**Implementation:**

- Maintains an in-memory cache of the last successfully fetched value for each critical data
  source (FRED, NY Fed, OpenBB).
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
2. **High-Volume Ingestion** (Polygon WS) — large pool, tolerant of delays. WebSocket tick
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
If the Polygon WebSocket stream hangs due to network saturation or reconnection storms, the
ILI calculation and core FRED/NY Fed ingestion threads remain unaffected. Without bulkhead
isolation, a single misbehaving service (e.g., a slow WebSocket reconnection) can starve the
thread pool required for core KPI processing.

### 14. EventBasedTimeConverter (Proposal 03: Regime Detection)

Event-driven alternative to time-based tick aggregation.

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/time/EventBasedTimeConverter.java`

**Implementation:**

- Maps raw tick data from Polygon into discrete events: directional changes and overshoots.
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
- **News sentiment trigger:** Uses the OpenBB news endpoint to search for keywords:
  `Earthquake`, `Tsunami`, `Hurricane`. When a matching news cluster is detected alongside
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
    - Polygon WS message processing (`ingestion.polygon.message`)
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
reconstruct the full request path from Polygon tick ingestion through ILI computation to
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

### 20. Options Data Ingestion Client (Proposal 06: GEX Monitor)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/options/OptionsDataClient.java`

Options chain data ingestion via Polygon Options API.

**Implementation:**

- Polls options chain data (Open Interest and Volume at various strikes) via Polygon Options API.
- Ingests ATM and OTM implied volatilities for BSM Greeks calculation.
- Data stored in `market_events` hypertable with event_type `OPTIONS_CHAIN_UPDATE`.
- Separate circuit breaker from equity data sources.
- Scheduled polling at configurable interval (default 60s during market hours, disabled outside).

**Configuration:**

```yaml
monitor:
  ingestion:
    options:
      enabled: false
      api_url: "https://api.polygon.io/v3/snapshot/options"
      symbols: ["SPY", "QQQ"]
      poll_interval: "60s"
      market_hours_only: true
```

### 21. ToxicityMonitor (Proposal 07: Trader Toxicity)

**Location:** `ingestion/src/main/java/com/tickonomics/ingestion/quality/ToxicityMonitor.java`

Per-venue toxicity scoring from order flow patterns.

**Implementation:**

- Computes toxicity metrics from Polygon trade data:
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
- Requires L2 order book data from Polygon (if available) or estimated from trade condition patterns.

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

  openbb:
    base-url: "http://localhost:8000"
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  polygon:
    ws-url: "wss://socket.polygon.io/stocks"
    api-key: "${POLYGON_API_KEY}"
    reconnect-backoff-max: "60s"

  timescaledb:
    ingestion:
      batch-size: 500
      flush-interval: "500ms"

  ingestion:
    fred_sync_interval: "6h"
    nyfed_sync_interval: "6h"
    openbb_sync_interval: "6h"
    polygon_ws_enabled: true
    polygon_rest_fallback: true
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
    api_url: "https://api.polygon.io/v3/snapshot/options"
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
│   │   └── PolygonTickCdmAdapter.java             # PolygonTick → CdmTick
│   ├── openbb/
│   │   ├── OpenBBIngestionClient.java              # Interface
│   │   ├── OpenBBIngestionClientImpl.java          # RestClient impl (equity prices only)
│   │   └── OpenBBEndpoints.java                    # Endpoint constants
│   ├── polygon/
│   │   └── PolygonWsClient.java                    # Direct WebSocket client
│   ├── filter/
│   │   ├── DeRoundingFilter.java            # v5: Round-mark volume smoothing (Proposal 05)
│   │   └── TimePeriodicityFilter.java       # v5: 1-second spike removal (Proposal 05)
│   ├── guard/
│   │   └── AlgorithmicSanityGuard.java      # v5: Extreme condition detection (Proposal 06)
│   ├── options/
│   │   └── OptionsDataClient.java           # v5: Polygon Options API (Proposal 06)
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
    ├── openbb/
    │   └── OpenBBIngestionClientTest.java           # WireMock
    ├── polygon/
    │   └── PolygonWsClientTest.java                # Mock WebSocket
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

- `monitor.ingestion.events.total` — counter, tagged by source (`fred`, `nyfed`, `openbb`, `polygon-ws`).
- `monitor.ingestion.latency` — timer from event receipt to TimescaleDB write.
- `monitor.ingestion.fred.latency` — timer for FRED API round-trips.
- `monitor.ingestion.nyfed.latency` — timer for NY Fed API round-trips.
- `monitor.ingestion.openbb.latency` — timer for OpenBB REST round-trips.
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

---

## Self-Monitoring Alerts

- Chronicle Queue depth > 50% of disk quota: log `OVERFLOW_QUEUE_GROWING`.
- FRED API unreachable > 5 min: log `FRED_API_DOWN`.
- NY Fed API unreachable > 5 min: log `NYFED_API_DOWN`.
- OpenBB sidecar unreachable > 5 min: log `OPENBB_SIDECAR_DOWN` (equity prices degraded).
- No new ticks in > stale threshold: log `DATA_STALE`.
- Polygon WS disconnected > 5 min: log `POLYGON_WS_FALLBACK`.
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

---

## Validation

- [ ] FRED client fetches all required series (EFFR, RRP, TGA, WALCL, IORB) with deduplication.
- [ ] NY Fed client fetches SOFR, TGCR, BGCR, Treasury rates with deduplication.
- [ ] OpenBB client fetches equity/ETF prices (non-critical path).
- [ ] Direct FRED/NY Fed clients work without OpenBB sidecar running (Finding 5).
- [ ] Polygon WS connects, subscribes, receives ticks, writes to TimescaleDB.
- [ ] Auto-reconnect works after Polygon WS disconnect.
- [ ] Fallback to equity aggregates via direct API after 5 min Polygon outage.
- [ ] Batched INSERT flushes at correct batch-size and interval.
- [ ] Chronicle Queue overflow activates when memory > 80%.
- [ ] Chronicle Queue uses dedicated storage path (Finding 7).
- [ ] Overflow replays correctly on reconnect.
- [ ] Circuit breaker opens after 5 failures and half-opens after 30s for each source.
- [ ] Staleness detection marks series `STALE` after configured threshold.
- [ ] Outlier detection flags > 50 bps moves as `SUSPECT`.
- [ ] Proxy divergence monitor detects T-Bill/SOFR dislocation (Finding 3).
- [ ] Unit tests with WireMock (FRED, NY Fed, OpenBB) and mocked WebSocket servers.
- [ ] Integration tests with TimescaleDB testcontainers.
- [ ] Off-heap buffer allocated via `ByteBuffer.allocateDirect()` with no per-tick heap allocations (v3).
- [ ] GC pause gauge (`monitor.buffer.gc.pause_ms`) reports during flush cycles (v3).
- [ ] Chronicle Queue configured with `MAPPED_FILE` mode on dedicated NVMe partition (v3).
- [ ] FRED, NY Fed, Polygon clients produce CDM-typed output via adapters (v3).
- [ ] No source-specific raw types (`FredObservation`, `NyFedRateResponse`) visible outside ingestion module (v3).

**v4 additions:**

- [ ] Idempotency keys prevent duplicate writes during simulated network retries (Proposal 02).
- [ ] `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` correctly deduplicates rows in
  TimescaleDB when the same FRED observation or Polygon tick is processed twice.
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
- [ ] Bulkhead isolates critical ingestion (FRED/NY Fed) from Polygon WS saturation —
  Polygon pool at 100% does not affect FRED fetch latency (Proposal 02).
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
- [ ] OpenTelemetry spans are created for FRED fetch, NY Fed fetch, Polygon WS message,
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
- [ ] Options data client fetches OI and IV from Polygon Options API (when enabled).
- [ ] ToxicityMonitor produces per-symbol toxicity scores with HARMFUL/BENEFICIAL/NEUTRAL classification.
- [ ] Economic calendar client fetches high-impact event schedule.
- [ ] Tick data tagged with session position (PRE_OPEN, DR_WINDOW, POST_DR).
- [ ] OrderCancellationMonitor tracks cancellation ratios per symbol.
- [ ] HighFrequencyAggregator applies realized kernel filters to OHLCV data (v5.2).

### v5.2 Validation Additions (Proposal 13 & 14: Quantitative Engine)

- [ ] `IntegrityPathLogger` tracks 'coding rules' for data transformation (v5.2).
- [ ] `HighFrequencyAggregator` applies realized kernel filters to OHLCV data (v5.2).
- [ ] Polygon options/bonds clients fetch Greeks and yields for strategy inputs (v5.2).

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
