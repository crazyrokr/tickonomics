# Proxy Divergence Event Review

## Overview

The `ProxyDivergenceGuard` monitors the spread between proxy rates (e.g., RRP
proxy) and actual rates (e.g., SOFR). When divergence exceeds acceptable bounds,
events are logged for operator review.

## Detection

- `ProxyDivergenceGuard` logs warnings at `WARN` level.
- Entries in the `proxy_divergence_events` database table.
- Monitoring alert triggered on divergence threshold breach.

## Querying Open Events

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT id, proxy_type, proxy_value, actual_value, spread, detected_at
   FROM proxy_divergence_events
   WHERE resolved_at IS NULL
   ORDER BY detected_at DESC;"
```

## Reviewing Each Event

For each unresolved event:

1. Compare the proxy value against the actual rate:

   ```bash
   docker compose exec timescaledb psql -U tickonomics -c \
     "SELECT proxy_type, proxy_value, actual_value, spread
      FROM proxy_divergence_events WHERE id = <EVENT_ID>;"
   ```

2. Check the spread magnitude. A spread > 5 basis points warrants
   investigation.

3. Assess data quality for the event window by cross-referencing source feeds.

## Root Causes

| Cause | Diagnostic |
|---|---|
| Stale FRED data | Check `fred_update_log` for last successful fetch |
| Finnhub / Yahoo data gaps | Review ingestion logs for missing ticks |
| Calculation errors | Verify engine logs for arithmetic exceptions |

Check FRED update freshness:

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT series_id, last_updated FROM fred_update_log ORDER BY last_updated DESC LIMIT 10;"
```

## Resolution

Once the root cause is identified and corrected:

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "UPDATE proxy_divergence_events
   SET resolved_at = NOW(),
       resolution_note = '<brief description of fix>'
   WHERE id = <EVENT_ID>;"
```

Resolve all events in bulk if they share the same root cause:

```bash
docker compose exec timescaledb psql -U tickonomics -c \
  "UPDATE proxy_divergence_events
   SET resolved_at = NOW(),
       resolution_note = '<brief description>'
   WHERE resolved_at IS NULL AND proxy_type = '<AFFECTED_TYPE>';"
```

## Prevention

1. Tune the LKG cache staleness threshold:

   ```yaml
   lkg-cache:
     max-staleness-seconds: 300
   ```

   Reduce this value to reject stale proxy data sooner.

2. Verify the FRED API key is active and not rate-limited:

   ```bash
   curl -s "https://api.stlouisfed.org/fred/series?series_id=SOFR&api_key=${FRED_API_KEY}" | head -20
   ```

3. Set up proactive monitoring on the `proxy_divergence_events` table to alert
   on unresolved events older than 1 hour.
