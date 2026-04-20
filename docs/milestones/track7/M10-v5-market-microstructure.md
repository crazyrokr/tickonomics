# Milestone 10: v5 Market Microstructure

**Status:** DONE
**Depends on:** M3 (KPI Cards), M5 (Perspective Grids)
**Estimated scope:** ~7 files

## Objective

Implement v5 market microstructure visualizations: toxicity heatmap, trader type dominance, behavioural stress axis (5th systemic risk axis), liquidity reliability scoring, signal informativeness (PJR), and performance duality comparison.

## Components

### 10.1 Market Toxicity Heatmap

**File:** `components/charts/ToxicityHeatmap.tsx`

- Heatmap showing which assets or venues currently exhibit predatory trading behaviors
- Color scale: green (beneficial) → yellow (neutral) → red (harmful)
- Updates near-real-time with ingestion data

Data source: `GET /api/v1/analytics/toxicity`

### 10.2 Trader Type Dominance Panel

**File:** `components/panels/TraderTypePanel.tsx`

- Stacked bar chart showing estimated dominance per symbol: Algorithmic, Institutional, Professional, Retail
- Spread compression overlay showing AT vs. non-AT spread difference

Data source: `GET /api/v1/analytics/trader-types`

### 10.3 Behavioural Stress Axis

**Integration into:** `components/charts/LiquidityHeatmap.tsx`

- Adds 5th axis "Behavioural Stress" to the Systemic Risk Heatmap
- Shows BRI components: OFI z-score, spread volatility, sentiment polarity
- Herding and panic indicators displayed as badges when thresholds exceeded

Data source: `GET /api/v1/kpi/systemic-risk-heatmap` (augmented with 5th axis)

### 10.4 Liquidity Reliability Score

**Integration into:** KPI cards (M3)

- Displays "Liquidity Reliability" score (inverse of PLI) on dashboard cards
- Amber badge when PLI is high (unreliable depth)
- Historical PLI sparkline showing phantom liquidity trend

Data source: `GET /api/v1/kpi/phantom-liquidity`

### 10.5 Signal Informativeness Panel

**File:** `components/panels/SignalInformativenessPanel.tsx`

- Visualizes PJR (Price Jump Ratio) for recent signals
- Helps distinguish informed trading from noise reaction
- Shows CAR (Cumulative Abnormal Return) decomposition chart

Data source: `GET /api/v1/backtest/pjr`

### 10.6 Performance Duality Comparison

**Standalone panel or dashboard integration**

- Side-by-side comparison of "Pure ILI" strategy vs. "Subjective Benchmark" (Buy & Hold)
- Detailed "ILI Confidence Score" for every real-time data point
- Empirically demonstrates the algorithm's objective edge
- Updates in real-time with live data

Data source: `GET /api/v1/demo/performance-duality`

## Acceptance Criteria

- [ ] Toxicity heatmap updates near-real-time with color-coded classification
- [ ] Color scale: green (beneficial) → yellow (neutral) → red (harmful)
- [ ] Trader type dominance panel shows estimated breakdown per symbol
- [ ] Stacked bar chart renders all 4 trader types
- [ ] Liquidity heatmap includes 5th axis for behavioural stress
- [ ] Herding and panic indicators appear as badges when thresholds exceeded
- [ ] Liquidity reliability score displays inverse PLI with amber badge on high PLI
- [ ] PLI sparkline shows historical phantom liquidity trend
- [ ] Signal informativeness panel shows PJR for recent signals
- [ ] CAR decomposition chart renders correctly
- [ ] Performance duality comparison updates in real-time
- [ ] ILI Confidence Score displayed for each data point
- [ ] Unit tests for toxicity color mapping, trader type stacking, PJR calculation

## Technical Notes

- Toxicity heatmap updates frequently — use Perspective or Canvas for rendering performance
- Behavioural stress axis extends the existing LiquidityHeatmap from M5 — add the 5th axis as a new quadrant dimension
- Liquidity reliability score integrates into existing KPI cards from M3 — add as a new indicator overlay
- Performance duality comparison needs live WebSocket data — subscribe to `/ws/signals` and `/ws/prices`
- Trader type data may be sparse — handle missing symbols gracefully
