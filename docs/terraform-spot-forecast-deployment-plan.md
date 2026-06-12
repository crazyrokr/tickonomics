# Plan: Terraform Spot Instance Deployment for Forecast Tasks

## Context

The Tickonomics platform currently runs exclusively via Docker Compose with no Infrastructure-as-Code. The CI/CD deploy steps are placeholder stubs. This plan adds Terraform modules to provision spot instances on AWS, GCP, and Azure, run a complete forecast pipeline in under 1 hour, collect results to cloud storage, and self-terminate — achieving ~$0.35–0.38 per forecast run.

**Key constraints:** The project has no single "forecast" endpoint — the pipeline is a composition of Java `@Scheduled` ingestion, KPI computation services, and Python ML analytics. The orchestration script must drive this end-to-end.

**Storage prerequisite:** The forecast and anomaly detection subsystems have three storage gaps that must be closed before spot-instance orchestration can deliver reliable results. These gaps — forecast result storage, anomaly score wiring, and model state persistence — are addressed in [`docs/forecast-anomaly-storage-gap-elimination-plan.md`](forecast-anomaly-storage-gap-elimination-plan.md). The changes below assume those gaps are resolved (migrations V29, V30; entity/repository updates; `ForecastPersistenceService`, `AnomalyScoringService`, `ModelArtifactService`).

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

**Modified (Application — 4 files)**:
- `app/src/main/resources/application.yml` — add `server.shutdown: graceful`, `spring.lifecycle.timeout`
- `computation/src/main/java/.../lifecycle/GracefulShutdown.java` — new file
- `analytics/app/main.py` — add SIGTERM handler
- `analytics/Dockerfile` — add `--timeout-graceful-shutdown 30`

---

## Storage Gap Prerequisites

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
