# Plan v6 — Implementation Verification Report

**Date:** 2026-06-13
**Verified against:** `docs/plan_v6/` (15 documents, ~16,400 lines)
**Branch:** `feature/dataset-sources` (HEAD `834546b`)
**Method:** Each of the 14 plan tracks was verified against the actual codebase (Java/Python/TS/Terraform) via direct file enumeration, `grep`, and source reading. Evidence pointers are included per finding. Confidence is HIGH unless noted.

---

## 1. Executive Summary

The repository reflects a **substantially implemented platform** whose core analytical pipeline is real and working. The strongest layers are the **database schema (V1–V34)**, the **Python analytics worker (61 sources, 33 tests)**, and the **Java computation engine (162 main classes + 57 test classes)**. The biggest gaps are in **production infrastructure (Track 14 ≈ 0% implemented)**, **the demo dashboard UI**, and a set of **"island" components that exist but are not wired** into their consuming pipeline.

### Per-track scorecard

| Track | Document | Status | Est. completeness | Confidence |
|:------|:---------|:-------|:------------------|:-----------|
| 1 | Scaffolding & API Contracts | ⚠️ Partial | ~70% | HIGH |
| 2 | Database Schema | ✅ Implemented | ~95% | HIGH |
| 3 | Analytics Worker (Python) | ✅ Implemented (with substitutions) | ~85% | HIGH |
| 4 | Ingestion Layer | ⚠️ Partial (thin tests) | ~85% class coverage | HIGH |
| 5 | Computation Engine | ✅ Mostly implemented | ~90% | HIGH |
| 6 | Landing Page | ✅ Implemented | ~95% | HIGH |
| 7 | Analytics Dashboard | ⚠️ Components built, page not wired | ~80% | HIGH |
| 8 | Backtesting Framework | ⚠️ Core only | ~45% | HIGH |
| 9 | CI/CD Pipeline | ⚠️ Partial (placeholders) | ~55% | HIGH |
| 10 | Demo / Virtual Portfolio | ✅ Finalized (ADR-017) | ~90% | HIGH |
| 11 | Deployment & Operations | ⚠️ Partial | ~60% | HIGH |
| 12 | Architecture Diagrams | ✅ Implemented (Mermaid only) | ~90% | HIGH |
| 13 | Terraform Spot/Forecast | ⚠️ IaC present, runtime broken | ~50% | HIGH |
| 14 | Production Infrastructure | ❌ Not implemented | ~0% | HIGH |

### Aggregate counts observed in the repo

| Metric | Count |
|:-------|------:|
| Gradle modules (`settings.gradle`) | 9 |
| Java files (ingestion) | 45 (41 main / 4 test) |
| Java files (computation) | 219 (162 main / 57 test) |
| Option strategies | 30 (`StrategyType`) |
| Equity strategies | 25 (`EquityStrategyType`) |
| Fixed-income strategies | 3 |
| Python analytics sources | 61 |
| Python analytics tests | 33 |
| FastAPI routers | 27 |
| Flyway migrations | 34 (V1–V34, contiguous) |
| Continuous aggregates | 11 |
| Frontend dashboard components | 47 |
| Landing components | 15 |
| GitHub Actions workflows | 8 |
| Runbooks | 20 (17 plan-aligned + 3 stale/extra) |
| Terraform modules | 6 (Track 13 only; Track 14 = 0) |

---

## 2. Track-by-Track Findings

### Track 1 — Scaffolding & API Contracts ⚠️ ~70%

**Plan intent:** Gradle multi-module build, OpenAPI/AsyncAPI contracts, FINOS CDM subset + per-source adapters, shared transport (`TalibAdapter`, `ArrowIpcTransport`), typed `@ConfigurationProperties`, generated TS clients.

| Item | Status | Evidence |
|:-----|:-------|:---------|
| 9-module Gradle build, Java 25, Spring Boot 3.5 | ✅ IMPLEMENTED | `build.gradle`, `settings.gradle` |
| FINOS CDM module (enums + models + mapper) | ✅ IMPLEMENTED | `cdm/src/.../cdm/{enums,model,mapper}` |
| 13 per-source CDM adapters | ⚠️ PARTIAL — present but in the `cdm` module (not ingestion); `WsTradeCdmAdapter` vs plan's `WsTickCdmAdapter`; **legacy `PolygonTickCdmAdapter` + `PolygonTick.java` remain** | `cdm/src/.../cdm/adapter/` |
| `CdmAdapter<T,R>` interface | ⚠️ PARTIAL — generic `toCdm(T)`, not the plan's `CdmAdapter<T>` / `toCdmRate` signature | `CdmAdapter.java` |
| `TalibAdapter` (7 methods + RSI) + `ArrowIpcTransport` | ✅ IMPLEMENTED | `computation/.../talib/TalibAdapter.java`, `transport/ArrowIpcTransport.java` |
| Spring Boot entrypoint, virtual threads, actuator, OTLP | ✅ IMPLEMENTED | `TickonomicsApplication.java`, `application.yml` |
| OpenAPI 3.1 spec | ⚠️ PARTIAL — covers only 7 v5.2 quant endpoints; ~30+ planned endpoints absent | `api-contracts/src/main/resources/openapi.yaml` |
| AsyncAPI spec (`/ws/prices`, `/ws/signals`) | ❌ MISSING — no asyncapi file; no WS handlers in `web/` | `find . -name "asyncapi*"` (empty) |
| TypeScript client generation | ❌ MISSING | no `openapi-typescript` config |
| `@ConfigurationProperties` typed binding | ⚠️ PARTIAL — 4 classes vs many `monitor.*` YAML namespaces left un-typed | `grep @ConfigurationProperties` |
| `integration-tests/` | ⚠️ PARTIAL — 2 smoke-test classes only (acknowledged gap in plan) | `integration-tests/src/test/` |
| Architecture diagrams (Track 12) | ✅ 13 inline Mermaid diagrams; ❌ no rendered `.svg/.png` artifacts | `12-architecture-diagrams.md` |

**REST endpoints actually present (3 controllers, ~15 paths):** `QuantController` (7 quant endpoints), `DemoController` (5 demo endpoints), `HealthController`. **Missing controllers:** `/api/v1/kpi/*`, `/api/v1/signals`, `/api/v1/config`, `/api/v1/anomaly`, `/api/v1/regime`, `/api/v1/disaster`, `/api/v1/stress`, `/api/v1/optimization`, `/api/v1/sentiment`, `/api/v1/leverage`, `/api/v1/pairs`.

---

### Track 2 — Database Schema ✅ ~95%

**Plan intent:** TimescaleDB as single DB; full Flyway migration set; continuous aggregates (`materialized_only=false`); CDM-aligned enums; `order_flow_imbalance`, `evt_risk_metrics`, `strategy_definitions`, `alpha_signals`.

- **Core tables (V1–V18):** ✅ all present, including Finding-3/6 columns (`active_weights`, `proxy_divergence_status`), CDM CHECK constraints, compression + retention policies, demo tables.
- **Continuous aggregates (11):** ✅ all present — `ohlcv_1min/1h/1d`, `daily_kpi_summary`, `kpi_rolling_correlation/beta`, `kpi_zscore_daily`, `daily_event_summary`, `ili_robustness_heatmap`, `daily_comovement_factor`, `daily_toxicity_summary`.
- **Quant engine (V19–V25):** ✅ present, **but re-versioned** from the plan's single "V18 mega-migration" into V18/V19/V20/V21/V22/V23/V24.
- **Post-plan additions (V26–V34):** strategy seeds, demo indexes, `evt_risk_metrics` view, `factor_returns`, yield-curve enums, `index_snapshots`, `data_import_tracker`, `volatility_forecasts`, `model_artifacts`.

| Item | Status | Note |
|:-----|:-------|:-----|
| `strategy_definitions` + `alpha_signals` | ✅ IMPLEMENTED | V19, richer schema than planned |
| `evt_risk_metrics` | ⚠️ VIEW not hypertable | V22 creates `risk_evt_parameters` hypertable; V28 adds `CREATE VIEW evt_risk_metrics` with planned column shape |
| `order_flow_imbalance` | ⚠️ COLUMN not table | `DOUBLE PRECISION` on `tick_data` (V14) + partial index (V16); no dedicated hypertable |
| `factor_returns` / `index_snapshots` / `data_import_tracker` | ⚠️ re-versioned | V29/V31, not plan's V19; `data_import_tracker` column set diverges |

**Discrepancies:** the plan's migration file listing (stops at V19) is **stale** and omits V17–V34. The recent "re-number DB migrations" commit (`834546b`) was narrow — it only renamed two V30 files to V33/V34 and fixed invalid `compress_after` syntax in V29/V31; it did **not** reshuffle V1–V25.

---

### Track 3 — Analytics Worker (Python) ✅ ~85% (with substitutions)

**Plan intent:** FastAPI statistical sidecar with AIC lag selection, EVT/GPD, BH-FDR, Nelson-Siegel, TVP-SVAR, quantile regression, transfer entropy, LSTM/GARCH regime, FinBERT/SALI sentiment, Arrow IPC.

**Strong (genuine implementations):** econometrics (ADF + AIC lag sweep, Granger AIC loop, OLS), risk (VaR/CVaR/GARCH hand-rolled via scipy MLE), statistical v5.2 (EVT/GPD, BH-FDR, QR, transfer entropy, Nelson-Siegel, TVP-SVAR), autoencoder anomaly (real PyTorch), BSM Greeks/GEX, PCA comovement, Amihud, Sobol, Markov stops, climate, drift, fixed-income/Q-world, RDS scorer.

**Substituted / weaker than plan:**

| Component | Plan | Actual |
|:----------|:-----|:-------|
| Transport | Arrow IPC (headline) | ❌ REST-only — `transport/arrow_ipc.py` is a 4-line stub |
| GARCH | `arch>=7.0.0` | Hand-rolled scipy MLE (no `arch` dep) |
| LSTM volatility | BiLSTM model | GARCH-only; `model="lstm"` param accepted but ignored |
| CNN-LSTM regime | Trained model | Architecture exists but runs **untrained** (`model.eval()` on random init) |
| FinBERT / SALI | `transformers` + lexicon | ❌ Hand-rolled word-list lexicon; no transformers/TextBlob |
| XGBoost tournament | `xgboost` models | ❌ numpy momentum + `np.random` noise proxy |
| SHAP | `shap` | ❌ Permutation-based "SHAP-style" attribution |
| OpenBB / FinanceToolkit | SDK dependencies | ❌ Absent; everything reimplemented in numpy/scipy/statsmodels |

**Counts:** 61 sources / 33 tests / 27 routers / 29 services. Only `torch` is actually imported among ML libs; `requirements.txt` + `pyproject.toml` list none of `transformers/arch/xgboost/shap/textblob`.

---

### Track 4 — Ingestion Layer ⚠️ ~85% (class coverage); tests severely thin

**Plan intent:** Free-tier data source clients replacing all paid (Polygon) sources, CDM adapters at ingestion boundary, `TimescaleDbWriter`, idempotency, overflow buffer, ingestion guards, filters.

**All data-source clients present and free-tier:** `FredClient`, `NyFedClient`, `YahooFinanceClient` (primary), `FinnhubEquityClient` (fallback), `FinnhubWsClient`, `YahooOptionsClient`, `FinnhubNewsClient`, `FedRSSClient`, `FrenchFactorClient`, `AlphaVantageClient`, `DataHubBackfillClient`, `EconomicCalendarClient`, `DisasterAlertClient`.

**All ingestion guards/filters/writers present:** `TimescaleDbWriter`, `FileOverflowBuffer` (+ tiered/in-memory buffers), `LastKnownGoodCache`, `DataQualityChecker`, `ProxyDivergenceGuard`, `ToxicityMonitor`, `OrderCancellationMonitor`, `DeRoundingFilter`, `TimePeriodicityFilter`, `AlgorithmicSanityGuard`, `EventBasedTimeConverter`, `KernelAggregator`, `IdempotencyGuard`.

| Item | Status | Note |
|:-----|:-------|:-----|
| CDM adapters | ✅ all 13 — but consolidated in `cdm` module (cleaner than plan) | `cdm/src/.../cdm/adapter/` |
| `IdempotencyGuard` | ⚠️ In-memory dedup cache, not SQL `INSERT ... ON CONFLICT` | `writer/IdempotencyGuard.java` |
| LKG `X-Data-Age: STALE` header | ⚠️ Not found in cache | `cache/` |
| `AnomalyDetectionWorker` (Java) | ❌ MISSING — runs in Python worker only | intentional per plan appendix |
| `IngestionBulkheadConfig` | ❌ MISSING | no `bulkhead/` package in ingestion |
| `IngestionTracingConfig` | ❌ MISSING | no `tracing/` package in ingestion |
| Legacy Polygon artifacts | ❌ Still present | `PolygonTickCdmAdapter.java`, `raw/PolygonTick.java` |

**Test coverage:** **Correction (post-implementation, 2026-06-13):** the original "4 test files for 41 classes" finding undercounted coverage — it counted only `.java` tests and missed the module's ~25 **Groovy Spock specifications** (`src/test/groovy/`), which already covered the source clients, buffer, cache, quality monitors, filters, kernel, and writer via Spock mocks. The four items above (`IdempotencyGuard` DB-level dedup, LKG `X-Data-Age`, `IngestionBulkheadConfig`, `IngestionTracingConfig`, legacy Polygon residue, and the async anomaly path) were the real gaps and are now closed — see [ADR-013](adr/ADR-013-ingestion-resilience-finalization.md). The full ingestion + persistence suites are green.

---

### Track 5 — Computation Engine ✅ ~90%

**Plan intent:** ILI, correlation/causality, regime detection, signal generation, KPIs, plus the v4/v5/v5.1 component set (regime/ILI, optimization, liquidity, execution, sentiment, ML validation) and the v5.2 quant engine.

**Implemented across all phases** — nearly every named component exists: regime/ILI (`SessionRangeService`, `AmbiguityAdjustedIli`, `SurpriseIndicator`, `ParticipationGovernanceService`, `LiquidityStressTestModule`); optimization (`RegimeAwareWeightingService`, `ScheduledCalibrationTask`, `FireflyWeightOptimizer`, `BayesianWeightOptimizer`); liquidity layer (all 10: `AlgorithmicIntensityMetric` … `LiquidityPremiumFactor`); core analytics (`PriceImpactKpi`, `InformationEfficiencyAnalyzer`, `ComparativeExecutionAnalysis`, `LeverageSignaler`, `PairsTradingEngine`, `PortfolioManagementAlgebra`, `SaliProcessor`, `SentimentVolatilityGuard`, `DiscreteMonitoringCorrection`); ML validation (`WalkForwardValidator`, `CrossModelValidator`, `VotingClassifier`, `AgnosticAggregator`, `MarketEfficiencyMonitor`, `RiskPremiumResidualMonitor`, `MarketSensitivityLibrary`); AUMF (`AumfScenarioEngine`, `CrisisProfile`, `MarketStressSimulator`); quant engine (`LegMatchService`, `UniverseAggregator`, `IntersubjectiveAuditService`, `ReproducibilityService`, `BarrierHittingTimeAnalyzer`).

**Missing named components (6):**
1. `GarchRegimeDetector`, `DlRegimeDetector`, `QEDRegimeDetector` — regime logic is **Python-only**; `RegimeDetector.java` does not delegate to a configured implementation (the v4 delegation model is unrealized in Java).
2. `ExogenousShockDetector`, `CrashProbabilityScore` — the `EXOGENOUS_SHOCK` *regime value* exists; the detector/scoring classes do not.
3. `CalculationGuard`, `AlertManager`, `MarkovStopService` — absent.
4. `EvtRiskService`, `QuantileBandService` Java wrappers — Python-only; no computation-side consumer.
5. `SignalStatus` enum — modeled as `String` constants in `SignalResult.java` (functional, structural deviation).
6. `HighFrequencyAggregator` — implemented as `KernelAggregator` but lives in **ingestion**, not computation.

**Strategy count reality check:** 30 options + 25 equity + 3 fixed-income = **58 distinct classes**, not the plan's "151 strategies" (that figure counts equation/leg permutations).

---

### Track 6 — Landing Page ✅ ~95%

All 8 planned sections render on `landing/app/page.tsx`. v4 status badges (`regime`, `anomaly`, `participation`) live in `StatusBadges.tsx`. SEO/OG/robots/sitemap all present. Stack: Next.js **16.2.6** (plan said 15 — intentional upgrade), React 19, `lightweight-charts`, Tailwind v4, Vitest.

**Minor gaps:** ISR proxy route (`app/api/demo-data/route.ts`) absent — uses client-side SWR fetch instead; analytics (Plausible/Umami) not wired (optional).

---

### Track 7 — Analytics Dashboard ⚠️ ~80% — **components built, page not wired**

**Stack present & correct:** Next.js 16, TanStack Query, Tailwind v4, `d3 ^7.9.0`, `@finos/perspective ^3.8.0` (+ viewer/d3fc/datagrid), `@playwright/test`. OAuth2/PKCE (`lib/auth.ts`), real-time WS hooks, lib utilities all implemented.

**47 components exist and are individually unit-tested** — Perspective used in the 3 planned spots (correlation matrix, liquidity heatmap, signal log); statistical integrity (QQ/ACF/Convergence), market efficiency, monetary policy, Greeks, toxicity, sentiment, pairs, leverage, dynamic stops, model tournament, explainability, performance duality, config editor + audit trail all present.

**Two significant gaps:**
1. **Dashboard page is a scaffold shell.** `frontend/app/page.tsx` renders only `Sidebar` + `Header` + placeholder text "Milestone 1 scaffold. Charts, KPI cards, and panels will be added in subsequent milestones." **None of the 47 components are mounted on the live route.** This is the single most impactful gap in the frontend.
2. **D3 is underused** — only `D3IliHeatmap.tsx` uses the D3 library; the QQ/ACF/convergence/heatmap family uses hand-rolled SVG. Playwright e2e is smoke-only (3 checks vs ~50 planned criteria).

---

### Track 8 — Backtesting Framework ⚠️ ~45%

**Core v1 implemented:** `BacktestEngine`, `HistoricalDataReplay`, `BacktestResult`, `ExecutionDelay`, `DelayDExecutor`, `Eq553SlippageModel`, plus `BayesianWeightOptimizer`, `FireflyWeightOptimizer`, `WalkForwardValidator`, `DiscreteMonitoringCorrection` (Broadie-Kou-Glasserman β=0.5826), `ReturnGapCalculator`, `ComparativeExecutionAnalysis`.

**Capabilities exist but in Python, not as the plan's Java classes:** Sobol (`analytics/.../simulation/sobol_service.py`), robustness heatmap/scan (`analytics/.../backtest/robustness_service.py`), RDS (both Java `ReproducibilityService` and Python `rds_scorer_service.py`).

**Missing (clearly absent):**
- **Regulatory behavioral testing entirely absent** — no MiFID II SMC scenarios, no OTR monitoring, no circuit breaker, no kill-switch, no `RegulatoryComplianceReport` (grep for `mifid|otr|circuit.?break|kill.?switch` = 0 hits; `MarketStressSimulator` only applies scalar multipliers).
- Plan's named Java classes missing: `MultiDimensionalParameterScanner`, `RobustnessHeatmapGenerator`, `AdvancedSlippageModule` (tiered submission impact 0.84/2.03/9.04 bps — only `Eq553SlippageModel` exists), `SobolRobustnessMode`, `ReturnGapIntegration`, `PriceJumpRatioBacktest`, `PairsTradingBacktester`, `SemanticBacktester`, `MultiModelTournament`, `OptimizerComparisonFramework`, `StrategyBacktestRunner`, `RiskBacktestValidator`, `MultipleTestingCorrectionService`.
- v4 speculative components (except `BarrierHittingTimeAnalyzer`) missing.

---

### Track 9 — CI/CD Pipeline ⚠️ ~55%

**8 workflow files present** (plan envisioned 7): `pr-checks.yml`, `deploy-staging.yml`, `deploy-production.yml`, `demo-report.yml`, `deploy-landing.yml`, `chaos-tests.yml`, `chaos-benchmark.yml`, `forecast-deploy.yml`. `atlantis.yaml` present (3 TF projects).

| Workflow | Status | Note |
|:---------|:-------|:-----|
| `pr-checks.yml` | ✅ (gaps) | Java/Python/frontend/integration jobs; **no Checkstyle/SpotBugs lint, no OTEL javaagent, no benchmark regression** |
| `deploy-staging.yml` | ⚠️ PARTIAL | Triggers on `develop` (plan: `main`); **no Docker build/push, no Flyway, deploy+smoke are `echo` placeholders** |
| `deploy-production.yml` | ⚠️ PARTIAL | Triggers on `push` to `main` (plan: `workflow_dispatch` + approval gate); deploy/verify are placeholders |
| `demo-report.yml` | ✅ IMPLEMENTED | Weekly Mon 06:00 UTC; queries DB, posts GitHub Issue |
| `deploy-landing.yml` | ✅ IMPLEMENTED | Vercel + Docker/Cloudflare fallback |
| `chaos-tests.yml` | ⚠️ PARTIAL | 5 scenarios via Docker `tc netem` (plan wanted Chaos Mesh/Toxiproxy); **Scenario 4 still says "Polygon WebSocket disconnect" (stale)**; no report posted |
| `chaos-benchmark.yml` | ⚠️ placeholder | `benchmark` job is `echo "Benchmark placeholder"` |
| standalone `benchmark.yml` | ❌ MISSING | non-functional inside chaos-benchmark only |

**Other gaps:** no CodeQL/security scanning, no Lighthouse CI for landing.

---

### Track 10 — Demo / Virtual Portfolio ✅ ~90% (finalized — see [ADR-017](adr/ADR-017-demo-virtual-portfolio-finalization.md))

**v1 core implemented:** `VirtualPortfolio` (open/close/MTM, SL/TP, `PortfolioSummary`), `PaperTradingEngine` (respects `enabled`/`autoExecute`, skips `DISLOCATED` [Finding 3], gates `DEGRADED` [Finding 6], `ACTIONABLE`-only, `Eq553SlippageModel`), `SignalQualityAnalyzer` (= plan's `SignalQualityReport`, renamed), `DemoController`, `DemoConfig`.

**v5 execution model now implemented (ADR-017):** all eight named classes built and wired into `PaperTradingEngine` — `RandomizedExecutionWindow`, `MarketStabilityGuard`, `OrderImpactPredictor`, `MarketMakerExecutionModel`, `FillProbabilityEngine`, `MarkovStopHandler`, `PassiveExecutionHandler`, `SniperExecutionHandler` — plus nine nested config blocks and a `DemoConfig.core(...)` factory.

**"Island" components now wired:**
- `LeverageSignaler` → `VirtualPortfolio.applyLeverageRotation()` flattens positions on `LEVERAGE_OFF` (`POST /api/v1/demo/leverage-rotation/evaluate`).
- `PortfolioManagementAlgebra` → standardized cost model applied to each opening trade.
- `MarkovStopHandler` (new Java consumer) reads `markov_stop_calibrations`; dynamic stops override fixed levels when `dynamic-stops.enabled`.
- `ComparativeExecutionAnalysis` + `ReturnGapCalculator` → feed the signal-quality report.

**Kill-switch / Big Red Button:** now real — `KillSwitch` service + `/api/v1/demo/kill-switch/{activate,deactivate,status}`; `PaperTradingEngine` blocks on `isActive()`.

**Signal quality enriched:** hit-rate-by-horizon (1/5/10/20d), false-positive rate, avg return per signal, vs-SPY, return gap, mistake attribution, and degraded/valid breakdown — populating the previously-null DB columns + `verification_progress` JSONB. **No DB migration required.**

**Demo dashboard frontend:** new `/demo` route (no auth) mounting `PortfolioSummary`, `TradeHistory`, `SignalQualityMetrics`, `LiveIndicator`, `DisclaimerBanner`, `DemoBadge` + TanStack Query hooks; 29 unit tests added.

**Still deferred:** climate-simulate endpoint, disaster overlay, sentiment-suppressed signals, dual-portfolio A/B runtime, BRI herding/panic guard (speculative / Track 8 overlap), and kill-switch persistence.

---

### Track 11 — Deployment & Operations ⚠️ ~60%

**Docker Compose:** base (backend, analytics-worker, dashboard, landing, timescaledb, **jaeger**), dev, prod (resource limits + log rotation), demo (`MONITOR_DEMO_*` env). `Dockerfile` multi-stage, builds native TA-Lib, runs non-root.

**Runbooks:** 20 `.md` files; **17 align with the plan**, but **stale duplicates** `openbb-sidecar-setup.md` and `polygon-websocket-outage.md` remain, and the **v6 Finnhub/free-data-source replacement runbook was never created**. `runbooks/README.md` lists only 3 (out of date).

| Operational component | Status | Note |
|:----------------------|:-------|:-----|
| Bulkhead executor isolation | ✅ IMPLEMENTED | Used in `KpiProcessor`, `FredClient`, `NyFedClient`, schedulers |
| OpenTelemetry tracing | ✅ IMPLEMENTED | OTLP config + Jaeger in compose |
| Chronicle Queue overflow volume | ✅ (as `ingestion_overflow`) | Replaces Chronicle (intentional) |
| Big Red Button kill-switch | ❌ DOCUMENTATION ONLY | 0 Java matches for `KillSwitch`/`BigRedButton`; no endpoint |
| Systemic Resilience Monitor / Global Safe Mode | ❌ MISSING | runbook exists; only scattered `safe.mode` refs in scenario code |
| v6 data-source env | ⚠️ STALE | base compose still uses `POLYGON_API_KEY`; no `SPRING_PROFILES_ACTIVE=virtual-threads` |

---

### Track 13 — Terraform Spot/Forecast ⚠️ ~50% (IaC present, runtime broken)

**IaC substantially present:** 6 modules (`networking`, `storage`, `container-registry`, `compute-spot`, `orchestrator`, `monitoring`) × 3 environments (AWS/GCP/Azure). `forecast_trigger.py`, `forecast_status.py`, `spot_interruption.py`, shared compose/scripts, `run-forecast.sh`, `forecast-deploy.yml`.

**Graceful shutdown (Plan Phase 2): ✅ fully implemented** — `GracefulShutdown.java`, SIGTERM handler, `server.shutdown: graceful`.

**Unresolved runtime blockers (from the plan's own review findings):**
- **C1:** `forecast_trigger.py` is a **no-op** — no `request_spot`/`run_instances`; spot instances never launch.
- **C2:** Healthcheck uses `curl` which is **not installed** in `eclipse-temurin:25-jre` (actuator now on classpath, but curl missing).
- **C4/C5:** cloud-config + user-data templates reference `forecast-task.sh` and the broken healthcheck — double-exec / missing-script issues persist.
- **M3:** `user-data.tftpl` is dead code (unreferenced).
- **Phase 5 local path** (`infra/local/`): absent.

---

### Track 14 — Production Infrastructure ❌ ~0%

**Entirely unimplemented.** None of the 7 planned new modules exist: `hosting`, `database`, `dns-tls`, `secrets`, `observability`, `backup`, `budget`. No `infra/local/` full-stack path. No per-environment staging/production overrides. No monitoring stack (Prometheus/Loki/Grafana, `docker-compose.monitoring.yml`). No Caddy service. `deploy-staging.yml`/`deploy-production.yml` still reference placeholders ("requires Track 11 Docker infrastructure"). The plan exists only as a design document.

---

## 3. Cross-Cutting Themes

These patterns recur across multiple tracks and represent the highest-leverage items to address:

1. **v6 free-data-source migration is incomplete at the edges.** Source clients are migrated (Track 4 ✅), but legacy Polygon artifacts linger in `cdm/adapter/`, the base `docker-compose.yml` still reads `POLYGON_API_KEY`, the chaos workflow still references "Polygon WebSocket", and the Finnhub/free-source runbook was never written. **Cleanup is partial.**

2. **The ML/sentiment layer is "shape-correct but substance-substituted."** Every ML service except the autoencoder is a numpy/scipy proxy: no `transformers`/FinBERT, no `xgboost`, no `shap`, no `arch`, LSTM ignored, CNN-LSTM untrained. Arrow IPC (the headline transport) is a 4-line stub. Functionally present for demo purposes; diverges materially from the planned dependency model.

3. **Regime detection delegation model is unrealized in Java.** GARCH/CNN-LSTM/QED run purely in the Python worker; `RegimeDetector.java` does not route to them — the v4 pluggable-regime design is not wired on the Java side.

4. **"Island" components — built but not integrated.** `LeverageSignaler`, `PortfolioManagementAlgebra`, `Markov stops` (Python), comparative execution, return gap, etc. exist as standalone classes/services but are **not consumed** by `PaperTradingEngine`/`VirtualPortfolio`/`BacktestResult`. The pieces exist; the assembly does not.

5. **Frontend: components built, not mounted.** 47 dashboard components exist and are unit-tested, but `frontend/app/page.tsx` is still the Milestone-1 scaffold — nothing is rendered on the live route. The landing page, by contrast, is fully wired.

6. **Test coverage is uneven and risky where it matters most.** Computation (57 tests) and analytics (33 tests) are well-covered. **Ingestion coverage was undercounted** in the original report — the module carries ~25 Groovy Spock specs (not just the 4 `.java` tests), so source clients, buffer, cache, and quality monitors were already covered; the real ingestion gaps were the new resilience code (since closed in ADR-013). `integration-tests/` has 2 smoke classes.

7. **Safety/operability controls are documentation-only.** Big Red Button kill-switch and Systemic Resilience Monitor/Global Safe Mode have runbooks but no Java implementation. Regulatory behavioral testing (MiFID II SMC/OTR/circuit breaker) is entirely absent.

8. **Production deployment path is not real.** Track 14 is 0% implemented; deploy workflows are `echo` placeholders; the spot pipeline's trigger is a no-op and its healthcheck is broken. The platform runs locally; it does not yet deploy to production.

---

## 4. Recommended Priority Order

Based on the gaps above (impact × the plan's own dependency ordering):

1. **Wire the dashboard** (Track 7) — mount the 47 existing components on `app/page.tsx`. Highest visible ROI; components already exist.
2. **Connect the "island" components** (Tracks 5/8/10) — integrate leverage rotation, portfolio algebra, Markov stops, comparative execution, return gap into `PaperTradingEngine`/`VirtualPortfolio`/`BacktestResult`.
3. **Add ingestion tests** (Track 4) — WireMock/Testcontainers coverage for every free-data-source client.
4. **Implement Big Red Button + kill-switch** (Tracks 10/11) — headlined safety control, currently runbook-only.
5. **Finish v6 cleanup** (Tracks 1/4/9/11) — remove Polygon artifacts, fix compose env, rename chaos scenario, write Finnhub runbook.
6. **Production infrastructure** (Track 14) — currently 0%; blocking any real deployment.
7. **Real ML** (Track 3) — replace lexicon/proxy services with FinBERT/XGBoost/SHAP if the demo's credibility requires it; otherwise document the substitution explicitly.

---

## 5. Methodology

- One verification pass per plan track, executed in parallel.
- Each pass read its plan document(s), enumerated the relevant source trees (`find`), checked each named class/feature (`grep`, case-insensitive), and read representative sources to confirm behavior (not just presence).
- Status legend: ✅ IMPLEMENTED · ⚠️ PARTIAL/DEVIATES · ❌ MISSING/DEFERRED.
- Confidence is HIGH across all tracks: findings rest on direct file inspection, not inference.
- This report is a point-in-time snapshot against branch `feature/dataset-sources` @ `834546b` (2026-06-13).
