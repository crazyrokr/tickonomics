# TimescaleDB Continuous Aggregate Refresh

## Overview

Continuous aggregates provide pre-computed rollups of tick and OHLCV data. Aggregates are defined in migrations V3 (1-minute OHLCV), V12 (hourly rollups), and V15 (daily statistical summaries). Keeping them fresh is critical for dashboard accuracy.

## Check Refresh Status

```sql
SELECT hypertable_name, view_name, materialized_only, compression_enabled
FROM timescaledb_information.continuous_aggregates;
```

## Check Automatic Refresh Policies

```sql
SELECT job_id, hypertable_name, schedule_interval, max_runtime,
       last_run_status, last_run_started_at
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_refresh_continuous_aggregate';
```

## Manual Refresh

Refresh a specific time window. Always use a bounded range -- unbounded refreshes are expensive.

```sql
CALL refresh_continuous_aggregate('candle_one_minute', '2026-06-01 00:00:00', '2026-06-03 00:00:00');
CALL refresh_continuous_aggregate('candle_hourly', '2026-05-01 00:00:00', '2026-06-03 00:00:00');
CALL refresh_continuous_aggregate('daily_stats', '2026-01-01 00:00:00', '2026-06-03 00:00:00');
```

## Common Issues

### Stale Aggregates (data does not match raw tick data)

1. Confirm the refresh job ran successfully:
   ```sql
   SELECT last_run_status, last_run_started_at
   FROM timescaledb_information.jobs
   WHERE proc_name = 'policy_refresh_continuous_aggregate';
   ```
2. If `last_run_status` is not `Success`, check the server logs for the associated job ID.
3. Force a manual refresh over the affected time range (see above).

### Refresh Job Failures

1. Check for long-running refreshes blocking the job:
   ```sql
   SELECT pid, now() - xact_start AS duration, query
   FROM pg_stat_activity
   WHERE query LIKE '%refresh_continuous_aggregate%';
   ```
2. Terminate a stuck refresh if needed:
   ```sql
   SELECT pg_terminate_backend(<pid>);
   ```
3. Re-run the manual refresh.

## Compression Policy (V4 Migration)

Old chunks are compressed automatically to reduce storage. Verify compression status:

```sql
SELECT chunk_name, compression_status, before_compression_bytes, after_compression_bytes
FROM timescaledb_information.chunks
WHERE hypertable_name = 'tick'
ORDER BY chunk_name DESC
LIMIT 10;
```

If compression is not running, check the compression policy job:

```sql
SELECT * FROM timescaledb_information.jobs
WHERE proc_name = 'policy_compression';
```

## Emergency: Disable Automatic Refresh

If a runaway refresh is consuming resources, disable the job temporarily:

```sql
SELECT alter_job(<job_id>, schedule_interval => INTERVAL '1 year');
-- Re-enable when ready:
SELECT alter_job(<job_id>, schedule_interval => INTERVAL '1 hour');
```
