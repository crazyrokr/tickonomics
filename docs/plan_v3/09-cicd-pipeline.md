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
│   └── deploy-landing.yml
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
