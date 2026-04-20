# Milestone 5: Data Grids with Perspective

**Status:** DONE
**Depends on:** M1 (Scaffolding, Layout, Auth, API Client)
**Estimated scope:** ~4 files

## Objective

Implement high-density data visualizations using FINOS Perspective for tabular data grids and heatmaps. These components handle large real-time datasets where custom D3.js would struggle with rendering performance.

## Components

### 5.1 Correlation Matrix (Perspective)

**File:** `components/charts/CorrelationMatrix.tsx`

- `<perspective-viewer>` widget for high-frequency correlation data
- Heatmap: rolling correlation (`TA_CORREL`) between funding metrics and equities
- Real-time updates via WebSocket — Perspective handles incremental data push natively
- Tooltip with: p-value, sample size, AIC-selected lag order
- Color scale: blue (negative) → white (zero) → red (positive)
- Sortable, filterable columns for p-value and correlation threshold exploration

### 5.2 Signal Log (Perspective Data Grid)

**File:** `components/signals/SignalLog.tsx`

- `<perspective-viewer>` widget as a real-time data grid for signal history
- Columns: timestamp, signal type, status, ILI value, instrument, action
- Sortable and filterable — Perspective handles this natively
- Real-time row insertion via WebSocket `/ws/signals`
- Click-through to signal detail or chart navigation

### 5.3 Liquidity Heatmap (D3.js + Perspective Fallback)

**File:** `components/charts/LiquidityHeatmap.tsx`

Primary rendering: D3.js bespoke 4-axis quadrant visualization.

4 axes from Systemic Risk Heatmap KPI:
1. Tri-party vs GCF spread
2. SOFR 99th-25th percentile
3. TGCR vs BGCR spread
4. TGA balance change

Perspective fallback: When data volume exceeds 10K data points, switch to `<perspective-viewer>` for rendering performance.

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/kpi/correlation-matrix` | GET | Correlation matrix data |
| `GET /api/v1/signals` | GET | Signal history for log |
| `GET /api/v1/kpi/systemic-risk-heatmap` | GET | 4-axis systemic risk data |
| `/ws/signals` | WS | Real-time signal updates for log |

## Acceptance Criteria

- [ ] Correlation matrix renders in Perspective widget with real-time updates
- [ ] Correlation heatmap color scale: blue → white → red
- [ ] Tooltip shows p-value, sample size, and AIC lag order
- [ ] Columns are sortable and filterable
- [ ] Signal log renders as Perspective data grid with real-time row insertion
- [ ] Liquidity heatmap renders 4-axis quadrant with D3.js
- [ ] Perspective fallback activates when data exceeds 10K points
- [ ] Perspective `<perspective-viewer>` integrates visually with TailwindCSS design
- [ ] Perspective WebAssembly module loads and initializes correctly
- [ ] Unit tests for data transformation and threshold logic
- [ ] No visual conflicts between Perspective widget styles and TailwindCSS

## Technical Notes

- Perspective requires loading a WebAssembly module — handle loading state gracefully
- The Perspective viewer has its own styling system; wrap in a container that respects TailwindCSS layout
- D3.js heatmap should use `d3-scale` for color interpolation
- The 10K-point threshold for Perspective fallback should be configurable
- Consider lazy-loading Perspective to reduce initial bundle size
