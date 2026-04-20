Now I have all the data. Here is the complete comparison:

---

# Plan v5 vs. Implementation — Unimplemented Features

## Summary

| Area                      | Planned        | Implemented    | Missing                            |
| ------------------------- | -------------- | -------------- | ---------------------------------- |
| Computation Engine (Java) | ~64 components | ~27 components | **37 components**                  |
| Ingestion Layer (Java)    | 23 components  | ~16 components | **7 components**                   |
| CI/CD Workflows           | 7 workflows    | 4 workflows    | **3 workflows**                    |
| Database Tables           | 34+ tables     | 31 tables      | **2 tables**                       |
| Docker/Infra              | 7 items        | 4 items        | **3 items**                        |
| Frontend (Dashboard)      | 40 components  | 52 components* | **2 items** (D3.js, e2e)           |
| Runbooks                  | 21 runbooks    | 4 runbooks     | **17 runbooks**                    |
| Auth/Security             | OAuth2+PKCE    | Partial        | **Spring Security config missing** |

\*Dashboard has more components than planned due to granular breakdown.

---

## 1. Computation Engine — 37 Missing Components
**Plan ref:** `05-computation-engine.md`

### v4 Components (Proposals 02–04)
| #   | Component                        | Plan Section     | Description                                                    |
| --- | -------------------------------- | ---------------- | -------------------------------------------------------------- |
| 1   | `LiquidityStressTestModule`      | §v4 Governance   | Swiss Franc 2015, Repo Spike 2019, COVID 2020 stress scenarios |
| 2   | `FireflyWeightOptimizer`         | §v4 Optimization | Population-based metaheuristic, A/B test vs Bayesian           |
| 3   | `RegimeAwareWeightingService`    | §v4 Optimization | Efficiency-regime weight adjustments                           |
| 4   | `AmbiguityAdjustedIli`           | §v4 ILI          | Uncertainty bands, UNCERTAIN status                            |
| 5   | `ScheduledCalibrationTask`       | §v4 Optimization | 30-day interval autonomous calibration                         |
| 6   | `SurpriseIndicator`              | §v4 ILI          | Information theory entropy/surprise scoring                    |
| 7   | `ClimateSensitivityFactor`       | §v4 Climate      | Climate-adjusted ILI thresholds                                |
| 8   | `ClimateRiskGuard`               | §v4 Climate      | Climate-adjusted ILI confidence                                |
| 9   | `SessionRangeService`            | §v4 Regime       | Session-aware regime detection (Asian/European/US)             |
| 10  | `ParticipationGovernanceService` | §v4 Governance   | Formal signal decomposition with admissibility checks          |

### v5 Core Components (Proposals 05–07)
| #   | Component                       | Plan Section  | Description                                                         |
| --- | ------------------------------- | ------------- | ------------------------------------------------------------------- |
| 11  | `ComovementTrigger`             | §v5 Liquidity | Liquidity comovement factor integration                             |
| 12  | `PhantomLiquidityService`       | §v5 Liquidity | PLI calculation and ILI discounting                                 |
| 13  | `ToxicityAdjustedIli`           | §v5 Liquidity | Toxicity-based ILI sensitivity adjustment                           |
| 14  | `AlgorithmicIntensityMetric`    | §v5 Liquidity | Message-based AT proxy with quintile bucketing                      |
| 15  | `BehaviouralRiskProcessor`      | §v5 Liquidity | BRI with 5th systemic risk axis                                     |
| 16  | `TimeOfDayThresholdManager`     | §v5 Liquidity | Adaptive intraday thresholds (tighten at open/close)                |
| 17  | `DefiningRangeService`          | §v5 Liquidity | DR-based signal confidence, news confidence decay                   |
| 18  | `LiquiditySourceClassifier`     | §v5 Liquidity | Trader type estimation (Algo/Institutional/Professional/Retail)     |
| 19  | `LiquidityMeanReversionSpeed`   | §v5 Liquidity | AT activity lagged quality effect                                   |
| 20  | `LiquidityPremiumFactor`        | §v5 Liquidity | AT intensity premium in cost-benefit model                          |
| 21  | `StrategicRunService`           | §v5 Core      | Groups consecutive buy/sell child orders for pattern identification |
| 22  | `PriceImpactKpi`                | §v5 Core      | NBBO midpoint at submission time                                    |
| 23  | `InformationEfficiencyAnalyzer` | §v5 Core      | Price Jump Ratio diagnostic                                         |

### v5 Execution & Sentiment Components (Proposals 08–09)
| #   | Component                      | Plan Section  | Description                                                  |
| --- | ------------------------------ | ------------- | ------------------------------------------------------------ |
| 24  | `LeverageSignaler`             | §v5 Execution | 200-day MA leverage rotation (LEVERAGE_ON/OFF)               |
| 25  | `PairsTradingEngine`           | §v5 Execution | Minimum-distance pairs trading verification                  |
| 26  | `ComparativeExecutionAnalysis` | §v5 Execution | Passive (limit-order) vs aggressive (market-order) execution |
| 27  | `PortfolioManagementAlgebra`   | §v5 Execution | Margin algebra, standardized cost model                      |
| 28  | `AlgorithmicBehaviorAlignment` | §v5 Execution | Time-decay for IntradayProxyService                          |
| 29  | `SaliProcessor`                | §v5 Sentiment | Sentiment-augmented ILI (70% ILI / 30% sentiment)            |
| 30  | `SentimentVolatilityGuard`     | §v5 Sentiment | BERT certainty-based regime threshold adjustment             |

### v5.1 Advanced Components (Proposals 10–12)
| #   | Component                    | Plan Section      | Description                                                      |
| --- | ---------------------------- | ----------------- | ---------------------------------------------------------------- |
| 31  | `AgnosticAggregator`         | §v5.1 ML          | Regret-minimization weight aggregation using exponential weights |
| 32  | `WalkForwardValidator`       | §v5.1 ML          | Anchored walk-forward analysis for OOS validation                |
| 33  | `CrossModelValidator`        | §v5.1 ML          | Tournament framework ILI vs XGBoost vs LSTM                      |
| 34  | `VotingClassifier`           | §v5.1 ML          | Optional ML confirmation filter                                  |
| 35  | `MarketEfficiencyMonitor`    | §v5.1 Sensitivity | Fundamental-algorithmic gap indicator                            |
| 36  | `RiskPremiumResidualMonitor` | §v5.1 Sensitivity | Q-world validation of ProxyDivergenceGuard                       |
| 37  | `MarketSensitivityLibrary`   | §v5.1 Sensitivity | Standardized Greeks nomenclature (Repo-Delta, Rate-Delta, Volga) |

---

## 2. Ingestion Layer — 7 Missing Components
**Plan ref:** `04-ingestion-layer.md`

| #   | Component                       | Plan Section  | Description                                                                                                 |
| --- | ------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------- |
| 1   | `DisasterAlertClient`           | §Component 15 | USGS Earthquake + GDACS RSS polling, EXOGENOUS_SHOCK regime trigger                                         |
| 2   | `AlgorithmicSanityGuard`        | §Component 19 | Price >10% in <1s or >10000 msg/sec detection, Manual Oversight trigger                                     |
| 3   | `OrderCancellationMonitor`      | §Component 23 | Cancellation ratios per symbol, volatility leading indicator                                                |
| 4   | `OpenBBClient` (Java)           | §Component 3  | OpenBB sidecar for equity/ETF prices (deferred to Python-only)                                              |
| 5   | `EventBasedTimeConverter`       | §Component 14 | Maps raw ticks to directional change and overshoot events                                                   |
| 6   | `AnomalyDetectionWorker` (Java) | §Component 10 | Async autoencoder-based anomaly detection in Java                                                           |
| 7   | **Chronicle Queue integration** | §Component 6  | Off-heap ring buffer + disk-backed overflow (replaced by `TieredIngestionBuffer` with `FileOverflowBuffer`) |

> **Note:** Items 4, 6, and 7 may be intentional design simplifications — the analytics worker handles anomaly detection in Python, and `FileOverflowBuffer` replaces Chronicle Queue. `OpenBBClient` was deferred in favor of direct FRED/NY Fed clients.

---

## 3. CI/CD — 3 Missing Workflows
**Plan ref:** `09-cicd-pipeline.md`

| #   | Workflow             | Trigger                                | Description                                                                                    |
| --- | -------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 1   | `demo-report.yml`    | Weekly Monday 6:00 UTC                 | Queries demo DB for P&L/signal count/hit rate, posts GitHub Issue                              |
| 2   | `deploy-landing.yml` | Push to main with `landing/**` changes | Build and deploy landing page to Vercel/Cloudflare                                             |
| 3   | `chaos-tests.yml`    | Weekly Saturday 2:00 UTC               | 5 chaos scenarios: TSDB latency, worker restart, buffer overflow, WS disconnect, total failure |

> **Note:** The plan calls for 7 separate workflows; the implementation has 4 (with `chaos-benchmark.yml` merging chaos + benchmark). The `benchmark.yml` as a standalone is also missing but partially covered by `chaos-benchmark.yml`.

---

## 4. Database — 2 Missing Tables
**Plan ref:** `02-database-schema.md`

| #   | Table                  | Status      | Note                                                                        |
| --- | ---------------------- | ----------- | --------------------------------------------------------------------------- |
| 1   | `order_flow_imbalance` | **MISSING** | V16 creates an index referencing it, but no `CREATE TABLE` exists           |
| 2   | `evt_risk_metrics`     | **MISSING** | V22 creates `risk_evt_parameters` instead; no hypertable `evt_risk_metrics` |

---

## 5. Docker & Infrastructure — 3 Missing Items
**Plan ref:** `11-deployment-operations.md`

| #   | Item                                       | Description                                                                                                              |
| --- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| 1   | `docker-compose.openbb.yml`                | OpenBB Platform sidecar for equity prices                                                                                |
| 2   | Chronicle Queue volume in docker-compose   | Dedicated NVMe/tmpfs volume for ingestion overflow                                                                       |
| 3   | **Spring Security / OAuth2 config** (Java) | Plan requires OAuth2+PKCE, all `/api/**` require JWT, HTTPS, CSP headers — no Spring Security dependency or config found |

> **Note:** Jaeger **is** present in `docker-compose.yml`. The landing page Dockerfile **does** use nginx (matching the plan). Backend Dockerfile **does** use multi-stage build.

---

## 6. Frontend (Dashboard) — 2 Missing Items
**Plan ref:** `07-analytics-dashboard.md`

| #   | Item                     | Description                                                                                          |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------- |
| 1   | **D3.js integration**    | Plan specifies D3.js for heatmaps; current implementation uses only Perspective + lightweight-charts |
| 2   | **Playwright e2e tests** | Plan specifies Playwright; only Vitest unit tests exist                                              |

> **Note:** Perspective **is** used (CorrelationMatrix, SignalLog, LiquidityHeatmap). Auth **is** implemented (PKCE flow in `frontend/lib/auth.ts`). The 40 planned dashboard components are all implemented.

---

## 7. Runbooks — 17 Missing
**Plan ref:** `11-deployment-operations.md` (21 planned, 4 exist)

**Existing runbooks** (`docs/runbooks/`):
- `deployment.md`, `monitoring-troubleshooting.md`, `data-management.md`, `README.md`

**Missing runbooks:**

| #   | Runbook                                  |
| --- | ---------------------------------------- |
| 1   | OpenBB Sidecar Setup                     |
| 2   | Analytics Worker Deployment              |
| 3   | Polygon WebSocket Outage Procedure       |
| 4   | Chronicle Queue Overflow Recovery        |
| 5   | ILI Weight Recalibration                 |
| 6   | Proxy Divergence Event Review            |
| 7   | TimescaleDB Continuous Aggregate Refresh |
| 8   | TA-Lib Adapter Integration               |
| 9   | Bulkhead Pool Monitoring                 |
| 10  | Distributed Tracing with Jaeger          |
| 11  | Calibration Task Monitoring              |
| 12  | Disaster Alert Verification              |
| 13  | Big Red Button Runbook                   |
| 14  | Systemic Resilience Monitor Runbook      |
| 15  | Regulatory Compliance Report Generation  |
| 16  | De-Rounding Filter Verification          |
| 17  | Options Data Pipeline Verification       |

---

## 8. Structural / Architectural Gaps

| #   | Gap                                      | Plan Ref                | Note                                                                                          |
| --- | ---------------------------------------- | ----------------------- | --------------------------------------------------------------------------------------------- |
| 1   | `webflux/` subproject                    | `01-scaffolding.md` §12 | WebFlux adapter module for future reactive runtime — **not created** (intentionally deferred) |
| 2   | `analytics/` Gradle subproject with Java | `settings.gradle`       | Listed in `settings.gradle` but contains only Python, no Java analytics bridge code           |
| 3   | `integration-tests/` subproject          | `01-scaffolding.md`     | Module exists in `settings.gradle` with Testcontainers config but **no test classes written** |

---

## Prioritized Summary

**Critical gaps** (core functionality missing):
1. **37 computation engine components** — the v4 governance, v5 liquidity, and v5.1 ML integration layers are entirely absent from Java
2. **Spring Security / OAuth2** — no API authentication or authorization
3. **Integration tests** — empty module, no integration test coverage

**Significant gaps** (operational completeness):
4. **3 CI/CD workflows** — demo reporting, landing deployment, chaos testing
5. **17 runbooks** — operational documentation at ~19% of plan
6. **7 ingestion components** — disaster alerts, sanity guard, cancellation monitor, event-based time

**Minor gaps** (partial coverage or intentional deferral):
7. **2 database tables** — `order_flow_imbalance` (dangling index), `evt_risk_metrics`
8. **D3.js + Playwright** — frontend uses alternative libraries
9. **Chronicle Queue** — replaced by simpler `FileOverflowBuffer`
10. **webflux/ module** — intentionally deferred per plan