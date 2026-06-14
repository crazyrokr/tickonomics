# ADR-018: Systemic Resilience Monitor — Global Safe Mode

**Date:** 2026-06-13
**Status:** Accepted
**Track:** 11 — Deployment & Operations (Phase 8)
**Supersedes / extends:** ADR-010 (deployment & operations), ADR-017 (kill-switch pattern)

## Context

`docs/plan_v6-verification-report.md` scored Track 11 at **~60%**. The bulkhead isolation,
OTLP/Jaeger tracing, overflow volume, and multi-stage Dockerfile were already real, and the
runbook-only kill-switch had been promoted to live code in ADR-017. The remaining open gap was the
**Systemic Resilience Monitor / Global Safe Mode**: it existed only as a runbook, with no Java
implementation. The plan's v5 validation criteria — auto entry on correlated degradation, manual-ack
recovery, and <5s correlation-detection trigger — were unmet, and `PaperTradingEngine` had no
automatic counterpart to the manual `KillSwitch`.

The same Track 11 pass also surfaced stale v6 data-source residue: `docker-compose.yml` still set
`POLYGON_API_KEY`, the two legacy runbooks (`openbb-sidecar-setup.md`, `polygon-websocket-outage.md`)
had never been replaced, and `options-data-pipeline.md` still described the defunct Polygon Options
pipeline.

This ADR records the Systemic Resilience Monitor design; the runbook/compose cleanup is documented
in the verification-report update below.

## Decision

### 1. Correlated-degradation detector, not single-threshold gating

Global Safe Mode auto-activates only when **≥ `min-degraded-indicators`** (default 2) of three health
indicators degrade simultaneously. A lone degraded indicator never trips Safe Mode — this is the
explicit false-positive guard, since any single source (overflow depth, worker latency, proxy
divergence) can blip without indicating systemic failure. The threshold is configurable (1–3) so
operators can trade sensitivity for noise.

### 2. Latching with manual-ack recovery

Once tripped, Safe Mode **latches**: `autoActivated` stays on even after health returns. Recovery
requires an explicit manual acknowledgment (`POST /api/v1/demo/safe-mode/deactivate`), per the plan's
"recovery requires explicit manual acknowledgment" requirement. `status().recoveryReady` reports
current health independently of the latch, so the operator knows when clearing is safe without the
monitor auto-clearing. Auto-recovery was considered and rejected: silent re-enabling of trading after
a systemic event is riskier than requiring a human to acknowledge.

### 3. Decision logic in `computation`, probe wiring in `app`

The module dependency graph drove the split: `computation` does not depend on `ingestion`, but
`PaperTradingEngine` (the gate consumer) lives in `computation`, while the health inputs
(`IngestionBuffer`, `ProxyDivergenceGuard`) live in `ingestion` and `AnalyticsWorkerClient` in
`computation`. So:

- `ResilienceHealthSnapshot` (record), `ResilienceHealthProbe` (interface), and
  `SystemicResilienceMonitor` (the pure decision/latching logic) live in `computation.demo`, next to
  the consumer, fully unit-testable with synthetic snapshots.
- `CrossModuleResilienceHealthProbe` (the bean that reads all three sources) lives in `app`, the only
  module that sees both `ingestion` and `computation`.

### 4. Defensive probes — unknown ≠ degraded

`ResilienceHealthSnapshot` uses `-1` sentinels for unknown utilization/latency. The decision logic
never counts an unknown reading as degraded, and `CrossModuleResilienceHealthProbe` resolves the
ingestion buffer via `ObjectProvider` (no buffer bean is registered today) and wraps every probe call
in try/catch returning a non-degrading default. Net effect: a partially wired deployment — or a
thrown probe call — cannot trip Safe Mode through missing data. The monitor can still activate on the
worker + proxy indicators alone.

### 5. In-memory volatile state, mirroring the kill-switch

Like `KillSwitch` (ADR-017), Safe Mode state is in-memory: a process restart returns it to inactive.
Demo trading is opt-in (`monitor.demo.enabled: false`), so this is an accepted operational trade-off.
Persisting activations to a table (e.g. an audit row) is explicitly deferred — out of scope here, and
noted for a future hardening pass alongside kill-switch persistence.

### 6. Scheduled evaluation meets the <5s SLA

`SystemicResilienceMonitor.evaluateCurrent()` is `@Scheduled(fixedDelayString=
"${monitor.demo.global-safe-mode.evaluation-interval-ms:5000}")`. `@EnableScheduling` is already on
`TickonomicsApplication`, so the cadence is active only in the full app context; in unit tests the
annotation is inert and `evaluate()` is driven directly.

### 7. REST surface

Three endpoints under `/api/v1/demo/safe-mode/`, mirroring the kill-switch pattern:
`GET /status`, `POST /activate` (manual override), `POST /deactivate` (manual acknowledgment).

## Configuration

New nested `DemoConfig.GlobalSafeMode` record under `monitor.demo.global-safe-mode`:

| Property | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch |
| `overflow-utilization-threshold-pct` | `80` | Buffer fill counted as degraded |
| `worker-latency-threshold-ms` | `5000` | Worker latency counted as degraded |
| `min-degraded-indicators` | `2` | Correlated-degradation threshold (1–3) |
| `evaluation-interval-ms` | `5000` | Scheduled probe cadence |

Buffer capacity for utilization math: `monitor.ingestion.resilience.buffer-capacity` (default
`10000`).

## Consequences

- **Positive:** the v5 systemic-resilience validation criteria are met; `PaperTradingEngine` now has
  an automatic safety control alongside the manual kill-switch; the false-positive guard prevents
  single-source trips.
- **Negative / trade-off:** state is volatile (restart clears it); recovery is deliberately manual,
  which means an operator must act — acceptable given the systemic-failure context. The overflow
  indicator is currently inert (no buffer bean registered), so activation today is driven by the
  worker and proxy indicators; it lights up automatically the moment a buffer bean is introduced.
- **Risk:** latency is measured by timing `isHealthy()`, which adds one `/health` HTTP call per
  evaluation cycle (~every 5s) — negligible, and it doubles as a freshness probe.

## Testing

`SystemicResilienceMonitorTest` (Given-When-Then, `@Nested`) covers: all-healthy → inactive; each
single indicator degraded in isolation → inactive (false-positive guard, four cases); correlated
2- and 3-indicator degradation → activates; exact-threshold boundary (80% / 5000ms); latching
(healthy snapshot without manual ack stays active); manual activate/deactivate; disabled config never
activates; null/unknown snapshot → inactive. `PaperTradingEngineTest` adds the
`global_safe_mode` gate case; `DemoControllerTest` covers the three endpoints; `DemoConfigTest`
covers `GlobalSafeMode` validation.

## Verification

- `./gradlew :computation:test :web:test` — green.
- `./gradlew :app:compileJava` — probe resolves against `ingestion` + `computation` + `api-contracts`.
- `docker compose -f docker-compose.yml config` — no `POLYGON` references; v6 keys present.
- Runbook grep: no live Polygon/OpenBB references remain (only intentional migration-history notes).
