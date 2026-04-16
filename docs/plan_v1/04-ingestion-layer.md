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

**Analysis findings applied:**

- **Finding 1 (Virtual Threads Primary):** Single-mode implementation using Virtual Threads.
  No dual-mode parity. Client interfaces are defined without coupling to a specific HTTP
  library so WebFlux adapters can be added later in `webflux/` module.
- **Finding 5 (Direct FRED/NY Fed Clients):** Core ILI data sources fetched via direct
  lightweight Java HTTP clients — no OpenBB dependency on the critical ingestion path.
  OpenBB sidecar retained only for equity prices (long-tail data).
- **Finding 7 (Chronicle Queue Placement):** Overflow queue mapped to dedicated storage
  (`tmpfs` or separate NVMe partition) to prevent I/O contention with TimescaleDB.

---

## Components to Implement

### 1. Direct FRED Client (Finding 5)

Lightweight Java HTTP client for FRED REST API — no OpenBB dependency.

**Implementation:**

- `@Scheduled` with `SimpleAsyncTaskExecutor` configured for virtual threads.
- Uses `java.net.http.HttpClient` with `StructuredTaskScope` for parallel series fetches.
- Direct FRED API:
  `https://api.stlouisfed.org/fred/series/observations?series_id={symbol}&api_key={key}&observation_start={date}&file_type=json`

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

### 5. TimescaleDB Writer

- Accumulates ticks in a thread-safe buffer.
- Flushes via batched `INSERT`:
    - Configurable `batch-size` (default 500).
    - Configurable `flush-interval` (default 500ms).
    - Standard PostgreSQL JDBC with HikariCP connection pool.
- Handles connection pool management.
- Uses `INSERT ... ON CONFLICT DO NOTHING` for deduplication.

### 6. Disk-Backed Ingestion Buffer (Finding 7)

- **Tier 1:** In-memory ring buffer (capacity from config, default 1M events).
- **Tier 2:** Chronicle Queue disk-backed overflow when memory > 80%.
- **Storage (Finding 7):** Chronicle Queue path mapped to dedicated storage:
    - Production: separate NVMe partition from TimescaleDB `data` directory.
    - Development: `tmpfs` mount for transient spikes.
    - Configuration: `chronicle_queue_storage_type: "dedicated_nvme"` or `"tmpfs"`.
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

### 8. Circuit Breakers (Resilience4j)

| Target                         | Config                                       |
|:-------------------------------|:---------------------------------------------|
| FRED API (direct)              | Opens after 5 failures, half-opens after 30s |
| NY Fed API (direct)            | Opens after 5 failures, half-opens after 30s |
| OpenBB sidecar (equity prices) | Opens after 5 failures, half-opens after 30s |
| Polygon WebSocket              | Opens after 5 failures, half-opens after 30s |
| Analytics worker               | Opens after 5 failures, half-opens after 30s |

### 9. Retry Policy (Resilience4j)

- All external HTTP: 3 retries, exponential backoff (1s, 2s, 4s).
- Configurable via `monitor.openbb.retry-*` and `monitor.fred.retry-*`.

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
│   ├── openbb/
│   │   ├── OpenBBIngestionClient.java              # Interface
│   │   ├── OpenBBIngestionClientImpl.java          # RestClient impl (equity prices only)
│   │   └── OpenBBEndpoints.java                    # Endpoint constants
│   ├── polygon/
│   │   └── PolygonWsClient.java                    # Direct WebSocket client
│   ├── buffer/
│   │   ├── IngestionBuffer.java                    # Interface
│   │   ├── InMemoryIngestionBuffer.java            # Tier 1
│   │   ├── ChronicleQueueOverflow.java             # Tier 2 (dedicated storage, Finding 7)
│   │   └── DiskBackedIngestionBuffer.java          # Composite
│   ├── writer/
│   │   └── TimescaleDbWriter.java                  # Batched INSERT
│   ├── quality/
│   │   ├── DataQualityChecker.java                 # Staleness, outliers
│   │   └── ProxyDivergenceMonitor.java             # T-Bill/SOFR divergence (Finding 3)
│   ├── config/
│   │   └── IngestionConfig.java                    # @ConfigurationProperties
│   └── resilience/
│       └── CircuitBreakerConfig.java               # Resilience4j config
└── src/test/java/com/tickonomics/ingestion/
    ├── fred/
    │   └── FredClientTest.java                     # WireMock
    ├── nyfed/
    │   └── NyFedClientTest.java                    # WireMock
    ├── openbb/
    │   └── OpenBBIngestionClientTest.java           # WireMock
    ├── polygon/
    │   └── PolygonWsClientTest.java                # Mock WebSocket
    ├── writer/
    │   └── TimescaleDbWriterTest.java              # Testcontainers
    └── buffer/
        └── DiskBackedIngestionBufferTest.java
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

---

## Self-Monitoring Alerts

- Chronicle Queue depth > 50% of disk quota: log `OVERFLOW_QUEUE_GROWING`.
- FRED API unreachable > 5 min: log `FRED_API_DOWN`.
- NY Fed API unreachable > 5 min: log `NYFED_API_DOWN`.
- OpenBB sidecar unreachable > 5 min: log `OPENBB_SIDECAR_DOWN` (equity prices degraded).
- No new ticks in > stale threshold: log `DATA_STALE`.
- Polygon WS disconnected > 5 min: log `POLYGON_WS_FALLBACK`.
- T-Bill/SOFR divergence > 2 std deviations: log `PROXY_DIVERGENCE_DETECTED` (Finding 3).

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
