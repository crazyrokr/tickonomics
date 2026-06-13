# Runbook: Forecast Pipeline — Deploy, Run, Verify

## 1. Overview

The Tickonomics forecast pipeline runs on cloud spot instances (AWS, GCP, or Azure). It provisions ephemeral infrastructure, boots a three-container stack (backend, analytics-worker, timescaledb), ingests market data, computes KPIs and signals, collects results, and self-terminates — all in under one hour at ~$0.35–0.38 per run.

**Architecture on the spot instance:**

```
┌─────────────────────────────────────────────────┐
│  Spot VM (c5.2xlarge / c2-standard-8 / F8s_v2)  │
│                                                  │
│  ┌──────────┐  ┌──────────────────┐  ┌────────┐ │
│  │ backend  │──│ analytics-worker │  │  S3/   │ │
│  │ :8080    │  │ :8001            │  │GCS/Blob│ │
│  └────┬─────┘  └──────────────────┘  └────▲───┘ │
│       │                                   │     │
│  ┌────▼─────────────────────────────┐     │     │
│  │ TimescaleDB :5432                 │     │     │
│  │ Data: /mnt/timescaledb (EBS/Disk) │     │     │
│  └──────────────────────────────────┘     │     │
│                                           │     │
│  forecast-task.sh ──collect-results.sh────┘     │
└─────────────────────────────────────────────────┘
```

---

## 2. Prerequisites

### 2.1 Tooling

| Tool | Install | Purpose |
|---|---|---|
| Terraform `>= 1.7` | [hashicorp.com](https://developer.hashicorp.com/terraform/downloads) | Infrastructure provisioning |
| Cloud CLI | `aws` / `gcloud` / `az` | Authentication and result download |
| Docker | [docker.com](https://docs.docker.com/get-docker/) | Building container images |
| `jq` | system package manager | Parsing JSON results |
| `curl` | system package manager | Health checks and API calls |

### 2.2 Credentials

| Cloud | Required secrets | How to set |
|---|---|---|
| **AWS** | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, SSH key pair | Env vars or `aws configure` |
| **GCP** | Service account JSON, SSH public key | `GOOGLE_APPLICATION_CREDENTIALS` env var |
| **Azure** | `ARM_CLIENT_ID`, `ARM_CLIENT_SECRET`, `ARM_SUBSCRIPTION_ID`, `ARM_TENANT_ID` | Env vars or `az login` |

### 2.3 Configuration

Copy the example vars file for your provider and fill in real values:

```bash
cd infra/terraform/environments/aws  # or gcp, azure
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars — set postgres_password, ssh_public_key, API keys, etc.
```

Required variables for every provider:
- `postgres_password` — TimescaleDB password (strong, non-default)
- `ssh_public_key` — Your `~/.ssh/id_ed25519.pub` contents
- `image_tag` — Container tag to deploy (default: `latest`)

---

## 3. Deploy

### 3.1 Option A: Terraform CLI (full stack)

```bash
cd infra/terraform/environments/aws   # or gcp, azure

# Initialize providers and modules
terraform init

# Preview all resources
terraform plan -var-file=terraform.tfvars -out=tfplan

# Provision everything
terraform apply tfplan
```

This creates:
- VPC, subnet, security groups
- S3/GCS/Blob results bucket
- EBS/Persistent disk for TimescaleDB
- ECR/Artifact Registry/ACR repositories
- Spot instance (with cloud-init bootstrap)
- Lambda/Cloud Functions + API Gateway for triggering
- CloudWatch/Monitor alerts + dashboard

**Outputs after apply:**

```
spot_instance_public_ip = "54.123.45.67"
results_bucket_name     = "tickonomics-forecast-results"
api_gateway_endpoint    = "https://abc123.execute-api.us-east-1.amazonaws.com/prod"
ecr_backend_url         = "123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/backend"
ecr_analytics_url       = "123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/analytics"
ebs_volume_id           = "vol-0abc123def456"
cloudwatch_dashboard_url = "https://us-east-1.console.aws.amazon.com/cloudwatch/..."
```

### 3.2 Option B: CLI one-shot script

For manual runs without the orchestrator stack:

```bash
# Deploy and run a forecast on AWS
./infra/scripts/run-forecast.sh --provider aws --tag latest

# With options
./infra/scripts/run-forecast.sh \
  --provider aws \
  --tag v1.2.0 \
  --results-dir ./my-forecast-output \
  --no-destroy    # keep the spot VM running after completion
```

The script handles `terraform init` → `apply` → poll status → download results → `destroy`.

### 3.3 Option C: Trigger via API Gateway (scheduled or on-demand)

Once the full stack is deployed, trigger forecasts via the API:

```bash
# Trigger a new forecast run
curl -X POST https://abc123.execute-api.us-east-1.amazonaws.com/prod/forecast \
  -H "Content-Type: application/json"

# Response:
# {"task_id": "20260609-143025-a1b2c3d4", "status": "TRIGGERED"}
```

The EventBridge schedule runs this automatically on `cron(0 6 ? * MON-FRI *)` (weekdays 6 AM UTC).

### 3.4 Option D: Atlantis (PR-driven)

Push a change to `infra/terraform/` on a branch, open a PR. Atlantis will:
1. Run `terraform plan` automatically
2. Post plan output as a PR comment
3. On `atlantis apply` comment: provision the resources

---

## 4. Build and Push Container Images

Before the spot instance can pull images, they must be in the container registry:

### AWS (ECR)

```bash
# Authenticate
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Build and push backend
docker build -t 123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/backend:latest .
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/backend:latest

# Build and push analytics
docker build -t 123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/analytics:latest ./analytics
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/tickonomics/analytics:latest
```

### GCP (Artifact Registry)

```bash
gcloud auth configure-docker us-central1-docker.pkg.dev
docker build -t us-central1-docker.pkg.dev/PROJECT/tickonomics-backend/latest .
docker push us-central1-docker.pkg.dev/PROJECT/tickonomics-backend/latest
```

### Azure (ACR)

```bash
az acr login --name tickonomicsregistry
docker build -t tickonomicsregistry.azurecr.io/tickonomics/backend:latest .
docker push tickonomicsregistry.azurecr.io/tickonomics/backend:latest
```

---

## 5. Monitor the Running Forecast

### 5.1 Check instance status

```bash
# AWS
aws ec2 describe-spot-instance-requests \
  --spot-instance-request-ids $(terraform output -raw spot_request_id) \
  --query 'SpotInstanceRequests[0].Status.Code'

# SSH into the instance
ssh -o StrictHostKeyChecking=no ubuntu@$(terraform output -raw spot_instance_public_ip)
```

### 5.2 Watch the bootstrap log

```bash
# On the spot instance via SSH
tail -f /var/log/forecast-task.log
```

Log phases:
1. `Phase 1: Installing Docker and AWS CLI...`
2. `Phase 2: Mounting persistent disk...`
3. `Phase 3: Pulling container images...`
4. `Phase 4: Starting Docker Compose services...`
5. `Phase 5: Waiting for services to become healthy...`
6. `Phase 6: Waiting for data ingestion...`
7. `Phase 7: Triggering KPI and signal computation...`
8. `Phase 8: Collecting results...`
9. `Phase 9: Graceful shutdown...`
10. `Phase 10: Self-terminating spot instance...`

### 5.3 Check container health

```bash
# On the spot instance
docker compose -f /opt/tickonomics/docker-compose.forecast.yml ps

# Expected output:
# NAME                        STATUS
# forecast-backend-1          Up 5 min (healthy)
# forecast-analytics-worker-1 Up 5 min (healthy)
# forecast-timescaledb-1      Up 5 min (healthy)
```

### 5.4 Health endpoints

```bash
# Backend (Spring Boot actuator)
curl -sf http://<SPOT_IP>:8080/actuator/health
# Expected: {"status":"UP"}

# Analytics worker (FastAPI)
curl -sf http://<SPOT_IP>:8001/health
# Expected: {"status":"UP","scipy_version":"1.14.0",...}

# TimescaleDB
docker exec forecast-timescaledb-1 pg_isready -U tickonomics
# Expected: accepting connections
```

### 5.5 Check data ingestion progress

```bash
# On the spot instance
docker exec forecast-timescaledb-1 psql -U tickonomics -t -c \
  "SELECT COUNT(*) FROM rate_snapshots"
# Expected: >= 50 rows before computation triggers
```

### 5.6 Poll task status via API

```bash
# AWS API Gateway
curl -sf https://abc123.execute-api.us-east-1.amazonaws.com/prod/forecast/20260609-143025-a1b2c3d4

# Response progression:
# {"task_id":"...","status":"RUNNING","has_results":false}
# {"task_id":"...","status":"COMPLETED","has_results":true,"results_url":"s3://..."}
```

### 5.7 Monitor via dashboard

- **AWS:** Open the CloudWatch dashboard URL from `terraform output cloudwatch_dashboard_url`
- **GCP:** Cloud Console → Logging → filter `resource.type="gce_instance"`
- **Azure:** Azure Monitor → Metric Alerts → `tickonomics-high-cpu`, `tickonomics-spot-eviction`

### 5.8 Spot interruption handling

If the cloud provider reclaims the spot instance mid-run:

1. **AWS:** EventBridge rule detects `EC2 Spot Instance Interruption Warning` → Lambda logs marker to S3
2. **GCP:** Log-based metric on `compute.instances.preempted` → Pub/Sub notification
3. **Azure:** Monitor alert on `VM Eviction` metric

In all cases, the application graceful shutdown activates:
- Spring Boot `server.shutdown: graceful` with 60s timeout
- Analytics worker receives SIGTERM → `shutdown_requested = True` → uvicorn allows 30s for in-flight requests
- `TimescaleDbWriter.flushAll()` drains all buffered tick and rate data to disk

---

## 6. Verify Results

### 6.1 Download results

```bash
# AWS S3
aws s3 sync s3://tickonomics-forecast-results/<TASK_ID>/ ./forecast-results/

# GCS
gsutil -m cp gs://tickonomics-forecast-results/<TASK_ID>/* ./forecast-results/

# Azure Blob
az storage blob download-batch \
  --destination ./forecast-results/ \
  --source forecast-results \
  --pattern "<TASK_ID>/*"
```

### 6.2 Result archive structure

```
forecast-results/
├── forecast-results.tar.gz    # Complete archive
├── STATUS                     # "COMPLETED" or "FAILED"
├── tickonomics_dump.sql.gz    # Full PostgreSQL dump (compressed)
├── kpis.json                  # KPI computation results
├── signals.json               # Alpha signals
├── strategies.json            # Active strategies
├── backtest-results.json      # Backtest performance data
├── risk-summary.json          # Risk metrics
├── regime.json                # Market regime state
├── liquidity.json             # Liquidity measures
├── analytics-evt.json         # EVT tail risk analysis
├── analytics-risk.json        # VaR / CVaR / GARCH
├── analytics-regime.json      # Regime detection results
└── analytics-diagnostics.json # QQ-plot, ACF, convergence stats
```

### 6.3 Verify result integrity

```bash
#!/bin/bash
set -euo pipefail

RESULTS_DIR="./forecast-results"
ERRORS=0

echo "=== Forecast Result Verification ==="

# 1. Check status marker
STATUS=$(cat "$RESULTS_DIR/STATUS" 2>/dev/null || echo "MISSING")
if [ "$STATUS" = "COMPLETED" ]; then
  echo "[PASS] Status: COMPLETED"
else
  echo "[FAIL] Status: $STATUS (expected COMPLETED)"
  ERRORS=$((ERRORS + 1))
fi

# 2. Verify archive exists and is valid
ARCHIVE="$RESULTS_DIR/forecast-results.tar.gz"
if [ -f "$ARCHIVE" ]; then
  SIZE=$(stat -f%z "$ARCHIVE" 2>/dev/null || stat -c%s "$ARCHIVE" 2>/dev/null)
  if [ "$SIZE" -gt 1024 ]; then
    echo "[PASS] Archive size: ${SIZE} bytes"
    tar -tzf "$ARCHIVE" > /dev/null 2>&1 && echo "[PASS] Archive integrity: valid" || {
      echo "[FAIL] Archive integrity: corrupted"
      ERRORS=$((ERRORS + 1))
    }
  else
    echo "[FAIL] Archive too small: ${SIZE} bytes"
    ERRORS=$((ERRORS + 1))
  fi
else
  echo "[FAIL] Archive not found"
  ERRORS=$((ERRORS + 1))
fi

# 3. Verify database dump
DUMP="$RESULTS_DIR/tickonomics_dump.sql.gz"
if [ -f "$DUMP" ]; then
  echo "[PASS] Database dump: $(du -h "$DUMP" | cut -f1)"
  gunzip -t "$DUMP" 2>/dev/null && echo "[PASS] Dump integrity: valid" || {
    echo "[FAIL] Dump integrity: corrupted"
    ERRORS=$((ERRORS + 1))
  }
else
  echo "[WARN] Database dump not found (may be empty run)"
fi

# 4. Verify JSON result files are non-empty and valid JSON
for json_file in kpis signals strategies risk-summary regime liquidity \
                 analytics-evt analytics-risk analytics-regime analytics-diagnostics; do
  FILE="$RESULTS_DIR/${json_file}.json"
  if [ -f "$FILE" ]; then
    if jq empty "$FILE" 2>/dev/null; then
      RECORDS=$(jq 'if type == "array" then length elif type == "object" then 1 else 0 end' "$FILE")
      echo "[PASS] ${json_file}.json: valid JSON (${RECORDS} items)"
    else
      echo "[FAIL] ${json_file}.json: invalid JSON"
      ERRORS=$((ERRORS + 1))
    fi
  else
    echo "[WARN] ${json_file}.json: not found"
  fi
done

# 5. Check for error indicators in results
for json_file in "$RESULTS_DIR"/*.json; do
  [ -f "$json_file" ] || continue
  if grep -q '"error"' "$json_file" 2>/dev/null; then
    echo "[WARN] $(basename "$json_file"): contains error response"
  fi
done

# 6. Verify database has data (from dump inspection)
if [ -f "$DUMP" ]; then
  RATE_COUNT=$(zcat "$DUMP" 2>/dev/null | grep -c "COPY.*rate_snapshots" || echo "0")
  if [ "$RATE_COUNT" -gt 0 ]; then
    echo "[PASS] rate_snapshots table present in dump"
  else
    echo "[WARN] rate_snapshots table not found in dump"
  fi
fi

echo ""
echo "========================================"
if [ "$ERRORS" -eq 0 ]; then
  echo "All verifications passed"
else
  echo "$ERRORS verification(s) failed"
fi
echo "========================================"
exit "$ERRORS"
```

### 6.4 Key metrics to check

| File | What to verify | Expected |
|---|---|---|
| `kpis.json` | ILI scores, correlation engine results | Non-null numeric values |
| `signals.json` | Alpha signal generation | At least 1 active signal if data was ingested |
| `risk-summary.json` | Risk premium, toxicity metrics | Finite values (no NaN/Infinity) |
| `regime.json` | Current market regime | One of: TRENDING, MEAN_REVERTING, CRISIS, CALM |
| `analytics-evt.json` | Tail risk parameters (xi, sigma) | Finite, physically plausible values |
| `analytics-diagnostics.json` | Model convergence stats | Convergence flags set to true |

### 6.5 Spot-check with SQL (from the dump)

```bash
# Extract and query the dump locally
zcat tickonomics_dump.sql.gz | head -100

# Or restore to a local PostgreSQL for ad-hoc queries
createdb tickonomics_verify
zcat tickonomics_dump.sql.gz | psql tickonomics_verify

psql tickonomics_verify -c "
  SELECT
    (SELECT COUNT(*) FROM rate_snapshots)   AS rate_count,
    (SELECT COUNT(*) FROM tick_data)        AS tick_count,
    (SELECT COUNT(*) FROM signal_results)   AS signal_count
;"
```

---

## 7. Troubleshooting

### 7.1 Spot instance never boots

```bash
# Check spot request status (AWS)
aws ec2 describe-spot-instance-requests \
  --query 'SpotInstanceRequests[0].{Status:Status.Code,Message:Status.Message}'

# Common causes:
# - "price-too-low" → increase spot_price_max
# - "capacity-not-available" → try a different instance_type or availability_zone
# - "constraint-not-fulfillable" → check AMI exists in the target AZ
```

### 7.2 Containers fail health checks

```bash
# Check container logs
docker logs forecast-backend-1 --tail 50
docker logs forecast-analytics-worker-1 --tail 50

# Common backend failures:
# - "Connection refused" to timescaledb → check DB is healthy first
# - "Flyway migration failed" → check if schema is corrupted on persistent disk
# - "OutOfMemoryError" → increase instance type

# Common analytics failures:
# - "ModuleNotFoundError" → image build issue, check Dockerfile
# - "Address already in use" → port conflict, restart container
```

### 7.3 Ingestion stalls (< 50 rows)

```bash
# Check if scheduled ingestion is running
docker logs forecast-backend-1 2>&1 | grep -i "scheduled\|fred\|nyfed\|ingestion"

# Common causes:
# - POLYGON_API_KEY not set → no WebSocket tick ingestion
# - FRED_API_KEY not set → no economic rate data
# - API rate limiting → check for 429 responses in logs
# - Network restrictions → check security group egress rules
```

### 7.4 Results upload fails

```bash
# Check S3 access from the instance
aws s3 ls s3://tickonomics-forecast-results/

# Common causes:
# - IAM role missing S3 write policy → check module.storage.s3_write_policy_arn
# - Bucket doesn't exist → run terraform apply for storage module
# - Network issue → check VPC has internet gateway and NAT (for private subnets)
```

### 7.5 Graceful shutdown fails

```bash
# Check application logs for shutdown sequence
docker logs forecast-backend-1 2>&1 | grep -i "shutdown\|graceful\|dispose"

# Expected sequence:
# 1. "Graceful shutdown initiated" (GracefulShutdown.java)
# 2. "Shutting down..." (Spring Boot)
# 3. "Flushed N tick records" (TimescaleDbWriter)
# 4. "Flushed N rate records" (TimescaleDbWriter)
```

### 7.6 Persistent disk issues

```bash
# Check if disk is mounted
mountpoint /mnt/timescaledb
lsblk

# Reattach manually if needed (AWS)
aws ec2 attach-volume --volume-id vol-xxx --instance-id i-xxx --device /dev/sdf

# On the instance
mount /dev/sdf /mnt/timescaledb
```

---

## 8. Cleanup

### 8.1 Tear down spot resources only

```bash
cd infra/terraform/environments/aws
terraform destroy \
  -target=module.compute_spot \
  -var-file=terraform.tfvars \
  -auto-approve
```

This destroys the spot instance but preserves:
- S3 bucket with results
- EBS volume with TimescaleDB data
- ECR repositories
- VPC and networking
- Lambda/API Gateway orchestrator

### 8.2 Full teardown

```bash
cd infra/terraform/environments/aws
terraform destroy -var-file=terraform.tfvars -auto-approve
```

**Warning:** This deletes everything including the S3 results bucket and EBS volume.

### 8.3 Download results before teardown

```bash
# Download all results
aws s3 sync s3://tickonomics-forecast-results/ ./forecast-results-backup/
```

---

## 9. Cost Verification

After a forecast run, verify the actual cost:

### AWS

```bash
# Check Cost Explorer for the run period
aws ce get-cost-and-usage \
  --time-period Start=2026-06-09,End=2026-06-10 \
  --granularity DAILY \
  --metrics BlendedCost \
  --group-by Type=DIMENSION,Key=SERVICE
```

### Expected costs per run

| Component | AWS | GCP | Azure |
|---|---|---|---|
| Spot compute (1 hr) | $0.09 | $0.08 | $0.09 |
| Disk (prorated) | $0.003 | $0.004 | $0.002 |
| Registry | $0.05/mo | $0.05/mo | $0.00 |
| Storage | $0.02 | $0.02 | $0.02 |
| Network | $0.18 | $0.15 | $0.15 |
| Lambda/Functions | <$0.01 | $0.00 | $0.00 |
| **Total per run** | **~$0.38** | **~$0.36** | **~$0.35** |
| **Monthly (20 runs)** | **~$7.60** | **~$7.20** | **~$7.00** |

---

## 10. Quick Reference

### End-to-end in one command

```bash
./infra/scripts/run-forecast.sh --provider aws --tag latest
```

### Manual step-by-step

```bash
# 1. Build and push images
docker build -t $ECR/backend:latest . && docker push $ECR/backend:latest
docker build -t $ECR/analytics:latest ./analytics && docker push $ECR/analytics:latest

# 2. Deploy infrastructure
cd infra/terraform/environments/aws
terraform init && terraform apply -auto-approve

# 3. Monitor
tail -f /var/log/forecast-task.log  # on the spot instance

# 4. Check status
curl $API_ENDPOINT/forecast/$TASK_ID

# 5. Download results
aws s3 sync s3://tickonomics-forecast-results/$TASK_ID/ ./results/

# 6. Verify
bash verify-results.sh  # (from section 6.3)

# 7. Cleanup
terraform destroy -target=module.compute_spot -auto-approve
```

### API Endpoint Summary

| Service | Endpoint | Method | Purpose |
|---|---|---|---|
| Backend | `/actuator/health` | GET | Health check |
| Backend | `/health` | GET | Custom health |
| Backend | `/api/v1/quant/signals/active` | GET | Active alpha signals |
| Backend | `/api/v1/quant/strategies/active` | GET | Active strategies |
| Backend | `/api/v1/quant/risk/tail-parameters` | GET | Tail risk params |
| Backend | `/api/v1/demo/portfolio` | GET | Virtual portfolio |
| Analytics | `/health` | GET | Health + library versions |
| Analytics | `/api/v1/risk/var` | POST | Value at Risk |
| Analytics | `/api/v1/risk/cvar` | POST | Conditional VaR |
| Analytics | `/api/v1/regime/garch-regime` | POST | Regime detection |
| Analytics | `/api/v1/statistical/evt/fit` | POST | EVT tail fitting |
| Analytics | `/api/v1/diagnostics/stats` | POST | Convergence diagnostics |
| Orchestrator | `/forecast` | POST | Trigger forecast run |
| Orchestrator | `/forecast/{taskId}` | GET | Check task status |
