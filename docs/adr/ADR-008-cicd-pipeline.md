# ADR-008: CI/CD Pipeline

**Date:** 2026-05-30
**Status:** Accepted

## Context

The project has no CI/CD infrastructure. Builds and tests run only locally. The monorepo contains four distinct build targets (Java 25/Gradle, Python 3.12/FastAPI, Node 22/Next.js SSR, Node 22/Next.js static export) plus a TA-Lib native C library that must be compiled before Java tests run. Integration tests require PostgreSQL with TimescaleDB.

## Decision

Use GitHub Actions as the CI/CD platform (native integration with the GitHub repository). Define four workflows:

1. **pr-checks.yml** — Triggered on pull requests to `main` and `test`. Four parallel jobs: Java build+test (with TA-Lib native build), Python pytest, frontend build+test (matrix for landing + dashboard), integration tests (TimescaleDB service container, depends on Java job).

2. **deploy-staging.yml** — Triggered on push to `develop`. Builds all artifacts, deploys to staging environment, runs smoke tests. Deploy step requires Track 11 Docker infrastructure.

3. **deploy-production.yml** — Triggered on push to `main`. Uses GitHub `environment: production` with required reviewers for manual approval gate. Full build+test before deploy.

4. **chaos-benchmark.yml** — Scheduled weekly (Monday 06:03 UTC). Runs full test suite including integration tests against a live TimescaleDB container. Benchmark job runs performance validation.

Key technical choices:
- JDK 25 via Temurin distribution (`actions/setup-java@v4`)
- Python 3.12 (minimum required by `pyproject.toml`)
- Node.js 22 for both Next.js apps
- Gradle cache keyed on `*.gradle` and `gradle-wrapper.properties` hashes
- TA-Lib built via `./gradlew :computation:cloneTalibNative` + `buildTalibNative` in every Java job
- Concurrency groups prevent parallel runs on the same branch

## Consequences

- All PRs validated across all four language stacks before merge
- TA-Lib native build adds ~2-3 minutes to Java jobs (cached after first run)
- Staging and production deploy steps are placeholder until Track 11 provides Docker infrastructure
- Integration tests require TimescaleDB service container (Docker on GitHub Actions runners)
