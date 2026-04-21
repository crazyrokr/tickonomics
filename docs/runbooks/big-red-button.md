# Big Red Button - Emergency Kill-Switch Runbook

## Purpose

Halt all automated trading and signal dispatch within 50ms during critical incidents.

## Triggers

- Critical system failure (data corruption, runaway algorithms)
- Detected fraud or unauthorized activity
- Regulatory order to cease operations
- Unexplained P&L deviation beyond tolerance

## Step 1: Halt Paper Trading Engine

Immediately stop signal processing and trade execution:

```bash
curl -X POST http://localhost:8080/api/v1/demo/stop
```

Expected response:

```json
{"status": "STOPPED", "timestamp": "2026-06-03T12:00:00Z"}
```

This halts the `PaperTradingEngine` instantly. All in-flight trades complete gracefully. New signals are queued but not dispatched.

## Step 2: Full System Shutdown (if required)

If the situation demands a complete shutdown beyond signal halting:

```bash
docker compose stop backend
```

To stop all services:

```bash
docker compose down
```

## Step 3: Verify Halt

Confirm no signal processing is occurring:

```bash
curl -s http://localhost:8080/actuator/health | jq '.status'
# Expected: "UP" but with trading engine reporting STOPPED

curl -s http://localhost:8080/actuator/health | jq '.components.paperTradingEngine.status'
# Expected: "STOPPED"
```

Check backend logs for halt confirmation:

```bash
docker compose logs backend --tail=20 | grep -i "stop"
```

## Manual Recovery

Re-enable trading only after incident review and sign-off:

```bash
# 1. Review incident logs
docker compose logs backend --since=1h | grep -E "ERROR|WARN|STOP"

# 2. Confirm authorization (require written approval)
# 3. Re-enable trading engine
curl -X POST http://localhost:8080/api/v1/demo/start
```

Expected response:

```json
{"status": "STARTED", "timestamp": "2026-06-03T12:30:00Z"}
```

## Data Preservation Guarantees

| Aspect | Behavior |
|---|---|
| In-flight trades | Complete to settlement |
| Queued signals | Preserved, not dispatched until restart |
| Historical data | Fully retained in TimescaleDB |
| Audit trail | All halt/start events logged with timestamps |

## Post-Incident Checklist

1. Review all logs from incident window
2. Generate audit trail export for compliance
3. Verify queued signals are valid before resuming
4. File compliance report if regulatory trigger
5. Schedule post-mortem within 24 hours

## SLA

- **Target**: <50ms from `POST /api/v1/demo/stop` to halt confirmation
- **Verification**: measure response time from curl output or APM metrics
