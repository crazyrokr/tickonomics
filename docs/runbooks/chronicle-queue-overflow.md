# FileOverflowBuffer (Ingestion Buffer Overflow)

## Overview

The FileOverflowBuffer persists tick data to disk when the ingestion pipeline
cannot keep up with the incoming data rate. This replaced the previous Chronicle
Queue implementation.

## Detection

- Log message: `FileOverflowBuffer threshold exceeded`
- Docker volume `ingestion_overflow` usage above 80%:

  ```bash
  docker system df -v | grep ingestion_overflow
  ```

- Monitoring alert on `ingestion.buffer.size` metric.

## Immediate Actions

### 1. Check Host Disk Space

```bash
df -h /var/lib/docker
```

If the host disk is above 90%, free space before proceeding.

### 2. Verify TimescaleDB Connectivity

```bash
docker compose exec timescaledb pg_isready -U tickonomics
```

If the database is unreachable, the overflow buffer will continue growing.

## If Disk Is Full

1. Stop non-essential services to free disk space:

   ```bash
   docker compose stop openbb analytics-worker
   ```

2. Clean old overflow files older than 7 days:

   ```bash
   find /var/lib/docker/volumes/ingestion_overflow/_data -type f \
     -mtime +7 -delete
   ```

3. Restart essential services once space is available.

## If Database Is Slow

1. Check for long-running queries and locks:

   ```bash
   docker compose exec timescaledb psql -U tickonomics -c \
     "SELECT pid, state, query, wait_event FROM pg_stat_activity WHERE state != 'idle';"
   ```

2. Review TimescaleDB compression policy to ensure old partitions are
   compressed:

   ```bash
   docker compose exec timescaledb psql -U tickonomics -c \
     "SELECT * FROM timescaledb_information.compression_settings;"
   ```

## Recovery

1. Restart the backend to replay buffered data:

   ```bash
   docker compose restart backend
   ```

2. Monitor replay progress in logs:

   ```bash
   docker compose logs backend -f | grep "FileOverflowBuffer replay"
   ```

3. Verify no data loss by comparing expected vs. actual tick counts for the
   affected window.

## Prevention

Tune the following configuration parameters in `application.yml`:

```yaml
writer:
  batch-size: 500
  flush-interval-ms: 1000
```

- Increase `batch-size` for higher throughput at the cost of memory.
- Decrease `flush-interval-ms` for lower latency writes.
- Increase the `ingestion_overflow` volume size in `docker-compose.yml` to
  provide a larger safety margin.
