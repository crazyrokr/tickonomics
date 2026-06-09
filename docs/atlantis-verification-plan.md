# Plan: Atlantis Verification of Forecast Terraform Environments

## 1. Overview

Run [Atlantis](https://www.runatlantis.io/) locally to verify that every PR touching `infra/terraform/` produces a valid `terraform plan` for the affected cloud environment(s). Atlantis runs `terraform plan` on PR open/update and `terraform apply` after approval — catching syntax errors, drift, and misconfigurations before merge.

---

## 2. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Terraform | `>= 1.7` | Required by Atlantis |
| Atlantis binary | [latest release](https://github.com/runatlantis/atlantis/releases/latest) | Plan/apply engine |
| ngrok | [latest](https://ngrok.com/download) | Expose local Atlantis to GitHub webhooks |
| Docker | `>= 24` | Alternative: run Atlantis in a container |
| AWS CLI | `>= 2.x` | Authenticate to AWS for plan |
| gcloud CLI | latest | Authenticate to GCP for plan |
| Azure CLI | latest | Authenticate to Azure for plan |
| jq | `>= 1.6` | Parse Atlantis JSON output |
| tflint | `>= 0.50` | Lint Terraform files (custom workflow step) |
| terraform-docs | `>= 0.18` | Optional: diff module documentation |

---

## 3. Project Atlantis Configuration

### 3.1 `atlantis.yaml` (repo root)

Create this file at the project root. It declares each Terraform environment as a separate Atlantis *project* so Atlantis knows which directories to plan/apply and what variables are required.

```yaml
version: 3
projects:
  # ── AWS ──────────────────────────────────────────────
  - name: forecast-aws
    dir: infra/terraform/environments/aws
    terraform_version: "1.7.5"
    autoplan:
      enabled: true
      when_modified:
        - "*.tf"
        - "*.tfvars"
        - "../../modules/**/*.tf"
        - "../../shared/**/*"
    plan_requirements:
      - approved
    apply_requirements:
      - approved
      - mergeable
    workflow: forecast-aws
    tf_vars:
      - name: image_tag
        value: latest
    import_requirements:
      - approved

  # ── GCP ──────────────────────────────────────────────
  - name: forecast-gcp
    dir: infra/terraform/environments/gcp
    terraform_version: "1.7.5"
    autoplan:
      enabled: true
      when_modified:
        - "*.tf"
        - "*.tfvars"
        - "../../modules/**/*.tf"
        - "../../shared/**/*"
    plan_requirements:
      - approved
    apply_requirements:
      - approved
      - mergeable
    workflow: forecast-gcp
    import_requirements:
      - approved

  # ── Azure ────────────────────────────────────────────
  - name: forecast-azure
    dir: infra/terraform/environments/azure
    terraform_version: "1.7.5"
    autoplan:
      enabled: true
      when_modified:
        - "*.tf"
        - "*.tfvars"
        - "../../modules/**/*.tf"
        - "../../shared/**/*"
    plan_requirements:
      - approved
    apply_requirements:
      - approved
      - mergeable
    workflow: forecast-azure
    import_requirements:
      - approved

workflows:
  forecast-aws:
    plan:
      steps:
        - env:
            name: TF_VAR_aws_region
            value: us-east-1
        - run: terraform fmt -check -recursive ../../modules/ ../../environments/aws/
        - run: terraform init -input=false -no-color
        - run: terraform plan -input=false -no-color -var-file=terraform.tfvars -out=$PLANFILE
    apply:
      steps:
        - run: terraform apply -no-color $PLANFILE

  forecast-gcp:
    plan:
      steps:
        - env:
            name: TF_VAR_region
            value: us-central1
        - run: terraform fmt -check -recursive ../../modules/ ../../environments/gcp/
        - run: terraform init -input=false -no-color
        - run: terraform plan -input=false -no-color -var-file=terraform.tfvars -out=$PLANFILE
    apply:
      steps:
        - run: terraform apply -no-color $PLANFILE

  forecast-azure:
    plan:
      steps:
        - env:
            name: TF_VAR_region
            value: "East US"
        - run: terraform fmt -check -recursive ../../modules/ ../../environments/azure/
        - run: terraform init -input=false -no-color
        - run: terraform plan -input=false -no-color -var-file=terraform.tfvars -out=$PLANFILE
    apply:
      steps:
        - run: terraform apply -no-color $PLANFILE

# Global: only react to infra/terraform changes
automerge: false
parallel_plan: true
parallel_apply: false
abort_on_execution_order_fail: true
```

### 3.2 Key design decisions

| Decision | Rationale |
|---|---|
| `when_modified` includes `../../modules/**/*.tf` and `../../shared/**/*` | A module change triggers plans for all environments that consume it |
| `parallel_plan: true` | Plan all 3 environments concurrently for speed |
| `parallel_apply: false` | Avoid cross-account apply race conditions |
| `plan_requirements: [approved]` | Require at least one reviewer approval before plan runs |
| `apply_requirements: [approved, mergeable]` | Require approval AND no merge conflicts before apply |
| Custom workflows with `fmt -check` step | Catch formatting issues before `init`/`plan` |
| `terraform_version` pinned per project | Ensures consistent provider resolution |

---

## 4. Local Atlantis Setup (step-by-step)

### 4.1 Start ngrok

```bash
ngrok http 4141
# Note the forwarding URL, e.g. https://a1b2c3d4.ngrok-free.app
export URL="https://a1b2c3d4.ngrok-free.app"
```

### 4.2 Create GitHub webhook

In the repository **Settings → Webhooks → Add webhook**:

| Field | Value |
|---|---|
| Payload URL | `$URL/events` |
| Content type | `application/json` |
| Secret | (generate a random string) |
| Events | Pull request reviews, Pushes, Issue comments, Pull requests |

```bash
export SECRET="<your-webhook-secret>"
```

### 4.3 Create GitHub Personal Access Token

Settings → Developer settings → Personal access tokens → Fine-grained token:
- **Repository access**: tickonomics repo only
- **Permissions**: Read/write on Pull requests, Commit statuses

```bash
export TOKEN="<your-github-pat>"
export USERNAME="<your-github-username>"
export REPO_ALLOWLIST="github.com/<owner>/tickonomics"
```

### 4.4 Provide cloud credentials

Atlantis inherits environment variables from its launch process. Provide credentials for the environment(s) you want to test:

```bash
# AWS
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_DEFAULT_REGION=us-east-1

# GCP (write to a file Atlantis can reference)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/gcp-sa-key.json

# Azure
export ARM_CLIENT_ID=...
export ARM_CLIENT_SECRET=...
export ARM_SUBSCRIPTION_ID=...
export ARM_TENANT_ID=...
```

### 4.5 Start Atlantis

```bash
atlantis server \
  --atlantis-url="$URL" \
  --gh-user="$USERNAME" \
  --gh-token="$TOKEN" \
  --gh-webhook-secret="$SECRET" \
  --repo-allowlist="$REPO_ALLOWLIST" \
  --repo-config=atlantis.yaml \
  --allow-repo-config \
  --tf-download \
  --parallel-plan-count=3 \
  --log-level=debug
```

**Alternative: Docker**

```bash
docker run -d \
  --name atlantis \
  -p 4141:4141 \
  -e ATLANTIS_GH_USER="$USERNAME" \
  -e ATLANTIS_GH_TOKEN="$TOKEN" \
  -e ATLANTIS_GH_WEBHOOK_SECRET="$SECRET" \
  -e ATLANTIS_ATLANTIS_URL="$URL" \
  -e ATLANTIS_REPO_ALLOWLIST="$REPO_ALLOWLIST" \
  -e AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID" \
  -e AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY" \
  -e AWS_DEFAULT_REGION="$AWS_DEFAULT_REGION" \
  -v $(pwd)/atlantis.yaml:/home/atlantis/atlantis.yaml \
  ghcr.io/runatlantis/atlantis:latest \
  server \
  --repo-config=atlantis.yaml \
  --allow-repo-config
```

---

## 5. Verification Procedure

### Phase 1: Static validation (no cloud credentials needed)

Run these checks **before** opening a PR, to catch issues locally:

```bash
# 5.1 Format check
terraform fmt -check -recursive infra/terraform/

# 5.2 Init + validate each environment (backend=false — no remote state needed)
for env in aws gcp azure; do
  echo "=== Validating $env ==="
  cd infra/terraform/environments/$env
  terraform init -backend=false
  terraform validate
  cd -
done

# 5.3 tflint (per environment)
for env in aws gcp azure; do
  echo "=== tflint $env ==="
  tflint --init --config "$(git rev-parse --show-toplevel)/.tflint.hcl"
  tflint infra/terraform/environments/$env/
done

# 5.4 Module cross-references
# Verify every module source path in environments/*/main.tf resolves
grep -r 'source = "\.\./\.\./modules/' infra/terraform/environments/*/main.tf | \
  while read line; do
    dir=$(echo "$line" | cut -d: -f1 | xargs dirname)
    src=$(echo "$line" | grep -oP 'source = "\K[^"]+')
    target="$dir/$src"
    if [ ! -d "$target" ]; then
      echo "BROKEN: $target (from $line)"
    fi
  done
```

**Expected outcome:** All commands exit 0. No broken module references.

### Phase 2: Atlantis autoplan on PR open

1. Create a branch, make a trivial change (e.g., add a comment in `infra/terraform/modules/networking/main.tf`):
   ```bash
   git checkout -b test/atlantis-verify
   echo "# Atlantis verification test" >> infra/terraform/modules/networking/main.tf
   git add -A && git commit -m "test: verify Atlantis autoplan"
   git push origin test/atlantis-verify
   ```

2. Open a PR against `main`.

3. **Verify Atlantis behavior:**

   | Check | Expected result |
   |---|---|
   | Atlantis receives webhook | Log shows `Webhook received: pull_request opened` |
   | Detects changed projects | Log shows `Planning projects: forecast-aws, forecast-gcp, forecast-azure` (all three, because a shared module changed) |
   | `terraform fmt -check` passes | No format diff in plan output |
   | `terraform init` succeeds | Provider plugins downloaded, backend configured |
   | `terraform plan` produces output | Plan shows `No changes` (if no real infra changes) or the diff of the comment addition |
   | PR comment posted | Atlantis comments with plan output for each project |
   | Commit status set | Green check on `atlantis/plan` status check |

4. **If plan fails**, Atlantis posts the error as a PR comment. Common failures to expect and fix:

   | Error | Cause | Fix |
   |---|---|---|
   | `Error: Invalid value for module source` | Relative path doesn't resolve from PR checkout | Check `source` paths in `main.tf` |
   | `Error: Required variable not set` | Missing `terraform.tfvars` or env var | Ensure `tf_vars` in `atlantis.yaml` or provide a `.tfvars` file |
   | `Error: Failed to query available provider packages` | Network or version constraint mismatch | Check `versions.tf` constraints |
   | `Error: Backend configuration changed` | S3/GCS/Azure blob backend not accessible locally | Use `-backend=false` in workflow, or provide credentials |

### Phase 3: Targeted plan (single environment)

Comment on the PR to test a single environment:

```
atlantis plan -d infra/terraform/environments/aws
```

| Check | Expected result |
|---|---|
| Only AWS project planned | GCP and Azure are skipped |
| Plan output posted as PR comment | Single project plan block |

Then test the others:

```
atlantis plan -d infra/terraform/environments/gcp
atlantis plan -d infra/terraform/environments/azure
```

### Phase 4: Plan with real variables

Replace the trivial comment with a real variable change:

```bash
# Edit infra/terraform/environments/aws/variables.tf — change a default
# e.g., change instance_type default from "c5.2xlarge" to "c5.4xlarge"
git commit -am "test: change instance type"
git push
```

| Check | Expected result |
|---|---|
| Plan shows the instance type change | Diff includes `instance_type: "c5.2xlarge" → "c5.4xlarge"` |
| Only AWS plan triggered | GCP/Azure don't import `variables.tf` defaults from the AWS dir |

### Phase 5: Apply verification (requires approval + mergeable)

1. Get the PR approved by a second reviewer.
2. Comment `atlantis apply -d infra/terraform/environments/aws`.
3. **This step is DESTRUCTIVE** — only run against a throwaway AWS account or use `terraform plan` mode only. For verification purposes, confirm Atlantis **rejects** the apply without approval:

   | Scenario | Expected result |
   |---|---|
   | Apply without approval | Atlantis rejects with "apply requirements not met: approved" |
   | Apply on unmergeable PR (merge conflict) | Atlantis rejects with "apply requirements not met: mergeable" |
   | Apply with approval + mergeable | Atlantis runs `terraform apply` |

### Phase 6: Cross-module trigger verification

Verify that changes to shared modules trigger plans in all environments:

```bash
# Change a shared module
echo "# Cross-module trigger test" >> infra/terraform/modules/storage/variables.tf
git commit -am "test: cross-module trigger"
git push
```

| Check | Expected result |
|---|---|
| All 3 projects planned | `autoplan.when_modified` includes `../../modules/**/*.tf` |

### Phase 7: Shared script trigger verification

```bash
# Change a shared script
echo "# Script trigger test" >> infra/terraform/shared/scripts/forecast-task.sh
git commit -am "test: shared script trigger"
git push
```

| Check | Expected result |
|---|---|
| All 3 projects planned | `autoplan.when_modified` includes `../../shared/**/*` |

---

## 6. Error Scenarios to Test

| # | Scenario | How to trigger | Expected Atlantis behavior |
|---|---|---|---|
| E1 | Invalid HCL syntax | Add `resource "aws_s3_bucket" "bad {` (unclosed) | Plan fails → PR comment with parse error |
| E2 | Missing required variable | Remove `postgres_password` from tfvars | Plan fails → `Required variable not set` |
| E3 | Circular module dependency | Add `module.storage` dependency on `module.compute_spot` | Plan fails → cycle error |
| E4 | Provider version conflict | Pin conflicting `aws` provider versions in `versions.tf` | Init fails → provider constraint error |
| E5 | Broken module source path | Change `source = "../../modules/storage"` to `../../modules/nonexistent` | Init fails → module not found |
| E6 | Backend inaccessibility | Revoke AWS credentials mid-session | Init fails → S3 backend error |
| E7 | Merge conflict on PR | Push conflicting changes to main | Apply blocked — not mergeable |
| E8 | Large plan output (>64KB) | Add 100+ resources | Atlantis truncates and links to full output |

---

## 7. Verification Checklist (copy into PR)

```markdown
## Atlantis Verification Checklist

- [ ] **V1: Static checks pass locally**
  - [ ] `terraform fmt -check -recursive infra/terraform/` exits 0
  - [ ] `terraform validate` passes for aws, gcp, azure (with `-backend=false`)
  - [ ] `tflint` passes for all environments
  - [ ] All module `source` paths resolve correctly

- [ ] **V2: Atlantis receives PR webhook**
  - [ ] Atlantis logs show `Webhook received: pull_request`
  - [ ] Autoplan triggers for affected project(s)

- [ ] **V3: Plan succeeds per environment**
  - [ ] `forecast-aws` plan completes (or shows expected diff)
  - [ ] `forecast-gcp` plan completes (or shows expected diff)
  - [ ] `forecast-azure` plan completes (or shows expected diff)
  - [ ] Plan output posted as PR comment

- [ ] **V4: Cross-module triggering works**
  - [ ] Change to `modules/networking/main.tf` triggers all 3 plans
  - [ ] Change to `modules/storage/main.tf` triggers all 3 plans
  - [ ] Change to `shared/scripts/forecast-task.sh` triggers all 3 plans

- [ ] **V5: Apply guardrails work**
  - [ ] Apply rejected without approval
  - [ ] Apply rejected on unmergeable PR
  - [ ] Apply succeeds with approval + mergeable (optional — use throwaway account)

- [ ] **V6: Error reporting works**
  - [ ] Invalid HCL → error in PR comment
  - [ ] Missing variable → error in PR comment
  - [ ] Broken module path → error in PR comment

- [ ] **V7: Targeted plan works**
  - [ ] `atlantis plan -d infra/terraform/environments/aws` plans only AWS
  - [ ] `atlantis plan -d infra/terraform/environments/gcp` plans only GCP
  - [ ] `atlantis plan -d infra/terraform/environments/azure` plans only Azure
```

---

## 8. Files to Create

| File | Location | Purpose |
|---|---|---|
| `atlantis.yaml` | repo root | Atlantis project + workflow configuration |
| `.tflint.hcl` | repo root | tflint configuration for all providers |

---

## 9. Cleanup

After verification:

```bash
# Stop Atlantis
docker stop atlantis && docker rm atlantis  # if using Docker
# OR: Ctrl-C the atlantis server process     # if running natively

# Stop ngrok
# Ctrl-C the ngrok process

# Delete the test branch
git branch -D test/atlantis-verify
git push origin --delete test/atlantis-verify

# Delete the GitHub webhook
# Settings → Webhooks → click the test webhook → Delete
```
