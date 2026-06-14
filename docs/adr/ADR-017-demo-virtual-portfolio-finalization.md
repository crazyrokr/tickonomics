# ADR-017: Demo / Virtual Portfolio Finalization

**Date:** 2026-06-13
**Status:** Accepted
**Track:** 10 — Demo / Virtual Portfolio (Phase 6)
**Supersedes / extends:** ADR-009

## Context

`docs/plan_v6-verification-report.md` scored Track 10 at **~40%**: only the v1 paper-trading core
existed (`VirtualPortfolio`, `PaperTradingEngine`, `SignalQualityAnalyzer`, `DemoController`,
`DemoConfig`). Four classes of gap remained:

1. **Island components built but never consumed** — `LeverageSignaler`, `PortfolioManagementAlgebra`,
   `ComparativeExecutionAnalysis`, `ReturnGapCalculator`, and the Python `markov_stop_service`
   (whose `markov_stop_calibrations` table had no Java reader).
2. **`SignalQualityAnalyzer` was v1-thin** — it wrote `null` to every analytic column the schema
   already provided (`hit_rate_1d/5d/10d/20d`, `false_positive_rate`, `avg_return_per_signal`,
   `vs_spy_return`) and had no degraded/valid split, return-gap, or mistake attribution.
3. **No kill-switch / Big Red Button** — runbook-only; zero Java matches.
4. **v5 execution model absent** — `RandomizedExecutionWindow`, `MarketStabilityGuard`,
   `OrderImpactPredictor`, `MarketMakerExecutionModel`, `FillProbabilityEngine`, `MarkovStopHandler`,
   `PassiveExecutionHandler`, `SniperExecutionHandler` were unbuilt, and the demo dashboard frontend
   (`frontend/components/demo/`) did not exist.

This ADR records the finalization that closed gaps 1–4 (scope: *Core + v5 execution model*), bringing
Track 10 to substantially complete.

## Decision

### 1. Price lookups behind a port, not a hard DB coupling

Introduced `MarketPriceLookup` (interface in `computation.demo`) with `closingPrice(symbol, day)` and
`closingPrices(symbol, from, to)`. `OhlcvDailyMarketPriceLookup` reads the existing `ohlcv_1d`
continuous aggregate via a new `OhlcvDailyRepository` (`(candle).close`). The port backs hit-rate,
vs-SPY, and leverage rotation, and is mocked in unit tests so domain logic never depends on the
persistence layer.

### 2. No database migration

Hit-rate-by-horizon, false-positive rate, average return, and vs-SPY populate the existing nullable
columns on `signal_quality_reports`. Return gap, volatility-impact proxy, mistake attribution
(`DATA_ANOMALY` / `LOGIC_BOUNDS`), and the signal breakdown ride in the existing
`verification_progress` JSONB. `MarkovStopHandler` reads the pre-existing `markov_stop_calibrations`
hypertable via a new `MarkovStopCalibrationRepository`.

### 3. Wiring the islands

- `LeverageSignaler` → `VirtualPortfolio.applyLeverageRotation(...)`: on `LEVERAGE_OFF` flattens all
  open positions at current prices (rotate to cash/Treasuries); exposed via
  `POST /api/v1/demo/leverage-rotation/evaluate`.
- `PortfolioManagementAlgebra` → `PaperTradingEngine` applies the standardized cost model
  `Trading costs = (t0 + t1)P + sP` (t0/t1 from the submission-impact bps) to each opening trade.
- `ComparativeExecutionAnalysis` → every executed trade records passive-vs-sniper `ExecutionSlippage`
  samples; `PaperTradingEngine.executionComparison()` surfaces the recommendation.
- `ReturnGapCalculator` → `SignalQualityAnalyzer` reports execution alpha/drag (portfolio return vs
  buy-and-hold SPY) into `verification_progress`.
- `MarkovStopHandler` → when `dynamic-stops.enabled`, calibrated SL/TP override the fixed config
  levels at position open.

### 4. Kill-switch is an in-memory operational toggle

`KillSwitch` holds a volatile active flag; `PaperTradingEngine` short-circuits on `isActive()`, and
`activate()` liquidates open positions at supplied prices. Endpoints under
`/api/v1/demo/kill-switch/{activate,deactivate,status}`. State is intentionally volatile: on restart
it returns to inactive, which is safe because demo trading is opt-in
(`monitor.demo.enabled: false`). A persistent kill-switch would require a new table and is deferred.

### 5. v5 execution model

Eight new stateless, individually unit-tested components (`RandomizedExecutionWindow`,
`MarketStabilityGuard`, `OrderImpactPredictor`, `MarketMakerExecutionModel`, `FillProbabilityEngine`,
`MarkovStopHandler`, `PassiveExecutionHandler`, `SniperExecutionHandler`) gate or shape each trade in
`PaperTradingEngine`, all config-gated and no-op by default where speculative. An extensible
`ExecutionContext` (recent returns, PLI, bid/ask, liquidity) carries the v5 inputs so the original
3-arg `processSignal` signature stays compatible.

### 6. Configuration: single canonical constructor

`DemoConfig` gained nine nested config records (`AdvancedCostModel`, `RandomizedExecution`,
`MarketStabilityGuard`, `OrderImpactPredictor`, `MarketMakerMode`, `DynamicStops`,
`PortfolioAlgebra`, `LeverageRotation`, `KillSwitchConfig`). Rather than a second constructor (which
Spring Boot's record property binding rejects, raising `NoSuchMethodException` at startup — verified
and fixed during finalization), a static `DemoConfig.core(...)` factory fills the v5 blocks with
defaults while keeping call sites concise. Spring binds via the single canonical constructor.

### 7. Demo dashboard frontend

A new `/demo` route (no auth) mounts `PortfolioSummary`, `TradeHistory`, `SignalQualityMetrics`,
`LiveIndicator`, `DisclaimerBanner`, and `DemoBadge`, backed by TanStack Query hooks
(`useDemoData.ts`) and typed API client functions added to `lib/api.ts`.

## Consequences

- Track 10 moves from ~40% to substantially complete; every previously-orphaned island component now
  has a consumer, the signal-quality report is analytic, the kill-switch is real, and the demo UI
  exists.
- Full Given-When-Then coverage for all new code; existing demo tests updated for the new
  collaborators and `core()` factory.
- The integration test (`ApplicationStartupIT`) confirms the context boots with all new beans, the
  YAML binding, and the repositories under Testcontainers.
- **Deferred** (out of scope, documented for a later pass): climate-simulate endpoint, disaster
  overlay, sentiment-suppressed signals, dual-portfolio A/B runtime, BRI herding/panic guard, and
  the speculative backtest-only v5 items (Track 8 overlap). Kill-switch persistence and the
  `volatility_impact` metric (currently a return-gap/spread proxy) are also deferred.
