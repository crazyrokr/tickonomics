# Milestone 2: Core Charts

**Status:** DONE
**Depends on:** M1 (Scaffolding, Layout, Auth, API Client)
**Estimated scope:** ~5 files

## Objective

Implement the primary multi-pane chart (equity price candlestick + ILI overlay) with signal markers and ILI status visualization. This is the centerpiece of the dashboard — the component users interact with most.

## Components

### 2.1 Multi-Pane Price + ILI Chart

**File:** `components/charts/PriceIliChart.tsx`

**Top pane:** Equity price candlestick from TimescaleDB continuous aggregates.

**Bottom pane:** ILI overlay with buy/sell signal markers.

Signal marker types:
- `ACTIONABLE` — green triangle up/down
- `SPECULATIVE_STALE_MACRO` — yellow triangle with `?` icon
- `COST_EXCEEDS_EXPECTED_MOVE` — gray cross
- `COOLDOWN` — dimmed circle
- `INSUFFICIENT_DATA` — hollow circle

ILI status line:
- `VALID` — green line
- `DEGRADED_COMPONENT_STALE` — yellow line with dashed segments
- `DISLOCATED` — red line with shaded background for dislocated periods (Finding 3)

Real-time updates via WebSocket `/ws/prices`.

### 2.2 Sparkline Component

**File:** `components/charts/Sparkline.tsx`

Reusable mini-chart for embedding in KPI cards. Lightweight SVG-based sparkline with optional threshold line and color zones.

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/kpi/ili/history` | GET | ILI time-series for bottom pane |
| `GET /api/v1/signals` | GET | Signal markers overlaid on chart |
| `/ws/prices` | WS | Real-time price candlestick updates |

## Acceptance Criteria

- [ ] Multi-pane chart renders price candlesticks in top pane
- [ ] ILI overlay renders in bottom pane with color-coded status line
- [ ] Signal markers display correctly for all 5 status types
- [ ] `DISLOCATED` ILI periods shaded in red on chart
- [ ] `DEGRADED_COMPONENT_STALE` shown as yellow dashed line
- [ ] Real-time price updates apply without full chart re-render
- [ ] Chart supports zoom, pan, and crosshair
- [ ] Sparkline renders with optional threshold line
- [ ] Unit tests for marker type rendering and ILI status coloring
- [ ] Chart loads within 500ms of data arrival

## Technical Notes

- Use `lightweight-charts` (same library as `landing/`) for the candlestick and line rendering
- The chart container must handle resize events for responsive layout
- ILI history endpoint may return large datasets — implement client-side windowing or server-side pagination
- Dislocated period shading requires custom plugin or series overlay on Lightweight Charts
