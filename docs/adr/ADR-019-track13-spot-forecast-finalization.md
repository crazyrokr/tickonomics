# ADR-019: Terraform Spot/Forecast Deployment Finalization

**Date:** 2026-06-14
**Status:** Accepted
**Track:** 13 — Terraform Spot/Forecast Deployment
**Supersedes / extends:** ADR-011 (gap-elimination), Plan v6 Track 13 review findings

## Context

`docs/plan_v6-verification-report.md` scored Track 13 at **~50% — "IaC present, runtime broken"**. The
six Terraform modules, three cloud environments, graceful-shutdown wiring (Plan Phase 2), and the CLI /
CI entry points already existed, but the spot pipeline could not complete a run end-to-end. The report
listed five unresolved blockers, and the same files carried coherence defects that would leave it broken
even after those five were nominally addressed:

- **C1** — `forecast_trigger.py` returned `{"status":"TRIGGERED"}` without ever calling
  `ec2.run_instances()`/`request_spot_instances()`. Scheduled and API-driven runs never launched.
- **C2** — backend healthcheck used `curl`, absent from `eclipse-temurin:25-jre`. (Actuator *was* on the
  classpath, so `/actuator/health` existed — `curl` was the only gap.)
- **C4/C5** — cloud-init double-executed `forecast-task.sh` (`runcmd` + a separate shellscript part),
  and `health-check.sh` / `collect-results.sh` / `shutdown.sh` were never deployed to the instance.
- **M3** — `user-data.tftpl` was unreferenced dead code.
- **Phase 5** — `infra/local/` (local-server forecast path) was absent.

Coherence defects in the same scripts: hard-coded `forecast-*-1` container names that Compose's project
naming would not produce (**H3**); calls to non-existent endpoints `/api/v1/kpis/compute`,
`/api/v1/signals/generate`, `/api/v1/analytics/forecast` (**C3-script**); no `FAILED` status on error, so
`forecast_status` returned `RUNNING` forever (**H2**); proceeding below the 50-row ingestion floor
(**M4**); fragile `lsblk` disk selection (**H4**); the trigger Lambda trapped in a VPC with no NAT,
blocking C1 itself (**H5**); monitoring CPU alarm/dashboard pinned to a transient `spot_instance_id`
(**H6**); and legacy `POLYGON_API_KEY` references while the platform migrated to v6
`FINNHUB_API_KEY`/`ALPHAVANTAGE_API_KEY` (ADR-012/018).

This ADR records the decisions that close these. The verification-report scorecard is updated below.

## Decision

### 1. Launch template as the shared spot-launch mechanism (C1)

Fixing C4 embeds four shell scripts into the cloud-init, so the rendered user-data (gzip+base64)
exceeds Lambda's 4 KB env-var ceiling — passing user-data inline to the trigger Lambda is not viable.
Instead a new `aws_launch_template.forecast` (in `modules/compute-spot/main.tf`) holds the AMI,
instance type, key, IAM instance profile, the cloud-init user-data, the root block device, and the
`Name` tag. Both launch paths consume it:

- `aws_spot_instance_request.forecast` (the `terraform apply` / `run-forecast.sh` path) references the
  template via `launch_template { version = "$Default" }`.
- `forecast_trigger.py` calls `ec2.run_instances(LaunchTemplate=…, InstanceMarketOptions={spot,
  one-time, terminate})`, then writes a `RUNNING` STATUS marker carrying the `instance_id`.

Network (`subnet_id`, `vpc_security_group_ids`) stays at the **top level** of both the spot request and
`run_instances` — putting it inside the template's `network_interfaces` would conflict with top-level
network attrs. `update_default_version = true` keeps `$Default` pointing at the latest version so
edits take effect without a version pin.

### 2. Trigger Lambda out of the VPC (H5)

`vpc_config` is removed from the trigger Lambda and the `AWSLambdaVPCAccessExecutionRole` attachment is
dropped. The Lambda only calls public AWS APIs (EC2, S3); a public-subnet ENI without a NAT made every
outbound call fail. The status and interruption Lambdas were already VPC-less.

### 3. Cloud-init: single execution, base64 scripts (C4, C5, M3)

`user-data.tftpl` is deleted (M3). The `cloudinit_config` collapses to one `text/cloud-config` part.
All four scripts are deployed via `write_files` using `encoding: b64` +
`base64encode(file(...))` — this sidesteps YAML indentation and preserves the scripts' bash `${…}`
verbatim (a `templatefile` would have tried to interpolate them). `config.env` is written with
`COMPOSE_PROJECT_NAME=forecast`. `runcmd` runs `forecast-task.sh` exactly once (the duplicate
shellscript part is gone).

### 4. `curl` in the backend image (C2)

`curl` is installed in the runtime stage of the root `Dockerfile` (same `apt-get install` pattern the
build stage already uses), so the `/actuator/health` healthcheck can probe. This benefits the main
compose as well as the forecast stack.

### 5. Script coherence (H2, H3, H4, M4, C3-script)

`forecast-task.sh` is rewritten:

- `trap cleanup EXIT` uploads a `FAILED` STATUS (+ partial-results tarball) on non-zero exit and never
  overwrites an already-terminal status (H2).
- A configurable `MIN_ROWS` (default 50) is enforced; below the floor after timeout the task exits 1
  → the trap writes `FAILED` (M4).
- All container access goes through `docker compose exec -T <service>` / `docker compose ps -q`, keyed
  on service names rather than hard-coded `forecast-*-1` containers (H3).
- Disk mount honors a `DATA_DEVICE` env var, else selects the largest block device not mounted at `/`
  (robust to NVMe remapping) (H4).
- It triggers only the real `POST :8001/api/v1/analytics/volatility-forecast` and relies on the
  backend's `@Scheduled` jobs (plus a settle wait) for KPI/signal compute; `collect-results.sh` collects
  only real endpoints (`quant/signals/active`, `quant/strategies/active`, `quant/risk/tail-parameters`,
  `quant/risk/evt-tail`, `quant/macro/shock-response`, `analytics/volatility-forecast`). The missing
  POST compute-trigger endpoints are a Track 1 concern — see Deferred.

### 6. Tag-aware monitoring (H6)

EC2 `CPUUtilization` is dimensioned by `InstanceId`, not by tag, so a tag-keyed *alarm* is not directly
expressible. The brittle `spot_instance_id`-pinned `high_cpu` alarm is removed (it went
`INSUFFICIENT_DATA` the moment the instance terminated); the dashboard CPU widget becomes a CloudWatch
`SEARCH` expression so it surfaces whichever tagged instance is running. The spot-interruption and
Lambda-error alarms (both instance-agnostic) remain. Per-instance CPU *alerting* via tags would need a
custom metric emitted at launch — see Deferred. The interruption Lambda's IAM is broadened to allow
`PutObject` on `_interruption/*` so its (already-wired) marker write is actually permitted.

### 7. v6 data-source migration

`POLYGON_API_KEY` is replaced by `FINNHUB_API_KEY` + `ALPHAVANTAGE_API_KEY` across the forecast stack:
`compute-spot` variables, the cloud-init `config.env` and embedded compose, the shared
`docker-compose.forecast.yml`, and all three environments' `variables.tf` / `terraform.tfvars.example`.
No live Polygon reference remains under `infra/`.

### 8. Phase 5 — local-server path

`infra/local/` is added: `forecast-local.sh` (compose up → health → ingestion floor → forecast trigger
→ collect real endpoints → `pg_dump` → tar → `STATUS` file, with a `FAILED` trap), `docker-compose.forecast.local.yml`
(local image refs + named volume), `.env.forecast.example`, `backup-forecast-results.sh`,
`setup-forecast-cron.sh`, and `crontab`. It runs the same pipeline with no cloud dependency.

### 9. CI linting (M5)

`terraform fmt -check` / `validate` are joined by `tflint --recursive` and `tfsec` in the
`terraform-validate` job of `.github/workflows/forecast-deploy.yml`.

### 10. Lambda unit tests

The three handlers were untested. `modules/orchestrator/tests/` adds a stdlib-only fake `boto3`
(`conftest.py` injects it into `sys.modules` so the handlers import without the SDK installed) plus
Given-When-Then tests for trigger (task-id parsing, `run_instances` params incl. launch template + spot
options + MaxPrice, RUNNING/FAILED markers), status (RUNNING/COMPLETED/FAILED, results URL, missing
taskId), and interruption (marker write, missing-bucket guard, unknown-instance fallback).

## Deferred

Documented as known limitations, none of which block the report's Track 13 closure:

- **Compute-trigger endpoints (Track 1 / C3)** — `POST /api/v1/quant/kpis/compute`,
  `signals/generate`, `GET /api/v1/quant/kpis`, `risk/summary` do not exist; the scripts call existing
  endpoints and rely on backend `@Scheduled` jobs. New endpoints are app-layer (Track 1) work.
- **GCP/Azure parity (M9/M10)** — the orchestrator module is AWS-only (Lambda/EventBridge/API GW).
  GCP/Azure environments reuse `forecast_trigger.py` and the shared `forecast-task.sh`; provider-specific
  trigger functions and the Azure VM-extension script delivery remain stubs. Their v6 variable names are
  normalized but not yet wired into inline resources.
- **Secrets in state (M6)** — secrets still render into the launch-template user-data and Terraform
  state, unchanged from before. Migration to SSM/Secrets Manager is not worsened here.
- **Persistent-volume reattach for Lambda launches** — only the `terraform apply` path attaches the
  persistent EBS volume; Lambda-launched instances start cold (re-ingest) and lose warm-start. Adding
  `ec2.attach_volume` to the trigger is a refinement tied to the deferred model-warm-start (L3).
- **Tag-based per-instance CPU alarm** — would require a launch-emitted custom metric; the SEARCH
  dashboard widget provides visibility meanwhile.

## Consequences

- The spot pipeline is now internally consistent: launch → bootstrap → compute (scheduled) → collect
  real endpoints → terminal `COMPLETED`/`FAILED` → self-terminate, on both the `terraform apply` and
  Lambda-trigger paths, plus a fully local path.
- AWS is the validated environment; GCP/Azure remain IaC-only (validate-clean) pending M9/M10.
- The launch template becomes the single source of truth for instance launch spec; future spot config
  changes edit one place.

## Verification

- `terraform fmt -check -recursive infra/terraform/` clean; `terraform init -backend=false && terraform
  validate` → `Success` for aws, gcp, azure.
- `tflint --recursive` → exit 0 (warnings only: pre-existing module-level `required_providers` style
  nits and the expected GCP/Azure unused v6 vars pending M9/M10 wiring).
- `python3 -m pytest infra/terraform/modules/orchestrator/tests/` → 13 passed.
- `shellcheck` + `bash -n` clean across all eight orchestration/local scripts.
- `grep` confirms no `polygon`, no `user-data.tftpl`, no removed `vpc_config`/`subnet_ids`, and no stale
  endpoints under `infra/`.
