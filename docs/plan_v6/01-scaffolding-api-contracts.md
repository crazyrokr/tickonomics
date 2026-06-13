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
├── ingestion/             # FRED/NY Fed direct clients, Finnhub WebSocket client, Yahoo Finance + Finnhub REST clients, CDM adapters (v3), disaster/ (v4)
├── computation/           # KPI calculation, correlation engine, signal generation, regime/ (v4), stress/ (v4), governance/ (v4), optimization/ (v4)
├── analytics/             # Python analytics worker (ADF, Granger, FinanceToolkit, Arrow IPC), anomaly/ (v4), drift/ (v4)
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
- `Equity` — stock/ETF symbols from Finnhub/Yahoo

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

<!-- v4: Anomaly & Drift endpoints (from Proposal 01: Data Quality) -->

- `POST /api/v1/anomaly/detect` — autoencoder anomaly detection on submitted data window (v4)
- `POST /api/v1/drift/simulate` — drift-diffusion simulation for parameter trajectories (v4)

<!-- v4: Regime detection endpoints (from Proposal 03: Regime Detection) -->

- `GET /api/v1/regime/garch` — GARCH regime status and conditional variance classification (v4)
- `GET /api/v1/regime/hybrid` — CNN-LSTM regime status and ensemble classification (v4)
- `GET /api/v1/regime/qed` — QED market dynamics status and crash probability (v4)
- `GET /api/v1/regime/compare` — A/B comparison of all regime detectors (v4)

<!-- v4: Disaster alert endpoint (from Proposal 03: Regime Detection) -->

- `POST /api/v1/disaster/alerts` — disaster alert status from USGS/GDACS feeds (v4)

<!-- v4: Governance & Stress endpoints (from Proposal 02: Resilience Operations) -->

- `GET /api/v1/participation/status` — participation governance admissibility status (v4)
- `POST /api/v1/stress/liquidity` — liquidity stress test execution (Swiss franc model) (v4)

<!-- v4: Optimization endpoints (from Proposal 04: Weight Optimization) -->

- `GET /api/v1/optimization/status` — Bayesian/Firefly optimizer status and last result (v4)
- `POST /api/v1/optimization/run` — trigger weight optimization run (v4)

### 3. AsyncAPI Spec for WebSocket Endpoints

Define in `api-contracts/asyncapi.yaml`:

- `/ws/prices` — real-time price ticks from Finnhub
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
  Used on the critical ILI ingestion path. No third-party data aggregator dependency (Finding 5).
  Returns source-specific raw types (e.g., `FredObservation`, `NyFedRateResponse`).
- `OpenBBAnalyticsClient` — removed in v6 — analytics worker communicates via REST + Arrow IPC
  directly, no OpenBB intermediary.
- `EquityWsClient` — source-agnostic interface for equity WebSocket streams (Finnhub WebSocket
  implementation in Track 4).

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
public class WsTickCdmAdapter implements CdmAdapter<FinnhubTrade> { ... }
public class YahooEquityCdmAdapter implements CdmAdapter<YahooEquityQuote> { ... }
public class FinnhubEquityCdmAdapter implements CdmAdapter<FinnhubQuote> { ... }
public class YahooOptionsCdmAdapter implements CdmAdapter<YahooOptionsChain> { ... }
public class NewsArticleCdmAdapter implements CdmAdapter<NewsArticle> { ... }
public class FrenchFactorCdmAdapter implements CdmAdapter<FrenchFactorData> { ... }
public class AlphaVantageCdmAdapter implements CdmAdapter<AlphaVantageResponse> { ... }
public class ShillerSp500CdmAdapter implements CdmAdapter<ShillerSp500Record> { ... }
public class VixCdmAdapter implements CdmAdapter<VixData> { ... }
public class OilPriceCdmAdapter implements CdmAdapter<OilPriceData> { ... }
public class GoldPriceCdmAdapter implements CdmAdapter<GoldPriceData> { ... }
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

  yahoo-finance:
    base-url: "https://query1.finance.yahoo.com"
    connect-timeout: "5s"
    read-timeout: "30s"
    retry-max-attempts: 3
    retry-backoff: "1s,2s,4s"

  finnhub:
    rest-base-url: "https://finnhub.io/api/v1"
    api-key: "${FINNHUB_API_KEY}"
    connect-timeout: "5s"
    read-timeout: "30s"
    ws-url: "wss://ws.finnhub.io"
    reconnect-backoff-max: "60s"

  talib:
    unstable-period: 0

  # --- v4: Anomaly detection (from Proposal 01: Data Quality) ---
  anomaly:
    enabled: true
    mse_threshold_multiplier: 3.0

  # --- v4: Regime detection (from Proposal 03: Regime Detection) ---
  regime:
    method: "garch"                     # "garch" (primary), "cnn-lstm", "qed"

  # --- v4: Disaster alerts (from Proposal 03: Regime Detection) ---
  disaster:
    usgs_enabled: true
    gdacs_enabled: true
    poll_interval: "30s"

  # --- v4: Weight optimization (from Proposal 04: Weight Optimization) ---
  optimization:
    method: "bayesian"                  # "bayesian" (primary), "firefly"
    trigger_interval: "30d"
    min_improvement_sharpe: 0.1

  # --- v4: Distributed tracing (from Proposal 02: Resilience Operations) ---
  tracing:
    enabled: true
    exporter: "otlp"
    endpoint: "http://localhost:4317"
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
lightweight Java HTTP clients. Analytics worker communicates via REST + Arrow IPC directly
— no OpenBB intermediary. Equity market data sourced from Finnhub WebSocket (real-time trades)
and Yahoo Finance REST (historical quotes, options chains).

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
| Equity Data        | Finnhub WebSocket (real-time), Yahoo Finance REST (historical) |
| Analytics Data     | Python analytics worker (REST + Arrow IPC, no intermediary)    |

---

## Validation

- [ ] All modules compile independently.
- [ ] OpenAPI spec validates via `spectral` linter.
- [ ] AsyncAPI spec validates via asyncapi validator.
- [ ] TypeScript types generated and importable.
- [ ] `TalibAdapter` unit tests pass against known mathematical results.
- [ ] Configuration properties validation catches all invalid inputs.
- [ ] Spring Boot application starts with `virtual-threads` profile.
- [ ] `FederationDataClient` interfaces compile and are usable without third-party data aggregator dependency.
- [ ] `ArrowIpcTransport` serializes/deserializes time-series arrays correctly.
- [ ] Shared service layer interfaces allow `webflux/` module stub to compile.
- [ ] `cdm/` module compiles with CDM dependency and exposes `InstrumentType`, `RateType` enums (v2).
- [ ] `CdmInstrumentMapper` converts between domain types and CDM types (v2).
- [ ] OpenAPI spec uses CDM-aligned enums for instrument and rate type fields (v2).
- [ ] `CdmAdapter` implementations map raw source data to `CdmRateSnapshot` / `CdmTick` correctly (v3).
- [ ] `FredCdmAdapter`, `NyFedCdmAdapter`, `WsTickCdmAdapter` compile and produce valid CDM types (v3).
- [ ] Computation engine receives only CDM-typed data — no source-specific types leak past ingestion boundary (v3).
- [ ] `POST /api/v1/anomaly/detect` endpoint returns anomaly scores with MSE threshold classification (v4).
- [ ] `POST /api/v1/drift/simulate` endpoint returns drift-diffusion trajectory with Ito process parameters (v4).
- [ ] `GET /api/v1/regime/garch` endpoint returns GARCH regime classification and conditional variance (v4).
- [ ] `GET /api/v1/regime/hybrid` endpoint returns CNN-LSTM ensemble regime status (v4).
- [ ] `GET /api/v1/regime/qed` endpoint returns QED metastable state and crash probability (v4).
- [ ] `GET /api/v1/regime/compare` endpoint returns side-by-side comparison of all regime detectors (v4).
- [ ] `POST /api/v1/disaster/alerts` endpoint receives and returns USGS/GDACS disaster alert data (v4).
- [ ] `GET /api/v1/participation/status` endpoint returns participation governance admissibility decomposition (v4).
- [ ] `POST /api/v1/stress/liquidity` endpoint executes liquidity stress test and returns results (v4).
- [ ] `GET /api/v1/optimization/status` endpoint returns current optimizer method, last run timestamp, and Sharpe
  improvement (v4).
- [ ] `POST /api/v1/optimization/run` endpoint triggers weight optimization and returns new weight proposal (v4).
- [ ] `regime/` module compiles within `computation/` and exposes regime classifier interfaces (v4).
- [ ] `anomaly/` and `drift/` modules compile within `analytics/` and expose detection/simulation interfaces (v4).
- [ ] `disaster/` module compiles within `ingestion/` and exposes USGS/GDACS feed polling interfaces (v4).
- [ ] `stress/`, `governance/`, `optimization/` modules compile within `computation/` and expose respective interfaces (
  v4).
- [ ] Configuration properties `monitor.anomaly.*`, `monitor.regime.*`, `monitor.disaster.*`, `monitor.optimization.*`,
  `monitor.tracing.*` validate correctly with JSR 380 (v4).

### v5 Validation Additions

- [ ] `POST /api/v1/sentiment/analyze` endpoint returns FinBERT sentiment scores with certainty (v5).
- [ ] `POST /api/v1/sentiment/lexicon` endpoint returns TextBlob/AFINN sentiment scores (v5).
- [ ] `POST /api/v1/stops/calibrate` endpoint returns Markov optimal stop levels (v5).
- [ ] `GET /api/v1/leverage/status` endpoint returns current leverage rotation signal (v5).
- [ ] `GET /api/v1/pairs/active` endpoint returns active pairs with divergence status (v5).
- [ ] `GET /api/v1/demo/execution-comparison` endpoint returns dual portfolio P&L comparison (v5).
- [ ] `leverage/`, `pairs/`, `execution/`, `portfolio/` modules compile within `computation/` and expose respective
  interfaces (v5).
- [ ] `sentiment/`, `stops/` modules compile within `analytics/` and expose respective interfaces (v5).
- [ ] Configuration properties `monitor.demo.leverage_rotation.*`, `monitor.demo.dynamic_stops.*`,
  `monitor.demo.dual_portfolio.*`, `monitor.demo.sentiment.*` validate correctly with JSR 380 (v5).

### v5.2 Validation Additions (Proposal 13 & 14: Quantitative Engine)

- [ ] `/api/v1/strategies/**` and `/api/v1/kpi/evt-risk` endpoints return valid results (v5.2).
- [ ] `/api/v1/audit/intersubjective-reproducibility` returns data transformation audit logs (v5.2).
- [ ] `CdmOptionSnapshot` and `CdmBondSnapshot` include Greeks and Duration fields (v5.2).
- [ ] `statistical_methods/` and `strategy/` modules compile and expose interfaces (v5.2).

### v6 Validation Additions (Free Data Source Migration)

- [ ] `EquityWsClient` interface compiles and connects to Finnhub WebSocket without Polygon dependency.
- [ ] `YahooEquityCdmAdapter` maps Yahoo Finance quote data to `CdmTick` correctly.
- [ ] `FinnhubEquityCdmAdapter` maps Finnhub REST quote data to `CdmTick` correctly.
- [ ] `WsTickCdmAdapter` maps `FinnhubTrade` to `CdmTick` correctly.
- [ ] `YahooOptionsCdmAdapter` maps Yahoo Finance options chain data to `CdmOptionSnapshot` correctly.
- [ ] `NewsArticleCdmAdapter`, `FrenchFactorCdmAdapter`, `AlphaVantageCdmAdapter` compile and produce valid CDM types.
- [ ] `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter`, `GoldPriceCdmAdapter` compile and produce valid
  CDM types.
- [ ] Configuration properties `monitor.yahoo-finance.*`, `monitor.finnub.*` validate correctly with JSR 380.
- [ ] No compile-time or runtime references to Polygon or OpenBB remain in the codebase.
- [ ] Analytics worker communicates via REST + Arrow IPC directly — no OpenBB sidecar required.

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|:--------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Arrow IPC transport, direct FRED/NY Fed clients, Virtual Threads primary.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| v2      | Added `cdm/` module (FINOS CDM subset projection) to project structure for canonical instrument types (FloatingRateNote, Repo, Bill, Equity). Added `InstrumentType` enum, `RateType` enum, and `CdmInstrumentMapper` class. Added "FINOS CDM Projection Module" as deliverable section 2. Updated tech stack with "Instrument Model: FINOS CDM" row.                                                                                                                                                                                                                                                                             |
| v3      | Added "CDM Adapter Layer" (Proposal #5) as deliverable section 7. Added `CdmAdapter<T>` interface, per-source adapters (`FredCdmAdapter`, `NyFedCdmAdapter`, `PolygonTickCdmAdapter`), and CDM output types (`CdmRateSnapshot`, `CdmTick`, `CdmInstrumentRef`). Updated `ingestion/` in project structure to include CDM adapters. Updated `FederationDataClient` to return source-specific raw types.                                                                                                                                                                                                                            |
| v4      | Added 11 new OpenAPI endpoints: anomaly detection, drift simulation, GARCH/CNN-LSTM/QED/compare regime endpoints, disaster alerts, participation governance, liquidity stress test, optimization status/run. Added `regime/` to computation modules. Added `anomaly/` and `drift/` to analytics modules. Added `disaster/` to ingestion modules. Added `stress/`, `governance/`, `optimization/` to computation modules. Added configuration properties for anomaly, regime, disaster, optimization, and tracing. Added 17 v4 validation checklist items.                                                                         |
| v5      | Added from Proposals 05, 06, 07: robustness heatmap, BSM Greeks, GEX aggregate, comovement factor, strategic runs, reproducibility scoring, regulatory compliance report, price jump ratio, execution comparison, and related endpoints. Added 15 v5 validation checklist items.                                                                                                                                                                                                                                                                                                                                                  |
| v5      | Added 6 new OpenAPI endpoints from Proposals 08, 09: sentiment analyze (FinBERT), sentiment lexicon (SALI), stops calibrate (Markov), leverage status, pairs active, demo execution-comparison. Added `leverage/`, `pairs/`, `execution/`, `portfolio/` to computation modules. Added `sentiment/`, `stops/` to analytics modules. Added configuration properties for leverage_rotation, dynamic_stops, dual_portfolio, sentiment. Added 9 v5 validation checklist items.                                                                                                                                                         |
| v5.1    | Added 8 new OpenAPI endpoints from Proposals 10, 11, 12: diagnostics stats (QQ-plot, ACF, convergence), volatility forecast (LSTM), tournament evaluate (multi-model benchmark), explainability feature-importance (SHAP), fixed-income q-world-fair-value (CIR), fixed-income tbill-greeks (analytical Greeks), kpi efficiency-gap, kpi monetary-policy. Added `sensitivity/` to computation modules. Added `diagnostics/`, `ml/`, `benchmark/`, `explainability/`, `fixed_income/` to analytics modules. Added configuration properties for tournament, walk_forward, greeks, q_world. Added 8 v5.1 validation checklist items. |
| v5.2    | Added Proposal 13 & 14 (Quantitative Engine): Consolidates 550+ strategy formulas with advanced statistical rigor (EVT, BH-FDR, IR Audit, Quantile Regression). Added 4 new validation items.                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| v6      | Migrated to free data sources: replaced Polygon WebSocket with Finnhub WebSocket, replaced OpenBB with direct Yahoo Finance + Finnhub REST clients. Renamed `PolygonWsClient` to `EquityWsClient` (source-agnostic). Replaced `PolygonTickCdmAdapter` with `WsTickCdmAdapter<FinnhubTrade>`. Added 10 new CDM adapters: `YahooEquityCdmAdapter`, `FinnhubEquityCdmAdapter`, `YahooOptionsCdmAdapter`, `NewsArticleCdmAdapter`, `FrenchFactorCdmAdapter`, `AlphaVantageCdmAdapter`, `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter`, `GoldPriceCdmAdapter`. Replaced `openbb:` config with `yahoo-finance:` and `finnhub:` blocks. Removed OpenBB sidecar dependency — analytics worker communicates via REST + Arrow IPC directly. Added 10 v6 validation checklist items. |

---

## Appendix: 08-structural-gaps.md

> *Merged from `gaps/08-structural-gaps.md` / `done/08-structural-gaps.md` during plan_v6 consolidation.*

# Structural / Architectural Gaps

| #   | Gap                                      | Plan Ref                | Note                                                                                          |
| --- | ---------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------------- |
| 1   | `webflux/` subproject                    | `01-scaffolding.md` §12 | WebFlux adapter module for future reactive runtime — **not created** (intentionally deferred) |
| 2   | `analytics/` Gradle subproject with Java | `settings.gradle`       | Listed in `settings.gradle` but contains only Python, no Java analytics bridge code           |
| 3   | `integration-tests/` subproject          | `01-scaffolding.md`     | Module exists in `settings.gradle` with Testcontainers config but **no test classes written** |

---

## Appendix: Deferred Items

> *Merged from `deferred-items-implementation-plan.md` during plan_v6 consolidation.*

## 4. webflux/ Subproject

### Current State

- All REST endpoints use Spring MVC (servlet-based, `web/` module)
- WebSocket ingestion uses `javax.websocket` via `FinnhubWsClient` (v6: replaced DefaultPolygonWsClient)
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

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 8. Structural / Architectural Gaps

| #   | Gap                                      | Plan Ref                | Note                                                                                          |
| --- | ---------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------------- |
| 1   | `webflux/` subproject                    | `01-scaffolding.md` §12 | WebFlux adapter module for future reactive runtime — **not created** (intentionally deferred) |
| 2   | `analytics/` Gradle subproject with Java | `settings.gradle`       | Listed in `settings.gradle` but contains only Python, no Java analytics bridge code           |
| 3   | `integration-tests/` subproject          | `01-scaffolding.md`     | Module exists in `settings.gradle` with Testcontainers config but **no test classes written** |

---
