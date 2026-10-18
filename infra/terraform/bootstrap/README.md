# Terraform State Backend Bootstrap

One-time stack that provisions the **remote state backend** consumed by every environment
(`infra/terraform/environments/{aws-forecast,aws-production,gcp,azure}/backend.tf`): the S3 state bucket and the
DynamoDB lock table.

`terraform init` against an S3 backend cannot create that bucket itself — Terraform connects to
the backend *before* it can provision anything — so the backend must already exist. This stack
exists to create it, once per AWS account. See [ADR-038](../../../docs/adr/ADR-038-terraform-state-backend-bootstrap.md).

## Why a separate stack

The environments **use** the state backend; they do **not** manage it. Owning the state bucket
from inside the state it stores would be circular and fragile (an `apply` could destroy the
bucket holding the state that tracks it). This stack therefore keeps its **own state local**
(`backend "local"`) and owns the bucket + table permanently.

## Usage

```bash
cd infra/terraform/bootstrap
terraform init                 # local backend — no S3 access needed
terraform plan                 # review the bucket + table to be created
terraform apply                # creates tickonomics-terraform-state + tickonomics-terraform-locks

cd ../environments/aws-forecast
terraform init                 # now the S3 backend resolves (aws-production inits separately)
```

Defaults match the two AWS stack keys — `environments/aws-forecast/backend.tf` and
`environments/aws-production/backend.tf` — plus `gcp`/`azure` (same bucket
`tickonomics-terraform-state`, `us-east-1`, `tickonomics-terraform-locks`, SSE enabled). Override the
names/region if needed — they must stay in sync with every environment's `backend.tf`.

> **Globally-unique bucket name.** S3 bucket names are worldwide-unique; if
> `tickonomics-terraform-state` is taken, change `var.state_bucket` here **and** in every
> environment's `backend.tf`.

## Recovering from lost local state

This stack's state (`terraform.tfstate`) is git-ignored (see root `.gitignore`) and lives only
on the operator's machine. If lost, reconstruct it from the live resources — no re-creation:

```bash
cd infra/terraform/bootstrap
terraform init
terraform import aws_s3_bucket.state tickonomics-terraform-state
terraform import aws_dynamodb_table.locks tickonomics-terraform-locks
terraform plan   # should show no changes
```

## Tearing down

Destroying this stack deletes the state bucket and lock table, taking all environment state with
it. Back up state first and destroy the environments before destroying the backend.
