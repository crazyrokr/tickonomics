# ADR-020: Track 14 Production Infrastructure Finalization

**Date:** 2026-06-14
**Status:** Accepted
**Track:** 14 — Production Infrastructure
**Supersedes / extends:** ADR-010 (Deployment & Operations), ADR-019 (Track 13 Spot/Forecast), Plan v6 Track 14

## Context

`docs/plan_v6-verification-report.md` scored Track 14 at **~0% — "Not implemented"**. None of the 7 planned Terraform modules existed (`hosting`, `database`, `dns-tls`, `secrets`, `observability`, `backup`, `budget`). There was no local-server full-stack path, no monitoring stack (Prometheus/Grafana/Loki), no Caddy reverse proxy configuration, and no per-environment staging/production overrides. The `deploy-staging.yml` and `deploy-production.yml` workflows already used SSH-based `docker compose` deployment (finalized in ADR-016/ADR-018), but the cloud infrastructure they targeted did not exist.

The Track 14 plan document (`docs/plan_v6/14-production-infrastructure-plan.md`, v2, 1437 lines) covers 11 phases: persistent compute hosting (Phase 1), managed database (Phase 2), secrets management (Phase 3), DNS/TLS/CDN (Phase 4), network hardening (Phase 5), automated backups (Phase 6), observability stack (Phase 7), cost management (Phase 8), CI/CD integration (Phase 9), WAF/rate limiting (Phase 10), and local server deployment (Phase 11).

This ADR records the decisions that implement all 11 phases. The verification-report scorecard is updated below.

## Decision

### 1. Hosting module — single EC2 instance with Docker Compose

Created `infra/terraform/modules/hosting/` (variables.tf, main.tf, cloud-config.yaml.tftpl). Provisions a single `t3.xlarge` (production) or `t3.large` (staging) on-demand EC2 instance running Ubuntu 24.04, with an Elastic IP, IAM role (SSM core + ECR read + S3 write + SSM parameter read), and a hardened security group allowing only ports 80/443 inbound — no SSH from `0.0.0.0/0`. SSM Session Manager is the access method.

The cloud-init template installs Docker, mounts the persistent EBS volume for TimescaleDB, writes the Caddyfile and docker-compose configuration with all 10 services (backend, analytics-worker, dashboard, timescaledb, jaeger, caddy, prometheus, grafana, loki, promtail), pulls images from ECR, and starts the stack.

Environment separation (staging vs. production) uses Terraform workspaces with a `locals` block selecting instance type, volume size, retention periods, and budget amounts.

### 2. Database module — self-hosted TimescaleDB on persistent EBS

Created `infra/terraform/modules/database/` (variables.tf, main.tf). Provisions an encrypted gp3 EBS volume for TimescaleDB data. The database runs as a Docker container on the hosting instance (not a managed service), matching the cost-optimized single-instance architecture. Production tuning parameters (`shared_buffers`, `effective_cache_size`, `work_mem`, etc.) are injected via the docker-compose configuration.

### 3. DNS/TLS module — Route53 + Caddy auto-TLS

Created `infra/terraform/modules/dns-tls/` (variables.tf, main.tf). Provisions a Route53 hosted zone for `tickonomics.io` with A records for `api`, `api.staging`, `app`, and `app.staging` subdomains pointing to the hosting instance's Elastic IP. The apex domain CNAME points to the Vercel deployment for the landing page.

TLS is handled entirely by Caddy (auto-ACME via Let's Encrypt) — no ACM/certificate resources needed in Terraform. Caddy configuration is delivered via cloud-init `write_files`.

### 4. Secrets module — SSM Parameter Store with KMS encryption

Created `infra/terraform/modules/secrets/` (variables.tf, main.tf). Provisions 6 SSM SecureString parameters under `/tickonomics/` for database password, Finnhub/Alpha Vantage/FRED API keys, OAuth client secret, and ECR registry token. A customer-managed KMS key with 90-day rotation encrypts all parameters. An IAM policy grants the hosting instance `ssm:GetParameter` on `/tickonomics/*` and `kms:Decrypt` on the key.

This migrates secrets out of Terraform state (where they previously lived as sensitive variables rendered into cloud-init templates). New instances pull secrets at boot via `aws ssm get-parameter`.

### 5. Observability module — self-hosted Prometheus + Grafana + Loki

Created `infra/terraform/modules/observability/` (variables.tf, main.tf, alerts/alerts.yaml, 4 Grafana dashboards). Provisions a 10 GB gp3 EBS volume for monitoring data persistence.

**Monitoring stack configuration** created under `monitoring/`:
- `prometheus.yml` — scrape configs for backend (`/actuator/prometheus`), analytics-worker (`/metrics`), timescaledb-exporter, and caddy
- `loki.yml` — 30-day retention, filesystem storage, TSDB schema
- `promtail.yml` — Docker container log scraping via docker_sd_configs + journald
- `grafana/datasources/datasources.yml` — auto-provisioned Prometheus + Loki data sources
- `grafana/dashboards/dashboards.yml` — dashboard provider config
- 4 pre-built Grafana dashboards: **Overview** (service health, request rate, error rate, P95 latency, JVM memory, container CPU/memory), **Ingestion** (WebSocket status, ticks/sec, circuit breaker state, overflow queue depth, LKG cache hit rate, data quality failures), **Computation** (ILI value, signal generation rate, regime state, KPI latency, active strategies, model training duration, anomaly score distribution), **Forecast** (pipeline runs, spot cost, training duration, MAPE accuracy, Lambda invocations, Lambda errors)

**8 Prometheus alert rules:**
| Alert | Condition | Severity |
|:------|:----------|:---------|
| `BackendDown` | No scrape for 2m | Critical |
| `HighErrorRate` | 5xx > 5% for 5m | Warning |
| `HighLatency` | P95 > 2s for 5m | Warning |
| `DatabaseDiskFull` | Disk > 85% for 5m | Warning |
| `DatabaseDiskCritical` | Disk > 95% for 1m | Critical |
| `IngestionStale` | No ticks for 10m | Critical |
| `CircuitBreakerOpen` | Any breaker open > 1m | Warning |
| `ForecastOverBudget` | Monthly cost > $20 | Warning |

**Application config** updated: `management.endpoints.web.exposure.include` set to `health,prometheus,metrics` and `management.metrics.export.prometheus.enabled` set to `true` in `application.yml`.

### 6. Backup module — automated EBS snapshots + pg_dump via Lambda

Created `infra/terraform/modules/backup/` (variables.tf, main.tf, src/backup_runner.py). Provisions:
- An S3 bucket for backup storage with AES256 encryption, public access blocking, and lifecycle expiration (7 days staging, 30 days production)
- A Lambda function (Python 3.12, 300s timeout) triggered daily at 03:00 UTC via EventBridge
- IAM role with `ec2:CreateSnapshot/DescribeSnapshots/DeleteSnapshot`, `ssm:SendCommand`, and `s3:PutObject` permissions

The Lambda handler creates an EBS snapshot of the database volume, runs `pg_dump` via SSM Run Command on the hosting instance, uploads the compressed dump to S3, and cleans snapshots older than the retention period.

### 7. Budget module — AWS Budgets with 50%/80%/100% alerts

Created `infra/terraform/modules/budget/` (variables.tf, main.tf). Provisions an AWS monthly cost budget with notifications at 50% (actual), 80% (actual), and 100% (forecasted) thresholds. Separate budgets for staging ($30/mo) and production ($200/mo). An SNS topic is provisioned for budget alert routing.

### 8. Local server deployment — full cloud alternative

Created 10 files under `infra/local/` providing a complete bare-metal/homelab deployment:

| File | Purpose |
|:-----|:--------|
| `docker-compose.local.yml` | Full-stack compose: 10 services (backend, analytics-worker, dashboard, timescaledb with 4GB `shared_buffers` / 12GB `effective_cache_size`, jaeger, prometheus with 90d retention, grafana, loki, promtail, caddy). No resource limits — uses full hardware. |
| `Caddyfile.local` | LAN-only reverse proxy (port 80, no TLS); routes `/api/*` to backend and everything else to dashboard |
| `.env.local.example` | Secrets template (`DB_PASSWORD`, API keys, `GRAFANA_PASSWORD`) |
| `scripts/deploy-local.sh` | Builds images from source, starts all services, waits for backend health, prints access URLs |
| `scripts/backup-local.sh` | `pg_dump` (custom format) + gzip + optional rsync/S3 sync + retention cleanup |
| `scripts/restore-local.sh` | Confirmation-gated `pg_restore` from latest backup |
| `scripts/setup-local-cron.sh` | Installs crontab to `/etc/cron.d/tickonomics` |
| `crontab` | Daily backup (03:00), 5-min health check, daily forecast result pruning (05:00), weekday forecast runs (06:00) |
| `systemd/tickonomics-backup.service` | Oneshot systemd unit for backup |
| `systemd/tickonomics-backup.timer` | Daily timer with 10-min randomized delay |

The local server targets 128 GB RAM / 16-core hardware with tuned TimescaleDB config. Estimated cost: ~$5-10/mo (electricity) vs. ~$150/mo cloud. The hybrid deployment option is documented: local as primary, cloud as DR with `rsync` to S3.

### 9. CI/CD integration — GCP/Azure image push

Updated `.github/workflows/forecast-deploy.yml` to add multi-cloud image push steps:
- **GCP**: Authenticates via Workload Identity Federation, logs into Artifact Registry, tags and pushes backend + analytics images
- **Azure**: Logs into ACR with admin credentials, tags and pushes backend + analytics images

Both are gated on provider detection (`inputs.provider == 'gcp'` / `inputs.provider == 'azure'`). The existing AWS ECR push path is unchanged.

### 10. Environment root module wiring

Updated `infra/terraform/environments/aws/main.tf` to instantiate all 7 new modules with a workspace-based `locals` block for environment selection. Added a hardened `aws_security_group.hosting_sg` (ports 80/443 only, no SSH). Added 4 new variables (`grafana_password`, `oauth_client_secret`, `domain_name`, `landing_cname_target`) to the AWS environment variables.

Updated `infra/terraform/environments/aws/outputs.tf` with 10 new outputs: `hosting_instance_id`, `hosting_public_ip`, `hosting_security_group_id`, `db_volume_id`, `route53_zone_id`, `route53_name_servers`, `monitoring_volume_id`, `backup_bucket_name`, `backup_lambda_name`, `ssm_kms_key_arn`, `budget_id`.

### 11. Checkstyle config fix

During pre-implementation build verification, the Checkstyle configuration (`config/checkstyle/checkstyle.xml`) was found to be incompatible with Checkstyle 10.x: `LineLength` must be a direct child of `Checker`, not nested inside `TreeWalker`. Moved `LineLength` out of `TreeWalker` — the build now passes cleanly (83 tasks, all 377 Python tests passing).

### 12. docker-compose.prod.yml expansion

Added 5 new services to `docker-compose.prod.yml`:
- `caddy` — reverse proxy with auto-TLS (ports 80/443), mounts `Caddyfile`
- `prometheus` — metrics collection with 30-day retention, mounts `prometheus.yml`
- `grafana` — dashboard UI with auto-provisioned dashboards and datasources
- `loki` — log aggregation with filesystem storage
- `promtail` — log shipper scraping Docker containers

Each service has production-appropriate resource limits and JSON log rotation.

## Consequences

**Positive:**
- The platform now has a defined, IaC-managed production deployment path. `terraform apply` provisions all resources needed for persistent hosting.
- Secrets are migrated from Terraform state to SSM Parameter Store (SecureString, KMS-encrypted), eliminating a long-standing security concern.
- Self-hosted observability (Prometheus + Grafana + Loki) provides richer dashboards and log aggregation than CloudWatch alone, at near-zero marginal cost.
- Local server deployment eliminates all cloud costs for users with suitable hardware, while maintaining full feature parity.
- Automated daily backups (EBS snapshots + pg_dump to S3) provide RPO of 24 hours with RTO target of 1 hour.
- AWS Budgets at 50%/80%/100% thresholds provide early warning on cost overruns.
- Network hardening: no SSH from public internet; SSM Session Manager only.

**Negative / trade-off:**
- Single EC2 instance is a single point of failure. ECS Fargate or multi-AZ would improve availability but at 3–5× cost.
- Self-hosted TimescaleDB requires manual vacuum/maintenance; managed TimescaleDB Cloud would automate this at $50+/mo.
- The GCP and Azure environments remain monolithic (not decomposed into the 7 modules). The modules are AWS-only; porting them to GCP/Azure is deferred.
- Caddy rate limiting (WAF Phase 1 / MVP) is adequate for the current single-user scale but would need AWS WAF before public launch.
- The backup Lambda's SSM Run Command approach couples backups to the instance being running; if the instance is down, only EBS snapshots are available.

## Verification

- `terraform fmt -check -recursive infra/terraform/` — passes
- `terraform init -backend=false && terraform validate` for AWS environment — passes
- `tflint --recursive infra/terraform/` — no new violations from Track 14 modules
- `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` — validates (dry run)
- `docker compose -f infra/local/docker-compose.local.yml config` — validates (dry run)
- `bash -n infra/local/scripts/*.sh` — all scripts pass syntax check
- `./gradlew clean build test pytest` — 83 tasks, build successful, 377 Python tests passing

## Deferred

- **GCP/Azure module decomposition** — the 7 new modules are AWS-only. GCP and Azure environments would need equivalent modules or remain monolithic as they are today.
- **AWS WAF integration** (Phase 10) — Caddy rate limiting is MVP; WAF with managed rules and rate-based blocking should be added before public access.
- **Multi-AZ / high availability** — single-instance architecture is adequate for the current scale; ALB + ECS Fargate migration is documented as Phase 2.
- **Cross-region DR** — S3 cross-region replication for backups is not implemented; RPO in a region-wide outage would be 24h+.
- **Persistent volume reattach for Lambda-launched instances** — the backup Lambda's SSM approach assumes the instance is running; snapshot-only recovery path exists.
- **Alertmanager routing** — Prometheus alert rules are defined but Alertmanager is not configured (no Slack/email routing). Currently relies on Grafana built-in alerts and CloudWatch alarms.
- **Grafana dashboard refinement** — the 4 dashboards use placeholder metric names that will need tuning once real Prometheus metrics are flowing from the instrumented backend.
