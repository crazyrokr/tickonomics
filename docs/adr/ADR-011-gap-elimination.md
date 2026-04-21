# ADR-011: Plan v5 Gap Elimination

## Status

Accepted

## Date

2026-06-03

## Context

The gap analysis in `docs/plan_v5/gaps/` identified 67 items across 10 phases that were planned but not implemented. The gaps fell into three severity tiers:

- **Critical (3):** 37 computation engine components, Spring Security/OAuth2, integration test module
- **Significant (3):** 3 CI/CD workflows, 17 runbooks, 7 ingestion components (4 real + 3 intentional deferrals)
- **Minor (4):** 2 database tables, D3.js/Playwright, Chronicle Queue (replaced), webflux/ (deferred)

## Decision

Implement all non-deferred gaps following the dependency-ordered phase plan from `00-implementation-priority.md`.

### Items intentionally deferred

| Item | Reason |
|------|--------|
| `OpenBBClient` (Java) | Direct FRED/NY Fed clients serve the same purpose |
| `AnomalyDetectionWorker` (Java) | Python analytics worker handles anomaly detection |
| Chronicle Queue | Replaced by `FileOverflowBuffer`/`TieredIngestionBuffer` |
| `webflux/` subproject | Intentionally deferred per original plan |
| `analytics/` Java bridge | Python-only analytics worker sufficient |

### Database tables clarification

- `order_flow_imbalance` is a column on `tick_data` (added by V14), not a standalone table. The V16 index references this column correctly.
- `evt_risk_metrics` is functionally covered by `risk_evt_parameters` (V22). Created a SQL view alias for plan alignment (V28).

## Implementation Summary

### Phase 0 — Foundation

| Item | Files |
|------|-------|
| V28 migration | `persistence/src/main/resources/db/migration/V28__create_evt_risk_metrics_view.sql` |
| Spring Security | `web/src/main/java/.../web/config/SecurityConfig.java`, `SecurityProperties.java` |
| Security config | `app/src/main/resources/application.yml` (OAuth2 client + resource server) |
| Integration tests | `integration-tests/src/test/java/.../integration/AbstractIntegrationTest.java`, `ApplicationStartupIT.java` |

### Phase 1–6 — Computation Engine (37 components)

All 37 components implemented with full unit tests (82 Java files total).

| Phase | Package | Components |
|-------|---------|------------|
| 1 | `regime`, `ili`, `signal`, `governance`, `stress` | SessionRangeService, AmbiguityAdjustedIli, SurpriseIndicator, ParticipationGovernanceService, LiquidityStressTestModule |
| 2 | `optimization`, `ili`, `risk` | RegimeAwareWeightingService, ScheduledCalibrationTask, FireflyWeightOptimizer, ClimateSensitivityFactor, ClimateRiskGuard |
| 3 | `ingestion/external`, `ingestion/guard`, `ingestion/quality`, `ingestion/time` | DisasterAlertClient, AlgorithmicSanityGuard, OrderCancellationMonitor, EventBasedTimeConverter |
| 4 | `kpi`, `liquidity`, `risk`, `signal`, `session` | AlgorithmicIntensityMetric, LiquiditySourceClassifier, PhantomLiquidityService, ToxicityAdjustedIli, ComovementTrigger, TimeOfDayThresholdManager, DefiningRangeService, BehaviouralRiskProcessor, LiquidityMeanReversionSpeed, LiquidityPremiumFactor |
| 5 | `kpi`, `backtest`, `execution`, `leverage`, `pairs`, `portfolio`, `signal` | StrategicRunService, PriceImpactKpi, InformationEfficiencyAnalyzer, ComparativeExecutionAnalysis, LeverageSignaler, PairsTradingEngine, PortfolioManagementAlgebra, AlgorithmicBehaviorAlignment, SaliProcessor, SentimentVolatilityGuard |
| 6 | `backtest`, `signal`, `weight`, `kpi`, `risk`, `sensitivity` | WalkForwardValidator, CrossModelValidator, VotingClassifier, AgnosticAggregator, MarketEfficiencyMonitor, RiskPremiumResidualMonitor, MarketSensitivityLibrary |

### Phase 7 — CI/CD Workflows

| Workflow | File | Trigger |
|----------|------|---------|
| demo-report | `.github/workflows/demo-report.yml` | Weekly Monday 06:00 UTC |
| deploy-landing | `.github/workflows/deploy-landing.yml` | Push to main with `landing/**` |
| chaos-tests | `.github/workflows/chaos-tests.yml` | Weekly Saturday 02:00 UTC |

### Phase 8 — Frontend

| Item | File |
|------|------|
| D3.js heatmap | `frontend/components/charts/D3IliHeatmap.tsx` |
| D3.js heatmap test | `frontend/__tests__/components/charts/D3IliHeatmap.test.tsx` |
| Playwright config | `frontend/playwright.config.ts` |
| E2E tests | `frontend/e2e/dashboard.spec.ts` |

### Phase 9 — Runbooks (17)

All 17 missing runbooks created in `docs/runbooks/`.

### Docker Infrastructure

| Item | File |
|------|------|
| OpenBB sidecar | `docker-compose.openbb.yml` |

## Consequences

- All 67 planned items addressed (59 implemented, 5 deferred by design, 3 clarified as non-gaps)
- Build compiles and all existing + new tests pass
- `QuantControllerSpec` updated to exclude `SecurityAutoConfiguration` for `@WebMvcTest` compatibility
- Spring Security defaults to `auth-disabled: true` for development; production must set `AUTH_DISABLED=false` and configure a real OIDC provider
