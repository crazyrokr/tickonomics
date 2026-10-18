# ADR-038: Terraform State Backend Bootstrap Stack

**Status:** Proposed
**Date:** 2026-06-24

## Context

The forecast environments (`infra/terraform/environments/{aws,gcp,azure}/backend.tf`) store state in
cloud backends. The AWS environment stores state in an S3 bucket (`tickonomics-terraform-state`)
with a DynamoDB lock table (`tickonomics-terraform-locks`). Two facts make this a recurring problem:

1. **Nothing in the repo created those resources.** The first `terraform init` in a fresh account
   fails with `Failed to get existing workspaces: S3 bucket "tickonomics-terraform-state" does not
   exist`. This was hit during local bring-up (and is documented as runbook §7.8).
2. **`terraform init` cannot self-create its own backend.** Terraform connects to the configured
   backend (listing workspaces / reading state) *before* it can provision any resource, so the
   bucket + table must already exist.

The runbook previously required hand-typed `aws s3api` / `aws dynamodb create-table` commands (still
listed in §2.4 as an alternative). Those are untracked, drift-prone, easy to get wrong (encryption,
versioning, public-access block), and invisible to review. The repo is multi-cloud and CI-applied
(ADR-026 / Atlantis), so the backend bootstrap should be a version-controlled, repeatable artifact
that matches the environment `backend.tf` contract — not a paste of CLI commands.

## Decision

1. **Add a dedicated bootstrap stack** at `infra/terraform/bootstrap/`. It provisions the S3 state
   bucket and DynamoDB lock table consumed by `environments/aws-forecast/backend.tf` and `environments/aws-production/backend.tf` — versioning enabled,
   SSE-AES256 default encryption, public access fully blocked, `PAY_PER_REQUEST` lock table with a
   `LockID` hash key (matching `backend.tf`).

2. **Local backend by design.** The bootstrap stack uses `backend "local"`. The state of the
   resources that *back* remote state cannot itself live in that remote backend — that is circular.
   It is applied once per AWS account, before the first environment `terraform init`.

3. **Ownership boundary.** The bootstrap stack **owns** the state-backend resources permanently; the
   environments only **consume** them. Environments must never declare or import the state bucket or
   lock table: doing so would create a circular dependency (the resource holding the state that
   tracks it) and expose state storage to accidental `terraform destroy`.

4. **Names are variable-driven but default to the exact `backend.tf` values**
   (`tickonomics-terraform-state`, `us-east-1`, `tickonomics-terraform-locks`). Any change must be
   mirrored in every environment's `backend.tf`.

5. **Runbook wiring.** §2.4 recommends the bootstrap stack as the primary method (manual CLI kept as
   a faster, untracked alternative); §3.1 gates the first `terraform init` on the backend existing.

6. **Scope: AWS only.** GCP (GCS) and Azure have equivalent one-time backend bootstrap needs; they
   remain manual steps for now (follow-up).

## Consequences

**Positive:**

- Backend infrastructure is version-controlled, peer-reviewed, reproducible, and idempotent — no
  more hand-typed CLI commands drifting from the real configuration.
- Self-documents the backend contract (encryption, versioning, locking, public-access block) in code
  rather than prose.
- Recovery from a lost local bootstrap state is trivial via `terraform import` (documented in
  `infra/terraform/bootstrap/README.md`).
- The ownership boundary (D3) prevents circular dependencies and accidental destruction of state
  storage.

**Negative:**

- Adds a second Terraform stack to maintain and a one-time procedural step (apply bootstrap before
  the first environment `init`) — small operational surface.
- Bootstrap state is local and git-ignored (root `.gitignore` ignores `*.tfstate`), so it lives only
  on the operator's machine; loss requires re-import (mitigated: import is documented and trivial).
- The state bucket name is globally unique across all of S3; a collision forces a rename propagated
  to every environment's `backend.tf`.
- Multi-cloud asymmetry: only AWS has a bootstrap stack today; GCP/Azure stay manual until their
  equivalents are added.

## Related ADRs

- **ADR-026** (local GitHub Actions verification / Atlantis apply flow) — the CI apply path that
  this one-time bootstrap precedes for a fresh account.
- **ADR-022** (foundation realignment) — the multi-cloud environment layout this stack sits within.
- Forecast deployment runbook (§2.4 / §3.1 / §7.8) — the operational consumer of this stack.

## Open follow-ups

- Add equivalent bootstrap configs for GCP (GCS) and Azure backends so the one-time step is uniform
  across clouds.
- Decide whether to commit the bootstrap local state to a secure store (or seed a dedicated tiny
  remote backend) if operational drift on the operator's machine becomes a concern.
- Evaluate HCP Terraform as an alternative managed remote backend for environments that want to avoid
  per-account cloud state buckets entirely.
