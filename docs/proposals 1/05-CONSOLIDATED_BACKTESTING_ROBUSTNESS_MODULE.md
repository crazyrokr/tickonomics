# Consolidated Proposal: Backtesting Robustness Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)

## v3 Integration Points

| Track    | Component                | Role                                                                          |
|----------|--------------------------|-------------------------------------------------------------------------------|
| Track 3  | Python Analytics Worker  | Sobol simulation service, reproducibility scoring, strategic run analytics    |
| Track 4  | Ingestion Layer          | De-rounding filter, periodicity filter for tick data cleanup                  |
| Track 5  | Computation Engine       | Zeta-adjusted thresholds, submission-based impact KPI, cost model integration |
| Track 7  | Analytics Dashboard      | Robustness heatmap visualization, performance decomposition panel             |
| Track 8  | Backtesting Framework    | Multi-dimensional parameter scanning, stress testing, regulatory compliance   |
| Track 9  | CI/CD                    | Benchmarking service, chaos suite integration                                 |
| Track 10 | Demo / Virtual Portfolio | Enhanced cost model, randomized execution window, behavioral certification    |

---

## Core Proposal: Multi-Dimensional Parameter Scanning and Robustness Optimization

**Source:** BACKTESTING_ROBUSTNESS_OPTIMIZATION
**Reference Paper:** `ssrn-3620154.pdf` ("Backtesting of Algorithmic Cryptocurrency Trading Strategies" by Jan Frederic
Spörer, 2020)

### 1. Multi-Dimensional Parameter Scanning

Extend the `WeightOptimizer` in Track 8 to perform grid searches or Sobol sequences across multiple dimensions
simultaneously:

- **ILI component weights** (RRP, SOFR/IORB spread, Volatility)
- **`buy_percentile`** threshold
- **`sell_percentile`** threshold
- **`cooldown_period`** duration

This ensures strategy parameters are not tuned to a single narrow optimum that would indicate overfitting.

### 2. Robustness Heatmaps

Implement two classes of sensitivity visualizations described in the reference paper:

- **Time Sensitivity Heatmaps:** Show whether strategy success is concentrated in a specific historical time window. A
  strategy that only profits during 2020-03 (COVID crash) is not robust.
- **Parameter Sensitivity Heatmaps:** Display performance across 2D parameter slices (e.g., buy_percentile vs.
  sell_percentile). Identify narrow parameter "islands" of profitability that indicate overfitting.

### 3. Advanced Slippage Module

Adopt the paper's approach of combining fixed commission with variable slippage that optionally scales with volume (
Track 8, section 10.5 of the reference paper). This replaces simplistic flat-rate cost assumptions with a more realistic
execution cost model.

### Implementation Plan (Core)

1. **Backtest Engine:** Update `BacktestResult.java` to store metadata for heatmap generation (performance across 2D
   parameter slices).
2. **Analytics Worker:** Create a `/api/v1/backtest/robustness-scan` endpoint that handles the batch execution of
   multiple scenarios using Java 25 virtual threads.
3. **Frontend:** Implement `BacktestHeatmap.tsx` component using D3.js or Perspective for the Backtest Report Page.

### Validation Criteria (Core)

- [ ] Weight Recalibration runbook produces a 2D sensitivity heatmap.
- [ ] Backtest engine can run 100 parameter permutations in < 5 minutes using virtual threads.

---

## Complementary Additions

### A. Quasi-Random Simulation and Riemann Zeta Discrete Monitoring Correction

**Source:** RIEMANN_ZETA_SIMULATION
**Reference:** SSRN-4549131 - "How truly random Is The Brownian Motion? The Hidden Connection with Riemann Zeta
Function & Riemann Hypothesis!"

#### Sobol Quasi-Random Monte Carlo

Standard pseudo-random Monte Carlo simulations may leave gaps in the state space. Sobol quasi-random sequences provide
better coverage, ensuring the backtest explores worst-case liquidity paths more efficiently.

- Implement Monte Carlo simulations using **Sobol sequences** (`scipy.stats.qmc`) to stress-test ILI signals.
- Expose a `/simulate/sobol` endpoint in the Python Analytics Worker to generate non-standard "random" paths for
  `ili_value`.
- Update `HistoricalDataReplay` to include a "Robustness Mode" that perturbs historical paths using quasi-random noise.

#### Broadie-Kou-Glasserman Discrete Monitoring Correction

For discrete monitoring (daily signal evaluations at 1-minute or 1-day intervals), the continuous-to-discrete barrier
adjustment uses the Riemann Zeta function:

$$\beta = -\frac{\zeta(1/2)}{\sqrt{2\pi}} \approx 0.5826$$

This constant beta corrects for the bias introduced by sampling continuous liquidity at discrete points. Apply it to:

- Signal thresholds (BUY/SELL percentiles) when evaluating historical performance.
- `RegimeDetector` bandwidths to ensure thresholds are fairly set for discrete digital execution.
- `CalculationGuard` or `SignalGenerator` to adjust for discrete sampling errors.

#### Benefits

- Sobol-based simulations uncover edge cases in signal behavior that standard random tests miss.
- The Riemann Zeta adjustment provides a scientifically rigorous way to set discrete trading barriers, aligning
  tickonomics with institutional-grade quant standards.
- Better understanding of the quasi-random nature of liquidity leads to more accurate Granger causality results.

---

### B. Return Gap Performance Diagnostic

**Source:** RETURN_GAP_PERFORMANCE_DIAGNOSTIC
**Reference:** SSRN-2980774

#### Metric Definition

Implement the `ReturnGap` calculation to separate interim trading skill from static holdings returns:

```
ReturnGap = InvestorGrossReturn - HoldingsReturn
```

Where `HoldingsReturn` is the buy-and-hold return of the initial portfolio.

#### Integration Points

1. **Backtest Engine (Track 8):** Integrate Return Gap as a key performance indicator. Every backtest result includes
   both holdings return and the execution alpha from interim trading.
2. **Computation Engine (Track 5):** Track interim trading profits separately from static holdings performance. Use
   Return Gap as a target for optimizing execution algorithms (timing of fills, slice-size adjustments).
3. **Dashboard (Track 7):** Add a "Performance Decomposition" panel showing the breakdown of strategy gains into
   `HoldingsReturn` and `ReturnGap`.

#### Benefits

- Provides a clear measure of "execution alpha" generated by trading algorithms.
- Validates strategy ability to exploit AT-related liquidity improvements.
- Enables nuanced performance attribution, separating investment selection skill from interim trading skill.

---

### C. ML Reproducibility and Benchmarking Integration

**Source:** PROPOSAL_ML_REPRODUCIBILITY_BENCHMARKING
**Reference:** Khan (2026), "Machine Learning in Quantitative Finance: A Systematic Review (2015-2025)"

#### Reproducibility Disclosure Score (RDS)

Adopt a 0-2 rubric for quantifying disclosure quality of all models in the backtesting framework. This ensures all
models have reproducible code and data trails, supporting regulatory-defensible deployment.

#### Internal Benchmarking Service

Establish a benchmarking service within the computation module that compares new ML-based models against traditional
baselines (linear regression, TA-Lib indicators) using standardized performance protocols. The paper validates that
tree-based ensembles (gradient boosting) remain superior for cross-sectional data, while deep learning (transformers)
excels in volatility forecasting.

#### Audit-Grade Artifacts

Add a `Reproducibility` metadata field to the `backtest_results` TimescaleDB hypertable to track:

- Code version (git SHA)
- Dataset hash
- Model hyperparameters
- RDS score

This aligns with the paper's emphasis that "the field's most pressing need is not bigger models, but better benchmarks,
stricter reproducibility norms, and audit-grade artifacts for regulator-defensible deployment."

#### Integration Plan

- Phase 5: Integrate RDS rubric into internal model review process.
- Phase 8: Prioritize audit-grade data storage for all backtest experiments to support regulatory-grade deployment
  requirements.

---

### D. Regulatory Algorithm Behavioral Testing Standards

**Source:** REGULATORY_ALGO_TESTING_STANDARDS
**Reference:** `ssrn-3807935.pdf` ("Algorithms put to test" by Patrick Raschner, 2021)

#### Stressed Market Condition (SMC) Scenarios

Define specific backtest scenarios based on MiFID II definition of SMC (Section IV.1.c):

- **Message Spikes:** Simulate a 10x increase in tick volume from Polygon.
- **Volatility Jumps:** Simulate rapid price moves > 5% in 5 minutes.
- **Capacity Exhaustion:** Simulate delayed execution times in the virtual engine.

#### Order-to-Trade Ratio (OTR) Monitoring

Implement OTR tracking as a safety KPI. High OTR can be a precursor to "disorderly trading" as defined by ESMA.
Implement a global `CircuitBreaker` in `SignalGenerator` that halts all signals if the system-wide OTR exceeds a safety
threshold.

#### Behavioral Certification

Formalize the "Graduation Criteria" into a "Self-Certification Report" similar to the MiFID II declaration (Section
IV.3). This report documents:

- System behavior during the 90-day demo period.
- Behavior during "Proxy Dislocated" events.
- Kill-Switch verification results.

#### Kill-Switch Verification

Mandatory testing of the "safety control program disconnect switch" (Section IV.1.d) during simulated stress events in
the demo environment.

#### Implementation Plan

1. **Backtest Engine:** Add `MarketStressSimulator` module to Track 8.
2. **Reporting:** Create a `RegulatoryComplianceReport` generator that aggregates metrics for MiFID II style
   self-certification.
3. **Safety:** Implement global `CircuitBreaker` with < 100ms latency.

#### Validation Criteria

- [ ] System passes the "Kill-Switch Test" (manual and automated) with < 100ms latency.
- [ ] Compliance report correctly captures all "Stressed Market" intervals during the 90-day verification period.

---

### E. Human Bias Mitigation (De-Rounding and Randomized Execution)

**Source:** HUMAN_BIAS_MITIGATION
**Reference:** `ssrn-2375739.pdf` (Broussard and Nikiforov, 2013)

#### De-Rounding Data Filter (Track 4)

Add a `DeRoundingFilter` to the `DataQualityChecker`. When processing intraday volume/trades, the filter identifies and
smooths out spikes that occur within 30 seconds of round time marks (e.g., 9:45, 10:00, 10:05) by interpolating with
adjacent non-round-mark intervals.

Broussard and Nikiforov (2013) show that algorithmic trading activity spikes at round time marks due to human programmer
bias, not liquidity optimization. These spikes introduce noise and price impact without providing better liquidity.

#### Randomized Execution Window (Track 10)

Implement a `RandomizedExecutionWindow` for all virtual portfolio trades. To avoid the price impact caused by
human-biased spikes, all market orders in the `PaperTradingEngine` execute within a randomized 0-60 second window after
the signal is received, specifically avoiding the "round-mark" danger zone.

#### Validation Plan

- [ ] Implement `DeRoundingFilter` and verify it effectively smooths out the identified 5-minute volume spikes.
- [ ] Verify `PaperTradingEngine` trade execution times remain within the 60s random window and avoid spike clusters.
- [ ] Backtest strategy performance before and after applying the "De-rounding" filter to verify slippage reduction.

---

### F. Time-Periodicity Filter for Algorithmic Loop Artifacts

**Source:** PERIODICITY_FILTER
**Reference:** `ssrn-2496669.pdf` (Muravyev and Picard, 2022)

#### TimePeriodicityFilter (Track 5)

Muravyev and Picard (2022) document systematic intraday spikes in trade/quote activity at 1-second intervals, driven by
algorithmic "loops" (human bias). These spikes increase volatility but do not improve liquidity.

Add a `TimePeriodicityFilter` to the `IliCalculator` that:

- Detects trade/quote volumes within the 100ms "spike" window of round seconds.
- Adjusts them using a time-weighted average of the adjacent non-spike periods.
- "Cleans" the high-frequency tick data before it is aggregated into the 5-minute ILI components.

This complements the De-Rounding Filter (Section E) by addressing second-granularity artifacts rather than
minute-granularity round-mark spikes.

#### Validation Plan

- [ ] Implement `TimePeriodicityFilter` and confirm it smooths out the identified 1-second activity spikes.
- [ ] Verify that the `LiquidityStressIndex` (LSI) remains robust during periods of high quote-spike activity.
- [ ] Re-run backtests comparing ILI signals with and without the filter to measure the reduction in false-positive
  signals.

---

### G. Submission-Based Price Impact and Strategic Run Detection

**Source:** TRADING_ALGORITHM_ANATOMY
**Reference:** `ssrn-3497001.pdf` ("The Anatomy of Trading Algorithms" by Tyler Beason and Sunil Wahal, 2021)

#### Submission-Based Price Impact KPI

Develop a KPI that tracks "Intended Move vs. Executed Move." By monitoring the NBBO midpoint at the time a Polygon trade
condition (e.g., "Odd Lot") appears, estimate the submission-time impact described in the paper. Child orders (mostly
limit orders) incur price impact at the time they are **submitted**, even if they are unexecuted or passively priced.

#### StrategicRunDetector

Implement a `StrategicRunDetector` that groups consecutive buy or sell child orders. If an algorithm is in an "
aggressive run" (as defined in the paper), expect higher transaction costs and adjust `expected_move` thresholds
accordingly.

#### Enhanced Cost Model (Track 10)

Update the `PaperTradingEngine` cost model to include a tiered "Submission Impact" penalty:

- **Passive orders:** 0.84 bps
- **Aggressive orders:** 2.03 bps
- **Large orders (benchmark):** avg 9.04 bps

This is in addition to the current slippage model, providing a more realistic simulation of institutional execution.

#### NBBO Midpoint Drift Simulation

Simulate "post-submission quote drift" by adjusting the virtual entry price based on 10-second horizon results from the
paper.

#### Implementation Plan

1. **Computation:** Add `StrategicRunService` to analyze the sequence of incoming ticks for pattern identification.
2. **Demo Engine:** Refactor `VirtualPortfolio` to support a tiered cost model (Execution Cost + Submission Impact).
3. **Analytics:** Add `/api/v1/econometrics/strategic-runs` endpoint to calculate transition probabilities between
   passive/aggressive states.

#### Validation Criteria

- [ ] Virtual portfolio transaction costs correlate more closely with "Large Order" benchmarks (avg 9.04 bps) cited in
  the paper.
- [ ] `StrategicRunDetector` correctly identifies the "alternating phases of providing and taking liquidity" for known
  high-volume symbols.

### Complementary Addition 8: Price Jump Ratio Diagnostic for Information Efficiency

**Source:** PRICE_JUMP_RATIO_DIAGNOSTIC
**Reference:** SSRN-2662254

Implement a `PriceJumpRatioDiagnostic` to quantify how effectively Tickonomics signals capture information relative to
total announcement-period price variation.

#### Price Jump Ratio Metric

`PJR = CAR(T-a, T+b) / CAR(T-k, T+b)` where `T` is the earnings announcement time.

#### Implementation

1. Add `InformationEfficiencyAnalyzer` to `computation/src/main/java/com/tickonomics/computation/backtest/`
2. Integrate with the existing `BacktestEngine` to calculate PJR scores for historical trading periods
3. Add a "Signal Informativeness" panel to the Analytics Dashboard (Track 7) to visualize PJR for recent signals

#### Benefits

Provides a quantitative benchmark for distinguishing informed trading from noise reaction, helping tune signal
thresholds to maximize information incorporation.

---

## Source Proposals

1. **BACKTESTING_ROBUSTNESS_OPTIMIZATION** (PRIMARY) - `ssrn-3620154.pdf` ("Backtesting of Algorithmic Cryptocurrency
   Trading Strategies" by Spörer, 2020)
2. **RIEMANN_ZETA_SIMULATION** (COMPLEMENTARY) - SSRN-4549131 - "How truly random Is The Brownian Motion?"
3. **RETURN_GAP_PERFORMANCE_DIAGNOSTIC** (COMPLEMENTARY) - SSRN-2980774
4. **PROPOSAL_ML_REPRODUCIBILITY_BENCHMARKING** (COMPLEMENTARY) - Khan (2026), "Machine Learning in Quantitative
   Finance: A Systematic Review (2015-2025)"
5. **REGULATORY_ALGO_TESTING_STANDARDS** (COMPLEMENTARY) - `ssrn-3807935.pdf` ("Algorithms put to test" by Raschner,
   2021)
6. **HUMAN_BIAS_MITIGATION** (COMPLEMENTARY) - `ssrn-2375739.pdf` (Broussard and Nikiforov, 2013)
7. **PERIODICITY_FILTER** (COMPLEMENTARY) - `ssrn-2496669.pdf` (Muravyev and Picard, 2022)
8. **TRADING_ALGORITHM_ANATOMY** (COMPLEMENTARY) - `ssrn-3497001.pdf` ("The Anatomy of Trading Algorithms" by Beason and
   Wahal, 2021)
9. **PRICE_JUMP_RATIO_DIAGNOSTIC** (COMPLEMENTARY) - `ssrn-2662254.pdf` - Information efficiency diagnostic measuring
   signal informativeness
