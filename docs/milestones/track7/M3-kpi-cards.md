# Milestone 3: KPI Dashboard Cards

**Status:** DONE
**Depends on:** M1 (Scaffolding), M2 (Core Charts — for Sparkline reuse)
**Estimated scope:** ~8 files

## Objective

Implement the 5 core KPI dashboard cards that provide at-a-glance metrics for liquidity, risk, and market regime. Each card displays a primary metric, a historical sparkline, and relevant status indicators.

## Components

### 3.1 ILI Card

**File:** `components/kpi/IliCard.tsx`

- Current ILI value with historical sparkline
- Status badge: `VALID` (green), `DEGRADED_COMPONENT_STALE` (yellow), `DISLOCATED` (red)
- When degraded: shows active weights and excluded components (Finding 6)
- Dynamic weight redistribution display: which components are active, which are NaN-excluded

### 3.2 Liquidity Stress Index Gauge

**File:** `components/kpi/LiquidityStressGauge.tsx`

- Gauge visualization (positive = stress)
- Color zones: green (negative/stable) → yellow (mild) → red (high stress)
- Historical trend sparkline

### 3.3 Repo/Equity Beta Table

**File:** `components/kpi/RepoEquityBetaTable.tsx`

- Tabular display of repo/equity beta per watched symbol
- Sortable by symbol, beta value, or last update time
- Color-coded: beta > 1 (red, amplifying), beta < 1 (green, dampening), beta ≈ 1 (neutral)

### 3.4 RRP Drain Velocity Trend

**File:** `components/kpi/RrpDrainTrend.tsx`

- Line chart showing reverse repo facility drain velocity over time
- Trend direction indicator (accelerating/decelerating/stable)
- Current velocity value with day-over-day change

### 3.5 Volatility Regime Indicator

**File:** `components/kpi/VolatilityRegimeIndicator.tsx`

- Regime indicator from Bollinger Bands analysis: `LOW_VOL` / `NORMAL` / `HIGH_VOL`
- Color coding: green / blue / red
- Mini Bollinger Bands chart showing current position relative to bands

### 3.6 KPI Data Hook

**File:** `hooks/useKpiData.ts`

- TanStack Query hook for fetching all KPI data
- Polling interval: 30s for dashboard cards
- WebSocket integration for immediate updates on `/ws/signals`
- Stale-while-revalidate strategy

## Data Sources

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/kpi/ili` | GET | Current ILI value, status, weights |
| `GET /api/v1/kpi/liquidity-stress` | GET | Liquidity Stress Index |
| `GET /api/v1/kpi/repo-equity-beta` | GET | Repo/Equity Beta per symbol |
| `GET /api/v1/kpi/rrp-drain` | GET | RRP Drain Velocity trend |
| `GET /api/v1/kpi/volatility-regime` | GET | Current volatility regime |

## Acceptance Criteria

- [ ] ILI card displays current value with status badge (VALID/DEGRADED/DISLOCATED)
- [ ] ILI card shows active weights when status is DEGRADED
- [ ] Liquidity Stress gauge renders with correct color zones
- [ ] Repo/Equity Beta table is sortable and color-coded
- [ ] RRP Drain trend shows direction indicator
- [ ] Volatility Regime indicator shows LOW/NORMAL/HIGH with correct colors
- [ ] All sparklines render historical data
- [ ] KPI data hook polls every 30s and updates immediately on WebSocket push
- [ ] Cards handle loading, error, and empty states gracefully
- [ ] Unit tests for each card component with mock data

## Technical Notes

- Each card should be a self-contained component that can be independently tested
- The useKpiData hook should use TanStack Query's `useQuery` with `refetchInterval`
- Cards should animate value changes (not re-mount on every update)
- Color values should use the Tailwind theme tokens from globals.css
