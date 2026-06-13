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
      - FINNHUB_API_KEY=${FINNHUB_API_KEY}
      - ALPHAVANTAGE_API_KEY=${ALPHAVANTAGE_API_KEY}
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

> **v6 Note:** OpenBB Platform sidecar removed in v6 (free data source migration). All data now
> fetched via direct REST/WebSocket clients.

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

| Environment | API Keys                                                        | Database          | Purpose                              |
|:------------|:----------------------------------------------------------------|:------------------|:-------------------------------------|
| **dev**     | Free API keys: FRED, Finnhub, Alpha Vantage. No sidecar containers. | Local TimescaleDB | Development                          |
| **demo**    | Real API keys                                                   | Cloud TimescaleDB | Verification with virtual portfolio  |
| **staging** | Real API keys                                                   | Cloud TimescaleDB | Pre-production validation            |
| **prod**    | Real API keys                                                   | HA TimescaleDB    | Production (after demo verification) |

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

| Concern               | Implementation                                          |
|:----------------------|:--------------------------------------------------------|
| API Keys              | Environment variables or HashiCorp Vault, never in YAML |
| Finnhub API Key       | `${FINNHUB_API_KEY}` in application config              |
| Alpha Vantage API Key | `${ALPHAVANTAGE_API_KEY}` in application config         |
| WebSocket Auth        | Finnhub API key in connection handshake                 |
| Frontend Auth         | OAuth2 + PKCE via Spring Security (Auth0/Keycloak)      |
| API Endpoints         | All `/api/**` require valid JWT                         |
| DB Credentials        | Environment variables, standard PostgreSQL auth         |
| HTTPS                 | Enforced via `server.ssl` or reverse proxy              |
| Analytics Worker      | Localhost only, no external access                      |

---

## Runbook

### Finnhub WebSocket Configuration + Yahoo Finance Setup

1. Configure Finnhub WebSocket: set `FINNHUB_API_KEY` in environment or `.env` file.
2. Verify WebSocket connection: check backend logs for successful Finnhub trade subscription.
3. Configure Yahoo Finance: no API key required — REST calls use public endpoints with rate limiting.
4. Verify Yahoo Finance OHLCV fetch: check backend logs for successful historical data retrieval.

### Analytics Worker Deployment

1. Build: `docker compose build analytics-worker`.
2. Start: `docker compose up -d analytics-worker`.
3. Verify: `curl http://localhost:8001/health`.

### Finnhub WebSocket Outage Procedure

1. Circuit breaker opens automatically after 5 failures.
2. Fallback to direct API equity aggregates activates after 5 min.
3. Auto-reconnect with exponential backoff (1s → 60s).
4. Monitor `FINNHUB_WS_FALLBACK` and `OVERFLOW_QUEUE_GROWING` logs.

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
2. Route real-time Finnhub ticks to TimeBase first (hot cache for last 24h).
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
2. Route real-time Finnhub ticks to QuestDB first (hot path, last 24-48h).
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
    - `monitor.ingestion.bulkhead.high-volume.utilization` (Finnhub WS)
    - `monitor.ingestion.bulkhead.computation.utilization` (ILI/Signals)
2. If critical pool > 80%: investigate FRED/NY Fed ingestion latency. Check circuit breaker
   state — repeated half-open cycles indicate upstream API degradation.
3. If high-volume pool saturated: check Finnhub WS message rate. Consider temporarily reducing
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
    - From Finnhub tick receipt to signal log write.
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
- [ ] Finnhub WebSocket connects and receives trades. Yahoo Finance REST fetches OHLCV.
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

**v6 additions:**

- [ ] `FINNHUB_API_KEY` and `ALPHAVANTAGE_API_KEY` configured in backend environment.
- [ ] Finnhub WebSocket connects and receives real-time trades without OpenBB sidecar.
- [ ] Yahoo Finance REST fetches OHLCV historical data without sidecar dependency.
- [ ] No OpenBB sidecar container present in any Docker Compose configuration.

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
| v6      | `11-deployment-operations.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Migrated from Polygon/OpenBB to free data sources. Replaced `POLYGON_API_KEY` with `FINNHUB_API_KEY` and added `ALPHAVANTAGE_API_KEY` in Docker Compose. Removed `docker-compose.openbb.yml` section; replaced with note about direct REST/WebSocket clients. Updated Environment Strategy table to reference free API keys (FRED, Finnhub, Alpha Vantage). Replaced Polygon API Key and OpenBB Sidecar rows in Security Configuration with Finnhub API Key and Alpha Vantage API Key. Replaced "OpenBB Sidecar Setup" runbook with "Finnhub WebSocket Configuration + Yahoo Finance Setup". Replaced "Polygon WebSocket Outage Procedure" with "Finnhub WebSocket Outage Procedure". Updated all internal references from Polygon to Finnhub (bulkhead monitoring, distributed tracing, deferred migration paths). Added 4 v6 validation criteria. |

---

## Appendix: 05-docker-infra.md

> *Merged from `gaps/05-docker-infra.md` / `done/05-docker-infra.md` during plan_v6 consolidation.*

# Docker & Infrastructure — 3 Missing Items

**Plan ref:** `11-deployment-operations.md`

| #   | Item                                       | Description                                                                                                              |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| 1   | `docker-compose.openbb.yml`                | OpenBB Platform sidecar for equity prices (Removed in v6: replaced by direct free API clients — no sidecar container needed) |
| 2   | Chronicle Queue volume in docker-compose   | Dedicated NVMe/tmpfs volume for ingestion overflow                                                                       |
| 3   | **Spring Security / OAuth2 config** (Java) | Plan requires OAuth2+PKCE, all `/api/**` require JWT, HTTPS, CSP headers — no Spring Security dependency or config found |

> **Note:** Jaeger **is** present in `docker-compose.yml`. The landing page Dockerfile **does** use nginx (matching the plan). Backend Dockerfile **does** use multi-stage build.

---

## Appendix: 07-runbooks.md

> *Merged from `gaps/07-runbooks.md` / `done/07-runbooks.md` during plan_v6 consolidation.*

# Runbooks — 17 Missing

**Plan ref:** `11-deployment-operations.md` (21 planned, 4 exist)

## Existing Runbooks (`docs/runbooks/`)

- `deployment.md`
- `monitoring-troubleshooting.md`
- `data-management.md`
- `README.md`

## Missing Runbooks

| #   | Runbook                                                                  |
| --- | ------------------------------------------------------------------------ |
| 1   | Finnhub WebSocket Configuration + Yahoo Finance Setup                    |
| 2   | Analytics Worker Deployment                                              |
| 3   | Finnhub WebSocket Outage Procedure                                       |
| 4   | Chronicle Queue Overflow Recovery (supplemented by DataHub CSV Backfill Recovery in v6) |
| 5   | ILI Weight Recalibration                                                 |
| 6   | Proxy Divergence Event Review                                            |
| 7   | TimescaleDB Continuous Aggregate Refresh                                 |
| 8   | TA-Lib Adapter Integration                                               |
| 9   | Bulkhead Pool Monitoring                                                 |
| 10  | Distributed Tracing with Jaeger                                          |
| 11  | Calibration Task Monitoring                                              |
| 12  | Disaster Alert Verification                                              |
| 13  | Big Red Button Runbook                                                   |
| 14  | Systemic Resilience Monitor Runbook                                      |
| 15  | Regulatory Compliance Report Generation                                  |
| 16  | De-Rounding Filter Verification                                          |
| 17  | Options Data Pipeline Verification                                       |

---

## Appendix: RUNBOOK_QUANTITATIVE_ENGINE.md

> *Merged from `gaps/RUNBOOK_QUANTITATIVE_ENGINE.md` / `done/RUNBOOK_QUANTITATIVE_ENGINE.md` during plan_v6 consolidation.*

# Runbook: Exhaustive Quantitative Engine Engineering Specification

**Purpose:** This is the definitive technical specification and implementation manual for the Tickonomics Quantitative
Engine. It integrates the "151 Trading Strategies" library (Kakushadze & Serur) with advanced statistical guardrails (
Rahaman, Vansteenberghe, Gideonsson, Coupette). This unified roadmap ensures that high-fidelity strategy execution is
protected by rigorous statistical inference, forensic auditability, adaptive macro-flexibility, and institutional-grade
risk management.

**Document Status:** v5.3.0-INSTITUTIONAL-SPEC
**Core Mandate:** Zero-Loss Implementation of Mathematical Research into Production-Grade Code.

---

## Table of Contents

1. [Theoretical Pillars & Primary Research References](#1-theoretical-pillars--primary-research-references)
2. [Taxonomy & Complexity Tiers](#2-taxonomy--complexity-tiers)
3. [Track-by-Track Implementation Map](#3-track-by-track-implementation-map)
4. [Phase 1: Heavy Math & Infrastructure (Python Sidecar)](#4-phase-1-heavy-math--infrastructure)
5. [Phase 2: CDM Enrichment & Forensic Audit (Java/Ingestion)](#5-phase-2-cdm-enrichment--forensic-audit)
6. [Phase 3: Options Strategy Porting (LegMatch & Alignment)](#6-phase-3-options-strategy-porting)
7. [Phase 4: Equity Alpha & Universe Aggregation](#7-phase-4-equity-alpha--universe-aggregation)
8. [Phase 5: Fixed Income & Macro Impulse Responses](#8-phase-5-fixed-income--macro-impulse-responses)
9. [Phase 6: Conditional Inference & Quantile Bands](#9-phase-6-conditional-inference--quantile-bands)
10. [Phase 7: Data Quality & High-Frequency Microstructure](#10-phase-7-data-quality--high-frequency-microstructure)
11. [Phase 8: Backtesting Alignment & Statistical Rigor](#11-phase-8-backtesting-alignment--statistical-rigor)
12. [Phase 9: Demo, Verification & AUMF Integration](#12-phase-9-demo-verification--aumf-integration)
13. [Phase 10: Institutional Operations & Incident Response](#13-phase-10-institutional-operations--incident-response)
14. [Cross-Cutting Engineering Concerns](#14-cross-cutting-engineering-concerns)
15. [Dependency & Sprint Sequence (20-Sprint Roadmap)](#15-dependency--sprint-sequence)
16. [Testing Strategy (Given-When-Then)](#16-testing-strategy)
17. [Risk Register & Mitigations](#17-risk-register--mitigations)
18. [Appendix A: Source Document Index](#appendix-a-source-document-index)
19. [Appendix B: Comprehensive Strategy Catalog](#appendix-b-comprehensive-strategy-catalog)

---

<a name="1-theoretical-pillars--primary-research-references"></a>

## 1. Theoretical Pillars & Primary Research References

This engine is built upon the synthesis of five foundational academic research papers, providing the theoretical
blueprints for every line of code:

1. **`pdf-ssrn/ssrn-3247865.pdf`**: "151 Trading Strategies" (Kakushadze & Serur, 2018). Provides 550+ closed-form
   formulas across options, equity, fixed income, and commodities.
2. **`pdf-ssrn/ssrn-6661758.pdf`**: "Statistical Methods in Quantitative Finance" (Rahaman, 2026). Authority on multiple
   testing correction (BH-FDR) and Extreme Value Theory (EVT) for liquidity shocks.
3. **`pdf-ssrn/ssrn-5178205.pdf`**: "Quantitative methods in finance" (Vansteenberghe, 2026). Graduate-level lecture
   notes providing the framework for Quantile Regression (QR) and formal Christian-Christoffersen VaR validation.
4. **`pdf-ssrn/ssrn-6553778.pdf`**: "Quantitative Trading Algorithm" (Gideonsson, 2025). Methodology for Time-Varying
   Parameter Structural VAR (TVP-SVAR) and Transfer Entropy synergy.
5. **`pdf-ssrn/ssrn-3377384.pdf`**: "Quantitative Rechtswissenschaft" (Coupette & Fleckner, 2026). Standardizes "
   Intersubjective Reproducibility" (IR) and Descriptive vs. Inferential partitioning.

---

<a name="2-taxonomy--complexity-tiers"></a>

## 2. Taxonomy & Complexity Tiers

### 2.1 Strategy & Method Categories

| Category                   | Primary Reference        | Objective                                   | Implementation Track  |
|:---------------------------|:-------------------------|:--------------------------------------------|:----------------------|
| **Options Spreads**        | Kakushadze Sec 2         | 30+ strategies (Vertical, Diagonal, Credit) | Track 5 (Computation) |
| **Options Combinations**   | Kakushadze Sec 2         | Butterflies, Condors, Iron Spreads          | Track 5 (Computation) |
| **Equity Momentum**        | Kakushadze Sec 3.1       | Cross-sectional return ranking models       | Track 5 (Computation) |
| **Mean-Reversion**         | Kakushadze Sec 3.9       | Universe de-meaning (Eq. 293)               | Track 5 (Computation) |
| **Fixed Income**           | Kakushadze Sec 5         | Bullet/Barbell/Butterfly Portfolios         | Track 3 & 5           |
| **Risk Modeling**          | Rahaman / Vansteenberghe | EVT-based "Crash Thresholds"                | Track 3 (Analytics)   |
| **Statistical Rigor**      | Rahaman 2026             | Benjamini-Hochberg (BH-FDR) P-Correction    | Track 3 & 8           |
| **Flexible Sensitivities** | Gideonsson 2025          | TVP-SVAR (Macro Impulse Response)           | Track 3 (Analytics)   |
| **Data Synergy**           | Gideonsson 2025          | Transfer Entropy (Synergy Quantification)   | Track 3 (Analytics)   |
| **Forensic Audit**         | Coupette 2026            | IR Audit Logs & Coding Rules Registry       | Track 2, 4, 7         |

### 2.2 Computational Complexity Tiers (Latency Mandates)

Performance is strictly monitored using Tiered Latency Budgets.

| Tier       | Description     | Latency | Technology Stack     | Example Component                 |
|:-----------|:----------------|:--------|:---------------------|:----------------------------------|
| **Tier A** | Pure Arithmetic | < 1ms   | Java / TA-Lib        | SMA, EMA, ROC, B/P ratio          |
| **Tier B** | Multi-Leg Match | < 10ms  | Java / ConcurrentMap | Bull Call Spread strike alignment |
| **Tier C** | Universe Agg    | < 100ms | Java / DirectBuffer  | Eq 293 (Universe Means)           |
| **Tier D** | Local Opt       | < 500ms | Python / Arrow IPC   | Nelson-Siegel curve fitting       |
| **Tier E** | Heavy Inference | < 2s    | Python / Statsmodels | TVP-SVAR IRF simulation           |

---

<a name="3-track-by-track-implementation-map"></a>

## 3. Track-by-Track Implementation Map

### 3.1 Track 1: Scaffolding & API Contracts

**API Mandate:** All quantitative engine endpoints are isolated under the `/api/v1/quant/` namespace.

```yaml
components:
  schemas:
    AlphaSignal:
      type: object
      required: [timestamp, strategyId, symbol, direction]
      properties:
        timestamp: { type: string, format: date-time }
        strategyId: { type: string, format: uuid }
        symbol: { type: string }
        direction: { type: string, enum: [LONG, SHORT, NEUTRAL] }
        strength: { type: number, format: double, minimum: 0, maximum: 1 }
        confidence: { type: number, format: double, minimum: 0, maximum: 1 }
        expected_move: { type: number, format: double }
        metrics: { type: object, additionalProperties: { type: number } }

    EvtMetric:
      type: object
      properties:
        shape_xi: { type: number, format: double }
        scale_beta: { type: number, format: double }
        threshold_u: { type: number, format: double }
        tail_var_999: { type: number, format: double }
        exceedance_count: { type: integer }

    IntersubjectivePath:
      type: object
      properties:
        data_point_id: { type: string, format: uuid }
        coding_rules: { type: array, items: { type: object } }
        ir_score: { type: number, format: double }

    IrfVector:
      type: object
      properties:
        horizon: { type: array, items: { type: integer } }
        response_path: { type: array, items: { type: number } }
        confidence_high: { type: array, items: { type: number } }
        confidence_low: { type: array, items: { type: number } }

paths:
  /api/v1/quant/signals/active:
    get:
      summary: Stream of active signals from 151 strategies.
      parameters:
        - name: category
          in: query
          schema: { type: string, enum: [OPTIONS, EQUITY, MACRO, FX] }
      responses:
        200:
          description: List of AlphaSignal objects.
          content:
            application/json:
              schema: { type: array, items: { $ref: '#/components/schemas/AlphaSignal' } }

  /api/v1/quant/strategies/active:
    get:
      summary: List all active strategies and their current status.
      responses:
        200:
          description: List of active strategies with formula references.
          content:
            application/json:
              schema: { type: array, items: { $ref: '#/components/schemas/StrategyStatus' } }

  /api/v1/quant/strategies/options/butterfly:
    post:
      summary: Calculate butterfly spread signal (Eq. 79-100).
      parameters:
        - name: underlying
          in: query
          required: true
          schema: { type: string }
      responses:
        200:
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AlphaSignal' }

  /api/v1/quant/risk/tail-parameters:
    get:
      summary: Generalized Pareto Distribution (GPD) parameters for tail-risk assessment.
      responses:
        200:
          description: Shape and scale parameters with Tail-VaR.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/EvtMetric' }

  /api/v1/quant/risk/evt-tail:
    get:
      summary: Retrieve GPD parameters for tail-risk.
      responses:
        200:
          content:
            application/json:
              schema: { $ref: '#/components/schemas/EvtMetric' }

  /api/v1/quant/audit/intersubjective-reproducibility/{id}:
    get:
      summary: Forensic reconstruction path (Coding Rules) for a specific data point.
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        200:
          description: Trace of data transformations and IR score.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/IntersubjectivePath' }

  /api/v1/quant/macro/shock-response:
    get:
      summary: Structural VAR impulse response for funding liquidity shocks.
      responses:
        200:
          description: Response vector showing sensitivity decay.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/IrfVector' }
```

### 3.2 Track 2: Database Schema

**SQL Migration Definitions (V19-V28):**

```sql
-- V19: Strategy Definition Registry
CREATE TABLE strategy_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    category        TEXT NOT NULL,
    section_ref     TEXT,
    formula_refs    TEXT[],
    priority        INT NOT NULL DEFAULT 2,
    complexity_tier TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    expected_hit_rate DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- V20: Alpha Signal Hypertable
CREATE TABLE alpha_signals (
    time            TIMESTAMPTZ NOT NULL,
    strategy_id     UUID NOT NULL REFERENCES strategy_definitions(id),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,
    strength        DOUBLE PRECISION CHECK (strength BETWEEN 0 AND 1),
    confidence      DOUBLE PRECISION CHECK (confidence BETWEEN 0 AND 1),
    expected_move   DOUBLE PRECISION,
    metadata        JSONB,
    PRIMARY KEY (time, strategy_id, symbol)
);
SELECT create_hypertable('alpha_signals', 'time', chunk_time_interval => INTERVAL '7 days');

-- V21: Intersubjective Audit Log (Coupette Transparency Mandate)
CREATE TABLE intersubjective_audit_log (
    time            TIMESTAMPTZ NOT NULL,
    data_point_id   UUID NOT NULL,
    coding_rule     TEXT NOT NULL,
    rule_version    TEXT NOT NULL,
    input_hash      TEXT NOT NULL,
    output_value    DOUBLE PRECISION,
    ir_score        DOUBLE PRECISION DEFAULT 1.0,
    metadata        JSONB
);
SELECT create_hypertable('intersubjective_audit_log', 'time', chunk_time_interval => INTERVAL '1 day');

-- V22: Option Greeks Cache
CREATE TABLE option_chain_snapshots (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    strike          DOUBLE PRECISION NOT NULL,
    expiry          DATE NOT NULL,
    option_type     TEXT NOT NULL,
    bid             DOUBLE PRECISION,
    ask             DOUBLE PRECISION,
    last_price      DOUBLE PRECISION,
    delta           DOUBLE PRECISION,
    gamma           DOUBLE PRECISION,
    theta           DOUBLE PRECISION,
    vega            DOUBLE PRECISION,
    rho             DOUBLE PRECISION,
    implied_vol     DOUBLE PRECISION,
    ttm_years       DOUBLE PRECISION,
    underlying_price DOUBLE PRECISION,
    PRIMARY KEY (time, symbol, strike, expiry, option_type)
);
SELECT create_hypertable('option_chain_snapshots', 'time', chunk_time_interval => INTERVAL '1 day');

-- V23: Macro Shock Impulse Response History
CREATE TABLE macro_shock_irfs (
    time            TIMESTAMPTZ NOT NULL,
    shock_source    TEXT NOT NULL,
    target_kpi      TEXT NOT NULL,
    horizon_days    INT NOT NULL,
    response_path   DOUBLE PRECISION[],
    confidence_high DOUBLE PRECISION[],
    confidence_low  DOUBLE PRECISION[],
    magnitude_std   DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, shock_source, target_kpi)
);
SELECT create_hypertable('macro_shock_irfs', 'time', chunk_time_interval => INTERVAL '30 days');

-- V24: GPD Risk Parameters History
CREATE TABLE risk_evt_parameters (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    shape_xi        DOUBLE PRECISION NOT NULL,
    scale_beta      DOUBLE PRECISION NOT NULL,
    threshold_u     DOUBLE PRECISION NOT NULL,
    tail_var_99     DOUBLE PRECISION NOT NULL,
    tail_var_999    DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, symbol)
);
SELECT create_hypertable('risk_evt_parameters', 'time', chunk_time_interval => INTERVAL '30 days');

-- V25: Quantile Regression Coefficients
CREATE TABLE quantile_coefficients (
    time            TIMESTAMPTZ NOT NULL,
    kpi_name        TEXT NOT NULL,
    quantile        DOUBLE PRECISION NOT NULL,
    coefficients    DOUBLE PRECISION[],
    pseudo_r2       DOUBLE PRECISION,
    PRIMARY KEY (time, kpi_name, quantile)
);
SELECT create_hypertable('quantile_coefficients', 'time', chunk_time_interval => INTERVAL '30 days');

-- V26: Transfer Entropy Synergy Matrix
CREATE TABLE synergy_entropy_matrix (
    time            TIMESTAMPTZ NOT NULL,
    source_a        TEXT NOT NULL,
    source_b        TEXT NOT NULL,
    entropy_bits    DOUBLE PRECISION NOT NULL,
    p_value         DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (time, source_a, source_b)
);
SELECT create_hypertable('synergy_entropy_matrix', 'time', chunk_time_interval => INTERVAL '30 days');

-- V27: Universe Statistics Cache (Eq. 293 input)
CREATE TABLE universe_stats_history (
    time            TIMESTAMPTZ PRIMARY KEY,
    universe_mean   DOUBLE PRECISION NOT NULL,
    universe_std    DOUBLE PRECISION NOT NULL,
    symbol_count    INT NOT NULL
);
SELECT create_hypertable('universe_stats_history', 'time', chunk_time_interval => INTERVAL '1 day');

-- Performance Indexes
CREATE INDEX idx_alpha_signals_strategy ON alpha_signals (strategy_id, time DESC);
CREATE INDEX idx_option_chain_symbol_expiry ON option_chain_snapshots (symbol, expiry, strike);
CREATE INDEX idx_audit_log_data_point ON intersubjective_audit_log (data_point_id);
```

---

<a name="4-phase-1-heavy-math--infrastructure"></a>

## 4. Phase 1: Heavy Math & Infrastructure (Python Sidecar)

**Target:** `analytics/` (Python FastAPI Worker)

### 4.1 Extreme Value Theory (EVT) Service Implementation

We implement the Peak-Over-Threshold (POT) method from Rahaman (2026). This captures tail exceedances and fits them to a
Generalized Pareto Distribution (GPD).

**Critical Service: `EvtRiskService.py`**

```python
import numpy as np
from scipy.stats import genpareto
from typing import Dict, Any, List
import logging
from pydantic import BaseModel

class EvtFitRequest(BaseModel):
    data: List[float]
    quantile_u: float = 0.95

class EvtRiskService:
    """
    Mandate: Model non-Gaussian tail behavior in liquidity shocks (SOFR spikes).
    Algorithm: Generalized Pareto Distribution (GPD) fitting via Maximum Likelihood.
    Reference: Rahaman (2026), Section 4.2.
    """
    def __init__(self):
        self.logger = logging.getLogger(__name__)

    def fit_tail_distribution(self, req: EvtFitRequest) -> Dict[str, Any]:
        """
        Fits exceedances y = x - u to GPD.
        G(x; xi, beta) = 1 - (1 + xi*x/beta)^(-1/xi)
        """
        series = np.array(req.data)
        if len(series) < 100:
            return {"status": "ERROR", "message": "Sample size too small for EVT"}

        u = np.quantile(series, req.quantile_u)
        exceedances = series[series > u] - u

        if len(exceedances) < 10:
            return {"status": "ERROR", "message": "Insufficient tail observations"}

        try:
            xi, _, beta = genpareto.fit(exceedances)
        except Exception as e:
            self.logger.error(f"GPD fit failed: {e}")
            return {"status": "FIT_FAILURE", "error": str(e)}

        n = len(series)
        nu = len(exceedances)
        p = 0.999
        tail_var = u + (beta / xi) * (((n / nu) * (1 - p)) ** (-xi) - 1)

        return {
            "status": "SUCCESS",
            "shape_xi": float(xi),
            "scale_beta": float(beta),
            "threshold_u": float(u),
            "tail_var_999": float(tail_var),
            "exceedance_count": int(nu),
            "fit_quality": float(1.0 - (1.0 / len(exceedances)))
        }

    def simulate_tail_paths(self, params: Dict[str, float], n_paths: int = 1000):
        """Generates synthetic tail scenarios for stress testing options portfolios."""
        return np.random.genpareto(params['shape_xi'], (n_paths,)) * params['scale_beta']
```

### 4.2 Multiple Testing Correction (BH-FDR) Implementation

Prevents the selection of false strategies during massive backtest parameter scanning.

**Critical Service: `MultipleTestingCorrectionService.py`**

```python
from statsmodels.stats.multitest import multipletests
import numpy as np
from pydantic import BaseModel
from typing import List

class FdrCorrectionRequest(BaseModel):
    p_values: List[float]
    alpha: float = 0.05

class FdrCorrectionResponse(BaseModel):
    actionable_mask: List[bool]
    adjusted_p_values: List[float]
    rejected_count: int
    discovery_reduction: float

def apply_fdr_correction(req: FdrCorrectionRequest) -> FdrCorrectionResponse:
    """
    Logic: P(i) <= (i/m) * Q
    m: total tests, i: rank, Q: false discovery rate target.
    Reference: Rahaman (2026), Section 2.1.
    """
    if not req.p_values:
        return FdrCorrectionResponse(
            actionable_mask=[], adjusted_p_values=[],
            rejected_count=0, discovery_reduction=0.0
        )

    p_array = np.array(req.p_values)
    m = len(req.p_values)

    reject, adjusted_p, _, _ = multipletests(
        p_array,
        alpha=req.alpha,
        method='fdr_bh'
    )

    return FdrCorrectionResponse(
        actionable_mask=reject.tolist(),
        adjusted_p_values=adjusted_p.tolist(),
        rejected_count=int(np.sum(reject)),
        discovery_reduction=float(1.0 - np.sum(reject) / m)
    )
```

---

<a name="5-phase-2-cdm-enrichment--forensic-audit"></a>

## 5. Phase 2: CDM Enrichment & Forensic Audit (Java/Ingestion)

**Target:** `cdm/` and `ingestion/` (Java)

### 5.1 CDM Model Extensions (Institutional Scale)

All instrument snapshots must carry strategy-ready metadata to ensure zero signal drift between Java and Python tracks.

**Java Record: `CdmOptionSnapshot.java`**

```java
public record CdmOptionSnapshot(
    UUID id,
    String underlyingSymbol,
    BigDecimal strike,
    LocalDate expiryDate,
    OptionType type,
    double delta,
    double gamma,
    double theta,
    double vega,
    double rho,
    double impliedVol,
    double ttmYears,
    double bid,
    double ask,
    long openInterest,
    Instant observationTime
) {
    /**
     * PRECISION MANDATE (Institutional Rule):
     * - Equity Options MUST use ACT/365 day-count convention.
     * - IR Options MUST use ACT/360.
     * - US Treasury Bonds MUST use ACT/ACT.
     * Mismatch between track conventions will result in signal drift and P&L leakage.
     */
}
```

### 5.2 Intersubjective Reproducibility (IR) Registry (Track 4)

We implement the Coupette & Fleckner (2026) "Coding Rules" registry. This is an append-only log of every transformation
applied to raw data, stored with the hash of the input to ensure forensic integrity.

**Critical Service Logic: `IntersubjectiveAuditService.java`**

```java
@Service
public class IntersubjectiveAuditService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Mandate: Every AlphaSignal MUST be traceable back to raw provider bytes.
     * Coding Rules Registry (Forensic Audit):
     * - Rule 01 (RAW_FETCH): source_uri, raw_payload_hash.
     * - Rule 02 (ADAPTER_MAP): mapper_class, logic_version.
     * - Rule 03 (GAP_FILL): method (LOCF/Linear), fill_distance.
     * - Rule 04 (Normalization): SMA/STDDEV windows, effective weights.
     *
     * IR_SCORE calculation:
     * - 1.0: Full raw data, no gaps.
     * - 0.8: LOCF used for missing point.
     * - 0.5: Linear interpolation over > 3 points.
     */
    public void logTransformation(UUID dataPointId, CodingRule rule,
                                   String inputHash, double outputValue) {
        String sql = """
            INSERT INTO intersubjective_audit_log
            (time, data_point_id, coding_rule, rule_version, input_hash, output_value, ir_score)
            VALUES (NOW(), ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(sql,
            dataPointId,
            rule.getRuleName(),
            rule.getVersion(),
            inputHash,
            outputValue,
            rule.calculateConfidence()
        );
    }

    public List<AuditEntry> reconstructPath(UUID signalId) {
        return jdbcTemplate.query(
            "WITH RECURSIVE path AS (...) SELECT * FROM path", ...
        );
    }
}
```

---

<a name="6-phase-3-options-strategy-porting"></a>

## 6. Phase 3: Options Strategy Porting (LegMatch & Alignment)

**Location:** `computation/` (Java)

### 6.1 LegMatch Service (Tier B Complexity)

Multi-leg options strategies (Butterfly, Condor, Spreads) require the simultaneous presence of all legs. We use a
high-performance concurrent matcher over strike-sorted chains.

**Critical Service Logic: `LegMatchService.java`**

```java
public class LegMatchService {
    /**
     * Matching Logic for Butterfly (Eq 79-100):
     * 1. Same Underlying, Same Expiry.
     * 2. Three Strikes (K1 < K2 < K3).
     * 3. MIDPOINT CONSTRAINT: K2 = (K1 + K3) / 2 (Symmetric Wings).
     * 4. RATIO CONSTRAINT: +1 K1, -2 K2, +1 K3.
     * 5. TIMEOUT: 500ms for full-leg cluster arrival before discarding.
     */
    public List<LegGroup> findButterflySpreads(String underlying,
                                                List<CdmOptionSnapshot> chain) {
        Map<LocalDate, List<CdmOptionSnapshot>> byExpiry = chain.stream()
            .collect(groupingBy(CdmOptionSnapshot::expiryDate));

        List<LegGroup> clusters = new ArrayList<>();

        for (var entry : byExpiry.entrySet()) {
            List<CdmOptionSnapshot> sorted = entry.getValue().stream()
                .sorted(comparing(CdmOptionSnapshot::strike))
                .toList();

            for (int i = 0; i < sorted.size() - 2; i++) {
                for (int j = i + 2; j < sorted.size(); j++) {
                    double targetK2 = (sorted.get(i).strike().doubleValue()
                        + sorted.get(j).strike().doubleValue()) / 2.0;

                    int mid = findStrikeInRange(sorted, i + 1, j - 1, targetK2);
                    if (mid != -1) {
                        clusters.add(new LegGroup(
                            List.of(sorted.get(i), sorted.get(mid), sorted.get(j)),
                            StrategyType.BUTTERFLY
                        ));
                    }
                }
            }
        }
        return clusters;
    }
}
```

### 6.2 Option Strategy Interface Framework

**Component: `BaseOptionStrategy.java`**

```java
public abstract class BaseOptionStrategy implements BaseStrategy<LegGroup> {

    @Override
    public final AlphaSignal compute(LegGroup legs, StrategyContext ctx) {
        if (ctx.getIrScore() < 0.9) {
            return AlphaSignal.neutral(this.strategyId(), legs.getUnderlying());
        }

        double signalStrength = calculateFormula(legs);

        Map<String, Double> metrics = Map.of(
            "net_delta", calculateNetDelta(legs),
            "net_theta", calculateNetTheta(legs),
            "spread_efficiency", calculateSpreadEfficiency(legs)
        );

        return new AlphaSignal(
            this.strategyId(),
            legs.getUnderlying(),
            mapDirection(signalStrength),
            Math.abs(signalStrength),
            ctx.getConfidence(),
            Instant.now(),
            metrics
        );
    }

    protected abstract double calculateFormula(LegGroup legs);

    protected double calculateNetDelta(LegGroup legs) {
        return legs.getComponents().stream()
            .mapToDouble(CdmOptionSnapshot::delta).sum();
    }
}
```

---

<a name="7-phase-4-equity-alpha--universe-aggregation"></a>

## 7. Phase 4: Equity Alpha & Universe Aggregation

**Location:** `computation/` (Java)

### 7.1 UniverseAggregator (Tier C Complexity)

Cross-sectional strategies (momentum ranking, cluster mean-reversion) require the mean of the entire universe once per
tick cycle. Computing this independently in 151 threads is prohibited.

**Critical Service Logic: `UniverseAggregator.java`**

```java
/**
 * INSTITUTIONAL MANDATE: Cross-Strategy Determinism.
 * All equity alpha strategies requiring de-meaning (Eq. 293)
 * MUST pull from this shared context to prevent race conditions.
 */
@Component
public class UniverseAggregator {
    private final OffHeapUniverseBuffer cache;
    private final AtomicReference<StrategyContext> currentCycleContext = new AtomicReference<>();

    public void processTickCycle(Map<String, Double> symbolReturns) {
        double r_bar = symbolReturns.values().stream()
            .mapToDouble(d -> d).average().orElse(0.0);

        double variance = symbolReturns.values().stream()
            .mapToDouble(r -> Math.pow(r - r_bar, 2))
            .average().orElse(0.0);
        double sigma_r = Math.sqrt(variance);

        this.currentCycleContext.set(new StrategyContext(
            Instant.now(),
            r_bar,
            sigma_r,
            calculateSectorDrift(symbolReturns)
        ));

        persistence.saveUniverseStats(currentCycleContext.get());
    }

    public StrategyContext getContext() {
        return currentCycleContext.get();
    }
}
```

### 7.2 Strategy Deep-Dive: Momentum Decile Ranking (Eq. 266)

* **Formula:** $S_i = \text{Rank}(R_{i,(T-252, T)})$
* **Production Constraint:** Sorted once per cycle by `UniverseAggregator` and broadcast to prevent $O(N^2)$ sorting
  overhead.
* **Outcome:** LONG if symbol in Top Decile, SHORT if in Bottom Decile.

### 7.3 Strategy Deep-Dive: Cluster Mean-Reversion (Eq. 293)

* **Formula:** $r_{i,adj} = r_{i} - \frac{1}{N} \sum_{j=1}^{N} r_{j}$
* **Mandate:** "All equity alpha strategies requiring cross-sectional de-meaning MUST use the output of the
  `UniverseAggregator` service. Independent mean calculation is strictly prohibited."
* **Threshold:** ACTIONABLE if $r_{i,adj} < -2.0 \cdot \sigma_{universe}$.

---

<a name="8-phase-5-fixed-income--macro-impulse-responses"></a>

## 8. Phase 5: Fixed Income & Macro Impulse Responses

**Location:** `computation/` (Java) and `analytics/` (Python)

### 8.1 Yield Curve Nelson-Siegel Interpolation (Track 3)

We use the Nelson-Siegel model from Vansteenberghe (2026) to bridge the gap between discrete Treasury data and
continuous strategy requirements.

**Critical Service Logic: `InterpolationService.py`**

```python
from scipy.optimize import curve_fit
import numpy as np

def nelson_siegel(t, b0, b1, b2, tau):
    exp_term = np.exp(-t / tau)
    term1 = (1 - exp_term) / (t / tau)
    term2 = term1 - exp_term
    return b0 + b1 * term1 + b2 * term2

def fit_yield_curve(maturities: np.ndarray, yields: np.ndarray) -> Dict:
    """
    Fits discrete Treasury yields (3M, 2Y, 5Y, 10Y, 30Y) to a continuous function.
    Mandate: Fixed Income strategies MUST NOT use linear interpolation.
    """
    p0 = [yields[-1], yields[0] - yields[-1], 0.02, 1.5]

    try:
        popt, pcov = curve_fit(nelson_siegel, maturities, yields, p0=p0)
        return {
            "beta_0": float(popt[0]),
            "beta_1": float(popt[1]),
            "beta_2": float(popt[2]),
            "tau": float(popt[3]),
            "covariance_trace": float(np.trace(pcov))
        }
    except Exception as e:
        return {"status": "FAIL", "error": str(e)}
```

### 8.2 TVP-SVAR Macro Shocks (Gideonsson, 2025)

Adapting strategy responsiveness as the macro environment shifts.

**Critical Service: `MacroShockMonitor.py`**

```python
from statsmodels.tsa.statespace.varmax import VARMAX

def compute_impulse_response(data_frame, steps=5):
    """
    Input: DataFrame with [ILI_Index, SPY_Beta]
    Model: Structural VAR with Time-Varying Parameters.
    """
    model = VARMAX(data_frame, order=(1, 0), trend='c')
    res = model.fit(maxiter=1000, disp=False)

    irf = res.impulse_responses(steps=steps, impulse=0)

    return {
        "horizon": list(range(steps)),
        "beta_response_path": irf['SPY_Beta'].tolist(),
        "shock_std_dev": 1.0
    }
```

---

<a name="9-phase-6-conditional-inference--quantile-bands"></a>

## 9. Phase 6: Conditional Inference & Quantile Bands

**Location:** `computation/` (Java) and `analytics/` (Python)

### 9.1 Quantile Regression (QR) Bands

QR-bands (Vansteenberghe, 2026) are robust to the non-constant variance (heteroscedasticity) seen in financial markets,
where standard z-scores fail by expanding too slowly during regime shifts.

**Critical Service Logic: `QuantileRegEngine.py`**

```python
from statsmodels.regression.quantile_regression import QuantReg

def calculate_ili_bands(x_features, y_target):
    """
    Objective: Find the 95th and 5th quantile planes.
    Formula: Q_y(tau | X) = X * beta_tau
    Reference: Vansteenberghe (2026).
    """
    model = QuantReg(y_target, x_features)

    res_95 = model.fit(q=0.95)
    res_05 = model.fit(q=0.05)

    return {
        "beta_upper": res_95.params.tolist(),
        "beta_lower": res_05.params.tolist(),
        "quantile_goodness": float(res_95.prsquared)
    }
```

**Integration Pattern:**

* `SignalGenerator` fetches QR coefficients from the sidecar.
* Today's band = `Current_Features * beta_QR`.
* Signal is `ACTIONABLE` only if the point is outside the QR band, not just a static z-score threshold.

---

<a name="10-phase-7-data-quality--high-frequency-microstructure"></a>

## 10. Phase 7: Data Quality & High-Frequency Microstructure

**Location:** `ingestion/` (Java)

### 10.1 Realized Variance Kernels (Rahaman, 2026)

**Critical Service Logic: `KernelAggregator.java`**

* **Objective:** Eliminate "Microstructure Noise" and the "Epps Effect" (correlation decay at high frequencies).
* **Mechanism:**
    1. Collect raw ticks $P_i$ for the 1-minute bucket.
    2. Calculate log returns $r_i = \log(P_i / P_{i-1})$.
    3. Compute autocovariances $\gamma_j = \frac{1}{n} \sum (r_k \cdot r_{k-j})$ for lags $j \in [1, H]$.
    4. Apply kernel weights to lag coefficients. Supported kernels:
        * **Tukey-Hanning:** $k(x) = \frac{1}{2}(1 + \cos(\pi x))$
        * **Parzen:** $k(x) = 1 - 6x^2 + 6|x|^3$ for $0 \le |x| \le \frac{1}{2}$
    5. $RV_{kernel} = \gamma_0 + 2 \sum_{j=1}^{H} k(j/(H+1)) \gamma_j$.
* **Benefit:** Provides robust volatility input for GARCH and EVT modules, and higher-fidelity IV for options
  combinations.

---

<a name="11-phase-8-backtesting-alignment--statistical-rigor"></a>

## 11. Phase 8: Backtesting Alignment & Statistical Rigor

**Location:** `computation/` (Track 8)

### 11.1 Delay-d Execution Policy (Kakushadze Appendix A)

We enforce a dedicated `DelayDExecutor` to prevent data leakage (look-ahead bias) in backtests.

* **Delay-0 (Aggressive):** Signal at $T$, execute at $T+0$ close. (Discovery estimate only).
* **Delay-1 (Conservative):** Signal at $T$, execute at $T+1$ open. (**Mandatory Production Standard**).
* **Validation Check:** If Delay-0 Sharpe is high but Delay-1 Sharpe is negative, the strategy is discarded as "
  Liquidity Sensitive" or "Bias Dependent."

### 11.2 Volume-Scaled Slippage (Eq. 553)

Institutional cost modeling replaces flat 5bps assumptions. Cost is proportional to volatility and inversely
proportional to dollar volume.

**Algorithm: `Eq553SlippageModel.java`**

* **Formula:** $\text{Cost (bps)} = \zeta \cdot \frac{\sigma}{V_{ADDV}} \cdot |Shares|$
* **Calibrated Parameters:** $\zeta = 0.15$ (Market Impact Coefficient).
* **Implementation:** Tracks 20-day rolling ADDV in the ingestion layer.

---

<a name="12-phase-9-demo-verification--aumf-integration"></a>

## 12. Phase 9: Demo, Verification & AUMF Integration

**Location:** `computation/` (Track 10)

### 12.1 Hybrid Synergy Analyzer (Gideonsson, 2025)

**Critical Service: `TransferEntropyTracker`**

* **Goal:** Quantify if adding "Sentiment" to "ILI" signals actually adds predictive power or just noise.
* **Metric:** Transfer Entropy ($TE$) from Sentiment ($Y$) to Returns ($X$).
* **Rule:** If $TE_{Y \to X} < 0.01$ bits, set `sentiment_weight = 0.0`.
* **Outcome:** Prevents "Diversification Dilution" where adding poor quality data sources degrades the Sharpe ratio of
  clean indicators.

### 12.2 Christian-Christoffersen VaR Validation

* **Reference:** Vansteenberghe (2026), Chapter on Backtesting VaR.
* **Method:** Formal likelihood-ratio test checking whether the observed violation rate matches the nominal VaR level.
* **Integration:** Applied in the backtest engine (Track 8) to validate EVT-derived tail risk estimates.

---

<a name="13-phase-10-institutional-operations--incident-response"></a>

## 13. Phase 10: Institutional Operations & Incident Response

### 13.1 High-Availability Math Sidecar (Track 11)

* **Orchestration:** Deploy 3 instances of Python `analytics-worker` behind a load balancer.
* **Health Probe:** `/health` endpoint must verify `statsmodels` and `scipy` readiness.
* **Transport:** Arrow-over-Unix-Sockets for localized IPC (< 50us latency).

### 13.2 Morning Pre-Flight Protocol (08:00 AM ET)

1. **NY Fed Sync:** Verify SOFR publication vs. yesterday's T-Bill proxy.
2. **Audit Review:** Launch `IntersubjectiveAuditService` dashboard; verify `ir_score > 0.95`.
3. **GPD Update:** Trigger `EvtRiskService` to recalibrate extreme tail thresholds.
4. **BH-FDR Review:** Review strategies that dropped significance after FDR correction.

### 13.3 Signal Dislocation Protocol

* **Event:** Strategy signal deviates from theoretical paper results by > 5%.
* **Check 1:** Verify `ir_score` in `intersubjective_audit_log`.
* **Check 2:** Verify Day-Count convention in `CdmOptionSnapshot`.
* **Check 3:** Verify `UniverseAggregator` broadcast context for data gaps.

### 13.4 Risk Limit Breach Protocol

* **Event:** Portfolio VaR exceeds EVT-derived crash threshold.
* **Action:** Immediate halt of Virtual Execution.
* **Review:** Python `MacroShockMonitor` IRF analysis of current shock propagation.

### 13.5 Intraday Dislocation Protocol

* **Threshold:** `ProxyDivergenceGuard` score $> 2.0$ std dev.
* **State Change:** AUMF enters `SUSPENDED_UNCERTAINTY` (Stage 3).
* **Audit:** Launch `IntersubjectiveAuditService` dashboard; identify the "Coding Rule" or source provider causing the
  divergence.
* **Resolution:** Manual clearing by two authorized quant operators.

---

<a name="14-cross-cutting-engineering-concerns"></a>

## 14. Cross-Cutting Engineering Concerns

### 14.1 Numerical Precision Policy

| Context                     | Java Type    | Scale | Rationale                                                       |
|:----------------------------|:-------------|:------|:----------------------------------------------------------------|
| **Notionals / Cash Flows**  | `BigDecimal` | 8     | No rounding accumulation in multi-leg trades.                   |
| **Option Prices / Strikes** | `BigDecimal` | 4     | Exact match with exchange market data.                          |
| **Greeks / Signals**        | `double`     | 15    | Standard IEEE 754 adequate for inference.                       |
| **P-Values / Alpha**        | `float32`    | N/A   | Arrow IPC memory efficiency; $10^{-7}$ precision is sufficient. |
| **Audit Hashes**            | `String`     | 64    | SHA-256 for data verification.                                  |

### 14.2 High-Efficiency Memory Management

**Problem:** 151 strategies x 500 symbols x 252 days = 19M doubles (~152 MB raw).

* **JVM Overhead:** Spikes to ~500 MB when using standard `ArrayList<Double>`.
* **Mitigation:**
    1. **Flyweight Buffers:** All strategies share a single `HistoryManager` instance.
    2. **Off-Heap Direct Memory:** `DirectByteBuffer` for the `UniverseAggregator` cache to minimize GC pauses during
       tick cycles.

### 14.3 Virtual Thread Pinning Prevention

* **Mandate:** Avoid pinning Virtual Threads during Python sidecar calls (Track 3).
* **Logic:** Use a `Semaphore(max_concurrent_math_calls)` (default 50) to bound I/O operations.
* **Monitoring:** Enable `-Djdk.tracePinnedThreads=short` to identify any blocking math logic.

---

<a name="15-dependency--sprint-sequence"></a>

## 15. Dependency & Sprint Sequence (20-Sprint Roadmap)

| Sprint    | Phase | Objective             | Key Tasks                                                                                    |
|:----------|:------|:----------------------|:---------------------------------------------------------------------------------------------|
| **S1-S2** | 1     | Math Sidecar          | Build `statistical_methods` Python router; Implement EVT fitting and FDR correction service. |
| **S3**    | 2     | CDM Enrichment        | Add Greeks, Duration, and precise TTM fields to instrument snapshots. Enforce ACT/365.       |
| **S4**    | 2     | Forensic Audit        | Implement `IntersubjectiveAuditService` in Ingestion Layer. Build Rule Registry.             |
| **S5-S6** | 3     | Options Spreads       | `LegMatchService` + Eq 17-78 porting. Multi-leg strike alignment testing.                    |
| **S7-S8** | 3     | Option Combos         | Butterflies (Eq 79) and Straddles. Integrate Greeks-fallback in sidecar.                     |
| **S9**    | 4     | Equity Ranking        | Sorted decile engine for momentum strategies.                                                |
| **S10**   | 4     | Universe Aggregation  | `UniverseAggregator` (Eq 293) global mean broadcast.                                         |
| **S11**   | 5     | Fixed Income          | Bullet/Barbell portfolios + Yield Curve NS model.                                            |
| **S12**   | 6     | Conditional Inference | `QuantileRegEngine` (QR Bands) bridge.                                                       |
| **S13**   | 7     | HF Aggregation        | `KernelAggregator` for Polygon tick return kernels.                                          |
| **S14**   | 8     | Backtest Rigor        | `DelayDExecutor` and Volume-Scaled Slippage (Eq 553) implementation.                         |
| **S15**   | 8     | Risk Validation       | Christian-Christoffersen VaR tests in Backtest.                                              |
| **S16**   | 9     | Strategy Audit        | Cross-validate top 50 strategies against SSRN paper.                                         |
| **S17**   | 10    | HA Deployment         | Kubernetes Helm charts for Python sidecar; Arrow socket optimization.                        |
| **S18**   | 10    | Disaster Recovery     | Failover protocol for SOFR data gaps using T-Bill proxies.                                   |
| **S19**   | 5     | Multi-Asset Synergy   | Transfer Entropy tracker implementation for ILI/Sentiment.                                   |
| **S20**   | 9     | Demo Virt Portf       | 151-strategy virtual execution demo with live slippage monitoring.                           |

---

<a name="16-testing-strategy"></a>

## 16. Testing Strategy (Given-When-Then)

### 16.1 Institutional Unit Tests

**Test 01: Multiple Testing Discovery Control**

* **Given:** 100 candidate strategies with uniformly random p-values [0,1].
* **When:** `MultipleTestingCorrectionService` is called with $Q=0.05$.
* **Then:** Actionable mask contains ~0 `true` bits; prevents P-Hacking.

**Test 02: Leg Match Symmetry Constraint**

* **Given:** Option chain with strikes [95.0, 100.0, 110.0].
* **When:** `LegMatchService.findButterflies()` is called.
* **Then:** Resulting list is EMPTY because symmetry constraint (100-95 != 110-100) fails.

**Test 03: Intersubjective Reproducibility Audit**

* **Given:** An AlphaSignal ID generated from a SOFR spike period.
* **When:** `intersubjective_audit_log` is queried for that ID.
* **Then:** Trace shows Rule 01 (RAW), Rule 03 (GAP), Rule 04 (CALC) in linear sequence with SHA-256 hash of the
  original NY Fed JSON payload.

**Test 04: FDR True Positive Detection**

* **Given:** 100 candidate strategies where 5 have truly significant p-values (0.001).
* **When:** `MultipleTestingCorrectionService.correct()` is called with $Q=0.05$.
* **Then:** Actionable mask contains exactly 5 `true` bits; 95 random noise candidates are suppressed.

### 16.2 False-Positive Statistical Tests

1. **Reverse Time-Series Test:** Run any strategy with the time index reversed. Result MUST be NEUTRAL (Hit
   rate $= 0.50 \pm 0.02$).
2. **Delay-d Sensitivity Check:** Run Delay-0 vs Delay-1 backtests. If Sharpe($D_0$) / Sharpe($D_1$) $> 3.0$, the
   strategy is flagged for "Microstructure Sensitivity" and reviewed for data leakage.

---

<a name="17-risk-register--mitigations"></a>

## 17. Risk Register & Mitigations

| ID     | Risk Factor             | Impact              | Prob | Mitigation Strategy                                      |
|:-------|:------------------------|:--------------------|:-----|:---------------------------------------------------------|
| **R1** | GPD Non-Convergence     | Model failure       | Med  | Fallback to Student-t (df=4) distribution.               |
| **R2** | IR Log Storage Bloat    | Storage spike       | High | Partition audit log by week; purge > 90d.                |
| **R3** | LegMatch Matching Lag   | Signal missing      | Med  | Parallelize matching; use concurrent map; timeout 500ms. |
| **R4** | SVAR Matrix Singularity | Macro IRF failure   | Low  | Apply Ridge regularization (L2) to coefficient matrix.   |
| **R5** | Day-Count Signal Drift  | Accuracy divergence | Med  | Shared `CdmDayCount` enum enforced via validation beans. |
| **R6** | UniverseAggregator OOM  | System crash        | Low  | Enforce 1000 symbol cap; use direct off-heap memory.     |

---

## Appendix A: Source Document Index

| ID     | Title                           | SSRN ID | Track                            |
|:-------|:--------------------------------|:--------|:---------------------------------|
| **S1** | 151 Trading Strategies          | 3247865 | Track 5 (Strategy Library)       |
| **S2** | Statistical Methods Review      | 6661758 | Track 3 & 8 (Statistical Rigor)  |
| **S3** | Quant Finance Lecture Notes     | 5178205 | Track 3 & 5 (Advanced Inference) |
| **S4** | Quantitative Trading Algorithm  | 6553778 | Track 3 & 10 (Macro Flexibility) |
| **S5** | Quantitative Rechtswissenschaft | 3377384 | Track 2 & 4 (Auditability)       |

---

## Appendix B: Comprehensive Strategy Catalog

This appendix provides the definitive production logic for the "Essential" strategies.

### B.1 Options Spreads (Section 2)

| ID        | Strategy Name        | Equation Ref | Production Logic                                                |
|:----------|:---------------------|:-------------|:----------------------------------------------------------------|
| **OS.01** | Bull Call Spread     | Eq. 17-28    | Buy Low Strike Call ($K_1$), Sell High Strike Call ($K_2$).     |
| **OS.02** | Bear Put Spread      | Eq. 29-38    | Buy High Strike Put ($K_2$), Sell Low Strike Put ($K_1$).       |
| **OS.03** | Long Straddle        | Eq. 239-246  | Buy ATM Call + ATM Put; Volatility expansion trade.             |
| **OS.04** | Call Butterfly       | Eq. 79-100   | +1 $K_1$, -2 $K_2$, +1 $K_3$ ($K_2$ is Midpoint). Range play.   |
| **OS.05** | Put Butterfly        | Eq. 101-130  | Same as OS.04 but using Puts. Max profit at $K_2$.              |
| **OS.06** | Iron Condor          | Eq. 179-208  | OTM Put Spread + OTM Call Spread. Volatility dampening.         |
| **OS.07** | Iron Butterfly       | Eq. 131-158  | ATM Straddle + OTM Wing Spreads. High-conviction range.         |
| **OS.08** | Long Strangle        | Eq. 253-260  | Buy OTM Call + OTM Put; Cheap volatility spike trade.           |
| **OS.09** | Ratio Spread         | Eq. 209-220  | Uneven leg ratios (e.g. 2:1) for directional bias.              |
| **OS.10** | Calendar Spread      | Eq. 221-238  | Same Strike, Different Expiries; Theta arbitrage.               |
| **OS.11** | Box Spread           | Eq. 247-252  | Risk-free rate arb using Bull Call + Bear Put spreads.          |
| **OS.12** | Diagonal Spread      | Sec 2.9      | Strikes and Expiries different; capture Vega/Theta basis.       |
| **OS.13** | Backspread           | Sec 2.11     | Ratio spread biased toward long legs for volatility.            |
| **OS.14** | Vertical Put Spread  | Eq. 49       | Sell OTM Put, Buy further OTM Put (Credit).                     |
| **OS.15** | Diagonal Call Spread | Sec 2.x      | Calendar spread with strike bias for delta capture.             |
| **OS.16** | Covered Call         | Sec 2.1      | Long Underlying + Short OTM Call; Income generation.            |
| **OS.17** | Protective Put       | Sec 2.2      | Long Underlying + Long ATM Put; Portfolio insurance.            |
| **OS.18** | Collar Spread        | Sec 2.3      | Long Underlying + Short Call + Long Put; Range lock.            |
| **OS.19** | Married Put          | Sec 2.4      | Concurrent purchase of Underlying and ATM Put.                  |
| **OS.20** | Synthetic Long       | Sec 2.5      | Long Call + Short Put at same strike; delta-1 proxy.            |
| **OS.21** | Bull Put Spread      | Eq. 49-58    | Credit spread: Sell High Put, Buy Low Put.                      |
| **OS.22** | Bear Call Spread     | Eq. 39-48    | Credit spread: Sell Low Call, Buy High Call.                    |
| **OS.23** | Call Condor          | Eq. 159-178  | Vertical spread mix using four distinct call strikes.           |
| **OS.24** | Put Condor           | Sec 2.8      | Vertical spread mix using four distinct put strikes.            |
| **OS.25** | Risk Reversal        | Sec 2.12     | Sell OTM Put, Buy OTM Call; capture skew directional bias.      |
| **OS.26** | Butterfly Backspread | Sec 2.13     | Combination of OS.04 and OS.13 for extreme convexity.           |
| **OS.27** | Christmas Tree       | Sec 2.14     | Broken-wing butterfly using skipped strikes for cost reduction. |
| **OS.28** | Seagull Spread       | Sec 2.15     | Option collar with a directional credit spread kicker.          |
| **OS.29** | Straddle Swap        | Sec 2.16     | Arbitrage between implied vol of calls vs puts.                 |
| **OS.30** | Calendar Straddle    | Sec 2.17     | Long straddle in far month, Short straddle in near month.       |

### B.2 Equity Alpha (Section 3)

| ID        | Strategy Name      | Equation Ref | Core Mechanism                                           |
|:----------|:-------------------|:-------------|:---------------------------------------------------------|
| **EA.01** | Price Momentum     | Eq. 266-280  | Ranking returns over 252-day window; Long Top Decile.    |
| **EA.02** | Earnings Surprise  | Eq. 281-288  | Standardized Unanticipated Earnings (SUE) ranking.       |
| **EA.03** | Mean-Reversion     | Eq. 294-300  | Residuals from OLS vs Market-Beta; Reverse extremes.     |
| **EA.04** | Cluster MR         | Eq. 293      | Demeaning returns by Universe Mean (UniverseAggregator). |
| **EA.05** | Pairs Coint.       | Eq. 301-310  | Arb on drift between cointegrated symbols (Johansen).    |
| **EA.06** | Value (B/P)        | Eq. 289-292  | Cross-sectional rank of Book-to-Price ratio.             |
| **EA.07** | MA Crossover       | Eq. 311-320  | EMA(12) / EMA(26) signal crossover with confirmation.    |
| **EA.08** | Support/Resistance | Eq. 331-340  | Breakout of 20-day high/low price channel.               |
| **EA.09** | BB Breakout        | Eq. 341-350  | Volatility channel exit; Buy on Upper Band breach.       |
| **EA.10** | Accum/Dist         | Eq. 351-360  | Volume-weighted price trend confirmation index (A/D).    |
| **EA.11** | RSI Oscillator     | Sec 3.15     | Overbought (>70) or Oversold (<30) RSI reversal.         |
| **EA.12** | MACD Divergence    | Sec 3.11     | Oscillator vs Price direction mismatch.                  |
| **EA.13** | Bollinger Width    | Eq. 350      | Squeeze play on narrow volatility bands.                 |
| **EA.14** | Volume Momentum    | Sec 3.5      | Ranking symbols by volume acceleration over 20 days.     |
| **EA.15** | Stochastic Cross   | Sec 3.15     | K-D line crossover in oversold territory.                |
| **EA.16** | Chaikin Vol        | Sec 3.18     | EMA of high-low range to detect volatility expansion.    |
| **EA.17** | MFI Inversion      | Sec 3.19     | Volume-weighted RSI reversal at extremes.                |
| **EA.18** | TRIX Reversal      | Sec 3.20     | Triple-smoothed EMA momentum oscillator.                 |
| **EA.19** | Coppock Curve      | Sec 3.21     | Long-term buying pressure filter (Sum of ROCs).          |
| **EA.20** | Keltner Channel    | Sec 3.22     | ATR-based volatility envelope breakout.                  |
| **EA.21** | Donchian Channel   | Sec 3.23     | High/Low breakout logic over N-period window.            |
| **EA.22** | Aroon Oscillator   | Sec 3.24     | Detection of time since high/low to find trend age.      |
| **EA.23** | Parabolic SAR      | Sec 3.25     | Trailing stop-reversal momentum indicator.               |
| **EA.24** | ZigZag Filter      | Sec 3.26     | Eliminating noise by filtering price moves < X%.         |
| **EA.25** | Ichimoku Cloud     | Sec 3.27     | Multi-timeframe trend and support/resistance grid.       |

### B.3 Fixed Income & Macro Logic

| ID        | Strategy Name    | Theory Ref     | Production Objective                                                 |
|:----------|:-----------------|:---------------|:---------------------------------------------------------------------|
| **FI.01** | Bullet Portf.    | Sec 5 intro    | Bond clustering at a single target duration point.                   |
| **FI.02** | Barbell Portf.   | Sec 5 intro    | High-convexity mix of short-end and long-end tenors.                 |
| **FI.03** | Duration Neutral | Eq 374-383     | Butterfly portfolio with Zero aggregate net duration.                |
| **FI.04** | NS Interpolator  | Vansteenberghe | Parametric yield curve function ($\beta_0, \beta_1, \beta_2, \tau$). |
| **MC.01** | TVP-SVAR IRF     | Gideonsson     | Impulse response showing shifting sensitivity over time.             |
| **MC.02** | QR Bands         | Vansteenberghe | Conditional support levels robust to heteroscedasticity.             |
| **MC.03** | TE Synergy       | Gideonsson     | Transfer Entropy audit of information synergy flow.                  |
| **MC.04** | EVT Thresholds   | Rahaman        | Extreme event probability fitting for "Crash Alerts".                |

### B.4 Mandatory Implementation Logic Specifications

**1. Universe Mean Return MANDATE (Eq. 293):**

* "All equity alpha strategies requiring cross-sectional de-meaning MUST use the output of the `UniverseAggregator`.
  Independent mean calculation is strictly prohibited to ensure mathematical consistency."

**2. Call Butterfly Construction MANDATE (Eq. 79-100):**

* **Construction:** $+1 C(K_1), -2 C(K_2), +1 C(K_3)$.
* **Constraint:** $K_2 = (K_1 + K_3) / 2$ (Symmetry Mandate).
* **Execution:** "Backtest engine MUST verify simultaneous fills for all three legs. Partial fills invalidate the signal
  log."

**3. Volume-Scaled Slippage MANDATE (Eq. 553):**

* **Formula:** $\Delta P = \zeta \cdot \frac{\sigma}{V_{ADDV}} \cdot Q$.
* **Logic:** "Every backtest result MUST display the delta between 'Ideal Return' and 'Eq 553 Adjusted Return'.
  Strategies that collapse under volume-scaled slippage are marked `LIQUIDITY_FRAGILE`."

**4. Intersubjective Reproduction MANDATE (Coupette 2026):**

* "No signal may transition to `ACTIONABLE` status if the `ir_score` for its constituent data points is below 0.90.
  Transparency of the 'Coding Rules' path is the absolute requirement for institutional deployment."

**5. EVT Crash Alert MANDATE (Rahaman 2026):**

* "When `tail_var_999` exceeds current ILI by > 3.0 std deviations, trigger `AUMF_STAGE_2` (Elevated Uncertainty).
  System MUST reduce paper trading position sizes by 50%."

---

*End of Exhaustive Quantitative Engineering Specification (v5.3.0-INSTITUTIONAL-MERGED)*

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 5. Docker & Infrastructure — 3 Missing Items
**Plan ref:** `11-deployment-operations.md`

| #   | Item                                       | Description                                                                                                              |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| 1   | `docker-compose.openbb.yml`                | REMOVED — replaced by direct free API clients (v6 free data source migration)                                            |
| 2   | Chronicle Queue volume in docker-compose   | Dedicated NVMe/tmpfs volume for ingestion overflow                                                                       |
| 3   | **Spring Security / OAuth2 config** (Java) | Plan requires OAuth2+PKCE, all `/api/**` require JWT, HTTPS, CSP headers — no Spring Security dependency or config found |

> **Note:** Jaeger **is** present in `docker-compose.yml`. The landing page Dockerfile **does** use nginx (matching the plan). Backend Dockerfile **does** use multi-stage build.

---


## 7. Runbooks — 17 Missing
**Plan ref:** `11-deployment-operations.md` (21 planned, 4 exist)

**Existing runbooks** (`docs/runbooks/`):
- `deployment.md`, `monitoring-troubleshooting.md`, `data-management.md`, `README.md`

**Missing runbooks:**

| #   | Runbook                                  |
| --- | ---------------------------------------- |
| 1   | OpenBB Sidecar Setup                     |
| 2   | Analytics Worker Deployment              |
| 3   | Polygon WebSocket Outage Procedure       |
| 4   | Chronicle Queue Overflow Recovery        |
| 5   | ILI Weight Recalibration                 |
| 6   | Proxy Divergence Event Review            |
| 7   | TimescaleDB Continuous Aggregate Refresh |
| 8   | TA-Lib Adapter Integration               |
| 9   | Bulkhead Pool Monitoring                 |
| 10  | Distributed Tracing with Jaeger          |
| 11  | Calibration Task Monitoring              |
| 12  | Disaster Alert Verification              |
| 13  | Big Red Button Runbook                   |
| 14  | Systemic Resilience Monitor Runbook      |
| 15  | Regulatory Compliance Report Generation  |
| 16  | De-Rounding Filter Verification          |
| 17  | Options Data Pipeline Verification       |

---
