# Milestone 11: v5 Trading Intelligence

**Status:** DONE
**Depends on:** M2 (Core Charts), M3 (KPI Cards)
**Estimated scope:** ~6 files

## Objective

Implement v5 trading intelligence panels: sentiment heatmap, execution comparison, pairs verification, leverage regime indicator, and dynamic stops visualization.

## Components

### 11.1 Sentiment Heatmap

**File:** `components/charts/SentimentHeatmap.tsx`

- Heatmap showing sentiment polarity across tracked symbols and macro events
- Dual display: lexicon score (fast) and FinBERT score (accurate) side-by-side
- News correlation markers overlaid on ILI timeline showing causal impact of sentiment on liquidity stress
- Source breakdown: FOMC, Fed speakers, market news, earnings

Data source: `GET /api/v1/sentiment/history`

### 11.2 Execution Comparison Panel

**File:** `components/panels/ExecutionComparisonPanel.tsx`

- Side-by-side cumulative P&L chart comparing passive (limit-order) vs. aggressive (market-order) execution
- "Price Efficiency" metric showing statistical difference between execution modes
- Slippage vs. fill rate trade-off visualization during ILI spike periods

Data source: `GET /api/v1/demo/execution-comparison`

### 11.3 Pairs Verification Panel

**File:** `components/panels/PairsVerificationPanel.tsx`

- Price-distance chart for tracked pairs showing divergence from historical formation period
- Visual indicator when ILI signal and pairs signal diverge (potential dislocation)
- Formation/trading period separator on timeline

Data source: `GET /api/v1/pairs/active`

### 11.4 Leverage Regime Indicator

**File:** `components/panels/LeverageRegimeIndicator.tsx`

- Visual indicator showing current leverage regime: `LEVERAGE_ON` or `LEVERAGE_OFF`
- S&P 500 200-day MA overlay chart with current position
- Historical leverage rotation signals marked on ILI timeline

Data source: `GET /api/v1/leverage/status`

### 11.5 Dynamic Stops Panel

**File:** `components/panels/DynamicStopsPanel.tsx`

- Visualization of current dynamic stop-loss and take-profit levels for open positions
- State indicator: "signal-active" (positive drift) vs. "signal-exhausted" (absorbing noise)
- Stop level history showing real-time adjustments

Data source: `GET /api/v1/stops/active`

## Acceptance Criteria

- [ ] Sentiment heatmap renders dual lexicon/FinBERT scores side-by-side
- [ ] News correlation markers overlaid on ILI timeline
- [ ] Source breakdown filterable by FOMC, Fed speakers, market news, earnings
- [ ] Execution comparison panel shows cumulative P&L for passive vs. aggressive modes
- [ ] Slippage vs. fill rate trade-off visualized during ILI spikes
- [ ] Pairs verification panel shows price-distance chart with formation/trading separator
- [ ] ILI/pairs divergence indicator visible when signals conflict
- [ ] Leverage regime indicator shows `LEVERAGE_ON` or `LEVERAGE_OFF`
- [ ] S&P 500 200-day MA overlay renders with current position marked
- [ ] Dynamic stops panel shows stop-loss and take-profit levels
- [ ] Signal state indicator shows active vs. exhausted states
- [ ] Stop level history chart updates in real-time
- [ ] Unit tests for sentiment scoring, execution P&L calculation, pairs divergence detection

## Technical Notes

- Sentiment heatmap uses two different scoring models (lexicon vs. FinBERT) — ensure both are displayed for comparison
- Execution comparison requires historical P&L data — implement efficient time-series charting
- Pairs verification panel needs formation period data — highlight the formation window on the chart timeline
- Leverage regime indicator integrates with the S&P 500 data from the computation engine
- Dynamic stops panel updates frequently — consider using Canvas rendering for stop level history
