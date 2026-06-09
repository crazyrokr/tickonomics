---
name: Terraform Infrastructure Change
about: Changes to forecast infrastructure
---

## Summary

<!-- Brief description of what this PR changes -->

## Affected Environments

- [ ] AWS (`infra/terraform/environments/aws`)
- [ ] GCP (`infra/terraform/environments/gcp`)
- [ ] Azure (`infra/terraform/environments/azure`)
- [ ] Shared modules (`infra/terraform/modules/`)
- [ ] Shared scripts (`infra/terraform/shared/`)

## Pre-Merge Verification

- [ ] `terraform fmt -check -recursive infra/terraform/` passes
- [ ] `terraform validate` passes for all affected environments
- [ ] `tflint` passes for all affected environments
- [ ] All module `source` paths resolve correctly

## Atlantis Verification

> Atlantis will automatically run `terraform plan` on this PR for each affected environment.

- [ ] Atlantis autoplan triggered correctly for affected project(s)
- [ ] Plan output reviewed and matches expectations
- [ ] No unexpected resource destruction or recreation

## Post-Merge

- [ ] Atlantis `terraform apply` approved and completed
- [ ] Smoke test passed (if compute resources changed)
