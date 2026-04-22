# Backtesting Framework Assessment for Empirical Validation

## Frameworks Evaluated

| Framework | License | Language | Cost | Decision |
|-----------|---------|----------|------|:--------:|
| **VectorBT** | Apache 2.0 (core) / Commercial (PRO) | Python | Free (core) | ✅ **INCLUDED** |
| **Backtrader** | GPL v3 | Python | Free | ❌ Dropped — unmaintained, Python 3.12 risk |
| **Zipline Reloaded** | Apache 2.0 | Python | Free | ❌ Not applicable |
| **Lean / QuantConnect** | Apache 2.0 | C# + Python | Free (community) | ❌ Not applicable |
| **TradingView / Pine Script** | Proprietary | Pine Script | Freemium | ❌ No |
| **QuantConnect Platform** | Proprietary SaaS | C# + Python | Free tier limited | ❌ No |
| **RoboForex StrategyQuant** | Commercial | Java GUI | Paid | ❌ No |
| **StrategyQuant** | Commercial | Java GUI | Paid | ❌ No |

---

## Detailed Assessment

### VectorBT — RECOMMENDED as Primary Oracle

**Why it fits the Tickonomics validation suite:**

1. **Vectorized architecture** — represents entire backtests as NumPy arrays, accelerated by Numba and optional Rust kernels. Can sweep thousands of parameter configurations in seconds. This is critical for validating the optimizer, robustness scan, and stop-loss calibration services which all require parameter sweeps.

2. **Built-in data integration** — `vbt.YFData.download("SPY")` fetches data directly via yfinance. No extra data pipeline needed. Aligns perfectly with the empirical validation plan's data acquisition step.

3. **Portfolio simulation** — `vbt.Portfolio.from_signals()` simulates portfolios with entry/exit signals, commission models, slippage, and position sizing. This provides an independent oracle for:
   - VaR/CVaR backtesting (compute rolling P&L, count exceptions)
   - Stop-loss strategy simulation (compare markov_stop_service output)
   - Optimizer weight delta (apply computed weights, compare portfolio Sharpe)
   - Regime strategy comparison (compare returns in HIGH_VOL vs NORMAL regimes)

4. **Performance metrics** — computes Sharpe, Sortino, max drawdown, win rate, Calmar, and 50+ other metrics independently. These serve as oracles for the `performance_service` functions.

5. **Walk-forward splitting** — `vbt.range_split(n=k)` splits data into k segments for out-of-sample validation. Directly supports the walk-forward methodology needed for GARCH and VaR validation.

6. **Zero conflict with existing stack** — uses pandas + NumPy, same as the analytics worker. Apache 2.0 license. `pip install vectorbt` adds it cleanly.

**Limitations:**
- Core (free) version does NOT include portfolio optimization, limit orders, or leverage modeling — those are PRO-only. But the free core is sufficient for all validation steps in the plan.
- No built-in GARCH, EVT, or Nelson-Siegel models — it's a backtesting engine, not a statistical library. The validation suite still needs scipy/statsmodels for those.

**Specific validation steps VectorBT enables:**

| Plan Step | VectorBT Usage |
|-----------|----------------|
| Step 1 (VaR backtest) | `vbt.Portfolio.from_holding()` to compute rolling P&L, then count VaR exceptions |
| Step 2 (GARCH accuracy) | `vbt.MA.run()` for naive baseline comparison; VectorBT computes realized vol from price data |
| Step 10 (Sobol convergence) | `vbt.Portfolio.from_signals()` at multiple n_paths to verify convergence |
| Step 14 (Regime detection) | `vbt.range_split()` to segment returns by regime, compare strategy returns per segment |
| Step 15 (Stop-loss) | `vbt.Portfolio.from_signals()` with stop-loss and take-profit exits via `stop_loss` and `take_profit` params — independent oracle for markov_stop_service |
| Step 17 (Transfer entropy) | `vbt.MA.run()` crossovers as known-causal signal source |
| Basel traffic light | VectorBT computes daily returns, portfolio values — feed into VaR service for exception counting |

### Backtrader — DROPPED (Python 3.12 risk, unmaintained)

**Why it was considered:** Event-driven execution with slippage/commission simulation, 122 indicators, built-in analyzers.

**Why it was dropped:**
- **Unmaintained since ~2021** — no Python 3.12 testing, may fail to install
- **Requires matplotlib** even if you never plot
- Only 2 of 20 test steps would use it
- A 30-line numpy helper function covers the same ground without the risk

**Replacement:** Custom `execution_simulator.py` helper (see Step 20 below)

### Zipline Reloaded — NOT RECOMMENDED

**Reasons:**
- Requires NASDAQ Data Link (formerly Quandl) API key for data ingestion (`zipline ingest`). This adds an external dependency and registration step that yfinance + VectorBT avoids entirely.
- Designed for equity strategy research, not for validating analytics services. Its `handle_data()` / `initialize()` API is optimized for writing trading algorithms, not for computing VaR, GARCH, or Nelson-Siegel.
- Heavy installation footprint: requires `exchange-calendars`, `bcolz-zipline`, `trading-calendars`, and specific pandas/NumPy version constraints that may conflict with the analytics worker's stack (numpy 2.4.6, pandas 3.0.3).
- No clear advantage over VectorBT for any validation step in the plan.

### Lean / QuantConnect — NOT RECOMMENDED

**Reasons:**
- **C# engine** with Python support as a secondary interface. The Python API is less complete and less documented than the C# API.
- **Cloud-dependent data** — the free data library requires a QuantConnect account and runs through their cloud platform. Local backtesting requires manual data bundle setup.
- **19,000+ GitHub stars but 5,000+ forks** suggests an active community, but the installation is heavy (requires .NET SDK, Mono on Linux).
- Designed for full algorithmic trading systems, not focused validation of specific analytics functions. No benefit over VectorBT for the validation use case.

### TradingView / Pine Script — NOT RECOMMENDED

**Reasons:**
- **Proprietary language** — Pine Script runs only inside TradingView's platform. Cannot be executed locally or in CI/CD.
- **Not programmable via API** — strategies run in TradingView's web UI. No Python API to run backtests programmatically.
- **Freemium limits** — free tier has restrictions on strategy complexity, data access, and alert frequency.
- Cannot be integrated into the `pytest` test suite.

### QuantConnect Platform — NOT RECOMMENDED

**Reasons:**
- SaaS platform — strategies run on QuantConnect's cloud, not locally.
- Free tier has backtesting time limits and data access restrictions.
- Not suitable for automated testing in a CI/CD pipeline.
- Lean (the engine) can be self-hosted, but the setup complexity outweighs any benefit over VectorBT.

### RoboForex StrategyQuant — NOT RECOMMENDED

**Reasons:**
- **Commercial product** — not free.
- **Java GUI application** — no Python API, no programmatic access.
- **Forex-focused** — designed for MetaTrader integration, not for Python analytics validation.
- Cannot be integrated into the pytest test suite.

### StrategyQuant — NOT RECOMMENDED

**Reasons:**
- **Commercial product** — €999+ license. Not free.
- **Java-based GUI** — no Python API.
- Not applicable to programmatic validation.

---

## Updated Plan: Integrating VectorBT and Backtrader

### New dependencies to add

```
# analytics/requirements.txt additions:
yfinance>=0.2.40
pandas>=3.0.0
vectorbt>=0.26.0
backtrader>=1.9.78.123
```

### Step 0.6 — VectorBT data integration

Replace the custom `data_loader.py` yfinance-only approach with VectorBT's built-in data fetching:

```python
import vectorbt as vbt

# Single line replaces 30+ lines of custom yfinance code
price = vbt.YFData.download("SPY", start="2010-01-01", end="2025-12-31").get("Close")
returns = price.pct_change().dropna().values.tolist()
```

### New Step 19 — VectorBT Portfolio Backtesting Oracle

This is the key addition. VectorBT provides an independent, industry-tested portfolio simulation that validates the platform's trading advice end-to-end.

**19.1 VaR Exception Backtest via VectorBT**

```python
import vectorbt as vbt

# Given: SPY prices downloaded via VectorBT
price = vbt.YFData.download("SPY", start="2010-01-01", end="2025-12-31").get("Close")
returns = price.pct_change().dropna()

# For each rolling 252-day window, compute VaR via the platform's service,
# then check if next-day return exceeds VaR.
# VectorBT provides the independent return computation.
window = 252
for i in range(window, len(returns)):
    hist_returns = returns.iloc[i-window:i].values.tolist()
    var_result = value_at_risk(hist_returns, confidence=0.99, method="historical")
    actual_return = returns.iloc[i]
    # Count exception if actual < -var
```

**19.2 Stop-Loss Strategy Backtest via VectorBT**

```python
# VectorBT provides independent stop-loss/take-profit simulation
# This validates markov_stop_service's recommendations
price = vbt.YFData.download("SPY", start="2020-01-01", end="2025-12-31").get("Close")

# Generate entries (e.g., buy on first day of each month)
entries = price.groupby(price.index.to_period('M')).apply(lambda x: pd.Series(False, index=x.index))
# Set first bar of each month to True
entries_resampled = price.resample('MS').first().reindex(price.index, method='ffill')
entries = entries_resampled == price
entries = entries.shift(1).fillna(False)

# Apply stop-loss and take-profit from the platform's calibration
stop_loss_level = -0.023  # from markov_stop_service output
take_profit_level = 0.045  # from markov_stop_service output

pf = vbt.Portfolio.from_signals(
    price,
    entries=entries,
    exits=entries.shift(5),  # exit after 5 days if no SL/TP hit
    stop_loss=stop_loss_level,
    take_profit=take_profit_level,
    freq='1D'
)
# Compare pf.sharpe_ratio() to the service's reported best_sharpe
```

**19.3 Walk-Forward Regime Segmentation via VectorBT**

```python
# Split returns into regimes based on the platform's regime detection
# Then compare strategy performance per regime
price = vbt.YFData.download("SPY", start="2010-01-01", end="2025-12-31").get("Close")

# Use VectorBT's range_split for out-of-sample validation
split_price, split_index = price.vbt.range_split(n=10)  # 10 non-overlapping periods

# Run the same strategy on each split independently
pf = vbt.Portfolio.from_holding(split_price, init_cash=100000)
# Verify that HIGH_VOL periods have lower returns than NORMAL periods
```

**19.4 Performance Metrics Cross-Validation via VectorBT**

```python
# VectorBT computes Sharpe, Sortino, max drawdown independently
pf = vbt.Portfolio.from_holding(price, init_cash=100000, freq='1D')

# These must match the platform's performance_service output
assert abs(pf.sharpe_ratio() - platform_sharpe) < 0.01
assert abs(pf.sortino_ratio() - platform_sortino) < 0.01
assert abs(pf.max_drawdown() - platform_max_dd) < 0.01
```

### New Step 20 — Numpy Execution Simulation (replaces Backtrader)

Create `analytics/tests/empirical/execution_simulator.py` — a lightweight 50-line helper that replaces Backtrader's event-driven execution with simple numpy:

```python
def simulate_with_stops(prices, stop_loss, take_profit, commission_bps=10, slippage_bps=5):
    """Simulate a buy-and-hold strategy with stop-loss and take-profit exits.
    Re-enters after exit on the next bar.
    Returns: dict with final_value, sharpe, max_drawdown, n_trades
    """
    import numpy as np
    cash = 100000.0
    shares = 0
    entry_price = 0.0
    equity_curve = []
    trade_returns = []

    for i in range(len(prices)):
        price = prices[i] * (1 + slippage_bps / 10000)  # slippage on entry
        if shares == 0:
            shares = int(cash / price)
            cash -= shares * price * (1 + commission_bps / 10000)
            entry_price = price
        else:
            pnl_pct = (price - entry_price) / entry_price
            if pnl_pct <= -stop_loss or pnl_pct >= take_profit:
                exit_price = price * (1 - slippage_bps / 10000)
                cash += shares * exit_price * (1 - commission_bps / 10000)
                trade_returns.append(pnl_pct)
                shares = 0
        equity_curve.append(cash + shares * price)

    equity = np.array(equity_curve)
    returns = np.diff(equity) / equity[:-1]
    sharpe = np.mean(returns) / np.std(returns, ddof=1) * np.sqrt(252)
    max_dd = np.max(1 - equity / np.maximum.accumulate(equity))
    return {"final_value": equity[-1], "sharpe": sharpe, "max_drawdown": max_dd, "n_trades": len(trade_returns)}


def simulate_delta_hedge(prices, strikes, T, r, sigma, commission_bps=10):
    """Simulate delta-hedging a short call position daily.
    Returns: list of daily hedging P&L values
    """
    from app.services.greeks.bsm_service import bsm_greeks
    hedge_pnl = []
    prev_hedge_shares = 0

    for i in range(1, len(prices)):
        spot = prices[i]
        tau = max(T - i / 252, 1 / 252)
        greeks = bsm_greeks(spot, strikes, tau, r, sigma, "call")
        target_shares = -greeks["delta"] * 100  # short call → hedge with stock
        trade_shares = target_shares - prev_hedge_shares
        cost = abs(trade_shares) * spot * commission_bps / 10000
        daily_pnl = prev_hedge_shares * (prices[i] - prices[i-1]) - cost
        hedge_pnl.append(daily_pnl)
        prev_hedge_shares = target_shares

    return hedge_pnl
```

**Usage in tests:**

```python
# Step 15 alternative: validate stop-loss advice
result = simulate_with_stops(spy_prices, stop_loss=0.023, take_profit=0.045)
assert result["sharpe"] > 0  # platform's advice should at least be profitable after costs

# Step 4 alternative: validate delta hedging
pnl = simulate_delta_hedge(spy_prices, strikes=spy_prices[0], T=30/252, r=0.05, sigma=0.2)
assert abs(np.mean(pnl)) < 0.05 * spy_prices[0] * 100  # hedging error < 5% of premium
```

---

## Final Verdict: What Goes Into the Plan

| Tool | Role | Steps |
|------|------|-------|
| **VectorBT** (core, free) | Primary oracle for portfolio simulation, walk-forward splits, performance metrics, and VaR exception counting | Steps 1, 2, 8, 10, 14, 15, 19 (new) |
| **Custom numpy helpers** | Execution simulation with slippage/commission (replaces Backtrader) | Steps 4, 15, 20 (new) |
| **scipy/statsmodels/numpy** (already installed) | Statistical oracles for GARCH, EVT, yield curve, ADF, OLS, quantile regression | Steps 2–7, 9, 11, 16–18 |
| yfinance + pandas | Data fetching infrastructure | Step 0 |

**Not included:** Backtrader (dropped — Python 3.12 risk, unmaintained), Zipline Reloaded, Lean, TradingView, QuantConnect Platform, StrategyQuant, RoboForex — all excluded per the assessment above.

### Dependencies to Add

```
# analytics/requirements.txt additions:
yfinance>=0.2.40
pandas>=3.0.0
vectorbt>=0.26.0
# No backtrader — replaced by custom execution_simulator.py (50 lines of numpy)
```
