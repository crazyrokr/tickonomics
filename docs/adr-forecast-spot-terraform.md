# ADR: Terraform Spot Instance Infrastructure for Forecast Pipeline

**Date:** 2026-06-09
**Status:** Implemented

## Context

The Tickonomics platform had no Infrastructure-as-Code. CI/CD deploy steps were placeholder stubs. Forecast runs required manual Docker Compose execution with no cost optimization, no cloud storage for results, and no scheduling.

The platform's forecast pipeline is a composition of Java `@Scheduled` ingestion, KPI computation services, and Python ML analytics — not a single endpoint. Any infrastructure solution must orchestrate this end-to-end on ephemeral spot instances.

## Decision

Implement Terraform modules provisioning spot instances on AWS, GCP, and Azure. Each run boots a spot instance, runs the full forecast pipeline in under 1 hour, collects results to cloud storage, and self-terminates at ~$0.35–0.38 per run.

### Architecture

**Modules:**
- `networking` — VPC, subnets, security groups
- `storage` — S3/GCS/Blob (30-day lifecycle) + persistent EBS/disk for TimescaleDB
- `compute-spot` — Spot instance with cloud-init user-data bootstrap
- `container-registry` — ECR/Artifact Registry/ACR repositories
- `orchestrator` — Lambda/Cloud Functions + EventBridge/Scheduler + API Gateway
- `monitoring` — CloudWatch/Monitor alerts, dashboards, SNS topics

**Pipeline lifecycle (user-data):**
1. Install Docker + cloud CLI
2. Format/mount persistent disk at `/mnt/timescaledb`
3. Pull images from container registry
4. `docker compose up` (backend + analytics-worker + timescaledb only)
5. Wait for healthy services + data ingestion threshold
6. Trigger KPI/signal/analytics computation endpoints
7. Collect results (API dumps + pg_dump) → tar → upload to cloud storage
8. Graceful shutdown, unmount disk, self-terminate instance

**Application changes:**
- `application.yml`: `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 60s`
- New `GracefulShutdown.java`: `@Component` implementing `DisposableBean` with `AtomicBoolean` flag
- `TickonomicsApplication.java`: Explicit `@EnableScheduling` annotation
- `analytics/app/main.py`: SIGTERM handler setting `shutdown_requested` flag
- `analytics/Dockerfile`: `--timeout-graceful-shutdown 30` on uvicorn CMD

**CI/CD:** `.github/workflows/forecast-deploy.yml` — build/push images, terraform validate/plan/apply, smoke test.

**Atlantis PR automation:** `atlantis.yaml` at repo root configures per-environment plan/apply workflows triggered by PRs touching `infra/terraform/`. Three Atlantis projects (`forecast-aws`, `forecast-gcp`, `forecast-azure`) plan in parallel, require approval before apply. Verification plan at `docs/atlantis-verification-plan.md`.

**CLI:** `infra/scripts/run-forecast.sh` — interactive one-shot for manual runs.

**Runbook:** `docs/runbook-forecast-deployment.md` — end-to-end guide covering deploy, monitor, verify results, troubleshoot, and cleanup. Result verification script at `infra/scripts/verify-results.sh`.

### File Inventory

**Created (47 files):**
- `infra/terraform/versions.tf`
- `infra/terraform/modules/networking/` (3 files)
- `infra/terraform/modules/storage/` (3 files)
- `infra/terraform/modules/compute-spot/` (4 files)
- `infra/terraform/modules/container-registry/` (3 files)
- `infra/terraform/modules/orchestrator/` (6 files)
- `infra/terraform/modules/monitoring/` (3 files)
- `infra/terraform/environments/aws/` (6 files)
- `infra/terraform/environments/gcp/` (7 files)
- `infra/terraform/environments/azure/` (6 files)
- `infra/terraform/shared/` (5 files)
- `infra/scripts/run-forecast.sh`
- `.github/workflows/forecast-deploy.yml`
- `atlantis.yaml`
- `.tflint.hcl`
**Modified (4 files):**
- `app/src/main/resources/application.yml`
- `computation/src/main/java/.../lifecycle/GracefulShutdown.java` (new)
- `analytics/app/main.py`
- `analytics/Dockerfile`
- `app/src/main/java/com/tickonomics/TickonomicsApplication.java`

## Consequences

- **Cost:** ~$0.35–0.38 per forecast run; ~$7–8/month for 20 weekday runs.
- **Resilience:** Persistent EBS/disk survives spot termination. Graceful shutdown flushes DB buffers.
- **Multi-cloud:** Identical pipeline runs on AWS, GCP, and Azure via environment-specific Terraform.
- **Observability:** CloudWatch dashboards, SNS alerts, spot interruption handlers per provider.
- **Complexity:** ~50 new infrastructure files; requires cloud provider credentials and remote state setup.
- **PR safety:** Atlantis enforces plan-before-apply, approval gates, and mergeability checks on all Terraform changes.
