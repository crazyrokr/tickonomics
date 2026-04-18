# Detailed Technical Specification: 151 Trading Strategies Integration

This document provides a deep-dive analysis and refined task descriptions for integrating the "151 Trading Strategies"
encyclopedia into Tickonomics. It identifies potential bottlenecks, precision requirements, and architectural
incompatibilities, with a strict **Math-First** implementation focus.

## Strategy Priority: Mathematical Foundation

As per architectural decision ADR-001, Tickonomics prioritizes the 550+ mathematical formulas described in the
encyclopedia. Machine Learning (ML) is relegated to a future phase (Plan B) to ensure the platform's core is built on
rigorous, explainable quantitative finance.

---

## Phase 1: CDM Enrichment & Infrastructure

...

### Task 1.1: Multi-Asset Snapshot Extension

**Description:** Expand the Common Data Model (CDM) to support "Enrichment Fields" required by quantitative formulas.

- **Options (`CdmOptionSnapshot`):** Add `delta`, `gamma`, `vega`, `theta`, `rho` (Greeks) and `impliedVolatility`.
- **Bonds (`CdmBondSnapshot`):** Add `macaulayDuration`, `modifiedDuration`, `convexity`, and `yieldToMaturity`.
- **Commodities/Futures (`CdmCommodityTick`):** Add `openInterest` and `hedgingPressure`.

**Bottlenecks & Risks:**

- **Data Load:** Fetching Greeks for a full option chain (thousands of instruments) will saturate the Ingestion Layer.
- **Computation vs. Ingestion:** If the source (e.g., Polygon.io) doesn't provide Greeks, the `AnalyticsWorker` must
  compute them using Black-Scholes/Whaley. This adds 10-50ms latency per instrument.
- **Precision:** Formulas in Section 2 rely on precise TTM (Time-to-Maturity) calculations (Eq. 9-16). Inconsistencies
  in day-count conventions (ACT/360 vs. ACT/365) will cause signal drift.

### Task 1.2: Strategy Interface Framework

**Description:** Define a type-safe interface hierarchy for strategy execution.

- `BaseStrategy<T>`: Root interface with `compute(T input)` returning `AlphaSignal`.
- `OptionStrategy`, `EquityStrategy`, `FixedIncomeStrategy`: Specialized interfaces providing context-specific helper
  methods (e.g., `getMidPoint()`, `getSpread()`).

---

## Phase 2: Category-Specific Strategy Porting

### Task 2.1: Option Spread & Combinations (Section 2)

**Description:** Port vertical, horizontal, and diagonal spread formulas (Eq. 17-265).

- **Core Challenge:** Data Alignment. A "Long Call Butterfly" (2.40) requires simultaneous prices for three different
  strikes ($K_1, K_2, K_3$).
- **Refinement:** Implement a `LegMatchService` to group multi-leg snapshots before strategy evaluation.

### Task 2.2: Equity Momentum & Mean-Reversion (Section 3)

**Description:** Port cross-sectional momentum (3.1) and cluster-based mean-reversion (3.9).

- **Bottleneck:** Eq. 293 (Demeaned Returns) requires the mean of the entire universe. This is a global barrier in the
  computation loop.
- **Refinement:** Use a `UniverseAggregator` that computes the mean once per tick cycle and broadcasts it to all
  parallel strategy workers.

### Task 2.3: Fixed Income Yield Curve Strategies (Section 5)

**Description:** Port duration-neutral butterflies and curve flatteners/steepeners.

- **Incompatibility:** Term structure models (Eq. 374-383) assume a smooth yield curve. Real-market data is discrete (
  T-Bill, 2Y, 10Y).
- **Refinement:** Integrate an interpolation service (Cubic Spline or Nelson-Siegel) in the `AnalyticsWorker` to provide
  a continuous curve for the `FixedIncomeStrategy`.

---

## Phase 3: Machine Learning & Alpha Combos

### Task 3.1: Single-Stock KNN (Section 3.17)

**Description:** Implement the K-Nearest Neighbor predictor using technical indicators as features.

- **Bottleneck:** Finding the $k$ nearest neighbors in a large historical buffer is $O(N)$.
- **Refinement:** Use the `KD-Tree` implementation from Apache Commons Math to reduce lookup to $O(\log N)$.

### Task 3.2: Regression-Based Alpha Combos (Section 3.20)

**Description:** Implement the "Mega-Alpha" combiner that weights individual alphas based on serial regression.

- **Risk:** Overfitting. The paper warns that "ubiquitous alphas are faint, ephemeral."
- *Refinement:* Implement a "Shrinkage" factor (e.g., Ridge or Lasso) in the `AnalyticsWorker` to prevent extreme
  weights on noisy signals.

---

## Phase 4: Backtesting Alignment

### Task 4.1: "Delay-d" Methodology Implementation

**Description:** Standardize the `BacktestEngine` to enforce realistic execution delays.

- **Requirement:** Support `Delay-0` (established at open) and `Delay-1` (established using yesterday's data) as defined
  in Appendix A.
- **Risk:** "Borderline in-sample" results. If the delay is too small, the backtest will overestimate profitability due
  to microstructure artifacts.

### Task 4.2: Advanced Slippage & Impact Modeling

**Description:** Transition from flat transaction costs to volume-scaled impact models.

- **Heuristic:** Use the paper's model: `Cost = ζ * (σ / ADDV) * |Position|` (Eq. 553).
- **Refinement:** This requires the `BacktestEngine` to have access to historical Average Daily Dollar Volume (ADDV),
  not just price.

---

## Cross-Cutting Bottlenecks

1. **Precision Mismatch:** Java's `double` (64-bit) vs. Python's `numpy.float64` vs. R's `numeric`. We must use
   `BigDecimal` for currency notionals and `double` with strict rounding for Greeks.
2. **State Management:** 150+ strategies each maintaining a rolling window (20 to 252 days) will consume significant
   heap memory.
    - **Mitigation:** Use off-heap storage or memory-mapped files for historical window buffers if strategy count scales
      beyond 1,000.
3. **Virtual Thread Contention:** While Java 25 handles millions of threads, the `ComputationPool` (Track 5) must be
   tuned to avoid pinning threads during long-running `AnalyticsWorker` calls (External I/O).
