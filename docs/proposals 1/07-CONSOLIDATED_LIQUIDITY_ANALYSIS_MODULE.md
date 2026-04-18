# Consolidated Proposal: Liquidity Analysis Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)
**Consolidation Date:** 2026-05-21

## v3 Integration Points

| Track                             | Components Affected                                                                                                                          |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Track 2 (DB)                      | `tick_data` schema, `ohlcv_1min` aggregates, `order_flow_imbalance` column, `market_events` hypertable                                       |
| Track 3 (Analytics Worker)        | `LiquidityComovementMonitor`, `MLFeatureEngine`, Python PCA/statistical services                                                             |
| Track 4 (Ingestion)               | `PolygonWsClient`, `ToxicityMonitor`, `EventBasedTimeConverter`, `SessionTimestamping`, economic calendar client                             |
| Track 5 (Computation)             | `SignalGenerator`, `KpiProcessor`, `BehaviouralRiskProcessor`, `TimeOfDayThresholdManager`, `SessionRangeService`, `SaliProcessor`           |
| Track 7 (Dashboard)               | Liquidity Reliability score, Market Toxicity Heatmap, Trader Type Dominance panel, Systemic Risk Heatmap (5th axis), Behavioural Stress axis |
| Track 8 (Backtesting)             | AT proxy modelling, Brazil market case study, news-aware backtesting, dynamic RRR testing                                                    |
| Track 10 (Demo/Virtual Portfolio) | `PaperTradingEngine` fill probability, impact-sensitive execution, herding filter, panic guard, liquidity-aware execution mode               |

---

## PART I: CORE PROPOSALS

These three modules form the foundational liquidity monitoring layer. Each is independently deployable but they are
designed to feed into one another.

---

### 1.1 Liquidity Comovement Monitor

**Source:** SSRN-2337523 (Huh, 2011)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation)

#### Rationale

Algorithmic trading increases liquidity comovement (correlation) across stocks, effectively raising systemic liquidity
risk. Higher commonality means stocks become illiquid together, especially during stress. A spike in liquidity
comovement is a leading indicator for systemic stress not currently monitored by the ILI.

#### Module: LiquidityComovementMonitor (Track 3)

- **Location:** `analytics/app/services/liquidity_comovement.py`
- **Implementation:** Calculate the **Rolling Comovement Factor** using first-differenced standardized spreads.
    - Methodology: Principal Component Analysis (PCA) on 5-minute bucketed, first-differenced, standardized spreads.
    - Factor: The percentage of variance explained by the first two principal components.
- **Output:** `LiquidityComovementFactor` (float, 0-1 range) exposed via `/api/v1/analytics/comovement-factor`

#### Module: Systemic Risk Trigger (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/risk/ComovementTrigger.java`
- **Implementation:** Integrate `LiquidityComovementFactor` into `SignalGenerator`.
    - When the factor exceeds a historical 90-day threshold, flag the `LiquidityStressIndex` as "Elevated Systemic
      Risk".
    - The `PaperTradingEngine` switches to a more defensive, liquidity-preserving stance.

#### Validation Criteria

- [ ] Implement `LiquidityComovementMonitor` with PCA-based variance calculation.
- [ ] Incorporate the comovement factor into the LSI (Liquidity Stress Index).
- [ ] Verify that systemic liquidity risk spikes correlate with historical periods of market dislocation (e.g., Flash
  Crash).

---

### 1.2 Phantom Liquidity Detection

**Source:** SSRN-3846814 (Sadaf et al., 2021)
**Tracks:** Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)

#### Rationale

"Ghost liquidity" or "phantom liquidity" appears present in the order book but is canceled before execution. This
represents unreliable funding depth that should discount the ILI. The paper also highlights a shift towards "
behavioural" regulation focusing on outcomes rather than intent.

#### Module: PhantomLiquidityService (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/liquidity/PhantomLiquidityService.java`
- **KPI - Phantom Liquidity Index (PLI):** Compare `TVOBV` (Total Volume at Best Quotes) with `EXEC` (Fully Executed
  Orders) over short intervals.
    - A high ratio of canceled-to-executed volume indicates high "phantom liquidity".
    - PLI should discount the ILI as it represents unreliable funding depth.
- **Persistence:** Ensure `ohlcv_1min` aggregate includes `canceled_volume` if supported by the Polygon feed.

#### Module: Execution Fill Probability (Track 10)

- **Location:** `demo/src/main/java/com/tickonomics/demo/execution/FillProbabilityEngine.java`
- **Implementation:** Refine `PaperTradingEngine` to use PLI as a "fill probability" filter.
    - If PLI is high, simulate higher chance of "missed fills" or increased slippage.
    - The liquidity seen at the time of the signal was likely "phantom".

#### Validation Criteria

- [ ] Virtual portfolio "Realized Slippage" increases linearly with the PLI.
- [ ] `LiquidityStressIndex` shows higher predictive accuracy when weighted by the "Liquidity Reliability" score.
- [ ] Frontend displays "Liquidity Reliability" score (inverse of PLI) on dashboard cards.

---

### 1.3 Trader Toxicity Monitor

**Source:** SSRN-2813870
**Tracks:** Track 4 (Ingestion), Track 5 (Computation), Track 7 (Dashboard)

#### Rationale

Algorithmic traders are not monolithic; they cluster into "harmful" and "beneficial" groups. Identifying these patterns
allows Tickonomics to improve its ILI by incorporating the quality of liquidity, not just the volume.

#### Module: ToxicityMonitor (Track 4)

- **Location:** `ingestion/src/main/java/com/tickonomics/ingestion/quality/ToxicityMonitor.java`
- **Metrics:** Toxicity scores based on:
    - Order-to-trade ratios.
    - Intraday round-trip trade percentage.
    - Inventory-holding patterns.
- Applied per monitored venue/strategy.

#### Module: Toxicity-Adjusted ILI (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/risk/ToxicityAdjustedIli.java`
- **Implementation:** Enhance `Computation Engine` to adjust ILI sensitivity when venue/asset liquidity is dominated
  by "toxic" patterns.
- Flag "toxic" market conditions in the `Systemic Resilience Monitor`.

#### Module: Market Toxicity Heatmap (Track 7)

- **Location:** Frontend component for the dashboard.
- **Display:** Heatmap showing which assets or venues currently exhibit predatory trading behaviors.

#### Validation Criteria

- [ ] Toxicity scores accurately differentiate predatory vs. liquidity-providing behavior.
- [ ] ILI adjustments based on toxicity improve risk-adjusted performance metrics.
- [ ] Heatmap updates in near-real-time with ingestion data.

---

## PART II: COMPLEMENTARY ADDITIONS

These modules enhance the core liquidity monitoring layer with additional dimensions of analysis.

---

### 2.1 Algorithmic Intensity KPI

**Source:** SSRN-2420017 (Hatch, Johnson, and Zhang, 2019)
**Tracks:** Track 5 (Computation), Track 7 (Dashboard)
**Type:** COMPLEMENTARY

#### Module: AlgorithmicIntensityMetric (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/kpi/AlgorithmicIntensityMetric.java`
- **Implementation:** Message-based AT proxy: `Negative Dollar Volume / Number of Messages`.
- Bucket securities into **quintiles** based on their intensity over the trailing quarter.

#### Module: Firm Valuation Adjustment Factor (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/LiquidityPremiumFactor.java`
- **Implementation:** Incorporate `AlgorithmicIntensityMetric` as a coefficient in `SignalGenerator` cost-benefit
  models.
- Assets in the top quintile of AT Intensity are assigned a "liquidity premium" reflecting improved value-add for highly
  liquid, high-AT firms.

#### Validation Criteria

- [ ] `AlgorithmicIntensityMetric` correlates with historical liquid asset trends.
- [ ] `SignalGenerator` uses the intensity metric as a premium factor.
- [ ] Backtest strategy performance against intensity buckets aligns with expected value-add.

---

### 2.2 Trader-Type Liquidity Dynamics

**Source:** SSRN-3673881 (Broussard et al., 2020)
**Tracks:** Track 4 (Ingestion), Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)
**Type:** COMPLEMENTARY

#### Module: LiquiditySourceClassifier (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/liquidity/LiquiditySourceClassifier.java`
- **Implementation:** Use Polygon trade condition codes and order flow patterns to estimate dominance of trader types:
    - Algorithmic
    - Institutional
    - Professional
    - Retail

#### Module: Spread Compression KPI (Track 5)

- Monitor the "Algorithmic Spread Reduction" effect (approximately 10% lower spreads).
- If this reduction vanishes during high volatility, flag as `LIQUIDITY_REGIME_SHIFT`.

#### Module: OrderCancellationMonitor (Track 5)

- Track the ratio of cancellations to total orders.
- High cancellation rates by "ALGOs" indicate "fishing" for true prices, usable as a leading indicator of price
  discovery.

#### Module: Impact-Sensitive Execution (Track 10)

- Adjust virtual execution model to account for "Maker-Taker" arrangements.
- Apply different slippage penalties based on whether virtual trade "takes" liquidity from a retail provider vs. an
  algorithmic maker.

#### Validation Criteria

- [ ] Correlation between high "ALGO" dominance and tighter bid-ask spreads is statistically significant in backtest
  data.
- [ ] `OrderCancellationRate` spikes correctly predict increased volatility in the subsequent 10-minute window.
- [ ] `tick_data` captures sufficient metadata (condition codes, exchange IDs) for trader-type imputation.

---

### 2.3 Behavioural Liquidity Guard

**Source:** SSRN-5001428
**Tracks:** Track 2 (DB), Track 4 (Ingestion), Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)
**Type:** COMPLEMENTARY

#### Rationale

The Behavioural Risk Index (BRI) tracks irrational market behaviors (herding, panic, overconfidence) as manifested in
order book microstructure and sentiment, providing a "psychological overlay" to the quantitative ILI. Order Flow
Imbalance and spread volatility often move minutes before macro-liquidity indicators, providing a "leading lead"
indicator.

#### Module: Microstructure Ingestion (Track 4)

- Update `PolygonWsClient` to capture Order Book Depth and Order Flow (not just price ticks).
- Implement real-time calculation of **Order Flow Imbalance (OFI)** on a per-symbol basis.

#### Module: BehaviouralRiskProcessor (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/risk/BehaviouralRiskProcessor.java`
- **BRI Calculation:** Weighs:
    - Z-scored Order Flow Imbalance.
    - Bid-Ask Spread Volatility (relative to the `DefiningRange`).
    - Sentiment Polarity (from the SALI/BERT proposals).
- **Systemic Risk Heatmap:** Add a 5th axis to the JSON matrix for "Behavioural Stress."

#### Module: Herding Filter & Panic Guard (Track 10)

- **Herding Filter:** Suppress signals if BRI indicates "Extreme Herding" (irrational momentum), as this often precedes
  a reversal.
- **Panic Guard:** Use BRI to trigger "Panic Mode" in `PaperTradingEngine`, automatically tightening stop-losses during
  periods of high behavioural risk.

#### Schema Changes (Track 2)

- Add `order_flow_imbalance` column to the `tick_data` table.

#### Validation Criteria

- [ ] OFI and spread volatility move before macro-liquidity indicators in historical data.
- [ ] System distinguishes between "healthy" liquidity drains and "panic-driven" selling.
- [ ] Microstructure-aware trade execution reduces market impact and slippage.

---

### 2.4 Intraday Liquidity Pattern Alignment

**Source:** SSRN-1913693 (Viljoen, Westerholm, Zheng, 2011)
**Tracks:** Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)
**Type:** COMPLEMENTARY

#### Module: TimeOfDayThresholdManager (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/signal/TimeOfDayThresholdManager.java`
- **Implementation:** Make signal thresholds (`buy_percentile`, `sell_percentile`) adaptive based on time-of-day.
    - Dynamically tighten thresholds during high-asymmetry (open/close) periods.
    - Relax thresholds during the middle of the trading day when liquidity provision is more efficient.
    - Leverages the well-documented "reverse U-shape" intraday liquidity pattern.

#### Module: Liquidity-Aware Execution (Track 10)

- Add a "liquidity-aware" execution mode to `PaperTradingEngine`.
- For `ACTIONABLE` signals, execution is delayed by 1-2 intervals during high-asymmetry market open/close.
- Waiting allows the algorithm to "price in" the shock.

#### Validation Criteria

- [ ] `TimeOfDayThresholdManager` integrated into `SignalGenerator`.
- [ ] "Liquidity-Aware" execution mode added to `PaperTradingEngine`.
- [ ] Backtest against intraday market data to verify hit rate improvement.

---

### 2.5 Intraday Session Filter

**Source:** SSRN-4492355
**Tracks:** Track 4 (Ingestion), Track 5 (Computation), Track 10 (Demo/Virtual Portfolio)
**Type:** COMPLEMENTARY

#### Rationale

The **Defining Range (DR)** concept establishes that the price range between 9:30 AM and 10:30 AM EST has an 85%
probability of containing either the intraday high or low. Technical/algorithmic accuracy drops from approximately 88%
to 44% during high-impact news events.

#### Module: Economic Calendar Ingestion (Track 4)

- Poll for high-impact news events via FRED's release schedule or a dedicated Economic Calendar API.
- Tag all `tick_data` and `rate_snapshots` with their relative position in the NY trading session (Pre-Open, DR Window,
  Post-DR).

#### Module: SessionRangeService (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/session/SessionRangeService.java`
- **Implementation:** Calculate high/low of the first 60 minutes of the NY session (9:30-10:30 AM EST).
- **Session Filter Logic in SignalGenerator:**
    - BUY signals are higher confidence if price is near the DR Low.
    - SELL signals are higher confidence if price is near the DR High.
- **News Confidence Decay:** Lower signal `status` from `ACTIONABLE` to `SPECULATIVE` within +/- 30 minutes of a
  high-impact news release.

#### Module: News-Aware Backtesting (Track 10)

- Replay historical signals alongside an economic calendar to verify accuracy drops during news events.
- Test 2:1 Risk-Reward Ratio (RRR) for intraday trades initiated after a DR breach.

#### Validation Criteria

- [ ] `DefiningRangeIndicator` tracks 5m candles in the 9:30-10:30 EST window.
- [ ] Signal confidence correctly decays during news windows.
- [ ] Reduced intraday noise and improved hit rate in backtesting.

---

## PART III: ALTERNATIVE PLUGGABLE IMPLEMENTATIONS (A/B Testing Candidates)

The following provides an **alternative KPI** for time-sensitive liquidity analysis. It can be compared against the
TimeOfDayThresholdManager approach from the Intraday Liquidity Pattern Alignment (Section 2.4) using A/B testing.

---

### 3.1 Algorithmic Trading Liquidity Impact (Alternative to TimeOfDayThresholdManager)

**Source:** SSRN-3413130 (Ramos and Perlin, 2019)
**Tracks:** Track 3 (Analytics Worker), Track 5 (Computation), Track 8 (Backtesting)
**Type:** ALTERNATIVE PLUGGABLE IMPLEMENTATION

#### Rationale

Algorithmic trading has a **dual effect**: it improves liquidity in the short run (1-minute horizon, reducing spreads
and price impact) but can reduce market quality over longer horizons (20-minute horizon). This provides an alternative
lens to the time-of-day approach by focusing on AT activity levels rather than session timing.

#### Module: AmihudMeasureService (Track 3)

- **Location:** `analytics/app/services/amihud_measure.py`
- **Implementation:** Compute high-frequency price impact metric (Amihud measure).

#### Module: LiquidityMeanReversionSpeed KPI (Track 5)

- **Location:** `computation/src/main/java/com/tickonomics/computation/kpi/LiquidityMeanReversionSpeed.java`
- **Implementation:** Measure how quickly price impact decays after a period of high AT activity.
- AT activity estimated via message intensity from Polygon.
- **Refined Liquidity Stress Index:** Adjust LSI to account for the "lagged reduction in market quality" finding. When
  high-frequency AT activity spikes, the system looks for a delayed liquidity drain 20 minutes later.

#### Module: AT Proxy in Backtesting (Track 8)

- Implement the Hendershott et al. (2011) AT activity proxy (`Volume/Messages`) in the backtest engine.
- Simulate how strategies react to varying levels of algorithmic saturation.

#### Persistence

- Add `at_activity_proxy` to `tick_data` aggregates.

#### Validation Criteria

- [ ] Correlation between `at_activity_proxy` and `realized_spreads` matches the paper's findings (-0.200 in the short
  run).
- [ ] The `LiquidityStressIndex` correctly flags the 20-minute "worsening" effect.
- [ ] Java backend `KpiProcessor` calculates AT proxy if message counts are available.

#### A/B Testing Plan

| Dimension             | Approach A (TimeOfDayThresholdManager) | Approach B (LiquidityMeanReversionSpeed)    |
|-----------------------|----------------------------------------|---------------------------------------------|
| Signal                | Time-based threshold adaptation        | AT-activity-based liquidity drain detection |
| Horizon               | Session-level (open/mid/close)         | 1-min vs 20-min horizon comparison          |
| Data requirement      | Timestamp metadata only                | Message counts + volume data                |
| Deployment complexity | Lower                                  | Higher                                      |
| Recommended for       | Initial v3 deployment                  | Phase 2 after AT proxy is validated         |

---

## Source Proposals

| # | Original Proposal                    | File                                               | Section           |
|---|--------------------------------------|----------------------------------------------------|-------------------|
| 1 | Liquidity Comovement Monitor         | `LIQUIDITY_COMOVEMENT_MONITOR_PROPOSAL.md`         | Core 1.1          |
| 2 | Phantom Liquidity Detection          | `PHANTOM_LIQUIDITY_DETECTION_PROPOSAL.md`          | Core 1.2          |
| 3 | Trader Toxicity Monitor              | `TRADER_TOXICITY_MONITOR_PROPOSAL.md`              | Core 1.3          |
| 4 | Algorithmic Trading Liquidity Impact | `ALGORITHMIC_TRADING_LIQUIDITY_IMPACT_PROPOSAL.md` | Alternative 3.1   |
| 5 | Algorithmic Intensity KPI            | `ALGORITHMIC_INTENSITY_KPI_PROPOSAL.md`            | Complementary 2.1 |
| 6 | Trader-Type Liquidity Dynamics       | `TRADER_TYPE_LIQUIDITY_DYNAMICS_PROPOSAL.md`       | Complementary 2.2 |
| 7 | Behavioural Liquidity Guard          | `BEHAVIOURAL_LIQUIDITY_GUARD_PROPOSAL.md`          | Complementary 2.3 |
| 8 | Intraday Liquidity Pattern Alignment | `INTRADAY_LIQUIDITY_PATTERN_ALIGNMENT.md`          | Complementary 2.4 |
| 9 | Intraday Session Filter              | `INTRADAY_SESSION_FILTER_PROPOSAL.md`              | Complementary 2.5 |
