# ADR-013: Ingestion Layer Resilience Finalization (Track 4)

**Status:** Implemented
**Date:** 2026-06-13
**Decision:** Close the remaining Track 4 (`04-ingestion-layer.md`) gaps — durable idempotency, observable bulkheads, distributed tracing, anomaly-aware quality checks, stale-data signalling, and removal of legacy paid-source residue.

## Context

The plan-v6 verification report (`docs/plan_v6-verification-report.md`) found the ingestion layer ~85% complete by class count but with six concrete gaps versus the Track 4 spec:

1. **Idempotency was in-memory only** (`IdempotencyGuard`, a `ConcurrentHashMap`) — duplicates could survive a process restart or multi-instance write.
2. **`IngestionBulkheadConfig` was absent** — `@Bulkhead` annotations existed but the `BULKHEAD_POOL_PRESSURE` (>80%) saturation alert from Proposal 02 did not.
3. **`IngestionTracingConfig` was absent** — the OpenTelemetry bridge dependency was present but no spans were created for ingestion operations.
4. **`DataQualityChecker` never called the analytics worker** for autoencoder anomaly detection (plan §7/§10) — it ran threshold checks only.
5. **The LKG cache did not expose the `X-Data-Age` header** value for downstream consumers.
6. **Legacy Polygon artifacts lingered** — `OptionsDataClient` (paid Polygon REST), `PolygonTickCdmAdapter`, `PolygonTick`, and `CdmInstrumentMapper.fromPolygonSymbol` — superseded by the v6 free-source migration (ADR-012) but never removed.

## Decision

### 1. Layered exactly-once idempotency (durable)

Keep `IdempotencyGuard` as a fast in-process pre-filter, and add a durable database backstop:

- New migration `V35__add_idempotency_keys.sql` adds a nullable `idempotency_key UUID` column plus a partial `UNIQUE INDEX (time, idempotency_key) … WHERE idempotency_key IS NOT NULL` to `tick_data` and `rate_snapshots`. Nullable + partial index means existing keyless inserts are unaffected (no backfill, no rewrite). The index includes the `time` partitioning column to satisfy TimescaleDB (see [§1a](#1a-timescaledb-hypertable-constraints-non-obvious)); for the compressed `tick_data` hypertable the migration drops compression for the DDL and restores it afterward.
- New `IdempotentRow<T>(UUID, T)` wrapper record pairs each row with its key **without modifying the row records** (`TickData`/`RateSnapshot` unchanged) — zero blast radius on consumers.
- New `saveAllIdempotent(List<IdempotentRow<T>>)` on both repositories uses `INSERT … ON CONFLICT (time, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING` — the composite conflict target matches the hypertable index.
- `TimescaleDbWriter` derives a **deterministic type-3 name-based UUID** (`UUID.nameUUIDFromBytes`) from the natural key, so a retried write of the same logical event yields the identical UUID and is suppressed by the constraint even across restarts. `countNew` is null-safe.

### 1a. TimescaleDB hypertable constraints (non-obvious)

Two TimescaleDB rules shape the durable-idempotency schema; both surfaced when the integration tests (`ApplicationStartupIT`) ran against `timescale/timescaledb:latest-pg16` and were confirmed by direct DDL experiments against that image:

- **Unique indexes on hypertables must cover every partitioning column.** A partial `UNIQUE INDEX (idempotency_key)` is rejected with `cannot create a unique index without the column "time" (used in partitioning)`. The index is therefore `(time, idempotency_key)`. Because `idempotency_key` is a deterministic name-based UUID (derived from `source:identifier:time`), including `time` does not change dedup semantics — the same logical event always carries the same `(time, key)` pair.
- **Index creation is blocked on a compressed hypertable.** `tick_data` has compression enabled (V4); `CREATE UNIQUE INDEX` on it raises `operation not supported on hypertables that have compression enabled` (SQL state `0A000`). The migration therefore toggles compression off for the DDL and restores it (segmentby/orderby re-declared) — the compression/retention policy survives the toggle. `rate_snapshots` has no compression policy, so no toggle is needed there.
- The repository `ON CONFLICT` target must list the index columns exactly, hence `ON CONFLICT (time, idempotency_key)`, not `(idempotency_key)` — otherwise PostgreSQL cannot infer the arbiter index at insert time.

### 2. Bulkhead saturation monitor

`IngestionBulkheadConfig` holds the canonical names of the three Resilience4j semaphore bulkheads (`criticalIngestion`, `highVolumeIngestion`, `computationEngine`) and hosts `BulkheadPressureMonitor`, a `@Scheduled` component that samples live utilization from the `BulkheadRegistry` and logs `BULKHEAD_POOL_PRESSURE` **once per transition** above 80% — so a saturated Finnhub-WS pool can never silently starve the critical FRED/NY-Fed pool. Effective pool sizes remain declared in `resilience4j.bulkhead.instances.*`; the monitor reads capacity from the registry at runtime.

### 3. Distributed tracing helper

`IngestionTracingConfig` is the canonical registry of seven span names; `IngestionTracer` starts them via the Micrometer `Tracer` (OTel bridge) and returns an auto-closeable `SpanScope`. When `monitor.tracing.enabled=false` a no-op scope is returned (zero overhead). Spans are wired into the representative `ingestion.timescaledb.write` (writer flush), `ingestion.fred.fetch`, and `ingestion.nyfed.fetch` paths; the remaining four (Finnhub WS message, quality check, anomaly detect, disaster poll) follow the same one-liner. **Trace-ID propagation via Arrow IPC is deferred** — Arrow IPC itself is not yet implemented (Track 3 gap); HTTP-header propagation to the analytics worker is automatic via the OTel bridge.

### 4. Async anomaly integration in DataQualityChecker

`checkRateWithAnomaly` sends the current rate value to the analytics worker's `/api/v1/anomaly/detect` autoencoder endpoint on a virtual thread with a bounded `orTimeout`. An anomalous result yields `SUSPECT_ANOMALY`; on any worker error or timeout the check degrades to the threshold-based result and logs `ANOMALY_WORKER_FALLBACK` once per unreachable transition. The pipeline is never blocked — callers receive a `CompletableFuture`. `AnalyticsWorkerClient` is injected by interface (bean provided by `computation.RestClientAnalyticsWorkerClient` at runtime).

### 5. Stale-data signalling

`CacheEntry.X_DATA_AGE_HEADER = "X-Data-Age"` and `LastKnownGoodCache.headerValue(source)` expose the staleness status (`FRESH`/`STALE`) so the web layer can set the header on cached responses. A testable `Clock` hook enables deterministic STALE/EXPIRED unit tests.

### 6. Polygon residue removal

Deleted `OptionsDataClient` (paid Polygon REST; dormant via `ConditionalOnProperty`, superseded by `YahooOptionsClient`), `PolygonTickCdmAdapter`, `PolygonTick`, and the unused `CdmInstrumentMapper.fromPolygonSymbol`.

## Consequences

- **Positive:** Exactly-once writes survive restarts and multi-instance deployment; bulkhead starvation and worker outages are now observable; anomaly detection runs at ingestion time without blocking; no paid-source residue remains; the ingestion test suite (Spock + JUnit) is green.
- **Negative:** A new schema migration (V35) and a new persistence record (`IdempotentRow`) are introduced; `DataQualityChecker` now depends on the analytics worker (non-blocking, with fallback). Trace propagation across the (unimplemented) Arrow IPC transport remains a future task.
- **Test-suite correction:** The verification report's "4 test files for 41 classes" finding undercounted coverage — the module already carried ~25 Spock specifications (Groovy) that the count missed. Track 4's test gap was therefore smaller than reported; the remaining work added tests for the genuinely new code and repaired the four specs broken by the constructor/method changes above.

## Verification

`./gradlew :ingestion:test :persistence:test :integration-tests:test` — all green. Coverage lives in Spock specifications: `TimescaleDbWriterSpec`, `LastKnownGoodCacheSpec`, `DataQualityCheckerSpec`, `IngestionBulkheadConfigSpec`, `IngestionTracingConfigSpec`, `IngestionIdempotencyRepositorySpec` (the earlier JUnit drafts were rewritten as Groovy Spock per the repo convention). Updated Spock specs: `FredClientSpec`, `NyFedClientSpec`, `DataQualityCheckerSpec`, `IdempotencyRoutingSpec`. The `:integration-tests:test` suite (`ApplicationStartupIT`) runs the full Flyway migration set against a TimescaleDB testcontainer and confirms V35 applies cleanly.
