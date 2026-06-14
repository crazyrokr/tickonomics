# Infrastructure and DevOps Code Review

**Date:** 2026-06-14
**Branch:** feature/devops
**Reviewer:** Automated Infrastructure Audit

---

## 1. Docker Best Practices

### 1.1 Multi-Stage Builds

**Main Dockerfile** (`/home/crazyrock/github/tickonomics/Dockerfile`): Multi-stage build implemented correctly. Uses `eclipse-temurin:25-jdk` for the build stage with full native compilation toolchain (cmake, gcc, g++, maven), then copies only the Spring Boot JAR into `eclipse-temurin:25-jre` for runtime. This correctly separates build toolchain from runtime, reducing attack surface and image size.

**Analytics Dockerfile** (`/home/crazyrock/github/tickonomics/analytics/Dockerfile`): Clean multi-stage. Build stage installs Python dependencies to `/install`; runtime stage copies only the installed packages and application code. Good separation.

**Frontend Dockerfile** (`/home/crazyrock/github/tickonomics/frontend/Dockerfile`): Proper Next.js standalone build pattern. Build stage produces `.next/standalone`; runtime stage copies only the standalone output and static assets. Correct.

**Landing Dockerfile** (`/home/crazyrock/github/tickonomics/landing/Dockerfile`): Build stage uses Node for SSG/export; runtime uses `nginx:1.27-alpine`. Effective separation.

### 1.2 Layer Caching

**Finding (Medium):** The main Dockerfile copies the entire project with `COPY . .` before running Gradle. Any source code change invalidates all downstream layers, including native TA-Lib compilation and dependency resolution. This wastes significant build time.

**Recommendation:** Restructure the main Dockerfile to copy Gradle wrapper, `settings.gradle`, `build.gradle`, and `gradle.properties` first, run `./gradlew dependencies` for dependency resolution, then copy source code and build. Example pattern:

```dockerfile
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test
```

The analytics and frontend Dockerfiles already follow this pattern (copy `requirements.txt` / `package.json` first, install dependencies, then copy source).

### 1.3 Security: Non-Root User

| Dockerfile | Non-root user | Status |
|---|---|---|
| Main (backend) | `tickonomics` user created with `adduser --system` | PASS |
| Analytics | `analytics` user created with `adduser --system` | PASS |
| Frontend | `dashboard` user created with `adduser -S` | PASS |
| **Landing** | **None** | **FAIL** |

**Finding (High):** The landing Dockerfile uses `nginx:1.27-alpine` which runs as root by default. There is no `USER nginx` directive. The `nginx` user (uid 101) exists in the Alpine image but is not activated. A compromised landing page container would run with root privileges.

**Recommendation:** Add `USER nginx` before the CMD/ENTRYPOINT in the landing Dockerfile. Verify that nginx can bind to port 80 as non-root, or use an unprivileged port mapping (e.g., listen on 8080 internally, map to 80 externally).

### 1.4 Image Size

**Main Dockerfile:** The build stage installs cmake, make, gcc, g++, git, and maven -- necessary for TA-Lib native C library compilation. No practical alternative exists unless TA-Lib is pre-compiled into a base image. The runtime stage is a clean JRE with no build tools. Acceptable.

**Finding (Low):** The Docker Compose forecast services reference `timescale/timescaledb:latest-pg16` which uses the floating `latest` tag for the patch version. This can cause unexpected image changes between pulls.

**Recommendation:** Pin TimescaleDB to a specific digest or at minimum a specific patch version (e.g., `timescale/timescaledb:2.16.1-pg16`).

### 1.5 .dockerignore

**Root** (`/home/crazyrock/github/tickonomics/.dockerignore`): Comprehensive. Excludes `.git`, `.github`, `build/`, `node_modules`, `.next`, `.gradle`, `.idea`, `.env` files, documentation, native-lib source directories, and Python bytecode.

**Analytics** (`/home/crazyrock/github/tickonomics/analytics/.dockerignore`): Covers Python-specific artifacts (`__pycache__`, `.pytest_cache`, `.mypy_cache`, `build.gradle`).

**Finding (Low):** No `.dockerignore` files exist for `frontend/` or `landing/`. While the root `.dockerignore` covers most patterns, service-specific ignore files would provide defense-in-depth if those directories are used as independent build contexts.

---

## 2. Terraform Best Practices

### 2.1 State Management

| Environment | Backend | Locking | Encryption |
|---|---|---|---|
| AWS | S3 + DynamoDB | DynamoDB table | `encrypt = true` |
| Azure | azurerm (Storage Account) | Built-in blob lease | Storage account encryption |
| GCP | GCS | Built-in | GCS encryption |

All three environments use remote backends with state locking and encryption. This is well-configured.

### 2.2 Module Design

**Finding (High):** Module usage is inconsistent across cloud providers.

- **AWS environment** uses five well-structured modules: `networking`, `storage`, `container_registry`, `compute_spot`, `orchestrator`, and `monitoring`. Each module has clean inputs/outputs.
- **Azure environment** has NO modules. All resources (networking, storage, VM, function app, monitoring) are defined inline in a 315-line `main.tf`. This makes the Azure environment harder to review, test, and maintain compared to the AWS equivalent.
- **GCP environment** uses a hybrid approach: some concerns (networking, storage, compute) are inline, while the orchestrator module is used via `data.archive_file` references. This is better than Azure but still inconsistent.

**Recommendation:** Extract Azure resources into modules mirroring the AWS structure. At minimum, extract networking, storage, and compute into their own modules. This would enable reuse and consistent testing across providers.

### 2.3 Variable Typing

All variables across all environments and modules have explicit `type` declarations and `description` fields. Sensitive variables (`postgres_password`, `polygon_api_key`, `fred_api_key`) are correctly marked `sensitive = true`. TFLint rules `terraform_typed_variables`, `terraform_documented_outputs`, and `terraform_documented_variables` are enabled, enforcing these standards.

**Finding (Low):** The `spot_price_max` variable is typed as `string` across all providers, but `instance_type` and `db_volume_size_gb` are `string` and `number` respectively. For consistency, `spot_price_max` could be `number` where the provider accepts numeric values.

### 2.4 Secrets Handling

**Finding (High):** Secrets (API keys, database passwords) flow through multiple layers of plain text:
1. `terraform.tfvars` or environment variables -> Terraform state
2. Terraform state -> `templatefile()` -> cloud-init user data
3. Cloud-init -> `/etc/tickonomics/config.env` (file on VM, permissions `0600`)
4. config.env -> Docker environment variables (visible in `docker inspect`)

While Terraform state is encrypted at rest (S3 SSE, GCS encryption, Azure Storage encryption) and `sensitive = true` prevents console output, secrets are still stored in the state file. Any user with `terraform state pull` access can extract them.

**Recommendations:**
- Use a secret manager (AWS Secrets Manager, Azure Key Vault, GCP Secret Manager) to store API keys and passwords.
- Have the forecast task script fetch secrets at runtime from the secret manager instead of baking them into cloud-init.
- At minimum, restrict Terraform state access to a minimal set of IAM roles/policies.

**Additional Finding (Medium):** The Azure environment stores `storage_account_access_key` directly in `app_settings` of `azurerm_linux_function_app`. This is the primary access key in plain text in both the state file and the Function App configuration.

**Recommendation:** Use Managed Identity for the Function App with Azure RBAC to access the storage account instead of passing the access key.

### 2.5 Provider Versioning

`/home/crazyrock/github/tickonomics/infra/terraform/versions.tf` pins providers with optimistic constraints:
- `aws ~> 5.0` (allows 5.x)
- `google ~> 5.0` (allows 5.x)
- `azurerm ~> 3.0` (allows 3.x)
- `null ~> 3.0`, `archive ~> 2.0`, `random ~> 3.0`

This is reasonable -- allows patch and minor updates but prevents major version breaking changes.

**Finding (Low):** The `versions.tf` file is at the root level but declares all providers for all clouds. Individual environment backends re-declare only their needed providers. This forces every environment init to depend on the root-level providers even if unused. Not a functional issue, but creates dependency confusion.

**Recommendation:** Move provider version constraints into each environment's `backend.tf` or providers file, and remove the root-level `versions.tf` declarations for providers not universally needed.

### 2.6 Terraform-Specific Issues

**Finding (High):** The `aws_spot_instance_request` resource uses `valid_until = timeadd(timestamp(), "4h")`. The `timestamp()` function produces a new value on every plan, causing perpetual plan drift. This is a well-known Terraform anti-pattern.

**Recommendation:** Use a static expiration or calculate it externally. If the spot request should be continually valid, use `spot_type = "persistent"` instead of `spot_type = "one-time"`, or use an external data source to compute the expiry once.

**Finding (Medium):** The `forecast_trigger.py` Lambda function accepts events, generates a UUID, and returns a success response -- but it does NOT actually launch any EC2 spot instances. The trigger function is effectively a no-op. The `ec2:RequestSpotInstances` and `ec2:RunInstances` permissions in the IAM role are unused. Forecast execution relies entirely on the scheduled EventBridge rule which invokes the Lambda, but the Lambda does not call the EC2 API.

**Recommendation:** Implement the EC2 `request_spot_instances` call in `forecast_trigger.py` using the boto3 client. Pass necessary parameters (AMI ID, instance type, subnet ID, security group, user data) as environment variables or fetch them from SSM Parameter Store.

**Finding (Medium):** The `user-data.tftpl` template uses shell variable substitution syntax (`${variable}`) inside Terraform template syntax. Both Terraform's `templatefile()` and bash use `${}`. This creates ambiguity -- Terraform substitutes `${var.foo}` at plan time, but bash-style `${BACKEND_IMAGE}` is meant to be resolved at runtime. The template uses both styles and relies on the `%{ if ... }` Terraform directives to differentiate. While functional, this is fragile and hard to read.

**Recommendation:** Either use Terraform's `$${...}` escape syntax for shell variables, or switch to a template format with distinct delimiters.

**Finding (Low):** GCP `main.tf` declares `google-beta` provider but does not use any beta resources. The `google_cloudfunctions2_function` resource exists in the GA provider since v5.0. The beta provider is unnecessary.

---

## 3. CI/CD Best Practices

### 3.1 Pipeline Efficiency

**pr-checks.yml:** Well-structured with parallel execution:
- `java-build-test`: Java compilation + tests + OTel agent attachment
- `python-test`: Python linting + pytest
- `frontend-build-test`: Matrix build for landing and frontend (lint, typecheck, build, test, Lighthouse CI)
- `code-quality`: Checkstyle + SpotBugs (advisory, `continue-on-error: true`)
- `integration-test`: Depends on java-build-test, runs against service container

**Finding (Medium):** The `code-quality` job uses a separate Gradle cache key (`gradle-cq-`) instead of reusing the `gradle-` key from `java-build-test`. This causes duplicate dependency downloads.

**Recommendation:** Use `restore-keys: gradle-` in the code-quality job to leverage the cache populated by java-build-test.

**deploy-staging.yml:** Builds four images sequentially in a single `build-and-push` job. Could be parallelized with a matrix strategy.

**Recommendation:** Use a matrix strategy for the image build job:
```yaml
strategy:
  matrix:
    service:
      - { name: backend, context: ".", dockerfile: Dockerfile }
      - { name: analytics, context: "./analytics", dockerfile: "./analytics/Dockerfile" }
      - { name: dashboard, context: "./frontend", dockerfile: "./frontend/Dockerfile" }
      - { name: landing, context: "./landing", dockerfile: "./landing/Dockerfile" }
```

### 3.2 Caching

Well-implemented caching strategy:
- **Buildx cache:** `type=gha` with `mode=max` for Docker layer caching across workflow runs. Effective.
- **Gradle cache:** Keyed on `**/*.gradle` and `gradle-wrapper.properties` hash. Correct scope.
- **TA-Lib native cache:** Keyed on `native-libs/ta-lib/VERSION` hash. Correct scope.
- **TA-Lib JNA cache:** Keyed on `computation/build.gradle` hash. Good.
- **Next.js cache:** Keyed on `package-lock.json` hash per app. Good.
- **pip cache:** Using `actions/setup-python@v5` with `cache: pip`. Good.

### 3.3 Job Parallelism and Dependencies

| Workflow | Parallel Jobs | Dependencies |
|---|---|---|
| pr-checks | java, python, frontend, code-quality | integration-test needs java |
| deploy-staging | build-and-push (sequential) | migrate needs build; deploy needs build+migrate; smoke-test needs deploy; demo needs build |
| deploy-production | build-gate -> promote -> release | Linear chain |
| chaos-tests | Single job (5 scenarios sequential) | None |
| forecast-deploy | build-images, terraform-validate (parallel) | terraform-plan needs validate; terraform-apply needs images+validate; smoke-test needs apply |

Deploy staging has a well-designed pipeline: build -> (migrate + deploy) -> smoke-test with proper conditionals (`always()` with result checks).

### 3.4 Artifact Management

**Finding (Medium):** Test results are uploaded as artifacts (`java-test-results`, `python-test-results`, `lint-reports`, `chaos-test-results`) with `if: always()` but are never consumed by downstream jobs. They serve only for manual inspection in the GitHub Actions UI.

**Recommendation:** Add a test-trend job that downloads test artifacts and posts a summary comment to the PR, or integrate with a test analytics service. This would provide visibility into flaky tests and regressions over time.

### 3.5 Workflow Validation Script

`/home/crazyrock/github/tickonomics/scripts/validate-cicd.py` is a structural contract test that validates the GitHub Actions workflow configuration against the ADR-016 specification. It checks for:
- Finnhub usage in chaos tests (not deprecated Polygon)
- Deploy staging pushes to main with GHCR + Flyway
- Deploy production is workflow_dispatch-only with production environment
- PR checks include code-quality advisory lint and OTel agent
- Benchmark workflow uses pytest-benchmark compare
- CodeQL has analyze step and security-events write permission
- Chaos benchmark is not an echo placeholder
- Lighthouse config exists
- No `secrets.*` references in `if` expressions (forbidden by GitHub Actions)

This is excellent CI/CD governance. The script is designed to run as a pre-commit or CI check.

---

## 4. Security

### 4.1 Secrets Management

| Aspect | Status |
|---|---|
| GitHub Secrets for sensitive values | PASS |
| Terraform `sensitive = true` for variables | PASS |
| Secrets in cloud-init user data | FAIL -- Secrets baked into EC2 metadata |
| Default passwords | FAIL -- `tickonomics` used as default in multiple places |
| Slack webhook hardened | PARTIAL -- `\|\| true` suppresses delivery failures |

**Finding (High):** Default passwords are used in multiple locations:
- `docker-compose.yml`: `POSTGRES_PASSWORD:-tickonomics`
- `chaos-tests.yml`: `POSTGRES_PASSWORD: tickonomics`
- `demo-report.yml`: `DEMO_DB_PASS: tickonomics`
- `pr-checks.yml` integration test: `POSTGRES_PASSWORD: tickonomics`

While some of these are for local/test environments, the pattern of hardcoded credentials in configuration files is a security anti-pattern.

**Recommendation:** Remove all hardcoded passwords. Use environment variables with no defaults for production paths, and use `.env` files (gitignored) for local development. For CI test environments, use GitHub Secrets or auto-generated random passwords.

### 4.2 Network Security

**Finding (High):** SSH access is open to the world (`0.0.0.0/0`) by default in all three cloud environments:
- AWS networking module: `ssh_cidr_blocks` defaults to `["0.0.0.0/0"]`
- Azure NSG: `source_address_prefix = "*"` for SSH rule (hardcoded)
- GCP firewall: `source_ranges = ["0.0.0.0/0"]` for SSH (hardcoded)

For a quantitative finance system handling market data and forecast results, this is a significant security risk.

**Recommendation:**
1. Change the default `ssh_cidr_blocks` to an empty list or a specific office/VPN CIDR range.
2. For Azure and GCP, make SSH source ranges configurable via variables (as AWS already supports).
3. Consider using AWS SSM Session Manager / Azure Bastion / GCP IAP for secure access without exposing SSH ports.

**Finding (Medium):** `docker-compose.dev.yml` exposes TimescaleDB port 5432 directly. This enables unauthenticated network access to the database if firewall rules permit it.

**Recommendation:** Remove the port exposure from dev compose or restrict it to `127.0.0.1:5432:5432`.

### 4.3 Least Privilege IAM

**AWS IAM -- Positive observations:**
- Spot instance role: Specific permissions for ECR read, S3 write, EBS attach, self-terminate
- Self-terminate policy scoped to instances with matching `Name` tag
- Lambda status role scoped to specific S3 bucket ARN
- Lambda interruption role scoped to specific S3 bucket/prefix

**Findings requiring attention (Medium):**

1. **`iam:PassRole` wildcard:** The Lambda trigger IAM policy allows `iam:PassRole` on `Resource: "*"`. This means the Lambda function can pass any IAM role to an EC2 instance. An attacker who compromises the Lambda function could launch an EC2 instance with an administrator role.

   **Recommendation:** Restrict the `PassRole` resource to the specific spot instance role ARN:
   ```hcl
   Resource = aws_iam_role.spot.arn
   ```

2. **ECR read wildcard:** The spot instance ECR read policy uses `Resource: "*"`. While ECR repositories are regional, this still permits reading from any ECR repository in the account.

   **Recommendation:** Scope to specific repository ARNs:
   ```hcl
   Resource = [
     "${var.backend_repository_arn}",
     "${var.analytics_repository_arn}"
   ]
   ```

3. **GCP `roles/storage.objectAdmin`** is overly broad for the forecast service account. It grants full control over all objects in all buckets.

   **Recommendation:** Grant `roles/storage.objectCreator` (for writing results) and `roles/storage.objectViewer` (for reading). Or create a custom role with only `storage.objects.create`, `storage.objects.get`, and `storage.objects.list` on the specific results bucket.

### 4.4 Container Security

**Finding (Medium):** The `container-registry` module enables `image_tag_mutability = "MUTABLE"` which allows overwriting image tags. This means `latest` tags can be retagged to any image, bypassing audit trails.

**Recommendation:** Set `image_tag_mutability = "IMMUTABLE"` to prevent tag overwriting. Use unique SHA-based tags for deployments and promote via new tags.

**Finding (Low):** No container image signing (Cosign, Notary) is configured. Image authenticity is not verified at pull time.

**Recommendation:** Implement Cosign keyless signing with GitHub Actions OIDC for production image verification.

---

## 5. Observability

### 5.1 Health Checks

| Service | Base compose | Forecast compose | Dockerfile HEALTHCHECK |
|---|---|---|---|
| Backend | None | `curl /actuator/health` | None |
| Analytics Worker | None | `curl /health` | Python urllib health check |
| TimescaleDB | `pg_isready` | `pg_isready` | N/A (external image) |
| Dashboard | None | N/A | None |
| Landing | None | N/A | None |

**Finding (Medium):** The base `docker-compose.yml` only has a health check for TimescaleDB. The backend and analytics-worker services lack health checks, which means `depends_on: condition: service_healthy` may not work correctly in some configurations.

**Recommendation:** Add health checks to the backend and analytics-worker services in the base `docker-compose.yml`:
```yaml
backend:
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:8080/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 60s
```

### 5.2 Logging Configuration

**docker-compose.prod.yml:** JSON file logging with rotation limits:
- Backend/Analytics/DB: 50 MB max size, 5 files max
- Dashboard/Landing/Jaeger: 10 MB max size, 3 files max

This is adequate for single-instance deployments, but has gaps for a financial system.

**Finding (Medium):** No centralized log aggregation is configured. Docker JSON file logs are ephemeral -- they are lost if the container is removed. There is no log shipping to CloudWatch Logs, Loki, Elasticsearch, or any other persistent store. Financial systems require complete audit trails.

**Recommendations:**
- Configure the Docker `awslogs` (CloudWatch) or `gcplogs` driver for production containers.
- For the forecast Lambda functions, increase CloudWatch log retention from 14 days to 90+ days to meet financial audit requirements.
- Enable S3 access logging on the results bucket.
- Enable CloudTrail for AWS API audit logging (not configured in the Terraform).

### 5.3 Metrics and Alerting

| Provider | Metrics | Alerts | Dashboard |
|---|---|---|---|
| AWS | CloudWatch + Lambda + EC2 metrics | Spot interruption, high CPU, Lambda errors | Yes (CloudWatch dashboard) |
| Azure | VM metrics | Spot eviction, high CPU | No |
| GCP | Pub/Sub preemption topic, logging metric | None | No |

**Finding (High):** The GCP environment has no Cloud Monitoring dashboards, alerting policies, or notification channels. The Azure environment has metric alerts but no dashboard. This creates an observability gap for non-AWS deployments.

**Recommendation:** Add GCP monitoring resources (alerting policies for VM preemption, CPU, and Cloud Function errors) and an Azure dashboard. The AWS monitoring module serves as a good template.

**Finding (Low):** The CloudWatch dashboard displays metrics, but the Lambda error alert threshold of 3 errors in 5 minutes may be too lax or too strict depending on forecast frequency. Make the threshold configurable.

### 5.4 Tracing

OpenTelemetry is configured for the backend (OTLP exporter pointed at Jaeger), and the OTel Java agent is attached in CI integration tests. However, tracing is disabled in the forecast environment (`MANAGEMENT_OTLP_TRACING_ENDPOINT: ""`). This is intentional for cost savings on spot instances that self-terminate, but means no traces are collected during the most critical operation (forecast runs).

**Recommendation:** Consider sampling-based tracing (1% or less) for forecast runs, or buffer traces locally and flush to S3 alongside results. This would provide forensic data for debugging forecast failures.

---

## 6. Financial System Specifics

### 6.1 Data Retention and Lifecycle

| Resource | Retention Policy | Status |
|---|---|---|
| S3 forecast results | 30 days, then expire | PASS |
| S3 noncurrent versions | 7 days | PASS |
| Azure blob results | Configurable `results_retention_days` | PASS |
| GCS results | Configurable via `results_retention_days` | PASS |
| ECR images | Keep last 10 tagged, remove untagged after 1 day | PASS |
| CloudWatch Lambda logs | 14 days | FAIL (too short) |
| PostgreSQL data | pg_dump to S3, no automated backup | PARTIAL |

**Finding (High):** For a quantitative finance system, the 30-day result retention may be insufficient for regulatory or backtesting purposes. Financial regulators often require years of historical data.

**Recommendations:**
- Extend results retention to 365 days or implement a tiered strategy: hot (30 days in primary bucket), warm (1 year in Infrequent Access storage), cold (7 years in Glacier Deep Archive).
- Enable S3 versioning on the results bucket (currently not configured) to protect against accidental deletion or overwrites.
- Implement automated EBS snapshot scheduling for the persistent TimescaleDB volume (e.g., daily snapshots retained for 30 days).
- Increase CloudWatch log retention to at least 90 days for Lambda functions.

### 6.2 Backup and Disaster Recovery

**What is present:**
- EBS volumes and Azure managed disks are tagged `Persistent = "true"` and survive spot/preemptible termination.
- PostgreSQL data is dumped (`pg_dump --compress=9`) and uploaded to S3/GCS/Blob as part of the forecast results archive.
- Multi-cloud infrastructure (AWS, GCP, Azure) provides geographic diversity for DR.

**What is missing (High):**
- No automated EBS snapshot schedule. If the EBS volume itself fails or is accidentally deleted, all TimescaleDB data is lost between forecast runs.
- No database WAL archiving or point-in-time recovery (PITR) configuration.
- No cross-region replication of the results bucket. A regional outage could cause data loss.
- No documented Recovery Time Objective (RTO) or Recovery Point Objective (RPO).
- No disaster recovery runbook or failover procedure.

**Recommendations:**
1. Add `aws_ebs_snapshot` or `aws_backup_plan` resources with a daily snapshot schedule (retained 30 days).
2. Enable WAL archiving for TimescaleDB to S3 for PITR capability.
3. Add cross-region replication for the S3 results bucket.
4. Document RTO/RPO targets in an ADR or operations runbook.

### 6.3 Audit Trail Preservation

| Requirement | Status |
|---|---|
| S3 server-side encryption | PASS -- AES256 enabled |
| S3 public access blocked | PASS -- All four block settings enabled |
| S3 access logging | MISSING |
| S3 versioning | MISSING (Azure explicitly disabled) |
| CloudTrail / API audit logging | MISSING |
| Database audit logging | MISSING |
| Immutable image tags | FAIL -- MUTABLE tag mutability |

**Finding (High):** Financial systems require comprehensive audit trails. The current configuration has gaps:
- No S3 access logging to track who accessed forecast results.
- No S3 versioning to maintain object history.
- No AWS CloudTrail (or equivalent) configured for API call auditing.
- `image_tag_mutability = "MUTABLE"` means container image tags can be overwritten, breaking the provenance chain.

**Recommendations:**
- Create an S3 access log bucket and enable logging on the results bucket.
- Enable S3 versioning on the results bucket.
- Add CloudTrail trail configuration to the AWS environment.
- Change `image_tag_mutability` to `"IMMUTABLE"`.
- Add Azure diagnostic settings for storage account and VM audit logging.
- Enable GCP audit logging (Cloud Audit Logs are enabled by default, but verify configuration).

### 6.4 Forecast Pipeline Integrity

**Finding (Critical):** The `forecast_trigger.py` Lambda function does not actually trigger anything -- it generates a UUID and returns a 200 response. The forecast execution relies entirely on:
1. Cloud-init script running `forecast-task.sh` on VM boot
2. Scheduled EventBridge rule invoking the Lambda (which does nothing)

The API Gateway integration with the Lambda returns a `task_id` and status `TRIGGERED`, but no EC2 instance launch occurs. This means the `POST /forecast` API endpoint is non-functional.

**Recommendation:** Implement EC2 `RunInstances` or `RequestSpotInstances` in `forecast_trigger.py`. Pass the necessary parameters from environment variables or SSM Parameter Store.

### 6.5 Compliance Observations

**Positive:**
- Results data is encrypted at rest (S3 SSE, Azure Storage encryption, GCS encryption).
- Public access to result storage is blocked by default.
- Database dumps and results archives provide movability and audit artifacts.
- Multi-cloud deployment enables regulatory data residency options.

**Gaps:**
- No data classification tags on storage resources.
- No retention policy aligned with financial regulations (e.g., SEC rule 17a-4).
- No WORM (Write Once Read Many) storage configuration for compliance-grade immutability.
- No data deletion verification or certificate of destruction process.

---

## 7. Summary of Findings by Severity

### Critical
1. **forecast_trigger.py is a no-op** -- The Lambda function does not launch EC2 instances. The API Gateway endpoint returns success but nothing happens.

### High
2. **Landing Dockerfile runs as root** -- No `USER` directive; container runs with root privileges.
3. **SSH open to 0.0.0.0/0 in all cloud environments** -- Default firewall rules allow SSH from anywhere.
4. **Secrets in cloud-init user data** -- API keys and passwords are baked into EC2 instance metadata.
5. **GCP has no monitoring/alerting** -- No dashboards, alerts, or notification channels for GCP deployment.
6. **Hardcoded default passwords** -- `tickonomics` password used in compose files and CI workflows.
7. **Insufficient audit trail** -- No S3 access logging, no versioning, no CloudTrail.
8. **Azure environment lacks modular structure** -- All resources in a single flat file, inconsistent with AWS.

### Medium
9. **Main Dockerfile poor layer caching** -- `COPY . .` before Gradle build invalidates all caching.
10. **IAM PassRole wildcard** -- Lambda can pass any role to EC2, not scoped to the specific spot role.
11. **ECR read policy wildcard** -- Spot instance can read from any ECR repository in the account.
12. **Missing health checks** -- Backend and analytics-worker lack health checks in base docker-compose.
13. **No centralized logging** -- Docker JSON logs are ephemeral; no log shipping configured.
14. **No database backup schedule** -- EBS snapshots not automated; no WAL archiving; no PITR.
15. **Image tag mutability** -- MUTABLE tags allow overwriting, breaking provenance.
16. **timestamp() causes perpetual Terraform plan drift**.
17. **GCP google-beta provider unused**.
18. **Azure storage account key in Function App plain text settings**.
19. **deprecated Polygon.io references** in `polygon_api_key` variable names in all environments.
20. **build-and-push images built sequentially** rather than in a matrix.

### Low
21. **TimescaleDB uses floating `latest` tag** in compose files.
22. **Missing .dockerignore** for frontend/ and landing/ services.
23. **spot_price_max typed as string** instead of number for type consistency.
24. **code-quality job uses separate Gradle cache key** -- misses reuse opportunity.
25. **Artifacts uploaded but never consumed** downstream.
26. **Test results retention not leveraged** for trend analysis.
27. **No container image signing** (Cosign/Notary).
28. **No WORM storage** for financial compliance.
29. **CloudWatch log retention too short** (14 days).

---

## 8. Architecture Decision Record Recommendation

The following architectural changes should be documented in a new ADR:

1. **Secrets management strategy:** Migration from terraform-variable-based secrets to AWS Secrets Manager / Azure Key Vault / GCP Secret Manager.
2. **Observability standardization:** Consistent monitoring module across all three cloud providers (dashboard, alerts, log aggregation).
3. **Backup and DR policy:** RTO/RPO targets, EBS snapshot scheduling, cross-region replication, and database WAL archiving.
4. **Audit trail requirements:** S3 access logging, versioning, CloudTrail, and immutable image tags for financial compliance.

---

## 9. Positive Findings

The following aspects are well-implemented and should be maintained:

1. **Multi-cloud Terraform:** Infrastructure defined for AWS, GCP, and Azure with consistent naming and tagging. Provides deployment flexibility and DR diversity.
2. **Spot/preemptible instance strategy:** Cost-optimized compute with persistent storage for database durability. Self-termination logic conserves costs.
3. **Well-structured AWS modules:** Clean separation of networking, storage, compute, container registry, orchestrator, and monitoring concerns.
4. **Comprehensive health checks in forecast Compose:** Backend, analytics-worker, and TimescaleDB all have health checks with appropriate intervals and retries.
5. **CI/CD contract validation script:** `validate-cicd.py` provides automated enforcement of ADR-016 workflow contracts. Excellent governance practice.
6. **Build caching strategy:** Docker Buildx with GHA cache backend, Gradle dependency caching, TA-Lib native/JNA caching, and Next.js cache are all correctly configured.
7. **Dockerfile non-root users:** Three of four Dockerfiles implement non-root users correctly.
8. **Terraform state management:** All providers use remote backends with encryption and state locking.
9. **S3 security hardening:** SSE enabled, public access fully blocked, noncurrent version lifecycle configured.
10. **PR checks parallelism:** Efficient parallel execution of Java, Python, Frontend, and Code Quality checks.
11. **Chaos testing automation:** Five distinct failure scenarios with automated reporting to GitHub Issues.
12. **Lighthouse CI integration:** Performance, accessibility, best-practices, and SEO budgets enforced for the landing page.
13. **Graceful shutdown on forecast completion:** Proper container stop timeout (60s), filesystem sync, persistent disk unmount, and self-termination sequencing.
14. **CodeQL with security-events permission:** Properly configured with multi-language matrix and dedicated job-level permissions.
