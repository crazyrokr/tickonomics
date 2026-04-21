# Monitoring & Troubleshooting

## Health Checks

### Backend (Spring Boot Actuator)

```bash
curl http://localhost:8080/actuator/health
```

Response includes datasource connectivity, disk space, and overall status.

### Analytics Worker

```bash
curl http://localhost:8001/health
```

Returns `status: UP` with scipy, statsmodels, and numpy versions.

### TimescaleDB

```bash
docker compose exec timescaledb pg_isready -U tickonomics
```

## Distributed Tracing (Jaeger)

### Access Jaeger UI

Open `http://localhost:16686` in a browser.

### Key trace queries

- **Service:** Select `tickonomics-backend` to see backend traces
- **Operation:** Look for `ingestion.fred.fetch`, `computation.ili.calculate`, `computation.signal.generate`
- **Tags:** Filter by `http.status_code`, `error`

### Identifying bottlenecks

1. Search for traces with high duration (> 1s).
2. Examine individual spans in the Java -> Python -> Java chain:
   - Ingestion fetch -> Data quality check -> ILI calculation -> Signal generation
3. Cross-reference slow traces with bulkhead pool utilization.

### Expected latency targets

| Path | p50 | p99 |
|------|-----|-----|
| Tick ingestion to signal | < 100ms | < 500ms |
| Analytics worker round-trip | < 50ms | < 200ms |
| ILI calculation | < 30ms | < 100ms |

## Common Issues

### Backend fails to start: "Connection refused" to TimescaleDB

**Symptom:** Backend logs show `org.postgresql.util.PSQLException: Connection refused`.

**Cause:** TimescaleDB is not ready when backend starts.

**Fix:** The `depends_on` with `condition: service_healthy` should prevent this. If it persists:

```bash
docker compose restart backend
```

### Backend fails to start: "Flyway migration failed"

**Symptom:** Backend logs show Flyway validation errors.

**Cause:** Schema mismatch or corrupted `flyway_schema_history`.

**Fix:** For development only:

```bash
docker compose down -v
docker compose up -d
```

This recreates the database and re-runs all migrations.

### Analytics worker returns 503

**Symptom:** Backend logs show `RestClientException` when calling analytics worker.

**Cause:** Analytics worker not started or health check failing.

**Fix:**

```bash
docker compose logs analytics-worker
docker compose restart analytics-worker
```

### Dashboard shows "Failed to fetch"

**Symptom:** Dashboard loads but API calls fail.

**Cause:** Backend not reachable from dashboard container.

**Fix:** Verify the `NEXT_PUBLIC_API_URL` or proxy configuration points to `http://backend:8080` (container name, not localhost).

### TimescaleDB disk full

**Symptom:** Write errors, slow queries.

**Fix:** See [Data Management runbook](data-management.md) for compression and retention procedures.

## Log Access

```bash
docker compose logs -f backend           # Follow backend logs
docker compose logs --tail=100 analytics-worker  # Last 100 lines
docker compose logs -t --since=1h timescaledb    # Last hour with timestamps
```
