# ADR-016: CI/CD Pipeline Finalization (Track 9)

**Date:** 2026-06-13
**Status:** Accepted
**Supersedes/extends:** ADR-008 (CI/CD Pipeline)

## Context

The plan v6 verification report scored the CI/CD pipeline (Track 9) at ~55%. The eight
workflow files existed, but each carried concrete gaps: no static analysis gate, no
OpenTelemetry agent in the test JVM, no benchmark regression, deploy workflows were
`echo` placeholders (no image build/push, no Flyway, no real deploy), `deploy-staging`
triggered on the wrong branch, `deploy-production` had no manual approval gate, the chaos
workflow still referenced the deprecated Polygon WebSocket source, the chaos report was
never posted, the benchmark job was an `echo`, the standalone `benchmark.yml` was missing,
and there was no security scanning (CodeQL) or landing-page performance auditing
(Lighthouse).

ADR-008 documented the original four workflows and explicitly left the staging/production
deploy steps as placeholders "until Track 11 provides Docker infrastructure." This ADR
records the decisions taken to close every gap and bring the pipeline to a runnable,
structure-complete state.

Two scope decisions were fixed up front with the project owner:

1. **Lint is advisory.** The Java codebase (162 main classes) has never had a Checkstyle or
   SpotBugs configuration. A blocking gate would surface hundreds of legacy violations and
   stall every PR, so the gate runs and reports but never fails the build.
2. **The benchmark engine is `pytest-benchmark`.** The plan's Java JMH tasks
   (`backtestBenchmark`, `regulatoryScenarioTest`) and helper scripts
   (`compare_benchmarks.py`, `rds_check.py`) do not exist; building them is computation /
   backtesting (Track 5/8) work, not CI/CD wiring. A real, runnable Python benchmark
   regression closes the Track 9 gap now.

## Decision

### Static analysis — advisory Checkstyle + SpotBugs

Apply the built-in `checkstyle` plugin and the `com.github.spotbugs` Gradle plugin
(version `6.5.6`, the latest release, which requires Gradle ≥ 8.2 and is compatible with
the project's Gradle 9.5.1 and configuration cache) to all `subprojects` in
`build.gradle`. Configure both with `ignoreFailures = true` and a relaxed Checkstyle
configuration (`config/checkstyle/checkstyle.xml`). Register an aggregate `lint` task that
runs `checkstyleMain`, `checkstyleTest`, `spotbugsMain`, and `spotbugsTest`.

A dedicated `code-quality` job in `pr-checks.yml` runs `./gradlew lint` with
`continue-on-error: true` and uploads the XML/HTML reports as artifacts. Because
`ignoreFailures` is set, the normal `./gradlew build` / `check` lifecycle stays green;
lint surfaces only as reports and (eventually) non-blocking warnings. Tightening to a
blocking gate is a follow-up once the legacy codebase is cleaned against the new
configuration.

### OpenTelemetry in tests

The Java build job downloads the OpenTelemetry Java agent (`2.28.1`, pinned to the latest
Maven Central release) and runs the test step with `JAVA_TOOL_OPTIONS` pointing at the
agent and `OTEL_EXPORTER=none`. This verifies that instrumentation spans are created
without exporting traces to a backend during CI.

### Deploy strategy — real, secret-gated SSH + docker compose

Track 14 (production infrastructure) is 0% implemented, so there is no cluster to deploy
to. Rather than keep the deploy steps as inert placeholders, they are made real and
runnable using the same `appleboy/ssh-action` + `docker compose` pattern already proven in
`deploy-landing.yml`:

- **`deploy-staging.yml`** triggers on `push` to `main` (fixed from `develop`). It builds
  and pushes four images (backend, analytics, dashboard, landing) to GHCR tagged with the
  commit SHA and `latest`, applies Flyway migrations via the `flyway/flyway:10-alpine`
  container against `persistence/src/main/resources/db/migration/`, deploys over SSH with
  `docker compose pull && up -d`, runs smoke checks against `/actuator/health`, and updates
  the demo environment.
- **`deploy-production.yml`** is `workflow_dispatch`-only (manual promotion) with an
  `image_tag` input and a `skip_migrate` toggle. The `promote` job declares
  `environment: production`, which is where the required-reviewer approval gate is enforced
  via GitHub environment protection settings. It re-tags the promoted SHA as `latest`, runs
  Flyway against the production DB, deploys over SSH, waits up to 60 s for health, and
  creates a GitHub release.

Every deploy-related job and step that needs a host or database is guarded by secret
presence (e.g. `if: secrets.STAGING_SSH_KEY != ''`, `if: secrets.STAGING_DB_URL != ''`).
Image build/push (GHCR) and Flyway run unconditionally once their secret exists; the SSH
deploy activates the moment a host is provisioned. Nothing breaks when a secret is absent —
the guarded step simply skips.

### Benchmark — pytest-benchmark regression

`benchmark.yml` (standalone, triggered on `computation/**` / `analytics/**` PRs and
`workflow_dispatch`) and the `benchmark` job in `chaos-benchmark.yml` run a real
pytest-benchmark regression: install `pytest-benchmark`, cache `analytics/.benchmarks` as
the baseline, run `--benchmark-only --benchmark-compare --benchmark-compare-fail=mean:10%`,
and post a markdown report as a PR comment. Both are advisory (`continue-on-error`) until a
stable baseline is established.

The plan's Java JMH benchmark tasks and the `compare_benchmarks.py` / `rds_check.py`
scripts are explicitly deferred to Tracks 5/8; the `benchmark.yml` job records this
deferral in its final step.

### Security and performance scanning

- **`codeql.yml`** runs `github/codeql-action` over `java-kotlin`, `python`, and
  `javascript-typescript` on pushes/PRs to `main` and on a weekly schedule
  (`17 6 * * 1`), with `security-events: write`.
- **Lighthouse CI** (`.github/lighthouse/lighthouserc.js`) audits the landing page in the
  PR-checks landing matrix leg. Assertions are advisory (`warn`) and upload to temporary
  public storage.

### Chaos workflow cleanup

`chaos-tests.yml` Scenario 4 was renamed from the deprecated "Polygon WebSocket
disconnect" to "Finnhub WebSocket disconnect" (the v6 free-tier source). The workflow now
declares `permissions: issues: write`, generates a `chaos-report.md`, and posts it as a
comment on a tracking issue (`vars.CHAOS_TRACKING_ISSUE`) via `gh issue comment` — guarded
so it skips cleanly when the variable is unset.

### Regression test

`scripts/validate-cicd.py` encodes the post-finalization contract as a PyYAML-based
structural test (Given-When-Then assertions over every workflow). It is the regression test
for this configuration and is run as the primary verification step.

## Consequences

- The pipeline is structure-complete: every gap from the verification report is closed
  with working, runnable configuration rather than `echo` placeholders.
- The lint gate cannot break existing PRs (`ignoreFailures = true`); it provides reports
  only. The cost is build time for Checkstyle/SpotBugs during `check`, and the deferred
  payoff of a blocking gate.
- Deploys are real but inert until the operator provisions a host and sets the
  `STAGING_*` / `PROD_*` secrets and variables. Production promotion additionally requires
  configuring required reviewers on the `production` GitHub environment.
- The benchmark regression is Python-only; Java/computation performance regression
  detection remains a Track 5/8 deliverable.
- Local validation could not fully resolve the SpotBugs Gradle plugin because the Gradle
  Plugin Portal is unreachable from this environment; the plugin coordinate
  (`com.github.spotbugs:com.github.spotbugs.gradle.plugin:6.5.6`) was verified against the
  upstream GitHub releases API, and CI (with normal network) resolves it. The build script
  was confirmed to parse (it reached plugin resolution, not a syntax error).
