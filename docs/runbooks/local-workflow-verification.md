# Runbook: Local GitHub Actions Verification

Verify GitHub Actions workflows locally with `act` + `actionlint` before pushing.
Background and rationale: [ADR-026](../adr/ADR-026-local-github-actions-verification.md).

## Prerequisites

- `act` (`brew install act`)
- `actionlint` (`brew install actionlint`)
- Docker daemon running

Both tools are already present in this environment. Confirm with:

```sh
act --version
actionlint --version
```

## One-time setup

Install the git hooks (Tier 1 commit gate is always on; Tier 2 pre-push dry-run is opt-in):

```sh
make install-hooks
```

The hooks also auto-install (and refresh) on every `./gradlew build` via the
first-party `io.tickonomics.git-hooks` plugin (ADR-028); the step above is only
needed to get the hooks in place before the first build.

Pre-pull the runner image so the first dry-run is not dominated by a download (~1–2 GB, once):

```sh
make act-pull
```

## Makefile entry points

The `Makefile` is the canonical entry point for workflow verification. The Gradle
tasks that previously mirrored these were removed — `build.gradle` carries no
GitHub Actions references. `verify-workflows` runs the whole pipeline.

```sh
make verify-workflows            # full pipeline: lint + list + hook tests + gate guard + dry-run
make workflow-lint               # strict actionlint over all workflows
make workflow-list               # act -l job graph (no Docker)
make workflow-dryrun             # act -n on pr-checks + benchmark (pulls image first run)
make workflow-run JOB=python-test # run one job (WF=path optional)
make install-hooks               # install the pre-commit/pre-push hooks
make test-hooks                  # Given-When-Then tests for the pre-commit hook
make test-gate                   # guard: gate workflows declare no services: blocks
make act-pull                    # pre-pull the runner image
```

Each target shells out to `act`, `actionlint`, `docker`, or a `scripts/*.sh` test
directly and fails fast when the tool is missing.

## Day-to-day

### Lint all workflows (static, instant)

```sh
make workflow-lint            # actionlint over every .github/workflows/*.yml
```

The `pre-commit` hook runs this automatically on staged workflow files. Non-workflow
YAML is never linted by the hook.

### Resolve a workflow's job/step graph (no execution)

```sh
make workflow-list            # act -l : list jobs/events for all workflows (no Docker)
make workflow-dryrun          # act -n : resolve pr-checks + benchmark graphs for pull_request
```

### Run a single job locally

Routine development should target one job rather than the whole DAG — the Java 25
build plus TA-Lib native compile is heavy:

```sh
make workflow-run JOB=python-test
make workflow-run JOB=benchmark WF=.github/workflows/benchmark.yml
```

### Run a full workflow

```sh
act pull_request -W .github/workflows/pr-checks.yml
```

## Opt-in: pre-push dry-run

The `pre-push` hook dry-runs the gate workflows with `act -n`. Enable with one of:

```sh
export WORKFLOW_DRYRUN_ON_PUSH=1
git config --bool workflow.dryrunOnPush true
```

## Secrets

Provide local-only secrets in a gitignored `.secrets` file at the repo root:

```sh
echo 'GITHUB_TOKEN=ghp_...' > .secrets
act pull_request -W .github/workflows/benchmark.yml --secret-file .secrets
```

`act` auto-provides a `GITHUB_TOKEN`; jobs that call the GitHub API (e.g.
`benchmark.yml`'s "Comment on PR" step) still need a real token to succeed, so prefer
running other jobs with `-j` unless you are specifically testing the comment step.

## Per-workflow strategy

| Workflows | Local mode | Notes |
|---|---|---|
| `pr-checks.yml`, `benchmark.yml` | full / single-job (`-j`) | primary breakage surface |
| `chaos-tests.yml`, `chaos-benchmark.yml`, `demo-report.yml` | full run | `services:` containers supported |
| `codeql.yml`, `forecast-deploy.yml`, `deploy-*.yml` | dry-run only (`-n`) | cloud creds / API / CodeQL; verify graph only |

## Known local-only behavior

- `concurrency:` is ignored by `act` (no-op) — harmless.
- `actions/cache` and `upload-artifact` work but store data locally, not on GitHub.
- The runner image is a close match to GitHub's, not identical; `setup-*` actions
  install their own JDK/Python/Node into the container, so base-image versions rarely matter.

## Testing the hook itself

The `pre-commit` hook has Given-When-Then tests covering the allow/block paths and a
false-positive guard (non-workflow YAML is not linted):

```sh
make test-hooks
```

## Troubleshooting

- **`act` pulls a large image on first run.** Expected once; `make act-pull` isolates it.
- **A job fails only locally on `secrets.GITHUB_TOKEN` / API calls.** Provide a token
  in `.secrets`, or skip that job with `-j`.
- **Architecture mismatch errors.** `.actrc` pins `--container-architecture linux/amd64`;
  override in `.actrc.local` if running on a different host arch.
- **`actionlint` is noisy on `run:` blocks.** Install `shellcheck` for deeper checking,
  or note that warnings (unlike errors) do not fail the gate.
