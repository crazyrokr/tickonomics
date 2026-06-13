# Plan: Production Infrastructure for Tickonomics Platform

**Phase:** Phase 9 (after Track 11 — Deployment & Operations)
**Depends on:** Track 9 (CI/CD), Track 11 (Deployment & Operations), Plan 13 (Terraform Spot Forecast)
**Blocks:** Production go-live

---

## Context

Plan 13 provisions **ephemeral spot instances** for forecast pipeline runs — transient compute that self-terminates after results are collected. The platform also needs **persistent production infrastructure** where the backend, analytics worker, dashboard, and database run continuously to serve real-time data, signals, and the web UI.

Currently, `deploy-staging.yml` and `deploy-production.yml` contain placeholder deploy steps ("requires Track 11 Docker infrastructure"). Track 11 defines Docker Compose configurations and runbooks but does not provision the actual cloud resources those environments require. This plan fills that gap.

**In scope:**
- Persistent compute hosting (EC2 / Cloud Run / App Service) for the 4 services
- Local server hosting (bare-metal / homelab) as a full alternative to cloud
- Managed database (TimescaleDB / Cloud SQL for PostgreSQL)
- Secrets management (SSM / Secret Manager / Key Vault / local `.env`)
- DNS, TLS, and CDN
- Network hardening (private subnets, bastion, restrictive SGs)
- Automated database backups and disaster recovery
- Cost management and budget alerts
- Observability stack provisioning (Prometheus + Grafana + Loki)
- Staging vs. production environment separation
- CI/CD integration for both cloud and local deployment targets

**Out of scope (already covered):**
- Spot instance infrastructure for forecast tasks → Plan 13
- CI/CD workflow definitions → Track 9
- Docker Compose configurations and runbooks → Track 11
- Application-level changes (graceful shutdown, health endpoints) → Plan 13 Phase 2 (completed)

---

## Phase 1: Persistent Compute Hosting

### 1.1 Directory Structure

```
infra/
├── terraform/
│   ├── modules/
│   │   ├── hosting/                          # NEW — persistent app hosting
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── database/                         # NEW — managed TimescaleDB
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── dns-tls/                          # NEW — DNS records + ACM certs + CDN
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── secrets/                          # NEW — secrets manager resources
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   ├── observability/                    # NEW — Prometheus/Grafana/Loki
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   ├── dashboards/                   # JSON dashboard definitions
│   │   │   │   ├── overview.json
│   │   │   │   ├── ingestion.json
│   │   │   │   ├── computation.json
│   │   │   │   └── forecast.json
│   │   │   ├── alerts/                       # Alerting rules
│   │   │   │   └── alerts.yaml
│   │   │   └── outputs.tf
│   │   ├── backup/                           # NEW — automated DB backups
│   │   │   ├── variables.tf
│   │   │   ├── main.tf
│   │   │   └── outputs.tf
│   │   └── budget/                           # NEW — cost management
│   │       ├── variables.tf
│   │       ├── main.tf
│   │       └── outputs.tf
│   ├── environments/
│   │   ├── aws/
│   │   │   ├── main.tf                       # Updated: add new modules
│   │   │   ├── staging.tf                    # NEW — staging-specific overrides
│   │   │   └── production.tf                 # NEW — production-specific overrides
│   │   ├── gcp/
│   │   │   ├── main.tf                       # Updated: add new modules
│   │   │   ├── staging.tf
│   │   │   └── production.tf
│   │   └── azure/
│   │       ├── main.tf                       # Updated: add new modules
│   │       ├── staging.tf
│   │       └── production.tf
├── local/                                    # NEW — local server deployment
│   ├── scripts/
│   │   ├── deploy-local.sh                   # Deploy/restart full stack
│   │   ├── backup-local.sh                   # pg_dump + filesystem backup
│   │   ├── restore-local.sh                  # Restore from backup
│   │   └── setup-local-cron.sh               # Install backup/maintenance crontab
│   ├── docker-compose.local.yml              # Full stack for local server
│   ├── Caddyfile.local                       # Caddy config (LAN / local TLS)
│   ├── .env.local.example                    # Template for local secrets
│   ├── crontab                               # Backup + maintenance schedule
│   └── systemd/
│       ├── tickonomics-backup.service         # systemd unit for backup
│       └── tickonomics-backup.timer           # systemd timer (alternative to cron)
```

### 1.2 Hosting Strategy — AWS

**Decision: Single EC2 instance with Docker Compose (cost-optimized)**

The platform runs 4 lightweight services + 1 database. For the current scale (single user, demo/portfolio verification), a single `t3.xlarge` on-demand instance with Docker Compose is the simplest and cheapest option (~$120/month). This avoids the operational overhead of ECS/EKS while providing full Docker Compose parity with local development.

| Component | Resource | Rationale |
|:----------|:---------|:----------|
| Backend (Java) | Docker container on host | Shares EC2 with other services |
| Analytics worker (Python) | Docker container on host | Shares EC2, communicates via localhost |
| Dashboard (Next.js) | Docker container on host | Served behind reverse proxy |
| Landing (Next.js static) | S3 + CloudFront | Static hosting, already deployed via Vercel |
| Reverse proxy | Caddy container on host | Auto-TLS via Let's Encrypt, reverse proxy to backend/dashboard |

**`modules/hosting/main.tf` (AWS):**
- `aws_instance` (t3.xlarge, Ubuntu 22.04, 100GB gp3 root volume)
- `aws_ebs_volume` + `aws_volume_attachment` for TimescaleDB data (50GB gp3, encrypted)
- `aws_eip` for stable public IP
- `aws_iam_role` + `aws_iam_instance_profile` (SSM, ECR read, S3 read/write, Secrets Manager read)
- `aws_security_group` — restricted ingress (80/443 from ALB/LB, 22 from bastion only)
- `cloud-config.yaml.tftpl` — cloud-init that installs Docker, pulls images from ECR, runs `docker-compose.prod.yml`
- `null_resource` for initial provisioning via SSM

**GCP equivalent:** `google_compute_instance` (e2-standard-4) with container-optimized OS or Ubuntu + Docker.

**Azure equivalent:** `azurerm_linux_virtual_machine` (Standard_D4s_v5) with custom extension to install Docker.

### 1.3 Environment Separation

| Property | Staging | Production |
|:---------|:--------|:-----------|
| Instance type | `t3.large` | `t3.xlarge` |
| DB volume | 25 GB | 100 GB |
| DB backup retention | 7 days | 30 days |
| TimescaleDB config | Default | Tuned (shared_buffers, work_mem) |
| DNS | `staging.tickonomics.io` | `tickonomics.io` |
| Dashboard DNS | `app.staging.tickonomics.io` | `app.tickonomics.io` |
| Auto-scaling | None | None (single instance) |
| Monitoring | Basic alerts | Full dashboards + PagerDuty |
| Budget alert | $30/month | $200/month |
| State file | `staging.tfstate` | `production.tfstate` |

Implementation: Terraform workspaces (`terraform workspace new staging` / `terraform workspace new production`) with `locals` block that selects instance type, volume size, and DNS prefix based on `terraform.workspace`.

### 1.4 Reverse Proxy — Caddy

Replace the manual Nginx/TLS setup with [Caddy](https://caddyserver.com/) for automatic HTTPS:

```yaml
# docker-compose.prod.yml addition
caddy:
  image: caddy:2-alpine
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./Caddyfile:/etc/caddy/Caddyfile
    - caddy_data:/data
    - caddy_config:/config
  restart: unless-stopped
```

**`Caddyfile` (staging):**
```
app.staging.tickonomics.io {
    reverse_proxy dashboard:3000
}

api.staging.tickonomics.io {
    reverse_proxy backend:8080
}
```

**`Caddyfile` (production):**
```
app.tickonomics.io {
    reverse_proxy dashboard:3000
}

api.tickonomics.io {
    reverse_proxy backend:8080
}
```

Caddy automatically provisions and renews TLS certificates via Let's Encrypt. No ACM/ certificate management needed.

---

## Phase 2: Managed Database

### 2.1 Strategy — Self-Hosted TimescaleDB on Persistent Disk

TimescaleDB Cloud (managed) costs ~$50/month for the smallest instance. Self-hosting on the same EC2 instance with a persistent EBS volume costs the marginal disk (~$5/month) and provides full control over extensions and tuning.

**`modules/database/main.tf`:**
- No cloud-managed database resource — TimescaleDB runs in a Docker container
- `aws_ebs_volume` (gp3, encrypted, snapshot-based backup)
- `aws_db_snapshot` — daily automated snapshot via Lambda/EventBridge
- `aws_iam_policy` for snapshot creation
- TimescaleDB container with healthcheck and volume mount

**TimescaleDB production tuning** (injected via environment variables):

```yaml
timescaledb:
  image: timescale/timescaledb:latest-pg16
  environment:
    POSTGRES_DB: tickonomics
    POSTGRES_PASSWORD: ${DB_PASSWORD}
    # Performance tuning
    POSTGRES_MAX_CONNECTIONS: 50
  command: >
    postgres
    -c shared_buffers=512MB
    -c effective_cache_size=1536MB
    -c work_mem=16MB
    -c maintenance_work_mem=128MB
    -c checkpoint_completion_target=0.9
    -c wal_buffers=16MB
    -c default_statistics_target=100
    -c random_page_cost=1.1
    -c effective_io_concurrency=200
  volumes:
    - timescaledb_data:/var/lib/postgresql/data
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U tickonomics"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### 2.2 Database Migration Strategy

Flyway migrations run automatically on backend startup (existing behavior). For production safety:

1. **Pre-migration validation**: CI workflow runs `./gradlew flywayInfo` against a Testcontainers replica before deploy
2. **Backup-first**: Lambda triggers an EBS snapshot before deployment
3. **Rollback**: If migration fails, restore from the pre-deployment snapshot (documented in runbook)

---

## Phase 3: Secrets Management

### 3.1 Strategy — AWS SSM Parameter Store

SSM Parameter Store (SecureString, KMS-encrypted) is sufficient for this scale and avoids the cost of Secrets Manager ($0.40/secret/month vs. free for SSM Standard tier).

**`modules/secrets/main.tf`:**
- `aws_ssm_parameter` for each secret (SecureString, KMS customer-managed key)
- `aws_kms_key` for encryption
- `aws_iam_policy` granting the hosting EC2 instance `ssm:GetParameter` on `/tickonomics/*`

**Secrets stored:**

| Parameter Path | Value | Rotated By |
|:---------------|:------|:-----------|
| `/tickonomics/db/password` | TimescaleDB password | Manual (quarterly) |
| `/tickonomics/api/finnhub` | Finnhub API key | Manual (on change) |
| `/tickonomics/api/alphavantage` | Alpha Vantage API key | Manual (on change) |
| `/tickonomics/api/fred` | FRED API key | Manual (on change) |
| `/tickonomics/oauth/client-secret` | Keycloak/Auth0 secret | Manual (on change) |
| `/tickonomics/registry/token` | ECR auth token | Auto (12h) |

**Instance-side retrieval** (in cloud-init bootstrap):

```bash
# Retrieve all secrets and write to .env
aws ssm get-parameter --name "/tickonomics/db/password" --with-decryption --query Parameter.Value --output text > /opt/tickonomics/.env
```

**GCP equivalent:** `google_secret_manager_secret` + `google_secret_manager_secret_version`.

**Azure equivalent:** `azurerm_key_vault` + `azurerm_key_vault_secret` with access policy for the VM managed identity.

### 3.2 Migration from Current State

Currently, secrets are passed as Terraform variables and rendered into cloud-init templates. Migration steps:

1. Create SSM parameters with current secret values
2. Update `cloud-config.yaml.tftpl` to call `aws ssm get-parameter` instead of rendering secrets inline
3. Remove sensitive variables from `terraform.tfvars`
4. Run `terraform apply` — new instances pull secrets from SSM at boot
5. Rotate all secrets that were previously in Terraform state (one-time)
6. Run `terraform state rm` on sensitive outputs

---

## Phase 4: DNS, TLS, and CDN

### 4.1 DNS — Route53 / Cloud DNS / Azure DNS

**`modules/dns-tls/main.tf` (AWS):**
- `aws_route53_zone` for `tickonomics.io`
- `aws_route53_record` (A record) for:
  - `api.tickonomics.io` → EC2 elastic IP
  - `api.staging.tickonomics.io` → staging EC2 elastic IP
  - `app.tickonomics.io` → EC2 elastic IP (Caddy serves dashboard)
  - `app.staging.tickonomics.io` → staging EC2 elastic IP
- `aws_route53_record` (CNAME) for:
  - `tickonomics.io` → Vercel/Cloudflare (landing page, already deployed)

**GCP equivalent:** `google_dns_managed_zone` + `google_dns_record_set`.

**Azure equivalent:** `azurerm_dns_zone` + `azurerm_dns_a_record` / `azurerm_dns_cname_record`.

### 4.2 TLS — Automatic via Caddy

Caddy handles TLS certificate provisioning and renewal automatically via Let's Encrypt (ACME). No manual ACM/cert-manager needed.

Requirements:
- DNS A records must point to the EC2 instance before Caddy starts
- Port 80 and 443 must be open in the security group
- Caddy stores certificates in a persistent Docker volume (`caddy_data`)

### 4.3 CDN — CloudFront for Static Assets

The landing page already deploys via Vercel/Cloudflare (Track 9 Workflow 5). For the dashboard:

- **Phase 1 (MVP):** Caddy serves the dashboard directly — no CDN. The dashboard is an SSR Next.js app, so CDN caching is limited to static assets (JS/CSS bundles).
- **Phase 2 (optimization):** Add CloudFront distribution in front of Caddy for static asset caching:
  - `aws_cloudfront_distribution` with origin pointing to the EC2 instance
  - Cache behavior: `/api/*` → no cache (forward to origin), `/_next/static/*` → cache 1 year, everything else → pass-through
  - WAF integration for rate limiting (see Phase 7)

---

## Phase 5: Network Hardening

### 5.1 Security Group Overhaul

The current spot-instance networking module allows SSH from `0.0.0.0/0`. For persistent production hosting, tighten all ingress rules.

**`modules/hosting/main.tf` — Security Group rules:**

| Rule | Protocol | Port | Source | Purpose |
|:-----|:---------|:-----|:-------|:--------|
| Ingress | TCP | 80 | `0.0.0.0/0` | HTTP → Caddy redirect to HTTPS |
| Ingress | TCP | 443 | `0.0.0.0/0` | HTTPS → Caddy reverse proxy |
| Ingress | TCP | 22 | `<bastion-cidr>` only | SSH for debugging |
| Egress | TCP | 443 | `0.0.0.0/0` | Outbound HTTPS (API calls, image pulls) |
| Egress | TCP | 5432 | `self` | Internal TimescaleDB (container-to-container) |

**No SSH from 0.0.0.0/0.** Use AWS Systems Manager Session Manager for shell access (already granted via the IAM role).

### 5.2 VPC Architecture (Simplified)

Unlike the spot-instance plan which uses a single public subnet, production hosting uses a two-subnet layout:

```
VPC (10.0.0.0/16)
├── Public subnet (10.0.1.0/24)
│   └── EC2 instance (hosting all services)
├── No private subnet needed (single-instance architecture)
└── IGW for outbound
```

The single instance has a public IP for inbound HTTPS (via Caddy). SSM Session Manager provides SSH access without a bastion host. This avoids the cost of a NAT Gateway ($32/month) while maintaining security.

**Future:** If scale warrants, migrate to ALB + private subnet + ECS Fargate. The current single-instance approach is explicitly documented as Phase 1.

### 5.3 Analytics Worker Access Restriction

The analytics worker container must only be accessible from the backend container (localhost). Docker Compose already isolates this — the worker listens on port 8001 but is not mapped to the host. Caddy reverse proxy does not route to port 8001.

---

## Phase 6: Automated Backups and Disaster Recovery

### 6.1 Backup Strategy

| Backup Type | Frequency | Retention | Target |
|:------------|:----------|:----------|:-------|
| EBS snapshot (full disk) | Daily 03:00 UTC | 7 days (staging), 30 days (prod) | AWS snapshot |
| `pg_dump` (logical) | Daily 04:00 UTC | 7 days (staging), 30 days (prod) | S3 bucket |
| Forecast results | Already stored by spot pipeline | 30 days (lifecycle) | S3 bucket |

**`modules/backup/main.tf`:**
- `aws_lambda_function` — backup runner: creates EBS snapshot + runs `pg_dump` via SSM command
- `aws_cloudwatch_event_rule` — daily cron trigger
- `aws_lambda_permission` — allow EventBridge to invoke Lambda
- `aws_s3_bucket` (backup bucket, separate from forecast results)
- `aws_s3_bucket_lifecycle_configuration` — expire backups after retention period
- IAM policy: `ec2:CreateSnapshot`, `ec2:DescribeSnapshots`, `ssm:SendCommand`, `s3:PutObject`

**Backup Lambda pseudocode:**

```python
def handler(event, context):
    # 1. Create EBS snapshot
    snapshot = ec2.create_snapshot(
        VolumeId=volume_id,
        Description=f"tickonomics-daily-{date}",
        TagSpecifications=[{
            "ResourceType": "snapshot",
            "Tags": [{"Key": "AutoBackup", "Value": "true"}]
        }]
    )

    # 2. Run pg_dump via SSM
    ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={"commands": [
            f"docker exec tickonomics-timescaledb-1 pg_dump -U tickonomics | "
            f"aws s3 cp - s3://{backup_bucket}/pg_dump/{date}.sql.gz"
        ]}
    )

    # 3. Clean old snapshots
    old = ec2.describe_snapshots(Filters=[
        {"Name": "tag:AutoBackup", "Values": ["true"]}
    ])
    for s in old["Snapshots"]:
        if age(s) > retention_days:
            ec2.delete_snapshot(SnapshotId=s["SnapshotId"])
```

### 6.2 Disaster Recovery Runbook

**RTO target: 1 hour. RPO target: 24 hours (last daily backup).**

1. **Instance failure:** `terraform apply` provisions new instance from AMI + EBS snapshot restore
2. **Data corruption:** Stop backend, restore from latest `pg_dump` via SSM:
   ```bash
   aws s3 cp s3://tickonomics-backup/pg_dump/latest.sql.gz - | gunzip |
     docker exec -i tickonomics-timescaledb-1 psql -U tickonomics
   ```
3. **AZ outage:** Terraform applies in a different AZ with snapshot restore
4. **Region outage:** Restore from S3 cross-region replica (Phase 2 — not in MVP)

---

## Phase 7: Observability Stack

### 7.1 Strategy — Self-Hosted Prometheus + Grafana + Loki

CloudWatch (already provisioned in Plan 13 for spot instances) provides basic metric and log collection. For the persistent hosting environment, add a self-hosted observability stack for richer dashboards, log aggregation, and alerting.

**`modules/observability/main.tf`:**
- `aws_ebs_volume` (10 GB gp3) for Prometheus/Grafana data persistence
- `aws_volume_attachment`
- IAM policy for CloudWatch log export (optional)

**Docker Compose additions (`docker-compose.monitoring.yml`):**

```yaml
prometheus:
  image: prom/prometheus:v2.54.1
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    - prometheus_data:/prometheus
  command:
    - "--config.file=/etc/prometheus/prometheus.yml"
    - "--storage.tsdb.retention.time=30d"
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana:11.3.0
  volumes:
    - grafana_data:/var/lib/grafana
    - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
    - GF_SERVER_ROOT_URL=https://monitoring.tickonomics.io
  ports:
    - "3001:3000"

loki:
  image: grafana/loki:3.2.0
  volumes:
    - ./monitoring/loki.yml:/etc/loki/config.yml
    - loki_data:/loki
  ports:
    - "3100:3100"

promtail:
  image: grafana/promtail:3.2.0
  volumes:
    - /var/log:/var/log:ro
    - /var/lib/docker/containers:/var/lib/docker/containers:ro
    - ./monitoring/promtail.yml:/etc/promtail/config.yml
  command: -config.file=/etc/promtail/config.yml
```

### 7.2 Metrics Collection

**Backend (Spring Boot):** Micrometer already instruments the application (Plan 04). Add Prometheus scrape endpoint:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

**Prometheus scrape configuration:**

```yaml
scrape_configs:
  - job_name: "backend"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["backend:8080"]

  - job_name: "analytics-worker"
    static_configs:
      - targets: ["analytics-worker:8001"]
    metrics_path: /metrics

  - job_name: "timescaledb"
    static_configs:
      - targets: ["timescaledb-exporter:9187"]

  - job_name: "caddy"
    static_configs:
      - targets: ["caddy:2019"]
```

### 7.3 Dashboards

Pre-provisioned Grafana dashboards via provisioning files:

| Dashboard | Panels |
|:----------|:-------|
| **Overview** | Service health (all 4 containers), request rate, error rate, p95 latency |
| **Ingestion** | Finnhub WS connection status, ticks/sec, circuit breaker state, overflow queue depth, LKG cache hit rate |
| **Computation** | ILI value over time, signal generation rate, regime state, KPI computation latency |
| **Forecast** | Spot pipeline runs (from Plan 13), model training duration, anomaly score distribution, forecast accuracy metrics |

### 7.4 Alerting Rules

**Prometheus alert rules (`alerts.yaml`):**

| Alert | Condition | Severity | Channel |
|:------|:----------|:---------|:--------|
| `BackendDown` | No scrape response for 2m | Critical | SNS email + Slack |
| `HighErrorRate` | 5xx rate > 5% for 5m | Warning | Slack |
| `HighLatency` | p95 > 2s for 5m | Warning | Slack |
| `DatabaseDiskFull` | Disk usage > 85% | Warning | SNS email |
| `DatabaseDiskCritical` | Disk usage > 95% | Critical | SNS email + Slack |
| `IngestionStale` | No new ticks for 10m | Critical | SNS email + Slack |
| `CircuitBreakerOpen` | Any circuit breaker open for > 1m | Warning | Slack |
| `ForecastOverBudget` | Monthly cost exceeds budget | Warning | SNS email |

**Alert routing:** Alertmanager → SNS topic → email. Slack integration via webhook (optional).

### 7.5 Log Aggregation

Promtail ships Docker container logs to Loki. Grafana queries Loki for log exploration. Key log sources:

- Backend application logs (JSON format via Logback)
- Analytics worker logs (structured JSON via Python logging)
- Caddy access logs
- TimescaleDB logs (slow queries, connection pool)
- Docker daemon logs

---

## Phase 8: Cost Management

### 8.1 Budget Alerts

**`modules/budget/main.tf` (AWS):**
- `aws_budgets_budget` — monthly cost budget with alerts at 50%, 80%, 100%
- Separate budgets for staging and production

| Environment | Monthly Budget | Alert at 50% | Alert at 80% | Alert at 100% |
|:------------|:---------------|:-------------|:-------------|:--------------|
| Staging | $30 | $15 | $24 | $30 |
| Production | $200 | $100 | $160 | $200 |
| Forecast (spot) | $20 | $10 | $16 | $20 |

**GCP equivalent:** `google_billing_budget` via `google_billing_account`.

**Azure equivalent:** `azurerm_consumption_budget`.

### 8.2 Detailed Monthly Cost Breakdown (us-east-1)

Pricing based on AWS public rates as of June 2026.

#### Production Environment

| # | Resource | Specification | Unit Cost | Monthly Cost | Notes |
|:--|:---------|:-------------|:----------|:-------------|:------|
| 1 | EC2 instance (on-demand) | `t3.xlarge`, 4 vCPU / 16 GB | $0.1664/hr | **$121.47** | 730 hrs/mo; hosts backend, analytics, dashboard, Caddy, monitoring |
| 2 | EC2 — Reserved Instance (1yr, no upfront) | `t3.xlarge` | $0.1044/hr | **$76.14** | 37% savings; applies after commitment |
| 3 | Elastic IP | 1 × attached | $0.005/hr | **$3.65** | Stable public IP for DNS |
| 4 | EBS — root volume | 100 GB gp3 | $0.08/GB | **$8.00** | OS + Docker images + app data |
| 5 | EBS — TimescaleDB data | 100 GB gp3, encrypted | $0.08/GB | **$8.00** | Separate volume for DB; encrypted via KMS |
| 6 | EBS — monitoring data | 10 GB gp3 | $0.08/GB | **$0.80** | Prometheus + Grafana + Loki persistence |
| 7 | KMS key | 1 × customer-managed symmetric | $1.00/key | **$1.00** | EBS encryption + SSM SecureString; API calls within free tier |
| 8 | S3 — forecast results (Plan 13) | ~5 GB Standard | $0.023/GB | **$0.12** | 30-day lifecycle; shared with spot pipeline |
| 9 | S3 — pg_dump backups | ~14 GB Standard (7 daily × ~2 GB) | $0.023/GB | **$0.32** | 30-day retention; lifecycle expires old dumps |
| 10 | S3 — EBS snapshots | ~20 GB incremental (30 daily) | $0.05/GB | **$1.00** | Incremental; ~$0.05/GB/month for changed blocks |
| 11 | Route53 — hosted zone | 1 zone (`tickonomics.io`) | $0.50/zone | **$0.50** | Shared between staging + production |
| 12 | Route53 — DNS queries | ~3M queries/mo (estimated) | $0.40/M | **$1.20** | Dashboard users + API calls + health checks |
| 13 | CloudWatch — dashboard | 1 dashboard (Plan 13) | $3.00/dashboard | **$3.00** | Spot instance monitoring; first 3 free → $0 if only 1 |
| 14 | CloudWatch — alarms | 3 alarms (Plan 13) | $0.10/alarm | **$0.00** | First 10 alarms free |
| 15 | CloudWatch — log ingestion | ~2 GB/mo from spot instances | $0.50/GB | **$1.00** | Spot bootstrap + Lambda logs |
| 16 | SSM Parameter Store | 6 parameters (Standard tier) | — | **$0.00** | Standard tier is free |
| 17 | Lambda — backup runner | ~30 invocations/mo, ~30s each | — | **$0.00** | Within free tier (1M req + 400K GB-sec) |
| 18 | Lambda — spot orchestrator (Plan 13) | ~100 invocations/mo | — | **$0.00** | Within free tier |
| 19 | AWS Budgets | 2 action-enabled budgets | — | **$0.00** | First 2 are free |
| 20 | Data transfer (outbound) | ~15 GB/mo (API responses, dashboard) | First 100 GB free | **$0.00** | Under free tier |
| 21 | ACM / TLS certificates | 0 (Caddy + Let's Encrypt) | — | **$0.00** | No ACM needed; Caddy auto-provisions |
| 22 | SSM Session Manager | Shell access (replaces SSH) | — | **$0.00** | Free |
| | | | | | |
| | **Total (Production, On-Demand)** | | | **$150.06** | Full pay-as-you-go |
| | **Total (Production, Reserved Instance)** | | | **$104.73** | With 1-year RI on EC2 |

#### Staging Environment

| # | Resource | Specification | Unit Cost | Monthly Cost | Notes |
|:--|:---------|:-------------|:----------|:-------------|:------|
| 1 | EC2 instance (on-demand) | `t3.large`, 2 vCPU / 8 GB | $0.0832/hr | **$60.74** | Smaller instance for staging |
| 2 | Elastic IP | 1 × attached | $0.005/hr | **$3.65** | Stable public IP |
| 3 | EBS — root volume | 50 GB gp3 | $0.08/GB | **$4.00** | OS + Docker images |
| 4 | EBS — TimescaleDB data | 25 GB gp3, encrypted | $0.08/GB | **$2.00** | Smaller DB volume |
| 5 | EBS — monitoring data | 10 GB gp3 | $0.08/GB | **$0.80** | Same monitoring stack |
| 6 | KMS key | Shared with production | — | **$0.00** | Same key, no extra cost |
| 7 | S3 — pg_dump backups | ~7 GB (7 daily × ~1 GB) | $0.023/GB | **$0.16** | 7-day retention |
| 8 | S3 — EBS snapshots | ~8 GB incremental (7 daily) | $0.05/GB | **$0.40** | 7-day retention |
| 9 | Route53 — DNS queries | ~0.5M queries/mo | $0.40/M | **$0.20** | Low traffic |
| 10 | CloudWatch — log ingestion | ~1 GB/mo | $0.50/GB | **$0.50** | |
| 11 | SSM / Lambda / Budgets | Same free-tier services | — | **$0.00** | |
| | | | | | |
| | **Total (Staging)** | | | **$72.45** | On-demand only (no RI for staging) |

#### Spot Forecast Pipeline (from Plan 13, shared infrastructure)

| # | Resource | Specification | Monthly Cost | Notes |
|:--|:---------|:-------------|:-------------|:------|
| 1 | Spot instance compute | 20 runs × ~1 hr × $0.09/hr | **$1.80** | `c5.2xlarge` spot |
| 2 | Network (data transfer) | 20 runs × ~0.75 GB × $0.09/GB | **$1.35** | Image pulls + result upload |
| 3 | ECR storage | 2 repos × ~2 GB (10 tags each) | **$0.09** | $0.10/GB after 500 MB free |
| 4 | S3 — forecast results | Included in production S3 above | **$0.00** | Shared bucket |
| | | | | |
| | **Total (Spot Forecast)** | | **$3.24** | Per 20 weekday runs |

#### Combined Monthly Summary

| Environment | On-Demand | Reserved Instance | Notes |
|:------------|:----------|:------------------|:------|
| Staging | $72.45 | — | No RI (lower commitment) |
| Production | $150.06 | $104.73 | RI saves $45/mo |
| Spot Forecast (shared) | $3.24 | $3.24 | Spot pricing, no commitment |
| Route53 zone (shared) | included | included | $0.50 counted in production |
| | | | |
| **Grand Total (On-Demand)** | **$225.75** | | Staging + Production + Forecast |
| **Grand Total (with RI)** | | **$180.42** | Production RI, staging on-demand |

#### Cost Reduction Levers

| Lever | Savings | Trade-off |
|:------|:--------|:----------|
| EC2 Reserved Instance (1yr, prod) | −$45.33/mo | 1-year commitment; break-even after ~2 months |
| EC2 Savings Plan (1yr, compute) | −$40–50/mo | More flexible than RI; applies to any instance family |
| Graviton/ARM migration (`t4g.xlarge`) | −$30–40/mo | Requires ARM-compatible Docker images; Java 25 + Python 3.12 both support ARM |
| Stop staging nights/weekends | −$36/mo | Staging unavailable off-hours; use Lambda to start/stop on schedule |
| Reduce staging to `t3.medium` | −$30/mo | May OOM under load (4 GB RAM for all services) |
| S3 Glacier for old backups | −$0.20/mo | Negligible; not worth the complexity |

---

## Phase 9: CI/CD Integration — Replacing Placeholders

### 9.1 Update `deploy-staging.yml`

Replace the placeholder deploy step with actual SSH-based deployment:

```yaml
deploy-staging:
  needs: build-and-push
  runs-on: ubuntu-latest
  environment: staging
  steps:
    - Checkout
    - name: Deploy to staging via SSM
      run: |
        aws ssm send-command \
          --instance-ids ${{ secrets.STAGING_INSTANCE_ID }} \
          --document-name "AWS-RunShellScript" \
          --parameters commands=[
            "cd /opt/tickonomics",
            "aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $REGISTRY_URL",
            "docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml pull",
            "docker compose -f docker-compose.prod.yml -f docker-compose.monitoring.yml up -d --remove-orphans",
            "docker compose -f docker-compose.prod.yml exec -T backend sh -c 'while ! curl -sf http://localhost:8080/health; do sleep 2; done'"
          ] \
          --timeout-seconds 300 \
          --comment "Deploy staging $(echo ${{ github.sha }} | cut -c1-7)"

    - name: Smoke test
      run: |
        for i in $(seq 1 30); do
          if curl -sf https://api.staging.tickonomics.io/health; then
            echo "Staging healthy"
            exit 0
          fi
          sleep 10
        done
        echo "Staging health check failed"
        exit 1
```

### 9.2 Update `deploy-production.yml`

Same pattern with production secrets and instance ID. Add manual approval gate (already exists via `environment: production`).

### 9.3 Add GCP and Azure Image Push to `forecast-deploy.yml`

Currently only ECR push exists. Add provider-conditional steps:

```yaml
- name: Push to GCP Artifact Registry
  if: steps.detect-provider.outputs.provider == 'gcp'
  run: |
    gcloud auth configure-docker us-central1-docker.pkg.dev
    docker push us-central1-docker.pkg.dev/${{ secrets.GCP_PROJECT }}/tickonomics/backend:${{ github.sha }}

- name: Push to Azure ACR
  if: steps.detect-provider.outputs.provider == 'azure'
  run: |
    az acr login --name ${{ secrets.ACR_NAME }}
    docker push ${{ secrets.ACR_NAME }}.azurecr.io/tickonomics/backend:${{ github.sha }}
```

---

## Phase 10: WAF and Rate Limiting

### 10.1 Strategy — Caddy Rate Limiting (MVP)

For the initial deployment, rate limiting is handled at the Caddy layer:

```caddyfile
api.tickonomics.io {
    rate_limit {
        zone api_zone {
            key    {remote_host}
            events 100
            window 1m
        }
    }
    reverse_proxy backend:8080
}
```

### 10.2 Future — AWS WAF (Phase 2)

When the dashboard is publicly accessible, add WAF:

- `aws_wafv2_web_acl` with managed rules (AWSManagedRulesCommonRuleSet, AWSManagedRulesSQLiRuleSet)
- Rate-based rule: block IP after 2000 requests/5min
- Associate with CloudFront distribution (if added) or ALB

---

## Phase 11: Local Server Deployment

When a local server (bare-metal workstation, homelab, or on-premises rack) is available, the entire production stack can run without any cloud resources. This eliminates all cloud costs (~$180–226/month) and provides significantly better performance for compute-heavy workloads (GARCH training, backtesting, regime detection).

### 11.1 Target Hardware Profile

| Component | Minimum | Recommended | This Plan Targets |
|:----------|:--------|:------------|:------------------|
| CPU | 4 cores | 8+ cores | 16 cores (e.g., AMD Ryzen AI MAX+ 395, Zen 5) |
| RAM | 16 GB | 32 GB | 128 GB |
| Storage | 100 GB SSD | 500 GB NVMe | 500+ GB NVMe |
| Network | Broadband | Stable broadband | Stable broadband |
| OS | Ubuntu 22.04 / Fedora 43+ | Ubuntu 24.04 LTS | Linux (any modern distro) |

**The project requires ~8 GB RAM at Docker limits (~4 GB at reservations), so even modest hardware suffices.** The recommended specs provide headroom for the observability stack, large TimescaleDB caches, and concurrent forecast runs.

### 11.2 Architecture Comparison

```
Cloud (Phases 1–10):                         Local (Phase 11):
┌──────────────────────────────┐             ┌──────────────────────────────┐
│ Route53 → EC2 (t3.xlarge)   │             │ LAN / localhost              │
│   ├── Caddy (TLS)           │             │   ├── Caddy (self-signed     │
│   ├── Backend (Java)        │             │   │   or Let's Encrypt DNS)  │
│   ├── Analytics (Python)    │             │   ├── Backend (Java)         │
│   ├── Dashboard (Next.js)   │             │   ├── Analytics (Python)     │
│   └── TimescaleDB (EBS)     │             │   ├── Dashboard (Next.js)    │
│ SSM Parameter Store (secrets)│             │   └── TimescaleDB (NVMe)     │
│ Lambda → EBS snapshot (bkp) │             │ .env file (secrets)          │
│ CloudWatch (alerts)          │             │ crontab → pg_dump (bkp)      │
│ AWS Budgets (cost alerts)   │             │ Prometheus + Grafana (mon.)  │
│ ~$150/mo (prod on-demand)   │             │ ~$5–10/mo (electricity)      │
└──────────────────────────────┘             └──────────────────────────────┘
```

### 11.3 `docker-compose.local.yml`

Full-stack compose for local server. Same services as `docker-compose.prod.yml` but with local adaptations:

```yaml
services:
  # --- Core Services ---
  backend:
    image: tickonomics-backend:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=virtual-threads
      - SPRING_DATASOURCE_URL=jdbc:postgresql://timescaledb:5432/tickonomics
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - FINNHUB_API_KEY=${FINNHUB_API_KEY}
      - ALPHAVANTAGE_API_KEY=${ALPHAVANTAGE_API_KEY}
      - FRED_API_KEY=${FRED_API_KEY}
      - MONITOR_TRACING_ENABLED=true
      - MONITOR_TRACING_EXPORTER=otlp
      - MONITOR_TRACING_ENDPOINT=http://jaeger:4317
      - MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus,metrics
    depends_on:
      timescaledb:
        condition: service_healthy
    volumes:
      - ingestion_overflow:/data/overflow
    restart: unless-stopped

  analytics-worker:
    image: tickonomics-analytics:latest
    ports:
      - "8001:8001"
    environment:
      - TRACING_ENABLED=true
      - TRACING_EXPORTER=otlp
      - TRACING_ENDPOINT=http://jaeger:4317
    restart: unless-stopped

  dashboard:
    image: tickonomics-dashboard:latest
    ports:
      - "3000:3000"
    depends_on:
      - backend
    restart: unless-stopped

  timescaledb:
    image: timescale/timescaledb:latest-pg16
    environment:
      - POSTGRES_DB=tickonomics
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    command: >
      postgres
      -c shared_buffers=4GB
      -c effective_cache_size=12GB
      -c work_mem=64MB
      -c maintenance_work_mem=512MB
      -c checkpoint_completion_target=0.9
      -c wal_buffers=64MB
      -c default_statistics_target=200
      -c random_page_cost=1.1
      -c effective_io_concurrency=200
      -c max_connections=50
    volumes:
      - timescaledb_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tickonomics"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # --- Observability ---
  jaeger:
    image: jaegertracing/all-in-one:1.64
    ports:
      - "16686:16686"
      - "4317:4317"
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    restart: unless-stopped

  prometheus:
    image: prom/prometheus:v2.54.1
    volumes:
      - ../monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=90d"
    ports:
      - "9090:9090"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.3.0
    volumes:
      - grafana_data:/var/lib/grafana
      - ../monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ../monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
    ports:
      - "3001:3000"
    restart: unless-stopped

  loki:
    image: grafana/loki:3.2.0
    volumes:
      - ../monitoring/loki.yml:/etc/loki/config.yml
      - loki_data:/loki
    ports:
      - "3100:3100"
    restart: unless-stopped

  promtail:
    image: grafana/promtail:3.2.0
    volumes:
      - /var/log:/var/log:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - ../monitoring/promtail.yml:/etc/promtail/config.yml
    command: -config.file=/etc/promtail/config.yml
    restart: unless-stopped

  # --- Reverse Proxy ---
  caddy:
    image: caddy:2-alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile.local:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config
    restart: unless-stopped

volumes:
  timescaledb_data:
  ingestion_overflow:
  prometheus_data:
  grafana_data:
  loki_data:
  caddy_data:
  caddy_config:
```

**Key differences from cloud `docker-compose.prod.yml`:**

| Aspect | Cloud (prod.yml) | Local (local.yml) |
|:-------|:-----------------|:-------------------|
| Image source | ECR registry (`${REGISTRY}/backend:tag`) | Local build (`tickonomics-backend:latest`) |
| TimescaleDB `shared_buffers` | 512 MB (16 GB host) | 4 GB (128 GB host) |
| TimescaleDB `effective_cache_size` | 1.5 GB | 12 GB |
| Prometheus retention | 30d | 90d (disk is cheap) |
| Port exposure | Caddy only (80/443) | All ports exposed for local debugging |
| Jaeger | Not included | Included (local tracing is useful) |
| Resource limits | `mem_limit` / `cpuset` caps | No limits (use full hardware) |

### 11.4 Local Secrets Management

No SSM/Secret Manager needed. Use a `.env.local` file:

```bash
# .env.local (chmod 600, never committed)
DB_PASSWORD=<your-secure-password>
FINNHUB_API_KEY=<your-finnhub-key>
ALPHAVANTAGE_API_KEY=<your-alphavantage-key>
FRED_API_KEY=<your-fred-key>
GRAFANA_PASSWORD=<your-grafana-admin-password>
```

**`deploy-local.sh`** loads this file:

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env.local"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.local.yml"

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: ${ENV_FILE} not found. Copy .env.local.example and fill in values."
  exit 1
fi

# Build images from source (if not already built)
if [ "${BUILD_IMAGES:-false}" = "true" ]; then
  echo "Building images from source..."
  docker compose -f docker-compose.yml build
  echo "Images built."
fi

# Start all services
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans

# Wait for backend healthy
echo "Waiting for backend..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    echo "All services healthy."
    exit 0
  fi
  sleep 5
done
echo "WARNING: Backend health check timeout. Check: docker compose -f ${COMPOSE_FILE} ps"
exit 1
```

### 11.5 Local DNS and TLS

Three options depending on network setup:

**Option A — LAN access only (simplest):**
```
# Caddyfile.local
:80 {
    reverse_proxy /api/* backend:8080
    reverse_proxy /* dashboard:3000
}
```
No TLS, no DNS. Access via `http://<server-ip>`. Suitable for development and single-user access.

**Option B — Local domain with self-signed TLS:**
```
# Caddyfile.local
https://tickonomics.local {
    tls internal
    reverse_proxy /api/* backend:8080
    reverse_proxy /* dashboard:3000
}
```
Caddy generates a self-signed certificate. Add `tickonomics.local` to `/etc/hosts` on client machines. Browser will show a certificate warning (acceptable for personal use).

**Option C — Public domain with Let's Encrypt (if server is publicly reachable):**
```
# Caddyfile.local — same as cloud Caddyfile
app.tickonomics.io {
    reverse_proxy dashboard:3000
}
api.tickonomics.io {
    reverse_proxy backend:8080
}
```
Requires ports 80/443 open on the router/firewall and DNS A records pointing to the server's public IP. Caddy auto-provisions Let's Encrypt certificates. This makes the local server indistinguishable from a cloud deployment.

### 11.6 Local Automated Backups

No Lambda/EBS snapshots needed. A simple cron script:

**`backup-local.sh`:**
```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/tickonomics/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.local.yml}"

mkdir -p "${BACKUP_DIR}"

# pg_dump (logical backup)
docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
  pg_dump -U tickonomics --format=custom \
  > "${BACKUP_DIR}/tickonomics_${TIMESTAMP}.dump"

# Compress
gzip "${BACKUP_DIR}/tickonomics_${TIMESTAMP}.dump"

# Optional: rsync to external drive / NAS / remote server
if [ -n "${RSYNC_TARGET:-}" ]; then
  rsync -az --delete "${BACKUP_DIR}/" "${RSYNC_TARGET}"
fi

# Clean old backups
find "${BACKUP_DIR}" -name "tickonomics_*.dump.gz" -mtime +${RETENTION_DAYS} -delete

echo "Backup completed: tickonomics_${TIMESTAMP}.dump.gz"
```

**`restore-local.sh`:**
```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/tickonomics/backups}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.local.yml}"

LATEST=$(ls -t "${BACKUP_DIR}"/tickonomics_*.dump.gz | head -1)
if [ -z "${LATEST}" ]; then
  echo "ERROR: No backup found in ${BACKUP_DIR}"
  exit 1
fi

echo "Restoring from: ${LATEST}"
gunzip --stdout "${LATEST}" | \
  docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
  pg_restore -U tickonomics --clean --if-exists -d tickonomics

echo "Restore completed."
```

**Scheduling (crontab):**
```crontab
# /etc/cron.d/tickonomics-backup
# Daily backup at 03:00
0 3 * * * tickonomics /opt/tickonomics/infra/local/scripts/backup-local.sh >> /var/log/tickonomics-backup.log 2>&1
```

### 11.7 Local Observability

The observability stack (Prometheus + Grafana + Loki + Promtail + Jaeger) is included directly in `docker-compose.local.yml` — no separate Terraform provisioning needed. The same Grafana dashboards and Prometheus alert rules from Phase 7 apply.

**Local alert routing** — instead of SNS/email, use a simpler approach:

| Alert Channel | Setup |
|:--------------|:------|
| Grafana built-in alerts | Grafana → Alert Rules → email or webhook |
| Log file monitoring | `tail -f /var/log/tickonomics-backup.log` |
| Health check cron | `*/5 * * * * curl -sf http://localhost:8080/health \|\| echo "BACKEND DOWN" >> /var/log/tickonomics-health.log` |

### 11.8 Local CI/CD Integration

Two options for deploying to a local server from GitHub Actions:

**Option A — GitHub Actions self-hosted runner (recommended):**

Install a self-hosted runner on the local server:
```bash
# One-time setup (follow GitHub repo → Settings → Actions → Runners → New runner)
mkdir -p /opt/actions-runner && cd /opt/actions-runner
./config.sh --url https://github.com/<owner>/tickonomics --token <TOKEN>
./svc.sh install
./svc.sh start
```

Update `deploy-staging.yml` to target the self-hosted runner:
```yaml
deploy-staging:
  runs-on: self-hosted
  environment: staging
  steps:
    - Checkout
    - name: Build and deploy locally
      run: |
        docker compose -f docker-compose.yml build
        docker compose --env-file .env.local \
          -f infra/local/docker-compose.local.yml up -d --remove-orphans
    - name: Smoke test
      run: curl -sf http://localhost:8080/health
```

**Option B — SSH deploy from cloud runner:**

```yaml
deploy-local:
  runs-on: ubuntu-latest
  steps:
    - name: Deploy via SSH
      uses: appleboy/ssh-action@v1
      with:
        host: ${{ secrets.LOCAL_SERVER_IP }}
        username: tickonomics
        key: ${{ secrets.LOCAL_SSH_KEY }}
        script: |
          cd /opt/tickonomics
          git pull
          docker compose -f docker-compose.yml build
          docker compose --env-file .env.local \
            -f infra/local/docker-compose.local.yml up -d --remove-orphans
```

### 11.9 Performance Tuning for High-End Hardware

With 128 GB RAM and 16+ cores, the default configurations leave significant performance on the table. Tuning recommendations:

| Component | Cloud Default | Local Tuned (128 GB RAM, 16 cores) | Impact |
|:----------|:--------------|:------------------------------------|:-------|
| TimescaleDB `shared_buffers` | 512 MB | **4 GB** | Hot data stays in memory; disk reads eliminated |
| TimescaleDB `effective_cache_size` | 1.5 GB | **12 GB** | Planner assumes more cache → better query plans |
| TimescaleDB `work_mem` | 16 MB | **64 MB** | Larger sorts/hashes in memory |
| TimescaleDB `maintenance_work_mem` | 128 MB | **512 MB** | Faster vacuuming, index creation |
| TimescaleDB `max_connections` | 50 | **50** (unchanged) | Virtual threads don't need more |
| HikariCP pool | 20 connections | **20** (unchanged) | 20 is adequate for single-user |
| Prometheus retention | 30d | **90d** | Longer history for trend analysis |
| Docker `mem_limit` | 1–4 GB per service | **No limit** | Services use only what they need |
| Docker `cpuset` | Capped per service | **No limit** | Full 16-core utilization |

### 11.10 Cost Comparison — Local vs. Cloud

| Item | Cloud (AWS Production) | Local Server | Notes |
|:-----|:-----------------------|:-------------|:------|
| Compute | $121.47/mo (t3.xlarge on-demand) | $0 (sunk cost) | Hardware amortized over 3–5 years |
| Storage (DB) | $8.00/mo (100 GB EBS) | $0 (included in server) | NVMe SSD |
| Storage (backups) | $0.32/mo (S3) | $0 (local disk) | Add NAS/external for redundancy |
| Networking | $3.65/mo (EIP) + $1.20/mo (DNS) | $0 (LAN) or ISP cost | Public access needs port forwarding |
| Observability | $3.00/mo (CloudWatch) | $0 (self-hosted) | Prometheus + Grafana included |
| Secrets management | $1.00/mo (KMS) | $0 (.env file) | Acceptable for single-user |
| Backup automation | $0.00 (Lambda free tier) | $0 (cron + pg_dump) | |
| TLS certificates | $0.00 (Caddy + Let's Encrypt) | $0.00 (same) | |
| Budget alerts | $0.00 (AWS Budgets free tier) | N/A | Monitor electricity bill instead |
| Electricity | — | **~$5–10/mo** | 55–120W TDP × $0.12/kWh × 730 hrs |
| **Total** | **~$150/mo** (on-demand) | **~$5–10/mo** | |

**Break-even:** If the local server costs ~$2,000–3,000, it pays for itself in **13–20 months** vs. AWS on-demand. The performance advantage (7–10× faster CPU, 16× more RAM) is free.

### 11.11 Hybrid Deployment Option

Cloud and local are not mutually exclusive. A hybrid approach maximizes benefits:

| Component | Local Server | Cloud (AWS) |
|:----------|:-------------|:------------|
| Production hosting | Primary (daily use) | Disaster recovery failover |
| Forecast pipeline | `forecast-local.sh` (10–20 min) | Spot instances (Plan 13) if server offline |
| Dashboard | `app.tickonomics.local` (LAN) | `app.staging.tickonomics.io` (public, for demos) |
| Backups | Primary: local disk + NAS | Secondary: `rsync` to S3 (cross-site) |
| CI/CD | Self-hosted runner | GitHub Actions (cloud runner) as fallback |

Hybrid backup script addition:
```bash
# In backup-local.sh, after local backup:
if [ "${S3_BACKUP:-false}" = "true" ]; then
  aws s3 sync "${BACKUP_DIR}/" "s3://${S3_BUCKET}/backups/" --delete
fi
```

---

## Implementation Sequence

### Step 1 — Secrets management (Phase 3)
1. Create `modules/secrets/` with SSM parameters
2. Migrate all sensitive variables from Terraform state to SSM
3. Update cloud-init to pull secrets at boot
4. Verify: `terraform apply`, SSH to instance, confirm secrets are not in user-data

### Step 2 — Network hardening + hosting module (Phase 5 + Phase 1)
1. Create `modules/hosting/` with hardened security groups
2. Remove SSH ingress from `0.0.0.0/0` — SSM-only access
3. Add Caddy reverse proxy to `docker-compose.prod.yml`
4. Verify: instance accessible via HTTPS only, no direct port 22

### Step 3 — Database module (Phase 2)
1. Create `modules/database/` with EBS volume for TimescaleDB
2. Add production-tuned TimescaleDB config to Docker Compose
3. Verify: TimescaleDB healthy, Flyway migrations apply

### Step 4 — DNS + TLS (Phase 4)
1. Create `modules/dns-tls/` with Route53 zone and records
2. Verify: `api.tickonomics.io` resolves, Caddy provisions TLS cert
3. Verify: `app.tickonomics.io` resolves, dashboard loads over HTTPS

### Step 5 — Automated backups (Phase 6)
1. Create `modules/backup/` with daily snapshot + pg_dump Lambda
2. Verify: backup runs, snapshot appears in AWS console, pg_dump in S3
3. Test restore: create new instance from snapshot, verify data integrity

### Step 6 — Observability stack (Phase 7)
1. Create `modules/observability/` with Prometheus + Grafana + Loki + Promtail
2. Add Micrometer Prometheus endpoint to backend
3. Create Grafana dashboards and alert rules
4. Verify: metrics flowing, dashboards rendering, test alert fires

### Step 7 — Cost management (Phase 8)
1. Create `modules/budget/` with AWS Budgets
2. Verify: budget alerts configured, email notifications received on threshold

### Step 8 — CI/CD integration (Phase 9)
1. Replace `deploy-staging.yml` placeholder with SSM-based deployment
2. Replace `deploy-production.yml` placeholder with SSM-based deployment
3. Add GCP/Azure image push to `forecast-deploy.yml`
4. Verify: push to main triggers staging deploy, health check passes

### Step 9 — WAF and rate limiting (Phase 10)
1. Add Caddy rate limiting to the production Caddyfile
2. Verify: burst requests from single IP are throttled

### Step 10 — Local server deployment (Phase 11)
**Skip if only using cloud.** Execute if a local server is available:
1. Create `infra/local/docker-compose.local.yml` — full stack compose with tuned configs
2. Create `infra/local/.env.local.example` — secrets template
3. Create `infra/local/Caddyfile.local` — choose Option A/B/C based on network
4. Create `infra/local/scripts/deploy-local.sh` — build and start all services
5. Build images: `docker compose -f docker-compose.yml build`
6. Configure secrets: `cp .env.local.example .env.local && $EDITOR .env.local`
7. Deploy: `./infra/local/scripts/deploy-local.sh` — verify all services healthy
8. Create `infra/local/scripts/backup-local.sh` and `restore-local.sh`
9. Create `infra/local/scripts/setup-local-cron.sh` and install crontab
10. Verify: `curl http://localhost:8080/health` returns 200
11. Verify: Grafana at `http://localhost:3001` shows live metrics
12. Verify: backup runs and `pg_dump` file appears in backup directory
13. (Optional) Install GitHub Actions self-hosted runner for CI/CD
14. (Optional) Set up hybrid backup: local + rsync to S3

---

## Verification

### Infrastructure

- [ ] `terraform fmt -check && terraform validate && tflint` pass for all environments
- [ ] `terraform plan` shows no security groups with SSH ingress from `0.0.0.0/0`
- [ ] No sensitive values appear in `terraform show` output (all moved to SSM)
- [ ] `terraform apply` provisions: EC2 instance, EBS volumes, Route53 zone, SSM parameters, backup Lambda, budget alerts

### Security

- [ ] SSH not accessible from public internet (SSM Session Manager only)
- [ ] TLS certificates provisioned automatically via Caddy
- [ ] API keys not visible in Terraform state (stored in SSM SecureString)
- [ ] Security group allows only 80/443 inbound + all outbound
- [ ] Rate limiting blocks bursts > 100 req/min per IP

### Operational

- [ ] `curl https://api.tickonomics.io/health` returns 200
- [ ] `curl https://app.tickonomics.io` loads dashboard
- [ ] Daily backup Lambda runs successfully (check CloudWatch logs)
- [ ] EBS snapshot exists in AWS console after first backup
- [ ] `pg_dump` file exists in S3 backup bucket after first backup
- [ ] Prometheus scrapes all 4 service endpoints
- [ ] Grafana dashboards render with live data
- [ ] Budget alert email received (test by setting threshold to $0.01)

### DR

- [ ] Restore from latest snapshot: new instance boots, all services healthy
- [ ] Restore from pg_dump: data intact, no migration errors
- [ ] Total restore time < 1 hour (RTO)

### CI/CD

- [ ] Push to `main` triggers staging deploy via SSM
- [ ] Staging health check passes within 5 minutes
- [ ] Manual production promotion works with approval gate
- [ ] GCP/Azure image push succeeds (if multi-cloud is active)

### Local Server

- [ ] `deploy-local.sh` starts all 10 containers (8 services + 2 volumes)
- [ ] `curl http://localhost:8080/health` returns 200 within 2 minutes
- [ ] `curl http://localhost:8001/health` returns 200
- [ ] `curl http://localhost:3000` loads dashboard
- [ ] `curl http://localhost:9090/api/v1/targets` shows all Prometheus targets UP
- [ ] Grafana at `http://localhost:3001` renders dashboards with live data
- [ ] `backup-local.sh` produces a `.dump.gz` file and cleans old backups
- [ ] `restore-local.sh` restores from backup without errors
- [ ] Crontab installed: `crontab -l` shows backup and health check entries
- [ ] `.env.local` has mode `600` and is not tracked by git
- [ ] (If public) `curl https://api.tickonomics.io/health` returns 200 via Caddy TLS
- [ ] (If hybrid) `rsync` to S3 completes after local backup

---

## Files Created/Modified Summary

**Created (Terraform — ~25 files):**
- `infra/terraform/modules/hosting/` (3 files + templates)
- `infra/terraform/modules/database/` (3 files)
- `infra/terraform/modules/dns-tls/` (3 files)
- `infra/terraform/modules/secrets/` (3 files)
- `infra/terraform/modules/observability/` (3 files + dashboards + alerts)
- `infra/terraform/modules/backup/` (3 files + Lambda source)
- `infra/terraform/modules/budget/` (3 files)
- `infra/terraform/environments/aws/staging.tf`
- `infra/terraform/environments/aws/production.tf`
- `infra/terraform/environments/gcp/staging.tf`
- `infra/terraform/environments/gcp/production.tf`
- `infra/terraform/environments/azure/staging.tf`
- `infra/terraform/environments/azure/production.tf`

**Created (Local Server — 9 files):**
- `infra/local/docker-compose.local.yml` — full-stack compose with tuned configs
- `infra/local/Caddyfile.local` — reverse proxy config (LAN / TLS options)
- `infra/local/.env.local.example` — secrets template
- `infra/local/scripts/deploy-local.sh` — build and start all services
- `infra/local/scripts/backup-local.sh` — pg_dump + filesystem backup
- `infra/local/scripts/restore-local.sh` — restore from backup
- `infra/local/scripts/setup-local-cron.sh` — install crontab entries
- `infra/local/crontab` — backup and maintenance schedule
- `infra/local/systemd/tickonomics-backup.timer` — alternative to cron

**Created (Application — 5 files):**
- `monitoring/prometheus.yml` — scrape configuration
- `monitoring/loki.yml` — Loki config
- `monitoring/promtail.yml` — log shipping config
- `monitoring/grafana/datasources/` — auto-provisioned data sources
- `monitoring/grafana/dashboards/` — 4 JSON dashboard definitions

**Modified (Application — 3 files):**
- `app/src/main/resources/application.yml` — add `management.endpoints.web.exposure.include: prometheus`
- `docker-compose.prod.yml` — add Caddy, Prometheus, Grafana, Loki, Promtail services
- `infra/terraform/modules/networking/main.tf` — tighten security group rules

**Modified (CI/CD — 3 files):**
- `.github/workflows/deploy-staging.yml` — replace placeholder with SSM deploy
- `.github/workflows/deploy-production.yml` — replace placeholder with SSM deploy
- `.github/workflows/forecast-deploy.yml` — add GCP/Azure image push

---

## Relationship to Plan 13 (Spot Forecast)

| Concern | Plan 13 (Spot Forecast) | This Plan (Cloud Hosting) | This Plan (Local Hosting) |
|:--------|:------------------------|:--------------------------|:--------------------------|
| Compute | Ephemeral spot instances | Persistent EC2 on-demand | Bare-metal / homelab |
| Database | Self-contained TimescaleDB on EBS | Self-contained TimescaleDB on EBS | Self-contained TimescaleDB on NVMe |
| Registry | ECR/GCR/ACR (shared) | Same registries | Local Docker build |
| DNS | None | Route53 + Caddy TLS | `/etc/hosts` or Caddy TLS |
| Secrets | Cloud-init variables (to be migrated) | SSM Parameter Store | `.env.local` file |
| Monitoring | CloudWatch alarms | Prometheus + Grafana + Loki | Same stack, self-hosted |
| Backup | None (ephemeral) | Daily snapshots + pg_dump | Daily pg_dump + filesystem |
| Cost | ~$0.37/run | ~$100-150/month | ~$5-10/month (electricity) |
| CI/CD | `forecast-deploy.yml` | SSM deploy | Self-hosted runner or SSH |

Cloud and local plans share the same VPC, container registries, and S3 buckets. The networking module from Plan 13 is reused and extended (production hosting adds the hardened security group). Local deployment can run standalone or hybrid (primary local + cloud disaster recovery).

---

## Changelog

| Version | Change |
|:--------|:-------|
| v1 | Initial plan covering 10 phases: hosting, database, secrets, DNS/TLS/CDN, network hardening, backups, observability, cost management, CI/CD integration, WAF/rate limiting. |
| v2 | Added Phase 11: Local Server Deployment. Added `infra/local/` directory structure with full-stack Docker Compose, local secrets via `.env`, local backup/restore scripts, local DNS/TLS options (LAN / self-signed / Let's Encrypt), performance tuning for 128 GB / 16-core hardware, cost comparison ($5-10/mo vs. $150/mo cloud), hybrid deployment option, and self-hosted CI/CD runner. Updated directory structure, implementation sequence, verification checklist, files summary, and relationship table. |
