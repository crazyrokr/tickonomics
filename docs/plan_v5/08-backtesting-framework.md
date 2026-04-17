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

### 5. Liquidity Stress Testing Integration (v4 — Proposal 02)

- Integrates with `BacktestEngine` to enable "Stress Mode".
- Historical backtests subjected to liquidity shocks at random or user-specified points.
- Swiss Franc cap removal (2015) model as canonical stress scenario.
- Additional scenarios: repo spike (2019), COVID liquidity freeze (2020).
- Stress impact measured as deviation from baseline backtest results.
- Module: `computation/src/main/java/com/tickonomics/computation/stress/LiquidityStressScenario.java`

### 6. Barrier Hitting-Time Statistics (v4 — Proposal 01: Stochastic Drift)

- Evaluates strategy robustness during liquidity stress conditions.
- Measures how quickly a simulated path reaches critical thresholds (barrier levels).
- Computes distribution of first-passage times under drift-diffusion model.
- Integration with drift-diffusion service from Python analytics worker.
- Outputs: mean hitting time, percentile distribution, barrier breach frequency.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/BarrierHittingTimeCalculator.java`

### 7. Calibration Stability Tests (v4 — Proposal 03: Online Calibration)

- Verifies that online calibration logic does not lead to overfitting on recent data.
- Erratic weight shifts during historical stress tests are flagged and reported.
- Walk-forward validation: weights calibrated on window N are tested on window N+1.
- Flagging criteria: weight shift exceeding configurable percentage threshold during stress.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/CalibrationStabilityTest.java`

### 8. QED Drift Simulation (v4 — Proposal 03: QED)

- Python service to solve discrete-time QED (Quantum Economics Dynamics) equations.
- "What-if" scenarios for massive liquidity withdrawals (rapid quantitative tightening).
- Simulates potential well transitions under extreme market conditions.
- Integration with `BacktestEngine` as alternative simulation mode to standard Monte Carlo.
- Results compared against historical stress events for validation.

### 9. Climate Stress Scenarios (v4 — Proposal 03: Climate)

- "Climate Stress" scenarios embedded in backtesting framework.
- Scenario library: abrupt carbon-tax hike, massive clean energy investment event, regulatory
  shock to fossil fuel sector.
- Climate shocks integrated as new type of `ExogenousShock` in the computation pipeline.
- Portfolio impact measured as deviation from baseline backtest under each scenario.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/ClimateStressScenario.java`

### 10. Intrinsic Time Backtesting (v4 — Proposal 03: Event-Based Time)

- Backtesting using intrinsic time series as alternative to fixed-interval time-series bars.
- Agent-based strategies defined by an event-based language (price moves of delta %).
- Captures market activity independent of clock-time: high activity periods compressed,
  low activity periods expanded.
- Produces results comparable to traditional time-series backtesting for cross-validation.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/IntrinsicTimeBacktester.java`

### 11. Drift Consistency Diagnostics (v4 — Proposal 01: Stochastic Drift)

- Applies consistency conditions derived from drift estimation theory.
- Detects when the current drift estimate has become inconsistent with incoming data stream.
- Inconsistency triggers a model recalibration flag in the computation pipeline.
- Diagnostic output: drift estimate, confidence interval, consistency score, flag status.

### 12. FireflyWeightOptimizer (v4 — Proposal 04: Alternative)

- Location: `computation/src/main/java/com/tickonomics/computation/optimization/FireflyWeightOptimizer.java`
- Metaheuristic optimization inspired by firefly algorithm.
- A/B tested against Bayesian optimizer using identical fitness function.
- Same interface as `WeightOptimizer`; selected via `method: "firefly"` in backtest config.
- Configurable parameters: population size, generations, randomization alpha, light absorption coefficient.

### 13. Monte Carlo Parameter Exploration (v4 — Proposal 04)

- Random parameter sampling across weight space for robustness testing.
- Produces distribution of backtest outcomes across the full parameter space.
- Identifies regions of parameter space with consistently strong vs weak performance.
- Output: parameter sensitivity distribution, confidence intervals for Sharpe ratio.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/MonteCarloExplorer.java`

### 14. Robustness Report Generator (v4 — Proposal 04: Ambiguity Aversion)

- Evaluates strategy performance under +-10% deviations in input model parameters.
- Worst-case optimization mode: reports results under least favorable parameter perturbation.
- Sensitivity ranking of parameters by their impact on Sharpe ratio.
- Output: robust Sharpe (worst-case), parameter sensitivity ranking, heat map of performance
  across parameter grid.
- Module: `computation/src/main/java/com/tickonomics/computation/backtest/RobustnessReportGenerator.java`

### 15. Optimizer Comparison Framework (v4 — Proposal 04)

- A/B testing matrix: Bayesian vs Firefly vs RAHF (Risk-Ambiguity Hybrid Fitness).
- Metrics compared: convergence speed (iterations to threshold), final Sharpe ratio,
  parameter stability across regime changes, computational cost.
- Tournament results persisted to database for historical review and analysis.
- Configurable: select optimizers to include, number of rounds, fitness function variants.

### 16. VaR-Based Signal Filtering (v4 — Proposal 03: GARCH)

- Uses GARCH-based Value-at-Risk formulation to add a risk-check layer to signal generation.
- Suppresses signals if the 1-day VaR exceeds a configurable percentage of virtual equity.
- Integrated into `BacktestEngine` as an optional signal filter during replay.
- Output: count of suppressed signals, VaR threshold breaches, filtered vs unfiltered performance.

### 17. Efficiency-Depth Calibration Test (v4 — Proposal 04: Efficiency-Depth)

- Compares "Default Weights" vs "Regime-Aware Weights" over high-stress historical periods.
- Verifies that efficiency-only indicators (without depth/liquidity) produce realistic signal
  quality in normal conditions, while regime-aware weights improve stress-period performance.
- Identifies periods where depth information adds the most value.

### 18. MultiDimensionalParameterScanner (v5 — Proposal 05: Parameter Scanning)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/MultiDimensionalParameterScanner.java`
- Grid search or Sobol sequences across multiple dimensions simultaneously:
    - ILI component weights (RRP, SOFR/IORB spread, Volatility)
    - buy_percentile threshold
    - sell_percentile threshold
    - cooldown_period duration
- Ensures strategy parameters are not tuned to a single narrow optimum indicating overfitting.
- Uses `/api/v1/backtest/robustness-scan` from Python analytics worker.
- Backtest engine runs 100 parameter permutations in < 5 minutes using virtual threads.

### 19. RobustnessHeatmapGenerator (v5 — Proposal 05: Heatmaps)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/RobustnessHeatmapGenerator.java`
- Generates two classes of sensitivity visualizations:
    - Time Sensitivity Heatmap: strategy success concentrated in specific historical window.
    - Parameter Sensitivity Heatmap: 2D parameter slices (buy_percentile vs. sell_percentile).
- Updates `BacktestResult.java` to store metadata for heatmap generation (performance across 2D parameter slices).
- Output consumed by `BacktestHeatmap.tsx` frontend component.

### 20. AdvancedSlippageModule (v5 — Proposal 05: Slippage)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/AdvancedSlippageModule.java`
- Combines fixed commission with variable slippage that optionally scales with volume.
- Replaces simplistic flat-rate cost assumptions with more realistic execution cost model.
- Tiered submission impact penalty (from Proposal 05 StrategicRunService):
    - Passive orders: 0.84 bps
    - Aggressive orders: 2.03 bps
    - Large orders (benchmark): avg 9.04 bps

### 21. SobolRobustnessMode (v5 — Proposal 05: Sobol Simulation)

- Integration into `HistoricalDataReplay`.
- "Robustness Mode" perturbs historical paths using quasi-random noise from Sobol sequences.
- Consumes `/simulate/sobol` from Python analytics worker.
- Uncovers edge cases in signal behavior that standard random tests miss.

### 22. ReturnGapIntegration (v5 — Proposal 05: Return Gap)

- Integrates `ReturnGapCalculator` into backtest result output.
- Every backtest result includes:
    - HoldingsReturn (buy-and-hold return)
    - ReturnGap (execution alpha from interim trading)
    - TotalReturn (combined)
- Used to validate strategy ability to exploit AT-related liquidity improvements.

### 23. ReproducibilityService (v5 — Proposal 05: ML Reproducibility)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/ReproducibilityService.java`
- Tracks Reproducibility Disclosure Score (RDS) for all backtest models.
- Adds metadata to `backtest_results`: code version (git SHA), dataset hash, model hyperparameters, RDS score.
- Internal benchmarking service compares ML-based models against traditional baselines.
- Audit-grade artifacts support regulator-defensible deployment.

### 24. MarketStressSimulator (v5 — Proposal 05: Regulatory Testing)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/MarketStressSimulator.java`
- MiFID II-style Stressed Market Condition (SMC) scenarios:
    - Message Spikes: 10x increase in tick volume from Polygon.
    - Volatility Jumps: rapid price moves > 5% in 5 minutes.
    - Capacity Exhaustion: delayed execution times in virtual engine.
- Order-to-Trade Ratio (OTR) monitoring as safety KPI.
- Results feed into `RegulatoryComplianceReport` generator.

### 25. PriceJumpRatioBacktest (v5 — Proposal 05: Price Jump Ratio)

- Integration into `BacktestEngine` for PJR calculation during replay.
- Computes PJR scores for all historical trading periods.
- Provides quantitative benchmark for signal informativeness.
- Output consumed by `SignalInformativenessPanel` on dashboard.

### 26. RegulatoryComplianceReport (v5 — Proposal 05: Self-Certification)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/RegulatoryComplianceReport.java`
- "Self-Certification Report" similar to MiFID II declaration (Section IV.3).
- Documents:
    - System behavior during 90-day demo period.
    - Behavior during "Proxy Dislocated" events.
    - Kill-Switch verification results.
    - OTR breach count and severity.
- Generated on-demand or as part of weekly demo report.
- Stored in `regulatory_compliance_reports` table.

### 27. PairsTradingBacktester (v5 -- Proposal 08: Pairs Trading Verification)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/PairsTradingBacktester.java`
- Formation/Trading period enforcement: rigid 12-month formation / 6-month trading separation.
- `BacktestHorizonController` prevents look-ahead bias per SSRN-141615 emphasis on out-of-sample testing.
- Minimum-distance pairs matching with configurable distance threshold.
- Tracks divergence between ILI signals and pairs signals for dislocation detection.

### 28. SemanticBacktester (v5 -- Proposal 09: Old NLP vs New NLP)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/SemanticBacktester.java`
- Compares historical signals using "Old NLP" (lexicon-based) vs. "New NLP" (FinBERT).
- Quantifies reduction in false-positive signals during high-noise periods (e.g., earnings season).
- Benchmark: false-positive reduction percentage across identical historical data.
- Output feeds into `SaliProcessor` configuration for optimal sentiment weighting.

### 29. WalkForwardValidation (v5 -- Proposal 10: Walk-Forward Analysis)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/WalkForwardValidator.java`
- Anchored walk-forward analysis: train on expanding window, test on fixed out-of-sample segment.
- Prevents `WeightOptimizer` from overfitting to historical noise.
- Evaluates performance based on regret in addition to Sharpe ratio.
- Reports out-of-sample degradation percentage.

### 30. MultiModelTournament (v5 -- Proposal 10: Multi-Model Benchmark)

- Location: `computation/src/main/java/com/tickonomics/computation/backtest/MultiModelTournament.java`
- Replays historical data through ILI rule engine, XGBoost, and LSTM benchmark models.
- Regime-specific benchmarking: groups results by uptrend, sideways, downtrend.
- Side-by-side performance charts: Standard ILI, XGBoost-Augmented ILI, LSTM-Augmented ILI.
- Uses Python worker `/tournament/evaluate` endpoint for model inference.

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
      method: "bayesian"  # "bayesian" or "firefly"
      n_iterations: 100
      weight_constraint: "sum_to_one"
      cross_validate: true
      firefly_params:
        population_size: 15
        generations: 50
        randomization_alpha: 0.2
        light_absorption: 1.0
    stress:
      liquidity_enabled: true
      scenarios: ["swiss_franc_2015", "repo_spike_2019", "covid_2020"]
    climate:
      enabled: true
      scenarios: ["carbon_tax_hike", "clean_energy_investment"]
    intrinsic_time:
      enabled: true
      directional_change_threshold: 0.01
    robustness:
      enabled: true
      parameter_deviation_pct: 10
      n_monte_carlo_samples: 1000
      parameter_scanning:
        method: "sobol"  # "sobol" or "grid"
        n_permutations: 100
        dimensions:
          - name: "rrp_weight"
            range: [0.2, 0.6]
          - name: "spread_weight"
            range: [0.2, 0.6]
          - name: "vol_weight"
            range: [0.1, 0.3]
          - name: "buy_percentile"
            range: [2, 10]
          - name: "sell_percentile"
            range: [90, 98]
      heatmaps:
        time_sensitivity: true
        parameter_sensitivity: true
    calibration_stability:
      enabled: true
      max_weight_shift_pct: 10
      stress_periods: ["2008_gfc", "2019_repo_spike", "2020_covid"]
    advanced_slippage:
      enabled: true
      volume_scaling: true
      submission_impact:
        passive_bps: 0.84
        aggressive_bps: 2.03
        large_order_bps: 9.04
    reproducibility:
      enabled: true
      rds_required: true
      min_rds_score: 1
    regulatory:
      smc_scenarios_enabled: true
      otr_monitoring: true
      otr_threshold: 100
      self_certification: true
```

---

## Module Structure

```
computation/
├── src/main/java/com/tickonomics/computation/
│   ├── backtest/
│   │   ├── BacktestEngine.java                   # Existing
│   │   ├── HistoricalDataReplay.java              # Existing
│   │   ├── SimulatedPortfolio.java                # Existing
│   │   ├── WeightOptimizer.java                   # Existing (Bayesian)
│   │   ├── BacktestResult.java                    # Existing
│   │   ├── BacktestConfig.java                    # Existing, extended
│   │   ├── BarrierHittingTimeCalculator.java      # Hitting-time stats (v4)
│   │   ├── CalibrationStabilityTest.java          # Online calibration check (v4)
│   │   ├── ClimateStressScenario.java             # Climate shock scenarios (v4)
│   │   ├── IntrinsicTimeBacktester.java           # Event-based backtest (v4)
│   │   ├── MonteCarloExplorer.java                # Parameter exploration (v4)
│   │   ├── RobustnessReportGenerator.java         # Ambiguity robustness (v4)
│   │   ├── LiquidityStressScenario.java           # Liquidity shock (v4)
│   │   ├── MultiDimensionalParameterScanner.java  # Parameter scanning (v5)
│   │   ├── RobustnessHeatmapGenerator.java        # Heatmap data generation (v5)
│   │   ├── AdvancedSlippageModule.java            # Volume-scaled slippage (v5)
│   │   ├── SobolRobustnessMode.java               # Quasi-random perturbation (v5)
│   │   ├── ReturnGapIntegration.java              # Return Gap in backtest (v5)
│   │   ├── ReproducibilityService.java            # RDS and audit artifacts (v5)
│   │   ├── MarketStressSimulator.java             # SMC scenarios (v5)
│   │   ├── PriceJumpRatioBacktest.java            # PJR in backtest (v5)
│   │   └── RegulatoryComplianceReport.java        # Self-certification report (v5)
│   ├── optimization/
│   │   ├── BayesianWeightOptimizer.java           # PRIMARY (v4)
│   │   ├── FireflyWeightOptimizer.java            # Alternative (v4)
│   │   └── OptimizerComparisonFramework.java      # A/B testing (v4)
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
- [ ] Liquidity stress scenarios execute on historical data with measurable impact (v4).
- [ ] Barrier hitting-time statistics computed correctly for simulated paths (v4).
- [ ] Calibration stability test detects overfitting during walk-forward (v4).
- [ ] QED drift simulation produces consistent results with historical data (v4).
- [ ] Climate stress scenarios produce measurable portfolio impact (v4).
- [ ] Intrinsic time backtest produces results comparable to time-series backtest (v4).
- [ ] Firefly optimizer converges on same ILI weight problems as Bayesian (v4).
- [ ] Monte Carlo exploration produces parameter sensitivity distribution (v4).
- [ ] Robustness report quantifies sensitivity to each input parameter (v4).
- [ ] Optimizer comparison framework produces A/B tournament results (v4).
- [ ] VaR-based signal filtering suppresses signals during high VaR periods (v4).
- [ ] Efficiency-depth calibration test compares default vs regime-aware weights (v4).
- [ ] Multi-dimensional parameter scanner runs 100 permutations in < 5 minutes using virtual threads (v5).
- [ ] Robustness heatmap generator produces correct time sensitivity and parameter sensitivity data (v5).
- [ ] Advanced slippage module applies tiered submission impact penalties correctly (v5).
- [ ] Sobol robustness mode perturbs historical paths with quasi-random noise (v5).
- [ ] Return Gap computed correctly for every backtest result (v5).
- [ ] Reproducibility metadata (git SHA, dataset hash, RDS score) stored with each backtest result (v5).
- [ ] Market stress simulator generates SMC scenarios with message spikes, volatility jumps, capacity exhaustion (v5).
- [ ] OTR monitoring tracks order-to-trade ratio during backtests (v5).
- [ ] Price Jump Ratio computed for historical trading periods (v5).
- [ ] Regulatory compliance report captures all required fields for self-certification (v5).

**v5.2 Validation Additions (Proposal 13 & 14: Quantitative Engine):**

- [ ] MultipleTestingCorrectionService correctly adjusts p-values for optimization (v5.2).
- [ ] `RiskBacktestValidator` identifies GFC-era (2008) VaR failures (v5.2).
- [ ] `DelayDExecutor` and `VolumeScaledSlippage` align backtests with Eq. 553 costs (v5.2).
- [ ] `StrategyBacktestRunner` successfully replays strategy_definitions (v5.2).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|:--------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Initial backtesting framework: HistoricalDataReplay, BacktestEngine, WeightOptimizer (Bayesian), Backtest Report Page. Applied Findings 3 and 6 for proxy divergence replay and dynamic weight awareness.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v4      | Added 13 new components: Liquidity Stress Testing, Barrier Hitting-Time Statistics, Calibration Stability Tests, QED Drift Simulation, Climate Stress Scenarios, Intrinsic Time Backtesting, Drift Consistency Diagnostics, FireflyWeightOptimizer, Monte Carlo Parameter Exploration, Robustness Report Generator, Optimizer Comparison Framework, VaR-Based Signal Filtering, Efficiency-Depth Calibration Test. Extended BacktestConfig with stress, climate, intrinsic_time, robustness, and calibration_stability sections. Added firefly_params to optimization config. Added optimization sub-package with BayesianWeightOptimizer, FireflyWeightOptimizer, and OptimizerComparisonFramework. Added 13 validation items for v4 features. |
| v5      | `08-backtesting-framework.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Added 9 new components from Proposals 05, 06, 07: MultiDimensionalParameterScanner (grid/Sobol search), RobustnessHeatmapGenerator (time + parameter sensitivity), AdvancedSlippageModule (volume-scaled slippage with tiered submission impact), SobolRobustnessMode (quasi-random historical path perturbation), ReturnGapIntegration (execution alpha in backtest), ReproducibilityService (RDS scoring, audit-grade artifacts), MarketStressSimulator (MiFID II SMC scenarios, OTR monitoring), PriceJumpRatioBacktest (signal informativeness), RegulatoryComplianceReport (self-certification report generator). Extended configuration with robustness, advanced_slippage, reproducibility, and regulatory sections. Added 10 validation items. |
| v5      | `08-backtesting-framework.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Added 2 new components from Proposals 08, 09: PairsTradingBacktester (12m/6m formation/trading separation, look-ahead prevention), SemanticBacktester (Old NLP vs FinBERT false-positive comparison). Added 4 validation items. |
| v5.1    | `08-backtesting-framework.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Added 2 new components from Proposals 10: WalkForwardValidation (anchored walk-forward analysis, regret-based evaluation), MultiModelTournament (ILI vs XGBoost vs LSTM tournament, regime-specific benchmarking). Added 4 validation items. |
| v5.2    | Added Proposal 13 & 14 (Quantitative Engine): Advanced statistical rigor (BH-FDR, VaR Validation) integrated with strategy backtesting (Delay-d, Volume-Scaling).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
