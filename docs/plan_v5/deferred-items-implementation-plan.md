# Deferred Items — Detailed Implementation Plan

**Date:** 2026-06-03
**Status:** Deferred from Plan v5 gap elimination
**Reference:** `docs/plan_v5/gaps/02-ingestion-layer.md`, `08-structural-gaps.md`

---

## Overview

Five items were intentionally deferred during gap elimination. Each has a valid architectural reason for deferral but remains on the roadmap for when the triggering condition materializes.

| # | Item | Trigger Condition | Estimated Effort |
|---|------|-------------------|------------------|
| 1 | OpenBBClient (Java) | Equity/ETF universe exceeds Polygon coverage | 3 days |
| 2 | AnomalyDetectionWorker (Java) | Latency requirement forces in-process detection | 5 days |
| 3 | Chronicle Queue | Ingestion throughput exceeds FileOverflowBuffer capacity | 4 days |
| 4 | webflux/ Subproject | Reactive runtime needed for streaming endpoints | 7 days |
| 5 | analytics/ Java Bridge | Java modules need direct access to analytics computations | 5 days |
| 6 | DataHub Deferred Datasets | Project expands beyond core index/macro analysis scope | 2–3 days |

---

## 1. OpenBBClient (Java)

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

## 4. webflux/ Subproject

### Current State

- All REST endpoints use Spring MVC (servlet-based, `web/` module)
- WebSocket ingestion uses `javax.websocket` via `DefaultPolygonWsClient`
- No reactive pipeline exists
- `settings.gradle` does not include a `webflux` subproject

### Trigger Condition

Streaming endpoints (SSE for live ILI, reactive WebSocket for tick data) are needed with >10,000 concurrent connections. Servlet threading model becomes the bottleneck.

### Specification

**Module:** `webflux/` as a new Gradle subproject

**build.gradle:**

```groovy
plugins { id 'java-library' }
dependencies {
  api project(':computation')
  implementation project(':persistence')
  implementation 'org.springframework.boot:spring-boot-starter-webflux'
  implementation 'io.projectreactor:reactor-core'
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
  testImplementation 'io.projectreactor:reactor-test'
}
```

**Packages to create:**

| Package | Contents |
|---------|----------|
| `com.tickonomics.webflux.handler` | Reactive route handlers |
| `com.tickonomics.webflux.router` | Router function definitions |
| `com.tickonomics.webflux.websocket` | WebSocket handlers for tick streaming |
| `com.tickonomics.webflux.sse` | Server-Sent Event handlers for ILI |

**Key classes:**

| Class | Responsibility |
|-------|---------------|
| `IliSseHandler` | Streams ILI calculations as SSE events |
| `TickWebSocketHandler` | WebSocket handler for real-time tick data |
| `ReactiveIliRouter` | Router function mapping for reactive endpoints |
| `ReactiveConfig` | Netty server configuration |

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/reactive/ili/stream` | SSE stream of ILI calculations |
| WS | `/api/v1/reactive/ticks` | WebSocket for live tick data |
| GET | `/api/v1/reactive/signals/stream` | SSE stream of alpha signals |

**Integration:**

- Runs on a separate Netty port (8081) alongside the MVC server (8080)
- Shares computation and persistence modules with `web/`
- `app/` module includes both `web` and `webflux`
- Feature-flagged: `webflux.enabled=true` in `application.yml`

### Dependencies

- Spring WebFlux (already in Spring Boot BOM)
- Reactor Core (transitive from WebFlux)
- Reactor Netty (transitive)
- `computation` and `persistence` modules (already exist)

### File Manifest

```
settings.gradle                             (add 'webflux')
webflux/build.gradle
webflux/src/main/java/com/tickonomics/webflux/
  handler/IliSseHandler.java
  handler/TickWebSocketHandler.java
  router/ReactiveIliRouter.java
  config/ReactiveConfig.java
webflux/src/test/java/com/tickonomics/webflux/
  handler/IliSseHandlerTest.java
  handler/TickWebSocketHandlerTest.java
  router/ReactiveIliRouterTest.java
app/build.gradle                           (add webflux dependency)
app/src/main/resources/application.yml     (add webflux.* properties)
docker-compose.yml                         (expose port 8081)
```

### Test Plan

- Unit: handler logic with `StepVerifier` for Flux/Mono chains
- Integration: `WebTestClient` for route testing
- WebSocket: `WebSocketClient` for bidirectional testing
- Performance: verify back-pressure handling under load with `Flux.interval`
- SSE: verify event format (`data: ...\n\n`) and reconnect with `Last-Event-ID`

### Risk Assessment

- **Medium risk**: Adding a second web server (Netty alongside Tomcat) increases operational complexity
- **Mitigation**: Feature-flagged, only activated when needed; can run on separate container
- **Alternative**: Consider Spring MVC async (`DeferredResult`, `SseEmitter`) before full WebFlux migration

---

## 5. analytics/ Java Bridge

### Current State

- `analytics/` is a Python FastAPI worker registered in `settings.gradle`
- `RestClientAnalyticsWorkerClient` provides HTTP-based access from Java
- All 24 Python services are accessible via REST endpoints
- No Java-side API contracts or typed clients for analytics computations

### Trigger Condition

Java computation modules need synchronous, low-latency access to analytics computations (e.g., `ClimateSensitivityFactor` calling `/api/v1/climate/simulate` in a hot path). HTTP overhead becomes measurable.

### Specification

**Package:** `com.tickonomics.analytics.bridge`

**Classes to create:**

| Class | Responsibility |
|-------|---------------|
| `AnalyticsBridge` | Direct JNI/JNA bridge to Python via GraalVM or embedded interpreter |
| `AnalyticsBridgeConfig` | Configuration for bridge mode (HTTP vs embedded) |
| `AnalyticsResultMapper` | Maps Python dict responses to Java records |

**Alternative approaches (evaluate before implementing):**

| Approach | Latency | Complexity | When to Use |
|----------|---------|------------|-------------|
| A: Typed HTTP client | ~50ms | Low | Current approach, improve with generated client |
| B: Shared memory (Arrow IPC) | ~5ms | Medium | `ArrowIpcTransport` already exists |
| C: Embedded Python (GraalVM) | ~1ms | High | Only if sustained sub-5ms needed |
| D: Port computation to Java | ~0.01ms | Highest | Long-term, migrate hot-path services |

**Recommended approach: A (Typed HTTP client)**

Generate typed Feign-style client interfaces from OpenAPI spec:

```java
// api-contracts module
public interface AnalyticsClimateClient {
    @POST("/api/v1/climate/simulate")
    ClimateSimulationResult simulate(ClimateSimulationRequest request);

    @GET("/api/v1/optimizer/weight-delta")
    WeightDeltaResult computeWeightDelta(WeightDeltaRequest request);
}
```

**Steps:**

1. Generate OpenAPI spec from Python FastAPI (`/docs` endpoint)
2. Create typed request/response records in `api-contracts/`
3. Implement `AnalyticsWorkerClient` interface with typed methods
4. Replace `Map<String, Object>` responses with typed records in callers
5. Add circuit breaker per endpoint (not just per client)

### File Manifest

```
api-contracts/src/main/java/com/tickonomics/contracts/client/
  AnalyticsClimateClient.java
  AnalyticsOptimizerClient.java
  AnalyticsRiskClient.java
  ...
api-contracts/src/main/java/com/tickonomics/contracts/model/
  ClimateSimulationRequest.java
  ClimateSimulationResult.java
  WeightDeltaRequest.java
  WeightDeltaResult.java
  ...
computation/src/main/java/com/tickonomics/computation/client/
  TypedAnalyticsWorkerClient.java          (implements all client interfaces)
computation/src/test/java/com/tickonomics/computation/client/
  TypedAnalyticsWorkerClientTest.java
```

### Test Plan

- Unit: mock HTTP responses, verify typed deserialization
- Contract: verify request/response schemas match Python service
- Integration: run Python worker + Java client together
- Error: verify typed error handling for each endpoint

---

## 6. DataHub Deferred Datasets

### Current State

- DataHub (§3.9 of `FREE_DATA_SOURCES_INTEGRATION_PLAN.md`) provides free, no-auth CSV datasets via stable CDN URLs
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

- Phase 7 of `FREE_DATA_SOURCES_INTEGRATION_PLAN.md` must be complete (establishes `DataHubBackfillClient`, CSV parsing, and TimescaleDB write patterns)
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

## Implementation Priority (When Triggered)

```
Priority 1: OpenBBClient            — lowest effort, most immediate data coverage value
Priority 2: AnomalyDetectionWorker   — medium effort, latency-sensitive use case
Priority 3: Chronicle Queue          — significant effort, only when throughput demands it
Priority 4: DataHub Deferred Datasets — low effort, reuses existing DataHub pipeline
Priority 5: analytics Java Bridge    — medium effort, improves type safety
Priority 6: webflux/ Subproject      — highest effort, architectural shift
```

## Cost Estimate Summary

| Item | Java Files | Config Files | Test Files | Total |
|------|-----------|-------------|-----------|-------|
| OpenBBClient | 3 | 1 | 3 | 7 |
| AnomalyDetectionWorker | 3 | 0 | 3 | 6 |
| Chronicle Queue | 2 | 2 | 2 | 6 |
| webflux/ Subproject | 4 | 2 | 3 | 9 |
| analytics Java Bridge | 5+ | 1 | 2+ | 8+ |
| DataHub Deferred Datasets | 0–4 | 1 | 0–4 | 1–9 |
| **Total** | **17–21+** | **7** | **13–17+** | **37–45+** |
