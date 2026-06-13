# Big Red Button — Emergency Kill-Switch Runbook

## Purpose

Halt all automated paper-trading within 50ms during critical incidents. Backed by the in-process
`KillSwitch` service (ADR-017) and exposed under `/api/v1/demo/kill-switch/*`.

> **Scope:** the kill-switch halts new `PaperTradingEngine` trade execution. Data collection
> (`TimescaleDbWriter`) continues; the analytics worker and ingestion are unaffected.

## Triggers

- Critical system failure (data corruption, runaway algorithm)
- Detected fraud or unauthorized activity
- Regulatory order to cease operations
- Unexplained P&L deviation beyond tolerance

## Step 1: Activate the Kill-Switch

```bash
curl -X POST http://localhost:8080/api/v1/demo/kill-switch/activate \
  -H "Content-Type: application/json" \
  -d '{"SPY": 500.0}'
```

The optional JSON body maps symbols to current market prices. When present (and
`monitor.demo.kill-switch.liquidate-on-activate` is `true`, the default), every open position is
liquidated at the supplied prices; the response reports the count:

```json
{"active": true, "liquidatedTrades": 3}
```

Omit the body to arm the switch without liquidation:

```json
{"active": true}
```

While active, `PaperTradingEngine.processSignal` short-circuits every signal with reason
`kill_switch_active` — no new positions open.

## Step 2: Verify Activation

```bash
curl -s http://localhost:8080/api/v1/demo/kill-switch/status | jq .
# {"active": true}
```

Confirm the backend rejected a recent signal:

```bash
docker compose logs backend --tail=50 | grep "kill_switch_active"
```

## Step 3: Full System Shutdown (if required)

If the incident demands a complete shutdown beyond trading:

```bash
docker compose stop backend          # backend only
docker compose down                  # all services
```

## Manual Recovery

Re-arm trading only after incident review and sign-off. The kill-switch does not auto-clear —
recovery is an explicit manual acknowledgment:

```bash
curl -X POST http://localhost:8080/api/v1/demo/kill-switch/deactivate | jq .
# {"active": false}
```

## Data Preservation Guarantees

| Aspect | Behavior |
|---|---|
| Open positions | Liquidated at supplied prices when the body is provided; otherwise held |
| New signals | Rejected (`kill_switch_active`) until deactivated |
| Tick ingestion | Continues uninterrupted |
| Historical data | Fully retained in TimescaleDB |
| Audit trail | Activate/deactivate events logged |

## Operational Notes

- **Volatile state:** `KillSwitch` state is in-memory; a process restart returns it to inactive.
  Demo trading is opt-in (`monitor.demo.enabled=false`), so this is an accepted trade-off — see
  ADR-017.
- **Related control:** Global Safe Mode ([systemic-resilience-monitor.md](systemic-resilience-monitor.md))
  is the *automatic* counterpart — it latches on correlated cross-module degradation. The kill-switch
  is the *manual* emergency stop.

## SLA

- **Target:** <50ms from `POST /api/v1/demo/kill-switch/activate` to `PaperTradingEngine` rejecting
  the next signal.
- **Verification:** measure response latency from the curl output or APM traces.

## Reference

- ADR-017 — demo / virtual portfolio finalization (kill-switch design)
- Controller: `web/.../controller/DemoController.java` — `/api/v1/demo/kill-switch/*`
