# Track 5: Computation Engine (Analytics & Signals)

**Phase:** Phase 2
**Can start:** After Track 2 (database schema exists)
**Blocks:** Tracks 8 (Backtesting), 10 (Demo)
**Depends on:** Tracks 1, 2, 3 (analytics worker for ADF/Granger)

---

## Objective

Implement the core analytical engine: ILI calculation, correlation analysis, signal generation,
and all KPI computations. Uses TA-Lib Java for in-process statistics and the Python analytics
worker (Track 3) for econometrics functions not available in TA-Lib.

**Analysis findings applied (v1):**

- **Finding 2 (AIC Loop in Worker):** The AIC lag selection loop now runs entirely in the
  Python analytics worker. Java sends data once with `max_lag`, worker returns optimal result.
  `AicLagSelector` in Java is a thin client that calls the worker's `/causality` endpoint.
- **Finding 3 (Proxy Divergence Guard):** `IntradayProxyService` includes a divergence guard
  that detects T-Bill/SOFR dislocation and suppresses proxy-derived signals.
- **Finding 6 (Dynamic Weighting):** Zero-variance components return `NaN` instead of `0.0`.
  `IliCalculator` redistributes weight proportionally among valid components and sets
  `DEGRADED_COMPONENT_STALE` status.

**Improvement proposals (v3):**

- **Proposal #3 (Pre-computed KPI Views):** `CorrelationEngine` reads rolling correlation and
  beta from pre-computed TimescaleDB continuous aggregates (`kpi_rolling_correlation`,
  `kpi_rolling_beta`) for historical data. Only the current window's computation uses TA-Lib.
  This reduces per-cycle TA-Lib calls from O(symbols x metrics) to O(symbols) for the live window.
- **Proposal #5 (CDM-typed Inputs):** All computation components receive CDM-typed data
  (`CdmRateSnapshot`, `CdmTick`) from the ingestion layer. No source-specific types in the
  computation module. `NormalizationService` and `IliCalculator` operate on CDM enums and types.

**Improvement proposals (v4):**

- **Proposal 01 (Data Quality):** Enhanced `ProxyDivergenceGuard` via autoencoder anomaly
  detection. Detects erroneous shifts and shape corruptions in ILI component vectors,
  providing earlier warning of T-Bill/SOFR dislocation even before the 5-day correlation
  breaks down.
- **Proposal 02 (Participation Governance):** `ParticipationGovernanceService` introduces
  formal signal decomposition with explicit admissibility checks and suppression reason
  logging. `LiquidityStressTestModule` simulates extreme liquidity withdrawal scenarios.
- **Proposal 03 (Regime Detection):** GARCH regime detector replaces K-means as PRIMARY
  method. CNN-LSTM and QED detectors as pluggable alternatives. `ExogenousShockDetector`
  adds `EXOGENOUS_SHOCK` regime. `AdaptiveIliCalculator` enables self-tuning weights via
  online SGD optimizer. `SurpriseIndicator` adds information-theoretic anomaly scoring.
  `ClimateSensitivityFactor` and `ClimateRiskGuard` adjust ILI for energy mix shifts.
- **Proposal 04 (Weight Optimization):** `BayesianWeightOptimizer` provides closed-loop
  parameter optimization with safety constraints. `FireflyWeightOptimizer` as population-based
  alternative. `RegimeAwareWeightingService` adjusts weights for efficiency regimes.
  Ambiguity-Adjusted ILI computes uncertainty bands. `ScheduledCalibrationTask` provides
  autonomous calibration with validation pipeline.
- **Proposal 05 (Backtesting Robustness):** `DiscreteMonitoringCorrection` applies
  Broadie-Kou-Glasserman Riemann Zeta-based beta correction to signal thresholds.
  `ReturnGapCalculator` separates execution alpha from static holdings return.
  `StrategicRunService` groups consecutive buy/sell child orders for pattern identification.
  `InformationEfficiencyAnalyzer` computes Price Jump Ratio for signal informativeness.
  `PriceImpactKpi` tracks intended vs. executed move at submission time.
- **Proposal 06 (Risk Guardrails):** `AumfScenarioEngine` implements five-stage uncertainty
  management framework with scenario-based signal suppression. `AumfStatus` enum adds
  SAFE_MODE, SUSPENDED_UNCERTAINTY, PROCEED_CAUTIOUSLY to SignalStatus. GEX-weighted regime
  detection integrates options gamma exposure into `RegimeDetector`.
- **Proposal 07 (Liquidity Analysis):** `ComovementTrigger` integrates liquidity comovement
  factor into LSI. `PhantomLiquidityService` computes PLI discounting ILI for unreliable depth.
  `ToxicityAdjustedIli` adjusts ILI sensitivity for toxic market conditions.
  `AlgorithmicIntensityMetric` provides message-based AT proxy with quintile bucketing.
  `BehaviouralRiskProcessor` computes BRI from OFI, spread volatility, and sentiment.
  `TimeOfDayThresholdManager` makes signal thresholds adaptive by trading session.
  `SessionRangeService` enhanced with Defining Range calculation and news confidence decay.
  `LiquiditySourceClassifier` estimates trader type dominance. `LiquidityMeanReversionSpeed`
  measures price impact decay after AT activity spikes. `LiquidityPremiumFactor` incorporates
  AT intensity as a coefficient in cost-benefit models.

---

## Components to Implement

### 1. NormalizationService

Z-score over tiered configurable windows using `TalibAdapter`:

```
Z = (value - TA_SMA(value, lookback)) / TA_STDDEV(value, lookback)
```

**Tiered Lookback Strategy:**

| Component Type | Examples           | Lookback | Rationale                       |
|:---------------|:-------------------|:---------|:--------------------------------|
| Macro          | RRP_Volume, WALCL  | 252 days | Structural balance-sheet trends |
| Flow           | EFFR - IORB Spread | 60 days  | Medium-term liquidity cycles    |
| Volatility     | SOFR_Volatility    | 20 days  | High-frequency stress detection |

**Edge cases:**

- Insufficient data (< 10 points): return `NaN` with `DATA_INSUFFICIENT` status.
- Zero variance (`TA_STDDEV` < 0.0001): return `NaN` (not `0.0`) so that `IliCalculator`
  can apply dynamic weight redistribution (Finding 6). Returning `0.0` would mask stale
  data as "healthy/neutral" liquidity.

### 2. IliCalculator

ILI formula with validated weights and tiered lookback windows:

```
ILI = w1 * Z_rrp + w2 * Z_spread - w3 * Z_vol
```

**Dynamic Weight Redistribution (Finding 6):**
If any component z-score is `NaN` (zero variance or insufficient data), that component is
excluded and its weight is redistributed proportionally among the remaining valid components:

```
If Z_vol is NaN:
    effective_w1 = w1 / (w1 + w2)
    effective_w2 = w2 / (w1 + w2)
    ILI = effective_w1 * Z_rrp + effective_w2 * Z_spread
    status = DEGRADED_COMPONENT_STALE
```

- `active_weights` JSONB stored in `ili_history` to record actual weights used after redistribution.
- If ALL components are `NaN`, ILI is not calculated, return `DATA_INSUFFICIENT`.

**Constraints (validated at startup):**

- `w1 + w2 + w3 = 1.0` (tolerance +/- 0.001).
- Default weights: `{ rrp: 0.4, spread: 0.4, vol: 0.2 }`.
- All weights in `[0.0, 1.0]`.
- If ALL components have < 10 data points, ILI not calculated, return `DATA_INSUFFICIENT`.

**Historical note:** IOER renamed to IORB in July 2021. FRED series code is `IORB`. Use `IORB` consistently.

### 3. ADF Stationarity Check

- Calls analytics worker (Track 3) -> `obb.econometrics.unit_root`.
- Returns `adfstat` and `pvalue`.
- If `p > 0.05`, differenced again with warning logged.
- Applied at startup to most recent 252-day window.
- **Fallback:** If analytics worker unreachable, use last known stationarity result with warning.

### 4. AicLagSelector (Thin Client, Finding 2)

The AIC lag selection loop runs entirely in the Python analytics worker (Track 3).
Java sends data once with `max_lag` and receives the optimal result.

1. Send data + `max_lag_order` to analytics worker via Arrow IPC.
2. Worker internally iterates over candidate lags, computes AIC, returns optimal lag + full result.
3. Java receives: `{ optimal_lag, aic_values, test_result }`.

**Fallback:** If analytics worker unreachable, use default lag of 3 with warning.

### 5. CorrelationEngine

**Database pre-computed (v3, Proposal #3):**

- Historical rolling correlation read from `kpi_rolling_correlation` continuous aggregate.
- Historical rolling beta read from `kpi_rolling_beta` continuous aggregate.
- Eliminates redundant TA-Lib recomputation for historical windows that haven't changed.

**In-process (TA-Lib) -- live window only:**

- `TA_CORREL` for the current rolling window (default 20 trading days) -- only for today's data.
- `TA_BETA` for the current rolling window (default 60 days) -- only for today's data.
- Input: differenced (stationary) series using `TA_ROC`.
- Data aligned via TimescaleDB `time_bucket()` queries.
- Output per trading day: `{ correlation, p_value, sample_size }`.
- Result written to `correlation_outputs` hypertable -- feeds the pre-computed aggregate.

**Cross-process (Analytics Worker):**

- Full OLS statistics (r_squared, std_error) via `obb.econometrics.ols_regression`.

**Significance threshold:** Only correlations with `p_value < 0.05` AND `|correlation| > 0.3`
are surfaced. Others flagged `INSIGNIFICANT`.

### 6. GrangerCausalityTest

1. Uses `AicLagSelector` (thin client) which calls analytics worker's `/causality` endpoint
   with `max_lag` -- worker returns optimal lag and full causality result (Finding 2).
2. Outputs: `{ f_statistic, p_value, lag_order, direction }`.
4. Direction enum: `FUNDING_LEADS_EQUITY`, `EQUITY_LEADS_FUNDING`, `NO_RELATIONSHIP`.

**Fallback:** If analytics worker unreachable, skip Granger, use correlation-only analysis.

### 7. RegimeDetector

- `TA_BBANDS` on ILI series for volatility regime detection.
- Bandwidth = (upper - lower) / middle.
- K-means clustering (3 clusters) on last 252 days of daily ILI volatility (`TA_STDDEV` of changes).
- Regime classification:
    - Low: bandwidth < 0.5x median bandwidth -> tighten thresholds to 2%/98%.
    - Normal: between 0.5x and 2x.
    - High: bandwidth > 2x median bandwidth -> widen thresholds to 10%/90%.

**v4 Delegation Model (Proposal 03):**
`RegimeDetector` now delegates to the configured implementation rather than always using K-means.
The active method is determined by `monitor.strategies.liquidity_pivot.regime_detection.method`:

| Method            | Delegated Implementation          | Endpoint             |
|:------------------|:----------------------------------|:---------------------|
| `garch` (PRIMARY) | `GarchRegimeDetector`             | `/risk/garch-regime` |
| `cnn_lstm`        | `DlRegimeDetector`                | `/regime/hybrid`     |
| `qed`             | `QEDRegimeDetector`               | `/regime/qed`        |
| `kmeans`          | Internal K-means (v1/v3 behavior) | N/A (in-process)     |

If the configured external method is unavailable, falls back to internal K-means with warning.

### 8. IntradayProxyService

SOFR is published T+1 at 8:00 AM ET, leaving a gap during intraday trading hours:

- Uses **3-month Treasury Bill yields** as real-time proxy for funding pressure.
- T-Bill data from direct NY Fed client (Finding 5) `treasury_rates` endpoint.
- Proxy-adjusted ILI calculated intraday using T-Bill proxy in place of SOFR component.
- Signals using proxy data flagged with `SPECULATIVE_STALE_MACRO`.
- Once official SOFR published, recalculated and flag cleared or confirmed.

**Proxy Divergence Guard (Finding 3):**
During "Flight to Quality" events, T-Bill yields drop (buying pressure) while SOFR spikes
(collateral scarcity). The proxy would show "improving liquidity" while the market seizes up.

Detection:

- Maintain a rolling 5-day correlation between SOFR changes and T-Bill yield changes.
- If the T-Bill yield trend diverges > 2 standard deviations from the expected relationship
  (based on the 5-day correlation), flag the ILI as `DISLOCATED`.
- Record the event in `proxy_divergence_events` table with: `sofr_value`, `tbill_proxy_value`,
  `correlation_5d`, `divergence_score`.

Action:

- Suppress all automated signals derived from proxy data until the next official SOFR publication.
- Signal status set to `DISLOCATED` (new status).
- Resolution: cleared when official SOFR published and 5-day correlation restores to normal range,
  or manually cleared by operator.

**v4 Enhanced Proxy Divergence Guard (Proposal 01: Data Quality):**
The proxy divergence guard is enhanced with autoencoder-based anomaly detection for earlier
warning. The autoencoder, served by the analytics worker `/anomaly/detect` endpoint, provides
sensitivity to "erroneous shifts" and "shape corruptions" in ILI component vectors.

- When autoencoder detects an anomaly (`is_suspect_anomaly = true` or `is_anomaly = true`),
  the proxy guard flags divergence even before the 5-day correlation window breaks down.
- The anomaly-based check supplements (not replaces) the existing correlation-based check.
- Both checks run in parallel: if either triggers, ILI is flagged `DISLOCATED`.
- `monitor.anomaly.detection.latency` tracks autoencoder round-trip latency.

### 9. SignalGenerator

**Percentile-rank adaptive thresholds:**

1. Compute ILI percentile rank over rolling window (default 252 trading days).
2. Buy Signal: ILI percentile < `buy_percentile` (default 5%).
3. Sell Signal: ILI percentile > `sell_percentile` (default 95%).
4. Per-regime threshold adjustment via `RegimeDetector`.

**Filters:**

- **Look-ahead bias prevention:** Rolling window offset by 1 day -- today's ILI from data up to yesterday's close.
- **Minimum volatility filter:** If ILI ADR (Average Daily Range) over last 20 days < `min_ili_adr` (default 0.15),
  suppress signals.
- **Cooldown period:** After signal fires, no new signal of same direction for `cooldown_period` (default 4h).
  Configurable override.
- **Transaction cost modeling:**
    - Each signal includes `estimated_slippage` (configurable bps, default 5) + `commission_per_share` (default 0.005).
    - Signal expected move must exceed `2 * estimated_cost` to be actionable.
    - Non-actionable signals logged with status `COST_EXCEEDS_EXPECTED_MOVE`.

**Signal Status Enum:**

- `ACTIONABLE` -- passed all filters, cost-justified, fresh data.
- `SPECULATIVE_STALE_MACRO` -- using intraday proxy, awaiting official SOFR.
- `COST_EXCEEDS_EXPECTED_MOVE` -- direction valid but not economically viable.
- `COOLDOWN` -- duplicate direction suppressed.
- `INSUFFICIENT_DATA` -- not enough data points in lookback window.
- `DISLOCATED` -- proxy divergence detected, signals suppressed.
- `UNCERTAIN` -- v4: ambiguity band exceeded, confidence too low for actionable signal.

### 10. KpiProcessor

Orchestrates all KPI calculations and persists to TimescaleDB:

| KPI                        | Calculation                                                                       | Output                             |
|:---------------------------|:----------------------------------------------------------------------------------|:-----------------------------------|
| **Liquidity Stress Index** | `(SOFR - Fed_Target_Upper) / (Fed_Target_Upper - Fed_Target_Lower)`, z-scored 60d | Scalar                             |
| **Repo/Equity Beta**       | `TA_BETA(stock_return, repo_rate_change, 60d)` + analytics worker OLS             | `{beta, std_error, r_squared}`     |
| **RRP Drain Velocity**     | `(RRP_t - RRP_t-N) / N`, N=20 + `TA_LINEARREG_SLOPE` trend                        | `{velocity, acceleration, period}` |
| **Systemic Risk Heatmap**  | 4-axis z-scored matrix: tri-party vs GCF, SOFR pctl, TGCR vs BGCR, TGA change     | JSON matrix                        |
| **Volatility Regime**      | `TA_BBANDS` bandwidth on ILI                                                      | `{regime, bandwidth, percentile}`  |

### 11. AlertManager

- Evaluates signals from `SignalGenerator`.
- Dispatches alerts based on `alert_rules` table configuration.
- Logs all signal evaluations to `signal_log` table.

### 12. CalculationGuard

- Every KPI result passes `Double.isFinite()`.
- Invalid results logged and replaced with `NaN`.
- Prevents `NaN`/`Infinity` from propagating through the system.

### 13. GarchRegimeDetector (v4 -- Proposal 03: PRIMARY regime engine)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/GarchRegimeDetector.java`

Consumes GARCH forecasts from the Python analytics worker `/risk/garch-regime` endpoint.

- Regime classification based on conditional volatility percentile:
    - `LOW_VOL`: conditional volatility < 25th percentile.
    - `NORMAL`: conditional volatility between 25th and 75th percentile.
    - `HIGH_VOL`: conditional volatility > 75th percentile.
- Percentile thresholds configurable via `garch_percentile_low` and `garch_percentile_high`.
- Returns `{ regime, conditional_volatility, forecast_1d, percentile }` to `RegimeDetector`.
- This is the PRIMARY regime detection method in v4, replacing K-means as the default.
- **Fallback:** If the analytics worker is unreachable, `RegimeDetector` falls back to
  internal K-means with a warning.

### 14. DlRegimeDetector (v4 -- Proposal 03: Alternative A)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/DlRegimeDetector.java`

Consumes the CNN-LSTM hybrid ensemble endpoint from the Python analytics worker `/regime/hybrid`.

- Returns regime classification with confidence score and transition probabilities.
- Pluggable replacement for GARCH via config flag `regime_detection.method: "cnn_lstm"`.
- Provides richer output: `{ regime, confidence, transition_probability: {LOW_VOL, NORMAL, HIGH_VOL} }`.
- Intended for A/B testing against GARCH regime engine.
- **Fallback:** If the endpoint is unavailable, falls back to GARCH (which in turn may fall
  back to K-means).

### 15. QEDRegimeDetector (v4 -- Proposal 03: Alternative B)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/QEDRegimeDetector.java`

Uses capital flow proxies for metastability and crash probability estimation.

- **Capital flow proxies:** RRP Drain Velocity and TGA Balance Change.
- **Regime classification:**
    - `METASTABLE`: system is near a potential barrier but has not crossed it.
    - `UNSTABLE`: barrier distance is critically low, crash probability elevated.
    - `STABLE`: system is well within the potential well.
- Consumes `/regime/qed` endpoint from the Python analytics worker.
- Provides `CrashProbabilityScore` as an additional risk metric.
- Pluggable replacement for GARCH via config flag `regime_detection.method: "qed"`.
- **Fallback:** If the endpoint is unavailable, falls back to GARCH.

### 16. ExogenousShockDetector (v4 -- Proposal 03: Natural Disaster)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/ExogenousShockDetector.java`

Adds a new regime: `EXOGENOUS_SHOCK` that proactively flags ILI as `DISLOCATED` even before
the 5-day correlation breaks down.

- Triggers from `DisasterAlertClient` alerts sourced from USGS (earthquakes) and GDACS
  (global disaster alerts).
- When an exogenous shock is detected:
    1. `EXOGENOUS_SHOCK` regime is set immediately.
    2. ILI is flagged `DISLOCATED` within 30 seconds of alert receipt.
    3. All automated signals are suppressed until the shock is resolved.
- Resolution criteria: either the shock alert expires (configurable TTL) or an operator
  manually clears it.
- Operates independently of the configured regime detection method -- it overrides whatever
  the primary method reports.

### 17. CrashProbabilityScore (v4 -- Proposal 03: QED crash probability)

**Location:** `computation/src/main/java/com/tickonomics/computation/risk/CrashProbabilityScore.java`

Based on proximity of the current ILI/Price state to the potential barrier in the QED model.

- Computed by `QEDRegimeDetector` as part of its `/regime/qed` response.
- Score in `[0.0, 1.0]`: 0.0 means no crash risk, 1.0 means barrier has been breached.
- `barrier_distance`: normalized distance to the nearest potential barrier.
- When `crash_probability > 0.5`, signal allocation is reduced by a proportional factor.
- Spikes during historical SOFR/T-Bill dislocation periods.

### 18. AdaptiveIliCalculator (v4 -- Proposal 03: Online Calibration)

**Location:** `computation/src/main/java/com/tickonomics/computation/ili/AdaptiveIliCalculator.java`

Applies weight deltas from the Python online optimizer (SGD) to produce self-tuning ILI weights.

- Consumes `/risk/weight-delta` endpoint from the Python analytics worker.
- Weight deltas are bounded: each update shifts any single weight by at most 10% of its
  current value (`max_drift_pct: 10`).
- After applying deltas, weights are re-normalized to sum to 1.0.
- `WeightedWeightStore` maintains two weight sets:
    - **Base weights:** from configuration (the static defaults).
    - **Calibrated weights:** adjusted by online optimizer deltas.
- If the optimizer is unavailable or returns an error, calibrated weights remain unchanged
  (zero delta applied).
- Calibration weights are persisted and survive restarts.

### 19. WeightedWeightStore (v4 -- Proposal 03: Calibrated + base weights)

**Location:** `computation/src/main/java/com/tickonomics/computation/ili/WeightedWeightStore.java`

Maintains "calibrated" weights alongside "base" configuration weights.

- **Base weights:** loaded from `monitor.strategies.liquidity_pivot.ili_weights` at startup.
- **Calibrated weights:** persisted in database, initialized from base weights on first run.
- Provides atomic access to the current effective weight set.
- Exposes methods to apply weight deltas and reset to base weights.
- Logs all weight changes for audit.

### 20. SurpriseIndicator (v4 -- Proposal 03: Event-Based Time)

**Location:** `computation/src/main/java/com/tickonomics/computation/signal/SurpriseIndicator.java`

Information theory (entropy/surprise) for unlikeliness of current price trajectories.

- Computes a surprise score based on the information content of recent price movements
  relative to the historical distribution.
- Surprise = `-log2(P(observation))` where P is estimated from the empirical distribution
  of recent ILI component changes.
- High surprise indicates anomalous or rare behavior.
- Factors into position sizing: when surprise exceeds a configurable threshold, position
  size is reduced proportionally.
- Output: `{ surprise_score, entropy_estimate, position_size_modifier }`.
- Supplementary signal filter -- does not suppress signals entirely but reduces exposure
  when behavior is anomalous.

### 21. ClimateSensitivityFactor (v4 -- Proposal 03: Climate-Liquidity)

**Location:** `computation/src/main/java/com/tickonomics/computation/ili/ClimateSensitivityFactor.java`

Climate sensitivity factor for ILI threshold adjustments.

- Consumes `/climate/simulate` endpoint from the Python analytics worker.
- As global energy mix shifts (fossil -> renewable), funding market dynamics change.
- Adjusts ILI thresholds based on projected liquidity impact from climate policy scenarios.
- Factor is multiplicative: `adjusted_threshold = base_threshold * (1 + climate_factor)`.
- Updated at configurable intervals (default: daily).
- **Fallback:** If climate model is unavailable, factor defaults to 0.0 (no adjustment).

### 22. ClimateRiskGuard (v4 -- Proposal 03: Climate-Liquidity)

**Location:** `computation/src/main/java/com/tickonomics/computation/risk/ClimateRiskGuard.java`

Modifies ILI confidence based on carbon intensity weight.

- Cross-references `ClimateSensitivityFactor` with the current ILI computation.
- When climate-adjusted thresholds diverge significantly from base thresholds, ILI confidence
  is reduced, widening the ambiguity band.
- Produces a `confidence_modifier` in `[0.0, 1.0]` that scales the ILI certainty.
- Logged as part of the ILI computation audit trail.

### 23. SessionRangeService (v4 -- Proposal 03)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/SessionRangeService.java`

Session-aware range calculation for regime detection.

- Computes volatility and liquidity ranges segmented by trading session:
    - **Asian** (00:00-09:00 UTC)
    - **European** (07:00-16:00 UTC)
    - **US** (13:00-22:00 UTC)
- Regime thresholds are adjusted per session to account for intraday liquidity patterns.
- `RegimeDetector` implementations may use session-specific thresholds for more accurate
  classification during low-liquidity overnight periods.

### 24. ParticipationGovernanceService (v4 -- Proposal 02: Participation Governance)

**Location:** `computation/src/main/java/com/tickonomics/computation/governance/ParticipationGovernanceService.java`

Renames the decision orchestration layer to emphasize participation admissibility over
simple signal generation.

- Evaluates all admissibility conditions before a signal becomes actionable.
- Logs which stage of decomposition caused suppression.
- Formal signal decomposition: `Signal_t = g(s_t, h_t) * b(r_hat_t)`
    - `g(s_t, h_t)` = admissibility check (ILI status, proxy divergence, regime checks).
    - `b(r_hat_t)` = signal allocation (buy/sell/hold).
- `g(s_t, h_t)` returns 0 (inadmissible) or 1 (admissible). When 0, the signal is suppressed
  regardless of `b(r_hat_t)`.
- Explicit suppression reason logging:
    - "Forecast valid but Participation inadmissible due to proxy dislocation."
    - "ILI status degraded; participation suppressed."
    - "Regime check failed; participation inadmissible."
    - "Ambiguity band exceeded; participation suppressed due to UNCERTAIN status."
    - "Exogenous shock active; participation suspended."
- Counter `monitor.participation.suppressed.total` tagged by reason.

### 25. LiquidityStressTestModule (v4 -- Proposal 02: Liquidity Stress)

**Location:** `computation/src/main/java/com/tickonomics/computation/stress/LiquidityStressTestModule.java`

Swiss Franc cap removal model for extreme liquidity withdrawal.

- Simulates historical stress scenarios:
    - **Swiss Franc 2015:** SNB cap removal. AT liquidity disappearance, spread widening,
      uninformative volatility, cascading margin calls.
    - **Repo Spike 2019:** Overnight repo rate spike to 10%. Secured funding market seizure.
    - **COVID 2020:** Global liquidity freeze, Treasury market dysfunction.
- For each scenario, computes:
    - Strategy drawdown under simulated conditions.
    - Time to recovery.
    - Signal false-positive rate during stress.
- Results stored and surfaced as a resilience score.
- Runs on configurable schedule or on-demand via admin endpoint.
- Timer `monitor.stress.test.duration` tracks execution.

### 26. BayesianWeightOptimizer (v4 -- Proposal 04: PRIMARY)

**Location:** `computation/src/main/java/com/tickonomics/computation/optimization/BayesianWeightOptimizer.java`

Closed-loop controller with optimization profiles for automated parameter tuning.

**Optimization Profiles:**

| Profile              | Parameters                                               | Objective         |
|:---------------------|:---------------------------------------------------------|:------------------|
| Profit Maximizer     | `ili_weights`, thresholds, `min_move_to_cost_ratio`      | Maximize Sharpe   |
| Throughput Maximizer | `batch-size`, `buffer_capacity`                          | Minimize Latency  |
| Regime Adaptor       | `lookback_days`, `cooldown_period`, `correlation_window` | Maximize Hit Rate |

**Safety constraints:**

- Maximum 10% drift per update (`max_drift_pct: 10`).
- Manual approval gate: proposed changes require operator confirmation before application.
- Validation window: 30-day out-of-sample test before proposed weights go live.

**Fitness function:** Sharpe ratio computed by `BacktestEngine`.

**Cross-validation:** Via TA-Lib (in-process), FinanceToolkit (analytics worker), and OpenBB
(analytics worker) to ensure consistency.

### 27. FireflyWeightOptimizer (v4 -- Proposal 04: Alternative)

**Location:** `computation/src/main/java/com/tickonomics/computation/optimization/FireflyWeightOptimizer.java`

Population-based metaheuristic for weight optimization.

- A/B testing candidate against `BayesianWeightOptimizer`.
- Firefly algorithm parameters:
    - `population_size`: 15
    - `generations`: 50
    - `randomization_alpha`: 0.2
    - `light_absorption`: 1.0
- Each firefly represents a candidate weight set. Fitness (Sharpe ratio) determines brightness.
- Brighter fireflies attract dimmer ones, converging the population toward optimal weights.
- Same safety constraints as `BayesianWeightOptimizer`: max 10% drift, manual approval, 30d OOS validation.

### 28. RegimeAwareWeightingService (v4 -- Proposal 04: Efficiency-Depth)

**Location:** `computation/src/main/java/com/tickonomics/computation/optimization/RegimeAwareWeightingService.java`

Adjusts ILI weights based on efficiency regimes.

- During periods of high structural funding friction (detected by regime classification),
  adjusts `Z_spread` and `Z_vol` weights upward relative to `Z_rrp`.
- Efficiency regime classification:
    - **High efficiency:** standard weights from configuration.
    - **Medium efficiency:** spread and volatility weights boosted by 20%.
    - **Low efficiency:** spread and volatility weights boosted by 40%, RRP weight reduced.
- Weight adjustments are temporary and revert when efficiency regime normalizes.

### 29. Ambiguity-Adjusted ILI (v4 -- Proposal 04: Ambiguity Aversion)

Instead of a single ILI value, computes an "Uncertainty Band" with upper and lower bounds.

- The uncertainty band reflects the range of possible ILI values given parameter uncertainty.
- If the gap between upper and lower bounds exceeds `band_threshold` (default 0.5), ILI
  status is set to `UNCERTAIN` and signals are suppressed.
- Worst-Case Optimization mode in `BayesianWeightOptimizer`: optimizes for the worst-case
  performance within the uncertainty band rather than the point estimate.
- Robustness Report included in backtest results: strategy performance under +/-10% parameter
  deviations across all ILI weights.

### 30. ScheduledCalibrationTask (v4 -- Proposal 04)

**Location:** `computation/src/main/java/com/tickonomics/computation/optimization/ScheduledCalibrationTask.java`

Autonomous calibration service with validation pipeline.

- Runs on a background thread via `StructuredTaskScope`.
- Configuration:
    - Trigger interval: 30 days.
    - Lookback window: 180 days.
    - Validation window: 30 days out-of-sample.
    - Minimum improvement: 0.1 Sharpe ratio gain.
- Pipeline:
    1. Collect historical performance data over lookback window.
    2. Run `BayesianWeightOptimizer` (or configured optimizer) to propose new weights.
    3. Validate proposed weights on out-of-sample data.
    4. If improvement >= `min_improvement_sharpe`, queue for manual approval.
    5. If manual approval granted, apply calibrated weights via `WeightedWeightStore`.
    6. If improvement < threshold, log result and skip.
- Produces a calibration report with before/after Sharpe, parameter changes, and OOS performance.

### 31. OptimizationProfile (v4 -- Proposal 04)

**Location:** `computation/src/main/java/com/tickonomics/computation/optimization/OptimizationProfile.java`

Profile definition for weight optimization.

- Each profile specifies:
    - Target parameters to optimize.
    - Objective function (Sharpe, Latency, Hit Rate).
    - Bounds for each parameter.
    - Constraints (e.g., weights must sum to 1.0).
- Pre-defined profiles: `profit_maximizer`, `throughput_maximizer`, `regime_adaptor`.
- Custom profiles can be added via configuration.

### 32. DiscreteMonitoringCorrection (v5 -- Proposal 05: Riemann Zeta)

**Location:** `computation/src/main/java/com/tickonomics/computation/signal/DiscreteMonitoringCorrection.java`

Applies Broadie-Kou-Glasserman discrete monitoring correction using the Riemann Zeta function.

- The continuous-to-discrete barrier adjustment uses: `beta = -zeta(1/2) / sqrt(2*pi) ≈ 0.5826`
- Corrects for the bias introduced by sampling continuous liquidity at discrete points (1-minute or 1-day intervals).
- Applied to:
    - Signal thresholds (BUY/SELL percentiles) when evaluating historical performance.
    - `RegimeDetector` bandwidths to ensure thresholds are fairly set for discrete digital execution.
    - `CalculationGuard` or `SignalGenerator` to adjust for discrete sampling errors.
- Corrected threshold: `adjusted_threshold = original_threshold * beta * sqrt(delta_t)`
- Consumes `/simulate/discrete-correction` from the Python analytics worker.

### 33. ReturnGapCalculator (v5 -- Proposal 05: Return Gap)

**Location:** `computation/src/main/java/com/tickonomics/computation/kpi/ReturnGapCalculator.java`

Separates execution alpha from static holdings return.

- `ReturnGap = InvestorGrossReturn - HoldingsReturn`
- `HoldingsReturn` is the buy-and-hold return of the initial portfolio.
- Integrated into `KpiProcessor` as a key performance indicator.
- Every backtest result includes both holdings return and the execution alpha from interim trading.
- Tracks interim trading profits separately from static holdings performance.
- Used as a target for optimizing execution algorithms (timing of fills, slice-size adjustments).

### 34. StrategicRunService (v5 -- Proposal 05: Price Impact)

**Location:** `computation/src/main/java/com/tickonomics/computation/kpi/StrategicRunService.java`

Analyzes sequences of incoming ticks for strategic run pattern identification.

- Groups consecutive buy or sell child orders.
- If an algorithm is in an "aggressive run", adjusts `expected_move` thresholds accordingly.
- Computes transition probabilities between passive/aggressive states.
- Consumes `/econometrics/strategic-runs` from the Python analytics worker.
- Results stored in `strategic_run_events` table.

### 35. PriceImpactKpi (v5 -- Proposal 05: Submission-Based Impact)

**Location:** `computation/src/main/java/com/tickonomics/computation/kpi/PriceImpactKpi.java`

Tracks "Intended Move vs. Executed Move" at submission time.

- Monitors the NBBO midpoint at the time a Polygon trade condition (e.g., "Odd Lot") appears.
- Estimates the submission-time impact described in the Beason and Wahal (2021) paper.
- Child orders incur price impact at submission time, even if unexecuted or passively priced.

### 36. InformationEfficiencyAnalyzer (v5 -- Proposal 05: Price Jump Ratio)

**Location:** `computation/src/main/java/com/tickonomics/computation/backtest/InformationEfficiencyAnalyzer.java`

Quantifies how effectively signals capture information relative to announcement-period price variation.

- `PJR = CAR(T-a, T+b) / CAR(T-k, T+b)` where T is the earnings announcement time.
- Integrated with `BacktestEngine` to calculate PJR scores for historical trading periods.
- Provides a quantitative benchmark for distinguishing informed trading from noise reaction.
- Helps tune signal thresholds to maximize information incorporation.

### 37. AumfScenarioEngine (v5 -- Proposal 06: Uncertainty Management)

**Location:** `computation/src/main/java/com/tickonomics/computation/scenario/AumfScenarioEngine.java`

Five-stage algorithm uncertainty management framework.

**Five stages:**

1. **Categorize previous cases:** Analyze historical market stress events (March 2020, January 2015 SNB event).
2. **Identify algorithm behavior:** Understand how ILI models react in those cases.
3. **Create scenarios:** Draft "what-if" scenarios for potential future crises.
4. **Create best practices:** Define safe-operating boundaries.
5. **Design algorithm response:** Implement circuit breakers and safe modes.

**Implementation:**

- Matches current market conditions against historical crisis profiles.
- Scenario-based signal suppression: suppress signals when conditions match known crisis patterns.
- Ethical boundary filters: prevent signals that might contribute to feedback loops in fragile markets.
- Stress tests `PaperTradingEngine` with simulated flash crashes and divergence events.

### 38. AumfStatus Integration (v5 -- Proposal 06)

Extends `SignalStatus` with AUMF-aware states:

- `SAFE_MODE`: Algorithm operates in reduced capacity during detected uncertainty.
- `SUSPENDED_UNCERTAINTY`: All signals suspended due to matching crisis pattern.
- `PROCEED_CAUTIOUSLY`: Elevated uncertainty but signals allowed with reduced position sizing.

`SignalGenerator` includes AUMF status checks before dispatching alerts. The `AumfScenarioEngine`
provides the current AUMF state based on market conditions and scenario matching.

### 39. GexWeightedRegimeDetector (v5 -- Proposal 06: GEX Monitor)

**Location:** `computation/src/main/java/com/tickonomics/computation/regime/GexWeightedRegimeDetector.java`

Integrates `Market_GEX` into `RegimeDetector` as an optional 6th risk axis.

- Negative GEX (Short Gamma) = High probability of pinned volatility spikes.
- Positive GEX (Long Gamma) = Volatility dampening (mean reversion).
- Adds GEX as 6th axis to Systemic Risk Heatmap JSON.
- Consumes `/gex/aggregate` from the Python analytics worker.
- **Pluggable:** Only active when options data ingestion is enabled (Proposal 06).

### 40. ComovementTrigger (v5 -- Proposal 07: Liquidity Comovement)

**Location:** `computation/src/main/java/com/tickonomics/computation/risk/ComovementTrigger.java`

Integrates `LiquidityComovementFactor` into `SignalGenerator`.

- Consumes `/analytics/comovement-factor` from the Python analytics worker.
- When factor exceeds historical 90-day threshold, flags `LiquidityStressIndex` as "Elevated Systemic Risk".
- `PaperTradingEngine` switches to a more defensive, liquidity-preserving stance.
- Spikes in comovement are leading indicators for systemic stress not currently monitored by ILI.

### 41. PhantomLiquidityService (v5 -- Proposal 07: Phantom Liquidity)

**Location:** `computation/src/main/java/com/tickonomics/computation/liquidity/PhantomLiquidityService.java`

Phantom Liquidity Index (PLI) calculation.

- `PLI = canceled_volume / total_volume_at_best_quotes` over short intervals.
- High ratio of canceled-to-executed volume indicates high "phantom liquidity".
- PLI discounts ILI as it represents unreliable funding depth.
- Reads from `phantom_liquidity_metrics` hypertable.
- Inverse of PLI used as "Liquidity Reliability" score on dashboard.

### 42. ToxicityAdjustedIli (v5 -- Proposal 07: Trader Toxicity)

**Location:** `computation/src/main/java/com/tickonomics/computation/risk/ToxicityAdjustedIli.java`

Adjusts ILI sensitivity when venue/asset liquidity is dominated by toxic patterns.

- Reads toxicity scores from `toxicity_scores` table.
- When `HARMFUL` class dominates, ILI sensitivity is reduced (wider thresholds).
- When `BENEFICIAL` class dominates, ILI sensitivity is maintained or enhanced.
- Flags "toxic" market conditions in the `SystemicResilienceMonitor`.

### 43. AlgorithmicIntensityMetric (v5 -- Proposal 07: AT Intensity KPI)

**Location:** `computation/src/main/java/com/tickonomics/computation/kpi/AlgorithmicIntensityMetric.java`

Message-based AT proxy: `Negative Dollar Volume / Number of Messages`.

- Bucket securities into quintiles based on their intensity over the trailing quarter.
- Top quintile assets assigned a "liquidity premium" reflecting improved value-add.
- Integrated into `KpiProcessor` as a supplementary KPI.
- Uses `at_activity_proxy` column from `tick_data`.

### 44. BehaviouralRiskProcessor (v5 -- Proposal 07: Behavioural Liquidity Guard)

**Location:** `computation/src/main/java/com/tickonomics/computation/risk/BehaviouralRiskProcessor.java`

Behavioural Risk Index (BRI) calculation.

- BRI weighs:
    - Z-scored Order Flow Imbalance.
    - Bid-Ask Spread Volatility (relative to the `DefiningRange`).
    - Sentiment Polarity (from SALI/BERT proposals).
- Adds 5th axis to Systemic Risk Heatmap JSON: "Behavioural Stress".
- Writes to `behavioural_risk_index` hypertable.

### 45. TimeOfDayThresholdManager (v5 -- Proposal 07: Intraday Pattern)

**Location:** `computation/src/main/java/com/tickonomics/computation/signal/TimeOfDayThresholdManager.java`

Adaptive signal thresholds based on time-of-day.

- Dynamically tighten thresholds during high-asymmetry (open/close) periods.
- Relax thresholds during middle of the trading day when liquidity is more efficient.
- Leverages the well-documented "reverse U-shape" intraday liquidity pattern.
- Integrated into `SignalGenerator` as an optional threshold modifier.

### 46. DefiningRangeService (v5 -- Proposal 07: Intraday Session Filter)

**Location:** `computation/src/main/java/com/tickonomics/computation/session/DefiningRangeService.java`

Extends existing `SessionRangeService` with Defining Range (DR) logic.

- Calculates high/low of the first 60 minutes of the NY session (9:30-10:30 AM EST).
- DR has 85% probability of containing either the intraday high or low.
- Signal confidence logic:
    - BUY signals higher confidence if price is near DR Low.
    - SELL signals higher confidence if price is near DR High.
- News confidence decay: lower signal status from `ACTIONABLE` to `SPECULATIVE` within +/- 30 minutes of a high-impact
  news release.
- Reads economic calendar events from `market_events` (ingested by `EconomicCalendarClient`).

### 47. LiquiditySourceClassifier (v5 -- Proposal 07: Trader-Type Dynamics)

**Location:** `computation/src/main/java/com/tickonomics/computation/liquidity/LiquiditySourceClassifier.java`

Estimates trader type dominance from Polygon trade condition codes and order flow patterns.

- Classifies into: Algorithmic, Institutional, Professional, Retail.
- Monitors "Algorithmic Spread Reduction" effect (approximately 10% lower spreads).
- If spread reduction vanishes during high volatility, flags as `LIQUIDITY_REGIME_SHIFT`.
- Results stored in `trader_type_estimates` table.

### 48. LiquidityMeanReversionSpeed (v5 -- Proposal 07: AT Liquidity Impact Alt)

**Location:** `computation/src/main/java/com/tickonomics/computation/kpi/LiquidityMeanReversionSpeed.java`

Measures how quickly price impact decays after high AT activity.

- AT activity estimated via message intensity from Polygon (`at_activity_proxy`).
- Adjusts LSI to account for the "lagged reduction in market quality" finding.
- When high-frequency AT activity spikes, looks for delayed liquidity drain 20 minutes later.
- A/B testing candidate against `TimeOfDayThresholdManager`.

### 49. LiquidityPremiumFactor (v5 -- Proposal 07: AT Intensity Premium)

**Location:** `computation/src/main/java/com/tickonomics/computation/signal/LiquidityPremiumFactor.java`

Incorporates `AlgorithmicIntensityMetric` as a coefficient in `SignalGenerator` cost-benefit models.

- Assets in the top quintile of AT Intensity assigned a "liquidity premium".
- Reflects improved value-add for highly liquid, high-AT firms.
- Multiplicative factor on the `expected_move` calculation.

---

## Configuration

```yaml
monitor:
  strategies:
    liquidity_pivot:
      ili_weights: { rrp: 0.4, spread: 0.4, vol: 0.2 }
      ili_lookback_days:
        macro: 252
        flow: 60
        volatility: 20
      ili_min_data_points: 10
      zero_variance_threshold: 0.0001
      buy_percentile: 5
      sell_percentile: 95
      regime_detection:
        enabled: true
        clusters: 3
        low_vol_thresholds: { buy: 2, sell: 98 }
        high_vol_thresholds: { buy: 10, sell: 90 }
      min_ili_adr: 0.15
      cooldown_period: "4h"
      allow_override_in_cooldown: false
      correlation_window_days: 20
      granger_max_lag_order: 10
      correlation_significance:
        min_p_value: 0.05
        min_abs_correlation: 0.3
      intraday_proxy:
        enabled: true
        source: "openbb_treasury_3m"
      cost_model:
        estimated_slippage_bps: 5
        commission_per_share: 0.005
        min_move_to_cost_ratio: 2.0
  observability:
    metrics_enabled: true
    log_format: json
    health_check_interval: "30s"
```

**v4 Configuration Additions:**

```yaml
monitor:
  strategies:
    liquidity_pivot:
      regime_detection:
        method: "garch"  # "garch", "cnn_lstm", "qed", "kmeans"
        garch_percentile_low: 25
        garch_percentile_high: 75
      participation_governance:
        enabled: true
        log_suppression_reasons: true
      ambiguity:
        enabled: true
        band_threshold: 0.5
        status_on_exceed: "UNCERTAIN"
  anomaly:
    enabled: true
    enhance_proxy_guard: true
  stress:
    liquidity:
      enabled: true
      scenarios:
        - "swiss_franc_2015"
        - "repo_spike_2019"
        - "covid_2020"
  optimization:
    method: "bayesian"  # "bayesian" or "firefly"
    profiles:
      - "profit_maximizer"
      - "throughput_maximizer"
      - "regime_adaptor"
    calibration:
      enabled: true
      trigger_interval: "30d"
      min_improvement_sharpe: 0.1
      lookback_window: "180d"
      validation_window: "30d"
      max_drift_pct: 10
      manual_approval: true
    firefly_params:
      population_size: 15
      generations: 50
      randomization_alpha: 0.2
      light_absorption: 1.0
  bulkhead:
    critical_pool_size: 4
    high_volume_pool_size: 16
    computation_pool_size: 8
```

**v5 Configuration Additions:**

```yaml
monitor:
  strategies:
    liquidity_pivot:
      discrete_correction:
        enabled: true
        beta: 0.5826
      intraday_thresholds:
        enabled: true
        open_close_asymmetry_factor: 1.5
        midday_relaxation_factor: 0.8
      defining_range:
        enabled: true
        window_start: "09:30"
        window_end: "10:30"
        timezone: "America/New_York"
        news_decay_minutes: 30
      cost_model:
        submission_impact:
          enabled: true
          passive_bps: 0.84
          aggressive_bps: 2.03
          large_order_bps: 9.04
  aumf:
    enabled: true
    scenario_profiles:
      - "march_2020_covid"
      - "jan_2015_snb"
      - "oct_1987_black_monday"
    signal_suppression_on_match: true
    ethical_boundary_filters: true
  gex:
    enabled: false  # Requires options data ingestion
    symbols: ["SPY", "QQQ"]
    heatmap_axis: 6  # Optional 6th axis on systemic risk heatmap
  liquidity:
    comovement:
      enabled: true
      threshold_percentile: 90
      lookback_days: 90
    phantom:
      enabled: true
      pli_discount_factor: 0.5
    toxicity:
      enabled: true
      harmful_threshold: 0.7
    behavioural:
      enabled: true
      heatmap_axis: 5  # 5th axis on systemic risk heatmap
    intensity:
      enabled: true
      quintile_lookback_days: 63
    mean_reversion:
      enabled: false  # A/B testing candidate
      lagged_drain_window_minutes: 20
```

---

## Module Structure

```
computation/
├── src/main/java/com/tickonomics/computation/
│   ├── normalization/
│   │   └── NormalizationService.java
│   ├── ili/
│   │   ├── IliCalculator.java
│   │   ├── AdaptiveIliCalculator.java         # v4: Self-tuning weights (Proposal 03)
│   │   ├── ClimateSensitivityFactor.java      # v4: Climate-adjusted thresholds (Proposal 03)
│   │   └── WeightedWeightStore.java           # v4: Calibrated + base weights (Proposal 03)
│   ├── correlation/
│   │   ├── CorrelationEngine.java
│   │   ├── GrangerCausalityTest.java
│   │   └── AicLagSelector.java
│   ├── signal/
│   │   ├── SignalGenerator.java               # Existing, enhanced
│   │   ├── SignalStatus.java                  # Add UNCERTAIN, SAFE_MODE, SUSPENDED_UNCERTAINTY, PROCEED_CAUTIOUSLY (v4+v5)
│   │   ├── IntradayProxyService.java          # Existing, enhanced with autoencoder (v4)
│   │   ├── SurpriseIndicator.java             # v4: Entropy/surprise (Proposal 03)
│   │   ├── DiscreteMonitoringCorrection.java  # v5: Riemann Zeta correction (Proposal 05)
│   │   ├── TimeOfDayThresholdManager.java     # v5: Adaptive intraday thresholds (Proposal 07)
│   │   └── LiquidityPremiumFactor.java        # v5: AT intensity premium (Proposal 07)
│   ├── regime/
│   │   ├── RegimeDetector.java                # Now delegates to configured implementation
│   │   ├── GarchRegimeDetector.java           # v4: GARCH-based (Proposal 03, PRIMARY)
│   │   ├── DlRegimeDetector.java              # v4: CNN-LSTM (Proposal 03, Alt A)
│   │   ├── QEDRegimeDetector.java             # v4: QED quartic potential (Proposal 03, Alt B)
│   │   ├── ExogenousShockDetector.java        # v4: EXOGENOUS_SHOCK regime (Proposal 03)
│   │   ├── GexWeightedRegimeDetector.java     # v5: GEX-weighted regime (Proposal 06)
│   │   └── SessionRangeService.java           # v4+v5: Session-aware ranges + DR (Proposals 03, 07)
│   ├── governance/
│   │   └── ParticipationGovernanceService.java  # v4: Admissibility decomposition (Proposal 02)
│   ├── risk/
│   │   ├── CrashProbabilityScore.java         # v4: QED-based crash probability (Proposal 03)
│   │   ├── ClimateRiskGuard.java              # v4: Climate-adjusted confidence (Proposal 03)
│   │   ├── ComovementTrigger.java             # v5: Comovement factor integration (Proposal 07)
│   │   ├── ToxicityAdjustedIli.java           # v5: Toxicity-adjusted ILI (Proposal 07)
│   │   └── BehaviouralRiskProcessor.java      # v5: BRI calculation (Proposal 07)
│   ├── stress/
│   │   └── LiquidityStressTestModule.java     # v4: Liquidity stress testing (Proposal 02)
│   ├── scenario/
│   │   └── AumfScenarioEngine.java            # v5: Five-stage AUMF framework (Proposal 06)
│   ├── liquidity/
│   │   ├── PhantomLiquidityService.java       # v5: PLI calculation (Proposal 07)
│   │   └── LiquiditySourceClassifier.java     # v5: Trader type estimation (Proposal 07)
│   ├── optimization/
│   │   ├── BayesianWeightOptimizer.java       # v4: Bayesian optimization (Proposal 04, PRIMARY)
│   │   ├── FireflyWeightOptimizer.java        # v4: Firefly algorithm (Proposal 04, Alt)
│   │   ├── RegimeAwareWeightingService.java   # v4: Efficiency-depth weights (Proposal 04)
│   │   ├── ScheduledCalibrationTask.java      # v4: Auto-calibration (Proposal 04)
│   │   └── OptimizationProfile.java           # v4: Profile definition (Proposal 04)
│   ├── kpi/
│   │   ├── KpiProcessor.java
│   │   ├── AlertManager.java
│   │   ├── ReturnGapCalculator.java            # v5: Return Gap KPI (Proposal 05)
│   │   ├── StrategicRunService.java            # v5: Strategic run detection (Proposal 05)
│   │   ├── PriceImpactKpi.java                 # v5: Submission-based impact (Proposal 05)
│   │   ├── AlgorithmicIntensityMetric.java     # v5: AT proxy KPI (Proposal 07)
│   │   └── LiquidityMeanReversionSpeed.java    # v5: Mean reversion KPI (Proposal 07, Alt)
│   ├── guard/
│   │   └── CalculationGuard.java
│   └── config/
│       └── ComputationConfig.java
└── src/test/java/com/tickonomics/computation/
    ├── normalization/
    ├── ili/
    ├── correlation/
    ├── signal/
    ├── regime/
    ├── governance/                              # v4
    ├── risk/                                    # v4
    ├── stress/                                  # v4
    ├── scenario/                                # v5
    ├── liquidity/                               # v5
    ├── optimization/                            # v4
    ├── kpi/
    └── guard/
```

---

## Unit Tests (Given-When-Then)

- Given SOFR spike, When correlation engine runs (`TA_CORREL`), Then beta and p_value update.
- Given zero-variance input, When z-score computed, Then result is `0.0` (not `NaN`).
- Given < 10 data points, When ILI computed, Then status is `DATA_INSUFFICIENT`.
- Given signal during cooldown, When override disabled, Then signal is `COOLDOWN`.
- Given cost exceeds expected move, When signal evaluated, Then `COST_EXCEEDS_EXPECTED_MOVE`.
- Given signal using T-Bill proxy, When SOFR not yet published, Then `SPECULATIVE_STALE_MACRO`.
- Given AIC lag selection, When multiple lags tested, Then optimal lag minimizes AIC.
- Given analytics worker unreachable, When ADF requested, Then fallback gracefully.
- Given `TA_CORREL` output, When compared to analytics worker OLS, Then results agree within tolerance.
- Given `TA_BBANDS` bandwidth, When regime detected, Then classification matches k-means.

**Property-based tests (jqwik):**

- ILI invariant: for any valid weights and finite inputs, ILI is always finite.
- Zero-variance invariant: result is `0.0` (not `NaN`).
- Signal invariant: at most one direction per evaluation.
- Correlation invariant: `TA_CORREL` output in `[-1, 1]` for any finite equal-length series.
- AIC invariant: selected lag order in `[1, max_lag_order]`.

**v4 Unit Tests (Given-When-Then):**

- Given GARCH forecast > 75th percentile, When regime detected, Then classification is HIGH_VOL.
- Given QED capital flow reversal, When regime detected, Then classification is UNSTABLE.
- Given EXOGENOUS_SHOCK alert, When regime override, Then ILI flagged DISLOCATED within 30s.
- Given SUSPECT_ANOMALY data, When proxy guard enhanced, Then divergence detected earlier.
- Given participation inadmissible, When signal fires, Then explicit suppression reason logged.
- Given weight delta from SGD, When adaptive calculator applies, Then weights shift within 10%.
- Given ambiguity band exceeded, When ILI computed, Then status is UNCERTAIN.
- Given Bayesian optimizer, When 100 iterations, Then convergence to near-optimal weights.
- Given liquidity stress scenario, When Swiss Franc model applied, Then strategy resilience measured.

**v5 Unit Tests (Given-When-Then):**

- Given discrete monitoring correction, When beta = 0.5826 applied, Then threshold adjusted for 1-minute sampling.
- Given return gap calculation, When portfolio outperforms buy-and-hold, Then positive execution alpha reported.
- Given strategic run detected, When consecutive buy orders grouped, Then transition probabilities computed.
- Given AUMF scenario matches crisis pattern, When signal fires, Then SUSPENDED_UNCERTAINTY status set.
- Given AUMF scenario partially matches, When signal fires, Then PROCEED_CAUTIOUSLY status with reduced sizing.
- Given negative GEX (short gamma), When regime detected, Then volatility spike probability elevated.
- Given comovement factor > 90th percentile, When LSI computed, Then "Elevated Systemic Risk" flag set.
- Given high PLI, When ILI computed, Then ILI discounted for unreliable depth.
- Given HARMFUL toxicity dominance, When ILI sensitivity adjusted, Then thresholds widened.
- Given behavioural risk in PANIC regime, When herding detected, Then signals suppressed.
- Given time-of-day open period, When threshold computed, Then thresholds tightened by asymmetry factor.
- Given price near DR Low, When BUY signal evaluated, Then confidence boosted.
- Given high-impact news event in 30 min, When signal evaluated, Then status downgraded to SPECULATIVE.

---

## Observability (Metrics)

- `monitor.calculation.ili.duration` -- timer per ILI cycle.
- `monitor.calculation.talib.duration` -- timer for TA-Lib function calls.
- `monitor.signal.generated.total` -- counter, tagged by direction and status.
- `monitor.analytics.worker.latency` -- timer for Python analytics worker calls.

**v4 Observability Additions:**

- `monitor.regime.classification` -- gauge, tagged by method (garch, cnn_lstm, qed, kmeans).
- `monitor.regime.transition.total` -- counter of regime changes.
- `monitor.anomaly.detection.latency` -- timer for autoencoder round-trip.
- `monitor.participation.suppressed.total` -- counter, tagged by reason.
- `monitor.stress.test.duration` -- timer for liquidity stress test execution.
- `monitor.optimization.iteration.duration` -- timer per optimization iteration.
- `monitor.optimization.fitness.score` -- gauge of current best fitness.

**v5 Observability Additions:**

- `monitor.aumf.scenario.match.total` -- counter, tagged by scenario name.
- `monitor.aumf.status` -- gauge (SAFE_MODE=0, PROCEED_CAUTIOUSLY=1, NORMAL=2, SUSPENDED_UNCERTAINTY=3).
- `monitor.liquidity.comovement.factor` -- gauge of current comovement factor.
- `monitor.liquidity.phantom.pli` -- gauge of current PLI per symbol.
- `monitor.liquidity.toxicity.score` -- gauge, tagged by symbol and class.
- `monitor.liquidity.behavioural.bri` -- gauge of current BRI per symbol.
- `monitor.liquidity.intensity.quintile` -- gauge of AT intensity quintile per symbol.
- `monitor.backtest.return_gap` -- gauge of current return gap.
- `monitor.backtest.strategic_runs.total` -- counter of detected strategic runs.
- `monitor.gex.net_exposure` -- gauge of net GEX per symbol (when enabled).

---

## Validation

- [ ] `TalibAdapter` unit tests pass against known mathematical results.
- [ ] ILI with default weights produces finite output for all finite inputs.
- [ ] Zero-variance guard returns `0.0` instead of `NaN`.
- [ ] Correlation engine produces values in `[-1, 1]`.
- [ ] Signal generator applies all filters correctly.
- [ ] Regime detector classifies into low/normal/high.
- [ ] Intraday proxy substitutes T-Bill data correctly.
- [ ] CalculationGuard catches all `NaN`/`Infinity` cases.
- [ ] Cross-Stack Consistency tests pass for both modes.
- [ ] Property-based tests pass for all invariants.
- [ ] `CorrelationEngine` reads historical data from pre-computed aggregates and only computes live window via TA-Lib (
  v3).
- [ ] Pre-computed `kpi_rolling_correlation` and `kpi_rolling_beta` match TA-Lib output within tolerance (v3).
- [ ] Computation components receive only CDM-typed inputs -- no source-specific types visible (v3).
- [ ] `NormalizationService` operates on `CdmRateSnapshot` with CDM enum for rate type (v3).

**v4 Validation Additions:**

- [ ] GARCH regime detector consumes Python worker forecasts and classifies correctly.
- [ ] CNN-LSTM regime detector provides alternative classification.
- [ ] QED regime detector uses capital flow proxies for metastable/unstable detection.
- [ ] CrashProbabilityScore spikes during historical SOFR/T-Bill dislocation.
- [ ] ExogenousShockDetector triggers DISLOCATED within 30 seconds of disaster alert.
- [ ] AdaptiveIliCalculator applies SGD weight deltas correctly.
- [ ] SurpriseIndicator computes entropy-based anomaly scores.
- [ ] ClimateSensitivityFactor adjusts ILI thresholds.
- [ ] ParticipationGovernanceService logs explicit suppression reasons.
- [ ] LiquidityStressTestModule simulates Swiss Franc scenario.
- [ ] BayesianWeightOptimizer converges within 500 iterations.
- [ ] FireflyWeightOptimizer produces comparable results to Bayesian.
- [ ] Ambiguity bands widen during known high-volatility periods.
- [ ] ScheduledCalibrationTask runs validation pipeline correctly.

**v5 Validation Additions:**

- [ ] DiscreteMonitoringCorrection applies beta = 0.5826 correctly to signal thresholds.
- [ ] ReturnGapCalculator separates execution alpha from holdings return accurately.
- [ ] StrategicRunService groups consecutive child orders and computes transition probabilities.
- [ ] PriceImpactKpi tracks intended vs. executed move at submission time.
- [ ] InformationEfficiencyAnalyzer computes PJR scores for historical trading periods.
- [ ] AumfScenarioEngine matches at least 5 known historical crisis patterns.
- [ ] AUMF status correctly transitions to SAFE_MODE during simulated flash crashes.
- [ ] AUMF status correctly transitions to SUSPENDED_UNCERTAINTY during crisis pattern matches.
- [ ] GexWeightedRegimeDetector integrates net GEX into regime classification (when enabled).
- [ ] ComovementTrigger flags "Elevated Systemic Risk" when comovement factor exceeds 90th percentile.
- [ ] PhantomLiquidityService computes PLI and discounts ILI for unreliable depth.
- [ ] ToxicityAdjustedIli adjusts ILI sensitivity based on toxicity class dominance.
- [ ] AlgorithmicIntensityMetric buckets securities into quintiles correctly.
- [ ] BehaviouralRiskProcessor computes BRI with 5th axis on systemic risk heatmap.
- [ ] TimeOfDayThresholdManager tightens thresholds at open/close and relaxes at midday.
- [ ] DefiningRangeService calculates DR high/low from 9:30-10:30 EST window.
- [ ] DefiningRangeService decays signal confidence within +/- 30 min of high-impact news.
- [ ] LiquiditySourceClassifier estimates trader type dominance from trade conditions.
- [ ] LiquidityMeanReversionSpeed detects 20-minute lagged quality effect (when enabled).
- [ ] LiquidityPremiumFactor applies multiplicative premium for top-quintile AT intensity.

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|:--------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: AIC thin client, Proxy Divergence Guard, Dynamic Weighting with NaN.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v3      | Updated `CorrelationEngine` to read pre-computed rolling aggregates from TimescaleDB for historical data, reserving TA-Lib for live window only (Proposal #3). Added CDM-typed input requirement for all computation components (Proposal #5). Updated `NormalizationService` to use CDM enums.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| v4      | Major additions from consolidated proposals: (1) GARCH Regime Detector as PRIMARY regime engine, replacing K-means as default (Proposal 03). (2) CNN-LSTM and QED regime detectors as pluggable alternatives (Proposal 03). (3) ExogenousShockDetector adding EXOGENOUS_SHOCK regime with USGS/GDACS integration (Proposal 03). (4) AdaptiveIliCalculator with WeightedWeightStore for self-tuning ILI weights via SGD (Proposal 03). (5) SurpriseIndicator for entropy-based anomaly scoring and position sizing (Proposal 03). (6) ClimateSensitivityFactor and ClimateRiskGuard for climate-adjusted ILI (Proposal 03). (7) SessionRangeService for session-aware regime detection (Proposal 03). (8) ParticipationGovernanceService with formal signal decomposition and explicit suppression logging (Proposal 02). (9) LiquidityStressTestModule simulating Swiss Franc 2015, Repo Spike 2019, COVID 2020 scenarios (Proposal 02). (10) Enhanced ProxyDivergenceGuard with autoencoder anomaly detection for earlier warning (Proposal 01). (11) BayesianWeightOptimizer with optimization profiles and safety constraints (Proposal 04 PRIMARY). (12) FireflyWeightOptimizer as population-based metaheuristic alternative (Proposal 04). (13) RegimeAwareWeightingService for efficiency-depth weight adjustment (Proposal 04). (14) Ambiguity-Adjusted ILI with uncertainty bands and UNCERTAIN status (Proposal 04). (15) ScheduledCalibrationTask for autonomous calibration with 30d OOS validation (Proposal 04). New modules: `governance/`, `risk/`, `stress/`, `optimization/`. New SignalStatus: UNCERTAIN. 9 new unit tests. 7 new observability metrics. 14 new validation criteria.                                                                                                                                                                                                                               |
| v5      | Added 18 new components from Proposals 05, 06, 07: (1) DiscreteMonitoringCorrection — Riemann Zeta beta correction for discrete barriers (Proposal 05). (2) ReturnGapCalculator — execution alpha vs. holdings return (Proposal 05). (3) StrategicRunService — strategic run pattern detection (Proposal 05). (4) PriceImpactKpi — submission-based impact tracking (Proposal 05). (5) InformationEfficiencyAnalyzer — Price Jump Ratio diagnostic (Proposal 05). (6) AumfScenarioEngine — five-stage uncertainty management framework (Proposal 06). (7) AUMF Status Integration — SAFE_MODE, SUSPENDED_UNCERTAINTY, PROCEED_CAUTIOUSLY statuses (Proposal 06). (8) GexWeightedRegimeDetector — options gamma exposure integration (Proposal 06). (9) ComovementTrigger — liquidity comovement factor integration (Proposal 07). (10) PhantomLiquidityService — PLI calculation and ILI discounting (Proposal 07). (11) ToxicityAdjustedIli — toxicity-based ILI sensitivity adjustment (Proposal 07). (12) AlgorithmicIntensityMetric — message-based AT proxy with quintile bucketing (Proposal 07). (13) BehaviouralRiskProcessor — BRI with 5th systemic risk axis (Proposal 07). (14) TimeOfDayThresholdManager — adaptive intraday thresholds (Proposal 07). (15) DefiningRangeService — DR-based signal confidence and news decay (Proposal 07). (16) LiquiditySourceClassifier — trader type estimation (Proposal 07). (17) LiquidityMeanReversionSpeed — AT activity lagged quality effect (Proposal 07). (18) LiquidityPremiumFactor — AT intensity premium (Proposal 07). New modules: `scenario/`, `liquidity/`. New SignalStatus values: SAFE_MODE, SUSPENDED_UNCERTAINTY, PROCEED_CAUTIOUSLY. 13 new unit tests. 10 new observability metrics. 20 new validation criteria. Extended configuration with discrete_correction, intraday_thresholds, defining_range, submission_impact, aumf, gex, and liquidity sections. |
