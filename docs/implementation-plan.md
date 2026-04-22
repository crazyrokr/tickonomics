# Tickonomics Implementation Plan

**Last updated:** 2026-05-30
**Branch:** `test`

## Legend

- DONE - Fully implemented with tests
- PARTIAL - Some deliverables complete, others pending
- PENDING - Not started

---

## Track 1: Project Scaffolding & API Contracts (Phase 0)

**Status: DONE**

| Sub-track                                 | Status | Notes                                                                                                                                                                            |
|-------------------------------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.1 Multi-module Gradle structure         | DONE   | settings.gradle, root build.gradle, all module build files                                                                                                                       |
| 1.2 OpenAPI 3.1 spec                      | DONE   | api-contracts/openapi.yaml (quant strategies, signals, risk, audit endpoints)                                                                                                    |
| 1.3 CDM Module (enums, mappers, adapters) | DONE   | InstrumentType, RateType, CdmInstrumentMapper, CdmAdapter interface, Fred/NyFed/Polygon adapters, CdmRateSnapshot, CdmTick, CdmInstrumentRef, CdmBondSnapshot. 43 tests passing. |
| 1.4 TalibAdapter + native build           | DONE   | TalibAdapter (SMA, StdDev, Correl, Beta, LinearRegSlope, BollingerBands, ROC, RSI), BBandsResult, TalibNativeLoader. TA-Lib native C build via Gradle tasks.                     |
| 1.5 HTTP Client Interfaces                | DONE   | FederationDataClient, AnalyticsWorkerClient, PolygonWsClient interfaces                                                                                                          |
| 1.6 ArrowIpcTransport                     | DONE   | Serialize/deserialize time-series and analysis results via Arrow IPC                                                                                                             |
| 1.7 App module config                     | DONE   | TickonomicsApplication, application.yml (PostgreSQL, Flyway, virtual threads, Hikari)                                                                                            |

---

## Track 2: Database Schema - TimescaleDB (Phase 0)

**Status: DONE**
**Depends on:** Track 1 (DONE)

| Deliverable              | Status | Notes                                                                                                                                                                                                                                                                                                                                                                                                                    |
|--------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| V1-V18 Flyway migrations | DONE   | Hypertables (V1), relational tables (V2), continuous aggregates (V3), compression/retention (V4), indexes (V5), demo tables (V6), CDM enums (V7), precomputed KPI views (V8), anomaly columns (V9), idempotency/events (V10), optimization tables (V11), v4 aggregates (V12), v4 indexes (V13), proposals 05-07 tables (V14), v5 aggregates (V15), v5 indexes (V16), sentiment/execution (V17), diagnostics/greeks (V18) |
| V19-V25 migrations       | DONE   | Strategy definitions, audit log, option chains, macro shock, quantile coefficients, universe stats, indexes                                                                                                                                                                                                                                                                                                              |
| Entity records           | DONE   | TickData, RateSnapshot, IliHistory, ZscoreSeries, CorrelationOutput, SignalLog, ProxyDivergenceEvent, IngestionDlqEntry. 15 tests.                                                                                                                                                                                                                                                                                       |
| JDBC Repositories        | DONE   | TickDataRepository, RateSnapshotRepository, IliHistoryRepository, ZscoreSeriesRepository, CorrelationOutputRepository, SignalLogRepository, ProxyDivergenceEventRepository, IngestionDlqRepository. Batch insert support.                                                                                                                                                                                                |
| TimescaleDbConfig        | DONE   | HikariCP + Flyway baseline-on-migrate + NamedParameterJdbcTemplate                                                                                                                                                                                                                                                                                                                                                       |

---

## Track 3: Python Analytics Worker (Phase 0)

**Status: PARTIAL**
**Depends on:** Nothing (independent)

| Deliverable                                         | Status  | Notes                                                                                                                      |
|-----------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------|
| FastAPI app + config                                | DONE    | app/main.py, app/config.py                                                                                                 |
| Arrow IPC transport                                 | DONE    | app/transport/arrow_ipc.py                                                                                                 |
| Statistical routers/services                        | DONE    | EVT risk, macro shock, multiple testing, quantile regression, transfer entropy, yield curve. 6 service + 6 router modules. |
| Request/response models                             | DONE    | app/models/requests.py, responses.py                                                                                       |
| Health router                                       | DONE    | app/routers/health.py                                                                                                      |
| Tests                                               | DONE    | 27 test files, 151 total tests covering all services                                                                       |
| Econometrics (ADF, Granger, OLS)                    | DONE    | ADF with AIC lag selection, Granger causality, OLS regression. Router wired into main.py.                                  |
| Risk metrics (VaR, CVaR, GARCH)                     | DONE    | Historical/parametric VaR, CVaR (Expected Shortfall), GARCH(p,q) MLE forecasting. Router wired into main.py.               |
| Fixed income (duration, convexity, YTM)             | DONE    | Macaulay/modified duration, convexity, Newton-Raphson YTM. 12 tests.                                                       |
| Performance (Fama-French, Sharpe/Sortino)           | DONE    | Sharpe ratio, Sortino ratio, CAPM/3-factor Fama-French regression. 11 tests.                                               |
| v4 anomaly detection (autoencoder)                  | DONE    | PyTorch autoencoder train/detect, `/api/v1/anomaly/train`, `/api/v1/anomaly/detect`. 8 tests.                              |
| v4 regime detection (GARCH, CNN-LSTM, QED, RAHF)    | DONE    | GARCH regime, CNN-LSTM hybrid, QED quartic potential, RAHF harmonic. `/api/v1/regime/*`. 10 tests.                         |
| v4 online optimizer (SGD weight tuning)             | DONE    | Constrained SGD weight-delta with sum-to-one projection. `/api/v1/optimizer/weight-delta`. 8 tests.                        |
| v4 climate model (stochastic simulation)            | DONE    | Euler-Maruyama SDE with mean-reversion + seasonal forcing. `/api/v1/climate/simulate`. 4 tests.                            |
| v4 drift-diffusion (Ito process)                    | DONE    | Ito process simulation + barrier hitting probability. `/api/v1/drift/simulate`, `/api/v1/drift/barrier`. 5 tests.          |
| v5 Sobol Monte Carlo, BSM Greeks, GEX               | DONE    | Sobol quasi-random MC, discrete monitoring correction, BSM closed-form Greeks, GEX aggregation. `/api/v1/simulate/*`, `/api/v1/greeks/*`. 13 tests. |
| v5 liquidity analytics, backtesting scan            | DONE    | Comovement PCA, Amihud illiquidity, strategic run detection, Sobol robustness scan, RDS scoring. `/api/v1/liquidity/*`, `/api/v1/backtest/*`, `/api/v1/reproducibility/*`. 19 tests. |
| v5 FinBERT sentiment, Markov Stop Engine            | DONE    | Lexicon-based sentiment with negation/intensifier support, SALI polarity scoring, Markov stop-loss/take-profit calibration. `/api/v1/sentiment/*`, `/api/v1/stops/calibrate`. 10 tests. |
| v5 diagnostics, volatility forecast, explainability | DONE    | QQ-plot/ACF/convergence diagnostics, GARCH volatility forecast, benchmark tournament, SHAP feature importance, Q-World CIR bond pricer, T-Bill Greeks. `/api/v1/diagnostics/*`, `/api/v1/analytics/volatility-forecast`, `/api/v1/tournament/*`, `/api/v1/explainability/*`, `/api/v1/fixed-income/q-world-fair-value`, `/api/v1/fixed-income/tbill-greeks`. 21 tests. |

---

## Track 4: Ingestion & Resilience Layer (Phase 1)

**Status: DONE**
**Depends on:** Tracks 1 (DONE), 2 (DONE)

| Deliverable                                | Status  | Notes                                                                                                                |
|--------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------|
| KernelAggregator                           | DONE    | ingestion/kernel/ module                                                                                             |
| FredClient (direct FRED HTTP)              | DONE    | @Scheduled polling, CDM mapping via FredCdmAdapter, REST client                                                      |
| NyFedClient (direct NY Fed HTTP)           | DONE    | @Scheduled polling, CDM mapping via NyFedCdmAdapter, REST client                                                     |
| PolygonWsClient impl                       | DONE    |                                                                                                                      |
| TimescaleDbWriter (batched INSERT)         | DONE    | Concurrent queue buffers, configurable batch-size, auto-flush on interval, error re-buffering                        |
| Disk-backed ingestion buffer               | DONE    | TieredIngestionBuffer with JSON-lines file overflow. InMemoryIngestionBuffer + FileOverflowBuffer. Recovery on restart. |
| DataQualityChecker                         | DONE    | Outlier detection (>50 bps), staleness detection, batch checking                                                     |
| ProxyDivergenceGuard                       | DONE    | T-Bill/SOFR divergence detection, correlation computation, event recording                                           |
| Circuit breakers (Resilience4j)            | DONE    | @Retry on FredClient, NyFedClient, RestClientAnalyticsWorkerClient. Exponential backoff in application.yml.          |
| Polygon WebSocket client                   | DONE    | DefaultPolygonWsClient with auth, subscribe/unsubscribe, tick parsing, async handlers. ConditionalOnProperty toggle. |
| Idempotency key integration                | DONE    | FredClient/NyFedClient route through TimescaleDbWriter. Scheduled eviction on IdempotencyGuard.                      |
| Last known good cache                      | DONE    | LastKnownGoodCache component with ConcurrentHashMap, configurable max staleness, FRESH/STALE/EXPIRED statuses.       |
| Bulkhead executor isolation                | DONE    | @Bulkhead semaphore on criticalIngestion(4), highVolumeIngestion(16), computationEngine(8).                          |
| OpenTelemetry tracing                      | DONE    | Micrometer Observation + OTel bridge. Auto-instruments RestClient/JdbcTemplate. OTLP exporter to localhost:4317.     |
| v5 DeRoundingFilter, TimePeriodicityFilter | DONE    | TickFilter interface. DeRoundingFilter (round-mark volume smoothing). TimePeriodicityFilter (1s spike detection).    |
| v5 OptionsDataClient, ToxicityMonitor      | DONE    | OptionsDataClient polls Polygon Options API. ToxicityMonitor computes HARMFUL/BENEFICIAL/NEUTRAL scores.             |
| v5 EconomicCalendarClient                  | DONE    | Polls FRED release calendar. EconomicEvent record. ConditionalOnProperty toggle.                                     |

---

## Track 5: Computation Engine (Phase 2)

**Status: DONE**
**Depends on:** Tracks 1 (DONE), 2 (DONE), 3 (PARTIAL)

| Deliverable                                          | Status  | Notes                                                                                                                                                                                                                                                                                                         |
|------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Equity strategies (27 files)                         | DONE    | RSI, MACD, Bollinger, Ichimoku, Aroon, Coppock, Donchian, Keltner, MeanReversion, Pairs, SAR, Stochastic, Support/Resistance, TRIX, ValueBP, Volume, ZigZag, AccumDist, BBBreakout, ChaikinVol, ClusterMeanReversion, EarningsSurprise, MomentumDecile, MACrossover, MFIInversion, UniverseAggregator/Context |
| Options strategies (31 files)                        | DONE    | Iron Condor, Butterfly, Straddle, Strangle, Calendar, Diagonal, Box, Ratio, Seagull, Backspread, Bear/Bull spreads, Collar, Covered Call, Married Put, Protective Put, Christmas Tree, Risk Reversal, Synthetic, LegMatchService, StrategyRegistry                                                            |
| Fixed income (4 files)                               | DONE    | BondPosition, FixedIncomePortfolio, FixedIncomePortfolioBuilder, FixedIncomeStrategyType                                                                                                                                                                                                                      |
| Backtest (4 files)                                   | DONE    | BacktestResult, DelayDExecutor, Eq553SlippageModel, ExecutionDelay                                                                                                                                                                                                                                            |
| Audit (3 files)                                      | DONE    | AuditEntry, CodingRule, IntersubjectiveAuditService                                                                                                                                                                                                                                                           |
| Strategy framework (4 files)                         | DONE    | AlphaSignal, BaseStrategy, StrategyContext, Strategy                                                                                                                                                                                                                                                          |
| TalibAdapter                                         | DONE    | Full TA-Lib wrapper with 8 functions                                                                                                                                                                                                                                                                          |
| ArrowIpcTransport                                    | DONE    | Arrow IPC serialization                                                                                                                                                                                                                                                                                       |
| Tests (12 files)                                     | DONE    | Unit tests for audit, backtest, equity, fixed income, options, talib, transport                                                                                                                                                                                                                               |
| NormalizationService                                 | DONE    | Tiered Z-score (MACRO 252d, FLOW 60d, VOLATILITY 20d), percentile rank computation                                                                                                                                                                                                                            |
| IliCalculator                                        | DONE    | ILI = w1*Z_rrp + w2*Z_spread - w3*Z_vol. Dynamic weight redistribution. VALID/DEGRADED/DISLOCATED statuses.                                                                                                                                                                                                   |
| CorrelationEngine                                    | DONE    | Pre-computed aggregates + TA-Lib live (Pearson correlation, OLS beta). 3 tests.                                                                                                                                                                                                                              |
| GrangerCausalityTest                                 | DONE    | Delegates to Python analytics worker /api/v1/econometrics/granger. RestClientAnalyticsWorkerClient impl. 8 tests.                                                                                                                                                                                           |
| RegimeDetector                                       | DONE    | Volatility percentile analysis, exogenous shock override, rolling vol. 7 tests.                                                                                                                                                                                                                              |
| IntradayProxyService                                 | DONE    | Proxy quality monitoring with divergence detection (NORMAL/ELEVATED/DISLOCATED). 5 tests.                                                                                                                                                                                                                   |
| SignalGenerator                                      | DONE    | Percentile-rank adaptive thresholds, cooldown, transaction cost modeling, 5 status codes                                                                                                                                                                                                                      |
| KpiProcessor                                         | DONE    | 6 KPIs (LSI, Repo/Equity Beta, RRP Drain Velocity, Volatility Regime, Efficiency Gap, Systemic Risk Heatmap). 11 tests.                                                                                                                                                                                      |
| v4 AdaptiveIliCalculator, BayesianWeightOptimizer    | DONE    | AdaptiveIliCalculator wraps IliCalculator with SGD weight deltas. WeightedWeightStore with 10% max drift. BayesianWeightOptimizer with 3 profiles.                                                                                                                                                           |
| v5 DiscreteMonitoringCorrection, ReturnGapCalculator | DONE    | Broadie-Kou-Glasserman correction (beta=0.5826). ReturnGap = InvestorGrossReturn - HoldingsReturn. Integrated into KpiProcessor.                                                                                                                                                                             |
| v5 AumfScenarioEngine, GexWeightedRegimeDetector     | DONE    | AumfScenarioEngine matches conditions against COVID/SNB/BlackMonday profiles. GexWeightedRegimeDetector integrates options gamma exposure. AumfStatus enum. SignalResult extended with SAFE_MODE/SUSPENDED_UNCERTAINTY/PROCEED_CAUTIOUSLY.                                                                    |

---

## Track 6: Landing Page (Phase 3)

**Status: DONE**
**Depends on:** Nothing (independent frontend)

- Next.js 16 static-export marketing page at tickonomics.io
- Hero, Problem, How It Works, Live Demo (ILI chart + status badges), Signal Showcase, Portfolio Teaser, Pricing, Footer
- SEO with sitemap.xml, robots.txt, OpenGraph, Twitter Card, JSON-LD
- All dynamic sections gracefully degrade to mock data
- 38 unit tests, 92% line coverage
- ADR: `docs/adr/ADR-005-landing-page-architecture.md`

---

## Track 7: Analytics Dashboard (Phase 4)

**Status: DONE**
**Depends on:** Track 1 (DONE)
**Milestones:** [`docs/milestones/track7/`](milestones/track7/README.md)

| Milestone | Components | Depends on | Status |
|-----------|-----------|------------|--------|
| [M1: Scaffolding, Layout, Auth, API Client](milestones/track7/M1-scaffolding-layout-auth.md) | 7 | — | DONE |
| [M2: Core Charts](milestones/track7/M2-core-charts.md) | 2 | M1 | DONE |
| [M3: KPI Dashboard Cards](milestones/track7/M3-kpi-cards.md) | 6 | M1, M2 | DONE |
| [M4: System Monitoring](milestones/track7/M4-system-monitoring.md) | 3 | M1 | DONE |
| [M5: Data Grids with Perspective](milestones/track7/M5-perspective-grids.md) | 3 | M1 | DONE |
| [M6: Configuration & Admin](milestones/track7/M6-config-admin.md) | 2 | M1 | DONE |
| [M7: v4 Regime Analytics](milestones/track7/M7-v4-regime.md) | 5 | M2, M3, M4 | DONE |
| [M8: v4 Governance & Quality](milestones/track7/M8-v4-governance.md) | 4 | M3, M4 | DONE |
| [M9: v5 Backtesting & Explainability](milestones/track7/M9-v5-backtesting.md) | 5 | M2, M3, M5 | DONE |
| [M10: v5 Market Microstructure](milestones/track7/M10-v5-market-microstructure.md) | 6 | M3, M5 | DONE |
| [M11: v5 Trading Intelligence](milestones/track7/M11-v5-trading-intelligence.md) | 5 | M2, M3 | DONE |
| [M12: v5 Statistical Integrity & Risk](milestones/track7/M12-v5-statistical-risk.md) | 7 | M2, M3, M5 | DONE |

---

## Track 8: Backtesting Framework (Phase 5)

**Status: DONE**
**Depends on:** Track 5 (DONE)

| Deliverable                                         | Status | Notes                                                                                                                                                                            |
|-----------------------------------------------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EquityStrategyRegistry                              | DONE   | 25 equity strategies with UniverseAggregator support. Mirrors options StrategyRegistry pattern.                                                                                  |
| DelayDExecutor (fixed)                              | DONE   | Eq553SlippageModel wired via constructor. TradeLog populated per trade. Slippage, adjusted return, liquidity fragility computed per run.                                         |
| AlphaSignalRepository + BacktestResultRepository    | DONE   | Persistence layer for alpha_signals hypertable and backtest_results table. NamedParameterJdbcTemplate pattern.                                                                   |
| HistoricalDataReplay                                | DONE   | Tick data replay + daily return computation from tick_data hypertable.                                                                                                           |
| BacktestEngine                                      | DONE   | Central orchestrator: replay data → run strategy → DelayDExecutor → persist results.                                                                                           |
| WeightOptimizer                                     | DONE   | Sobol robustness scan via Python analytics worker. OptimizationResult record.                                                                                                    |
| MarketStressSimulator                               | DONE   | COVID-2020, SNB-2015, BlackMonday-1987 stress profiles. Sharpe degradation + drawdown increase metrics.                                                                          |
| ReproducibilityService                              | DONE   | Git SHA + SHA-256 dataset hash + RDS score from Python worker.                                                                                                                   |
| BarrierHittingTimeAnalyzer                          | DONE   | DiscreteMonitoringCorrection (Broadie-Kou-Glasserman beta=0.5826). Hit probability + expected hitting time.                                                                     |
| V26 migration                                       | DONE   | Seeds 25 equity strategy_definitions rows.                                                                                                                                       |
| ADR-007                                             | DONE   | `docs/adr/ADR-007-backtesting-framework.md`                                                                                                                                      |

---

## Track 9: CI/CD Pipeline (Phase 7)

**Status: DONE**
**Depends on:** Nothing (infrastructure)

| Deliverable                                   | Status | Notes                                                                                                         |
|-----------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------|
| PR checks workflow                            | DONE   | `.github/workflows/pr-checks.yml` — 4 parallel jobs: Java 25, Python 3.12, Node 22 (matrix), integration     |
| Staging deployment workflow                   | DONE   | `.github/workflows/deploy-staging.yml` — build all artifacts, deploy placeholder (needs Track 11 Docker)     |
| Production deployment with approval           | DONE   | `.github/workflows/deploy-production.yml` — environment: production with required reviewers                  |
| Chaos/benchmark workflow                      | DONE   | `.github/workflows/chaos-benchmark.yml` — weekly scheduled, full test suite + TimescaleDB service container  |
| ADR-008                                       | DONE   | `docs/adr/ADR-008-cicd-pipeline.md`                                                                          |

---

## Track 10: Demo / Virtual Portfolio (Phase 6)

**Status: DONE**
**Depends on:** Tracks 4 (DONE), 5 (DONE)

| Deliverable                                   | Status | Notes                                                                                                              |
|-----------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------|
| VirtualPortfolioPosition entity               | DONE   | Record with compact constructor validation. Direction, SL/TP, closed_at fields.                                   |
| VirtualPortfolioTrade entity                  | DONE   | Record with direction validation (BUY/SELL), commission, slippage, realized P&L.                                   |
| VirtualPortfolioPositionRepository            | DONE   | Save, findOpenPositions, findOpenBySymbol, updateMarkToMarket, close. NamedParameterJdbcTemplate.                 |
| VirtualPortfolioTradeRepository               | DONE   | Save, findByPositionId, findLatest (paginated), countByTradeType.                                                 |
| SignalQualityReportRepository                 | DONE   | Save, findLatest, findByDateBetween. JSONB verification_progress.                                                  |
| V27 migration                                 | DONE   | Added direction + closed_at columns, partial index on open positions, trade/quality report indexes.               |
| DemoConfig                                    | DONE   | @ConfigurationProperties(prefix="monitor.demo"). Opt-in defaults, SL/TP %, position sizing, data quality gating.   |
| VirtualPortfolio service                      | DONE   | Open/close positions, mark-to-market, SL/TP checks, portfolio summary. Stateless (queries DB per operation).      |
| PaperTradingEngine service                    | DONE   | Signal-to-trade bridge. Gates on config/data status/signal status. Eq553SlippageModel. Opposing direction flips.   |
| SignalQualityAnalyzer service                 | DONE   | Portfolio P&L, Sharpe, win rate, max drawdown. 90-day verification progress JSONB with 5 criteria.                |
| DemoController                                | DONE   | 7 REST endpoints under /api/v1/demo/. Portfolio, positions, trades, signal-quality, close-position.               |
| Entity tests                                  | DONE   | VirtualPortfolioPositionTest (10), VirtualPortfolioTradeTest (12). Given-When-Then structure.                      |
| Computation tests                             | DONE   | VirtualPortfolioTest (12), PaperTradingEngineTest (13), SignalQualityAnalyzerTest (12). Given-When-Then.           |
| ADR-009                                       | DONE   | `docs/adr/ADR-009-demo-virtual-portfolio.md`                                                                      |

---

## Track 11: Deployment & Operations (Phase 8)

**Status: DONE**
**Depends on:** All tracks

| Deliverable                                    | Status | Notes                                                                                                              |
|------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------|
| Backend Dockerfile (multi-stage)               | DONE   | eclipse-temurin:25, builds TA-Lib native + bootJar, non-root user                                                 |
| Analytics worker Dockerfile (multi-stage)      | DONE   | python:3.12-slim, two-stage dep install, health check on /health                                                   |
| Dashboard Dockerfile (standalone)              | DONE   | node:22-alpine, Next.js standalone output, non-root user                                                          |
| Landing page Dockerfile (static + nginx)       | DONE   | node:22-alpine build + nginx:1.27-alpine serve, SPA routing                                                       |
| docker-compose.yml (base)                      | DONE   | 6 services: backend, analytics-worker, dashboard, landing, timescaledb, jaeger. DB port not exposed by default     |
| docker-compose.dev.yml                         | DONE   | Override: exposes TimescaleDB port 5432 for local development                                                      |
| docker-compose.demo.yml                        | DONE   | Override: demo mode enabled, virtual portfolio, auto-execute signals                                               |
| docker-compose.prod.yml                        | DONE   | Override: resource limits, restart policies, internal-only DB, log rotation                                        |
| .dockerignore (root + analytics)               | DONE   | Exclude .git, build artifacts, node_modules, native-libs source                                                    |
| .env.example                                   | DONE   | All configurable env vars with safe defaults                                                                       |
| landing/nginx.conf                             | DONE   | SPA routing, static asset caching, gzip compression                                                                |
| requirements.txt fix                           | DONE   | Fixed `:` → `==` separator bug for valid pip syntax                                                                |
| frontend/next.config.ts update                 | DONE   | Added `output: "standalone"` for optimized Docker deployment                                                       |
| ADR-010                                        | DONE   | `docs/adr/ADR-010-deployment-operations.md`                                                                       |
| Runbooks (3)                                   | DONE   | `docs/runbooks/` — deployment, monitoring/troubleshooting, data management                                         |

---

## All Tracks Complete
