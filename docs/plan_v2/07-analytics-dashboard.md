# Track 7: Analytics Dashboard (`app.tickonomics.io`)

**Phase:** Phase 4
**Can start:** After Track 1 (needs API contract types)
**Blocks:** Nothing (frontend-only, other tracks don't depend on it)
**Depends on:** Track 1 (API contracts and TypeScript types)

---

## Objective

Build the analytics dashboard as a Next.js 15 single-page application with real-time
updates via WebSocket, multi-pane charts, and KPI visualization cards.

**Analysis findings applied (v1):**

- **Finding 1 (Virtual Threads Primary):** Dashboard connects to the Virtual Threads + Spring MVC
  backend (single mode). No dual-mode UI toggle needed.
- **Finding 3 (Proxy Divergence Guard):** Data Freshness Panel shows proxy divergence status.
  Dislocated ILI periods flagged with `DISLOCATED` badge. Proxy-derived signals marked
  `SPECULATIVE_STALE_MACRO`.
- **Finding 5 (Direct FRED/NY Fed Clients):** Data Freshness Panel shows direct FRED and NY Fed
  client health (not OpenBB) for core ILI data sources. OpenBB sidecar status shown only for
  equity price data.
- **Finding 6 (Dynamic Weighting):** ILI card displays degraded status when components are NaN.
  Active weights shown after dynamic redistribution.

**External integration (v2):**

- **Perspective (Adopt):** Used for high-frequency data grids and heatmaps. Handles massive,
  real-time updates better than custom D3.js tables. Applied to correlation matrix, signal log,
  and liquidity heatmap — contexts where tabular data density matters more than bespoke styling.
  Confined to data-heavy panels to avoid visual friction with TailwindCSS design.
- **FINOS FDC3 (Defer):** Future interoperability with professional financial desktops
  (Bloomberg, Reuters, OpenFin). Not implemented now. A future `fdc3/` integration layer would
  implement the Desktop Agent interface for context sharing and intent-based navigation.

---

## Technology

| Layer           | Technology                                          |
|:----------------|:----------------------------------------------------|
| Framework       | Next.js 15 (App Router)                             |
| Data Fetching   | TanStack Query (React Query)                        |
| Styling         | TailwindCSS                                         |
| Language        | TypeScript                                          |
| Price Charts    | Lightweight Charts (TradingView)                    |
| Heatmaps        | D3.js (bespoke), **Perspective** (data grids) — v2  |
| Real-time       | WebSocket to `/ws/signals` and `/ws/prices`         |
| Auth            | OAuth2 + PKCE via Spring Security (Auth0/Keycloak)  |
| Testing         | React Testing Library (component), Playwright (E2E) |
| Desktop Interop | FDC3 (future, not yet implemented) — v2             |

---

## Dashboard Components

### 1. Multi-Pane Price + ILI Chart (Lightweight Charts)

**Top pane:** Equity price candlestick from TimescaleDB continuous aggregates.

**Bottom pane:** ILI overlay with buy/sell signal markers.

- Signal markers distinguish `ACTIONABLE`, `SPECULATIVE_STALE_MACRO`, `COST_EXCEEDS_EXPECTED_MOVE`,
  `COOLDOWN`, and `INSUFFICIENT_DATA`.
- ILI status line shows `VALID`, `DEGRADED_COMPONENT_STALE`, or `DISLOCATED` with color coding
  (green/yellow/red). Dislocated periods shaded in red (Finding 3).

Data sources:

- `GET /api/v1/kpi/ili/history` — ILI time-series.
- `GET /api/v1/signals` — signal markers.
- WebSocket `/ws/prices` — real-time price updates.

### 2. Correlation Matrix (Perspective — v2)

- **Perspective** `<perspective-viewer>` widget for high-frequency correlation data.
- Heatmap: rolling correlation (`TA_CORREL`) between funding metrics and equities.
- Real-time updates via WebSocket — Perspective handles incremental data push natively.
- Tooltip with: p-value, sample size, AIC-selected lag order.
- Color scale: blue (negative) → white (zero) → red (positive).
- Sortable, filterable columns for p-value and correlation threshold exploration.

Data source: `GET /api/v1/kpi/correlation-matrix` (or equivalent).

### 3. Liquidity Heatmap (D3.js + Perspective fallback — v2)

- 4-axis quadrant from Systemic Risk Heatmap KPI.
- Axes: tri-party vs GCF spread, SOFR 99th-25th pctl, TGCR vs BGCR spread, TGA balance change.
- **Perspective fallback:** When data volume exceeds 10K data points, switch to Perspective
  `<perspective-viewer>` for rendering performance.

Data source: `GET /api/v1/kpi/systemic-risk-heatmap`.

### 4. KPI Dashboard Cards

- **Current ILI** with historical sparkline. Displays status badge (`VALID`,
  `DEGRADED_COMPONENT_STALE`, `DISLOCATED`). When degraded, shows active weights and excluded
  components (Finding 6).
- **Liquidity Stress Index** gauge (positive = stress).
- **Repo/Equity Beta** table per watched symbol.
- **RRP Drain Velocity** trend line.
- **Volatility Regime** indicator (from `TA_BBANDS`): low/normal/high with color coding.

Data sources:

- `GET /api/v1/kpi/ili`
- `GET /api/v1/kpi/liquidity-stress`
- `GET /api/v1/kpi/repo-equity-beta`
- `GET /api/v1/kpi/rrp-drain`
- `GET /api/v1/kpi/volatility-regime`

### 5. Data Freshness Panel

- Official data vs. T-Bill proxy indicators.
- **Proxy Divergence Guard status** (Finding 3): rolling 5-day T-Bill/SOFR correlation, divergence
  score, and `DISLOCATED` flag when > 2 std deviations from expected relationship.
- **Direct FRED client** health and last sync (Finding 5).
- **Direct NY Fed client** health and last sync (Finding 5).
- OpenBB sidecar connectivity status (equity prices only).
- Last update timestamps per data source.

Data source: `GET /api/v1/health`.

### 6. System Health Panel

- TimescaleDB health, compression status, continuous aggregate lag.
- Circuit breaker status: OpenBB, Polygon WS, analytics worker.
- Chronicle Queue overflow depth.

Data source: `GET /api/v1/health`.

### 7. Configuration Editor (Admin)

- Live YAML/JSON editor for hot-reloadable config.
- Client-side validation with diff view (before/after).
- Submit via `PUT /api/v1/config`.
- History view via `GET /api/v1/config/history`.

### 8. Real-Time Updates

WebSocket connections:

- `/ws/prices` — real-time price ticks → update chart and KPI cards.
- `/ws/signals` — real-time signal notifications → toast alerts and signal log.

Connection management:

- Auto-reconnect with exponential backoff.
- Connection status indicator in header.
- JWT token validation on WebSocket handshake.

---

## Directory Structure

```
frontend/
├── app/
│   ├── layout.tsx
│   ├── page.tsx                     # Dashboard main page
│   ├── login/
│   │   └── page.tsx                 # OAuth2 login
│   └── globals.css
├── components/
│   ├── charts/
│   │   ├── PriceIliChart.tsx         # Multi-pane Lightweight Charts
│   │   ├── CorrelationMatrix.tsx     # Perspective widget (v2)
│   │   ├── LiquidityHeatmap.tsx      # D3.js + Perspective fallback (v2)
│   │   └── Sparkline.tsx             # Mini sparkline for KPI cards
│   ├── kpi/
│   │   ├── IliCard.tsx
│   │   ├── LiquidityStressGauge.tsx
│   │   ├── RepoEquityBetaTable.tsx
│   │   ├── RrpDrainTrend.tsx
│   │   └── VolatilityRegimeIndicator.tsx
│   ├── panels/
│   │   ├── DataFreshnessPanel.tsx
│   │   └── SystemHealthPanel.tsx
│   ├── config/
│   │   ├── ConfigEditor.tsx
│   │   └── ConfigHistory.tsx
│   ├── signals/
│   │   ├── SignalLog.tsx             # Perspective data grid (v2)
│   │   └── SignalToast.tsx
│   └── layout/
│       ├── Header.tsx
│       ├── Sidebar.tsx
│       └── ConnectionStatus.tsx
├── hooks/
│   ├── useWebSocket.ts
│   ├── useKpiData.ts
│   └── useSignals.ts
├── lib/
│   ├── api.ts                        # Generated from OpenAPI spec
│   ├── auth.ts                       # OAuth2/PKCE client
│   └── websocket.ts
├── types/
│   └── api.ts                        # Generated from OpenAPI spec
├── __tests__/
│   ├── components/
│   └── hooks/
├── e2e/
│   └── dashboard.spec.ts             # Playwright E2E tests
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── Dockerfile
```

---

## Authentication

- OAuth2 + PKCE flow via Spring Security (backend).
- Supported providers: Auth0, Keycloak.
- Optional for single-user local deployment (configurable).
- All `/api/**` endpoints require valid JWT.
- WebSocket endpoints validate token on handshake.

---

## Security

- All API calls include JWT in `Authorization` header.
- HTTPS enforced in production.
- CSP headers configured in Next.js.
- No sensitive data stored in localStorage (use httpOnly cookies for tokens).

---

## Validation

- [ ] Multi-pane chart renders price candles and ILI overlay.
- [ ] Signal markers display correctly for all statuses (ACTIONABLE, SPECULATIVE_STALE_MACRO, etc.).
- [ ] ILI status line shows VALID/DEGRADED_COMPONENT_STALE/DISLOCATED with correct color coding (Finding 3).
- [ ] Dislocated ILI periods shaded in red on chart (Finding 3).
- [ ] ILI card shows active weights when degraded (Finding 6).
- [ ] Data freshness panel shows direct FRED/NY Fed client status (Finding 5).
- [ ] Proxy divergence score and DISLOCATED flag displayed (Finding 3).
- [ ] Correlation matrix renders in Perspective widget with real-time updates (v2).
- [ ] Liquidity heatmap renders 4-axis quadrant with Perspective fallback for large datasets (v2).
- [ ] Signal log renders as Perspective data grid with sorting and filtering (v2).
- [ ] All KPI cards display with correct formatting and sparklines.
- [ ] Data freshness panel shows correct status per source.
- [ ] System health panel shows circuit breaker status.
- [ ] Configuration editor validates and submits changes.
- [ ] WebSocket updates reflect in real-time on charts and cards.
- [ ] OAuth2 login flow works end-to-end.
- [ ] Responsive layout works on desktop and tablet.
- [ ] Component tests pass (React Testing Library).
- [ ] E2E tests pass (Playwright).
- [ ] Build succeeds with `npm run build`.
- [ ] TypeScript compilation passes with zero errors.
- [ ] Perspective `<perspective-viewer>` integrates with TailwindCSS without visual conflicts (v2).
- [ ] Perspective WebAssembly module loads and initializes correctly (v2).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                          |
|:--------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Virtual Threads single-mode, Proxy Divergence Guard, direct FRED/NY Fed freshness, Dynamic Weighting ILI card. Added ILI status badges, dislocated shading, data freshness panel updates.                                                                                                                                            |
| v2      | Replaced Correlation Matrix from D3.js to FINOS Perspective `<perspective-viewer>` widget for high-frequency real-time updates. Added Perspective fallback for Liquidity Heatmap when data exceeds 10K points. Replaced SignalLog with Perspective data grid. Added FDC3 as deferred future extension. Updated technology table with Perspective and FDC3 rows. |
