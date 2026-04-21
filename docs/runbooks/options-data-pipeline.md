# Options Data Pipeline Verification Runbook

## Purpose

Verify the end-to-end flow of options data from Polygon API through ingestion to storage, and confirm downstream consumers (GEX regime detection) receive valid data.

## Pipeline Architecture

```
Polygon Options API (WebSocket)
  -> OptionsDataClient
    -> IngestionBuffer
      -> TimescaleDbWriter
        -> option_chain_snapshots table
          -> GexWeightedRegimeDetector
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

Verify data is recent and not stale:

```sql
SELECT MAX(time) AS latest_snapshot, NOW() AS current_time,
       EXTRACT(EPOCH FROM (NOW() - MAX(time))) AS seconds_behind
FROM option_chain_snapshots;
```

Expected: `seconds_behind` should be under 60 seconds during active market hours.

## Step 3: Validate Data Quality

Check that key fields contain reasonable values:

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

The `GexWeightedRegimeDetector` consumes options data and writes to `market_gamma_history`:

```sql
SELECT time, symbol, weighted_gamma, regime_adjustment
FROM market_gamma_history
WHERE time > NOW() - INTERVAL '1 day'
ORDER BY time DESC
LIMIT 10;
```

Confirm regime adjustments are being applied based on gamma exposure.

## Troubleshooting

### No data in option_chain_snapshots

1. **Check API key**:

```bash
docker compose exec backend env | grep POLYGON_API_KEY
# Must be set and valid
```

2. **Check WebSocket connectivity**:

```bash
docker compose logs backend --tail=100 | grep -i "polygon\|websocket\|options"
```

Look for connection errors or authentication failures.

3. **Check ingestion buffer**:

```bash
curl -s http://localhost:8080/actuator/metrics | jq '.names[]' | grep -i buffer
curl -s http://localhost:8080/actuator/metrics/ingestion.buffer.size
```

A growing buffer with no drain indicates a writer issue.

4. **Check TimescaleDB writer**:

```bash
docker compose logs backend --tail=200 | grep -i "TimescaleDbWriter\|option_chain"
```

### Stale data (freshness check fails)

1. Verify Polygon API status: `https://api.polygon.io/v1/open-close/crypto/BTC-USD/2026-06-03`
2. Check if market is open (options trade during equity market hours only)
3. Restart the WebSocket connection:

```bash
docker compose restart backend
```

### Invalid data quality

1. Cross-reference with Polygon REST API for the same timestamp
2. Check for schema changes in the upstream data feed
3. Review ingestion buffer for partial or malformed messages

## Configuration

| Property | Description |
|---|---|
| `polygon.api.key` | API key for Polygon.io |
| `polygon.options.websocket.enabled` | Enable options WebSocket feed |
| `polygon.options.symbols` | List of symbols to subscribe to |

## Health Check Summary

```bash
# Full pipeline health in one script
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
