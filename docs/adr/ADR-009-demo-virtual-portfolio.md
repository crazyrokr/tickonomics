# ADR-009: Demo / Virtual Portfolio Architecture

**Date:** 2026-05-31
**Status:** Accepted
**Track:** 10 — Demo / Virtual Portfolio (Phase 6)

## Context

Track 10 requires a paper-trading system that bridges the existing `SignalGenerator` output to a virtual portfolio with 90-day signal quality verification. The database schema already exists in V6 migration (`virtual_portfolio_positions`, `virtual_portfolio_trades`, `signal_quality_reports`). The system must support manual and automatic trade execution, mark-to-market P&L tracking, stop-loss/take-profit exits, and signal quality reporting.

## Decision

### Architecture

Four new components in the `computation/demo` package:

1. **DemoConfig** — `@ConfigurationProperties(prefix="monitor.demo")` with opt-in defaults (`enabled: false`, `autoExecuteSignals: false`). Controls virtual balance, position sizing, SL/TP percentages, and data quality gating.

2. **VirtualPortfolio** — `@Service` managing position lifecycle. Stateless: queries the database on every operation for crash safety. Computes realized P&L for both BUY (exit > entry) and SELL (entry > exit) positions. Stop-loss/take-profit checks respect position direction.

3. **PaperTradingEngine** — `@Service` bridging `SignalGenerator` output to `VirtualPortfolio`. Gates on config flags, ILI data status (DISLOCATED → skip, DEGRADED → configurable), signal status (only ACTIONABLE trades). Applies `Eq553SlippageModel` to fill price. Handles opposing-direction position flips (close old + open new).

4. **SignalQualityAnalyzer** — `@Service` computing portfolio-level metrics: realized P&L, Sharpe ratio, win rate, max drawdown. Generates 90-day verification progress JSONB with five criteria (operation days ≥ 90, trades ≥ 50, hit rate ≥ 55%, Sharpe ≥ 0.50, max drawdown ≤ 20% of capital).

### Database Changes (V27)

- Added `direction TEXT NOT NULL DEFAULT 'BUY'` and `closed_at TIMESTAMPTZ` to `virtual_portfolio_positions`
- Added partial index on open positions (`WHERE closed_at IS NULL`)
- Added trade lookup indexes and signal quality report date index

### REST API

Seven endpoints under `/api/v1/demo/` exposing portfolio summary, open positions, trade history, signal quality, manual trade execution, manual position close, and exit evaluation.

### Design Trade-offs

| Decision | Rationale |
|----------|-----------|
| Stateless portfolio (query DB each time) | Crash-safe, no in-memory state drift. Throughput acceptable for demo use case. |
| No `@Scheduled` polling in MVP | Reduces concurrency complexity. Engine invoked explicitly via controller. |
| Single portfolio (no dual-portfolio) | MVP scope. Dual passive/aggressive comparison deferred. |
| `closed_at` column vs. status enum | Minimal schema change. Follows `resolved_at` pattern in `proxy_divergence_events`. |
| Conservative defaults | `enabled: false`, `auto-execute-signals: false`. Feature is opt-in. |

## Consequences

- Demo features require explicit configuration to activate
- All portfolio state persists through application restarts
- 90-day verification criteria are computed on report generation, not continuously
- Future enhancements: scheduled price refresh, dual-portfolio comparison, hit-rate computation with actual price lookups
