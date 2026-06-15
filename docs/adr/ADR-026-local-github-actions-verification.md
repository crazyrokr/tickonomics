# ADR-026: Local GitHub Actions Verification with `act`

**Date:** 2026-06-14
**Status:** Accepted
**Related:** ADR-008 (CI/CD Pipeline), ADR-016 (CI/CD Pipeline Finalization)

> **Update (2026-06-15):** `pr-checks.yml` no longer declares a `services:`
> block — the service-container `integration-test` job was split into
> `.github/workflows/integration-tests.yml` because `act` 0.2.89 panics on
> service containers in dry-run (`act -n`). Service-bearing workflows are now
> kept out of the dry-run gate by a regression guard. The `services:` row in the
> table below and the "Timescale/Postgres in `pr-checks`" mention are
> superseded — see **ADR-027**.

> **Update (2026-06-15):** The Gradle task mirror described below
> (`verifyWorkflows`, `workflowLint`, `workflowDryRun`, etc.) was **removed**.
> `actionlint` now lives only in the `pre-commit` hook and `act` only in the
> `pre-push` hook, with the `Makefile` as the unified entry point
> (`make verify-workflows`). `build.gradle` contains no GitHub Actions
> references. The "build.gradle mirrors those as Gradle tasks" bullet in the
> Decision below is superseded.

> **Update (2026-06-15):** Hook installation is now **automatic on
> `./gradlew build`** via a first-party `buildSrc` plugin
> (`io.tickonomics.git-hooks`); see **ADR-028**. `make install-hooks` remains as
> the bootstrap path for installing before the first build. The "Developer setup
> gains one step: `make install-hooks` after clone" consequence below is
> softened: that step is now optional once any `./gradlew build` has run.

## Context

Workflow changes have repeatedly broken CI after push — most recently `benchmark.yml`
required a fixup commit (`2eee0f4 fix(benchmark): fix benchmark regression workflow`)
and several workflows are modified in-flight on this branch. The only feedback loop
today is "push and watch GitHub Actions," which costs a push, a round-trip, and
Actions minutes for errors that are detectable locally.

The goal is to catch workflow errors **before commit/push** without leaving the
developer machine. Two candidate tools were investigated:

- **[`nektos/act`](https://github.com/nektos/act)** — runs GitHub Actions locally in
  Docker containers that match GitHub's runner images. 70k★, mature, Go.
- **[`bahdotsh/wrkflw`](https://github.com/bahdotsh/wrkflw)** — validates and runs
  workflows locally with Docker/Podman/emulation runtimes and a TUI. Newer, Rust.

### What this repo's workflows actually use

Surveying `.github/workflows/*.yml`, the gate-critical workflows
(`pr-checks.yml`, `benchmark.yml`) and others rely on features that differ sharply
between the two tools:

| Feature used here | `act` | `wrkflw` |
|---|---|---|
| `pull_request` / `push` / `schedule` triggers (8 of 10 workflows) | ✅ | ❌ only `workflow_dispatch` |
| `services:` (Timescale/Postgres in `pr-checks`, `chaos-*`, `demo-report`) | ✅ | ⚠️ Docker/Podman only |
| `actions/cache` (used in every workflow) | ✅ | ❌ emulation; partial container |
| `upload-artifact` | ✅ | ❌ |
| `concurrency:` (`pr-checks`, `benchmark`) | ignored locally (harmless) | ❌ |
| Matrix builds, `needs:` DAG, reusable workflows | ✅ | ✅ / partial |
| Dry-run / graph resolution (`-n` / `-l`) | ✅ | ❌ |

Because `wrkflw` only fully supports `workflow_dispatch`, it **cannot execute 8 of
the 10 workflows** as written. Its remaining value is `wrkflw validate` — a static
syntax/structure check that is strictly weaker than `actionlint`, which is already
installed in this environment.

## Decision

Adopt **`act`** as the local execution verifier, layered with **`actionlint`** as
the fast static gate. Do **not** introduce `wrkflw`.

Verification is split into two tiers, because running the full Java/Python/frontend
DAG locally on every commit is too slow to gate every commit:

1. **Tier 1 — static (every commit, <1s, no Docker).** `actionlint` over the staged
   `.github/workflows/*.yml` files, wired as a git `pre-commit` hook. Catches YAML
   syntax, schema, and `${{ }}` expression errors before the commit lands.
2. **Tier 2 — execution (opt-in / pre-push).** `act -n` dry-runs the PR-gate
   workflows (`pr-checks.yml`, `benchmark.yml`) for the `pull_request` event,
   resolving the full job/step graph and pulling actions **without executing
   steps**. Single-job runs (`act -j`) are used to actually execute a target job.

Concrete artifacts:

- **`.actrc`** maps `ubuntu-latest` / `ubuntu-22.04` / `ubuntu-24.04` to the
  `catthehacker/ubuntu:act-*` runner images and pins `--container-architecture`.
  Per-machine overrides (image choice, secrets) go in `.actrc.local` (gitignored).
- **`Makefile`** exposes: `verify-workflows` (full pipeline), `workflow-lint`,
  `workflow-list`, `workflow-dryrun`, `workflow-run JOB=<id> [WF=...]`,
  `install-hooks`, `act-pull`, `test-hooks`, `test-gate`. This is the single
  entry point; the former Gradle task mirror was removed so there is one source
  of truth and `build.gradle` carries no GitHub Actions references.
- **`scripts/git-hooks/{pre-commit,pre-push}`** are version-controlled hook
  sources; `scripts/install-git-hooks.sh` copies them into `.git/hooks/`. The
  `pre-push` dry-run is opt-in (`WORKFLOW_DRYRUN_ON_PUSH=1` or
  `git config --bool workflow.dryrunOnPush true`), since the first run pulls a
  ~1–2 GB runner image.
- **`scripts/test-pre-commit-hook.sh`** exercises the hook with Given-When-Then
  scenarios including a false-positive guard (non-workflow YAML is never linted).

### Per-workflow local-execution strategy

| Workflows | Local mode | Rationale |
|---|---|---|
| `pr-checks.yml`, `benchmark.yml` | full / single-job (`-j`) | primary breakage surface; fully runnable locally |
| `chaos-tests.yml`, `chaos-benchmark.yml`, `demo-report.yml` | full run | `act` supports their `services:` containers |
| `codeql.yml`, `forecast-deploy.yml`, `deploy-*.yml` | **dry-run only** (`-n`) | need cloud creds / GitHub API / CodeQL DB; verify graph resolution only |

## Consequences

- Workflow errors are caught locally: static errors at `git commit`, structural
  errors at `git push` (opt-in), with no Actions minutes spent.
- `act` ignores `concurrency` locally (a no-op) and stores `actions/cache` /
  `upload-artifact` data locally rather than on GitHub — harmless for verification.
- Jobs that call the GitHub API with `secrets.GITHUB_TOKEN` (e.g. `benchmark.yml`'s
  "Comment on PR" step) will fail locally without a real token; run other jobs with
  `-j` or provide a token in `.secrets`. This is documented in the runbook.
- The first `act` run downloads the runner image (~1–2 GB); `make act-pull` makes
  that explicit and separable.
- Developer setup gains one step: `make install-hooks` after clone.

## Alternatives considered

- **`wrkflw` (run mode).** Rejected: it cannot execute `pull_request`-triggered
  workflows at all, and lacks artifacts/cache/service-containers-in-emulation —
  every gate-critical workflow here uses at least one of those.
- **`wrkflw validate` (static only).** Rejected in favor of `actionlint`, which is
  already installed, more thorough, and the de-facto standard for GitHub Actions
  static analysis.
- **`act` as the sole gate.** Rejected as a commit gate: the full local DAG is too
  slow and the image pull too heavy to run on every commit. The two-tier split keeps
  the commit gate instant while reserving execution for push/on-demand.
- **GitHub Local Actions VS Code extension (wraps `act`).** Compatible and optional;
  developers who prefer an editor-driven flow can use it on top of this setup.
