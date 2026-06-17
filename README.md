# Tickonomics

[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-multi--project-02303A.svg)](build.gradle)
[![Python](https://img.shields.io/badge/Python-FastAPI-3776AB.svg)](analytics/requirements.txt)
[![Frontend](https://img.shields.io/badge/Next.js-16.2.6-000000.svg)](frontend/package.json)
[![Version](https://img.shields.io/badge/version-0.0.1--SNAPSHOT-lightgrey.svg)](build.gradle)

Tickonomics is a quantitative-research platform: it ingests market and rates data, computes alpha
signals and a composite **Liquidity Index**, backtests strategies, and exposes results through a
Spring REST/WebSocket API and a Next.js dashboard. The Java backend is the single system of record
for backtesting; a standalone Python FastAPI worker handles research and model training.

> **Status:** `0.0.1-SNAPSHOT` — foundation/research phase. The current 6-ETF hardcoded universe
> (`SPY,QQQ,IWM,TLT,HYG,GLD` + SPY benchmark) is scaffolding to be replaced by a broad,
> survivorship-bias-free, point-in-time cross-sectional stock universe. See
> [ADR-022](docs/adr/ADR-022-foundation-realignment.md).

---

## Highlights

- **Multi-project Gradle build** (Java 25 toolchain, Spring Boot 4.0.0) split into focused modules
  with a strict dependency flow.
- **Native quant engine** (`computation`): 50+ strategies, `TalibAdapter` (JNA bridge to the TA-Lib C
  library), `BacktestEngine` (updated), Intersubjective Liquidity Index (ILI), and signal generation.
- **Robust Ingestion**: Refactored clients with improved error handling, time management
  (`EventBasedTimeConverter`), and extended test coverage.
- **Contract-first API**: `openapi.yaml` is the source of truth for the REST surface.
- **Realtime broadcast**: WebSocket support (Finnhub trades + generated signals → dashboard).
- **Security**: Updated security configurations with refined access control and tested controllers.
- **Documentation**: Includes Java best practice reviews and ADR-034 licensing strategy.
- **Automated Infrastructure**: `graphify` knowledge graph updates configured via Git hooks (`post-checkout`, `post-merge`) for seamless synchronization. The installation process (`make install-hooks`) will automatically check for and install `graphify` if it is missing.
- **Local GitHub-Actions verification** with `act` + `actionlint`, enforced by git hooks
  (see [ADR-026](docs/adr/ADR-026-local-github-actions-verification.md)).

---

## Architecture

### Gradle modules and dependency flow

```
cdm ────────────────── base domain model + source adapters (no dependencies)
 ├── api-contracts     OpenAPI/AsyncAPI YAML + Java client interfaces (AnalyticsWorkerClient, EquityWsClient)
 ├── persistence       NamedParameterJdbcTemplate repos + Flyway migrations + record entities
 ├── ingestion         free data-source clients (FRED, NY Fed, Finnhub WS, Yahoo, Alpha Vantage, Ken French, DataHub, Fed RSS) → CDM → TimescaleDB
 ├── computation       quant engine: 50+ strategies, TalibAdapter, BacktestEngine, ILI, signal generation (native TA-Lib)
 └── web               REST controllers + WebSocket realtime broadcast + OAuth2/Keycloak security
       └── app             Spring Boot bootJar (com.tickonomics.TickonomicsApplication) — the deployable; aggregates web + ingestion
             └── integration-tests   Testcontainers/TimescaleDB end-to-end
```

`analytics/` is a **standalone Python FastAPI service** (`uvicorn app.main:app`), not a Gradle module.
Java calls it via `AnalyticsWorkerClient` (REST + Arrow IPC); trained models are intended to cross
back to Java for inference via ONNX. Java remains the single system of record for backtesting — there
is no second backtest path in Python (see [ADR-007](docs/adr/ADR-007-backtesting-framework.md),
[ADR-022](docs/adr/ADR-022-foundation-realignment.md)).

### Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.0.0, Spring Security (OAuth2/Keycloak) |
| Quant engine | TA-Lib (native C via JNA), BacktestEngine, ILI |
| Persistence | TimescaleDB (PostgreSQL 16), Flyway (`V1`…`V35`) |
| Analytics worker | FastAPI 0.136, PyTorch 2.6, NumPy, SciPy, statsmodels, pyarrow |
| Frontend | Next.js 16.2.6, React 19.2, TanStack Query, FinOS Perspective, lightweight-charts, Tailwind |
| Observability | Micrometer, OpenTelemetry → Jaeger, Prometheus |
| Infra | Terraform (multi-cloud AWS/GCP/Azure) via Atlantis |

---

## Prerequisites

- **JDK 25** (the Gradle toolchain pins Java 25)
- **Docker** + Docker Compose (for TimescaleDB, Jaeger, and the full stack)
- **Node.js 20+** (frontend / landing)
- **Python 3.10+** (analytics worker)
- **Native TA-Lib** — required by the `computation` module (see below)

---

## Quick start (full stack via Docker)

```bash
cp .env.example .env        # fill FRED / FINNHUB / ALPHAVANTAGE keys (all free tiers)
docker compose up            # backend :8080 · analytics-worker :8001 · dashboard :3001 · landing :3000 · timescaledb · jaeger :16686
```

Dev / prod / demo variants: `docker-compose.dev.yml`, `docker-compose.prod.yml`,
`docker-compose.demo.yml`.

| Endpoint | URL |
| --- | --- |
| Backend health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Analytics worker health | http://localhost:8001/health |
| Jaeger UI | http://localhost:16686 |
| Dashboard | http://localhost:3001 |
| Landing | http://localhost:3000 |

---

## Native TA-Lib (one-time setup)

The `computation` module requires the TA-Lib C library and JNA wrapper. CI builds them from source
in the `Dockerfile`; locally run once before the first build or after a clean checkout of
`native-libs`:

```bash
./gradlew :computation:setupTalib   # clones + builds TA-Lib C lib + JNA wrapper, installs JAR to ~/.m2
```

Without it, compilation/tests touching `TalibAdapter` will fail.

---

## Build, test, lint

```bash
./gradlew build -x :integration-tests:test   # full build + unit tests (the CI gate excludes integration-tests here)
./gradlew test                                # unit tests only
./gradlew lint                                # Checkstyle + SpotBugs — ADVISORY, never fails the build
./gradlew :integration-tests:test             # integration tests (needs Docker for Testcontainers/TimescaleDB)

# Run a single test
./gradlew :computation:test --tests "com.tickonomics.computation.strategy.BaseStrategySpec"
```

Two test styles coexist: Spock/Groovy specs (`*Spec.groovy`) and JUnit 5 tests (`*Test.java`), both
under JUnit Platform, all written in Given-When-Then structure. Static analysis (Checkstyle, SpotBugs)
runs with `ignoreFailures = true` — it surfaces reports but never fails a build.

### Analytics worker (`analytics/`)

```bash
cd analytics
pip install -r requirements.txt
python -m pytest tests -m "not benchmark"            # unit tests (CI gate)
python -m pytest tests -m benchmark                  # benchmark suite (separate)
ruff check .                                          # advisory lint (CI: continue-on-error)
```

### Frontend (`frontend/` dashboard, `landing/` marketing page)

```bash
cd frontend          # or landing
npm ci
npm run generate:api   # frontend only: regenerates lib/api/types.ts from openapi.yaml
npm run build | test | lint | typecheck
npm test -- src/path/to/test     # single vitest
npm run test:e2e                 # Playwright (frontend/ only)
```

Frontend↔backend wiring is direct (no Next.js rewrite/proxy): `NEXT_PUBLIC_API_BASE_URL` (REST) and
`NEXT_PUBLIC_WS_BASE_URL` (WS), both defaulting to `localhost:8080`. Auth is a hand-rolled PKCE flow
against Keycloak. **Disabling auth is two-sided**: `NEXT_PUBLIC_AUTH_DISABLED=true` on the frontend
*and* `AUTH_DISABLED=true` on the backend.

---

## Local dev without rebuilding images

Run just the infra, then the backend and worker directly for fast iteration:

```bash
docker compose up -d timescaledb jaeger                  # infra only
./gradlew :app:bootRun                                     # backend on :8080 (needs TA-Lib built)
cd analytics && uvicorn app.main:app --port 8001 --reload # worker on :8001
cd frontend && npm run dev                                 # dashboard on :3001 (or landing on :3000)
```

> The `SPRING_DATASOURCE_*` defaults in `application.yml` expect `localhost:5432`. When pointing the
> backend at a containerized DB, override the host accordingly.

---

## Configuration

Config is centralized in [`app/src/main/resources/application.yml`](app/src/main/resources/application.yml),
namespaced under `monitor.<source>.*` (enable flags, poll intervals, symbols) and `resilience4j.*`
(retry/bulkhead per client). Ingestion is **scheduler-driven** (`@Scheduled` + `@EnableScheduling`),
so each source's cadence and enable/disable lives in `application.yml`:

```yaml
monitor.<source>.poll-interval-ms: 60000
monitor.<source>.enabled: true
```

Override via environment variables: `FRED_API_KEY`, `FINNHUB_API_KEY`, `ALPHAVANTAGE_API_KEY`,
`AUTH_DISABLED`, `CORS_ALLOWED_ORIGINS`, `KEYCLOAK_ISSUER_URI`. See [`.env.example`](.env.example).

### Data sources

| Source | Type | Notes |
| --- | --- | --- |
| FRED | REST | US Treasury / Fed rates |
| NY Fed | REST | Rates (e.g. SOFR, EFFR) |
| Finnhub | WebSocket | Realtime equity trades |
| Yahoo | REST | Equity price history |
| Alpha Vantage | REST | Equity / rates |
| Ken French | Files | Factor returns (data library) |
| DataHub | Files | Datasets |
| Fed RSS | RSS | Releases |

---

## Local GitHub-Actions verification

Workflow files are verified locally with `act` + `actionlint`
([ADR-026](docs/adr/ADR-026-local-github-actions-verification.md)):

```bash
make verify-workflows              # full pipeline: actionlint + act -l + hook tests + gate-workflow dry-run
make workflow-lint | workflow-dryrun | workflow-run JOB=<job-id>
make install-hooks                 # bootstrap: install hooks before the first build (also auto-installs on ./gradlew build, see ADR-028)
```

---

## Project documentation

- **ADRs** — every architectural decision is recorded in [`docs/adr/`](docs/adr/)
  (ADR-001 … ADR-026). Read the relevant ADR before changing a load-bearing choice, and add a new ADR
  for any change of that magnitude.
- **Runbooks** — incidents, data-source outages, the kill-switch / global safe-mode, and data
  management: [`docs/runbooks/`](docs/runbooks/).
- **Module reviews** — per-module verification reports in [`docs/reviews/`](docs/reviews/).

### API contract (contract-first)

[`api-contracts/src/main/resources/openapi.yaml`](api-contracts/src/main/resources/openapi.yaml) is
the **source of truth** for the REST surface — it is *not* generated from controllers (there is no
springdoc/openapi-generator plugin). Its header calls it the "Phase 0 contract" and deliberately lists
planned endpoints no controller implements yet. `asyncapi.yaml` is the parallel contract for the
realtime WebSocket surface. The `web` controllers are an *implementation* of this contract, not its
definition.

---

## Operations & infra

- **Terraform** (`infra/terraform/`, multi-cloud AWS/GCP/Azure) is applied via **Atlantis**
  (`atlantis.yaml`). Run `terraform fmt -check -recursive`, `terraform validate`, and `tflint`
  (`.tflint.hcl`) before merging.
- **Observability:** Micrometer + OTLP tracing to Jaeger; Prometheus metrics at
  `/actuator/prometheus`.

---

## Contributing

1. Before changing load-bearing architecture, read the relevant ADR in `docs/adr/` and add a new ADR
   if the change is of that magnitude.
2. Keep `openapi.yaml` as the REST source of truth and regenerate frontend types with
   `npm run generate:api` after contract changes.
3. Never edit an applied Flyway migration — add a new `V<N>` file in
   `persistence/src/main/resources/db/migration/`.
4. Write tests in Given-When-Then structure; prefer records for DTOs/entities.
5. Use Latin characters and English only in source identifiers and comments.
6. Keep code self-documenting — descriptive names over comments.

---

## License

Copyright © Tickonomics contributors. Distributed under the
[GNU Affero General Public License v3.0](LICENSE).
