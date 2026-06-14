# Options Data Pipeline Verification Runbook

## Purpose

Verify the end-to-end flow of options data from the free-tier **Yahoo Finance** options source
through ingestion to storage, and confirm downstream consumers (GEX regime detection) receive valid
data. (Replaces the v5 Polygon Options pipeline — options are fetched via `YahooOptionsClient` in
v6; no API key required.)

> **Opt-in:** the options client is gated behind `monitor.yahoo-finance.options-enabled` (default
> `false`). This runbook applies only when that flag is `true`.

## Pipeline Architecture

```
Yahoo Finance Options REST (YahooOptionsClient)
  -> YahooOptionsCdmAdapter
    -> IngestionBuffer
      -> TimescaleDbWriter
        -> option_chain_snapshots table
          -> GEX-weighted regime detection
            -> market_gamma_history hypertable
```

## Step 1: Verify Data Arrival

Check that option chain snapshots are being written:

```sql
SELECT COUNT(*)
FROM option_chain_snapshots
WHERE time > NOW() - INTERVAL '1 day';
```

Expected: a non-zero count that grows steadily during market hours.

## Step 2: Check Data Freshness

```sql
SELECT MAX(time) AS latest_snapshot, NOW() AS current_time,
       EXTRACT(EPOCH FROM (NOW() - MAX(time))) AS seconds_behind
FROM option_chain_snapshots;
```

Expected: `seconds_behind` under the configured poll interval during active market hours.

## Step 3: Validate Data Quality

```sql
SELECT time, symbol, implied_volatility_atm, net_gamma, open_interest
FROM option_chain_snapshots
WHERE time > NOW() - INTERVAL '1 hour'
ORDER BY time DESC
LIMIT 20;
```

Quality checks:

| Field | Valid Range | Red Flag |
|---|---|---|
| `implied_volatility_atm` | > 0 | NULL or <= 0 indicates upstream failure |
| `net_gamma` | Any finite value | NULL indicates calculation failure |
| `open_interest` | >= 0 | Negative values indicate data corruption |

## Step 4: Verify GEX Calculation

```sql
SELECT time, symbol, net_gamma, gamma_flip_price
FROM market_gamma_history
WHERE time > NOW() - INTERVAL '1 day'
ORDER BY time DESC
LIMIT 10;
```

## Troubleshooting

### No data in option_chain_snapshots

1. **Confirm the options client is enabled** (it is off by default):

```bash
docker compose exec backend printenv | grep -i options
docker compose logs backend --tail=200 | grep -i "YahooOptionsClient"
```

If `monitor.yahoo-finance.options-enabled` is unset/false, no client bean is created. Set it to
`true` and restart.

2. **Check Yahoo REST connectivity** (no key — public endpoints):

```bash
docker compose logs backend --tail=100 | grep -iE "yahoo.*option|options"
```

3. **Check ingestion buffer / writer**:

```bash
docker compose logs backend --tail=200 | grep -i "TimescaleDbWriter\|option_chain"
```

### Stale data (freshness check fails)

1. Confirm market is open (options trade during equity market hours).
2. Yahoo public endpoints throttle aggressive polling — check for `429`s and back off via
   `monitor.yahoo-finance.read-timeout`.
3. Restart the backend to refresh the polling schedule:

```bash
docker compose restart backend
```

### Invalid data quality

1. Cross-reference with the Yahoo Finance options page for the same symbol/expiry.
2. Check for schema changes in the upstream feed (Yahoo field names are unofficial and can shift).
3. Review ingestion buffer for partial or malformed messages.

## Configuration

Under `monitor.yahoo-finance` in `application.yml`:

| Property | Default | Description |
|---|---|---|
| `options-enabled` | `false` | Master switch for `YahooOptionsClient` |
| `base-url` | `https://query1.finance.yahoo.com` | Yahoo Finance REST base URL |
| `options-symbols` | `SPY,QQQ` | Symbols whose option chains are polled |
| `connect-timeout` / `read-timeout` | `5s` / `30s` | REST timeouts |

## Health Check Summary

```bash
echo "=== Row Count ===" && \
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT COUNT(*) FROM option_chain_snapshots WHERE time > NOW() - INTERVAL '1 day';" && \
echo "=== Freshness ===" && \
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT NOW() - MAX(time) AS staleness FROM option_chain_snapshots;" && \
echo "=== Data Quality ===" && \
docker compose exec timescaledb psql -U tickonomics -c \
  "SELECT COUNT(*) AS invalid_rows FROM option_chain_snapshots WHERE implied_volatility_atm <= 0;"
```
