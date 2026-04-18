# Consolidated Proposal: Trading Strategy Enhancement Module

**Status:** Consolidated Proposal for Tickonomics v3
**Target Tracks:** Track 3 (Analytics Worker), Track 5 (Computation Engine), Track 8 (Backtesting), Track 10 (
Demo/Virtual Portfolio), Track 1 (API Contracts)

---

## v3 Integration Points

| Track    | Module                 | Enhancement                                                                               |
|:---------|:-----------------------|:------------------------------------------------------------------------------------------|
| Track 1  | API Contracts          | Special-purpose order type definitions in OpenAPI                                         |
| Track 3  | Analytics Worker       | MarkovStopEngine calibration, Firefly/RAHF optimizer endpoints                            |
| Track 5  | Computation Engine     | FormulaicAlphaLibrary, MegaAlphaEngine, LeverageSignaler, PairsTradingEngine, cost models |
| Track 8  | Backtesting Framework  | Monte Carlo parameter exploration, dual execution comparison, formation/trading periods   |
| Track 10 | Demo/Virtual Portfolio | State-dependent exits, execution handlers, margin algebra, dual portfolio comparison      |

---

## Core Proposals

These three modules form the primary strategy enhancement layer for the v3 computation engine and demo portfolio.

### 1. Formulaic Alpha Library (FORMULAIC_ALPHA_LIBRARY)

**Source:** SSRN-2701346 - 101 Formulaic Alphas

**Objective:** Implement a comprehensive library of 101 formulaic alphas for signal generation, enabling users to
backtest and combine diverse strategies into a robust "mega-alpha" portfolio.

**Module Structure:**

- Package: `computation/src/main/java/com/tickonomics/computation/alpha/`
- Common interface:
  ```java
  public interface Alpha {
      double[] compute(AlphaInputData data);
  }
  ```
- Each alpha (Alpha1 through Alpha101) implemented as a discrete class.

**MegaAlphaEngine:**

- Allows users to combine multiple alphas via configurable weights.
- Implements industry-neutralization logic using `IndClass` placeholders, leveraging industry-neutral alpha
  construction.
- Output: weighted composite signal fed into `SignalGenerator`.

**Backtesting Integration (Track 8):**

- `BacktestEngine` supports selecting and weighting combinations of these 101 alphas during backtesting.
- Identifies optimal "mega-alpha" portfolios across historical windows.

**Validation Criteria:**

- [ ] All 101 alphas produce non-NaN output for standard market data inputs.
- [ ] MegaAlphaEngine weighted combinations produce Sharpe ratios that exceed any individual alpha.
- [ ] Industry-neutralized alphas show lower sector-concentration risk.

---

### 2. Markov-Modulated Optimal Stops (MARKOV_OPTIMAL_STOPS)

**Source:** SSRN-2381830 (Di Graziano, 2014)

**Objective:** Replace static stop-loss/take-profit percentages with state-dependent levels derived from
Markov-modulated diffusion, treating P&L as a stochastic process transitioning between "positive drift" (signal-active)
and "absorbing noise" (signal-exhausted) states.

**Module: MarkovStopEngine (Track 3 - Analytics Worker)**

- Implements calibration routines using the A1/A2 integral transforms.
- Estimates signal drift (mu_1) and decay intensity (q) from live trade data.
- Outputs: `optimal_a` (stop-loss) and `optimal_b` (take-profit) thresholds.

**Dynamic Exit Logic (Track 5 - Computation Engine):**

- `SignalGenerator` and `PaperTradingEngine` support state-dependent exits.
- Stop levels adjust in real-time as the strategy moves through its signal lifecycle.
- Replaces fixed stops with calibrated, dynamic thresholds.

**Validation Criteria:**

- [ ] MarkovStopEngine calibration converges within 1000 iterations on historical trade data.
- [ ] Dynamic-stop strategy shows Sharpe ratio improvement over fixed-stop strategies in backtests spanning 2018-2025.
- [ ] State transitions correctly identify signal exhaustion periods.

---

### 3. Auto-Trading Strategy Improvements (AUTO_TRADING_STRATEGY_IMPROVEMENTS)

**Objective:** Modular, agent-driven auto-trading architecture with distributed task orchestration and Monte Carlo
optimization.

**Orchestration Layer:**

- Distributed task manager based on RabbitMQ/MassTransit model.
- Manages long-running simulation jobs across worker nodes.
- Java 25 Virtual Threads for high-performance backtesting.

**Simulation Layer:**

- Monte Carlo Strategy Optimizer for parameter exploration and portfolio construction.
- Randomized parameter perturbation: test perturbations around "optimal" parameters to measure sensitivity.
- Inverted data testing: validate against inverted price charts to detect luck-based bias.

**Calibration Engine:**

- Dynamic weight adjustment leveraging statistical findings of parameter stability.
- Integrates with the `AUTO_ADJUSTING_WEIGHTS_PLAN` closed-loop controller.

**Two-Tier Validation:**

Tier 1 - Statistical Robustness (Simulation):

- Randomized parameter perturbation for sensitivity analysis.
- Inverted data testing for bias detection.

Tier 2 - Real-time Risk-Guard (Execution):

- Pre-trade Validator: strict risk limits (max position size, frequency, volatility-adjusted thresholds).
- Post-trade Watchdog: real-time PnL, position delta, and latency monitoring with circuit breakers.

**Validation Criteria:**

- [ ] Monte Carlo parameter exploration runs in parallel across worker nodes.
- [ ] PreTradeValidator rejects orders exceeding configurable risk limits.
- [ ] Inverted data tests reveal no systematic luck-based bias.

---

## Complementary Additions

These proposals enhance the core strategy layer with orthogonal signals, execution refinement, and portfolio management
rigor.

### 4. Leverage Rotation Strategy (LEVERAGE_ROTATION_STRATEGY)

**Source:** SSRN-2741701

**Objective:** Systematic rotation between leveraged equity positions and risk-free assets guided by the 200-day Moving
Average of the S&P 500.

**Module: LeverageSignaler**

- Package: `computation/src/main/java/com/tickonomics/computation/leverage/`
- Monitors configurable MA window (default 200-day) of S&P 500.
- Outputs "Leverage-On" / "Leverage-Off" signal to `SignalGenerator`.
- Integrates with `VirtualPortfolio` (Track 10) for automated position scaling or rotation into Treasury bills during
  risk-off regimes.

**Dashboard Integration (Tracks 6 & 7):**

- Visual indicator for "Systemic Leverage Regime" (On/Off).
- LRS performance metrics in backtesting reports.

**Validation Criteria:**

- [ ] Leverage rotation improves Sharpe/Sortino ratios by truncating tail risk in backtests.
- [ ] "Leverage-Off" triggers during all major drawdown periods (2008, 2020, 2022).

---

### 5. Pairs Trading Verification (PAIRS_TRADING_VERIFICATION)

**Source:** SSRN-141615 (Gatev, Goetzmann, Rouwenhorst, 2006)

**Objective:** Use minimum-distance pairs trading as an orthogonal signal verification tool. When ILI signals (funding
liquidity) and pairs signals (relative value) diverge, it indicates a high probability of a liquidity-driven market
dislocation.

**Module: PairsTradingEngine (Track 5)**

- Distance-based matching engine identifies pairs and tracks price divergence.
- Monitors price-distance of "close substitutes" (e.g., ETFs of same sector or credit quality).

**Formation/Trading Period Logic (Track 8):**

- `BacktestHorizonController` enforces rigid 12-month formation / 6-month trading separation.
- Prevents look-ahead bias per the paper's emphasis on out-of-sample testing.

**Validation Criteria:**

- [ ] Pairs-trading profit correlates with ILI stress signals during dislocation events.
- [ ] 12/6 formation/trading methodology prevents look-ahead bias in backtests.

---

### 6. Comparative Execution Analysis (COMPARATIVE_EXECUTION_ANALYSIS)

**Source:** SSRN-4419304

**Objective:** Support and compare Limit-Order (Passive/Liquidity-Providing) vs. Market-Order (
Aggressive/Liquidity-Taking) execution classes.

**Signal Metadata Expansion (Track 5):**

- Include "Fair Price" estimate (midpoint) in signal metadata for limit-order placement.
- Configuration flag toggles between `PASSIVE` and `AGGRESSIVE` execution modes.

**Dual Portfolio Comparison (Track 10):**

- Two virtual portfolios run side-by-side: Market-Order execution vs. "Smart" Limit-Order execution.
- `PassiveExecutionHandler`: places limit orders at ILI-implied fair price +/- small markup (delta).
- `SniperExecutionHandler`: reacts instantly to ILI threshold crossings.

**Schema Update (Track 2):**

- Add `execution_type` column to `virtual_portfolio_trades` and `signal_log`.
- Add "Price Efficiency" metric to `SignalQualityReport`.

**Frontend (Track 7):**

- Visualization comparing cumulative P&L of two execution styles.

**Validation Criteria:**

- [ ] Slippage vs. fill rate trade-off quantified across ILI spike periods.
- [ ] Price Efficiency metric shows statistically significant difference between execution modes.

---

### 7. Portfolio Management Algebra (PORTFOLIO_MANAGEMENT_ALGEBRA)

**Source:** SSRN-2314590 (Beaudan, 2013)

**Objective:** Standardize rebalancing trades, leverage management, and margin-call mitigation using rigorous algebraic
framework.

**Margin Management Algebra (Track 10):**

- Integrate margin call equation [3.7] into `VirtualPortfolio` rebalancing engine.
- Implement the system of equations from Part 5 for portfolio rebalancing under margin constraints.

**Standardized Trading Cost Model (Track 5/10):**

- Adopt linear commission + slippage model: `Trading costs = (t0 + t1)P + sP` [3.2].
- Update `SignalGenerator` and `PaperTradingEngine` to use this standardized function.

**Validation Criteria:**

- [ ] Iterative solution for margin adjustment converges efficiently in simulations.
- [ ] Standardized cost model produces consistent results across all execution handlers.

---

### 8. Captive Finance Commitment Signal (CAPTIVE_FINANCE_COMMITMENT_SIGNAL)

**Source:** SSRN-2784519

**Objective:** Use financing-source analytics (captive vs. bank) as an auxiliary signal for ILI refinement and
collateral quality assessment.

**Data Integration (Track 4):**

- Ingestion Layer extended to differentiate bank-financed vs. captive-financed instruments.
- New `financing_source` metadata field in `rate_snapshots` table.

**Analysis Module (Track 5):**

- `FinancingSourceAnalyzer` adjusts ILI component weights based on captive vs. bank financing concentration.
- Refines "pledgeability" scores for collateralized assets.

**Dashboard (Track 7):**

- "Financing Source Breakdown" chart showing captive/bank split.

**Validation Criteria:**

- [ ] Captive-backed assets show statistically lower volatility in backtests.
- [ ] Financing source metadata correctly propagates through the ILI calculation pipeline.

---

### 9. Algorithmic Behavior Alignment (ALGORITHMIC_BEHAVIOR_ALIGNMENT)

**Source:** SSRN-1142738 (Gsell, 2006)

**Objective:** Replace static cost-justification model with dynamic model accounting for intrinsic costs of trading
large orders.

**Dynamic Cost-Justification (Track 5):**

- "Liquidity-Adjusted Slippage" factor using `LiquidityStressIndex` as input.
- Higher stress dynamically increases estimated slippage cost in `Actionable` signal check.

**Time-Dependent Proxy Sensitivity (Track 5):**

- `IntradayProxyService` includes `time-decay` function.
- As time to official SOFR publication (8:00 AM ET) decreases, signal threshold requirement increases proportionally.
- Reduces speculative proxy signals near data publication times.

**Validation Criteria:**

- [ ] Signal frequency and hit rate improve during high-liquidity stress events (2019 repo spike data).
- [ ] Time-decay function reduces false-positive proxy signals within 30 minutes of SOFR publication.

---

## Alternative Pluggable Implementations (A/B Testing Candidates)

### 10. Special-Purpose Order Support (SPECIAL_PURPOSE_ORDER_SUPPORT)

**Source:** SSRN-2884777

**Status:** Optional execution layer enhancement -- candidate for A/B testing against standard order types.

**Objective:** Expand API contracts and virtual portfolio to support pegged, combo, and linked order types for
professional-grade execution simulation.

**API Contracts (Track 1):**

- Update `api-contracts/openapi.yaml` with optional fields:
    - `PeggedOrders`: link price to external references.
    - `ComboOrders`: multi-leg instrument trading.
    - `LinkedOrders`: conditional execution logic (e.g., reduce quote size upon fill).

**Order Processing (Track 4/5):**

- `Order Management System` enhanced to handle special-purpose order types.
- Logic maps these to standard exchange-native types or simulates via client-side logic.

**Virtual Portfolio (Track 10):**

- Simulate execution of pegged, combo, and linked orders with specific risk-management and transaction-cost profiles.

**A/B Testing Protocol:**

- Compare portfolio performance with and without special-purpose orders enabled.
- Measure alpha preservation (alpha-P) during large-scale liquidation scenarios.

**Validation Criteria:**

- [ ] Pegged orders track external references with configurable tolerance.
- [ ] Combo orders correctly execute multi-leg strategies with atomic fill semantics.
- [ ] Linked orders fire conditional logic within defined latency bounds.

---

## Module Dependency Map

```
FormulaicAlphaLibrary
    |
    v
MegaAlphaEngine --> SignalGenerator --> PaperTradingEngine
    |                                        |
    v                                        v
MarkovStopEngine                     PortfolioManagementAlgebra
                                         |
                                         v
LeverageSignaler                    ComparativeExecutionAnalysis
    |                                        |
    v                                        v
PairsTradingEngine (verification)   SpecialPurposeOrders (alt)
    |
    v
CaptiveFinanceCommitmentSignal (auxiliary)
    |
    v
AlgorithmicBehaviorAlignment (cost model)
```

---

## Source Proposals

1. `FORMULAIC_ALPHA_LIBRARY_PROPOSAL.md` - 101 formulaic alphas from SSRN-2701346
2. `MARKOV_OPTIMAL_STOPS_PROPOSAL.md` - State-dependent stops from Markov-modulated diffusion (SSRN-2381830)
3. `LEVERAGE_ROTATION_STRATEGY_PROPOSAL.md` - S&P 500 200-day MA leverage rotation (SSRN-2741701)
4. `PAIRS_TRADING_VERIFICATION_MODULE.md` - Minimum-distance pairs trading verification (SSRN-141615)
5. `SPECIAL_PURPOSE_ORDER_SUPPORT_PROPOSAL.md` - Pegged, combo, linked order types (SSRN-2884777)
6. `COMPARATIVE_EXECUTION_ANALYSIS.md` - Limit-order vs market-order dual portfolio (SSRN-4419304)
7. `PORTFOLIO_MANAGEMENT_ALGEBRA_PROPOSAL.md` - Algebraic rebalancing under margin constraints (SSRN-2314590)
8. `CAPTIVE_FINANCE_COMMITMENT_SIGNAL_PROPOSAL.md` - Financing-source analytics (SSRN-2784519)
9. `AUTO_TRADING_STRATEGY_IMPROVEMENTS.md` - Modular agent-driven auto-trading with Monte Carlo
10. `ALGORITHMIC_BEHAVIOR_ALIGNMENT.md` - Dynamic liquidity-adjusted slippage (SSRN-1142738)
