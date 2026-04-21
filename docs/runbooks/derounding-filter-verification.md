# De-Rounding Filter Verification Runbook

## Purpose

Verify that the De-Rounding filter correctly smooths volume spikes at 5-minute round marks caused by systematic rebalancing flows.

## Component Location

`com.tickonomics.ingestion.filter.DeRoundingFilter`

## Configuration

| Property | Default | Description |
|---|---|---|
| `filter.derounding.threshold-ms` | 5000 | Window around round marks to apply smoothing |
| `filter.derounding.enabled` | true | Enable/disable the filter |

Configuration is set in `application.yml`:

```yaml
filter:
  derounding:
    threshold-ms: 5000
    enabled: true
```

## Step 1: Inspect Raw Volume Data

Query recent tick data for a given symbol to identify unprocessed volume patterns:

```sql
SELECT time, volume
FROM tick_data
WHERE symbol = 'AAPL'
ORDER BY time DESC
LIMIT 100;
```

Look for volume spikes at `:00`, `:05`, `:10`, `:15`, `:20`, `:25`, `:30`, `:35`, `:40`, `:45`, `:50`, `:55` minute marks. These are characteristic of systematic rebalancing activity.

## Step 2: Compare Filtered Output

Query the filtered data stream and compare against raw values:

```sql
SELECT raw.time, raw.volume AS raw_volume, filt.volume AS filtered_volume
FROM tick_data raw
JOIN filtered_tick_data filt ON raw.time = filt.time AND raw.symbol = filt.symbol
WHERE raw.symbol = 'AAPL'
  AND raw.time > NOW() - INTERVAL '1 hour'
ORDER BY raw.time DESC;
```

Expected: filtered values show smoothed volumes at round marks, with no abrupt spikes.

## Step 3: Verify Filter Is Active

Check application logs for filter initialization:

```bash
docker compose logs backend | grep -i "DeRoundingFilter"
```

Verify the filter bean is registered:

```bash
curl -s http://localhost:8080/actuator/beans | jq '.contexts[].beans | keys[]' | grep -i deround
```

## Troubleshooting

### Spikes still visible in filtered output

1. Reduce the threshold window:

```yaml
filter:
  derounding:
    threshold-ms: 3000  # narrower window, more aggressive smoothing
```

2. Confirm the filter is enabled:

```bash
curl -s http://localhost:8080/actuator/configprops | jq '. | .. | objects | select(.derounding) // empty'
```

3. Restart the backend to apply configuration changes:

```bash
docker compose restart backend
```

### Filter appears inactive

Verify no profile override is disabling it:

```bash
grep -r "derounding" backend/src/main/resources/
```

## Disable for Testing

Temporarily disable the filter without code changes:

```yaml
filter:
  derounding:
    enabled: false
```

Or via environment variable:

```bash
FILTER_DEROUNDING_ENABLED=false docker compose up backend
```

Remember to re-enable after testing.

## Verification Checklist

- [ ] Raw data shows volume spikes at 5-minute marks
- [ ] Filtered data smooths those spikes within threshold
- [ ] Filter is enabled and registered in Spring context
- [ ] Configuration values match operational requirements
