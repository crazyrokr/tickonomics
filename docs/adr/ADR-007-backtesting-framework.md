# ADR-007: Backtesting Framework Architecture

**Date:** 2026-05-30
**Status:** Accepted

## Context

The computation engine has 25 equity strategies and 30 options strategies that need systematic validation against historical data. The existing `DelayDExecutor` was a bare POJO with no slippage integration, no trade log, and no persistence. There was no mechanism to replay historical data, run strategy backtests at scale, validate parameter robustness, stress-test against crisis scenarios, or ensure reproducibility of results.

## Decision

Introduce a layered backtesting framework in the `computation` module:

1. **EquityStrategyRegistry** — Central lookup for all 25 equity strategies, mirroring the existing options `StrategyRegistry` pattern. Accepts `UniverseAggregator` for strategies that require universe context.

2. **DelayDExecutor** (fixed) — Now accepts `Eq553SlippageModel` via constructor injection. Each trade populates a `tradeLog` entry with signal return, execution return, slippage in basis points, and adjusted return. Liquidity fragility is detected per-run via `Eq553SlippageModel.isLiquidityFragile()`.

3. **HistoricalDataReplay** — Wraps `TickDataRepository` for symbol-level and universe-level historical data replay. Computes daily returns from tick data by extracting last-price-per-day.

4. **BacktestEngine** — Central orchestrator: replay data, feed each strategy via registry, run `DelayDExecutor`, persist results to `backtest_results` and `alpha_signals` tables.

5. **WeightOptimizer** — Delegates Sobol quasi-random parameter scans to the Python analytics worker (`/api/v1/backtest/robustness-scan`) via `RestClientAnalyticsWorkerClient`.

6. **MarketStressSimulator** — Wraps existing `AumfScenarioEngine` crisis profiles (COVID-2020, SNB-2015, BlackMonday-1987). Applies volatility and spread multipliers to baseline backtest results.

7. **ReproducibilityService** — Captures git SHA (from `tickonomics.git-sha` property), dataset hash (SHA-256), and RDS score (from Python worker) for each backtest run.

8. **BarrierHittingTimeAnalyzer** — Uses `DiscreteMonitoringCorrection` (Broadie-Kou-Glasserman beta=0.5826) to adjust continuous-time barrier approximations for discrete monitoring.

All components are `@Component` with constructor injection. Persistence uses `NamedParameterJdbcTemplate` following existing repository patterns. V26 migration seeds 25 equity strategy definitions.

## Consequences

- All backtest runs are stateless: no in-memory state between invocations.
- Sobol parameter sweeps require Python analytics worker availability.
- Existing Spock tests for `DelayDExecutor` updated to use new constructor.
- 43 new unit tests cover all backtest components with Given-When-Then structure.
- `Eq553SlippageModel` is now the single source of truth for slippage computation across backtesting and future paper trading.
