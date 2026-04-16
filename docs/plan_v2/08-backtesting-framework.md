# Track 8: Backtesting Framework

**Phase:** Phase 5
**Can start:** After Track 5 (computation engine exists)
**Blocks:** Nothing (standalone framework)
**Depends on:** Track 5 (computation engine)

---

## Objective

Implement a backtesting framework that can replay historical data through the computation
pipeline, simulate trading strategies, optimize ILI weights via Bayesian optimization, and
generate comprehensive performance reports.

**Analysis findings applied:**

- **Finding 3 (Proxy Divergence Guard):** Backtest replays proxy divergence events from
  `proxy_divergence_events` table. Dislocated periods correctly suppress proxy-derived signals.
  Report includes proxy divergence impact analysis.
- **Finding 6 (Dynamic Weighting):** Backtest exercises dynamic weight redistribution when
  components have zero variance. Verifies that `DEGRADED_COMPONENT_STALE` periods produce
  valid ILI values with redistributed weights. WeightOptimizer operates over the constrained
  weight space respecting the dynamic redistribution logic.

---

## Components to Implement

### 1. HistoricalDataReplay

- Loads historical data from TimescaleDB:
    - Rate snapshots (SOFR, EFFR, RRP, IORB, etc.).
    - Equity prices from OHLCV aggregates.
    - ILI history.
- Replays through the computation pipeline at configurable speed.
- Supports date range selection.
- Produces deterministic output given fixed input data.
- **Proxy divergence replay (Finding 3):** Replays `proxy_divergence_events` entries. During
  dislocated periods, proxy-derived data is suppressed and only official SOFR values are used,
  matching production behavior.
- **Dynamic weight replay (Finding 6):** Replays periods where components had zero variance.
  Verifies `IliCalculator` correctly applies weight redistribution and records `active_weights`
  in backtest output.

### 2. BacktestEngine

- Takes strategy config and date range.
- Replays day-by-day:
    1. Advance to next trading day.
    2. Feed data through `IliCalculator` → `SignalGenerator`.
    3. Generate signals as if in real-time (respect look-ahead bias prevention).
    4. Track simulated portfolio: positions, P&L, trades.
- Outputs:
    - Sharpe ratio.
    - Max drawdown (computed via `TA_MAX` / `TA_MIN` on cumulative return series).
    - Win rate.
    - Profit factor.
    - Signal frequency.
    - Equity curve.
    - Drawdown curve.
- Results persisted to `backtest_results` TimescaleDB table.

### 3. WeightOptimizer

- **Bayesian optimization** over ILI weight space (`w1`, `w2`, `w3` subject to `w1 + w2 + w3 = 1`).
- Fitness evaluation: Sharpe ratio from `BacktestEngine`.
- **Dynamic weight awareness (Finding 6):** Optimizer evaluates how weight redistribution
  during zero-variance periods affects overall strategy performance. Candidate weights are
  tested with the same dynamic redistribution logic used in production.
- Cross-validated via:
    - `TalibAdapter` (in-process).
    - FinanceToolkit `sharpe_ratio` (analytics worker).
    - OpenBB `obb.quantitative.sharpe_ratio` (analytics worker).
- Maximizes backtest Sharpe ratio.
- Outputs: optimal weights, corresponding Sharpe, parameter sensitivity analysis.
- Calibration plan: quarterly backtest recalibrates weights.

### 4. Backtest Report Page (Frontend)

- Equity curve chart.
- Drawdown chart.
- Signal timeline with annotations.
- Parameter comparison table (multiple backtest runs).
- Accessible via `GET /api/v1/backtest/{id}`.

---

## Configuration

Backtesting-specific configuration:

```yaml
monitor:
  backtest:
    default_date_range:
      start: "2023-01-01"
      end: "2025-12-31"
    position_sizing:
      method: "fixed_pct"
      fixed_pct: 5.0
    transaction_costs:
      slippage_bps: 5
      commission_per_share: 0.005
    optimization:
      method: "bayesian"
      n_iterations: 100
      weight_constraint: "sum_to_one"
      cross_validate: true
```

---

## Module Structure

```
computation/
├── src/main/java/com/tickonomics/computation/
│   ├── backtest/
│   │   ├── BacktestEngine.java
│   │   ├── HistoricalDataReplay.java
│   │   ├── SimulatedPortfolio.java
│   │   ├── WeightOptimizer.java
│   │   ├── BacktestResult.java
│   │   └── BacktestConfig.java
```

---

## Validation

- [ ] Given known data and fixed parameters, verify deterministic output.
- [ ] Given 1 year of daily data, backtest completes in < 60 seconds.
- [ ] Sharpe ratio matches manual calculation.
- [ ] Max drawdown correctly computed from cumulative return series.
- [ ] Weight optimizer converges to near-optimal weights within 100 iterations.
- [ ] Cross-validated Sharpe agrees across TA-Lib, FinanceToolkit, and OpenBB.
- [ ] Look-ahead bias prevention works in backtest mode (no future data leaked).
- [ ] Transaction costs correctly deducted from simulated returns.
- [ ] Backtest results correctly persisted to TimescaleDB.
- [ ] Backtest report page renders equity curve and drawdown chart.
- [ ] Proxy divergence events replayed correctly — dislocated periods suppress proxy signals (Finding 3).
- [ ] Dynamic weight redistribution tested — zero-variance periods produce valid ILI with redistributed weights (Finding
  6).
- [ ] Weight optimizer accounts for dynamic redistribution in fitness evaluation (Finding 6).
- [ ] Backtest report includes proxy divergence impact analysis section.
