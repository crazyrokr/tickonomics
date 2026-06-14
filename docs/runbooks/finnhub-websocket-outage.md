# Finnhub WebSocket Outage Response

## Overview

This runbook covers procedures during Finnhub WebSocket outages that affect real-time tick data
ingestion. (Replaces the v5 Polygon WebSocket outage procedure — Finnhub is the v6 real-time
source.)

## Detection

- No trade data received for more than 60 seconds.
- Log entries containing `WebSocket disconnected`, `Finnhub connection lost`, or repeated reconnect
  attempts.
- `FINNHUB_WS_FALLBACK` or `OVERFLOW_QUEUE_GROWING` log markers.
- Alert from the ingestion health monitor.

## Automatic Fallback Behaviour

The ingestion layer is designed to fail over without manual intervention:

1. **Circuit breaker** opens after 5 consecutive WS failures (`FinnhubWsClient`).
2. **REST fallback** activates: `FinnhubEquityClient` polls equity aggregates (configurable interval)
   so tick-derived KPIs keep refreshing from aggregated data.
3. **Auto-reconnect** with exponential backoff (`ws-reconnect-backoff-max`, default 60s).
4. **Overflow buffer** captures inbound items to the `ingestion_overflow` volume while writes lag;
   see [chronicle-queue-overflow.md](chronicle-queue-overflow.md).

## Immediate Actions

### 1. Check Finnhub Service Status

```bash
curl -s https://status.finnhub.io
```

Verify whether Finnhub reports an active incident.

### 2. Inspect Backend Logs

```bash
docker compose logs backend --tail=200 | grep -iE "websocket|finnhub|fallback|reconnect"
```

Look for reconnect attempts, auth errors, or `FINNHUB_WS_FALLBACK` indicating the REST fallback is
serving data.

### 3. Confirm Fallback Is Running

```bash
docker compose logs backend --tail=100 | grep -i "FINNHUB_WS_FALLBACK"
```

### 4. Verify API Key Is Valid

A silent auth failure can look like an outage:

```bash
docker compose exec backend printenv FINNHUB_API_KEY
curl -s "https://finnhub.io/api/v1/quote?symbol=SPY&token=$FINNHUB_API_KEY" | jq .
```

An empty/invalid response indicates a key problem rather than a Finnhub-wide outage.

## Manual Override

If the system does not recover automatically:

```bash
docker compose restart backend
```

This forces a fresh Finnhub WebSocket handshake.

## Verification

After recovery, confirm the system is healthy:

```bash
curl -s http://localhost:8080/actuator/health | jq .status
```

Check that new ticks are being written:

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT COUNT(*) FROM tick_data WHERE time > NOW() - INTERVAL '5 minutes';"
```

## Post-Incident Review

1. Review the ingestion overflow buffer for data gaps during the outage window.
2. Verify the LKG (Last Known Good) cache is fresh:
   ```bash
   docker compose logs backend --tail=500 | grep "LKG cache"
   ```
3. If gaps exist, backfill from Yahoo Finance REST / `DataHubBackfillClient`.
4. If correlated degradation tripped Global Safe Mode during the outage, clear it via the manual
   recovery acknowledgment: `POST /api/v1/demo/safe-mode/deactivate` (see
   [systemic-resilience-monitor.md](systemic-resilience-monitor.md)).
5. Document the outage duration and data impact in the incident log.

## Reference

- Plan: `docs/plan_v6/11-deployment-operations.md` — "Finnhub WebSocket Outage Procedure"
- ADR-012 — free data source migration (Polygon → Finnhub)
