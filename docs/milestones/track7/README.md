# Track 7: Analytics Dashboard — Milestones

**Status:** PENDING
**Depends on:** Track 1 (DONE)
**Detailed plan:** `docs/plan_v5/07-analytics-dashboard.md`

## Overview

Build the analytics dashboard as a Next.js SPA at `app.tickonomics.io` with real-time WebSocket updates, multi-pane charts, KPI visualization, and 40 dashboard components spanning core infrastructure, v4 regime analytics, and v5 advanced panels.

## Technology Stack

| Layer           | Technology                                          |
|:----------------|:----------------------------------------------------|
| Framework       | Next.js (App Router) — align version with `landing/` |
| Data Fetching   | TanStack Query (React Query)                        |
| Styling         | Tailwind CSS v4 (`@tailwindcss/postcss`)            |
| Language        | TypeScript (strict)                                 |
| Price Charts    | Lightweight Charts (TradingView)                    |
| Heatmaps        | D3.js (bespoke), Perspective (data grids)           |
| Real-time       | WebSocket to `/ws/signals` and `/ws/prices`         |
| Auth            | OAuth2 + PKCE via Spring Security (Auth0/Keycloak)  |
| Testing         | Vitest + React Testing Library (unit), Playwright (E2E) |
| Desktop Interop | FDC3 (future, not yet implemented)                  |

## Milestones

| # | Milestone                                            | Components | Depends on | Status   |
|---|------------------------------------------------------|-----------|------------|----------|
| 1 | [Scaffolding, Layout, Auth, API Client](M1-scaffolding-layout-auth.md) | 7  | —          | DONE     |
| 2 | [Core Charts](M2-core-charts.md)                     | 2  | M1         | DONE     |
| 3 | [KPI Dashboard Cards](M3-kpi-cards.md)               | 6  | M1, M2     | DONE     |
| 4 | [System Monitoring](M4-system-monitoring.md)         | 3  | M1         | DONE  |
| 5 | [Data Grids with Perspective](M5-perspective-grids.md) | 3 | M1         | DONE  |
| 6 | [Configuration & Admin](M6-config-admin.md)          | 2  | M1         | DONE  |
| 7 | [v4 Regime Analytics](M7-v4-regime.md)               | 5  | M2, M3, M4 | DONE  |
| 8 | [v4 Governance & Quality](M8-v4-governance.md)       | 4  | M3, M4     | DONE  |
| 9 | [v5 Backtesting & Explainability](M9-v5-backtesting.md) | 5 | M2, M3, M5 | DONE  |
| 10| [v5 Market Microstructure](M10-v5-market-microstructure.md) | 6 | M3, M5 | DONE  |
| 11| [v5 Trading Intelligence](M11-v5-trading-intelligence.md) | 5 | M2, M3 | DONE  |
| 12| [v5 Statistical Integrity & Risk](M12-v5-statistical-risk.md) | 7 | M2, M3, M5 | DONE  |

## Dependency Graph

```
M1 (Scaffolding)
├── M2 (Core Charts)
│   ├── M3 (KPI Cards) ──────┐
│   ├── M7 (v4 Regime)       │
│   ├── M9 (v5 Backtest)     │
│   ├── M11 (v5 Trading)     │
│   └── M12 (v5 Stats)       │
├── M4 (System Monitoring) ──┤
│   ├── M7 (v4 Regime)       │
│   └── M8 (v4 Governance)   │
├── M5 (Perspective Grids) ──┤
│   ├── M9 (v5 Backtest)     │
│   ├── M10 (v5 Micro)       │
│   └── M12 (v5 Stats)       │
├── M6 (Config Admin)        │
├── M8 (v4 Governance) ──────┘
│   (also depends on M3)
├── M10 (v5 Micro)
│   (also depends on M3)
└── M11 (v5 Trading)
    (also depends on M3)
```

## Validation (Cross-Milestone)

All milestones share these acceptance gates:

- TypeScript compilation passes with zero errors
- `npm run build` succeeds
- Component tests pass (Vitest + React Testing Library)
- Responsive layout works on desktop (1280px+) and tablet (768px+)
- All API calls use typed responses from generated client
- WebSocket reconnect with exponential backoff verified
- No sensitive data in localStorage (httpOnly cookies for tokens)
