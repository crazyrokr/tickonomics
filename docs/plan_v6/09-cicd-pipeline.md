# Track 9: CI/CD Pipeline (GitHub Actions)

**Phase:** Phase 7
**Can start:** Immediately (infrastructure-only, independent of code)
**Blocks:** Track 11 (Deployment references CI/CD)
**Depends on:** Nothing (can start in parallel with Track 1)

---

## Objective

Implement a full CI/CD pipeline using GitHub Actions for all four deployable artifacts:
landing page, analytics dashboard, backend, and analytics worker. Covers PR checks,
staging deploys, production promotion, and demo environment reporting.

**Analysis findings applied:**

- **Finding 1 (Virtual Threads Primary):** Backend tests run with `virtual-threads` profile
  only. No cross-stack consistency test suite — dual-mode parity is not maintained. The
  `consistencyTest` Gradle task is removed from CI.

---

## Workflow 1: PR Checks (`.github/workflows/pr-checks.yml`)

**Triggers:** `pull_request` against any branch.

```yaml
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Java 25
      - Cache Gradle dependencies
      - Run: ./gradlew lint          # Checkstyle, SpotBugs
      - Run: ./gradlew test          # Unit tests (Virtual Threads profile)
      - Run: ./gradlew integrationTest  # TimescaleDB testcontainers
      - Upload test results on failure

  analytics-worker:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Python 3.12
      - Cache pip
      - Run: ruff check analytics/
      - Run: pytest analytics/tests/
      - Run: cd analytics && python -m pytest tests/ --benchmark-only --benchmark-compare=0001 --benchmark-compare-fail=mean:10%

  dashboard:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Node.js 22
      - Cache node_modules
      - Run: cd frontend && npm ci
      - Run: cd frontend && npm run lint
      - Run: cd frontend && npm run typecheck
      - Run: cd frontend && npm run test -- --coverage
      - Run: cd frontend && npm run build

  landing-page:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Node.js 22
      - Cache node_modules
      - Run: cd landing && npm ci
      - Run: cd landing && npm run lint
      - Run: cd landing && npm run typecheck
      - Run: cd landing && npm run build
      - Run: Lighthouse CI (performance, accessibility, SEO audit)
```

All four jobs run in **parallel**.

### OpenTelemetry Integration in PR Checks (v4)

Add to the backend job in `pr-checks.yml` to verify OpenTelemetry instrumentation without
exporting traces during tests:

```yaml
  backend:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Java 25
      - Cache Gradle dependencies
      - Run: ./gradlew lint          # Checkstyle, SpotBugs
      - Run: ./gradlew test          # Unit tests (Virtual Threads profile)
        env:
          JAVA_TOOL_OPTIONS: "-javaagent:opentelemetry-javaagent.jar"
          OTEL_EXPORTER: "none"      # Don't export during tests, just verify instrumentation
      - Run: ./gradlew integrationTest  # TimescaleDB testcontainers
      - Upload test results on failure
```

The OpenTelemetry agent is attached during unit tests to verify that all instrumentation
spans are created correctly without actually exporting traces. `OTEL_EXPORTER: "none"`
prevents network traffic to a tracing backend during CI.

---

## Workflow 2: Build & Deploy Staging (`.github/workflows/deploy-staging.yml`)

**Triggers:** `push` to `main`.

```yaml
jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Login to Container Registry (GHCR or Docker Hub)
      - Build and push Docker images:
        - tickonomics-backend:${{ github.sha }}
        - tickonomics-analytics-worker:${{ github.sha }}
        - tickonomics-dashboard:${{ github.sha }}
        - tickonomics-landing:${{ github.sha }}

  deploy-staging:
    needs: build-and-push
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - Deploy backend to staging (SSH or kubectl)
      - Run Flyway migrations against staging TimescaleDB
      - Deploy analytics worker
      - Deploy dashboard to app.staging.tickonomics.io
      - Deploy landing page to staging.tickonomics.io
      - Wait for health checks (30s timeout)
      - Run smoke tests: curl health endpoints, verify ingestion running
      - Notify team via Slack/Discord webhook on success or failure

  deploy-demo:
    needs: build-and-push
    runs-on: ubuntu-latest
    environment: demo
    steps:
      - Update demo environment with new images
      - Restart demo paper trading engine
      - Verify demo portfolio is tracking
```

---

## Workflow 3: Promote to Production (`.github/workflows/deploy-prod.yml`)

**Triggers:** `workflow_dispatch` (manual), requires approval from 1 reviewer.

```yaml
jobs:
  promote:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - Verify staging health checks pass
      - Run Flyway migrations against production TimescaleDB
      - Tag images: tickonomics-*:latest
      - Deploy backend to production
      - Deploy analytics worker to production
      - Deploy dashboard to app.tickonomics.io
      - Deploy landing page to tickonomics.io
      - Wait for health checks (60s timeout)
      - Run smoke tests against production
      - Notify team via Slack/Discord
      - Create GitHub release with changelog
```

---

## Workflow 4: Scheduled Demo Report (`.github/workflows/demo-report.yml`)

**Triggers:** `schedule` — every Monday 6:00 AM UTC.

```yaml
jobs:
  report:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Query demo environment TimescaleDB for:
          - Portfolio P&L over last 7 days
          - Signal count and hit rate
          - Verification criteria progress
      - Generate markdown report
      - Post report as GitHub Issue comment (weekly thread)
      - If verification criteria ALL met, create Issue:
          "Demo verification passed — ready for live evaluation"
```

---

## Workflow 5: Landing Page Deployment (`.github/workflows/deploy-landing.yml`)

**Triggers:** `push` to `main` with changes in `landing/` directory.

```yaml
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Node.js 22
      - Run: cd landing && npm ci && npm run build
      - Deploy to Vercel or Cloudflare Pages (edge CDN)
      - Invalidate CDN cache for live demo data routes
```

Path filter: only triggers when `landing/**` files change.

---

## Workflow 6: Chaos Testing (`.github/workflows/chaos-tests.yml`) — v4

**Triggers:** `schedule` — weekly (Saturday 2:00 AM UTC), or `workflow_dispatch`.

**Environment:** staging

Validates system resilience under adverse conditions using Chaos Mesh or Toxiproxy for
fault injection. Each scenario verifies that resilience patterns (circuit breakers, LKG cache,
fallback mechanisms) function correctly.

```yaml
name: Chaos Tests

on:
  schedule:
    - cron: '0 2 * * 6'    # Saturday 2:00 AM UTC
  workflow_dispatch:

jobs:
  chaos-test:
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - Checkout

      - name: Install Chaos Tools
        run: |
          # Install Chaos Mesh CLI or Toxiproxy CLI
          curl -sSL https://github.com/chaos-mesh/chaos-mesh/releases/latest/download/chaosctl-linux-amd64.tar.gz | tar xz
          # Or: pip install toxiproxy-python

      - name: Scenario 1 - TimescaleDB Latency Injection
        run: |
          # Inject 1000ms latency to TimescaleDB for 60 seconds
          # Verify: circuit breakers open, LKG cache serves stale data
          chaos inject latency --target timescaledb --delay 1000ms --duration 60s

      - name: Scenario 2 - Analytics Worker Restart
        run: |
          # Kill analytics worker for 30 seconds
          # Verify: fallback to threshold-based anomaly checks
          chaos inject pod-failure --target analytics-worker --duration 30s

      - name: Scenario 3 - Chronicle Queue Capacity
        run: |
          # Fill chronicle queue to 90% capacity
          # Verify: overflow handling, no data loss
          chaos inject stress --target chronicle-queue --level 90%

      - name: Scenario 4 - Finnhub WS Disconnect
        run: |
          # Disconnect Finnhub WebSocket for 6 minutes (> 5 min fallback threshold)
          # Verify: REST fallback activates, no tick data gaps
          chaos inject network-partition --target finnhub-ws --duration 6m

      - name: Scenario 5 - Total External Failure
        run: |
          # Network partition all external sources for 60 seconds
          # Verify: graceful degradation, no NaN/Infinity in API responses
          chaos inject network-partition --target all-external --duration 60s

      - name: Verify Recovery
        run: |
          # After each scenario, verify:
          # 1. System recovers within 2 minutes
          # 2. No NaN/Infinity in API responses
          # 3. Circuit breakers opened and closed correctly
          # 4. LKG cache served stale data during outage
          # 5. Anomaly detection fell back to threshold checks
          ./scripts/chaos-verify-recovery.sh

      - name: Generate Chaos Test Report
        if: always()
        run: |
          ./scripts/generate-chaos-report.sh > chaos-report.md

      - name: Post Report as GitHub Issue Comment
        if: always()
        run: |
          gh issue comment ${{ env.CHAOS_TRACKING_ISSUE }} --body-file chaos-report.md
```

**Chaos scenarios and expected outcomes:**

| Scenario                 | Fault Injection       | Expected Behavior                                                                        |
|:-------------------------|:----------------------|:-----------------------------------------------------------------------------------------|
| TimescaleDB latency      | 1000ms for 60s        | Circuit breakers open, LKG cache serves stale data with `X-Data-Age: STALE` header       |
| Analytics Worker restart | Kill for 30s          | Fallback to threshold-based anomaly checks, `ANOMALY_WORKER_FALLBACK` logged             |
| Chronicle Queue capacity | Fill to 90%           | Overflow to disk-backed Chronicle Queue, no data loss on replay                          |
| Finnhub WS disconnect    | 6 min outage          | REST fallback activates after 5 min, `FINNHUB_WS_FALLBACK` logged                        |
| Total external failure   | 60s network partition | Graceful degradation, no NaN/Infinity in API responses, LKG cache serves last known good |

---

## Workflow 7: Benchmarking Service (`.github/workflows/benchmark.yml`) — v5

**Triggers:** `pull_request` against main with changes in `computation/` or `analytics/`, or `workflow_dispatch`.

Compares new ML-based models against traditional baselines using standardized performance protocols.

```yaml
name: Benchmark Service

on:
  pull_request:
    paths:
      - 'computation/**'
      - 'analytics/**'
  workflow_dispatch:

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - Checkout
      - Setup Java 25
      - Setup Python 3.12

      - name: Run Backtest Benchmarks
        run: |
          # Run standard backtest with default weights
          ./gradlew :computation:backtestBenchmark
          # Compare Sharpe, hit rate, drawdown against baseline
          python analytics/scripts/compare_benchmarks.py --baseline=.benchmarks/latest.json

      - name: Run Reproducibility Check
        run: |
          # Verify RDS score for all models
          python analytics/scripts/rds_check.py --min-score=1

      - name: Run Regulatory Scenario Tests
        run: |
          # Run SMC scenarios (message spikes, volatility jumps, capacity exhaustion)
          ./gradlew :computation:regulatoryScenarioTest

      - name: Publish Benchmark Report
        if: always()
        run: |
          python analytics/scripts/publish_benchmark_report.py > benchmark-report.md

      - name: Comment on PR
        if: github.event_name == 'pull_request'
        run: |
          gh pr comment ${{ github.event.pull_request.number }} --body-file benchmark-report.md
```

**Benchmark comparison metrics:**
| Metric | Description |
| :--- | :--- |
| Sharpe ratio | Strategy risk-adjusted return |
| Hit rate | Signal direction accuracy |
| Max drawdown | Worst peak-to-trough decline |
| Return Gap | Execution alpha vs. buy-and-hold |
| RDS score | Reproducibility disclosure quality |
| SMC pass rate | Stressed market condition scenario survival |

---

## Environment Secrets

| Secret                     | Used In              | Purpose                               |
|:---------------------------|:---------------------|:--------------------------------------|
| `CONTAINER_REGISTRY_URL`   | All deploy workflows | Docker/GHCR registry URL              |
| `CONTAINER_REGISTRY_TOKEN` | All deploy workflows | Registry authentication               |
| `STAGING_SSH_KEY`          | deploy-staging       | SSH access to staging server          |
| `STAGING_DB_URL`           | deploy-staging       | TimescaleDB connection for migrations |
| `PROD_SSH_KEY`             | deploy-prod          | SSH access to production server       |
| `PROD_DB_URL`              | deploy-prod          | TimescaleDB connection for migrations |
| `DEMO_DB_URL`              | demo-report          | Read-only demo TimescaleDB access     |
| `SLACK_WEBHOOK_URL`        | All deploy workflows | Deployment notifications              |
| `VERCEL_TOKEN`             | deploy-landing       | Vercel deployment                     |
| `LANDING_API_URL`          | deploy-landing       | Staging API for live demo data        |
| `CHAOS_MESH_URL`           | chaos-tests (v4)     | Chaos Mesh API endpoint               |
| `TOXIPROXY_URL`            | chaos-tests (v4)     | Toxiproxy API endpoint                |
| `BENCHMARK_BASELINE_URL`   | benchmark (v5)       | URL to baseline benchmark results     |
| `FINNHUB_API_KEY`          | backend, ingestion   | Finnhub REST and WebSocket API key    |
| `ALPHAVANTAGE_API_KEY`     | backend, ingestion   | Alpha Vantage deep history API key    |

---

## Deployment Checklist

Each deployment (staging or prod):

1. All images built and pushed with git SHA tag.
2. Flyway migration dry-run (validate against target DB).
3. Apply Flyway migrations.
4. Deploy new container images (rolling update, zero-downtime).
5. Wait for health check endpoints to return 200.
6. Run smoke tests (API connectivity, ingestion active, WebSocket connected).
7. Verify TimescaleDB continuous aggregates are refreshing.
8. Verify demo environment is tracking (staging only).
9. Notify team.

---

## Directory Structure

```
.github/
├── workflows/
│   ├── pr-checks.yml
│   ├── deploy-staging.yml
│   ├── deploy-prod.yml
│   ├── demo-report.yml
│   ├── deploy-landing.yml
│   ├── chaos-tests.yml           # v4: Chaos testing workflow
│   └── benchmark.yml               # v5: Benchmarking service workflow
└── lighthouse/
    └── lighthouse-ci-config.js
```

---

## Validation

- [ ] PR checks workflow runs on all pull requests.
- [ ] All four parallel jobs (backend, analytics-worker, dashboard, landing-page) pass.
- [ ] Backend tests run with Virtual Threads profile (no dual-mode tests) (Finding 1).
- [ ] Staging deploy builds and pushes all four Docker images.
- [ ] Flyway migrations run against staging TimescaleDB.
- [ ] Smoke tests verify health endpoints after deploy.
- [ ] Production promotion requires manual trigger and approval.
- [ ] Demo report generates weekly markdown summary.
- [ ] Landing page deploy triggers only on `landing/` changes.
- [ ] All secrets configured in GitHub environment settings.
- [ ] Slack/Discord notifications fire on deploy success and failure.

**v4 additions:**

- [ ] Chaos test workflow runs weekly (Saturday 2:00 AM UTC) and on manual trigger (`workflow_dispatch`).
- [ ] Each chaos scenario verifies system recovery within 2 minutes.
- [ ] Chaos scenario 1 (TimescaleDB latency) verifies circuit breakers open and LKG cache serves stale data.
- [ ] Chaos scenario 2 (Analytics Worker restart) verifies fallback to threshold-based anomaly checks.
- [ ] Chaos scenario 3 (Chronicle Queue capacity) verifies overflow handling with no data loss.
- [ ] Chaos scenario 4 (Finnhub WS disconnect) verifies REST fallback activates after 5 min.
- [ ] Chaos scenario 5 (Total external failure) verifies no NaN/Infinity in API responses.
- [ ] OpenTelemetry agent attaches to backend tests without failures (spans created, `OTEL_EXPORTER=none`).
- [ ] Chaos test report generated and posted as GitHub Issue comment.

**v5 additions:**

- [ ] Benchmark workflow runs on PRs touching computation/ or analytics/ code (v5).
- [ ] Benchmark comparison reports Sharpe, hit rate, drawdown, Return Gap, RDS score (v5).
- [ ] Regulatory scenario tests (SMC) run as part of CI pipeline (v5).
- [ ] RDS score check requires minimum score of 1 for all models (v5).
- [ ] Benchmark regression check detects > 10% performance degradation (v5).
- [ ] Benchmark report posted as PR comment (v5).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|:--------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: removed dual-mode consistency tests, Virtual Threads profile only for backend tests.                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| v2      | No structural changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| v3      | No structural changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| v4      | Added Workflow 6: Chaos Testing (`.github/workflows/chaos-tests.yml`) with weekly schedule and manual trigger. Five chaos scenarios: TimescaleDB latency injection, analytics worker restart, chronicle queue capacity, Polygon WS disconnect, total external failure. Added OpenTelemetry agent to backend PR check job with `OTEL_EXPORTER=none`. Added two new environment secrets (`CHAOS_MESH_URL`, `TOXIPROXY_URL`). Added `chaos-tests.yml` to directory structure. Added 9 validation criteria for chaos testing and OpenTelemetry instrumentation. |
| v5      | `09-cicd-pipeline.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Added Workflow 7: Benchmarking Service (`.github/workflows/benchmark.yml`) triggered on PRs touching computation/analytics code. Compares backtest performance (Sharpe, hit rate, drawdown, Return Gap, RDS score) against baseline. Runs reproducibility check (min RDS score), regulatory scenario tests (SMC), and benchmark regression detection. Added benchmark regression step to analytics-worker PR job. Added `BENCHMARK_BASELINE_URL` environment secret. Added `benchmark.yml` to directory structure. Added 6 validation criteria. |
| v6      | Migrated data source references to free-tier providers. Chaos Scenario 4: Polygon WS Disconnect changed to Finnhub WS Disconnect with target `finnhub-ws`. Environment secrets: replaced `POLYGON_API_KEY` with `FINNHUB_API_KEY` and `ALPHAVANTAGE_API_KEY`. Chaos scenario expected outcomes table updated to reference Finnhub WS fallback. Validation checklist updated for Finnhub WS disconnect.                                                                                                                                                    |

---

## Appendix: 03-cicd-workflows.md

> *Merged from `gaps/03-cicd-workflows.md` / `done/03-cicd-workflows.md` during plan_v6 consolidation.*

# CI/CD — 3 Missing Workflows

**Plan ref:** `09-cicd-pipeline.md`

| #   | Workflow             | Trigger                                | Description                                                                                    |
| --- | -------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 1   | `demo-report.yml`    | Weekly Monday 6:00 UTC                 | Queries demo DB for P&L/signal count/hit rate, posts GitHub Issue                              |
| 2   | `deploy-landing.yml` | Push to main with `landing/**` changes | Build and deploy landing page to Vercel/Cloudflare                                             |
| 3   | `chaos-tests.yml`    | Weekly Saturday 2:00 UTC               | 5 chaos scenarios: TSDB latency, worker restart, buffer overflow, WS disconnect, total failure |

> **Note:** The plan calls for 7 separate workflows; the implementation has 4 (with `chaos-benchmark.yml` merging chaos + benchmark). The `benchmark.yml` as a standalone is also missing but partially covered by `chaos-benchmark.yml`.

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 3. CI/CD — 3 Missing Workflows
**Plan ref:** `09-cicd-pipeline.md`

| #   | Workflow             | Trigger                                | Description                                                                                    |
| --- | -------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 1   | `demo-report.yml`    | Weekly Monday 6:00 UTC                 | Queries demo DB for P&L/signal count/hit rate, posts GitHub Issue                              |
| 2   | `deploy-landing.yml` | Push to main with `landing/**` changes | Build and deploy landing page to Vercel/Cloudflare                                             |
| 3   | `chaos-tests.yml`    | Weekly Saturday 2:00 UTC               | 5 chaos scenarios: TSDB latency, worker restart, buffer overflow, WS disconnect, total failure |

> **Note:** The plan calls for 7 separate workflows; the implementation has 4 (with `chaos-benchmark.yml` merging chaos + benchmark). The `benchmark.yml` as a standalone is also missing but partially covered by `chaos-benchmark.yml`.

---
