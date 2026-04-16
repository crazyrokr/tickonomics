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

**Analysis findings applied:**

- **Finding 2 (AIC Loop in Worker):** The AIC lag selection loop now runs entirely in the
  Python analytics worker. Java sends data once with `max_lag`, worker returns optimal result.
  `AicLagSelector` in Java is a thin client that calls the worker's `/causality` endpoint.
- **Finding 3 (Proxy Divergence Guard):** `IntradayProxyService` includes a divergence guard
  that detects T-Bill/SOFR dislocation and suppresses proxy-derived signals.
- **Finding 6 (Dynamic Weighting):** Zero-variance components return `NaN` instead of `0.0`.
  `IliCalculator` redistributes weight proportionally among valid components and sets
  `DEGRADED_COMPONENT_STALE` status.

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

- Calls analytics worker (Track 3) → `obb.econometrics.unit_root`.
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

**In-process (TA-Lib):**

- `TA_CORREL` for rolling Pearson correlation (window default 20 trading days).
- `TA_BETA` for rolling Repo/Equity Beta (window default 60 days).
- Input: differenced (stationary) series using `TA_ROC`.
- Data aligned via TimescaleDB `time_bucket()` queries.
- Output per trading day: `{ correlation, p_value, sample_size }`.

**Cross-process (Analytics Worker):**

- Full OLS statistics (r_squared, std_error) via `obb.econometrics.ols_regression`.

**Significance threshold:** Only correlations with `p_value < 0.05` AND `|correlation| > 0.3`
are surfaced. Others flagged `INSIGNIFICANT`.

### 6. GrangerCausalityTest

1. Uses `AicLagSelector` (thin client) which calls analytics worker's `/causality` endpoint
   with `max_lag` — worker returns optimal lag and full causality result (Finding 2).
2. Outputs: `{ f_statistic, p_value, lag_order, direction }`.
4. Direction enum: `FUNDING_LEADS_EQUITY`, `EQUITY_LEADS_FUNDING`, `NO_RELATIONSHIP`.

**Fallback:** If analytics worker unreachable, skip Granger, use correlation-only analysis.

### 7. RegimeDetector

- `TA_BBANDS` on ILI series for volatility regime detection.
- Bandwidth = (upper - lower) / middle.
- K-means clustering (3 clusters) on last 252 days of daily ILI volatility (`TA_STDDEV` of changes).
- Regime classification:
    - Low: bandwidth < 0.5x median bandwidth → tighten thresholds to 2%/98%.
    - Normal: between 0.5x and 2x.
    - High: bandwidth > 2x median bandwidth → widen thresholds to 10%/90%.

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

### 9. SignalGenerator

**Percentile-rank adaptive thresholds:**

1. Compute ILI percentile rank over rolling window (default 252 trading days).
2. Buy Signal: ILI percentile < `buy_percentile` (default 5%).
3. Sell Signal: ILI percentile > `sell_percentile` (default 95%).
4. Per-regime threshold adjustment via `RegimeDetector`.

**Filters:**

- **Look-ahead bias prevention:** Rolling window offset by 1 day — today's ILI from data up to yesterday's close.
- **Minimum volatility filter:** If ILI ADR (Average Daily Range) over last 20 days < `min_ili_adr` (default 0.15),
  suppress signals.
- **Cooldown period:** After signal fires, no new signal of same direction for `cooldown_period` (default 4h).
  Configurable override.
- **Transaction cost modeling:**
    - Each signal includes `estimated_slippage` (configurable bps, default 5) + `commission_per_share` (default 0.005).
    - Signal expected move must exceed `2 * estimated_cost` to be actionable.
    - Non-actionable signals logged with status `COST_EXCEEDS_EXPECTED_MOVE`.

**Signal Status Enum:**

- `ACTIONABLE` — passed all filters, cost-justified, fresh data.
- `SPECULATIVE_STALE_MACRO` — using intraday proxy, awaiting official SOFR.
- `COST_EXCEEDS_EXPECTED_MOVE` — direction valid but not economically viable.
- `COOLDOWN` — duplicate direction suppressed.
- `INSUFFICIENT_DATA` — not enough data points in lookback window.

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

---

## Module Structure

```
computation/
├── src/main/java/com/tickonomics/computation/
│   ├── normalization/
│   │   └── NormalizationService.java
│   ├── ili/
│   │   └── IliCalculator.java
│   ├── correlation/
│   │   ├── CorrelationEngine.java
│   │   ├── GrangerCausalityTest.java
│   │   └── AicLagSelector.java
│   ├── signal/
│   │   ├── SignalGenerator.java
│   │   ├── SignalStatus.java
│   │   └── IntradayProxyService.java
│   ├── regime/
│   │   └── RegimeDetector.java
│   ├── kpi/
│   │   ├── KpiProcessor.java
│   │   └── AlertManager.java
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

---

## Observability (Metrics)

- `monitor.calculation.ili.duration` — timer per ILI cycle.
- `monitor.calculation.talib.duration` — timer for TA-Lib function calls.
- `monitor.signal.generated.total` — counter, tagged by direction and status.
- `monitor.analytics.worker.latency` — timer for Python analytics worker calls.

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

---

## Changelog

| Version | Change                                                                                                                                                                                                                                         |
|:--------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: AIC thin client, Proxy Divergence Guard, Dynamic Weighting with NaN.                                                                                                                                                |
| v3      | Updated `CorrelationEngine` to read pre-computed rolling aggregates from TimescaleDB for historical data, reserving TA-Lib for live window only (Proposal #3). Added CDM-typed input requirement for all computation components (Proposal #5). |
