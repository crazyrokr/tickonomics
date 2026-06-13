# Plan: Terraform Spot Instance Deployment for Forecast Tasks

## Context

The Tickonomics platform currently runs exclusively via Docker Compose with no Infrastructure-as-Code. The CI/CD deploy steps are placeholder stubs. This plan adds Terraform modules to provision spot instances on AWS, GCP, and Azure, run a complete forecast pipeline in under 1 hour, collect results to cloud storage, and self-terminate — achieving ~$0.35–0.38 per forecast run.

**Key constraints:** The project has no single "forecast" endpoint — the pipeline is a composition of Java `@Scheduled` ingestion, KPI computation services, and Python ML analytics. The orchestration script must drive this end-to-end.

**Storage prerequisite:** The forecast and anomaly detection subsystems have three storage gaps that must be closed before spot-instance orchestration can deliver reliable results. These gaps — forecast result storage, anomaly score wiring, and model state persistence — are addressed in [`docs/forecast-anomaly-storage-gap-elimination-plan.md`](forecast-anomaly-storage-gap-elimination-plan.md). The changes below assume those gaps are resolved (migrations V29, V30; entity/repository updates; `ForecastPersistenceService`, `AnomalyScoringService`, `ModelArtifactService`).

**Implementation status:** The Terraform infrastructure described in this plan has been provisionally implemented under `infra/terraform/`. A review against the actual codebase has identified several issues — ranging from critical runtime blockers to minor improvements — documented in the [Review Findings](#review-findings) section below. The plan has been updated to reflect the current state and incorporate all fixes.

---

## Phase 1: Terraform Foundation and AWS Implementation

### 1.1 Directory Structure

```
infra/
├── terraform/
│   ├── versions.tf                              # Provider version pins
│   ├── modules/
│   │   ├── networking/                          # VPC, subnets, security groups
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── storage/                             # S3/GCS/Blob + persistent disk + IAM
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── compute-spot/                        # Spot instance + user_data bootstrap
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   ├── outputs.tf
│   │   │   └── user-data.tftpl                  # Cloud-init template
│   │   ├── container-registry/                  # ECR/GCR/ACR repos
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── orchestrator/                        # Lambda/Functions + EventBridge/Timer + API GW
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   ├── outputs.tf
│   │   │   ├── src/
│   │   │   │   ├── forecast_trigger.py          # Lambda: create spot + start pipeline
│   │   │   │   └── forecast_status.py           # Lambda: query task status from S3
│   │   │   └── iam.tf                           # Least-privilege policies
│   │   └── monitoring/                          # CloudWatch alerts + dashboard
│   │       ├── variables.tf
│   │       ├── main.tf
│   │       └── outputs.tf
│   ├── environments/
│   │   ├── aws/
│   │   │   ├── backend.tf                       # S3 + DynamoDB remote state
│   │   │   ├── main.tf                          # Module composition
│   │   │   ├── variables.tf
│   │   │   ├── outputs.tf
│   │   │   ├── terraform.tfvars.example
│   │   │   └── providers.tf
│   │   ├── gcp/
│   │   │   ├── backend.tf                       # GCS remote state
│   │   │   ├── main.tf
│   │   │   ├── variables.tf
│   │   │   ├── outputs.tf
│   │   │   ├── terraform.tfvars.example
│   │   │   └── providers.tf
│   │   └── azure/
│   │       ├── backend.tf                       # Azure Blob remote state
│   │       ├── main.tf
│   │       ├── variables.tf
│   │       ├── outputs.tf
│   │       ├── terraform.tfvars.example
│   │       └── providers.tf
│   └── shared/
│       ├── docker-compose.forecast.yml           # Minimal compose (no dashboard/landing/jaeger)
│       └── scripts/
│           ├── forecast-task.sh                  # Main pipeline orchestration
│           ├── health-check.sh                   # Wait for all services healthy
│           ├── collect-results.sh                # API calls + pg_dump + upload
│           └── shutdown.sh                       # Graceful stop + unmount
```

### 1.2 `docker-compose.forecast.yml`

Minimal compose derived from `docker-compose.yml` — only backend, analytics-worker, and timescaledb. Images reference a container registry. Tracing disabled. TimescaleDB volume maps to a host path for EBS/disk mount. Explicit healthchecks on all three services.

### 1.3 `shared/scripts/forecast-task.sh` (user_data)

The main bootstrap script injected as cloud-init. Orchestrates the full lifecycle:

1. Install Docker + cloud CLI (aws/gcloud/az)
2. Format and mount persistent disk at `/mnt/timescaledb` (idempotent — skip if already formatted)
3. Login to container registry, pull images
4. `docker compose up -d` using `docker-compose.forecast.yml`
5. Wait for all three services healthy (delegated to `health-check.sh`)
6. Wait for data ingestion — poll `rate_snapshots` row count via `docker exec` psql until >= 50 rows or 10-min timeout
7. **Model warm-start**: Check `model_artifacts` table for active models matching the current data hash. If found, skip retraining — the `ModelArtifactService` will serve cached GARCH parameters and autoencoder weights. Otherwise, trigger training endpoints and persist results via `ForecastPersistenceService`.
8. Call forecast pipeline endpoints to trigger KPI/signal computation, anomaly scoring (`AnomalyScoringService`), and regime detection
9. Run `collect-results.sh` — curl all relevant API endpoints, `pg_dump` (including `volatility_forecasts`, `model_artifacts`, anomaly-scored `ili_history`/`rate_snapshots`), tar, upload to S3/GCS/Blob
10. Run `shutdown.sh` — stop containers, sync, unmount disk
11. Self-terminate the spot instance (if `AUTO_TERMINATE=true`)

### 1.4 `compute-spot/user-data.tftpl`

Terraform template that generates the cloud-init script. Injects variables: registry URL, image tag, DB password, bucket name, region, task ID, API keys (via SSM Parameter Store / Secrets Manager / cloud-native equivalent).

### 1.5 AWS Module Details

**`modules/networking/main.tf`**: `aws_vpc`, `aws_internet_gateway`, `aws_subnet` (public), `aws_route_table`, `aws_security_group` (spot: SSH in, all egress; Lambda: egress to VPC only).

**`modules/storage/main.tf`**: `aws_s3_bucket` (results, 30-day lifecycle), `aws_ebs_volume` (gp3, tagged `Persistent=true` for reattachment), IAM policies for S3 write and EBS attach.

**`modules/compute-spot/main.tf`**: `aws_key_pair`, `aws_iam_role` (SSM + S3 + EBS), `aws_iam_instance_profile`, `aws_spot_instance_request` (one-time, `interruption_behavior=stop`, `valid_until` +4h), `aws_volume_attachment`, `null_resource` for script upload.

**`modules/container-registry/main.tf`**: Two `aws_ecr_repository` (backend, analytics), lifecycle policy (keep last 10).

**`modules/orchestrator/main.tf`**: Two Lambda functions (trigger + status), EventBridge schedule rule, API Gateway v2 (HTTP API, POST /forecast + GET /forecast/{taskId}), CloudWatch Event Rule for spot interruption warning → graceful shutdown Lambda, IAM roles with least-privilege policies.

**`modules/monitoring/main.tf`**: CloudWatch metric alarms (spot interruption, high CPU), log group (14-day retention), SNS topic for alerts, CloudWatch dashboard.

### 1.6 GCP Module Mapping

| AWS Resource | GCP Equivalent |
|---|---|
| `aws_spot_instance_request` | `google_compute_instance` with `scheduling.preemptible=true` |
| `aws_ebs_volume` | `google_compute_disk` (pd-ssd) |
| `aws_ecr_repository` | `google_artifact_registry_repository` (Docker format) |
| `aws_s3_bucket` | `google_storage_bucket` (Standard class, 30-day lifecycle) |
| `aws_lambda_function` | `google_cloudfunctions2_function` (python3.12) |
| `aws_cloudwatch_event_rule` | `google_cloud_scheduler_job` (cron) |
| `aws_apigatewayv2_api` | `google_api_gateway_api` + `google_api_gateway_gateway` |
| Spot interruption warning | Log-based metric on `compute.instances.preempted` → Pub/Sub → Cloud Function |

### 1.7 Azure Module Mapping

| AWS Resource | Azure Equivalent |
|---|---|
| `aws_spot_instance_request` | `azurerm_linux_virtual_machine` with `priority="Spot"`, `eviction_policy="Deallocate"` |
| `aws_ebs_volume` | `azurerm_managed_disk` (Premium SSD) |
| `aws_ecr_repository` | `azurerm_container_registry` (Basic SKU) |
| `aws_s3_bucket` | `azurerm_storage_container` (Blob, hot tier, 30-day lifecycle) |
| `aws_lambda_function` | `azurerm_linux_function_app` (python, Flex Consumption) |
| `aws_cloudwatch_event_rule` | `azurerm_function_app_function` with `timer_trigger` binding |
| `aws_apigatewayv2_api` | `azurerm_api_management_api` (Consumption tier) |
| Spot eviction notice | Azure Monitor metric alert on `VM Eviction` event → webhook to Function |

---

## Phase 2: Application Graceful Shutdown Enhancements

### 2.1 Java Backend — 3 changes

**`app/src/main/resources/application.yml`** — add under `server:`:
```yaml
server:
  port: 8080
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s
```

**New file: `computation/src/main/java/com/tickonomics/computation/lifecycle/GracefulShutdown.java`**
- `@Component` implementing `DisposableBean`
- Sets `AtomicBoolean shuttingDown = true` on destroy
- Flushes `TimescaleDbWriter` in-memory buffers
- All `@Scheduled` methods gate on `shuttingDown` flag

**`app/src/main/java/com/tickonomics/TickonomicsApplication.java`** — add `@EnableScheduling` annotation (currently implicit via auto-config; make explicit for clarity).

### 2.2 Python Analytics Worker — 2 changes

**`analytics/app/main.py`** — add SIGTERM handler:
```python
import signal
shutdown_requested = False
def handle_sigterm(signum, frame):
    global shutdown_requested
    shutdown_requested = True
signal.signal(signal.SIGTERM, handle_sigterm)
```

**`analytics/Dockerfile`** — change CMD to add graceful shutdown timeout:
```
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001", "--timeout-graceful-shutdown", "30"]
```

---

## Phase 3: CI/CD Integration

### New file: `.github/workflows/forecast-deploy.yml`

1. Build Docker images (backend + analytics) and push to ECR/GCR/ACR
2. Run `terraform fmt -check`, `terraform validate`, `tflint`
3. On PR: `terraform plan`
4. On merge to main: `terraform apply -auto-approve`
5. Post-apply: trigger forecast smoke test

---

## Phase 4: CLI One-Shot Script

### New file: `infra/scripts/run-forecast.sh`

Interactive CLI script for manual runs:
1. Parse args: `--provider aws|gcp|azure`, `--symbol AAPL`, `--tag latest`
2. `terraform -chdir=infra/terraform/environments/{provider} apply -target=module.compute-spot -var="task_id=$(uuidgen)" -auto-approve`
3. Wait for instance running, SSH to monitor progress
4. Download results from S3/GCS/Blob to local `./forecast-results/`
5. `terraform -chdir=infra/terraform/environments/{provider} destroy -target=module.compute-spot -auto-approve`

---

## Phase 5: Local Server Deployment

For environments where a local server (bare-metal or homelab) is available, the entire forecast pipeline can run without any cloud infrastructure — no Terraform, no spot instances, no S3, no Lambda. The pipeline collapses to Docker Compose plus four shell scripts that already exist.

### 5.1 When to Use Local vs. Cloud

| Factor | Local Server | Cloud Spot (Plan 13 Phases 1–4) |
|:-------|:-------------|:-------------------------------|
| Server available 24/7? | Yes — run locally | No — use spot instances |
| Public API access needed? | No — results stay local | Yes — API Gateway trigger |
| Cost sensitivity | Electricity only (~$5–10/mo) | ~$0.37/run × 20 runs = ~$7/mo |
| Data residency | On-premises, full control | Cloud provider region |
| Startup time | Seconds (Docker already installed) | ~5 min (provision + bootstrap) |
| Suitable hardware | 8+ cores, 16+ GB RAM, SSD | Any (cloud provides the hardware) |

### 5.2 Directory Structure

```
infra/
├── local/
│   ├── scripts/
│   │   ├── forecast-local.sh              # Local forecast runner
│   │   ├── backup-forecast-results.sh     # Rotate local result archives
│   │   └── setup-forecast-cron.sh         # Install crontab entry
│   ├── docker-compose.forecast.local.yml  # Forecast compose (local image refs)
│   ├── .env.forecast.example              # Template for local secrets
│   └── crontab                            # Scheduled forecast entries
```

### 5.3 `docker-compose.forecast.local.yml`

Same service definitions as `shared/docker-compose.forecast.yml` but with key differences:

```yaml
services:
  backend:
    image: tickonomics-backend:latest       # Local build, no registry
    # ... same config as forecast compose ...
    volumes:
      - ../monitoring:/opt/tickonomics/monitoring  # Optional: shared config

  analytics-worker:
    image: tickonomics-analytics:latest     # Local build, no registry

  timescaledb:
    image: timescale/timescaledb:latest-pg16
    volumes:
      - timescaledb_forecast_data:/var/lib/postgresql/data
      - ./forecast-results:/forecast-results  # Mount for local result collection
```

Key differences from cloud version:
- **No registry login** — images are built locally (`docker build`) or loaded from archive
- **No cloud CLI installation** — no `aws`, `gcloud`, or `az` in bootstrap
- **No disk mount step** — uses Docker named volumes or a host bind mount
- **Result path** — `./forecast-results/` on host filesystem instead of S3 upload
- **No self-termination** — containers simply stop

### 5.4 `forecast-local.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

TASK_ID="$(uuidgen)"
RESULTS_DIR="${RESULTS_DIR:-./forecast-results}/${TASK_ID}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.forecast.local.yml}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

log() { echo "[$(date -Iseconds)] [${TASK_ID:0:8}] $*"; }

cleanup() {
  EXIT_CODE=$?
  if [ "$EXIT_CODE" -ne 0 ]; then
    log "FAILED (exit ${EXIT_CODE})"
    echo "FAILED" > "${RESULTS_DIR}/STATUS"
  fi
  log "Stopping containers..."
  docker compose -f "${COMPOSE_FILE}" down --timeout 60 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "${RESULTS_DIR}"
echo "RUNNING" > "${RESULTS_DIR}/STATUS"

# Phase 1: Start services
log "Starting forecast services..."
docker compose -f "${COMPOSE_FILE}" up -d

# Phase 2: Wait for healthy
log "Waiting for services healthy..."
for i in $(seq 1 60); do
  if docker compose -f "${COMPOSE_FILE}" exec -T backend \
       curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    log "Backend healthy"
    break
  fi
  [ "$i" -eq 60 ] && { log "FATAL: Backend health timeout"; exit 1; }
  sleep 5
done

# Phase 3: Wait for data ingestion
log "Waiting for data ingestion..."
MIN_ROWS="${MIN_ROWS:-50}"
for i in $(seq 1 120); do
  ROW_COUNT=$(docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
    psql -U tickonomics -t -c "SELECT COUNT(*) FROM rate_snapshots" 2>/dev/null | xargs || echo "0")
  if [ "$ROW_COUNT" -ge "$MIN_ROWS" ]; then
    log "Ingestion ready: ${ROW_COUNT} rows"
    break
  fi
  [ "$i" -eq 120 ] && {
    log "FATAL: Only ${ROW_COUNT} rows (minimum: ${MIN_ROWS})"
    exit 1
  }
  sleep 5
done

# Phase 4: Trigger computation pipeline
log "Triggering computation pipeline..."
COMPUTE_BASE="http://localhost:8080"
ANALYTICS_BASE="http://localhost:8001"

curl -sf -X POST "${COMPUTE_BASE}/api/v1/quant/kpis/compute"       || log "WARNING: KPI compute failed"
curl -sf -X POST "${COMPUTE_BASE}/api/v1/quant/signals/generate"   || log "WARNING: Signal generation failed"
curl -sf -X POST "${ANALYTICS_BASE}/api/v1/analytics/volatility-forecast" || log "WARNING: Forecast failed"

# Phase 5: Collect results
log "Collecting results..."
collect_endpoint() {
  local name="$1" url="$2"
  local outfile="${RESULTS_DIR}/${name}.json"
  HTTP_CODE=$(curl -sf -o "${outfile}" -w "%{http_code}" "${url}" 2>/dev/null) || {
    echo "{\"error\": \"endpoint unreachable\", \"url\": \"${url}\"}" > "${outfile}"
    log "WARNING: ${name} collection failed"
  }
}

collect_endpoint "signals"            "${COMPUTE_BASE}/api/v1/quant/signals/active"
collect_endpoint "strategies"         "${COMPUTE_BASE}/api/v1/quant/strategies/active"
collect_endpoint "volatility-forecast" "${ANALYTICS_BASE}/api/v1/analytics/volatility-forecast"
collect_endpoint "risk-summary"       "${COMPUTE_BASE}/api/v1/quant/risk/tail-parameters"
collect_endpoint "kpis"               "${COMPUTE_BASE}/api/v1/quant/kpis"

# pg_dump
docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
  pg_dump -U tickonomics > "${RESULTS_DIR}/pg_dump.sql" 2>/dev/null || \
  log "WARNING: pg_dump failed"

# Archive
tar -czf "${RESULTS_DIR}/../forecast-${TASK_ID:0:8}.tar.gz" -C "${RESULTS_DIR}/.." "${TASK_ID}" 2>/dev/null || true

# Phase 6: Done
log "COMPLETED"
echo "COMPLETED" > "${RESULTS_DIR}/STATUS"
```

### 5.5 Scheduling — Crontab

```crontab
# /etc/cron.d/tickonomics-forecast
# Run forecast pipeline weekdays at 06:00
0 6 * * 1-5 tickonomics /opt/tickonomics/infra/local/scripts/forecast-local.sh >> /var/log/tickonomics-forecast.log 2>&1
```

**`setup-forecast-cron.sh`:**
```bash
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cp "${SCRIPT_DIR}/crontab" /etc/cron.d/tickonomics-forecast
chmod 644 /etc/cron.d/tickonomics-forecast
echo "Crontab installed: weekdays at 06:00"
```

### 5.6 Local Forecast Results Management

**`backup-forecast-results.sh`:**
```bash
#!/usr/bin/env bash
RESULTS_DIR="${RESULTS_DIR:-./forecast-results}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

# Remove results older than RETENTION_DAYS
find "${RESULTS_DIR}" -name "forecast-*.tar.gz" -mtime +${RETENTION_DAYS} -delete

# Remove STATUS files from completed runs older than retention
find "${RESULTS_DIR}" -name "STATUS" -mtime +${RETENTION_DAYS} -exec rm -f {} \;

echo "Cleaned forecast results older than ${RETENTION_DAYS} days"
```

Add to crontab:
```crontab
# Cleanup old forecast results daily at 05:00
0 5 * * * tickonomics /opt/tickonomics/infra/local/scripts/backup-forecast-results.sh >> /var/log/tickonomics-forecast.log 2>&1
```

### 5.7 Status Tracking — Local

The cloud version uses S3 STATUS files queried by a Lambda. Locally, status is a plain file:

```bash
# Check status of latest run
LATEST=$(ls -td ./forecast-results/*/ 2>/dev/null | head -1)
cat "${LATEST}/STATUS"
# Output: RUNNING | COMPLETED | FAILED

# List recent runs
for d in ./forecast-results/*/; do
  echo "$(basename "$d"): $(cat "$d/STATUS" 2>/dev/null || echo 'UNKNOWN')"
done
```

### 5.8 Performance Comparison

| Metric | Cloud Spot (`c5.2xlarge`) | Local Server (16-core Zen 5, 128 GB) |
|:-------|:--------------------------|:--------------------------------------|
| Bootstrap time | ~5 min (Docker install + image pull) | ~10 sec (Docker already running) |
| Data ingestion to 50 rows | ~2–5 min (cold DB) | ~2–5 min (same API latency) |
| GARCH + model training | ~10–15 min | ~2–3 min (7–10× CPU advantage) |
| Full forecast pipeline | ~45–60 min | ~8–15 min |
| Result collection + upload | ~2 min (pg_dump + S3 upload) | ~1 min (pg_dump to local disk) |
| Total end-to-end | **~1 hour** | **~10–20 minutes** |
| Cost per run | ~$0.37 | ~$0.01 (electricity) |

### 5.9 Files Created

| File | Purpose |
|:-----|:--------|
| `infra/local/scripts/forecast-local.sh` | Full pipeline runner for local execution |
| `infra/local/scripts/backup-forecast-results.sh` | Rotate old local result archives |
| `infra/local/scripts/setup-forecast-cron.sh` | Install crontab scheduling |
| `infra/local/docker-compose.forecast.local.yml` | Forecast compose with local image refs |
| `infra/local/.env.forecast.example` | Template for local secrets |
| `infra/local/crontab` | Crontab entries for scheduling |

---

## Variable Design

| Variable | Type | Default | Description |
|---|---|---|---|
| `region` | string | `us-east-1` | Cloud region |
| `availability_zone` | string | `us-east-1a` | AZ (must match EBS) |
| `instance_type` | string | `c5.2xlarge` | Spot instance type |
| `spot_price_max` | string | `0.30` | Max bid price (USD/hr) |
| `db_volume_size_gb` | number | `100` | Persistent disk size (must accommodate model artifacts) |
| `image_tag` | string | `latest` | Container image tag |
| `postgres_password` | string (sensitive) | — | TimescaleDB password |
| `polygon_api_key` | string (sensitive) | `""` | Polygon WebSocket key |
| `fred_api_key` | string (sensitive) | `""` | FRED API key |
| `schedule_expression` | string | `cron(0 6 ? * MON-FRI *)` | Trigger schedule |
| `auto_terminate` | bool | `true` | Self-terminate after forecast |
| `results_retention_days` | number | `30` | S3/Blob lifecycle |
| `ssh_public_key` | string | — | SSH public key for spot access |
| `alert_email` | string | `""` | SNS/notification email |

---

## Output Design

| Output | Description |
|---|---|
| `spot_instance_public_ip` | SSH access IP |
| `results_bucket_name` | Where forecast outputs land |
| `api_gateway_endpoint` | `POST /forecast` and `GET /forecast/{id}` URL |
| `ecr_backend_url` | For CI/CD push |
| `ecr_analytics_url` | For CI/CD push |
| `ebs_volume_id` | Persistent disk (survives spot termination) |
| `cloudwatch_dashboard_url` | Monitoring link |

---

## Cost Per 1-Hour Forecast Run

| Provider | Compute (Spot) | Disk (prorated) | Registry | Storage | Network | Lambda/API | **Total** |
|---|---|---|---|---|---|---|---|
| AWS | $0.09 | $0.003 | $0.05/mo | $0.02 | $0.18 | <$0.01 | **~$0.38** |
| GCP | $0.08 | $0.004 | $0.05/mo | $0.02 | $0.15 | $0.00 | **~$0.36** |
| Azure | $0.09 | $0.002 | $0.00 | $0.02 | $0.15 | $0.00 | **~$0.35** |

Monthly (20 weekday runs): ~$7–8/month on any provider.

---

## Implementation Sequence

### Step 1 — Terraform skeleton + AWS networking
Create `infra/terraform/versions.tf`, `modules/networking/`, `environments/aws/providers.tf`, `environments/aws/backend.tf`, bootstrap script for remote state.

### Step 2 — Storage + Container Registry (AWS)
Create `modules/storage/`, `modules/container-registry/`, `environments/aws/main.tf` (partial wiring).

### Step 3 — Forecast compose + scripts
Create `shared/docker-compose.forecast.yml`, all four orchestration scripts, `user-data.tftpl`. Scripts must account for model warm-start (query `model_artifacts` before training) and collect forecast/anomaly results (include `volatility_forecasts`, `model_artifacts`, anomaly-scored rows in `pg_dump`).

### Step 4 — Compute module (AWS)
Create `modules/compute-spot/`, wire into `environments/aws/main.tf`. The EBS volume must be large enough to hold not only TimescaleDB data but also `model_artifacts` (autoencoder state can be several MB per version). Increase default `db_volume_size_gb` to 100 GB. Manual test: `terraform apply`, verify instance boots and pipeline runs end-to-end including model caching.

### Step 5 — Orchestrator module (AWS)
Create `modules/orchestrator/` with Lambda functions, EventBridge rule, API Gateway, spot interruption handler. Test all three trigger modes.

### Step 6 — Monitoring module (AWS)
Create `modules/monitoring/` with CloudWatch alarms, dashboard, SNS alerts.

### Step 7 — Application graceful shutdown
Modify `application.yml`, add `GracefulShutdown.java`, update `main.py` SIGTERM handler, update `analytics/Dockerfile` CMD.

### Step 8 — GCP environment
Create `environments/gcp/` with equivalent modules using GCP provider resources.

### Step 9 — Azure environment
Create `environments/azure/` with equivalent modules using Azure provider resources.

### Step 10 — CI/CD workflow
Create `.github/workflows/forecast-deploy.yml`, CLI one-shot script, add `tflint`/`tfsec` to PR checks.

---

## Verification

1. **Terraform validation**: `terraform fmt -check && terraform validate && tflint` pass for all three environments
2. **AWS smoke test**: `terraform apply` → instance boots → all containers healthy → results uploaded to S3 → instance self-terminates
3. **API Gateway test**: `POST /forecast` returns `task_id` → poll `GET /forecast/{id}` → status reaches COMPLETED → download results from S3
4. **Spot interruption drill**: Simulate termination during forecast → verify graceful shutdown logs in CloudWatch → verify partial results uploaded → verify EBS survives
5. **Multi-provider parity**: Run the same forecast on GCP and Azure, compare results for consistency
6. **Cost validation**: Check AWS Cost Explorer / GCP Billing / Azure Cost Management after 3 test runs — confirm ~$0.35–0.40 per run
7. **Storage gap verification**: After forecast run, confirm: (a) `volatility_forecasts` table has rows with `forecast_vol` populated and `realized_vol` NULL (back-filled later), (b) `ili_history`/`rate_snapshots` rows have `anomaly_score` and `is_suspect_anomaly` populated, (c) `model_artifacts` has active entries for GARCH and autoencoder models
8. **Model warm-start verification**: Run a second forecast immediately after the first → confirm `model_artifacts` cache hit (no retraining) in logs → confirm reduced pipeline execution time
9. **Local forecast verification**: `forecast-local.sh` completes end-to-end on a local server → results in `./forecast-results/` with `STATUS=COMPLETED` → `pg_dump.sql` present → JSON endpoint results non-empty
10. **Local crontab verification**: After `setup-forecast-cron.sh`, next scheduled run completes automatically → result archive appears with correct timestamp

---

## Files Modified/Created Summary

**Created (Terraform — ~35 files)**:
- `infra/terraform/versions.tf`
- `infra/terraform/modules/networking/` (3 files)
- `infra/terraform/modules/storage/` (3 files)
- `infra/terraform/modules/compute-spot/` (4 files including template)
- `infra/terraform/modules/container-registry/` (3 files)
- `infra/terraform/modules/orchestrator/` (6 files including Lambda Python sources)
- `infra/terraform/modules/monitoring/` (3 files)
- `infra/terraform/environments/aws/` (5 files)
- `infra/terraform/environments/gcp/` (5 files)
- `infra/terraform/environments/azure/` (5 files)
- `infra/terraform/shared/docker-compose.forecast.yml`
- `infra/terraform/shared/scripts/` (4 scripts)
- `infra/scripts/run-forecast.sh`
- `.github/workflows/forecast-deploy.yml`

**Created (Local Server — 6 files):**
- `infra/local/scripts/forecast-local.sh`
- `infra/local/scripts/backup-forecast-results.sh`
- `infra/local/scripts/setup-forecast-cron.sh`
- `infra/local/docker-compose.forecast.local.yml`
- `infra/local/.env.forecast.example`
- `infra/local/crontab`

**Modified (Application — 4 files)**:
- `app/src/main/resources/application.yml` — add `server.shutdown: graceful`, `spring.lifecycle.timeout`
- `computation/src/main/java/.../lifecycle/GracefulShutdown.java` — new file
- `analytics/app/main.py` — add SIGTERM handler
- `analytics/Dockerfile` — add `--timeout-graceful-shutdown 30`

---

---

## Review Findings

The following issues were identified by auditing the plan against the actual codebase state. Each issue includes the affected files, root cause, impact, and required fix.

### Critical — Runtime Blockers (pipeline will not complete)

#### C1. `forecast_trigger.py` Lambda is a no-op — never launches spot instances

**Files:** `infra/terraform/modules/orchestrator/src/forecast_trigger.py`
**Impact:** `POST /forecast` via API Gateway returns `{"status": "TRIGGERED"}` but never calls `ec2.request_spot_instances()`. The entire scheduled + API-driven trigger path is non-functional. The IAM policy grants `ec2:RequestSpotInstances` and `ec2:RunInstances`, but the code never uses them.
**Fix:** Implement `ec2.request_spot_instances()` in the handler, passing the subnet ID, security groups, IAM instance profile, user-data, and spot price from Lambda environment variables. Alternatively, use `ec2.run_instances()` with `InstanceMarketOptions` for simpler lifecycle management. The Lambda needs additional environment variables: `SUBNET_ID`, `SECURITY_GROUP_IDS`, `IAM_INSTANCE_PROFILE`, `SPOT_PRICE`, `INSTANCE_TYPE`, `AMI_ID`, `KEY_NAME`, and all forecast config variables that currently go into user-data.

#### C2. Backend healthcheck targets non-existent endpoint and missing `curl`

**Files:** `infra/terraform/modules/compute-spot/cloud-config.yaml.tftpl` (line 50), `infra/terraform/shared/docker-compose.forecast.yml` (referenced in plan)
**Impact:** The healthcheck `curl -sf http://localhost:8080/actuator/health` fails for two independent reasons: (1) Spring Boot Actuator is not on the classpath — no `/actuator/*` endpoints exist; the actual health endpoint is `GET /health` via a custom `HealthController`. (2) The backend Docker image (`eclipse-temurin:25-jre` base) does not contain `curl`. The container will never reach `healthy` status, so services that `depends_on` backend with `condition: service_healthy` (none currently in the forecast compose, but the healthcheck still fails and the monitoring pipeline cannot detect readiness).
**Fix:**
1. Change the healthcheck URL to `http://localhost:8080/health`.
2. Either install `curl` in the backend Dockerfile (`RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*`) or switch to a Java-native health probe (e.g., `CMD ["java", "-cp", "/app.jar", "org.springframework.boot.loader.launch.JarLauncher", "--health-check"]` or use `wget` which may be available in the JRE base, or use a standalone `curl` static binary).

#### C3. 7 of 8 API endpoints called by the pipeline do not exist

**Files:** `infra/terraform/shared/scripts/forecast-task.sh` (lines 95–99), `infra/terraform/modules/compute-spot/user-data.tftpl` (lines 150–152)
**Impact:** The pipeline calls these endpoints that do not exist in the codebase:
| Called endpoint | Actual endpoint | Status |
|---|---|---|
| `POST /api/v1/kpis/compute` | No KPI controller exists | **Missing** |
| `POST /api/v1/signals/generate` | Only `GET /api/v1/quant/signals/active` | **Wrong path + wrong method** |
| `POST /api/v1/analytics/forecast` | `POST /api/v1/analytics/volatility-forecast` | **Wrong path** |
| `GET /api/v1/kpis` | None | **Missing** |
| `GET /api/v1/signals` | `GET /api/v1/quant/signals/active` | **Wrong path** |
| `GET /api/v1/strategies` | `GET /api/v1/quant/strategies/active` | **Wrong path** |
| `GET /api/v1/risk/summary` | None | **Missing** |

All `curl` calls fail silently (piped to `|| true`), so the pipeline reports success with no computation results. The `collect-results.sh` script writes `{"error": "endpoint unreachable"}` JSON placeholders.
**Fix:** Create a dedicated forecast orchestration controller (or update `forecast-task.sh`) that calls the actual computation services programmatically. The endpoints that exist and should be used are: `POST /api/v1/analytics/volatility-forecast`, `GET /api/v1/quant/signals/active`, `GET /api/v1/quant/strategies/active`, `GET /api/v1/quant/risk/tail-parameters`, `GET /api/v1/quant/risk/evt-tail`. New endpoints needed: `POST /api/v1/quant/kpis/compute` (trigger KPI computation), `POST /api/v1/quant/signals/generate` (trigger signal generation), `GET /api/v1/quant/kpis` (retrieve KPI results), `GET /api/v1/quant/risk/summary` (aggregated risk).

#### C4. `cloud-config.yaml.tftpl` references scripts never deployed to the instance

**Files:** `infra/terraform/modules/compute-spot/cloud-config.yaml.tftpl` (runcmd section), `infra/terraform/modules/compute-spot/main.tf` (cloudinit_config)
**Impact:** The cloud-init `runcmd` tries to execute `bash /opt/tickonomics/scripts/forecast-task.sh`, but the `forecast-task.sh` is delivered as a separate cloud-init shellscript part — it is executed directly by cloud-init, not written to `/opt/tickonomics/scripts/`. The `runcmd` copy command (`cp /opt/tickonomics/scripts/forecast-task.sh /opt/tickonomics/scripts/forecast-task.sh`) is a no-op because the source doesn't exist at that path yet. Furthermore, `forecast-task.sh` calls `health-check.sh`, `collect-results.sh`, and `shutdown.sh` via `${SCRIPTS_DIR}/...`, but these three scripts are never deployed to the instance at all. The pipeline will fail at Phase 5 when it tries to call `health-check.sh`.
**Fix:** Either (a) use `write_files` in cloud-config to write all four scripts to `/opt/tickonomics/scripts/` and remove the separate shellscript part, or (b) embed the full pipeline inline in a single shellscript part that doesn't depend on external scripts. Option (a) is cleaner — use `templatefile()` for each script and add `write_files` entries for all four.

#### C5. Double execution of `forecast-task.sh` causes race conditions

**Files:** `infra/terraform/modules/compute-spot/cloud-config.yaml.tftpl`, `infra/terraform/modules/compute-spot/main.tf`
**Impact:** The cloudinit_config has two parts: a `cloud-config` part whose `runcmd` runs `bash /opt/tickonomics/scripts/forecast-task.sh`, and a separate `text/x-shellscript` part that delivers and executes `forecast-task.sh` directly. Both execute the same script, leading to double Docker install attempts, double `docker compose up`, and potential port conflicts or data corruption.
**Fix:** Remove the shellscript part from `cloudinit_config` and rely solely on the `runcmd` directive, OR remove the `runcmd` entry and use only the shellscript part. Ensure exactly one execution path.

### High — Reliability & Data Integrity Issues

#### H1. Spot interruption Lambda only logs — does not signal graceful shutdown

**Files:** `infra/terraform/modules/orchestrator/src/spot_interruption.py`
**Impact:** AWS provides a 2-minute warning before spot termination. The current Lambda just writes a JSON marker to S3. The running instance is never notified to flush buffers, upload partial results, or gracefully stop containers. The `GracefulShutdown` component and SIGTERM handler exist in the application but nothing triggers them during spot interruption.
**Fix:** The interruption Lambda should use AWS SSM `SendCommand` to run a graceful shutdown script on the instance:
```python
ssm.send_command(
    InstanceIds=[instance_id],
    DocumentName="AWS-RunShellScript",
    Parameters={"commands": [
        "docker compose -f /opt/tickonomics/docker-compose.forecast.yml down --timeout 60",
        "sync",
        "echo 'INTERRUPTED' | aws s3 cp - s3://{bucket}/{task_id}/STATUS"
    ]}
)
```
Alternatively, the instance can poll its own spot interruption notice via IMDS (`http://169.254.169.254/latest/meta-data/spot/instance-action`) every 5 seconds and self-initiate shutdown when detected.

#### H2. No `FAILED` status uploaded on pipeline errors

**Files:** `infra/terraform/shared/scripts/forecast-task.sh`, `infra/terraform/modules/compute-spot/user-data.tftpl`
**Impact:** If any phase fails (e.g., ingestion timeout, compute endpoint unreachable, Docker pull failure), the script either continues with `|| true` or exits via `set -e`. In both cases, no `FAILED` status marker is uploaded to S3. The `forecast_status.py` Lambda keeps returning `RUNNING` indefinitely for that task. The smoke test in CI (`forecast-deploy.yml`) will time out at 60 minutes without a clear failure signal.
**Fix:** Wrap the entire pipeline in a trap:
```bash
cleanup() {
  EXIT_CODE=$?
  if [ "$EXIT_CODE" -ne 0 ]; then
    echo "FAILED" | aws s3 cp - "s3://${RESULTS_BUCKET}/${TASK_ID}/STATUS" --region "$AWS_REGION" || true
    # Upload partial results if available
    if [ -d /opt/tickonomics/results ]; then
      tar -czf "/tmp/forecast-results-${TASK_ID}.tar.gz" -C /opt/tickonomics/results . 2>/dev/null || true
      aws s3 cp "/tmp/forecast-results-${TASK_ID}.tar.gz" "s3://${RESULTS_BUCKET}/${TASK_ID}/forecast-results-partial.tar.gz" --region "$AWS_REGION" || true
    fi
  fi
}
trap cleanup EXIT
```

#### H3. Docker container names hardcoded with wrong project prefix

**Files:** `infra/terraform/shared/scripts/forecast-task.sh` (lines 78, 79), `infra/terraform/shared/scripts/health-check.sh` (lines 11–13), `infra/terraform/shared/scripts/collect-results.sh` (line 43), `infra/terraform/modules/compute-spot/user-data.tftpl` (lines 127–129, 140, 163)
**Impact:** All scripts reference containers like `forecast-backend-1`, `forecast-analytics-worker-1`, `forecast-timescaledb-1`. Docker Compose v2 generates names as `{project}-{service}-{number}`, where `{project}` is the basename of the directory containing the compose file. If the compose file is at `/opt/tickonomics/docker-compose.forecast.yml`, the project name is `tickonomics` (from the working directory), producing `tickonomics-backend-1`, not `forecast-backend-1`. All `docker inspect`, `docker exec`, and `docker compose` commands targeting specific containers will fail.
**Fix:** Either (a) set `COMPOSE_PROJECT_NAME=forecast` in the environment before running `docker compose`, or (b) use `docker compose -f ... ps --format json` to dynamically discover container names, or (c) use `docker exec` with service names via `docker compose exec` instead of raw container names.

#### H4. Disk device selection is unreliable

**Files:** `infra/terraform/shared/scripts/forecast-task.sh` (line 44), `infra/terraform/modules/compute-spot/user-data.tftpl` (line 110)
**Impact:** `DEVICE=$(lsblk -dnpo NAME,SIZE | sort -k2 -h | tail -1 | awk '{print $1}')` selects the largest block device. This is fragile — it could select the root volume on some instance types, or pick a wrong device if the EBS volume has a different size than expected. The `user-data.tftpl` version tries to exclude `/dev/sda` but uses string matching that breaks on NVMe devices (`/dev/nvme0n1` vs `/dev/sda`).
**Fix:** Use the explicit device name from `aws_volume_attachment` (`/dev/sdf`). On modern AWS instances with NVMe, the device appears as `/dev/nvme1n1` (or similar). Use a more reliable approach:
```bash
# Wait for the specific device to appear
DEVICE="/dev/disk/by-id/*timescaledb*"
# Or use the EBS volume ID via udev symlink:
DEVICE=$(lsblk -dnpo NAME,MOUNTPOINT | grep -v " /$" | grep -v "^$" | head -1 | awk '{print $1}')
# Or simply: pass the device name as an environment variable from Terraform
```

#### H5. Lambda trigger placed in VPC without NAT Gateway or VPC endpoints

**Files:** `infra/terraform/modules/orchestrator/main.tf` (lines 56–59), `infra/terraform/modules/networking/main.tf`
**Impact:** `forecast_trigger` Lambda is configured with `vpc_config` pointing to the public subnet. Lambda functions in a VPC do NOT get public IPs — they only get private ENIs. The public subnet has an IGW route, but without a public IP, the Lambda cannot route outbound traffic. The Lambda cannot reach EC2 API (needed to launch spot instances), S3, or any other AWS service. It will fail on any outbound call.
**Fix:** Either (a) remove `vpc_config` from the trigger Lambda entirely (it doesn't need VPC access — it calls public AWS APIs), or (b) add a NAT Gateway + private subnet for Lambda placement, or (c) add VPC endpoints for EC2 and S3. Option (a) is simplest and most cost-effective.

#### H6. Monitoring module references transient `spot_instance_id`

**Files:** `infra/terraform/environments/aws/main.tf` (line 72), `infra/terraform/modules/monitoring/main.tf` (lines 49, 97)
**Impact:** The monitoring module receives `spot_instance_id = module.compute_spot.instance_id` for CloudWatch dashboard and CPU alarm. Spot instances are transient — after termination, `instance_id` becomes stale. The CloudWatch alarm for `CPUUtilization` will perpetually show `INSUFFICIENT_DATA` for a non-existent instance. On the next `terraform apply`, the alarm updates to the new instance ID, but between runs, monitoring is blind.
**Fix:** Remove the instance-ID-specific CPU alarm from Terraform (manage it dynamically via the orchestration Lambda when a spot instance launches) or use a CloudWatch metric math expression that finds the instance by tag (`Name = tickonomics-spot-forecast`) rather than by ID.

#### H7. `spot_type = "one-time"` contradicts plan's `interruption_behavior=stop`

**Files:** `infra/terraform/modules/compute-spot/main.tf` (line 122), plan Phase 1.5
**Impact:** The plan says `interruption_behavior=stop` but the code uses `spot_type = "one-time"` (which terminates the instance on interruption or completion). With `one-time`, the spot request is consumed and the instance cannot restart. The EBS volume persists (due to `Persistent=true` tag) but must be re-attached to a new instance. The `interruption_behavior` attribute is not set at all (defaults to `terminate`).
**Fix:** Decide on a strategy:
- **Option A (recommended):** Keep `one-time` + `terminate`. Each forecast run creates a fresh spot request. Simple and clean. Remove the `interruption_behavior=stop` from the plan.
- **Option B:** Use `persistent` spot type with `instance_interruption_behavior = "stop"`. The instance stops on interruption and can be restarted. More complex state management but preserves the running instance.

### Medium — Operational & Correctness Issues

#### M1. Phase 2 (Graceful Shutdown) is already fully implemented

**Files:** Plan Phase 2, actual codebase
**Impact:** All four changes listed in Phase 2 already exist in the codebase:
- `application.yml` already has `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 60s`
- `GracefulShutdown.java` already exists at `computation/.../lifecycle/GracefulShutdown.java`
- `analytics/app/main.py` already has the SIGTERM handler with `shutdown_requested` flag
- `analytics/Dockerfile` already has `--timeout-graceful-shutdown 30`
- `TickonomicsApplication.java` already has `@EnableScheduling`

**Fix:** Mark Phase 2 as **COMPLETED** in the plan. Remove it from the implementation sequence.

#### M2. V29/V30 storage gaps already resolved

**Files:** Plan "Storage Gap Prerequisites" section, actual codebase
**Impact:** The plan lists 18 files (12 new + 6 modified) as prerequisites, but all of them already exist in the codebase:
- `V29__add_volatility_forecasts.sql` and `V30__add_model_artifacts.sql` are present
- `VolatilityForecast.java`, `ModelArtifact.java` entities exist
- Corresponding repositories and tests exist
- `ForecastPersistenceService`, `AnomalyScoringService`, `ModelArtifactService` exist
- `IliHistory.java` and `RateSnapshot.java` already have `anomalyScore` and `isSuspectAnomaly` fields
**Fix:** Mark the storage gap prerequisites as **RESOLVED** and remove from the implementation sequence.

#### M3. `user-data.tftpl` is dead code — not referenced in `main.tf`

**Files:** `infra/terraform/modules/compute-spot/user-data.tftpl`, `infra/terraform/modules/compute-spot/main.tf`
**Impact:** `main.tf` uses `data.cloudinit_config.forecast` which combines `cloud-config.yaml.tftpl` + `forecast-task.sh`. The `user-data.tftpl` file exists but is never referenced — it duplicates the same logic inline (Docker install, disk mount, compose up, health check, API calls, result collection, shutdown). This creates confusion about which is the authoritative script and could lead to divergent fixes being applied to the wrong file.
**Fix:** Delete `user-data.tftpl`. The authoritative flow is `cloud-config.yaml.tftpl` (writes config + compose) + `forecast-task.sh` (pipeline execution). Add a comment in the module directory explaining the cloud-init architecture.

#### M4. No data validation before computation — pipeline proceeds with zero rows

**Files:** `infra/terraform/shared/scripts/forecast-task.sh` (lines 76–91)
**Impact:** The ingestion wait has a 10-minute timeout. If it expires with 0 rows (e.g., API keys invalid, Polygon API down, market closed), the script logs `WARNING: Ingestion timeout reached, proceeding with available data` and continues to computation. Running forecasts on empty data produces meaningless results that get uploaded as if they were valid. The `|| true` on every `curl` call means even if computation fails, the pipeline reports `COMPLETED`.
**Fix:** Make the ingestion threshold configurable and enforce a minimum row count:
```bash
MIN_ROWS="${MIN_ROWS:-50}"
# ... after timeout ...
if [ "$ROW_COUNT" -lt "$MIN_ROWS" ]; then
  log "FATAL: Only ${ROW_COUNT} rows available (minimum: ${MIN_ROWS}). Aborting."
  echo "FAILED" | aws s3 cp - "s3://${RESULTS_BUCKET}/${TASK_ID}/STATUS" --region "$AWS_REGION"
  exit 1
fi
```

#### M5. `tflint` and `tfsec` mentioned but never integrated

**Files:** Plan Step 10, `.github/workflows/forecast-deploy.yml`
**Impact:** The plan says "add `tflint`/`tfsec` to PR checks" and Step 10 mentions them, but the CI workflow only runs `terraform fmt -check` and `terraform validate`. No linting or security scanning is performed on the Terraform code.
**Fix:** Add steps to `terraform-validate` job in `forecast-deploy.yml`:
```yaml
- name: Setup tflint
  uses: terraform-linters/setup-tflint@v4
- name: Run tflint
  run: tflint --recursive infra/terraform/
- name: Run tfsec
  uses: aquasecurity/tfsec-action@v1.0.0
```

#### M6. Secrets stored in Terraform state as plaintext

**Files:** `infra/terraform/modules/compute-spot/variables.tf`, `infra/terraform/modules/compute-spot/cloud-config.yaml.tftpl`
**Impact:** `postgres_password`, `polygon_api_key`, and `fred_api_key` are passed as Terraform variables, rendered into the cloud-init template, and stored in the S3-backed Terraform state file. The cloud-init `write_files` section writes them to `/etc/tickonomics/config.env` with mode `0600`, but they're also visible in the Terraform state (S3), the EC2 console (user-data), and CloudWatch logs (if the bootstrap script logs environment variables).
**Fix:** Use AWS Secrets Manager or SSM Parameter Store for secrets:
1. Store secrets in SSM Parameter Store (SecureString type, KMS-encrypted)
2. Pass only parameter names to the instance via user-data
3. In `forecast-task.sh`, retrieve secrets: `aws ssm get-parameter --name "/tickonomics/db-password" --with-decryption --query Parameter.Value --output text`
4. Update IAM role to include `ssm:GetParameter` for the specific parameter paths

#### M7. No fallback strategy for spot request non-fulfillment

**Files:** `infra/terraform/modules/compute-spot/main.tf` (line 122)
**Impact:** `wait_for_fulfillment = true` means Terraform blocks until the spot request is fulfilled. If spot capacity is unavailable at the bid price (common during peak hours), `terraform apply` will hang for up to the `valid_until` time (4 hours), then fail. There's no fallback to on-demand instances, different instance types, or different AZs.
**Fix:** Add fallback logic in the orchestration Lambda:
1. Try spot at the primary instance type and AZ
2. If unfulfilled after 5 minutes, try an alternative instance type (e.g., `c5a.2xlarge`, `m5.2xlarge`)
3. If still unfulfilled after 10 minutes, fall back to on-demand (at higher cost)
4. Log the fallback for cost tracking

#### M8. `valid_until` evaluated at plan time, not apply time

**Files:** `infra/terraform/modules/compute-spot/main.tf` (line 124)
**Impact:** `timeadd(timestamp(), "4h")` is evaluated when Terraform runs, not when the spot instance starts. If there's a delay between plan and apply (e.g., Atlantis review/approval cycle), the `valid_until` could be stale, potentially causing the spot request to be rejected immediately.
**Fix:** Use `time_rotating` resource or calculate `valid_until` relative to apply time. Alternatively, increase the window to account for approval delays (e.g., `+6h` instead of `+4h`).

#### M9. GCP and Azure use the same non-functional `forecast_trigger.py`

**Files:** `infra/terraform/environments/gcp/main.tf` (line 184), `infra/terraform/environments/azure/main.tf`
**Impact:** The GCP and Azure environments reuse `forecast_trigger.py` from the orchestrator module. This Lambda/Function has the same no-op issue as C1 — it never actually launches the spot instance. The GCP Cloud Function and Azure Function App both run this code, which just returns `{"status": "TRIGGERED"}` without performing any cloud-specific instance launch.
**Fix:** Implement provider-specific trigger functions that call the respective cloud APIs:
- GCP: `google.cloud.compute_v1.InstancesClient().insert()` with preemptible scheduling
- Azure: Use Azure SDK `ComputeManagementClient.virtual_machines.begin_create_or_update()` with spot priority

#### M10. Azure `forecast-task.sh` never uploaded to the VM

**Files:** `infra/terraform/environments/azure/main.tf` (lines 205–218)
**Impact:** The `azurerm_virtual_machine_extension` runs `bash /opt/tickonomics/scripts/forecast-task.sh`, but nothing deploys the script to `/opt/tickonomics/scripts/` on the Azure VM. The VM extension's `fileUris` is empty (`fileUris = []`). Unlike AWS cloud-init which delivers the script inline, the Azure extension expects a pre-existing file or a downloadable URI. The extension will fail.
**Fix:** Upload `forecast-task.sh` to a storage container and reference it in `fileUris`, or use `protected_settings` with a `script` field to pass the content inline, or use `custom_data` (cloud-init) like AWS.

### Low — Improvements & Housekeeping

#### L1. Cost estimates may be inaccurate

**Impact:** The plan claims $0.35–0.38/run, but:
- `c5.2xlarge` spot prices vary by region and can spike above $0.30/hr
- The $0.18 "Network" cost for AWS seems high for a single forecast run (likely includes assumptions about data transfer volume)
- No accounting for the 5–10 minute bootstrap overhead (Docker install + image pull) on each run
- The 50-row ingestion threshold completes in seconds on a warm DB, but a cold start with empty TimescaleDB requires real-time data fetching from Polygon API which may take much longer
**Fix:** Add a note that costs are estimates and recommend monitoring actual costs via AWS Cost Explorer after the first 10 runs. Consider using `c5.xlarge` (half the cost) if memory pressure permits.

#### L2. S3 bucket name may collide globally

**Files:** `infra/terraform/modules/storage/main.tf` (line 2)
**Impact:** `bucket = "${var.name_prefix}-forecast-results"` — S3 bucket names must be globally unique across all AWS accounts. If `name_prefix` defaults to something generic (e.g., `tickonomics`), the bucket creation will fail.
**Fix:** Append a random suffix: `bucket = "${var.name_prefix}-forecast-results-${random_id.bucket_suffix.hex}"`.

#### L3. `forecast-task.sh` doesn't implement model warm-start

**Files:** `infra/terraform/shared/scripts/forecast-task.sh`, Plan Phase 1.3 Step 7
**Impact:** The plan describes a model warm-start check (query `model_artifacts` for active models matching data hash, skip retraining if found), but `forecast-task.sh` doesn't implement this logic. It proceeds directly to calling computation endpoints. This means every run retrains models from scratch, adding 5–15 minutes to the pipeline.
**Fix:** Add a warm-start check phase between ingestion wait and computation trigger:
```bash
log "Phase 7a: Checking model warm-start..."
ACTIVE_MODELS=$(docker exec forecast-timescaledb-1 psql -U tickonomics -t -c \
  "SELECT COUNT(*) FROM model_artifacts WHERE is_active = true" 2>/dev/null | xargs || echo "0")
if [ "$ACTIVE_MODELS" -gt 0 ]; then
  log "Found ${ACTIVE_MODELS} active model(s), skipping retraining"
else
  log "No active models found, training from scratch"
fi
```
Then pass a flag to the computation endpoints to skip or perform training.

#### L4. No structured logging or task correlation

**Files:** All orchestration scripts
**Impact:** Logs go to `/var/log/forecast-task.log` on the instance (lost on termination) and to `stdout` in cloud-init. There's no integration with CloudWatch Logs from the instance side. If the instance terminates before uploading results, there's no way to diagnose what went wrong.
**Fix:** Install the CloudWatch Logs agent on the spot instance and stream `/var/log/forecast-task.log` to a CloudWatch log group keyed by `TASK_ID`. Alternatively, log to S3 alongside results.

#### L5. `collect-results.sh` doesn't collect forecast/anomaly-specific endpoints

**Files:** `infra/terraform/shared/scripts/collect-results.sh`
**Impact:** The script collects generic endpoints (kpis, signals, strategies) but doesn't collect forecast-specific results like `GET /api/v1/analytics/volatility-forecast`, anomaly scores, or model artifact metadata. The `pg_dump` captures the database state, but the JSON result files don't include the most important outputs.
**Fix:** Add forecast-specific endpoint collections:
```bash
collect_endpoint "volatility-forecast" "${ANALYTICS_BASE}/api/v1/analytics/volatility-forecast"
collect_endpoint "anomaly-report"      "${ANALYTICS_BASE}/api/v1/anomaly"
collect_endpoint "regime-report"       "${ANALYTICS_BASE}/api/v1/regime"
```

#### L6. GCP and Azure `run-forecast.sh` commands are AWS-specific

**Files:** `infra/scripts/run-forecast.sh` (lines 130–140)
**Impact:** The CLI one-shot script has provider-specific S3/GS/Az commands for downloading results, but the `terraform apply` command only targets `module.compute_spot` (AWS module name). For GCP and Azure, all resources are in a single flat `main.tf`, so the `-target` flag doesn't apply correctly.
**Fix:** Make the Terraform target configurable per provider, or use `terraform apply` without `-target` for GCP/Azure (which deploy everything in one apply).

---

## Updated Implementation Sequence

### Step 0 — Mark completed items
The following are already implemented and can be skipped:
- ✅ V29/V30 storage gap migrations and entities
- ✅ Phase 2: Graceful Shutdown (all 4 files)
- ✅ `@EnableScheduling` on `TickonomicsApplication`
- ✅ Terraform skeleton and all modules (AWS, GCP, Azure)
- ✅ CI workflow (`forecast-deploy.yml`)
- ✅ CLI one-shot script (`run-forecast.sh`)

### Step 1 — Fix backend healthcheck (C2)
1. Add `spring-boot-starter-actuator` to `app/build.gradle` (or install `curl` in Dockerfile)
2. Update all healthcheck URLs from `/actuator/health` to `/health` (or add Actuator)
3. Verify in local Docker that the healthcheck passes

### Step 2 — Create missing API endpoints (C3)
1. Create `POST /api/v1/quant/kpis/compute` — triggers KPI computation
2. Create `POST /api/v1/quant/signals/generate` — triggers signal generation
3. Create `GET /api/v1/quant/kpis` — returns KPI results
4. Create `GET /api/v1/quant/risk/summary` — returns aggregated risk
5. Update `forecast-task.sh` and `collect-results.sh` to use correct endpoint paths

### Step 3 — Fix cloud-init script deployment (C4, C5)
1. Rewrite `cloud-config.yaml.tftpl` to deploy all four scripts via `write_files`
2. Remove the duplicate shellscript part from `cloudinit_config`
3. Set `COMPOSE_PROJECT_NAME=forecast` to fix container naming (H3)
4. Delete `user-data.tftpl` (dead code — M3)
5. Fix disk device selection to use explicit device name (H4)

### Step 4 — Implement trigger Lambda (C1)
1. Implement `ec2.request_spot_instances()` or `ec2.run_instances()` in `forecast_trigger.py`
2. Add required environment variables to the Lambda configuration
3. Remove `vpc_config` from trigger Lambda (H5) or add NAT Gateway
4. Implement GCP/Azure-specific trigger functions (M9)

### Step 5 — Add error handling and status tracking (H2, M4)
1. Add `trap cleanup EXIT` to `forecast-task.sh` that uploads `FAILED` status
2. Make ingestion minimum row count configurable and enforce it
3. Add CloudWatch Logs agent installation to the bootstrap script (L4)

### Step 6 — Fix spot interruption handling (H1)
1. Update `spot_interruption.py` to send SSM command to the instance
2. Add IMDS-based polling as a fallback on the instance side
3. Verify the 2-minute window is sufficient for graceful shutdown

### Step 7 — Security and operational improvements (M6, M5, M7)
1. Migrate secrets to SSM Parameter Store
2. Add `tflint` and `tfsec` to CI workflow
3. Add spot fallback logic (alternative instance types, on-demand)
4. Fix monitoring module to use tag-based instance discovery (H6)

### Step 8 — Model warm-start and result collection (L3, L5)
1. Add model warm-start check to `forecast-task.sh`
2. Add forecast-specific endpoints to `collect-results.sh`
3. Fix cost documentation with real-world measurements (L1)

### Step 9 — Local server forecast path (Phase 5)
**Skip if only using cloud deployment.** Execute if a local server is available:
1. Create `infra/local/scripts/forecast-local.sh` — local pipeline runner
2. Create `infra/local/docker-compose.forecast.local.yml` — compose with local image refs
3. Create `infra/local/.env.forecast.example` — local secrets template
4. Build images locally: `docker compose -f docker-compose.yml build`
5. Test: `./infra/local/scripts/forecast-local.sh` — verify full pipeline completes
6. Create `infra/local/scripts/backup-forecast-results.sh` — local result rotation
7. Create `infra/local/scripts/setup-forecast-cron.sh` — install crontab
8. Run `setup-forecast-cron.sh` — schedule weekday 06:00 runs
9. Verify: next scheduled run completes, results in `./forecast-results/`

---

## Verification

The following files from [`forecast-anomaly-storage-gap-elimination-plan.md`](forecast-anomaly-storage-gap-elimination-plan.md) must be in place **before** Steps 3–4 above. These are not part of the Terraform infrastructure but are application-layer changes the spot pipeline depends on.

**New (12 files)**:
- `persistence/.../db/migration/V29__add_volatility_forecasts.sql`
- `persistence/.../db/migration/V30__add_model_artifacts.sql`
- `persistence/.../entity/VolatilityForecast.java`
- `persistence/.../entity/ModelArtifact.java`
- `persistence/.../repository/VolatilityForecastRepository.java`
- `persistence/.../repository/ModelArtifactRepository.java`
- `computation/.../forecast/ForecastPersistenceService.java`
- `computation/.../anomaly/AnomalyScoringService.java`
- `computation/.../model/ModelArtifactService.java`
- `persistence/.../repository/VolatilityForecastRepositoryTest.java`
- `persistence/.../repository/ModelArtifactRepositoryTest.java`
- `computation/.../model/ModelArtifactServiceTest.java`

**Modified (6 files)**:
- `persistence/.../entity/IliHistory.java` — add `anomalyScore`, `isSuspectAnomaly`
- `persistence/.../entity/RateSnapshot.java` — add `anomalyScore`, `isSuspectAnomaly`
- `persistence/.../repository/IliHistoryRepository.java` — add `updateAnomalyScore`, update SQL
- `persistence/.../repository/RateSnapshotRepository.java` — add `updateAnomalyScore`, update SQL
- `persistence/.../repository/IliHistoryRepositoryTest.java` — update for new columns
- `persistence/.../repository/RateSnapshotRepositoryTest.java` — update for new columns
