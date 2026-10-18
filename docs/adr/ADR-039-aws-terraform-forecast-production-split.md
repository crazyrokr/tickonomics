# ADR-039: Split AWS Terraform into forecast (spot) and production (always-on) stacks

**Status:** Proposed
**Date:** 2026-06-24

## Context

`infra/terraform/environments/aws/` was a single monolithic stack that created **both** the ephemeral
spot-forecast infrastructure **and** the always-on production tier (the `t3.large` hosting instance +
`database`/`observability`/`dns`/`backup`/`budget` from Track 14, ADR-020). Applying it incurred
~$72/mo always-on regardless of whether a forecast ran — dominated by the hosting instance (~$61/mo;
see runbook §9).

The forecast pipeline is the **primary** use case (the runbook is built around one-shot spot runs);
the always-on hosting is an optional layer added later. Running a forecast should not require paying
for the always-on tier. The two paths, however, share three "base" modules — `networking` (VPC),
`storage` (S3 results + IAM), `container_registry` (ECR) — consumed by both `compute_spot` and
`hosting`. No `terraform_remote_state` existed in the repo, and no state had been applied yet (still
in plan phase), so this is a green-field restructure with no state migration.

## Decision

1. **Split into two sibling stacks**, replacing `environments/aws/`:
   - **`environments/aws-forecast/`** — standalone, cheap. Owns the shared base (`networking`,
     `storage`, `container_registry`) **plus** the spot path (`compute_spot`, `orchestrator`,
     `monitoring`) and the operator `admin_role` (an assumable role exported for CLI/Terraform
     AssumeRole chaining). **No always-on EC2**; applying runs a forecast and self-terminates.
   - **`environments/aws-production/`** — always-on, opt-in. Owns `hosting`, `database`,
     `observability`, `secrets`, `dns`, `backup`, `budget`, plus the inline `aws_security_group.hosting_sg`
     and the workspace-based `locals`.

2. **Base rides with the forecast stack.** This keeps the forecast stack independently deployable
   (the primary, cheap use case). The production stack consumes the base cross-stack via
   `terraform_remote_state` — the first cross-stack state reference in the repo. The forecast stack's
   `outputs.tf` exposes `vpc_id`, `public_subnet_id`, `results_bucket_name`, `s3_write_policy_arn`,
   and the three ECR repository URLs for production to read.

3. **Separate state keys** in the same bootstrapped bucket/lock table (ADR-038):
   `aws-forecast/terraform.tfstate` and `aws-production/terraform.tfstate`.

4. **Ordering.** Apply: forecast first, then production (production's plan/apply reads forecast
   state). Destroy: production first, then forecast. Atlantis enforces this via a
   `depends_on: [forecast-aws]` on the `production-aws` project.

5. **Modules reused, a few hardened.** All modules under `infra/terraform/modules/` are sourced
   directly by both stacks; none moved. The split landed with small module edits: a new `admin-role`
   module (assumable operator role, owned by `aws-forecast`); S3 SecureTransport deny policies plus
   multipart-abort lifecycle rules in `storage` and `backup`; EBS encryption in `storage`; the
   `aws_lambda_function_event_invoke_config` rename plus arm64 architectures in `backup`/`orchestrator`;
   `stage`/`prod` environment naming in `hosting`/`budget`; Graviton `t4g` defaults in `hosting`.
   Every `module.networking/storage/container_registry` reference inside `aws-production/main.tf` reads
   from `data.terraform_remote_state.forecast.outputs.*`.

## Consequences

**Positive:**

- Always-on production is **opt-in**: forecasts run standalone at ~$0.38/run with no always-on bill.
- Independent lifecycles — forecast and production can be applied/destroyed independently (subject to
  the base coupling in D2).
- The runbook §9 cost picture and §3 deploy/§8 cleanup flows now reflect the real two-stack model.
- Cost/ownership boundary is explicit: the expensive compute is isolated in its own stack.

**Negative:**

- Production now **depends on the forecast stack's state existing** — it cannot plan/apply until
  forecast is applied, and destroying forecast while production exists breaks production's references.
- A new **cross-stack `terraform_remote_state` pattern** (first in repo) adds a dependency edge to
  reason about.
- **Sensitive vars** (`postgres_password`, API keys, `oauth_client_secret`, `grafana_password`) are
  duplicated across both stacks' tfvars until consolidated.
- Two stacks to plan/apply (Atlantis two projects) with apply-ordering discipline.
- `aws-forecast` is not strictly "spot only" — it includes the shared base (VPC/S3/ECR), though those
  are ~$0 at idle; this was accepted to keep the forecast stack self-contained.

## Related ADRs

- **ADR-038** (state backend bootstrap) — the bucket/lock table both state keys live in.
- **ADR-020** (Track 14 production infra) — the always-on hosting tier now isolated in
  `aws-production`.
- **ADR-022** (foundation realignment) — the multi-cloud environment layout this sits within.
- **ADR-026** (local Actions verification / Atlantis) — the two-project Atlantis flow.

## Open follow-ups

- Consolidate shared/sensitive tfvars (a `common.auto.tfvars`, or SSM/secrets manager) so they are not
  duplicated across both stacks.
- Decide whether GCP/Azure get the same forecast/production split (today they are single spot-only
  stacks).
- If the forecast↔production coupling becomes painful, extract the shared base into its own
  `aws-base` stack (the 3-stack alternative deferred during this decision).
