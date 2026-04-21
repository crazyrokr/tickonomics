# Polygon WebSocket Outage Response

## Overview

This runbook covers procedures during Polygon.io WebSocket outages that affect
real-time tick data ingestion.

## Detection

- No tick data received for more than 60 seconds.
- Log entries containing `WebSocket disconnected` or `Polygon connection lost`.
- Alert from the ingestion health monitor.

## Immediate Actions

### 1. Check Polygon Service Status

```bash
curl -s https://status.polygon.io
```

Verify whether Polygon reports an active incident.

### 2. Inspect Backend Logs

```bash
docker compose logs backend --tail 200 | grep -i "websocket\|polygon\|ingestion"
```

Look for reconnect attempts or authentication errors.

## Fallback Behaviour

The system automatically switches to REST API polling when the WebSocket
connection is lost for more than 30 seconds. No manual intervention is needed
for the fallback to activate. Verify it is running:

```bash
docker compose logs backend --tail 50 | grep "REST polling"
```

## Manual Override

If the system does not recover automatically:

```bash
docker compose restart backend
```

This forces a fresh WebSocket handshake with Polygon.

## Verification

After recovery, confirm the system is healthy:

```bash
curl -s http://localhost:8080/actuator/health | jq .status
```

Check that new ticks are being written:

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT COUNT(*) FROM tick_data WHERE created_at > NOW() - INTERVAL '5 minutes';"
```

## Post-Incident Review

1. Review the ingestion buffer for data gaps during the outage window.
2. Verify the LKG (Last Known Good) cache is fresh:

   ```bash
   docker compose logs backend --tail 500 | grep "LKG cache"
   ```

3. If gaps exist, backfill from Polygon REST API using the manual ingestion
   endpoint.
4. Document the outage duration and data impact in the incident log.
