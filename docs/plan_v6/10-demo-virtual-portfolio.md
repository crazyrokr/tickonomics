# Track 10: Demo / Verification with Virtual Portfolio

**Phase:** Phase 6
**Can start:** After Tracks 4 and 5 (ingestion + computation working)
**Blocks:** Nothing (final validation phase)
**Depends on:** Tracks 4 (live ingestion), 5 (signal generation), 6 (landing page integration)

---

## Objective

Implement a verification phase where the platform runs against real market data but trades
against a virtual balance and portfolio. Used to validate signal quality and system behavior
end-to-end before any real capital is at stake. Serves as both verification tool and public showcase.

**Analysis findings applied:**

- **Finding 3 (Proxy Divergence Guard):** `PaperTradingEngine` skips signals with `DISLOCATED`
  ILI status. Proxy-divergent periods are excluded from performance metrics and signal quality
  reports. The demo dashboard shows a "Proxy Dislocated" badge when active.
- **Finding 6 (Dynamic Weighting):** `DEGRADED_COMPONENT_STALE` signals are tracked separately
  in signal quality reports. Paper trading can optionally execute on degraded signals (configurable)
  but reports distinguish them from fully validated signals.

---

## Components to Implement

### 1. VirtualPortfolio

Simulated portfolio with virtual balance:

- **Initial balance:** configurable (default $100,000).
- Supports **market orders** (buy/sell) at signal price with configured slippage.
- Tracks **positions:** symbol, quantity, entry price, current price, unrealized P&L.
- Tracks **trade history:** timestamp, symbol, direction, quantity, fill price, commission.
- Calculates **portfolio-level metrics:**
    - Total P&L.
    - Realized P&L.
    - Unrealized P&L.
    - Number of trades.
    - Win rate.
    - Max drawdown.

### 2. PaperTradingEngine

- Listens to signals from `SignalGenerator` (same signals as production).
- For each `ACTIONABLE` signal, executes simulated trade via `VirtualPortfolio`.
- **Skips signals when ILI status is `DISLOCATED`** (Finding 3). These signals are logged but
  not executed in paper trading.
- **Configurable behavior for `DEGRADED_COMPONENT_STALE` signals** (Finding 6): by default
  executes (ILI is still valid with redistributed weights), but tracks them separately in
  reports. Set `demo.execute-degraded-signals: false` to skip.
- Applies transaction cost model (slippage + commission) from configuration.
- **Position sizing:** fixed percentage of portfolio per trade (configurable, default 5%).
- **Exit rules:**
    - Hold until opposing signal, OR
    - Configurable stop-loss (default 5%), OR
    - Configurable take-profit (default 10%).
- Marks trades as `PAPER` in signal log to distinguish from live signals.
- **Equity price data (v6):** Equity price data for `PaperTradingEngine` now comes from Yahoo Finance/Finnhub REST clients (v6 free data source migration).

### 3. SignalQualityReport

Daily automated report on signal performance:

- **Signals generated** vs. signals acted upon (filter-through rate).
- **Hit rate:** percentage of signals where price moved in expected direction within 1, 5, 10, 20 trading days.
- **Average return per signal** (by direction, by regime, by status).
- **False positive rate:** signals followed by adverse move > cost threshold.
- **Degraded vs. valid signal breakdown** (Finding 6): separate hit rates for fully validated
  signals vs. `DEGRADED_COMPONENT_STALE` signals.
- **Proxy divergence impact** (Finding 3): count of signals suppressed during dislocated periods,
  duration of dislocated intervals, and comparison of signal density with/without proxy.
- **ILI percentile distribution** at signal time (verify buy < 5%, sell > 95%).
- **Comparison:** signal-based portfolio vs. buy-and-hold SPY benchmark.
- Stored in TimescaleDB `signal_quality_reports`, accessible via API.

### 4. Demo Configuration

```yaml
monitor:
  demo:
    enabled: true
    virtual-balance: 100000
    position-size-pct: 5
    stop-loss-pct: 5
    take-profit-pct: 10
    report-interval: "1d"
    auto-execute-signals: true
    execute-degraded-signals: true         # Execute on DEGRADED_COMPONENT_STALE (Finding 6)
    skip-dislocated-signals: true          # Always skip DISLOCATED signals (Finding 3)
```

### 5. Demo Dashboard Page

Separate from main dashboard, accessible without auth:

- **Virtual portfolio summary:** balance, P&L chart, positions table.
- **Trade history:** chronological list with P&L per trade.
- **Signal quality metrics:** hit rate, false positive rate, regime breakdown.
- **"Live" indicator** showing system is processing real data.
- **"Virtual Trading" badge** on every page to distinguish from live trading.
- **"Proxy Dislocated" badge** shown during T-Bill/SOFR divergence (Finding 3).
- **"Degraded Data" badge** shown when ILI components have zero variance (Finding 6).
- **Disclaimer banner:** "This is a simulated portfolio using virtual funds. Not financial advice."

### 6. Landing Page Integration

- Virtual portfolio performance card embedded in landing page (via API).
- Recent demo trades shown in signal showcase section.
- Auto-updated daily via ISR revalidation.

API endpoints:

- `GET /api/v1/demo/portfolio` -- portfolio summary.
- `GET /api/v1/demo/trades` -- trade history.
- `GET /api/v1/demo/signal-quality` -- signal quality report.

### 7. Verification Criteria

Before graduating from demo:

| Criterion              | Threshold                                             |
|:-----------------------|:------------------------------------------------------|
| Minimum operation time | 90 calendar days                                      |
| Minimum paper trades   | 50                                                    |
| Signal hit rate        | > 55% (statistically significant, p < 0.05)           |
| System bugs            | Zero missed signals, zero data gaps > stale threshold |
| Portfolio Sharpe ratio | > 0.5 over demo period                                |
| Max drawdown           | < 20% of virtual balance                              |

All criteria configurable and tracked in TimescaleDB.

### 8. Demo Environment Deployment

- Runs on staging infrastructure with free API keys (FRED, Finnhub, Alpha Vantage).
- Seeds initial historical data from TimescaleDB archives (at least 1 year).
- Continuous ingestion and signal generation -- identical to production config.
- Auto-restarts on failure, monitored via health checks.

---

## Database Tables (from Track 2)

- `virtual_portfolio_positions` -- open positions.
- `virtual_portfolio_trades` -- trade history with `trade_type = 'PAPER'`.
- `signal_quality_reports` -- daily metrics with `verification_progress` JSONB.

---

## Module Structure

```
computation/
├── src/main/java/com/tickonomics/computation/
│   ├── demo/
│   │   ├── VirtualPortfolio.java
│   │   ├── PaperTradingEngine.java
│   │   ├── SignalQualityReport.java
│   │   ├── VerificationCriteria.java
│   │   └── DemoConfig.java
```

Frontend additions in `frontend/`:

```
frontend/
├── components/
│   └── demo/
│       ├── PortfolioSummary.tsx
│       ├── TradeHistory.tsx
│       ├── SignalQualityMetrics.tsx
│       ├── LiveIndicator.tsx
│       └── DisclaimerBanner.tsx
```

---

## Unit Tests (Given-When-Then)

- Given `ACTIONABLE` buy signal, When paper trading enabled, Then virtual position opened at signal price + slippage.
- Given open position with 5% loss, When stop-loss configured at 5%, Then position closed automatically.
- Given 50 paper trades, When 30 profitable, Then hit rate reported as 60%.
- Given demo mode disabled, When signal fires, Then no virtual trade executed.
- Given signal hit rate 50% over 10 trades, When verification checked, Then criteria NOT met (insufficient trades).
- Given `DISLOCATED` ILI status, When signal fires, Then paper trade NOT executed (Finding 3).
- Given `DEGRADED_COMPONENT_STALE` signal, When `execute-degraded-signals: true`, Then trade executed and tagged as
  degraded (Finding 6).
- Given `DEGRADED_COMPONENT_STALE` signal, When `execute-degraded-signals: false`, Then trade NOT executed (Finding 6).

---

## v4 Additions

### Climate Stress Scenarios in Demo (Proposal 03)

Implement climate stress scenarios in the demo environment to observe the impact of
hypothetical climate policy events on the `PaperTradingEngine` strategy.

**Scenarios:**

1. **Abrupt carbon-tax hike:** Simulates a sudden increase in carbon taxation via
   `POST /api/v1/climate/simulate` with elevated `carbon_tax` parameter. Observe how the
   projected liquidity impact affects signal generation and virtual portfolio positions.
2. **Massive clean energy investment event:** Simulates a large-scale green capital injection
   via `POST /api/v1/climate/simulate` with increased `adaptation_finance_increase` parameter.
   Observe energy mix shift and its downstream effect on funding market liquidity signals.

**Demo dashboard integration:**

- Climate scenario results displayed in a dedicated "Climate Stress" panel.
- Shows projected liquidity impact, energy mix shift breakdown, and resulting ILI movement.
- Compares virtual portfolio performance under normal vs. climate-stress conditions.

### Kill-Switch Verification (Proposal 02)

Demo environment validates kill-switch functionality as part of weekly demo validation.

**Kill-switch behavior verified:**

1. Emergency signal suppression works correctly -- all pending and future signals are blocked.
2. All open virtual positions can be immediately liquidated at current market price.
3. No new trades can be opened after kill-switch activation until explicitly re-enabled.

**Testing cadence:** Kill-switch tested weekly as part of the scheduled demo report workflow
(`demo-report.yml`). The test activates the kill-switch, verifies suppression, liquidates
positions, verifies no new trades, then re-enables signal execution.

### Disaster Alert Integration (v4)

Demo dashboard shows a disaster overlay when active disaster alerts exist in the
`disaster_alerts` table.

**Behavior:**

- When a `WARNING` or `CRITICAL` severity alert is active, the demo dashboard displays a
  disaster alert banner with alert type (Earthquake, Tsunami, Hurricane), severity, and
  geographic region.
- Tests that `DISLOCATED` ILI status correctly suppresses paper trades during disaster events.
- After the disaster alert clears, verifies that paper trading resumes normally and the
  disaster overlay is removed.

### Updated Demo Configuration (v4)

```yaml
monitor:
  demo:
    enabled: true
    virtual-balance: 100000
    position-size-pct: 5
    stop-loss-pct: 5
    take-profit-pct: 10
    report-interval: "1d"
    auto-execute-signals: true
    execute-degraded-signals: true
    skip-dislocated-signals: true
    climate_stress_scenarios:
      enabled: true
      schedule: "weekly"
    kill_switch_test:
      enabled: true
      schedule: "weekly"
    anomaly_detection:
      suppress_anomalous_data: true
```

**New configuration fields:**

- `climate_stress_scenarios.enabled`: enables climate stress scenario testing in demo.
- `climate_stress_scenarios.schedule`: how often climate stress scenarios run (default weekly).
- `kill_switch_test.enabled`: enables automated kill-switch testing in demo.
- `kill_switch_test.schedule`: how often kill-switch verification runs (default weekly).
- `anomaly_detection.suppress_anomalous_data`: when `true`, paper trades on data flagged
  `SUSPECT_ANOMALY` are optionally suppressed (configurable per demo environment).

---

## v5 Additions (Proposals 05, 06, 07)

### Enhanced Cost Model with Submission Impact (v5 -- Proposal 05)

Update `PaperTradingEngine` cost model to include tiered "Submission Impact" penalty:

- **Passive orders:** 0.84 bps
- **Aggressive orders:** 2.03 bps
- **Large orders (benchmark):** avg 9.04 bps
- This is in addition to the current slippage model.

**NBBO Midpoint Drift Simulation:** Adjust virtual entry price based on 10-second horizon post-submission quote drift
results from the Beason and Wahal (2021) paper.

### RandomizedExecutionWindow (v5 -- Proposal 05: Human Bias Mitigation)

Implement a `RandomizedExecutionWindow` for all virtual portfolio trades.

- To avoid price impact caused by human-biased spikes, all market orders in `PaperTradingEngine` execute within a
  randomized 0-60 second window after signal is received.
- Specifically avoids the "round-mark" danger zone identified by the DeRoundingFilter.

### MarketStabilityGuard (v5 -- Proposal 06: Market Stability Guardrails)

- Monitors aggregate portfolio volatility.
- "Circuit Breaker++" extends `PaperTradingEngine` circuit breaker beyond simple connectivity checks.
- Halts virtual trading if anomalous volatility spike is detected across the whole portfolio.
- Triggers within 100ms of detecting anomalous volatility.

### OrderImpactPredictor (v5 -- Proposal 06: Pre-Trade Impact)

- Pre-trade "Systemic Impact" check that simulates order impact on current liquidity.
- Runs a quick simulation before any order is submitted.
- Blocks orders estimated to cause adverse market impact beyond configurable threshold.

### MarketMakerExecutionModel (v5 -- Proposal 06: Algorithmic Price Stability)

- `PaperTradingEngine` execution strategy mimics "Market Making" behavior.
- Prefers limit orders to capture spread rather than aggressive market orders.
- Reduces system's contribution to price bubbles.
- `volatility_impact` metric in `SignalQualityReport` measures whether executing the signal attenuated or exacerbated
  volatility.

### Herding Filter & Panic Guard (v5 -- Proposal 07: Behavioural Liquidity Guard)

**Herding Filter:** Suppress signals if BRI indicates "Extreme Herding" (irrational momentum), as this often precedes a
reversal.

**Panic Guard:** Use BRI to trigger "Panic Mode" in `PaperTradingEngine`, automatically tightening stop-losses during
periods of high behavioural risk.

### FillProbabilityEngine Enhancement (v5 -- Proposal 07: Phantom Liquidity)

Refine `PaperTradingEngine` to use PLI as a "fill probability" filter:

- If PLI is high, simulate higher chance of "missed fills" or increased slippage.
- The liquidity seen at the time of the signal was likely "phantom".

### Impact-Sensitive Execution (v5 -- Proposal 07: Trader-Type Dynamics)

Adjust virtual execution model to account for "Maker-Taker" arrangements:

- Apply different slippage penalties based on whether virtual trade "takes" liquidity from a retail provider vs. an
  algorithmic maker.

### Liquidity-Aware Execution Mode (v5 -- Proposal 07: Intraday Pattern)

Add a "liquidity-aware" execution mode to `PaperTradingEngine`:

- For ACTIONABLE signals, execution is delayed by 1-2 intervals during high-asymmetry market open/close.
- Waiting allows the algorithm to "price in" the shock.

### News-Aware Backtesting (v5 -- Proposal 07: Intraday Session Filter)

- Replay historical signals alongside an economic calendar to verify accuracy drops during news events.
- Test 2:1 Risk-Reward Ratio (RRR) for intraday trades initiated after a Defining Range breach.

### Human-in-the-Loop Toggle (v5 -- Proposal 06: Algorithm Aversion)

- `requires_approval` flag in `monitor.demo` configuration.
- "Manual Confirmation" mode for `PaperTradingEngine`: user must approve a signal before trade is logged.
- Provides the "Sense of Control" identified in research as critical for algorithm acceptance.

### Mistake Attribution in Signal Quality Reports (v5 -- Proposal 06: Algorithm Aversion)

- In `SignalQualityReport`, explicitly attribute missed hits to "Data Anomalies" (external) or "Logic Bounds" (
  internal).
- Shows the system is "self-aware" of its limitations.

### Dual Execution Evaluation (v5 -- Proposal 06: MarketMaker vs Sniper)

- A/B test mode comparing market-making execution vs. sniper execution.
- Measures spread capture rate and volatility impact for each mode.
- Market-making mode should produce lower volatility impact.

### Updated Demo Configuration (v5)

```yaml
monitor:
  demo:
    enabled: true
    virtual-balance: 100000
    position-size-pct: 5
    stop-loss-pct: 5
    take-profit-pct: 10
    report-interval: "1d"
    auto-execute-signals: true
    execute-degraded-signals: true
    skip-dislocated-signals: true
    climate_stress_scenarios:
      enabled: true
      schedule: "weekly"
    kill_switch_test:
      enabled: true
      schedule: "weekly"
    anomaly_detection:
      suppress_anomalous_data: true
    # v5 additions
    advanced_cost_model:
      enabled: true
      submission_impact:
        passive_bps: 0.84
        aggressive_bps: 2.03
        large_order_bps: 9.04
      nbbo_drift_simulation: true
    randomized_execution:
      enabled: true
      window_seconds: 60
      avoid_round_marks: true
    market_stability_guard:
      enabled: true
      volatility_threshold_pct: 5
      trigger_latency_ms: 100
    order_impact_predictor:
      enabled: true
      max_impact_threshold_bps: 10
    market_maker_mode:
      enabled: false  # A/B test with sniper mode
      prefer_limit_orders: true
      spread_capture_target_pct: 60
    behavioural:
      herding_filter: true
      panic_guard: true
      panic_stop_loss_tightening_pct: 2
    liquidity_aware:
      enabled: true
      open_close_delay_intervals: 2
    human_in_the_loop:
      enabled: false
      requires_approval: false
```

### v5 Additions (Proposals 08, 09)

#### Leverage Rotation in Demo (v5 -- Proposal 08)

- `VirtualPortfolio` integrates with `LeverageSignaler` for automated position scaling.
- When `LEVERAGE_OFF` signal fires, virtual portfolio rotates from leveraged equity into Treasury bills.
- Verifies that leverage rotation truncates tail risk in demo period.

#### State-Dependent Exits (v5 -- Proposal 08)

- `PaperTradingEngine` supports state-dependent stop-loss and take-profit from `MarkovStopEngine`.
- Stop levels adjust in real-time based on calibrated thresholds from `/stops/calibrate` endpoint.
- Compares fixed-stop vs. dynamic-stop Sharpe ratios in demo report.

#### Dual Portfolio Comparison (v5 -- Proposal 08)

- Two virtual portfolios run side-by-side: passive execution (limit orders at ILI fair price) vs. aggressive execution (
  market orders at threshold crossing).
- `execution_type` column on `virtual_portfolio_trades` and `signal_log` distinguishes the two portfolios.
- Slippage vs. fill rate trade-off quantified in demo report.

#### Portfolio Algebra Integration (v5 -- Proposal 08)

- Margin call equation integrated into `VirtualPortfolio` rebalancing engine.
- Standardized cost model: `Trading costs = (t0 + t1)P + sP`.
- Consistent cost calculations across all execution handlers.

#### Sentiment-Suppressed Signals (v5 -- Proposal 09)

- `PaperTradingEngine` suppresses signals when sentiment contradicts ILI direction.
- SALI-weighted position sizing: `position_size *= (1 + sentiment_weight * sentiment_score)`.
- During contradictory news, signals downgraded from `ACTIONABLE` to `SPECULATIVE`.

### Updated Demo Configuration (v5)

```yaml
monitor:
  demo:
    leverage_rotation:
      enabled: true
      ma_window_days: 200
      benchmark_symbol: "SPY"
    dynamic_stops:
      enabled: false  # Phase 2
      recalibration_interval: "7d"
    dual_portfolio:
      enabled: false  # A/B test mode
      passive_handler: "PassiveExecutionHandler"
      aggressive_handler: "SniperExecutionHandler"
    portfolio_algebra:
      use_standardized_cost_model: true
    sentiment:
      enabled: true
      engine: "lexicon"  # or "finbert" (Phase 2)
      confirmation_mode: "and_gate"  # or "weighted"
      weight: 0.3
```

### Module Structure additions (v5)

```
│   ├── demo/
│   │   ├── VirtualPortfolio.java
│   │   ├── PaperTradingEngine.java
│   │   ├── SignalQualityReport.java
│   │   ├── VerificationCriteria.java
│   │   ├── DemoConfig.java
│   │   ├── MarketStabilityGuard.java           # v5 (Proposal 06)
│   │   ├── OrderImpactPredictor.java            # v5 (Proposal 06)
│   │   ├── MarketMakerExecutionModel.java       # v5 (Proposal 06)
│   │   ├── RandomizedExecutionWindow.java       # v5 (Proposal 05)
│   │   ├── FillProbabilityEngine.java           # v5 (Proposal 07)
│   │   ├── PassiveExecutionHandler.java         # v5 (Proposal 08)
│   │   ├── SniperExecutionHandler.java          # v5 (Proposal 08)
│   │   └── MarkovStopHandler.java               # v5 (Proposal 08)
```

---

## Validation

- [ ] Virtual portfolio tracks balance, positions, and P&L correctly.
- [ ] Paper trading engine executes trades only for `ACTIONABLE` signals.
- [ ] Stop-loss triggers automatically at configured percentage.
- [ ] Take-profit triggers automatically at configured percentage.
- [ ] Trade history persisted to TimescaleDB with `PAPER` marker.
- [ ] Signal quality report generates daily with correct metrics.
- [ ] Hit rate calculation matches manual verification.
- [ ] Verification criteria checked correctly against configurable thresholds.
- [ ] Demo dashboard page renders without authentication.
- [ ] Landing page integration shows live demo data.
- [ ] Demo environment runs continuously without intervention.
- [ ] Disclaimer banner visible on all demo pages.

**v4 additions:**

- [ ] Climate stress scenarios produce measurable impact on virtual portfolio (carbon-tax and clean energy scenarios).
- [ ] Kill-switch immediately suppresses all signals and liquidates all open positions.
- [ ] Kill-switch prevents new trades after activation until explicitly re-enabled.
- [ ] Disaster alerts cause `DISLOCATED` ILI status and suppress paper trades during active disasters.
- [ ] Disaster overlay shows on demo dashboard when `WARNING` or `CRITICAL` alerts are active.
- [ ] Anomalous data (`SUSPECT_ANOMALY` flag) optionally suppresses paper trades when `suppress_anomalous_data: true`.

**v5 additions:**

- [ ] Tiered submission impact penalties applied correctly (passive 0.84 bps, aggressive 2.03 bps, large 9.04 bps).
- [ ] Virtual portfolio transaction costs correlate with "Large Order" benchmarks (avg 9.04 bps).
- [ ] Randomized execution window keeps trade execution times within 0-60s and avoids round-mark clusters.
- [ ] MarketStabilityGuard triggers within 100ms of detecting anomalous portfolio volatility.
- [ ] OrderImpactPredictor blocks orders exceeding configurable impact threshold.
- [ ] No false-positive circuit breaker triggers during normal market operations.
- [ ] Market-maker mode produces lower volatility impact than sniper mode in backtests.
- [ ] Spread capture rate exceeds 60% in normal market conditions when market-maker mode enabled.
- [ ] Herding filter suppresses signals during extreme BRI herding periods.
- [ ] Panic guard tightens stop-losses during high behavioural risk periods.
- [ ] Fill probability decreases linearly with increasing PLI.
- [ ] Liquidity-aware execution delays signals during high-asymmetry open/close periods.
- [ ] Human-in-the-loop mode holds trades pending user approval.
- [ ] Mistake attribution correctly classifies missed hits as "Data Anomalies" or "Logic Bounds".

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|:--------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Initial plan: VirtualPortfolio, PaperTradingEngine, SignalQualityReport, demo dashboard, landing page integration, verification criteria. Applied Findings 3 and 6 for proxy divergence and dynamic weighting.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v2      | No structural changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| v3      | No structural changes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| v4      | Added Climate Stress Scenarios in Demo (Proposal 03): carbon-tax hike and clean energy investment scenarios using `POST /api/v1/climate/simulate` with dashboard panel. Added Kill-Switch Verification (Proposal 02): weekly automated testing of emergency suppression, position liquidation, and trade blocking. Added Disaster Alert Integration: disaster overlay on demo dashboard during active alerts, verification of DISLOCATED ILI status suppressing paper trades. Added `anomaly_detection.suppress_anomalous_data` configuration option. Updated demo configuration with `climate_stress_scenarios`, `kill_switch_test`, and `anomaly_detection` sections. Added 6 validation criteria for v4 features. |
| v5      | `10-demo-virtual-portfolio.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Added from Proposals 05, 06, 07: Enhanced cost model with tiered submission impact and NBBO drift simulation (Proposal 05). RandomizedExecutionWindow avoiding round-mark danger zones (Proposal 05). MarketStabilityGuard with Circuit Breaker++ (Proposal 06). OrderImpactPredictor pre-trade impact check (Proposal 06). MarketMakerExecutionModel preferring limit orders (Proposal 06). Herding Filter and Panic Guard using BRI (Proposal 07). FillProbabilityEngine enhanced with PLI (Proposal 07). Impact-sensitive execution for maker-taker arrangements (Proposal 07). Liquidity-aware execution mode with open/close delay (Proposal 07). News-aware backtesting with economic calendar (Proposal 07). Human-in-the-loop toggle (Proposal 06). Mistake attribution in signal quality reports (Proposal 06). Dual execution A/B evaluation (Proposal 06). 14 new validation criteria. |
| v5.1    | `10-demo-virtual-portfolio.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Added from Proposals 08, 09: Leverage rotation with automated position scaling (Proposal 08). State-dependent exits from MarkovStopEngine (Proposal 08). Dual portfolio comparison: passive vs aggressive execution (Proposal 08). Portfolio management algebra with standardized cost model (Proposal 08). Sentiment-suppressed signals with SALI-weighted position sizing (Proposal 09). New handlers: PassiveExecutionHandler, SniperExecutionHandler, MarkovStopHandler. New configuration sections: leverage_rotation, dynamic_stops, dual_portfolio, portfolio_algebra, sentiment. 6 new validation criteria. |
| v6      | `10-demo-virtual-portfolio.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Updated demo environment deployment: replaced "real API keys (OpenBB, Polygon)" with "free API keys (FRED, Finnhub, Alpha Vantage)". Added note that equity price data for PaperTradingEngine now comes from Yahoo Finance/Finnhub REST clients (v6 free data source migration). No structural changes to demo components or validation criteria. |
