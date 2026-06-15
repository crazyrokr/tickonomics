# ADR-029: Java tests on all pull request events

**Date:** 2026-06-15
**Status:** Accepted
**Related:** ADR-016 (CI/CD Pipeline Finalization), ADR-026 (Local GitHub Actions Verification), ADR-027 (Service Containers Outside the act Dry-Run Gate)

## Context

The Java test workflows triggered on `pull_request` only for PRs targeting a branch
in a fixed whitelist:

```yaml
on:
  pull_request:
    branches: [main, test]
```

This appeared in both `pr-checks.yml` (the `java-build-test` job, `./gradlew build
-x :integration-tests:test`) and `integration-tests.yml` (the Testcontainers/TimescaleDB
suite). The whitelist was an accident of copy-paste from a deploy workflow, not an
intentional policy.

The active integration branch is `develop` — every feature PR lands there (e.g. PR #32
`feature/local-workflow-verification`), and `develop` is later promoted toward `main`.
Because `develop` was not in `[main, test]`, **PRs targeting `develop` ran no Java tests
at all**. For a personal quant-research sandbox whose entire trust model is "a signal is
only as good as the tests that gate it" (ADR-022), silently skipping the test gate on the
branch where work actually happens is the worst possible failure mode: it fails open.

## Decision

Remove the `branches` filter from the `pull_request` trigger in both Java test workflows,
so Java unit **and** integration tests run on **every** `pull_request` event regardless of
target branch:

```yaml
on:
  pull_request:
```

A regression assertion in `scripts/validate-cicd.py`
(`assert_pr_checks_runs_on_all_pull_requests`) fails if `branches` or `branches-ignore`
is re-added to `pr-checks.yml`'s `pull_request` trigger, so the policy cannot silently
regress. A `types:` activity filter remains permitted; only branch scoping is forbidden.

## Consequences

- Every PR — to `main`, `develop`, `test`, or any feature branch — runs the full Java
  suite: unit tests via `pr-checks.yml`, integration tests via `integration-tests.yml`.
  The develop blind spot is closed.
- The per-PR `concurrency` groups (`pr-${{ github.ref }}`, `integration-${{ github.ref }}`)
  are keyed on the PR ref, not the target branch, so `cancel-in-progress` still supersedes
  stale runs on repeated pushes to the same PR.
- Integration tests now run on every PR, which spends more Actions minutes (the
  `services:` TimescaleDB container starts per run). This is the accepted trade for a
  sandbox that prioritizes signal-verification coverage over CI cost.
- `integration-tests.yml` remains outside the local `act -n` dry-run gate (ADR-027) — its
  service container still panics act 0.2.89. Removing the branch filter does not affect
  graph resolution, so `pr-checks.yml` (a gate workflow) still dry-runs clean.

## Alternatives considered

- **Add `develop` to the whitelist (`[main, test, develop]`).** Rejected as brittle: the
  filter would need editing for every future integration branch, and the same blind spot
  recurs the moment a new branch becomes active. An allowlist is the wrong shape for "run
  on PRs" — the exception is restricting, not broadening.
- **Keep `[main, test]` and rely on promotion testing.** Rejected: it moves the test gate
  to merge time, where a red run blocks integration rather than informing it, and offers
  no feedback during PR review.
- **Add a `paths:` filter to skip docs-only PRs.** Out of scope for this decision, and it
  would reintroduce a fail-open path (a PR that touches only a comment in a Java file but
  is classified "docs" would skip the gate). Coverage over efficiency for this sandbox.
