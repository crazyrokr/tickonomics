# Consolidated Proposal: Regime Detection Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)
**Consolidation Date:** 2026-05-21

## v3 Integration Points

| Track                             | Components Affected                                                                                                              |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Track 2 (DB)                      | `market_events` hypertable, `tick_data` aggregates                                                                               |
| Track 3 (Analytics Worker)        | `garch_service.py`, `cnn_lstm_regime.py`, `qed_service.py`, climate model simulation, `SurpriseIndicator`                        |
| Track 4 (Ingestion)               | `EventBasedTimeConverter`, `DisasterAlertClient`, economic calendar ingestion, GDACS/USGS polling                                |
| Track 5 (Computation)             | `RegimeDetector`, `AdaptiveIliCalculator`, `WeightedWeightStore`, `QEDRegimeDetector`, `ClimateRiskGuard`, `SessionRangeService` |
| Track 7 (Dashboard)               | Volatility Cluster visualization, Potential Well chart, Structural Macro-Environment panel, Disaster Overlay markers             |
| Track 8 (Backtesting)             | Calibration stability tests, QED drift simulation, climate stress scenarios, intrinsic time backtesting                          |
| Track 10 (Demo/Virtual Portfolio) | Climate stress scenarios in demo environment                                                                                     |

---

## PART I: CORE PROPOSAL (PRIMARY)

This is the recommended primary upgrade from the current K-means regime detection.

---

### 1.1 Volatility-Aware Regime Engine (GARCH Integration)

**Source:** SSRN-5140015 ("Applications of Time Series Analysis in Quantitative Finance")
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 7 (Dashboard), Track 8 (Backtesting)

#### Rationale

Financial time series exhibit "volatility clustering" (heteroskedasticity), which simple technical indicators like
Bollinger Bands fail to model accurately over long horizons. GARCH(1,1) is specifically designed to model time-varying
conditional variance, providing superior "Volatility Regime" classification. Additionally, an "Online Calibration"
mechanism adapts ILI weights dynamically as market conditions shift, reducing the need for manual quarterly
recalibration.

#### Module: GARCH Volatility Service (Track 3)

- **Location:** `analytics/app/services/risk/garch_service.py`
- **Dependencies:** `arch` library (`arch.univariate`)
- **Implementation:** Conditional volatility forecasts for the ILI and key equity symbols.
- **Endpoint:** `/risk/garch-regime`

#### Module: Online Optimizer (Track 3)

- **Location:** `analytics/app/services/risk/online_optimizer.py`
- **Implementation:** Light-weight stochastic gradient descent (SGD) service providing "delta updates" for ILI component
  weights based on the last 5 days of signal performance.

#### Module: Advanced Regime Detection (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/regime/GarchRegimeDetector.java`
- **Regime Classification:**
    - Low-Vol Regime: GARCH forecast < 25th percentile
    - Normal Regime: 25th <= GARCH forecast <= 75th percentile
    - High-Vol Regime: GARCH forecast > 75th percentile
- Update `RegimeDetector` to consume GARCH forecasts from the Python worker.

#### Module: AdaptiveIliCalculator (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/ili/AdaptiveIliCalculator.java`
- **Implementation:** Apply weight deltas from the Python worker, creating a self-tuning liquidity model.
- **WeightedWeightStore:** Maintains current "calibrated" weights in addition to "base" configuration weights.

#### Module: VaR-Based Signal Filtering (Track 8)

- Use VaR (Value at Risk) formulation to add a risk-check to signals.
- Suppress signals if the 1-day VaR exceeds a percentage of virtual equity.

#### Module: Calibration Stability Test (Track 8)

- Verify that "Online Calibration" logic does not lead to "over-fitting" or erratic weight shifts during historical
  stress tests.

#### Module: Volatility Cluster Visualization (Track 7)

- Add "Volatility Cluster" visualization to the System Health panel.
- Show current GARCH-implied risk regime.

#### Validation Criteria

- [ ] GARCH identifies "volatility explosions" faster than SMA-based bandwidth indicators.
- [ ] Online calibration reduces operational burden of manual weight recalibration.
- [ ] VaR and GARCH-volatility metrics pass institutional risk management standards.

---

## PART II: ALTERNATIVE PLUGGABLE IMPLEMENTATIONS

These are pluggable replacements for the GARCH regime detector. Each can be swapped in via a configuration flag and
compared against GARCH on the same historical data.

---

### 2.1 Alternative A: Deep Learning Ensemble (CNN-LSTM)

**Source:** SSRN-6574419
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 7 (Dashboard)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION A

#### Rationale

Hybrid Deep Learning architectures (CNN-LSTM) capture both spatial (feature relationships) and temporal dependencies in
market states. This can improve volatility regime classification beyond what GARCH achieves with its linear assumptions.

#### Module: CNN-LSTM Regime Detector (Track 3)

- **Location:** `analytics/app/services/regime/cnn_lstm_regime.py`
- **Architecture:** Hybrid CNN-LSTM ensemble:
    - CNN layers for spatial feature extraction from multi-variate market data.
    - LSTM layers for temporal dependency capture.
- **Endpoint:** `/api/v1/regime/hybrid`
- **Input Features:** Raw component series (RRP, Spread, Vol) rather than just ILI bandwidth.

#### Module: DL-Enhanced RegimeDetector (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/regime/DlRegimeDetector.java`
- **Implementation:** Update `RegimeDetector.java` to optionally consume the hybrid API endpoint.
- **Training:** Utilize the Analytics Worker (Track 3) for ensemble training, leveraging existing ADF/Granger
  infrastructure.

#### Validation Criteria

- [ ] Experimental regime detection metric in `signal_quality_reports` compares K-means vs DL-ensemble accuracy in
  real-time.
- [ ] CNN-LSTM captures regime transitions that GARCH misses during rapid volatility shifts.

---

### 2.2 Alternative B: Quantum Equilibrium-Disequilibrium (QED) Model

**Source:** SSRN-3669972 (Halperin, 2020)
**Tracks:** Track 5 (Computation), Track 8 (Backtesting)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION B

#### Rationale

The QED model replaces the unstable Geometric Brownian Motion (GBM) with a metastable quartic potential. It explicitly
models markets as open systems influenced by capital flows (money injections/withdrawals), which is highly consistent
with Tickonomics' focus on Fed balance sheet and RRP dynamics.

#### Module: Open-System Liquidity Modeling (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/regime/QEDRegimeDetector.java`
- **Capital Flow Proxies:** Use `RRP Drain Velocity` and `TGA Balance Change` as proxies for the $u_t$ (capital
  inflow/outflow) term in the QED equations (Eq. 6).
- **Metastable Regime Detection:**
    - "Metastable" state: Liquidity flows provide a stabilizing force.
    - "Unstable" state: Flows accelerate a "free fall" or "bubble" (inverted parabola).

#### Module: Instanton (Crash) Probability (Track 5)

- **KPI:** `CrashProbabilityScore` based on the proximity of the current ILI/Price state to the "potential barrier"
  described in the model.
- **Location:** `computation/src/main/java/com/tickonomics/computation/risk/CrashProbabilityScore.java`

#### Module: QED Drift Simulation (Track 8)

- **Location:** `analytics/app/services/regime/qed_service.py`
- **Implementation:** Python service to solve the discrete-time QED dynamics (Eq. 6) given current $u_t$ proxies.
- Support QED-based drift modeling for "what-if" scenarios of massive liquidity withdrawals (e.g., rapid QT).

#### Module: Potential Well Chart (Track 7)

- Visualization showing the current price position relative to the QED minima.

#### Validation Criteria

- [ ] `CrashProbabilityScore` correctly spikes during historical periods of high SOFR/T-Bill dislocation.
- [ ] QED-based drift projections correlate more highly with realized trend changes than linear GBM baselines.

---

## PART III: COMPLEMENTARY ADDITIONS

These modules enhance any regime detector (GARCH, CNN-LSTM, or QED) and are detector-agnostic.

---

### 3.1 Event-Based Intrinsic Time

**Source:** SSRN-2951348
**Tracks:** Track 4 (Ingestion), Track 5 (Computation), Track 8 (Backtesting)
**Type:** COMPLEMENTARY

#### Rationale

Event-based ("intrinsic") time provides a more parsimonious and event-aware framework for algorithmic trading than
traditional time-based bars. During volatile events, time-based aggregation may obscure significant signal-generating
data. Intrinsic time makes strategies scale-invariant and adaptive to market-driven event intensity.

#### Module: EventBasedTimeConverter (Track 4)

- **Location:** `ingestion/src/main/java/com/tickonomics/ingestion/time/EventBasedTimeConverter.java`
- **Implementation:** Map raw tick data into discrete events: directional changes and overshoots.
- **Persistence:** Store metadata in new `market_events` hypertable in TimescaleDB.

#### Module: SurpriseIndicator (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/SurpriseIndicator.java`
- **Implementation:** Use information theory (entropy/surprise) to calculate the "unlikeliness" of current price
  trajectories.
- **Integration:** Use as a factor in position sizing and inventory management in `SignalGenerator` (e.g., reduce
  position size when market behavior is anomalous).

#### Module: Intrinsic Time Backtesting (Track 8)

- Enable backtesting simulations using intrinsic time series as an alternative to time-series bars.
- Support agent-based strategies defined by event-based language.

#### Validation Criteria

- [ ] Intrinsic time bars capture significant market events more efficiently than fixed-time bars.
- [ ] SurpriseIndicator correctly identifies anomalous price movements.
- [ ] Strategies tested on intrinsic time show improved robustness.

---

### 3.2 Climate-Liquidity Sensitivity

**Source:** SSRN-5370920 ("International Climate Finance: A Quantitative Economic Evaluation")
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 7 (Dashboard), Track 8 (Backtesting), Track 10 (
Demo/Virtual Portfolio)
**Type:** COMPLEMENTARY

#### Rationale

Climate finance and environmental policy are increasingly influencing global liquidity and central bank balance sheets.
As the global energy mix shifts, the "normal" level of RRP and TGA may shift, requiring ILI threshold adjustments. The
paper develops a multi-country, multi-sector structural trade model demonstrating mitigation/adaptation trade-offs and
marginal benefit decay.

#### Module: Climate-Sensitive ILI (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/ili/ClimateSensitivityFactor.java`
- **Implementation:** Create a "Climate Sensitivity Factor" for the ILI. As the global energy mix shifts, the system
  adjusts ILI thresholds accordingly.
- **Structural Model Integration:** Use the paper's "Marginal Carbon Emission" decomposition as a model for quantifying
  the "Marginal Liquidity Benefit" of different policy inputs.

#### Module: ClimateRiskGuard (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/risk/ClimateRiskGuard.java`
- **Implementation:** Modify ILI confidence based on a "Carbon Intensity" weight derived from the paper's carbon
  coefficients.

#### Module: Multi-Country Trade Model Simulation (Track 3)

- **Location:** `analytics/app/services/climate/climate_model.py`
- **Implementation:** Simulate "Energy Shift" dynamics using the multi-country trade model framework.
- Users can adjust sliders (e.g., "Increased Adaptation Finance") to see projected impact on energy-related liquidity
  demand.

#### Module: Climate Stress Scenarios (Track 10, Track 8)

- Implement "Climate Stress" scenarios in the Demo environment (e.g., abrupt carbon-tax hike, massive clean energy
  investment event).
- Integrate climate shocks as a new type of `ExogenousShock` in the backtester.
- Observe impact on `PaperTradingEngine` strategy.

#### Module: Structural Macro-Environment Panel (Track 7)

- Dashboard panel incorporating indices derived from "Carbon Emission Coefficients" and "Renewable Potential"
  indicators.

#### Validation Criteria

- [ ] Climate sensitivity factor adjusts ILI thresholds in response to energy mix data.
- [ ] Climate stress scenarios produce measurable impacts on virtual portfolio performance.
- [ ] Long-term structural macro-changes are correctly modeled.

---

### 3.3 Natural Disaster Exogenous Shock

**Source:** SSRN-3510505 (Grindsted TS, 2020)
**Tracks:** Track 4 (Ingestion), Track 5 (Computation), Track 7 (Dashboard)
**Type:** COMPLEMENTARY

#### Rationale

HFT algorithms react to earthquake early warning systems, news, and reports, often leading to rapid "micro-crashes" or "
Flight to Quality" movements. Natural disasters are primary drivers of "Flight to Quality" that should be detected as
exogenous shocks independent of normal volatility regime classification.

#### Module: DisasterAlertClient (Track 4)

- **Location:** `ingestion/src/main/java/com/tickonomics/ingestion/external/DisasterAlertClient.java`
- **Implementation:** Poll real-time natural disaster alerts from:
    - USGS Earthquake API
    - Global Disaster Alert and Coordination System (GDACS)
- **News Sentiment Trigger:** Flag "Natural Disaster" keywords (`Earthquake`, `Tsunami`, `Hurricane`) via the OpenBB
  news endpoint.

#### Module: EXOGENOUS_SHOCK Regime (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/regime/ExogenousShockDetector.java`
- **Implementation:** Define a new `VOLATILITY_REGIME` called `EXOGENOUS_SHOCK`.
- **Immediate Proxy Divergence Trigger:** Proactively flag ILI as `DISLOCATED` even before the 5-day correlation breaks
  down, as a safety measure.

#### Module: Disaster Overlay (Track 7)

- Display disaster events on the ILI history chart as vertical markers (similar to earnings markers).
- Provide context for sudden liquidity spikes or dislocations.
- Add "Event Context" panel to the dashboard.

#### Validation Criteria

- [ ] System proactively enters `DISLOCATED` state within 30 seconds of a major disaster alert appearing in the feed.
- [ ] Backtest engine verifies that disaster-induced signals are correctly suppressed.

---

## PART IV: A/B TESTING FRAMEWORK

All regime detection approaches are designed to be pluggable and can be compared on the same historical data.

### Tournament Configuration

| Model                         | Implementation        | Endpoint                | Classification Method            |
|-------------------------------|-----------------------|-------------------------|----------------------------------|
| K-means (current v2 baseline) | `RegimeDetector.java` | N/A (existing)          | Bollinger Bandwidth clustering   |
| GARCH (PRIMARY)               | `garch_service.py`    | `/risk/garch-regime`    | Conditional variance percentiles |
| CNN-LSTM (Alt A)              | `cnn_lstm_regime.py`  | `/api/v1/regime/hybrid` | Deep learning ensemble           |
| QED (Alt B)                   | `qed_service.py`      | (internal)              | Quartic potential stability      |

### Evaluation Metrics

| Metric                     | Description                                                   | Target                        |
|----------------------------|---------------------------------------------------------------|-------------------------------|
| Regime Transition Speed    | Time from volatility event start to regime detection          | GARCH faster than K-means     |
| False Positive Rate        | Regime changes that do not correspond to actual market shifts | < 5%                          |
| Sharpe Ratio Under Regime  | Strategy Sharpe when using each regime detector               | Highest wins                  |
| Crash Prediction Lead Time | How far in advance each model detects crashes                 | QED expected to lead          |
| Backtest Overfitting Score | Calibration stability during walk-forward testing             | Online SGD must remain stable |

### Complementary Modules Applied to All Models

The complementary modules (Event-Based Time, Climate Sensitivity, Natural Disaster) are regime-detector-agnostic. They
feed additional signals into whichever detector is active:

- **Event-Based Time** provides `SurpriseIndicator` as an additional input feature.
- **Climate Sensitivity** adjusts ILI thresholds for long-term structural shifts.
- **Natural Disaster** triggers the `EXOGENOUS_SHOCK` regime override regardless of which detector is active.

---

## Source Proposals

| # | Original Proposal                | File                                                 | Section           |
|---|----------------------------------|------------------------------------------------------|-------------------|
| 1 | Volatility-Aware Regime (GARCH)  | `VOLATILITY_AWARE_REGIME_PROPOSAL.md`                | Core 1.1          |
| 2 | DL Regime Detection (CNN-LSTM)   | `DL_REGIME_DETECTION_PROPOSAL.md`                    | Alternative A 2.1 |
| 3 | QED Market Dynamics              | `QED_MARKET_DYNAMICS_PROPOSAL.md`                    | Alternative B 2.2 |
| 4 | Event-Based Intrinsic Time       | `EVENT_BASED_INTRINSIC_TIME_INTEGRATION_PROPOSAL.md` | Complementary 3.1 |
| 5 | Climate-Liquidity Sensitivity    | `CLIMATE_LIQUIDITY_SENSITIVITY_PROPOSAL.md`          | Complementary 3.2 |
| 6 | Natural Disaster Exogenous Shock | `NATURAL_DISASTER_EXOGENOUS_SHOCK_PROPOSAL.md`       | Complementary 3.3 |
