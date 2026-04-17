# Track 11: Deployment & Operations

**Phase:** Phase 8
**Can start:** After all other tracks complete
**Blocks:** Nothing (final track)
**Depends on:** All tracks

---

## Objective

Containerize all artifacts, create Docker Compose configurations for each environment,
document operational runbooks, and implement data retention automation.

**Analysis findings applied (v1):**

- **Finding 1 (Virtual Threads Primary):** Backend runs Virtual Threads + Spring MVC only.
  No WebFlux mode. `SPRING_PROFILES_ACTIVE=virtual-threads` is the single profile. The
  "Concurrency Mode Switch" runbook is removed.
- **Finding 7 (Chronicle Queue Placement):** Docker Compose maps a dedicated Docker volume
  for Chronicle Queue overflow, separate from TimescaleDB data volume. Production deployments
  map this to a dedicated NVMe partition or `tmpfs` mount.

**External integration (v2):**

- **FINOS TimeBase-CE (Defer):** Noted as a future high-performance backup to TimescaleDB if
  sub-millisecond time-series ingestion is required. Not integrated now due to operational
  burden (second data infrastructure) and contradiction of the single-DB architecture. If
  adopted in the future, it would serve as a hot cache for the most recent tick window with
  TimescaleDB as the long-term store. Migration path documented below.

**Improvement proposals — deferred (v3):**

- **QuestDB Tiered Storage (Proposal #1, Defer):** QuestDB as a hot-path ingestion sidecar
  (last 24-48h of ticks) with TimescaleDB as cold-path analytical store. Deferred because the
  current ~10-symbol scope doesn't justify 4M rows/sec capacity, and reintroducing a second
  database contradicts the single-TimescaleDB consolidation decision. Re-evaluate only if
  TimescaleDB write throughput becomes a measured bottleneck.
- **Shared-Memory Arrow IPC (Proposal #4, Defer):** Arrow shared-memory buffers between Java
  and Python processes on the same host, eliminating network overhead. Deferred because
  containerized deployments run Java and Python in separate containers — shared memory requires
  `ipc: host` mode or volume-mounted shared memory segments, undermining container isolation.
  Socket-based Arrow IPC is already sub-ms for this workload. Revisit for bare-metal deployments.

---

## Containerization

### 1. Backend Dockerfile (Multi-Stage)

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
COPY --from=build /app/app-mvc/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Includes TA-Lib JAR (pure Java, no native binaries).

### 2. Analytics Dashboard Dockerfile

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/ .
RUN npm ci && npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 3. Analytics Worker Dockerfile

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY analytics/ .
RUN pip install --no-cache-dir -r requirements.txt
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

### 4. Landing Page Dockerfile

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY landing/ .
RUN npm ci && npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY landing/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Docker Compose Configurations

### `docker-compose.yml` (Local Dev)

```yaml
services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=virtual-threads
      - POLYGON_API_KEY=${POLYGON_API_KEY}
      - SPRING_DATASOURCE_URL=jdbc:postgresql://timescaledb:5432/tickonomics
    depends_on:
      - timescaledb
    volumes:
      # Dedicated volume for Chronicle Queue overflow (Finding 7)
      # Separate from TimescaleDB data to prevent I/O contention
      - chronicle_overflow:/data/overflow

  analytics-worker:
    build:
      context: ./analytics
      dockerfile: Dockerfile
    ports:
      - "8001:8001"

  dashboard:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    depends_on:
      - backend

  landing:
    build:
      context: ./landing
      dockerfile: Dockerfile
    ports:
      - "3001:80"

  timescaledb:
    image: timescale/timescaledb:latest-pg16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=tickonomics
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - timescaledb_data:/var/lib/postgresql/data

volumes:
  timescaledb_data:
  chronicle_overflow:
```

### Bulkhead Configuration in Docker Compose (v4)

Add bulkhead pool environment variables to the backend service:

```yaml
services:
  backend:
    environment:
      - MONITOR_BULKHEAD_CRITICAL_POOL_SIZE=4
      - MONITOR_BULKHEAD_HIGH_VOLUME_POOL_SIZE=16
      - MONITOR_BULKHEAD_COMPUTATION_POOL_SIZE=8
```

### OpenTelemetry Infrastructure (v4)

Add Jaeger/Tempo service to Docker Compose for distributed tracing:

```yaml
services:
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"  # Jaeger UI
      - "4317:4317"    # OTLP gRPC
    environment:
      - COLLECTOR_OTLP_ENABLED=true
```

Backend and analytics worker configured to export traces:

```yaml
  backend:
    environment:
      - MONITOR_TRACING_ENABLED=true
      - MONITOR_TRACING_EXPORTER=otlp
      - MONITOR_TRACING_ENDPOINT=http://jaeger:4317

  analytics-worker:
    environment:
      - TRACING_ENABLED=true
      - TRACING_EXPORTER=otlp
      - TRACING_ENDPOINT=http://jaeger:4317
```

### `docker-compose.openbb.yml` (Reference)

```yaml
# Reference compose for OpenBB Platform sidecar (user-managed)
services:
  openbb:
    image: openbb/platform:latest
    ports:
      - "8000:8000"
    environment:
      - OPENBB_API_KEY=${OPENBB_API_KEY}
    # User configures credentials separately
```

### `docker-compose.demo.yml` (Demo Environment)

```yaml
services:
  backend:
    # Same as main compose, plus:
    environment:
      - MONITOR_DEMO_ENABLED=true
      - MONITOR_DEMO_VIRTUAL_BALANCE=100000

  # Includes all services from docker-compose.yml
  # Plus: pre-seeded historical data, public-facing endpoints
```

---

## Environment Strategy

| Environment | API Keys                               | Database          | Purpose                              |
|:------------|:---------------------------------------|:------------------|:-------------------------------------|
| **dev**     | User-managed OpenBB, Polygon free tier | Local TimescaleDB | Development                          |
| **demo**    | Real API keys                          | Cloud TimescaleDB | Verification with virtual portfolio  |
| **staging** | Real API keys                          | Cloud TimescaleDB | Pre-production validation            |
| **prod**    | Real API keys                          | HA TimescaleDB    | Production (after demo verification) |

**Production gating:** Only deployed after Phase 6 demo verification criteria are met.

---

## Data Retention Automation

TimescaleDB background jobs (configured in Flyway migrations):

```sql
SELECT add_retention_policy('tick_data', drop_after => INTERVAL '90 days');
SELECT add_retention_policy('signal_log', drop_after => INTERVAL '730 days');
SELECT add_retention_policy('ingestion_dlq', drop_after => INTERVAL '90 days');
SELECT add_compression_policy('tick_data', compress_after => INTERVAL '7 days');

-- Proxy divergence events retained indefinitely for audit (Finding 3)
-- No retention policy on proxy_divergence_events
```

---

## Security Configuration

| Concern          | Implementation                                          |
|:-----------------|:--------------------------------------------------------|
| API Keys         | Environment variables or HashiCorp Vault, never in YAML |
| Polygon API Key  | `${POLYGON_API_KEY}` in application config              |
| WebSocket Auth   | Polygon API key in connection handshake                 |
| Frontend Auth    | OAuth2 + PKCE via Spring Security (Auth0/Keycloak)      |
| API Endpoints    | All `/api/**` require valid JWT                         |
| DB Credentials   | Environment variables, standard PostgreSQL auth         |
| HTTPS            | Enforced via `server.ssl` or reverse proxy              |
| OpenBB Sidecar   | Localhost or internal network, firewall restricted      |
| Analytics Worker | Localhost only, no external access                      |

---

## Runbook

### OpenBB Sidecar Setup

1. Start OpenBB Platform: `docker compose -f docker-compose.openbb.yml up -d`.
2. Configure credentials via OpenBB SDK or environment variables.
3. Verify: `curl http://localhost:8000/api/v1/health`.

### Analytics Worker Deployment

1. Build: `docker compose build analytics-worker`.
2. Start: `docker compose up -d analytics-worker`.
3. Verify: `curl http://localhost:8001/health`.

### Polygon WebSocket Outage Procedure

1. Circuit breaker opens automatically after 5 failures.
2. Fallback to direct API equity aggregates activates after 5 min.
3. Auto-reconnect with exponential backoff (1s → 60s).
4. Monitor `POLYGON_WS_FALLBACK` and `OVERFLOW_QUEUE_GROWING` logs.

### TimescaleDB Disk Space and Compression

1. Monitor: `SELECT * FROM timescaledb_information.compression_settings`.
2. Compression policy runs automatically on 7-day-old data.
3. If compression lag > 2 days: log `COMPRESSION_LAG`, investigate disk I/O.

### Chronicle Queue Overflow Recovery

1. Monitor buffer utilization gauge.
2. If overflow detected, Chronicle Queue captures to dedicated volume (`chronicle_overflow`).
3. On reconnect, replay via batched INSERT through `TimescaleDbWriter`.
4. Verify: `SELECT COUNT(*) FROM tick_data WHERE time > NOW() - INTERVAL '1 day'`.
5. **Storage configuration (Finding 7):** Ensure Chronicle Queue volume is on a separate
   physical disk from TimescaleDB `data` directory. In Docker, this is the `chronicle_overflow`
   named volume. In production bare-metal, map to a dedicated NVMe partition or `tmpfs` mount.

### ILI Weight Recalibration

1. Run `BacktestEngine` with current weights over last quarter.
2. Run `WeightOptimizer` for Bayesian optimization (100 iterations).
3. Compare: new Sharpe vs. current Sharpe.
4. If improved > 10%, update weights via config hot-reload.
5. Log config change to `config_snapshots`.

### Proxy Divergence Event Review

1. Check `proxy_divergence_events` table for recent dislocation incidents.
2. If unresolved events exist (`resolution IS NULL`), verify whether official SOFR has been
   published and 5-day correlation has restored.
3. Clear manually if needed: `UPDATE proxy_divergence_events SET resolution = 'MANUAL_CLEAR',
   resolved_at = NOW() WHERE id = ?`.
4. Monitor `PROXY_DIVERGENCE_DETECTED` log entries (Finding 3).

### TimescaleDB Continuous Aggregate Refresh

1. Check: `SELECT * FROM timescaledb_information.continuous_aggregates`.
2. If stale: `SELECT refresh_continuous_aggregate('ohlcv_1min', NULL, NULL)`.
3. Monitor: `COMPRESSION_LAG` log.

### TA-Lib Adapter Integration

1. TA-Lib is a pure Java dependency — no native library installation needed.
2. If `TalibAdapter` returns unexpected results, verify input array alignment.
3. Use `*_Lookback()` methods to determine valid output range.

### TimeBase-CE Future Migration Path (Deferred — v2)

Not for current implementation. Documented for future reference if sub-millisecond ingestion
is required.

**If needed in the future:**

1. Deploy TimeBase-CE alongside TimescaleDB: `docker compose -f docker-compose.timebase.yml up -d`.
2. Route real-time Polygon ticks to TimeBase first (hot cache for last 24h).
3. TimescaleDB remains the long-term store — batched writes from TimeBase every 1 minute.
4. Replace D3.js real-time chart data source with TimeBase streaming queries.
5. **Cost:** doubles data infrastructure operational burden. Requires TimeBase QQL/Schema API
   expertise. Only justified if tick ingestion latency exceeds TimescaleDB write throughput.

**Reference:** [FINOS TimeBase-CE](https://github.com/finos/TimeBase-CE)

### QuestDB Tiered Storage Path (Deferred — v3)

Not for current implementation. Documented for future reference if TimescaleDB write throughput
becomes a measured bottleneck at scale.

**If needed in the future:**

1. Deploy QuestDB alongside TimescaleDB: `docker compose -f docker-compose.questdb.yml up -d`.
2. Route real-time Polygon ticks to QuestDB first (hot path, last 24-48h).
3. QuestDB uses ILP (InfluxDB Line Protocol) for ingestion — 4M+ rows/sec.
4. TimescaleDB remains the long-term store — batched migration from QuestDB every 48h.
5. Pre-computed KPI views (V8) remain in TimescaleDB — they operate on aggregated data only.
6. **Cost:** dual-database operational burden (monitoring, backups, schema sync). QuestDB uses
   SQL but with custom extensions. Only justified if single-TimescaleDB ingestion is the proven
   bottleneck under real load.

**Reference:** [QuestDB](https://questdb.io/)

### Shared-Memory Arrow IPC Path (Deferred — v3)

Not for current implementation. Documented for future bare-metal deployments where Java and
Python share the same host.

**If needed in the future:**

1. Deploy backend and analytics worker without container isolation (bare-metal or single container).
2. Replace Arrow socket transport with `SharedMemoryChannel` from `pyarrow.ipc`.
3. Java side uses Apache Arrow Java's `SharedMemoryAllocator` for zero-copy buffer sharing.
4. Eliminates network stack overhead — latency drops from ~100us to nanoseconds.
5. **Cost:** loses container isolation, harder to scale independently, debugging complexity.
   Only justified if analytics worker round-trip latency is the proven bottleneck.

**Reference:** [Apache Arrow IPC — Shared Memory](https://arrow.apache.org/docs/cpp/ipc.html)

### Bulkhead Pool Monitoring (v4)

Monitor and adjust the three dedicated bulkhead executor pools.

1. Monitor per-pool utilization gauges:
    - `monitor.ingestion.bulkhead.critical.utilization` (FRED/NY Fed)
    - `monitor.ingestion.bulkhead.high-volume.utilization` (Polygon WS)
    - `monitor.ingestion.bulkhead.computation.utilization` (ILI/Signals)
2. If critical pool > 80%: investigate FRED/NY Fed ingestion latency. Check circuit breaker
   state — repeated half-open cycles indicate upstream API degradation.
3. If high-volume pool saturated: check Polygon WS message rate. Consider temporarily reducing
   symbol subscription count or increasing `high-volume-pool-size` via config hot-reload.
4. If computation pool starved: check TA-Lib calculation durations. Long-running correlation
   computations may need to be offloaded or batched differently.
5. Adjust pool sizes via config hot-reload if needed — no restart required.

### Distributed Tracing with Jaeger (v4)

End-to-end latency visibility across the Java-Python-Java chain.

1. Access Jaeger UI at `http://localhost:16686`.
2. Search for traces by service (`tickonomics-backend`, `analytics-worker`), operation name
   (e.g., `ingestion.fred.fetch`, `computation.ili.calculate`), or trace ID.
3. Identify bottleneck spans in the Java → Python → Java chain:
    - Ingestion fetch → Data quality check → ILI calculation → Signal generation
    - Look for spans with unusually high duration relative to their typical p50.
4. Monitor p99 latency for end-to-end signal generation path:
    - From Polygon tick receipt to signal log write.
    - Target: p99 < 500ms under normal load, < 2s during HIGH_VOL regime.
5. Cross-reference slow traces with bulkhead pool utilization to identify starvation.

### Calibration Task Monitoring (v4)

Monitor the `ScheduledCalibrationTask` that runs automated weight optimization.

1. Check `optimization_runs` table for recent calibration runs:
   ```sql
   SELECT id, method, status, started_at, completed_at, fitness_score
   FROM optimization_runs
   ORDER BY started_at DESC LIMIT 5;
   ```
2. Verify status is `COMPLETED` (not `FAILED` or stuck `RUNNING`).
    - If stuck `RUNNING` for > 2 hours: check analytics worker health and logs.
    - If `FAILED`: review `metadata` JSONB column for error details.
3. Review `active_weights` table for latest applied weights:
   ```sql
   SELECT applied_at, weights, source, backtest_sharpe, validation_passed
   FROM active_weights
   ORDER BY applied_at DESC LIMIT 1;
   ```
4. Compare backtest Sharpe before and after calibration:
    - `active_weights.backtest_sharpe` should show improvement over previous entry.
    - If Sharpe degraded, the validation pipeline should have caught it (`validation_passed = false`).
5. If `manual_approval` is enabled, review proposed changes in the admin dashboard before
   they are auto-deployed via `PUT /api/v1/config`.

**ScheduledCalibrationTask configuration:**

- 30-day trigger interval (runs monthly).
- 180-day lookback window for historical data.
- 30-day validation window (most recent data withheld for out-of-sample validation).
- Auto-deployment pipeline: if validation passes, trigger `PUT /api/v1/config` with new weights.
- Audit trail: all changes persisted to `config_snapshots` and `active_weights`.

### Disaster Alert Verification (v4)

Verify that disaster alert infrastructure is functioning correctly.

1. Check `disaster_alerts` table for recent alerts:
   ```sql
   SELECT detected_at, source, alert_type, severity, magnitude, location
   FROM disaster_alerts
   WHERE detected_at > NOW() - INTERVAL '7 days'
   ORDER BY detected_at DESC;
   ```
2. Verify `EXOGENOUS_SHOCK` regime was triggered for `CRITICAL` severity alerts:
   ```sql
   SELECT r.detected_at, r.regime, r.confidence, d.alert_type, d.severity
   FROM regime_detection_results r
   JOIN disaster_alerts d ON DATE(r.detected_at) = DATE(d.detected_at)
   WHERE d.severity = 'CRITICAL' AND r.regime = 'EXOGENOUS_SHOCK';
   ```
3. Confirm ILI status was set to `DISLOCATED` for the duration of the disaster alert:
   ```sql
   SELECT time, data_status
   FROM ili_history
   WHERE data_status = 'DISLOCATED'
   ORDER BY time DESC LIMIT 10;
   ```
4. Review suppressed signals in `signal_log` during disaster periods:
   ```sql
   SELECT created_at, symbol, direction, status, signal_metadata
   FROM signal_log
   WHERE signal_metadata->>'suppression_reason' LIKE '%disaster%'
   ORDER BY created_at DESC;
   ```

### Big Red Button Runbook (v5 — Proposal 06: AUMF)

Manual kill-switch as high-priority operational runbook.

1. **Activation:** Trigger the Big Red Button via admin endpoint `POST /api/v1/kill-switch/activate` or physical button
   in dashboard.
2. **Expected behavior:** All algorithmic trading activity halts within 50ms:
    - `SignalGenerator` stops dispatching new alerts.
    - `VirtualPortfolio` execution is halted.
    - All pending signals are cancelled.
    - `TimescaleDbWriter` continues writing (data collection does not stop).
3. **Verification:**
   ```sql
   SELECT COUNT(*) FROM signal_log WHERE created_at > NOW() - INTERVAL '1 minute' AND status NOT IN ('KILL_SWITCH_SUSPENDED');
   -- Should return 0
   ```
4. **Recovery:** Requires explicit manual acknowledgment:
   ```sql
   INSERT INTO config_snapshots (config_key, old_value, new_value, audit_reason, changed_by)
   VALUES ('kill_switch', 'ACTIVE', 'INACTIVE', 'Manual recovery after incident review', current_user);
   ```
5. **Continuous self-evaluation:** Integrated into live system health monitoring. Weekly automated test in demo
   environment.

### Systemic Resilience Monitor Runbook (v5 — Proposal 06: Systemic Resilience)

Monitor cross-module health for correlated degradation.

1. **Check cross-module health:**
   ```sql
   SELECT source, MAX(detected_at) as last_check
   FROM (SELECT 'ingestion' as source, MAX(created_at) FROM ingestion_dlq WHERE created_at > NOW() - INTERVAL '1 hour'
         UNION ALL
         SELECT 'computation', MAX(time) FROM ili_history WHERE time > NOW() - INTERVAL '1 hour'
         UNION ALL
         SELECT 'analytics_worker', MAX(computed_at) FROM regime_detection_results WHERE computed_at > NOW() - INTERVAL '1 hour') health;
   ```

2. **Global Safe Mode indicators:**
    - Chronicle Queue depth > 80% AND analytics worker latency > 5s: correlated degradation.
    - System enters Global Safe Mode automatically:
        - `TimescaleDbWriter` throughput throttled.
        - `SignalGenerator` stops dispatching.
        - `VirtualPortfolio` execution halted.

3. **Recovery from Global Safe Mode:**
    - Requires explicit manual acknowledgment.
    - Verify all three conditions resolved before clearing.
    - Log recovery in `config_snapshots` with `audit_reason`.

### Regulatory Compliance Report Generation (v5 — Proposal 05: Self-Certification)

Generate and review MiFID II-style self-certification reports.

1. **Generate report:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/backtest/regulatory-report \
     -H "Content-Type: application/json" \
     -d '{"period_start": "2026-02-21", "period_end": "2026-05-21"}'
   ```

2. **Review report contents:**
   ```sql
   SELECT generated_at, report_period_start, report_period_end, total_signals,
          stressed_market_intervals, kill_switch_tests, otr_breaches, compliance_status
   FROM regulatory_compliance_reports
   ORDER BY generated_at DESC LIMIT 1;
   ```

3. **Self-certification checklist:**
    - System behavior documented during 90-day demo period.
    - Behavior during "Proxy Dislocated" events captured.
    - Kill-Switch verification results logged (at least 12 weekly tests).
    - OTR breaches reviewed and explained.
    - SMC scenario results documented.

### De-Rounding and Periodicity Filter Verification (v5 — Proposal 05)

Verify data quality filters are functioning correctly.

1. **Check DeRoundingFilter activity:**
   ```sql
   SELECT time_bucket('1 hour', time) AS hour, symbol, SUM(volume) as total_volume
   FROM tick_data
   WHERE EXTRACT(MINUTE FROM time) % 5 = 0
     AND time > NOW() - INTERVAL '1 day'
   GROUP BY hour, symbol
   ORDER BY hour DESC;
   ```
   Volume at round marks should be smoothed relative to non-round-mark intervals.

2. **Check TimePeriodicityFilter activity:**
   Monitor `monitor.ingestion.periodicity.smoothed.total` gauge.
   A healthy system shows consistent low-level smoothing activity during market hours.

### Options Data Pipeline Verification (v5 — Proposal 06: GEX)

Verify options data ingestion and GEX calculation pipeline (when enabled).

1. **Check options data freshness:**
   ```sql
   SELECT time, symbol, net_gamma, gamma_flip_price, gex_dollar
   FROM market_gamma_history
   WHERE time > NOW() - INTERVAL '1 day'
   ORDER BY time DESC LIMIT 10;
   ```

2. **Verify GEX-weighted regime detection:**
   ```sql
   SELECT r.detected_at, r.regime, r.confidence, g.net_gamma, g.gamma_flip_price
   FROM regime_detection_results r
   JOIN market_gamma_history g ON DATE(r.detected_at) = DATE(g.time)
   WHERE r.metadata->>'gex_weighted' = 'true'
   ORDER BY r.detected_at DESC LIMIT 5;
   ```

---

## Validation

- [ ] All Dockerfiles build successfully.
- [ ] `docker-compose.yml` starts all services locally.
- [ ] Flyway migrations apply automatically on backend startup.
- [ ] Health checks return 200 for all services.
- [ ] OpenBB sidecar connects and fetches data.
- [ ] Analytics worker responds to health check.
- [ ] Dashboard loads and renders KPI cards.
- [ ] Landing page loads with ISR.
- [ ] Demo environment runs with virtual portfolio.
- [ ] Data retention and compression policies execute correctly.
- [ ] `chronicle_overflow` volume is separate from `timescaledb_data` volume (Finding 7).
- [ ] All runbook procedures documented and tested.
- [ ] Proxy divergence event review runbook tested (Finding 3).
- [ ] Backend runs with `virtual-threads` profile only, no dual-mode (Finding 1).
- [ ] FINOS CDM enum constraints (V7 migration) apply cleanly (v2).
- [ ] TimeBase-CE migration path documented but not implemented (v2).
- [ ] QuestDB tiered storage migration path documented but not implemented (v3).
- [ ] Shared-memory Arrow IPC migration path documented but not implemented (v3).
- [ ] V8 pre-computed KPI views migration applies cleanly (v3).

**v4 additions:**

- [ ] Bulkhead pools configured with correct sizes (`critical=4`, `high-volume=16`, `computation=8`) in Docker Compose.
- [ ] Jaeger service starts and receives traces from backend and analytics worker via OTLP gRPC.
- [ ] Distributed traces visible in Jaeger UI for end-to-end signal generation path (ingestion → ILI → signal).
- [ ] `ScheduledCalibrationTask` runs on configured interval (30-day trigger, 180-day lookback, 30-day validation).
- [ ] Calibration results persisted to `optimization_runs` and `active_weights` tables with correct status lifecycle.
- [ ] Chaos testing infrastructure (Chaos Mesh or Toxiproxy) deployable in staging environment.
- [ ] Disaster alert verification runbook returns correct results when `disaster_alerts` contain `CRITICAL` severity
  entries.

**v5 additions:**

- [ ] Big Red Button halts all trading within 50ms of activation (v5).
- [ ] Big Red Button recovery requires explicit manual acknowledgment (v5).
- [ ] System enters Global Safe Mode during simulated cascading failure (v5).
- [ ] Recovery from Global Safe Mode requires explicit manual acknowledgment (v5).
- [ ] Cross-module correlation detection triggers within 5 seconds (v5).
- [ ] Regulatory compliance report captures all required fields for self-certification (v5).
- [ ] De-rounding filter verification runbook produces correct results (v5).
- [ ] Options data pipeline verification runbook returns correct GEX data (when enabled) (v5).

### v5 Validation Additions

- [ ] FinBERT GPU memory usage stays within allocated limit during batch news processing.
- [ ] Sentiment service graceful degradation: falls back to lexicon SALI if FinBERT model fails to load.
- [ ] Dual portfolio demo maintains independent state for passive and aggressive execution paths.
- [ ] Markov stop calibration runs complete within reasonable time on standard demo hardware.

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|:--------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: removed dual-mode concurrency runbook, dedicated Chronicle Queue volume, proxy divergence event review runbook, data retention for proxy_divergence_events.                                                                                                                                                                                                                                                                                                                                                |
| v2      | Added FINOS TimeBase-CE deferral note in "External integration" section. Added "TimeBase-CE Future Migration Path" runbook (documentation only, not implemented). Describes future hot-cache architecture where TimeBase serves last 24h of ticks with TimescaleDB as long-term store.                                                                                                                                                                                                                                                |
| v3      | Added QuestDB tiered storage (Proposal #1) and shared-memory Arrow IPC (Proposal #4) as deferred proposals. Added "QuestDB Tiered Storage Path" and "Shared-Memory Arrow IPC Path" runbooks (documentation only). Both deferred with clear re-evaluation criteria.                                                                                                                                                                                                                                                                    |
| v4      | Added Bulkhead Configuration in Docker Compose with three pool sizes. Added OpenTelemetry Infrastructure: Jaeger all-in-one service with OTLP gRPC, backend and analytics worker trace export configuration. Added ScheduledCalibrationTask with 30-day interval, 180-day lookback, 30-day validation, and auto-deployment pipeline. Added four new runbooks: Bulkhead Pool Monitoring, Distributed Tracing with Jaeger, Calibration Task Monitoring, Disaster Alert Verification. Added 7 validation criteria for v4 infrastructure. |
| v5      | `11-deployment-operations.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Added five new runbooks from Proposals 05, 06, 07: Big Red Button (manual kill-switch with < 50ms latency, requires manual acknowledgment), Systemic Resilience Monitor (cross-module health monitoring, Global Safe Mode entry/recovery), Regulatory Compliance Report Generation (self-certification report generation and review), De-Rounding and Periodicity Filter Verification (data quality filter verification), Options Data Pipeline Verification (GEX pipeline verification when enabled). Added 8 validation criteria. |
| v5      | `11-deployment-operations.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Added from Proposals 08, 09: FinBERT GPU memory considerations for analytics worker, sentiment service graceful degradation to lexicon SALI, dual portfolio demo deployment note. 4 new validation criteria. |
| v5.1    | `11-deployment-operations.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Added from Proposals 10, 11, 12: XGBoost/SHAP memory allocation for analytics worker, Fed balance sheet data ingestion schedule (weekly), tournament benchmark compute resource considerations. 3 new validation criteria. |
