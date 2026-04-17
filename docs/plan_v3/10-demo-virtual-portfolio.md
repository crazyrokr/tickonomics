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

- `GET /api/v1/demo/portfolio` — portfolio summary.
- `GET /api/v1/demo/trades` — trade history.
- `GET /api/v1/demo/signal-quality` — signal quality report.

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

- Runs on staging infrastructure with real API keys (OpenBB, Polygon).
- Seeds initial historical data from TimescaleDB archives (at least 1 year).
- Continuous ingestion and signal generation — identical to production config.
- Auto-restarts on failure, monitored via health checks.

---

## Database Tables (from Track 2)

- `virtual_portfolio_positions` — open positions.
- `virtual_portfolio_trades` — trade history with `trade_type = 'PAPER'`.
- `signal_quality_reports` — daily metrics with `verification_progress` JSONB.

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
