# Track 1: Project Scaffolding & API Contracts

**Phase:** Phase 0
**Can start:** Immediately
**Blocks:** Tracks 2, 4, 5, 7
**Depends on:** Nothing

---

## Objective

Create the multi-module project structure, define all API contracts, and set up foundational
infrastructure that all other tracks depend on.

---

## Deliverables

### 1. Multi-Module Project Structure

```
tickonomics/
├── api-contracts/         # OpenAPI + AsyncAPI specs
├── cdm/                   # FINOS CDM projection (subset for instrument types) — v2
├── ingestion/             # FRED/NY Fed direct clients, Polygon WebSocket client, OpenBB client, CDM adapters (v3)
├── computation/           # KPI calculation, correlation engine, signal generation
├── analytics/             # Python analytics worker (ADF, Granger, FinanceToolkit, Arrow IPC)
├── persistence/           # TimescaleDB repositories (single database)
├── web/                   # REST controllers, WebSocket handlers (Virtual Threads + MVC)
├── webflux/               # WebFlux adapters (future, interface-based drop-in)
├── app/                   # Spring Boot entrypoint (Virtual Threads primary)
├── frontend/              # Next.js 15 analytics dashboard (app.tickonomics.io)
├── landing/               # Next.js 15 marketing landing page (tickonomics.io)
├── .github/workflows/     # GitHub Actions CI/CD pipelines
└── integration-tests/     # Cross-module integration tests
```

- Build tool: Gradle (multi-module) for Java, npm/pnpm for frontend.
- Java 25 with Spring Boot 3.4+.
- TA-Lib Java added as a Gradle dependency (pure Java JAR, no native binaries).

### 2. FINOS CDM Projection Module — v2

Implement a thin `cdm/` module containing a **subset/projection** of the FINOS Common Domain
Model. Only the instrument types consumed by tickonomics are mapped — no full CDM adoption.

**Included instrument types:**

- `FloatingRateNote` — SOFR, EFFR, TGCR, BGCR, IORB
- `Repo` — tri-party repo rates
- `Bill` — 3-month Treasury Bills (T-Bill proxy)
- `Equity` — stock/ETF symbols from Polygon

**Implementation:**

- Gradle dependency: `org.finos.cdm:cdm-java` (or JSON schema import).
- `cdm/` module exposes:
    - `InstrumentType` enum aligned with CDM product taxonomy.
    - `RateType` enum mapping tickonomics identifiers to CDM concepts.
    - `CdmInstrumentMapper` — converts between internal domain types and CDM types.
- Used by `api-contracts/` for OpenAPI schema enums and `persistence/` for `rate_type` values.
- Provides future interoperability with CDM-compliant systems without locking into the full model.

**Why subset only:** Full CDM adoption creates excessive boilerplate for a project of this scale.
The projection covers only the instrument types in the ILI computation pipeline.

### 3. OpenAPI 3.1 Spec for REST Endpoints

Define in `api-contracts/openapi.yaml`:

- `GET /api/v1/kpi/ili` — current ILI value with components
- `GET /api/v1/kpi/ili/history` — ILI time-series
- `GET /api/v1/kpi/liquidity-stress` — Liquidity Stress Index
- `GET /api/v1/kpi/repo-equity-beta` — Repo/Equity Beta per symbol
- `GET /api/v1/kpi/rrp-drain` — RRP Drain Velocity
- `GET /api/v1/kpi/systemic-risk-heatmap` — Systemic Risk Heatmap data
- `GET /api/v1/kpi/volatility-regime` — Volatility Regime status
- `GET /api/v1/signals` — recent signals with filters (symbol, direction, status)
- `GET /api/v1/signals/{id}` — single signal detail
- `GET /api/v1/config` — current configuration
- `PUT /api/v1/config` — update hot-reloadable configuration
- `GET /api/v1/config/history` — configuration change log
- `GET /api/v1/health` — system health aggregation
- `GET /api/v1/demo/portfolio` — virtual portfolio summary (Phase 6)
- `GET /api/v1/demo/trades` — paper trade history (Phase 6)
- `GET /api/v1/demo/signal-quality` — signal quality report (Phase 6)

### 3. AsyncAPI Spec for WebSocket Endpoints

Define in `api-contracts/asyncapi.yaml`:

- `/ws/prices` — real-time price ticks from Polygon
- `/ws/signals` — real-time signal notifications

### 4. TypeScript Client Generation

- Generate TypeScript types from the OpenAPI spec for use in the Next.js frontend.
- Use `openapi-typescript` or `openapi-generator-cli`.

### 5. TalibAdapter (Thin Wrapper)

Implement `TalibAdapter` — thin wrapper adapting TA-Lib's array-based API to domain types:

```java
public class TalibAdapter {
    public double[] computeSma(double[] data, int period);
    public double[] computeStdDev(double[] data, int period);
    public double[] computeCorrel(double[] x, double[] y, int period);
    public double[] computeBeta(double[] market, double[] benchmark, int period);
    public double[] computeLinearRegSlope(double[] data, int period);
    public BBandsResult[] computeBollingerBands(double[] data, int period, double devUp, double devDown);
    public double[] computeRoc(double[] data, int period);
}
```

Handles: array allocation, lookback periods, `TA_SetUnstablePeriod`.

### 6. HTTP Clients (Interfaces)

Define interfaces (implementations deferred to Tracks 4 and 5):

- `FederationDataClient` — direct lightweight Java HTTP client for FRED and NY Fed REST APIs.
  Used on the critical ILI ingestion path. No OpenBB dependency (Finding 5).
  Returns source-specific raw types (e.g., `FredObservation`, `NyFedRateResponse`).
- `OpenBBAnalyticsClient` — for analytics worker communication via Arrow IPC (Finding 2).
  Java sends data once, worker returns optimal results including AIC-selected lag.
- `PolygonWsClient` — interface for Polygon WebSocket (implementation in Track 4).

All client interfaces are defined without coupling to a specific HTTP library, so WebFlux
adapters (`WebClient`-based) can be added later in the `webflux/` module.

### 7. CDM Adapter Layer (Proposal #5 — v3)

Map raw source-specific data to CDM-typed objects at the ingestion boundary. The computation
engine (Track 5) consumes only CDM-typed data, ensuring source-agnostic KPI calculation.

```java
public interface CdmAdapter<T> {
    CdmRateSnapshot toCdmRate(T rawData);
}

// Per-source adapters
public class FredCdmAdapter implements CdmAdapter<FredObservation> { ... }
public class NyFedCdmAdapter implements CdmAdapter<NyFedRateResponse> { ... }
public class PolygonTickCdmAdapter implements CdmAdapter<PolygonTick> { ... }
```

**CDM types produced:**

- `CdmRateSnapshot` — canonical rate observation (rate type from CDM enum, value, timestamp, source).
- `CdmTick` — canonical equity tick (symbol, price, volume, timestamp, conditions).
- `CdmInstrumentRef` — instrument reference linking to CDM product taxonomy.

**Why:** Prevents source-specific field mismatch bugs in ILI calculation. If FRED changes field
names or NY Fed alters response structure, only the adapter changes — computation is insulated.

### 8. Arrow IPC Transport Layer

Implement `ArrowIpcTransport` — serialization/deserialization for analytics worker communication:

- Uses Apache Arrow Java for columnar in-memory format.
- Replaces REST/JSON for time-series array transfer to the Python analytics worker.
- Reduces serialization overhead for AIC lag selection and batch analytics (Finding 2).
- Fallback: REST/JSON for small payloads or when Arrow is unavailable.

### 8. Shared Configuration Properties Classes

Implement `@ConfigurationProperties` with `@Validated` + JSR 380 for all config in the
`monitor.*` namespace:

- `concurrency-mode` must be `"virtual-threads"` (primary). `"webflux"` accepted but not
  actively maintained — reserved for future use.
- `ili_weights` values must sum to `1.0 +/- 0.001`.
- Each weight in `[0.0, 1.0]`.
- `buy_percentile < sell_percentile`.
- `symbols` lists non-empty, each matching `[A-Z]{1,5}`.
- `buffer_capacity` > 0.
- `timescaledb.ingestion.batch-size` in `[1, 10000]`.

---

## Configuration Reference

```yaml
monitor:
  concurrency-mode: virtual-threads    # "virtual-threads" (primary), "webflux" (future)

  fred:
    base-url: "https://api.stlouisfed.org/fred"
    api-key: "${FRED_API_KEY}"
    connect-timeout: "5s"
    read-timeout: "30s"

  nyfed:
    base-url: "https://markets.newyorkfed.org/api"
    connect-timeout: "5s"
    read-timeout: "30s"

  openbb:
    base-url: "http://localhost:8000"
    analytics-worker-url: "http://localhost:8001"
    transport: "arrow-ipc"              # "arrow-ipc" (primary) or "rest-json" (fallback)
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"
  polygon:
    ws-url: "wss://socket.polygon.io/stocks"
    api-key: "${POLYGON_API_KEY}"
    reconnect-backoff-max: "60s"
  talib:
    unstable-period: 0
```

---

## Concurrency Design

**Primary runtime: Virtual Threads + Spring MVC.**

The shared service layer sits behind interfaces so that ingestion, computation, and API
modules are agnostic to the runtime model. This enables a future WebFlux adapter module
(`webflux/`) to be added without rewriting business logic.

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
│  Primary: MVC   │ │  Future: WebFlux    │
│ Virtual Threads │ │   (adapter module)  │
│ @Scheduled,     │ │  Flux/Mono,         │
│ StructuredTask  │ │  WebClient          │
│ Scope           │ │  (not yet built)    │
└─────────────────┘ └─────────────────────┘
```

**Current (Primary):** `spring.threads.virtual.enabled=true`, `@Scheduled`,
`SimpleAsyncTaskExecutor`, `java.net.http.HttpClient`, JDBC, `StructuredTaskScope`.

**Future (WebFlux adapter):** `spring-boot-starter-webflux`, `Flux.interval()`,
`WebClient`, `reactor-netty WebSocketClient`, R2DBC or `Schedulers.boundedElastic()` + JDBC.
Not actively maintained — module stub exists for future implementation.

**Data Source Architecture (Finding 5):**
Core ILI data (FRED EFFR, RRP, IORB, TGA; NY Fed SOFR, TGCR, BGCR) fetched via direct
lightweight Java HTTP clients — no OpenBB dependency on the critical path. OpenBB sidecar
used only for long-tail analytics (econometrics, factor analysis) via the analytics worker.

---

## Tech Stack

| Layer              | Technology                                                     |
|:-------------------|:---------------------------------------------------------------|
| Language           | Java 25 (LTS)                                                  |
| Framework          | Spring Boot 3.4+                                               |
| Concurrency        | Virtual Threads + Spring MVC (primary), WebFlux-ready (future) |
| Technical Analysis | TA-Lib Java (BSD-3)                                            |
| Instrument Model   | FINOS CDM (subset projection) — v2                             |
| Sidecar IPC        | Apache Arrow IPC (primary), REST/JSON (fallback)               |
| Database           | TimescaleDB (PostgreSQL extension)                             |
| Frontend           | Next.js 15, TailwindCSS, TypeScript                            |
| Charts             | Lightweight Charts (TradingView), D3.js                        |
| Core Data Sources  | Direct FRED + NY Fed HTTP clients                              |
| Analytics Data     | OpenBB Platform (sidecar, econometrics only)                   |

---

## Validation

- [ ] All modules compile independently.
- [ ] OpenAPI spec validates via `spectral` linter.
- [ ] AsyncAPI spec validates via asyncapi validator.
- [ ] TypeScript types generated and importable.
- [ ] `TalibAdapter` unit tests pass against known mathematical results.
- [ ] Configuration properties validation catches all invalid inputs.
- [ ] Spring Boot application starts with `virtual-threads` profile.
- [ ] `FederationDataClient` interfaces compile and are usable without OpenBB dependency.
- [ ] `ArrowIpcTransport` serializes/deserializes time-series arrays correctly.
- [ ] Shared service layer interfaces allow `webflux/` module stub to compile.
- [ ] `cdm/` module compiles with CDM dependency and exposes `InstrumentType`, `RateType` enums (v2).
- [ ] `CdmInstrumentMapper` converts between domain types and CDM types (v2).
- [ ] OpenAPI spec uses CDM-aligned enums for instrument and rate type fields (v2).
- [ ] `CdmAdapter` implementations map raw source data to `CdmRateSnapshot` / `CdmTick` correctly (v3).
- [ ] `FredCdmAdapter`, `NyFedCdmAdapter`, `PolygonTickCdmAdapter` compile and produce valid CDM types (v3).
- [ ] Computation engine receives only CDM-typed data — no source-specific types leak past ingestion boundary (v3).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Arrow IPC transport, direct FRED/NY Fed clients, Virtual Threads primary.                                                                                                                                                                                                                                                                                                   |
| v2      | Added `cdm/` module (FINOS CDM subset projection) to project structure for canonical instrument types (FloatingRateNote, Repo, Bill, Equity). Added `InstrumentType` enum, `RateType` enum, and `CdmInstrumentMapper` class. Added "FINOS CDM Projection Module" as deliverable section 2. Updated tech stack with "Instrument Model: FINOS CDM" row.                                                  |
| v3      | Added "CDM Adapter Layer" (Proposal #5) as deliverable section 7. Added `CdmAdapter<T>` interface, per-source adapters (`FredCdmAdapter`, `NyFedCdmAdapter`, `PolygonTickCdmAdapter`), and CDM output types (`CdmRateSnapshot`, `CdmTick`, `CdmInstrumentRef`). Updated `ingestion/` in project structure to include CDM adapters. Updated `FederationDataClient` to return source-specific raw types. |