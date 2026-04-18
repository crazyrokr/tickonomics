# Consolidated Proposal: Weight Optimization Module

**Status:** Consolidated Proposal for Tickonomics v3
**Target Tracks:** Track 3 (Analytics Worker), Track 5 (Computation Engine), Track 8 (Backtesting), Track 11 (
Deployment)

---

## v3 Integration Points

| Track    | Module                | Enhancement                                                                 |
|:---------|:----------------------|:----------------------------------------------------------------------------|
| Track 3  | Analytics Worker      | RAHF hybrid regime model, GARCH-Neural network learner                      |
| Track 5  | Computation Engine    | Bayesian/Firefly optimizer, RegimeAwareWeightingService, ambiguity bands    |
| Track 8  | Backtesting Framework | Monte Carlo parameter exploration, robustness reports, optimizer comparison |
| Track 11 | Deployment            | ScheduledCalibrationTask, auto-deployment pipeline, audit trail             |

---

## Core Proposal: Bayesian Optimization (AUTO_ADJUSTING_WEIGHTS)

**Source:** AUTO_ADJUSTING_WEIGHTS_PLAN.md

**Status:** Primary weight optimizer for v3.

### Architecture

The system functions as a closed-loop controller using Optimization Profiles:

1. **Metric Collector:** Aggregates performance data (Sharpe/Drawdown from backtests, Latency/Throughput from metrics).
2. **Fitness Evaluator:** Multi-objective scoring function for Strategy vs. Throughput.
3. **Bayesian Optimizer:** Searches the defined parameter space to maximize the profile score.
4. **Policy Injector:** Hot-reloads optimal settings via `PUT /api/v1/config`.

### Optimization Profiles

The calibration system categorizes parameters into operational profiles:

| Profile                  | Target Parameters                                        | Goal                       |
|:-------------------------|:---------------------------------------------------------|:---------------------------|
| **Profit Maximizer**     | `ili_weights`, `thresholds`, `min_move_to_cost_ratio`    | Maximize Sharpe Ratio      |
| **Throughput Maximizer** | `batch-size`, `buffer_capacity`                          | Minimize Latency/Spillover |
| **Regime Adaptor**       | `lookback_days`, `cooldown_period`, `correlation_window` | Maximize Signal Hit Rate   |

### Strategy Parameters (Computation Engine)

| Parameter                           | Optimization Rationale                                                          |
|:------------------------------------|:--------------------------------------------------------------------------------|
| `ili_lookback_days`                 | Markets evolve; optimal lookback shifts between trending/mean-reverting regimes |
| `buy_percentile`, `sell_percentile` | Regime shifts require wider/tighter signal bands                                |
| `cooldown_period`                   | Static 4-hour cooldown may miss follow-up moves or cause whip-saw trades        |
| `min_move_to_cost_ratio`            | Controls signal filter-through rate; must adapt to commission/liquidity changes |

### Ingestion Parameters (Ingestion Layer)

| Parameter                           | Optimization Rationale                                              |
|:------------------------------------|:--------------------------------------------------------------------|
| `timescaledb.ingestion.batch-size`  | Too small = high per-tx overhead; too large = memory pressure       |
| `monitor.ingestion.buffer_capacity` | Auto-scale based on available heap to prevent Chronicle Queue spill |
| `retry-backoff`                     | Adaptive backoff learns provider rate-limiting behavior             |

### Statistical Sensitivity Parameters

| Parameter                 | Optimization Rationale                                                         |
|:--------------------------|:-------------------------------------------------------------------------------|
| `correlation_window_days` | Tune alongside volatility regime to avoid overweighting stagnant relationships |
| `min_move_to_cost_ratio`  | Adjust when trading commissions or liquidity conditions change                 |

### Implementation Tracks

**Track A: Performance Metric API**

- `GET /api/v1/backtest/history` for strategy feedback.
- `GET /api/v1/metrics/ingestion` (latency/buffer usage) for throughput feedback.

**Track B: Autonomous Calibration Service**

- `ScheduledCalibrationTask` in `computation` module.
- Parameter-grouping for different profiles (Profit vs. Throughput).
- Background thread execution via `StructuredTaskScope`.

**Track C: Validation and Auto-Deployment**

- Validation Guard: candidate configs run through "Simulated Validation Window" (last 30 days out-of-sample).
- Auto-Deployment: trigger `PUT /api/v1/config` if performance exceeds thresholds.
- Audit Trail: persist automated changes to `config_snapshots` with strategy metadata.

### Configuration

```yaml
monitor:
  calibration:
    enabled: true
    trigger_interval: "30d"
    min_improvement_sharpe: 0.1
    min_improvement_throughput_pct: 0.05
    lookback_window: "180d"
    validation_window: "30d"
```

### Safety Constraints

- **Max Drift:** Constrain drift per update (weights/thresholds < 10% change).
- **Operational Guardrail:** Prevent throughput tuning if Chronicle Queue depth is nearing critical failure.
- **Manual Approval Gate:** System proposes changes in Admin Dashboard; manual approval required to commit.

### Validation Criteria

- [ ] "Dry Run" mode generates proposals without applying them.
- [ ] `active_weights` and `config_snapshots` reflect automated updates correctly.
- [ ] Zero NaN/Infinity in KPI calculations post-update.
- [ ] Convergence within 500 iterations on standard ILI optimization problems.

---

## Alternative A: Firefly Algorithm Optimizer (FIREFLY_ALGORITHM)

**Source:** FIREFLY_ALGORITHM_OPTIMIZATION_PROPOSAL.md (SSRN-2639019)

**Status:** Pluggable alternative optimizer -- A/B testing candidate against Bayesian.

### Module Structure

- `FireflyWeightOptimizer` implementation in `computation/src/main/java/com/tickonomics/computation/backtest/`.
- Follows the pseudo-code from SSRN-2639019.

### Configuration

```yaml
monitor:
  backtest:
    optimization:
      method: "bayesian"  # or "firefly"
      firefly_params:
        population_size: 15
        generations: 50
        randomization_alpha: 0.2
        light_absorption: 1.0
```

### Characteristics

- Population-based metaheuristic optimization.
- Faster convergence for high-dimensional parameter spaces in specific market regimes.
- Robust alternative to Genetic Algorithms and Particle Swarm Optimization for weight tuning.
- Uses the same dynamic weight redistribution logic during fitness evaluation.

### A/B Testing Protocol

- Compare FA convergence speed and resulting Sharpe ratios against Bayesian optimizer.
- Test across same ILI weight optimization problems with identical fitness functions.
- Measure: convergence time, final Sharpe ratio, parameter stability across regimes.

### Validation Criteria

- [ ] Firefly optimizer converges on the same ILI weight problems as Bayesian.
- [ ] Performance parity or improvement on at least 3 of 5 benchmark regimes.
- [ ] Configurable population size and generation count produce predictable compute-time scaling.

---

## Alternative B: Regime-Aware Hybrid Forecasting Framework (RAHF)

**Source:** PROPOSAL_RAHF_FRAMEWORK_INTEGRATION.md (Guo, 2025)

**Status:** Pluggable regime-aware optimizer -- A/B testing candidate, Phase 2 research track.

### Core Concept

Combines linear time series models (GARCH) with nonlinear machine learning models (deep learning) under a latent-regime
view of the market. Directly improves upon the current `RegimeDetector` which uses K-means on volatility.

### Module Structure

- Hybrid model in the Analytics Worker combining linear GARCH-type models with a nonlinear learner.
- Replaces K-means `RegimeDetector` with GARCH-Neural network hybrid.

### Key Components

1. **Enhanced RegimeDetector:** Hybrid GARCH + neural network for liquidity regime classification.
2. **Task-Model Mapping:** Structured mapping between financial tasks (forecasting, volatility prediction, dynamic
   portfolio allocation) and model classes.
3. **Empirical Protocol:** Template for evaluating time series models, adapted into CI/CD and backtesting framework.
4. **MSFE Measurement:** Mean Squared Forecast Error appropriately measured for non-stationary markets.

### A/B Testing Protocol

- Compare RAHF regime classification accuracy against K-means and pure GARCH.
- Measure: regime transition detection latency, classification accuracy, downstream Sharpe ratio.
- Test on same historical stress periods (2008, 2019 repo spike, 2020 COVID).

### Validation Criteria

- [ ] RAHF regime detection outperforms K-means on at least 3 of 5 stress event classifications.
- [ ] Hybrid model training completes within 4-hour window on standard compute.
- [ ] MSFE improvement over pure GARCH is statistically significant (p < 0.05).

---

## Complementary Additions

### Performance Tuning Profiles (PERFORMANCE_TUNING)

**Source:** PERFORMANCE_TUNING_PROPOSALS.md

These parameter categories are managed by the optimization profiles defined in the core Bayesian optimizer:

| Optimization Profile     | Variables                           | Goal                       |
|:-------------------------|:------------------------------------|:---------------------------|
| **Profit Maximizer**     | Weights, Thresholds, Min Move Ratio | Maximize Sharpe Ratio      |
| **Throughput Maximizer** | Batch Size, Buffer Capacity         | Minimize Ingestion Latency |
| **Regime Adaptor**       | Lookback Windows, Cooldown          | Maximize Hit Rate          |

The `ScheduledCalibrationTask` is extended to support these grouped profiles. Each profile operates independently but
shares the same validation pipeline and audit trail.

**Validation Criteria:**

- [ ] Each profile independently converges without interfering with other profiles.
- [ ] Throughput Maximizer reduces ingestion latency by at least 15% during peak volume.

---

### Efficiency-Depth Dynamic Weighting (FINANCE_GROWTH_EFFICIENCY_ALIGNMENT)

**Source:** SSRN-1001177 (Trew, 2007)

**Objective:** Adjust ILI weights based on historical "efficiency regimes" rather than treating the finance-growth
relationship as static.

**Module: RegimeAwareWeightingService (Track 5)**

- Extends `RegimeDetector` to map weights for `IliCalculator` based on efficiency regimes.
- Adjusts weights for `Z_spread` and `Z_vol` when the system identifies periods of high structural funding friction (low
  efficiency).
- Reinforces focus on financial efficiency/friction (spreads, repo rates) over aggregate throughput.

**Backtesting Integration (Track 8):**

- "Efficiency-Depth Calibration" test case compares "Default Weights" vs. "Regime-Aware Weights" over high-stress
  historical periods (2008, 2019 repo spike).
- Verifies that efficiency indicators alone (spreads, repo rates) produce realistic signal quality without requiring
  aggregate depth.

**Validation Criteria:**

- [ ] Regime-aware weights outperform static weights on Sharpe ratio during stress periods.
- [ ] Efficiency-only indicators produce signal quality within 5% of full-parameter models.

---

### Ambiguity Aversion Integration (AMBIGUITY_AVERSION_INTEGRATION)

**Source:** SSRN-2527808 (Donnelly)

**Objective:** Add formal "Ambiguity Aversion" guardrails to handle model misspecification in ILI calculation and signal
generation.

**Ambiguity-Adjusted ILI (Track 5):**

- Instead of a single ILI value, compute an "Uncertainty Band" (upper and lower bounds) based on variance of input
  series.
- If gap between bounds exceeds threshold, mark ILI status as `UNCERTAIN` and suppress signals.

**Robust Optimization (Track 8):**

- `WeightOptimizer` supports "Worst-Case Optimization" mode minimizing downside of model error rather than just Sharpe
  ratio.
- "Robustness Report" in backtest results shows strategy performance under +/-10% deviations in input model parameters.

**Implementation:**

1. `calculate_uncertainty_band()` in `IliCalculator`.
2. ILI status set to `UNCERTAIN` when band width exceeds threshold.
3. "Worst-Case Optimization" mode added to `WeightOptimizer` CLI.

**Validation Criteria:**

- [ ] Uncertainty bands correctly widen during known high-volatility periods.
- [ ] Worst-case optimization produces strategies with lower maximum drawdown.
- [ ] Robustness reports quantify sensitivity to each input parameter.

---

## A/B Testing Matrix

| Optimizer             | Test Problem                                | Metrics                                    | Regimes        |
|:----------------------|:--------------------------------------------|:-------------------------------------------|:---------------|
| Bayesian (Core)       | ILI weight optimization                     | Convergence speed, Sharpe, stability       | All 5 regimes  |
| Firefly (Alt A)       | ILI weight optimization                     | Convergence speed, Sharpe, stability       | All 5 regimes  |
| RAHF (Alt B)          | Regime classification + weight optimization | Classification accuracy, downstream Sharpe | Stress events  |
| Bayesian + Ambiguity  | Worst-case ILI optimization                 | Max drawdown, Sharpe                       | Stress periods |
| Bayesian + Efficiency | Regime-aware weight optimization            | Hit rate, Sharpe                           | Full history   |

---

## Source Proposals

1. `AUTO_ADJUSTING_WEIGHTS_PLAN.md` - Bayesian optimization with 3 profiles, YAML configuration, validation pipeline
2. `FIREFLY_ALGORITHM_OPTIMIZATION_PROPOSAL.md` - Firefly metaheuristic optimization (SSRN-2639019)
3. `PERFORMANCE_TUNING_PROPOSALS.md` - Optimization profiles for strategy, ingestion, and statistical parameters
4. `FINANCE_GROWTH_EFFICIENCY_ALIGNMENT.md` - Efficiency vs depth dynamic weighting (SSRN-1001177)
5. `AMBIGUITY_AVERSION_INTEGRATION_PROPOSAL.md` - ILI uncertainty bands, worst-case optimization (SSRN-2527808)
6. `PROPOSAL_RAHF_FRAMEWORK_INTEGRATION.md` - Regime-Aware Hybrid Forecasting combining GARCH + deep learning (Guo,
   2025)
