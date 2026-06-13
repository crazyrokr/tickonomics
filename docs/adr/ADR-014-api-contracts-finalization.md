# ADR-014: API Contracts Finalization (Track 1)

**Status:** Implemented
**Date:** 2026-06-13
**Decision:** Close the remaining Track 1 (`01-scaffolding-api-contracts.md`) contract gaps — comprehensive OpenAPI + AsyncAPI specs, generated TypeScript clients, live WebSocket channels, and typed/validated `monitor.*` configuration binding.

## Context

The plan-v6 verification report (`docs/plan_v6-verification-report.md`) scored Track 1 at ~70%. The scaffolding was solid (9-module Gradle build, `cdm/` projection, `TalibAdapter`, `ArrowIpcTransport`, virtual-thread entrypoint), but the **contract layer** itself was thin:

1. **OpenAPI spec documented only 7 of ~40 planned endpoints** — no `operationId`s, the implemented `demo`/`health` endpoints undocumented, and the entire v4/v5/v5.1/v5.2 surface (KPI, signals, config, anomaly, regime, disaster, stress, optimization, sentiment, stops, leverage, pairs, diagnostics, fixed-income) absent.
2. **AsyncAPI spec was entirely missing** — `/ws/prices` and `/ws/signals` existed only as plan text; no server-side WebSocket endpoints.
3. **TypeScript client generation was not wired** — no `openapi-typescript` config; the dashboard had no typed view of the API.
4. **`@ConfigurationProperties` typing was sparse** — only 3 classes (`DemoConfig`, `SignalGeneratorConfig`, `SecurityProperties`); 10 of the 11 `monitor.*` YAML namespaces were un-typed with no fail-fast validation.
5. **CDM naming drift** — `WsTradeCdmAdapter` vs the plan's `WsTickCdmAdapter`, and the `CdmAdapter<T,R>` interface diverged from the plan's single-return `CdmAdapter<T>`/`toCdmRate` shape.

Legacy Polygon artifacts (a prior Track 1 gap) were already removed by `5d19986`; `webflux/` remains intentionally deferred per the plan's own appendix.

## Decision

### 1. OpenAPI 3.1 spec — full planned surface (contract-first)

Rewrote `api-contracts/src/main/resources/openapi.yaml` as the Phase 0 contract: ~50 paths across 20 tags (kpi, signals, config, health, demo, quality, regime, disaster, operations, optimization, sentiment, stops, leverage, pairs, diagnostics, volatility, tournament, explainability, fixed-income, quant), every operation carrying an `operationId`, reusable request/response schemas, shared parameters (`Limit`, `Offset`, `UuidPath`) and error responses. Enumerations (`RateType`, `InstrumentType`, `OptionType`, `SignalDirection`) mirror the `cdm/` enums verbatim. This is the source of truth subsequent tracks implement against; endpoints without a controller are tagged parts of the planned surface.

### 2. AsyncAPI spec — real-time channels

Added `api-contracts/src/main/resources/asyncapi.yaml` (AsyncAPI 2.6) declaring the `/ws/prices` (subscribe `PriceTick`) and `/ws/signals` (subscribe `SignalNotification`) channels, with message payloads whose shape the server handlers emit exactly.

### 3. Live WebSocket channels (stub handlers)

Added `spring-boot-starter-websocket` to `web/` and `RealtimeWebSocketConfig` (`@EnableWebSocket`) registering two `BroadcastWebSocketHandler<T>` subclasses:

- `PriceWebSocketHandler` → `/ws/prices`, broadcasting `PriceTick`.
- `SignalWebSocketHandler` → `/ws/signals`, broadcasting `SignalNotification`.

The base handler tracks subscribers in a `ConcurrentHashMap`, serializes payloads via the shared `ObjectMapper`, and drops sessions that fail on send. Channels are **live and connection-accepting** but quiescent until a producer (ingestion WS client, signal generator) is wired to call `broadcast(...)` — that integration belongs to Tracks 4/5. `/ws/**` falls outside `/api/**`, so the existing `SecurityConfig` already permits the handshake under both auth-disabled and auth-enabled modes.

### 4. TypeScript client generation

Wired `openapi-typescript@^7` as a `frontend` devDependency. `npm run generate:api` reads the spec and emits `frontend/lib/api/types.ts`; the `prebuild` hook regenerates it on every `next build`, so the generated client cannot drift from the contract. Verified end-to-end: `openapi-typescript 7.13.0` produced 2,462 lines of typed output, exit 0.

### 5. Typed `@ConfigurationProperties` for `monitor.*`

Added one `@ConfigurationProperties` record per `monitor.*` sub-namespace in `ingestion/.../config` (ingestion, anomaly, equity-price, yahoo-finance, finnhub, news, fed-rss, ken-french, yield-curve, alpha-vantage, datahub), plus a shared `MonitorValidation` helper. **Validation follows the established `DemoConfig` idiom** — explicit guards in the record compact constructor — rather than the plan's `@Validated`/JSR-380:

- It matches the surrounding code (CLAUDE.md: write code that reads like the surrounding code), needs no new runtime dependency (no `spring-boot-starter-validation` is on the runtime classpath today), and runs at bind time, failing context startup on misconfiguration.
- Guards realize the plan §8 intent with the *actual* config keys (which differ from the plan's reference): positive durations/counts (`pollIntervalMs`, `connectTimeout`, `rateLimitDelayMs`, LKG staleness, bulkhead cadence), and `[A-Z]{1,5}` ticker validation on equity symbol lists. Commodity identifiers (e.g. `NATURAL_GAS`) are deliberately **not** ticker-validated. API keys are not constrained (they legitimately default to empty).
- The records are additive — existing `@Value("${monitor.*}")` consumption is untouched — and the full app context (including the integration test) binds cleanly against `application.yml`.

### 6. CDM interface — accepted deviation (no change)

`CdmAdapter<T,R>` with `R toCdm(T)` is **retained as-is**. The plan's `CdmAdapter<T>`/`toCdmRate` returning only `CdmRateSnapshot` cannot model the five CDM output types adapters actually produce (`CdmTick`, `CdmRateSnapshot`, `CdmOptionSnapshot`, `FactorReturn`, `NewsArticle`). The two-type-parameter form is the correct, source-agnostic design; forcing the plan's single return type would break the majority of adapters. This is recorded as an accepted deviation, not a gap. The `WsTradeCdmAdapter` naming drift is cosmetic and left in place to avoid a pointless cross-module rename churn.

## Consequences

- **The contract is now the source of truth.** New controllers (other tracks) implement against the documented `operationId`s; the dashboard imports typed clients. A spec that documents endpoints ahead of their controllers is intentional for a contract-first Phase 0 module — the `prebuild` codegen keeps the TS client honest to whatever the spec currently declares.
- **Operational: WebSocket endpoints are open but idle.** `/ws/prices` and `/ws/signals` accept connections immediately; broadcasting begins once a producer is wired (Tracks 4/5). Origin patterns are permissive (`*`) for the dashboard — production should tighten this.
- **Fail-fast config.** A malformed `monitor.*` value now prevents startup with a field-specific message, rather than failing later in a scheduler/client.
- **Validation tests are constructor-level (Given-When-Then).** They prove the guards catch invalid inputs without spinning a Spring context; the integration test (`ApplicationStartupIT`) exercises real binding against `application.yml`.

## Validation

- `ApiContractsSpec` (Spock, api-contracts) parses both YAMLs with SnakeYAML and asserts the OpenAPI version, the full planned path set (≥40 paths), `operationId` presence on every operation, and both AsyncAPI channels.
- `PriceWebSocketHandlerSpec` / `SignalWebSocketHandlerSpec` (Spock, web) assert broadcast delivery, no-op on zero subscribers, and subscriber removal on close.
- `MonitorPropertiesSpec` (Spock, ingestion) asserts valid construction and rejection of non-positive durations/counts, empty/malformed symbol lists, and nested LKG staleness.
- `./gradlew build` and `npm run generate:api` (frontend) verify end-to-end.
