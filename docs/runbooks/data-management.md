# Data Management

## Compression

TimescaleDB automatically compresses tick data older than 7 days (configured in V4 migration).

### Check compression status

```sql
SELECT hypertable_name, chunk_name, is_compressed, compressed_chunk_name
FROM timescaledb_information.chunks
WHERE hypertable_name = 'tick_data'
ORDER BY chunk_name DESC
LIMIT 20;
```

### Compression settings

```sql
SELECT * FROM timescaledb_information.compression_settings;
```

### Manual compression trigger

If automatic compression is lagging:

```sql
SELECT compress_chunk(c) FROM show_chunks('tick_data', older_than => INTERVAL '7 days') c;
```

## Retention Policies

| Table | Retention | Configured in |
|-------|-----------|---------------|
| `tick_data` | 90 days | V4 migration |
| `signal_log` | 730 days | V4 migration |
| `ingestion_dlq` | 90 days | V4 migration |
| `proxy_divergence_events` | Indefinite | No retention policy |

### Check retention jobs

```sql
SELECT application_name, schedule_interval, max_runtime, last_run_duration
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_retention';
```

### Modify retention period

```sql
SELECT alter_job(
  (SELECT job_id FROM timescaledb_information.jobs WHERE hypertable_name = 'tick_data' AND proc_name = 'policy_retention'),
  schedule_interval => INTERVAL '1 day'
);
```

## Continuous Aggregates

### Check aggregate freshness

```sql
SELECT view_name, materialized_only, compression_enabled
FROM timescaledb_information.continuous_aggregates;
```

### Manual refresh

```sql
SELECT refresh_continuous_aggregate('ohlcv_1min', NULL, NULL);
```

## Backup & Recovery

### Create a backup

```bash
docker compose exec timescaledb pg_dump -U tickonomics tickonomics > backup_$(date +%Y%m%d).sql
```

### Restore from backup

```bash
docker compose exec -T timescaledb psql -U tickonomics tickonomics < backup_20260531.sql
```

### Volume-level backup

```bash
docker run --rm -v tickonomics_timescaledb_data:/data -v $(pwd):/backup alpine \
    tar czf /backup/timescaledb_volume_$(date +%Y%m%d).tar.gz -C /data .
```

## Database Size Monitoring

### Overall database size

```sql
SELECT pg_size_pretty(pg_database_size('tickonomics'));
```

### Per-table sizes

```sql
SELECT hypertable_name,
       pg_size_pretty(total_bytes) AS total_size
FROM timescaledb_information.hypertables
ORDER BY total_bytes DESC;
```

### Chunk count and size distribution

```sql
SELECT hypertable_name,
       COUNT(*) AS chunk_count,
       pg_size_pretty(SUM(total_bytes)) AS total_size
FROM timescaledb_information.chunks
GROUP BY hypertable_name
ORDER BY SUM(total_bytes) DESC;
```
