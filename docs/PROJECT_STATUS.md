# Tickonomics — Implementation & Roadmap Status (2026-09-03)

Point-in-time review of what is implemented, what is in flight, and what remains.
Sources: code inventory (branch `develop`, commit `559dc7bb` + working tree), `docs/plan_v6-verification-report.md`,
ADR-022/033/035/036/037/038/039, `docs/reviews/00-progress.md`.

**Status:** `0.0.1-SNAPSHOT` — foundation/research phase. The 6-ETF hardcoded universe
(`SPY,QQQ,IWM,TLT,HYG,GLD` + SPY benchmark) is explicit scaffolding. The committed goal
([ADR-022](adr/ADR-022-foundation-realignment.md), Accepted) is a broad cross-sectional stock universe
with survivorship-bias-free point-in-time data, validated by live/paper trading.

---

## 1. Implemented (verified in code)

### Computation engine (Java) — ~90% per plan v6 verification

- **Strategies:** ~25 equity strategies + 7 options strategies, enum-driven
  (`EquityStrategyType` stable IDs → `BaseEquityStrategy`/`BaseOptionStrategy` → `EquityStrategyRegistry`),
  with the IR-Score trust gate in `BaseEquityStrategy.compute` (neutral signal when `irScore < 0.9`).
- **ILI:** `AdaptiveIliCalculator`, `AmbiguityAdjustedIli`, `ClimateSensitivityFactor`, `WeightedWeightStore`.
- **Backtesting:** `BacktestEngine` + 15 supporting classes — `WalkForwardValidator`, `CrossModelValidator`,
  `WeightOptimizer`, `Eq553SlippageModel`, `MarketStressSimulator`, `ReproducibilityService`,
  `HistoricalDataReplay`, barrier-hitting analysis.
- **Scope:** ~30 packages (anomaly, audit, demo, fixedincome, forecast, governance, kpi, leverage,
  liquidity, optimization, pairs, portfolio, regime, risk, scenario, sensitivity, session, signal, stress, …).

### Ingestion — 17 scheduled source clients

FRED, NY Fed, Alpha Vantage, Ken French, DataHub (backfill), Yahoo (equity + options),
Finnhub (REST + WebSocket), Polymarket, Fed RSS + Finnhub/generic news, OSINT, disaster alerts,
economic calendar. Resilience layer: `DataQualityChecker`, `ProxyDivergenceGuard`,
`ToxicityMonitor`, `OrderCancellationMonitor`, tiered ingestion buffer with file overflow,
`LastKnownGoodCache`, `IdempotencyGuard`, Resilience4j bulkheads (`critical` vs `high-volume`).
Cadence/enablement is config-driven: `monitor.<source>.poll-interval-ms` / `.enabled` in `application.yml`.

### Persistence

- 42 Flyway migrations (`V1`–`V42`; latest: `V42__add_news_events`, `V41__add_prediction_market_quotes`),
  TimescaleDB hypertables, continuous aggregates, compression/retention policies, demo/portfolio tables.
- 23 repository classes; entities are Java records; money columns migrated to numeric
  (ADR-033 work partially landed, `V36`).

### Web / API / security

- Controllers: `KpiController` (11 endpoints), `DemoController` (13 endpoints, incl. live
  kill-switch and safe-mode), `QuantController`, `PerspexController` (ADR-037), `HealthController`.
- Realtime: price + signal + broadcast WebSocket handlers, heartbeat scheduler.
- **Security is live:** OAuth2 resource server (JWT) in `config/SecurityConfig.java`,
  hand-rolled PKCE flow in `frontend/lib/auth.ts`; auth **enabled by default**
  (`auth-disabled: ${AUTH_DISABLED:false}`, `app/src/main/resources/application.yml`).
  RBAC + denyAll, WS CORS, rate limiting, security headers (P0/P2 remediation, ADR-035/036).
- Contract-first: `openapi.yaml` / `asyncapi.yaml` are the source of truth; `ApiContractsSpec` enforces.

### Analytics worker (Python FastAPI)

- 25 router domains: anomaly, backtest, climate, diagnostics, drift, econometrics, explainability,
  fixed_income, greeks, liquidity, ml, optimizer, performance, **perspex**, regime, reproducibility,
  risk, sentiment, simulation, statistical, stops, tournament (+ health).
- ML is now substantive (ADR-036 D2): CNN-LSTM regime model with a committed trained checkpoint
  (never random weights), autoencoder anomaly detection, quantile regression, EVT.
- `external_validation/`, `audit/`, `reference/` test suites.

### Perspective-mismatch signal (ADR-037, Track A) — shipped

Full stack merged via PR #64 (2026-06-25): Polymarket + news/OSINT CDM types → prediction-market and
news-event persistence stores → Polymarket/GDELT-class ingestion → deterministic divergence engine
(authoritative; LLM agents corroborative-only, IR 0.95 vs 0.70) → REST/WS contract → web pipeline +
`PerspexSignalGenerator` WS broadcast → `analytics` service (`perspex_service.py`, `_deterministic.py`).

### Frontend / landing

- Dashboard (~50 components): D3 v7 + lightweight-charts v5, KPI cards, demo panels (kill-switch/leverage),
  config editor, signals; PKCE auth + login + demo pages; vitest + Playwright e2e.
- Landing: marketing page (lightweight-charts v5).

### Infra / CI / docs

- Terraform: 4 environments (`aws-forecast`, `aws-production`, `azure`, `gcp`), applied via
  Atlantis; Azure refactored into 7 modules (ADR-036); GCP modularization is a known follow-up.
- 12 CI workflows: pr-checks, integration-tests, coverage-report, codeql, chaos-tests, chaos-benchmark,
  benchmark, demo-report, deploy-staging, deploy-production, deploy-landing, forecast-deploy.
  Local workflow verification via `act` + `actionlint` (ADR-026).
- 41 ADRs; 20 runbooks (plan v6 Track 11: all plan-aligned); Spring Boot 4.1.0.

### Remediation progress (per `docs/reviews/00-progress.md`, 2026-06-24)

| Phase | Items | Fixed | % |
|---|---|---|---|
| P0 (silent correctness & security) | 17 | 16 | 94% |
| P1 (data integrity, money precision, observability) | ~19 | 17 | 100% |
| P2 (robustness, validation, API typing) | 22 | 21 | 95% |
| P3 / infra / analytics sweep | ~15 | 7 | 47% |
| **Total** | **~74** | **62** | **~84%** |

Most v5-era gaps (`docs/plan_v5/LEFT_AFTER_FIRST_ITERATION.md`) have since been closed: security/OAuth2,
17 runbooks, D3.js, Playwright e2e, the named "missing" computation components (WalkForwardValidator,
CrossModelValidator, MarketSensitivityLibrary, …). Still valid from that era: KPI endpoint stubs and
thin integration-test coverage (below).

---

## 2. In flight

| Workstream | State |
|---|---|
| **ADR-039 — AWS forecast/production split** | *Proposed, landed in unpushed commit `22d440a`.* `aws-forecast` (spot) split from `aws-production` (always-on, consumes forecast state via `terraform_remote_state`), including the Graviton `t3→t4g` migration (`aws-production/main.tf`, `modules/hosting/variables.tf`), multi-arch `linux/arm64` image builds + dashboard image push (`forecast-deploy.yml`), and the two-project Atlantis ordering. Open: `prod`-sizing is workspace-keyed but no automation creates the `prod` workspace (needs explicit `var.environment` + ADR decision). |
| **ADR-038 — Terraform state backend bootstrap** | *Proposed.* Version-controlled bootstrap stack for S3+DynamoDB state (AWS-only; GCP/Azure open). Unpushed commit adds `infra/terraform/bootstrap/` (104-line `main.tf` + README). |
| **WIP stash** | `stash@{0}` on develop: legacy `aws/` env deletion, backup/storage/orchestrator module edits, pr-checks +47, atlantis +30, `TalibNativeLoader` + spec, runbook +274, S3 SecureTransport + EBS encryption. |
| **Housekeeping** | 10 unmerged dependabot branches (spotbugs 6.5.8, tfsec 1.0.3, gradle-wrapper 9.6.0, numpy/scipy/pytest, setup-node/python, fastapi, codeql-action, gradle/actions); `feature/commercial-contribution` stalled since 2026-06-17 (docs-only: COMMERCIAL.md, CONTRIBUTING.md, commercialization plans). |

**Note:** the ADR-038/039 infra work landed in unpushed commit `22d440a` (2026-10-18); next resumption
points are the workspace-sizing decision above, the stash's TA-Lib arm64 items, and the GCP/Azure
bootstrap equivalents.

---

## 3. Remaining work (committed roadmap)

### ADR-022 foundation (must precede trusting any cross-sectional backtest result)

1. **Point-in-time data provider selection** — one paid PIT source (Norgate/Sharadar/Compustat; D5 amends ADR-012; provider unchosen).
2. **Universe rule + benchmark pinning** — top-N by market cap + liquidity floor, config-driven (replace the 6-ETF scaffold); benchmark CRSP/Russell-3000 or equal-weight.
3. **ONNX inference bridge** — trained models in Python cross back to Java for inference (ADR-022 D4); no ONNX/XGBoost present in `analytics/` yet.
4. **PIT universe management** — survivorship-bias-free membership history.
5. **Backtesting depth** — plan v6 rates it ~45%: core engine done, regulatory/behavioral testing (MiFID II/OTR/circuit breaker) absent.

### Explicitly open items

- **10 stubbed REST endpoints** (501 `notImplemented`): 6 in `web/.../QuantController.java`
  (`/quant/signals/active`, `/quant/strategies/active`, butterfly, `/quant/risk/tail-parameters`,
  `/quant/risk/evt-tail`, `/quant/macro/shock-response`) and 4 in `web/.../KpiController.java`
  (`/kpi/correlation-matrix`, `/kpi/evt-risk`, `/kpi/efficiency-gap`, `/kpi/monetary-policy`).
  Services/worker endpoints for several exist — wiring is the gap (F-E1 was the top unfixed P0 finding).
- **Integration tests** — `integration-tests/` holds only `ApplicationStartupIT` (startup smoke test);
  no end-to-end ingestion→computation→web flows.
- **ADR-033 money-as-BigDecimal** — *Proposed*, cross-cutting, stays open for P3.
- **P3 sweep** — 7 items still open (infra/analytics).
- **ADR-037 Track B** — LLM corroboration layer + frontend panel, deferred pending ADR-022/036 foundations.
- **Dashboard route wiring** — plan v6 flagged most components built but not all mounted on the live route.
- **GCP Terraform modularization** + GCP/Azure state-backend bootstrap (ADR-038 follow-ups).

---

## 4. Suggested next steps (in dependency order)

1. **Land ADR-038/039 infra work** — the in-flight branch of work; commit or consciously shelve the
   working tree + stash; merge the dependabot backlog.
2. **Close the 10 endpoint stubs** — highest-value functional gap; services largely exist.
3. **Write the integration-test flows** (Testcontainers/TimescaleDB are already wired).
4. **Start ADR-022 foundation** — PIT provider decision is the blocking external choice.
