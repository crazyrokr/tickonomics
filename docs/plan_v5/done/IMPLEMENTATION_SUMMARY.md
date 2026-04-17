# Quantitative Engine Implementation Summary

**Date:** 2026-05-23
**Spec:** RUNBOOK_QUANTITATIVE_ENGINE.md v5.3.0-INSTITUTIONAL-SPEC
**Build status:** All Java tests pass, all 15 Python tests pass

---

## Architecture

```
api-contracts → cdm → persistence → ingestion, computation → web → app
                                                         ↑
                                              analytics (Python FastAPI, REST IPC)
integration-tests → app
```

---

## Module inventory (136 files total)

**cdm (8 Java files)** — Shared domain model

- Enums: InstrumentType, OptionType, DayCountConvention
- Records: CdmOptionSnapshot (17 fields with Greeks + BigDecimal strike), CdmRateSnapshot, CdmTick
- Compact constructors with validation (strike > 0, ask >= bid, finite values)
- 8 tests in CdmOptionSnapshotTest, CdmRateSnapshotTest

**persistence (7 SQL migrations V19–V25)** — TimescaleDB hypertables

- V19: strategy_definitions + alpha_signals (7-day chunks)
- V20: intersubjective_audit_log (1-day chunks)
- V21: option_chain_snapshots (1-day chunks)
- V22: macro_shock_irfs + risk_evt_parameters (30-day chunks)
- V23: quantile_coefficients + synergy_entropy_matrix (30-day chunks)
- V24: universe_stats_history (1-day chunks)
- V25: Performance indexes for all quant engine tables

**computation (87 Java files)** — Core engine

- strategy/ (4 files): Strategy, BaseStrategy<T>, StrategyContext, AlphaSignal (pre-existing)
- audit/ (4 files): CodingRule enum, AuditEntry record, IntersubjectiveAuditService (@Component, SHA-256 hashing, IR
  score gating at 0.90)
- options/ (33 files): StrategyType enum (30 types), LegGroup record, LegMatchService (butterfly/condor/vertical
  matching with strike tolerance), BaseOptionStrategy abstract class, 30 concrete strategies (BullCallSpread through
  CalendarStraddle), StrategyRegistry
- equity/ (29 files): UniverseContext record, UniverseAggregator (AtomicReference, 1000-symbol cap), EquityStrategyType
  enum (25 types), BaseEquityStrategy abstract class, 25 concrete strategies (MomentumDecile through IchimokuCloud)
- fixedincome/ (5 files): FixedIncomeStrategyType enum, BondPosition record, FixedIncomePortfolio record,
  FixedIncomePortfolioBuilder (bullet/barbell/duration-neutral)
- backtest/ (5 files): ExecutionDelay enum, BacktestResult record, DelayDExecutor (Delay-0 vs Delay-1, fragility
  detection at 3.0 Sharpe ratio), Eq553SlippageModel (zeta=0.15, volume-scaled)

**ingestion (2 Java files)** — Data quality

- kernel/KernelAggregator: Realized variance with Tukey-Hanning and Parzen kernels, autocovariance computation

**web (4 Java files)** — REST API

- config/WebConfig: CORS for /api/**
- controller/QuantController: 7 endpoints under /api/v1/quant/ (signals/active, strategies/active,
  strategies/options/butterfly, risk/tail-parameters, risk/evt-tail, audit/intersubjective-reproducibility/{id},
  macro/shock-response)
- controller/HealthController: GET /health

**app (1 Java file)** — Spring Boot entry point

- TickonomicsApplication: @SpringBootApplication
- application.yml: virtual threads enabled, PostgreSQL/TimescaleDB, Flyway, analytics worker URL

**analytics (31 Python files)** — Statistical sidecar

- FastAPI app on port 8001 with 7 routers
- services/statistical/evt_risk_service: GPD fitting via scipy.stats.genpareto, tail VaR at 99.9%
- services/statistical/multiple_testing_service: BH-FDR via statsmodels
- services/statistical/yield_curve_service: Nelson-Siegel via scipy.optimize.curve_fit
- services/statistical/macro_shock_service: TVP-SVAR IRF via statsmodels VARMAX
- services/statistical/quantile_regression_service: QR bands via statsmodels QuantReg
- services/statistical/transfer_entropy_service: TE with bootstrap permutation test
- 15 tests covering all services with Given-When-Then structure

---

## Key runbook mandates enforced

1. **IR Score Gating:** No signal transitions to ACTIONABLE if ir_score < 0.90 (BaseOptionStrategy.compute,
   IntersubjectiveAuditService)
2. **Universe Mean MANDATE (Eq 293):** All equity strategies use shared UniverseAggregator (no independent mean
   calculation)
3. **Day-Count Conventions:** ACT/365 for equity options, ACT/360 for IR, ACT/ACT for Treasuries (CdmOptionSnapshot +
   DayCountConvention enum)
4. **Volume-Scaled Slippage (Eq 553):** Eq553SlippageModel with zeta=0.15, marks strategies as LIQUIDITY_FRAGILE when
   adjusted return < 0
5. **Leg Symmetry Constraint:** Butterfly requires K2 = (K1+K3)/2 within 0.01 tolerance (LegMatchService)
6. **FDR False-Positive Control:** BH-FDR correction prevents p-hacking in strategy selection

---

## Build configuration

- Spring Boot 3.5.0, Java 25, Gradle multi-module
- `-parameters` compiler flag for Spring MVC parameter name resolution
- dependency-management plugin with Spring Boot BOM
- Resilience4j 2.3.0 for ingestion circuit breaker

---

## Not yet implemented (deferred from runbook)

- V1–V18 SQL migrations (core platform tables from 02-database-schema.md)
- Arrow IPC transport optimization (Phase 7 optimization)
- Christian-Christoffersen VaR validation (Phase 9 Python)
- Kubernetes Helm charts for HA deployment (Phase 10)
- Morning pre-flight / signal dislocation protocols (Phase 10 operational)
