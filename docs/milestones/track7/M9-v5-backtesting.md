# Milestone 9: v5 Backtesting & Explainability

**Status:** DONE
**Depends on:** M2 (Core Charts), M3 (KPI Cards), M5 (Perspective Grids)
**Estimated scope:** ~6 files

## Objective

Implement v5 backtesting visualizations and signal explainability panels: robustness heatmaps, performance decomposition, signal "Why?" explanations, gamma profile chart, and audit trail display.

## Components

### 9.1 Robustness Heatmap

**File:** `components/charts/BacktestHeatmap.tsx`

Two classes of sensitivity visualization:

1. **Time Sensitivity Heatmap:** Shows whether strategy success is concentrated in a specific historical window. A strategy that only profits during COVID crash is not robust.
2. **Parameter Sensitivity Heatmap:** Performance across 2D parameter slices (buy_percentile vs. sell_percentile). Identifies narrow "islands" of profitability indicating overfitting.

Renders with D3.js or Perspective depending on data volume.

Data source: `GET /api/v1/backtest/robustness-scan` and `GET /api/v1/backtest/{id}/heatmap`

### 9.2 Performance Decomposition Panel

**File:** `components/panels/PerformanceDecompositionPanel.tsx`

- Breakdown of strategy gains into `HoldingsReturn` and `ReturnGap` (execution alpha)
- Side-by-side bar chart: static holdings vs. active trading contribution
- Updates with each backtest run and live demo data

Data source: `GET /api/v1/kpi/return-gap`

### 9.3 Signal Explainability Panel

**File:** `components/panels/SignalExplainabilityPanel.tsx`

For every `ACTIONABLE` signal, shows a "Why?" tooltip/panel:
- Contributing Z-scores (RRP, Spread, Vol)
- Current percentile rank relative to last 252 days
- Active filters (e.g., "DR Window Breach confirmed")
- Visualizes the weighted sum formula of ILI in real-time
- Must load within 200ms

Data source: `GET /api/v1/signals/{id}/explain`

### 9.4 Gamma Profile Chart

**File:** `components/charts/GammaProfileChart.tsx`

- "Gamma Flip Zone" visualization showing price level where market moves from positive to negative gamma
- Color-coded regions: Long Gamma (green, dampening) vs. Short Gamma (red, amplifying)
- Only visible when options data ingestion is enabled

Data source: `GET /api/v1/gex/aggregate`

### 9.5 Audit Trail Display

**Integration into:** `components/config/ConfigEditor.tsx` and `components/config/ConfigHistory.tsx`

- Every automated configuration update displays mandatory `audit_reason` field alongside the change diff
- Dashboard shows intent clearly: e.g., "Recalibration based on Q2 2026 ILI drift"
- `ConfigHistory.tsx` shows `audit_reason` as a mandatory column

Data source: `GET /api/v1/config/history` (augmented with audit_reason)

## Acceptance Criteria

- [ ] Time sensitivity heatmap renders showing strategy performance across historical windows
- [ ] Parameter sensitivity heatmap renders 2D parameter slice grid
- [ ] Narrow profitability "islands" visually distinct (indicating overfitting)
- [ ] Performance decomposition panel shows HoldingsReturn vs. ReturnGap breakdown
- [ ] Side-by-side bar chart updates with each backtest run
- [ ] Signal explainability panel loads within 200ms
- [ ] Explainability shows contributing Z-scores with percentile ranks
- [ ] Active filters listed for each explained signal
- [ ] ILI weighted sum formula visualized
- [ ] Gamma profile chart shows gamma flip zone when options data enabled
- [ ] Chart hidden/disabled when options data ingestion is off
- [ ] Config history shows `audit_reason` as mandatory column
- [ ] No configuration change visible without `audit_reason`
- [ ] Unit tests for heatmap data transformation, decomposition math, explainability rendering

## Technical Notes

- Robustness heatmaps may use large 2D datasets — implement Canvas rendering for performance
- Signal explainability has a strict 200ms load requirement — pre-fetch explanation data on signal arrival
- Gamma profile chart should gracefully degrade when options data is unavailable (show "Enable options data" message)
- Audit trail integration extends ConfigHistory from M6 — add `audit_reason` column to existing table
