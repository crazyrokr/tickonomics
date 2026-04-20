# Implementation Priority by Dependencies

Each phase must complete before the next begins. Within a phase, items can be parallelized.

---

## Phase 0 — Foundation (blocking everything)

These are prerequisites that all subsequent layers depend on.

| Priority | Item | From | Why first |
|----------|------|------|-----------|
| 0.1 | `order_flow_imbalance` table | [04-database](04-database-tables.md) | V16 already has a dangling index referencing it; v5 liquidity components write to it |
| 0.2 | `evt_risk_metrics` hypertable | [04-database](04-database-tables.md) | Risk services query this; EVT framework is a dependency for `RiskPremiumResidualMonitor` |
| 0.3 | Spring Security / OAuth2+PKCE | [05-docker-infra](05-docker-infra.md) | All `/api/**` endpoints are unauthenticated; every new service adds endpoints |
| 0.4 | `integration-tests/` module — write first test class | [08-structural](08-structural-gaps.md) | No integration coverage exists; every new component needs integration verification |

---

## Phase 1 — Regime & ILI Primitives (v4)

Computation engine components that are *inputs* to everything else.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 1.1 | `SessionRangeService` | Produces session labels (Asian/EU/US); consumed by `TimeOfDayThresholdManager`, `RegimeAwareWeightingService` |
| 1.2 | `AmbiguityAdjustedIli` | Extends ILI with UNCERTAIN status; required by `ToxicityAdjustedIli`, `SaliProcessor` |
| 1.3 | `SurpriseIndicator` | Entropy scoring on ILI output; consumed by `BehaviouralRiskProcessor` and v5.1 ML validators |
| 1.4 | `ParticipationGovernanceService` | Signal decomposition + admissibility; gates all downstream signal processors |
| 1.5 | `LiquidityStressTestModule` | Stress scenario calibration; feeds into `RegimeAwareWeightingService` thresholds |

---

## Phase 2 — Optimization & Climate (v4)

Depends on Phase 1 regime/ILI outputs.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 2.1 | `RegimeAwareWeightingService` | Consumes `SessionRangeService` output; produces weight vectors used by v5 aggregators |
| 2.2 | `ScheduledCalibrationTask` | Periodic task that recalibrates weights; depends on `RegimeAwareWeightingService` |
| 2.3 | `FireflyWeightOptimizer` | Alternative optimizer for weight search; runs inside `ScheduledCalibrationTask` |
| 2.4 | `ClimateSensitivityFactor` | Adjusts ILI thresholds; consumed by `ClimateRiskGuard` and v5 liquidity adjusters |
| 2.5 | `ClimateRiskGuard` | Wraps `ClimateSensitivityFactor`; feeds confidence adjustments to ILI pipeline |

---

## Phase 3 — Ingestion Guards (v4)

Real-time ingestion safety nets — independent of computation but needed before v5 liquidity processing.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 3.1 | `AlgorithmicSanityGuard` | Detects >10% flash moves, >10k msg/sec; triggers regime overrides that affect ILI |
| 3.2 | `DisasterAlertClient` | Produces EXOGENOUS_SHOCK regime; consumed by `LiquidityStressTestModule` triggers |
| 3.3 | `EventBasedTimeConverter` | Maps ticks to directional change events; feeds `AlgorithmicIntensityMetric` |
| 3.4 | `OrderCancellationMonitor` | Cancellation ratio metric; consumed by `BehaviouralRiskProcessor` |

---

## Phase 4 — Liquidity Layer (v5 Core)

The largest single block. All items consume Phase 1–3 outputs.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 4.1 | `AlgorithmicIntensityMetric` | Consumes `EventBasedTimeConverter`; produces AT proxy quintiles used by 4.3–4.6 |
| 4.2 | `LiquiditySourceClassifier` | Trader type estimation; feeds `BehaviouralRiskProcessor` and `PhantomLiquidityService` |
| 4.3 | `PhantomLiquidityService` | PLI calculation; depends on `AlgorithmicIntensityMetric` + `LiquiditySourceClassifier` |
| 4.4 | `ToxicityAdjustedIli` | Depends on `AmbiguityAdjustedIli` (Phase 1); consumes `PhantomLiquidityService` |
| 4.5 | `ComovementTrigger` | Liquidity comovement factor; feeds `DefiningRangeService` |
| 4.6 | `TimeOfDayThresholdManager` | Consumes `SessionRangeService`; adjusts thresholds consumed by 4.7–4.9 |
| 4.7 | `DefiningRangeService` | DR-based confidence; depends on `ComovementTrigger` + `TimeOfDayThresholdManager` |
| 4.8 | `BehaviouralRiskProcessor` | BRI with systemic risk axis; consumes `SurpriseIndicator`, `OrderCancellationMonitor`, `LiquiditySourceClassifier` |
| 4.9 | `LiquidityMeanReversionSpeed` | AT lagged quality; depends on `AlgorithmicIntensityMetric` |
| 4.10 | `LiquidityPremiumFactor` | AT intensity premium; depends on `AlgorithmicIntensityMetric` + `LiquidityMeanReversionSpeed` |

---

## Phase 5 — Core Analytics (v5)

Trade execution and signal quality — depends on liquidity layer.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 5.1 | `StrategicRunService` | Groups child orders; feeds `PriceImpactKpi` |
| 5.2 | `PriceImpactKpi` | NBBO midpoint; depends on `StrategicRunService` order grouping |
| 5.3 | `InformationEfficiencyAnalyzer` | Price Jump Ratio; depends on `PriceImpactKpi` data |
| 5.4 | `ComparativeExecutionAnalysis` | Passive vs aggressive execution; depends on `PriceImpactKpi` |
| 5.5 | `LeverageSignaler` | 200-day MA rotation; independent, but feeds `PortfolioManagementAlgebra` |
| 5.6 | `PairsTradingEngine` | Minimum-distance pairs; independent, but its output feeds `PortfolioManagementAlgebra` |
| 5.7 | `PortfolioManagementAlgebra` | Margin algebra; depends on `LeverageSignaler`, `ComparativeExecutionAnalysis` |
| 5.8 | `AlgorithmicBehaviorAlignment` | Time-decay proxy; depends on `AlgorithmicIntensityMetric` from Phase 4 |
| 5.9 | `SaliProcessor` | 70/30 ILI-sentiment blend; depends on `AmbiguityAdjustedIli` + sentiment API |
| 5.10 | `SentimentVolatilityGuard` | BERT threshold adjustment; depends on `SaliProcessor` output |

---

## Phase 6 — ML Validation (v5.1)

Requires all Phase 1–5 outputs for training/tournament data.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 6.1 | `WalkForwardValidator` | Anchored WFA; needs full ILI signal history from Phases 1–5 |
| 6.2 | `CrossModelValidator` | Tournament ILI vs XGBoost vs LSTM; depends on `WalkForwardValidator` for fair splits |
| 6.3 | `VotingClassifier` | ML confirmation filter; depends on `CrossModelValidator` model outputs |
| 6.4 | `AgnosticAggregator` | Regret-min weight aggregation; consumes `CrossModelValidator` predictions |
| 6.5 | `MarketEfficiencyMonitor` | Gap indicator; depends on `InformationEfficiencyAnalyzer` |
| 6.6 | `RiskPremiumResidualMonitor` | Q-world validation; depends on `evt_risk_metrics` table (Phase 0) + `PhantomLiquidityService` |
| 6.7 | `MarketSensitivityLibrary` | Greeks nomenclature; depends on `LiquidityPremiumFactor` + `RiskPremiumResidualMonitor` |

---

## Phase 7 — CI/CD & Infrastructure

Needs stable codebase from Phases 0–6.

| Priority | Item | From |
|----------|------|------|
| 7.1 | `demo-report.yml` weekly workflow | [03-cicd](03-cicd-workflows.md) |
| 7.2 | `deploy-landing.yml` push workflow | [03-cicd](03-cicd-workflows.md) |
| 7.3 | `chaos-tests.yml` weekly workflow | [03-cicd](03-cicd-workflows.md) |

---

## Phase 8 — Frontend Completion

Needs backend APIs from Phases 1–6.

| Priority | Item | From |
|----------|------|------|
| 8.1 | D3.js heatmap integration | [06-frontend](06-frontend-dashboard.md) |
| 8.2 | Playwright e2e tests | [06-frontend](06-frontend-dashboard.md) |

---

## Phase 9 — Runbooks

Documentation for all operational procedures; can only be accurate after implementation is stable.

| Priority | Runbook | Reason it's here |
|----------|---------|------------------|
| 9.1 | Big Red Button Runbook | Emergency ops — document first among runbooks |
| 9.2 | Polygon WebSocket Outage Procedure | Critical path dependency |
| 9.3 | Systemic Resilience Monitor Runbook | Depends on `BehaviouralRiskProcessor` (Phase 4) |
| 9.4 | Bulkhead Pool Monitoring | Depends on Spring Security (Phase 0) |
| 9.5 | Distributed Tracing with Jaeger | Infra runbook, no code deps |
| 9.6 | ILI Weight Recalibration | Depends on `ScheduledCalibrationTask` (Phase 2) |
| 9.7 | Calibration Task Monitoring | Depends on `ScheduledCalibrationTask` (Phase 2) |
| 9.8 | Proxy Divergence Event Review | Depends on `RiskPremiumResidualMonitor` (Phase 6) |
| 9.9 | Disaster Alert Verification | Depends on `DisasterAlertClient` (Phase 3) |
| 9.10 | TimescaleDB Continuous Aggregate Refresh | DB ops |
| 9.11 | Analytics Worker Deployment | Deployment |
| 9.12 | OpenBB Sidecar Setup | Depends on OpenBB decision |
| 9.13 | TA-Lib Adapter Integration | Integration guide |
| 9.14 | De-Rounding Filter Verification | Filter verification |
| 9.15 | Options Data Pipeline Verification | Pipeline verification |
| 9.16 | Regulatory Compliance Report Generation | Compliance |
| 9.17 | Chronicle Queue Overflow Recovery | May be obsolete (replaced by FileOverflowBuffer) |

---

## Dependency Graph (simplified)

```
Phase 0: Foundation
  ├── Phase 1: Regime & ILI (v4)
  │     ├── Phase 2: Optimization & Climate (v4)
  │     └── Phase 3: Ingestion Guards
  │           └── Phase 4: Liquidity Layer (v5)
  │                 └── Phase 5: Core Analytics (v5)
  │                       └── Phase 6: ML Validation (v5.1)
  ├── Phase 7: CI/CD
  ├── Phase 8: Frontend
  └── Phase 9: Runbooks
```

## Component Count by Phase

| Phase | Components | Cumulative |
|-------|-----------|------------|
| 0 — Foundation | 4 | 4 |
| 1 — Regime & ILI | 5 | 9 |
| 2 — Optimization & Climate | 5 | 14 |
| 3 — Ingestion Guards | 4 | 18 |
| 4 — Liquidity Layer | 10 | 28 |
| 5 — Core Analytics | 10 | 38 |
| 6 — ML Validation | 7 | 45 |
| 7 — CI/CD | 3 | 48 |
| 8 — Frontend | 2 | 50 |
| 9 — Runbooks | 17 | 67 |

**Total: 67 items across 10 phases.**
