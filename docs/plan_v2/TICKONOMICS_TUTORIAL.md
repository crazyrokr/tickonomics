# Tickonomics: User Tutorial

Welcome to Tickonomics. This guide will help you understand how to ingest data, interpret the Liquidity Index (ILI)
signals, and translate those signals into actionable real-world trading strategies.

---

## 1. Glossary

* **ILI (Liquidity Index):** A composite metric derived from Repo rates, RRP volumes, and volatility to assess systemic
  liquidity stress.
* **Proxy Divergence:** A state where short-term Treasury (T-Bill) yields and SOFR rates decouple, often indicating a "
  flight to quality" event.
* **Zero-Variance Component:** A state where an input component (e.g., RRP) stops changing, signaling a data staleness
  or market freeze.
* **AIC Lag Selection:** An automated statistical method used to determine the optimal historical window for Granger
  causality tests.
* **Slippage:** The difference between the expected price of a trade and the price at which the trade is executed.

---

## 2. System Inputs & Outputs

### Input Data Required

To run the system, provide the following via environment variables or configuration files:

1. **API Keys:** FRED, NY Fed, and Polygon.io credentials.
2. **Configuration:**
    * `symbols`: List of equities to track (e.g., `SPY`, `TLT`).
    * `weights`: ILI component weighting (RRP, Spread, Vol).
    * `thresholds`: Percentiles (default 5% / 95%) for signal generation.

### System Output

1. **Dashboard Visuals:** Real-time ILI charts, Liquidity Heatmaps, and Correlation Matrices.
2. **Signals:** Actionable trade alerts via WebSocket.
3. **Performance Reports:** Daily verification reports from the Virtual Portfolio engine.

---

## 3. Dashboard Legend

| Status                        | Meaning                                                      |
|:------------------------------|:-------------------------------------------------------------|
| **`ACTIONABLE`**              | High-confidence signal; cost-justified.                      |
| **`SPECULATIVE_STALE_MACRO`** | Signal relies on intraday T-Bill proxy; await official SOFR. |
| **`DISLOCATED`**              | Funding stress detected; automated signals suppressed.       |
| **`DEGRADED`**                | Component data stale; ILI calculated with adjusted weights.  |
| **`COOLDOWN`**                | Strategy cooldown period active; ignore signal.              |

---

## 4. How to Use Tickonomics: Step-by-Step

### Step 1: System Initialization

Ensure the backend is running (`virtual-threads` profile) and the Analytics Worker is connected. Verify in the **System
Health Panel** that all sources (FRED, NY Fed, Polygon) show "Healthy."

### Step 2: Monitoring Market Context

Observe the **Data Freshness Panel**.

* If `ILI Status` is **`VALID`**, proceed to Step 3.
* If `ILI Status` is **`DEGRADED`**, review the `active_weights` footnote to see which component is missing.
* If `ILI Status` is **`DISLOCATED`**, **do not trade**. The market is in a stress event where historical relationships
  are broken.

### Step 3: Translating Signals to Action

1. **Wait for `ACTIONABLE` Signal:** Only consider signals marked `ACTIONABLE`.
2. **Check Cost-Justification:** Review the `estimated_cost` vs. `expected_move`.
    * *Rule:* `Expected Move` must be > `2 * Estimated Cost`.
3. **Confirm Market Regime:** Check the **Volatility Regime Indicator**.
    * If `High`, widen your stop-loss and reduce position size.
    * If `Low`, you may tighten your thresholds.
4. **Execute:**
    * If signal is **BUY**: The liquidity backdrop is over-extended (ILI < 5th percentile); accumulate position.
    * If signal is **SELL**: The market is overheated or liquidity is evaporating (ILI > 95th percentile); trim or
      hedge.

---

## 4. How to Choose Correct ILI Weights

The default weights (`rrp: 0.4, spread: 0.4, vol: 0.2`) provide a balanced view, but market conditions may require
recalibration. Follow this methodology:

1. **Analyze Historical Regimes:** Use the `RegimeDetector` data to see how your chosen weights perform during "High"
   vs "Low" volatility regimes.
2. **Backtest Sensitivity:** Run the `WeightOptimizer` (Bayesian optimization) over a 1-year historical window. Compare
   the resulting Sharpe ratio of the optimized weights against the default.
3. **Component Contribution:**
    * If you find your strategy is too sensitive to **RRP spikes**, consider reducing `rrp` weight.
    * If **SOFR/T-Bill spreads** are your primary signal driver, prioritize the `spread` weight.
4. **Recalibration Cycle:** Recalibrate weights quarterly or whenever a major change occurs in the Federal Reserve’s
   balance sheet policy.

*Always apply new weights in Demo Mode first to observe signal density changes before promoting to production.*

---

## 5. Verification Checklist (Before Real Capital)

*   [ ] Run the system in Demo Mode for 90 days.
*   [ ] Achieve > 55% signal hit rate.
*   [ ] Verify max drawdown in Virtual Portfolio is < 20%.
*   [ ] Ensure no automated signals were triggered during `DISLOCATED` states.

*Disclaimer: Tickonomics is an automated liquidity analysis tool. Not financial advice.*
