# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Tickonomics is a quantitative-research platform: it ingests market/rates data, computes alpha signals
and a composite Liquidity Index, backtests strategies, and exposes results through a Spring API and a
Next.js dashboard.

The *stated* product framing (real-time funding-market intelligence) and the current 6-ETF hardcoded
universe (`SPY,QQQ,IWM,TLT,HYG,GLD` + SPY benchmark) are **placeholders**. The real goal, recorded in
[ADR-022](docs/adr/ADR-022-foundation-realignment.md), is a broad cross-sectional stock universe with
survivorship-bias-free point-in-time data, validated by live/paper trading. Treat the 6-ETF set and SPY
benchmark as scaffolding to be replaced; make universe/benchmark choices config-driven, not hardcoded.

Every architectural decision is captured as an ADR in `docs/adr/` (ADR-001 through ADR-026). Read the
relevant ADR before changing a load-bearing choice, and add a new ADR for any change of that magnitude.

## Build, test, lint

The project is a Gradle multi-project build (Java 25 toolchain). Always use the wrapper.

```bash
./gradlew build -x :integration-tests:test   # full build + unit tests (the CI gate excludes integration-tests here)
./gradlew test                                # unit tests only
./gradlew lint                                # Checkstyle + SpotBugs — ADVISORY, never fails the build
./gradlew :integration-tests:test            # integration tests (needs Docker for Testcontainers/TimescaleDB)

# Run a single test (module:test --tests "<fully.qualified.Class>" or "<*Spec.method>")
./gradlew :computation:test --tests "com.tickonomics.computation.strategy.BaseStrategySpec"
```

**Native TA-Lib is required for the `computation` module.** Run once before the first build or after a
clean checkout of `native-libs`:

```bash
./gradlew :computation:setupTalib   # clones + builds TA-Lib C lib + JNA wrapper, installs JAR to ~/.m2
```

Without it, compilation/tests touching `TalibAdapter` will fail. CI builds it from source in the Dockerfile.

### Python analytics worker (`analytics/`)

```bash
cd analytics
pip install -r requirements.txt
python -m pytest tests -m "not benchmark"           # unit tests (CI gate)
python -m pytest tests -m benchmark                  # benchmark suite (separate; runs in benchmark.yml)
python -m pytest tests/path/test_file.py::test_name  # single test
ruff check .                                          # advisory lint (CI: continue-on-error)
```

### Frontend (`frontend/` dashboard, `landing/` marketing page — both Next.js 16 / React 19)

```bash
cd frontend   # or landing
npm ci
npm run generate:api   # frontend only: regenerates lib/api/types.ts from api-contracts openapi.yaml
npm run build | test | lint | typecheck
npm test -- src/path/to/test     # single vitest
npm run test:e2e                 # Playwright (frontend/ only)
```

`frontend/CLAUDE.md` and `landing/CLAUDE.md` delegate to `AGENTS.md` — read the local Next.js docs in
`node_modules/next/dist/docs/` before writing frontend code; this Next.js has breaking changes.

**Frontend↔backend wiring** (no `next.config.ts` rewrite/proxy — the dashboard calls the backend directly):
`NEXT_PUBLIC_API_BASE_URL` (REST, `lib/api.ts`) and `NEXT_PUBLIC_WS_BASE_URL` (WS, `lib/websocket.ts`),
both defaulting to `localhost:8080`. Auth is a hand-rolled PKCE flow against Keycloak (`lib/auth.ts`).
**Disabling auth is two-sided**: set `NEXT_PUBLIC_AUTH_DISABLED=true` on the frontend *and* `AUTH_DISABLED=true`
(=`security.auth-disabled`) on the backend — setting only one leaves the halves mismatched.

### Local GitHub-Actions verification (the current branch's focus)

Workflow files are verified locally with `act` + `actionlint` (see ADR-026 and the `Makefile`):

```bash
make verify-workflows              # full pipeline: actionlint + act -l + hook tests + gate-workflow dry-run
make workflow-lint | workflow-dryrun | workflow-run JOB=<job-id>
make install-hooks                 # installs pre-commit (actionlint) + pre-push (workflow dry-run) from scripts/git-hooks/
```

### Running the stack locally

```bash
cp .env.example .env   # fill FRED/FINNHUB/ALPHAVANTAGE keys (all free tiers)
docker compose up      # backend(:8080) analytics-worker(:8001) dashboard(:3001) landing(:3000) timescaledb jaeger(:16686)
```

Dev/prod/demo variants: `docker-compose.dev.yml`, `docker-compose.prod.yml`, `docker-compose.demo.yml`.
Health endpoints: backend `/actuator/health`, worker `/health` (see `docs/runbooks/README.md`).

### Local dev without rebuilding images

Run just the infra, then the backend and worker directly for fast iteration (point them at the
containerized DB/worker — the `SPRING_DATASOURCE_*` defaults in `application.yml` expect localhost:5432,
so override the host):

```bash
docker compose up -d timescaledb jaeger                      # infra only
./gradlew :app:bootRun                                        # backend on :8080 (needs TA-Lib built; see above)
cd analytics && uvicorn app.main:app --port 8001 --reload     # worker on :8001
cd frontend && npm run dev                                    # dashboard on :3001 (or landing on :3000)
```

## Architecture

### Gradle modules (`settings.gradle`) and dependency flow

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

`analytics/` is a standalone Python FastAPI service, not a Gradle module.

### API contract is hand-maintained (contract-first)

`api-contracts/src/main/resources/openapi.yaml` is the **source of truth** for the REST surface — it is
*not* generated from controllers (there is no springdoc/openapi-generator plugin). Its header calls it the
"Phase 0 contract" and it deliberately lists planned endpoints no controller implements yet. To change an
endpoint: edit `openapi.yaml`, keep `ApiContractsSpec` green (it asserts ≥40 paths and the required
endpoint families), then regenerate frontend types with `npm run generate:api`. The `web` controllers are
an *implementation* of this contract, not its definition. `asyncapi.yaml` is the parallel contract for the
realtime WebSocket surface.

### The Java↔Python boundary (ADR-022 D4)

**Java is the single system of record for backtesting** — one definition of "robust," one BacktestEngine
(ADR-007). Python is the research and ML-training surface. Java calls the worker via
`AnalyticsWorkerClient` (REST `sendAnalysisRequest` + Arrow IPC `sendArrowRequest`); trained models are
intended to cross back to Java for inference via ONNX (planned). Do **not** build a second backtest path
in Python.

### Data flow

`ingestion` source clients → CDM adapters (normalize provider-specific raw DTOs into `cdm` record types)
→ quality checks (`DataQualityChecker`, `ProxyDivergenceGuard`) → batched/buffered `TimescaleDbWriter`
→ TimescaleDB hypertables → continuous aggregates + compression. Resilience4j bulkheads separate
"critical" vs "high-volume" ingestion pools (see `resilience4j.bulkhead` in `application.yml`).
Realtime: Finnhub WS trades → `PriceWebSocketHandler` → dashboard; generated signals → `SignalWebSocketHandler`.
Ingestion is **scheduler-driven**, not push: each source client uses
`@Scheduled(fixedDelayString = "${monitor.<source>.poll-interval-ms:…}")`, armed by `@EnableScheduling`
on the main class. So poll cadence and enable/disable for a source live entirely in `application.yml`
under `monitor.<source>.poll-interval-ms` / `monitor.<source>.enabled` (the writer flush and guard
eviction run on the same mechanism: `writer.flush-interval-ms`, `guard.evict-interval-ms`).

### Migrations & schema

Flyway migrations live in `persistence/src/main/resources/db/migration/` (`V1`…`V35`). TimescaleDB
hypertables, continuous aggregates, compression/retention policies, and the demo/portfolio tables are all
defined there. Never edit an applied migration — add a new `V<N>` file.

### Adding an equity or options strategy

The strategy catalog is enum-driven and manually registered — three files change for each new strategy:

1. **`EquityStrategyType`** (enum): add a constant carrying a stable ID (`EA.01`) and a `formulaRef`
   (textbook page/equation the strategy implements). This ID is the strategy's persistent identity.
2. **`BaseEquityStrategy`** (abstract): extend it and implement
   `computeSignal(Map<String,Double> input, StrategyContext ctx)`. Use the inherited
   `buildSignal(...)` / `mapDirection(...)` helpers; don't override `compute`.
3. **`EquityStrategyRegistry`** (`@Component`): `register()` your instance in `registerDefaults()`.

Options strategies parallel this via `BaseOptionStrategy`. The `computation/options` package is the
options equivalent of `computation/equity`.

**Trust-gate subtlety:** `BaseEquityStrategy.compute` returns a *neutral* signal when
`ctx.irScore() < 0.9` — low intersubjective-reliability data silently produces no signal, by design
(see `IntersubjectiveAuditService` / IR Score, ADR-011). A strategy that "never fires" may be correct
behaviour, not a bug. `StrategyContext` carries `irScore` + `confidence`.

### Adding a data source

The ingestion→computation boundary is enforced by `CdmAdapter<T,R>` (`@FunctionalInterface`,
`R toCdm(T rawData)`): the computation engine consumes **only** CDM-typed objects, so every source maps
its raw shape to CDM at the edge. To add a source:

1. **Raw DTO** in `cdm/adapter/raw/` — the provider's response shape (e.g. `FredObservation`).
2. **`*CdmAdapter implements CdmAdapter<RawDto, CdmType>`** in `cdm/adapter/` — use `CdmInstrumentMapper`
   to translate source-specific IDs (FRED series, Yahoo symbol) into a CDM `CdmInstrumentRef`.
3. **Source client** in `ingestion/<source>/` (a `@Component`) — inject the adapter, fetch,
   `adapter.toCdm(...)`, then write via `TimescaleDbWriter.writeXxx(toEntity(cdm))`. Cadence and resilience
   are declarative: `@Scheduled(fixedDelayString = "${monitor.<source>.poll-interval-ms:…}")` on the poll
   method, plus `@Bulkhead(name="…")` / `@Retry(name="…")` whose names resolve into the
   `resilience4j.{bulkhead,retry}.instances` map in `application.yml` (e.g. `fredApi`, `criticalIngestion`).
   Per-source config is read with `@Value("${monitor.<source>.*}")`.
4. **Config** — add the `monitor.<source>.*` block to `application.yml` (some sources add an `enabled`
   flag the client checks; fred/nyfed rely on poll-interval alone).

## Conventions that matter

- **Two test styles coexist.** Spock/Groovy specs (`*Spec.groovy`, ~67 files) and JUnit 5 tests
  (`*Test.java`, ~94 files). Both run under JUnit Platform. All tests use **Given-When-Then** structure.
  `web` integration tests use Spock-Spring.
- **Static analysis is advisory.** Checkstyle and SpotBugs run with `ignoreFailures = true`; ruff and
  Lighthouse run with `continue-on-error`. They surface reports, never fail builds. The `./gradlew lint`
  task aggregates Checkstyle + SpotBugs across modules.
- **Records for DTOs/entities.** Domain types in `cdm` and persistence entities are Java `record`s.
- **Gradle configuration-cache is on** (`org.gradle.configuration-cache=true`). Custom tasks in
  `build.gradle` must not touch `project`/`rootProject` at execution time — capture values at
  configuration time. (Workflow verification no longer lives in `build.gradle`; it runs through
  git hooks + the `Makefile` — see ADR-026.)
- **Config knobs** are centralized in `app/src/main/resources/application.yml`, namespaced under
  `monitor.<source>.*` (enable flags, poll intervals, symbols) and `resilience4j.*` (retry/bulkhead per
  client). Demo/paper-portfolio behavior is under `monitor.demo.*`. Override via env vars (`*_API_KEY`,
  `AUTH_DISABLED`, `CORS_ALLOWED_ORIGINS`, `KEYCLOAK_ISSUER_URI`).
- **Source language:** Latin characters and English only (no Cyrillic in identifiers/comments), per the
  global rules. Prefer self-documenting names over comments.

## Operations & infra

- **Terraform** (`infra/terraform/`, multi-cloud AWS/GCP/Azure) is applied via **Atlantis**
  (`atlantis.yaml`). Terraform PRs use the `.github/PULL_REQUEST_TEMPLATE.md` checklist; run
  `terraform fmt -check -recursive`, `terraform validate`, and `tflint` (`.tflint.hcl`) before merging.
- **Observability:** Micrometer + OTLP tracing to Jaeger; Prometheus metrics at `/actuator/prometheus`.
- **Runbooks** for incidents, data-source outages, the kill-switch / global safe-mode, and data management
  are in `docs/runbooks/`. Per-module verification reports are in `docs/reviews/`.
