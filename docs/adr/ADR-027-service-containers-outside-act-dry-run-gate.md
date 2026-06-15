# ADR-027: Service-Container Jobs Are Kept Out of the `act` Dry-Run Gate

**Date:** 2026-06-15
**Status:** Accepted
**Related:** ADR-026 (Local GitHub Actions Verification with `act`), ADR-016 (CI/CD Pipeline Finalization)

## Context

The Tier-2 step of the verification pipeline (`make verify-workflows`; ADR-026)
runs `act -n pull_request -W <gate>` over the gate workflows (`pr-checks.yml`,
`benchmark.yml`). With
`act` 0.2.89 (the current Homebrew stable, and the installed version) this
crashed with a Go nil-pointer panic:

```
panic: runtime error: invalid memory address or nil pointer dereference
[signal SIGSEGV: segmentation violation code=0x1 addr=0xa0 pc=0xc806ea]
github.com/nektos/act/pkg/container.(*containerReference).GetHealth(...)
  pkg/container/docker_run.go:175
github.com/nektos/act/pkg/runner.(*RunContext).waitForServiceContainer.1(...)
  pkg/runner/run_context.go:593
```

**Root cause (in `act`, not in our workflows):** in dry-run mode (`-n`) no
Docker client is wired up, yet `act` still walks the `waitForServiceContainer`
→ `containerReference.GetHealth()` path for any job that declares a `services:`
block. `GetHealth` calls `cr.cli.ContainerInspect(ctx, cr.id)` on a nil `cr.cli`
and dereferences it → SIGSEGV. The crash fires exactly when the dry-run reaches
`pr-checks.yml`'s `integration-test` job (TimescaleDB service container with
`--health-cmd pg_isready`); `benchmark.yml`, which has no `services:`, dry-runs
cleanly. Reproduced locally: gate workflows with services panic; service-free
ones exit 0.

The only upstream fix, [nektos/act PR #5782](https://github.com/nektos/act/pull/5782)
("Fix segmentation fault in dry-run mode with service containers"), was **closed
without being merged** on 2026-03-05 and its branch deleted — so no released
`act` version contains the fix. The workflow YAML itself is valid (actionlint
passes; it runs correctly on real GitHub Actions, where service containers are
actually created).

## Decision

**Keep service-container jobs out of the `act` dry-run gate.** Concretely:

1. **Split** the `integration-test` job (and its TimescaleDB `services:`
   container) out of `pr-checks.yml` into a new
   `.github/workflows/integration-tests.yml`. The gate workflows
   (`pr-checks.yml`, `benchmark.yml`) now declare no `services:` blocks, so
   `act -n` resolves them without panic.
2. The new workflow runs on the same `pull_request` trigger, with a **distinct
   concurrency group** (`integration-${{ github.ref }}`): GitHub shares
   concurrency groups across workflows, so reusing `pr-<ref>` would let the two
   workflows cancel each other mid-run.
3. The `integration-test` job remains **self-contained** (own checkout / Java /
   Gradle cache / TA-Lib build) — its previous `needs: java-build-test` was
   dropped because GitHub Actions does not support cross-workflow `needs`. Since
   the job compiles everything `:integration-tests:test` needs anyway, the only
   behavioural change is that integration tests now run independently of the
   unit-test job's pass/fail rather than being skipped on its failure.
4. **Regression guard:** `scripts/test-gate-workflows.sh` (Given-When-Then,
   wired into `make verify-workflows` and runnable as `make test-gate`) asserts
   the gate workflows declare no `services:` block, and includes a true-positive
   case so it cannot silently false-pass. If a future change adds a service
   container to a gate workflow (or moves one back into `pr-checks.yml`), the
   guard fails with a pointer to this ADR.

Service-bearing workflows that are **not** in the dry-run gate
(`chaos-tests.yml`, `chaos-benchmark.yml`, `demo-report.yml`, and now
`integration-tests.yml`) are still linted by actionlint and remain runnable via
`act -j <job> -W <file>` (single-job execution does not hit the dry-run
`GetHealth` path). The constraint is specifically on the `act -n` gate.

## Consequences

- `make verify-workflows` no longer panics; Tier-2 dry-run coverage over the
  gate workflows is restored.
- The status-check name for integration tests is unchanged (`Integration Tests`),
  so branch-protection / required-check configuration keyed on that name is
  unaffected. The check now lives under a separate workflow file in the Actions
  UI.
- The `services:`-in-gate invariant is now machine-enforced, so this class of
  regression cannot recur silently.
- If/when `act` ships a release containing the #5782 fix, the guard can be
  relaxed and `integration-test` moved back into `pr-checks.yml` if desired — but
  keeping integration tests in their own workflow is a reasonable permanent
  structure regardless.

## Alternatives considered

- **Lint-only for `pr-checks.yml`, dry-run `benchmark.yml` only.** Rejected: it
  drops Tier-2 graph resolution for the primary gate workflow to dodge a bug in
  `act`, losing real coverage. Splitting the service job restores full dry-run
  coverage instead of reducing it.
- **Build `act` from a fork carrying #5782.** Rejected: heavy developer-setup
  burden and a fork divergence for a single closed PR; the workflow split is a
  one-time, repo-local change with no external dependency.
- **Make `make workflow-dryrun` tolerate the panic (non-fatal).** Rejected:
  swallowing a panic hides real dry-run failures and leaves the pipeline
  partially green for the wrong reason. The split makes the pipeline genuinely
  green.
