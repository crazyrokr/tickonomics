# Systemic Resilience Monitor — Global Safe Mode Runbook

## Purpose

Monitor cross-module health indicators and manage **Global Safe Mode** to protect against correlated
degradation. Implemented by `SystemicResilienceMonitor` (ADR-018); this is the *automatic*
counterpart to the manual kill-switch ([big-red-button.md](big-red-button.md)).

## Health Indicators

Global Safe Mode evaluates three indicators on a fixed schedule (default every 5s — meets the <5s
correlation-detection SLA). Each is "degraded" when:

| Indicator | Degraded when | Source |
|---|---|---|
| Ingestion overflow utilization | buffer fill ≥ `overflow-utilization-threshold-pct` (default 80%) | `CrossModuleResilienceHealthProbe` |
| Analytics worker | reported unhealthy, **or** probe latency ≥ `worker-latency-threshold-ms` (default 5000ms) | `AnalyticsWorkerClient.isHealthy()` |
| Proxy divergence | an intraday SOFR/T-Bill dislocation is active | `ProxyDivergenceGuard.checkDivergence()` |

Unknown readings (e.g. no buffer bean registered, probe unreachable) are **never** counted as
degraded — a partially wired deployment cannot trip Safe Mode through missing data alone.

## Activation — Correlated Degradation

Safe Mode auto-activates when **at least `min-degraded-indicators`** (default 2) indicators are
degraded simultaneously:

- A **single** degraded indicator does **not** trip Safe Mode — this is the false-positive guard.
- Two or more degraded indicators indicate correlated (systemic) degradation and latch Safe Mode on.

When active, `PaperTradingEngine.processSignal` rejects every signal with reason `global_safe_mode`.

## Check Safe Mode Status

```bash
curl -s http://localhost:8080/api/v1/demo/safe-mode/status | jq .
```

Example response when latched:

```json
{
  "active": true,
  "autoActivated": true,
  "manualOverride": false,
  "enabled": true,
  "lastReason": "overflow_utilization_exceeded + analytics_worker_degraded",
  "lastActivationAt": "2026-06-13T10:00:00Z",
  "degradedIndicators": ["overflow_utilization_exceeded", "analytics_worker_degraded"],
  "recoveryReady": false
}
```

`recoveryReady` reflects *current* health (all indicators healthy), independent of the latch — it
tells the operator when it is safe to clear, but does not clear Safe Mode on its own.

## Recovery — Manual Acknowledgment

Safe Mode **latches**: it does not auto-clear when health returns. Recovery requires an explicit
manual acknowledgment (verify `recoveryReady: true` first):

```bash
# 1. Confirm all indicators are healthy
curl -s http://localhost:8080/api/v1/demo/safe-mode/status | jq '.recoveryReady'
# true

# 2. Clear Safe Mode
curl -X POST http://localhost:8080/api/v1/demo/safe-mode/deactivate | jq .
# {"active": false, "source": "manual_ack"}
```

`deactivate` clears both the auto-latch and any manual override.

## Manual Override (force Safe Mode)

Operators can force Safe Mode on without waiting for auto-detection (e.g. proactive de-risking):

```bash
curl -X POST http://localhost:8080/api/v1/demo/safe-mode/activate | jq .
# {"active": true, "source": "manual_override"}
```

## Diagnosing Active Indicators

When Safe Mode is latched, drill into the underlying signal:

```bash
# Ingestion overflow depth
docker compose logs backend --tail=200 | grep -i "OVERFLOW_QUEUE_GROWING"

# Analytics worker health + latency
curl -s http://localhost:8001/health | jq .

# Proxy divergence (SOFR vs T-Bill)
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT detected_at, divergence_score, correlation, status FROM proxy_divergence_events
   WHERE resolved_at IS NULL ORDER BY detected_at DESC LIMIT 5;"
```

## Disabling

To disable Global Safe Mode entirely (e.g. in an environment where the probe inputs are not wired),
set `monitor.demo.global-safe-mode.enabled: false`. When disabled, neither auto-detection nor manual
override can activate Safe Mode.

## Configuration

Under `monitor.demo.global-safe-mode` in `application.yml`:

| Property | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch |
| `overflow-utilization-threshold-pct` | `80` | Buffer fill % counted as degraded |
| `worker-latency-threshold-ms` | `5000` | Worker latency counted as degraded |
| `min-degraded-indicators` | `2` | Correlated-degradation threshold (1–3) |
| `evaluation-interval-ms` | `5000` | Scheduled probe cadence |

## Operational Notes

- **Volatile state:** like the kill-switch, Safe Mode state is in-memory; a process restart returns
  it to inactive (ADR-017 / ADR-018 trade-off).
- **Escalation:** if Safe Mode auto-latches more than 3 times in 24h, escalate to the risk committee
  — persistent correlated degradation signals an upstream data or infra problem.

## Reference

- ADR-018 — Systemic Resilience Monitor design
- `computation/.../demo/SystemicResilienceMonitor.java`, `ResilienceHealthProbe.java`,
  `ResilienceHealthSnapshot.java`
- `app/.../CrossModuleResilienceHealthProbe.java` (probe wiring)
