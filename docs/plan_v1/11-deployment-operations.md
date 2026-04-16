# Track 11: Deployment & Operations

**Phase:** Phase 8
**Can start:** After all other tracks complete
**Blocks:** Nothing (final track)
**Depends on:** All tracks

---

## Objective

Containerize all artifacts, create Docker Compose configurations for each environment,
document operational runbooks, and implement data retention automation.

**Analysis findings applied:**

- **Finding 1 (Virtual Threads Primary):** Backend runs Virtual Threads + Spring MVC only.
  No WebFlux mode. `SPRING_PROFILES_ACTIVE=virtual-threads` is the single profile. The
  "Concurrency Mode Switch" runbook is removed.
- **Finding 7 (Chronicle Queue Placement):** Docker Compose maps a dedicated Docker volume
  for Chronicle Queue overflow, separate from TimescaleDB data volume. Production deployments
  map this to a dedicated NVMe partition or `tmpfs` mount.

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

