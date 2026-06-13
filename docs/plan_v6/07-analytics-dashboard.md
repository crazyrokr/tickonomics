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
  client health (not OpenBB) for core ILI data sources. Yahoo Finance + Finnhub connectivity
  shown for equity price data.
- **Finding 6 (Dynamic Weighting):** ILI card displays degraded status when components are NaN.
  Active weights shown after dynamic redistribution.

**External integration (v2):**

- **Perspective (Adopt):** Used for high-frequency data grids and heatmaps. Handles massive,
  real-time updates better than custom D3.js tables. Applied to correlation matrix, signal log,
  and liquidity heatmap -- contexts where tabular data density matters more than bespoke styling.
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
| Heatmaps        | D3.js (bespoke), **Perspective** (data grids) -- v2  |
| Real-time       | WebSocket to `/ws/signals` and `/ws/prices`         |
| Auth            | OAuth2 + PKCE via Spring Security (Auth0/Keycloak)  |
| Testing         | React Testing Library (component), Playwright (E2E) |
| Desktop Interop | FDC3 (future, not yet implemented) -- v2             |

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

- `GET /api/v1/kpi/ili/history` -- ILI time-series.
- `GET /api/v1/signals` -- signal markers.
- WebSocket `/ws/prices` -- real-time price updates.

### 2. Correlation Matrix (Perspective -- v2)

- **Perspective** `<perspective-viewer>` widget for high-frequency correlation data.
- Heatmap: rolling correlation (`TA_CORREL`) between funding metrics and equities.
- Real-time updates via WebSocket -- Perspective handles incremental data push natively.
- Tooltip with: p-value, sample size, AIC-selected lag order.
- Color scale: blue (negative) -> white (zero) -> red (positive).
- Sortable, filterable columns for p-value and correlation threshold exploration.

Data source: `GET /api/v1/kpi/correlation-matrix` (or equivalent).

### 3. Liquidity Heatmap (D3.js + Perspective fallback -- v2)

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
- Yahoo Finance + Finnhub connectivity status (equity prices).
- Last update timestamps per data source.

Data source: `GET /api/v1/health`.

### 6. System Health Panel

- TimescaleDB health, compression status, continuous aggregate lag.
- Circuit breaker status: Yahoo Finance, Finnhub WS, analytics worker.
- Chronicle Queue overflow depth.

Data source: `GET /api/v1/health`.

### 7. Configuration Editor (Admin)

- Live YAML/JSON editor for hot-reloadable config.
- Client-side validation with diff view (before/after).
- Submit via `PUT /api/v1/config`.
- History view via `GET /api/v1/config/history`.

### 8. Real-Time Updates

WebSocket connections:

- `/ws/prices` -- real-time price ticks -> update chart and KPI cards.
- `/ws/signals` -- real-time signal notifications -> toast alerts and signal log.

Connection management:

- Auto-reconnect with exponential backoff.
- Connection status indicator in header.
- JWT token validation on WebSocket handshake.

### 9. Volatility Cluster Visualization (v4 -- Proposal 03: GARCH)

- Component: `components/charts/VolatilityClusterChart.tsx`
- Displays current GARCH-implied risk regime over time.
- Color-coded clusters: `LOW_VOL` (green), `NORMAL` (blue), `HIGH_VOL` (red).
- Regime transitions highlighted with vertical markers.
- Legend showing current regime and time spent in each cluster.

Data source: `GET /api/v1/regime/garch`.

### 10. Regime Comparison Panel (v4 -- Proposal 03: A/B Testing)

- Component: `components/panels/RegimeComparisonPanel.tsx`
- Side-by-side comparison of GARCH, CNN-LSTM, and QED regime classifications.
- Shows agreement/disagreement between models with consensus indicator.
- Highlight periods where models diverge (potential regime transition zones).
- Historical accuracy overlay showing which model performed best during past transitions.

Data source: `GET /api/v1/regime/compare`.

### 11. Potential Well Chart (v4 -- Proposal 03: QED)

- Component: `components/charts/PotentialWellChart.tsx`
- Shows current price position relative to QED minima.
- Visualizes metastable vs unstable potential regions.
- Particle-in-well analogy: price as a ball rolling on the potential surface.
- Depth of well indicates stability; shallow wells flagged as transition risk zones.

Data source: `GET /api/v1/regime/qed`.

### 12. Disaster Overlay Markers (v4 -- Proposal 03: Natural Disaster)

- Integration into existing `PriceIliChart.tsx`.
- Vertical markers on ILI history chart for disaster events (earthquakes, hurricanes, pandemics).
- Tooltip on hover: disaster type, severity, magnitude, location.
- Impact shading showing post-event ILI movement window.
- Filter toggle to show/hide disaster markers.

Data source: `GET /api/v1/disaster/alerts`.

### 13. Structural Macro-Environment Panel (v4 -- Proposal 03: Climate)

- Component: `components/panels/MacroEnvironmentPanel.tsx`
- Indices derived from carbon emission coefficients and renewable energy potential.
- Climate sensitivity impact on ILI thresholds.
- Forward-looking climate scenario selector (baseline, carbon tax, green transition).
- Historical correlation between climate indices and liquidity conditions.

Data source: `POST /api/v1/climate/simulate`.

### 14. Stale Data Indicator (v4 -- Proposal 02: LKG Cache)

- Integration into existing `DataFreshnessPanel.tsx`.
- Displays `X-Data-Age: STALE` badge when Last Known Good (LKG) cache is actively serving data.
- Shows timestamp of last fresh data per source.
- Visual warning (amber border) when data is served from cache rather than live source.
- Countdown indicator showing elapsed time since last fresh update.

Data source: `X-Data-Age` response header from API calls.

### 15. Participation Governance Panel (v4 -- Proposal 02)

- Component: `components/panels/ParticipationPanel.tsx`
- Shows current admissibility status for each data source and composite indicator.
- Lists active suppression reasons (stale data, anomaly detected, manual override, etc.).
- Audit trail of recent suppression events with timestamps and triggering conditions.
- Toggle to expand full participation history for any given indicator.

Data source: `GET /api/v1/participation/status`.

### 16. Anomaly Score Indicator (v4 -- Proposal 01: Data Quality)

- Integration into existing KPI cards.
- Displays autoencoder MSE reconstruction score with configurable threshold line.
- Visual badge (amber) when `SUSPECT_ANOMALY` flag is active on any data point.
- Historical anomaly score sparkline showing recent trend.
- Click-through to anomaly detail view with feature-level breakdown.

Data source: ILI history with `anomaly_score` column from `GET /api/v1/kpi/ili/history`.

### 17. Optimizer Status Panel (v4 -- Proposal 04)

- Component: `components/panels/OptimizerStatusPanel.tsx`
- Shows current optimization method (Bayesian or Firefly).
- Displays last calibration date, current fitness score (Sharpe ratio), and convergence status.
- Weight history chart showing how ILI component weights evolved over calibration cycles.
- A/B comparison mode: side-by-side metrics for competing optimizer runs.

Data source: `GET /api/v1/optimization/status`.

### 18. Robustness Heatmap (v5 -- Proposal 05: Backtesting Robustness)

- Component: `components/charts/BacktestHeatmap.tsx`
- Two classes of sensitivity visualization:
    - **Time Sensitivity Heatmap:** Shows whether strategy success is concentrated in a specific historical window. A
      strategy that only profits during COVID crash is not robust.
    - **Parameter Sensitivity Heatmap:** Performance across 2D parameter slices (buy_percentile vs. sell_percentile).
      Identifies narrow "islands" of profitability indicating overfitting.
- Uses D3.js or Perspective for rendering.
- Data source: `GET /api/v1/backtest/robustness-scan` and `GET /api/v1/backtest/{id}/heatmap`

### 19. Performance Decomposition Panel (v5 -- Proposal 05: Return Gap)

- Component: `components/panels/PerformanceDecompositionPanel.tsx`
- Shows breakdown of strategy gains into `HoldingsReturn` and `ReturnGap` (execution alpha).
- Side-by-side bar chart: static holdings vs. active trading contribution.
- Updates with each backtest run and live demo data.
- Data source: `GET /api/v1/kpi/return-gap`

### 20. Signal Explainability Panel (v5 -- Proposal 06: Algorithm Aversion)

- Component: `components/panels/SignalExplainabilityPanel.tsx`
- For every ACTIONABLE signal, shows a "Why?" tooltip/panel:
    - Contributing Z-scores (RRP, Spread, Vol).
    - Current percentile rank relative to last 252 days.
    - Active filters (e.g., "DR Window Breach confirmed").
- `SignalExplainabilityComponent` visualizes the weighted sum formula of ILI in real-time.
- Must load within 200ms.
- Data source: `GET /api/v1/signals/{id}/explain`

### 21. Gamma Profile Chart (v5 -- Proposal 06: GEX Monitor)

- Component: `components/charts/GammaProfileChart.tsx`
- "Gamma Flip Zone" visualization showing price level where market moves from positive to negative gamma.
- Color-coded regions: Long Gamma (green, dampening) vs. Short Gamma (red, amplifying).
- Only visible when options data ingestion is enabled.
- Data source: `GET /api/v1/gex/aggregate`

### 22. Audit Trail Display (v5 -- Proposal 06: Auditability)

- Integration into existing `ConfigEditor.tsx` and `ConfigHistory.tsx`.
- Every automated configuration update displays mandatory `audit_reason` field alongside the change diff.
- Dashboard shows intent clearly: e.g., "Recalibration based on Q2 2026 ILI drift".
- `ConfigHistory.tsx` shows audit_reason as a mandatory column.
- Data source: `GET /api/v1/config/history` (augmented with audit_reason)

### 23. Market Toxicity Heatmap (v5 -- Proposal 07: Trader Toxicity)

- Component: `components/charts/ToxicityHeatmap.tsx`
- Heatmap showing which assets or venues currently exhibit predatory trading behaviors.
- Color scale: green (beneficial) -> yellow (neutral) -> red (harmful).
- Updates near-real-time with ingestion data.
- Data source: `GET /api/v1/analytics/toxicity`

### 24. Trader Type Dominance Panel (v5 -- Proposal 07: Trader-Type Dynamics)

- Component: `components/panels/TraderTypePanel.tsx`
- Stacked bar chart showing estimated dominance per symbol: Algorithmic, Institutional, Professional, Retail.
- Spread compression overlay showing AT vs. non-AT spread difference.
- Data source: `GET /api/v1/analytics/trader-types`

### 25. Behavioural Stress Axis (v5 -- Proposal 07: Behavioural Liquidity Guard)

- Integration into existing `LiquidityHeatmap.tsx`.
- Adds 5th axis "Behavioural Stress" to the Systemic Risk Heatmap JSON.
- Shows BRI components: OFI z-score, spread volatility, sentiment polarity.
- Herding and panic indicators displayed as badges when thresholds exceeded.
- Data source: `GET /api/v1/kpi/systemic-risk-heatmap` (augmented with 5th axis)

### 26. Liquidity Reliability Score (v5 -- Proposal 07: Phantom Liquidity)

- Integration into existing KPI cards.
- Displays "Liquidity Reliability" score (inverse of PLI) on dashboard cards.
- Amber badge when PLI is high (unreliable depth).
- Historical PLI sparkline showing phantom liquidity trend.
- Data source: `GET /api/v1/kpi/phantom-liquidity`

### 27. Signal Informativeness Panel (v5 -- Proposal 05: Price Jump Ratio)

- Component: `components/panels/SignalInformativenessPanel.tsx`
- Visualizes PJR (Price Jump Ratio) for recent signals.
- Helps distinguish informed trading from noise reaction.
- Shows CAR (Cumulative Abnormal Return) decomposition chart.
- Data source: `GET /api/v1/backtest/pjr`

### 28. Performance Duality Comparison (v5 -- Proposal 06: Algorithm Aversion)

- Integration into existing dashboard or as a standalone panel.
- Side-by-side comparison of "Pure ILI" strategy vs. "Subjective Benchmark" (Buy & Hold).
- Detailed "ILI Confidence Score" for every real-time data point.
- Empirically demonstrates the algorithm's objective edge.
- Updates in real-time with live data.
- Data source: `GET /api/v1/demo/performance-duality`

### 29. Sentiment Heatmap (v5 -- Proposal 09: Sentiment Analysis)

- Heatmap showing sentiment polarity across tracked symbols and macro events.
- Dual display: lexicon score (fast) and FinBERT score (accurate) side-by-side.
- News Correlation markers overlaid on ILI timeline showing causal impact of sentiment on liquidity stress.
- Source breakdown: FOMC, Fed speakers, market news, earnings.
- Data source: `GET /api/v1/sentiment/history`

### 30. Execution Comparison Panel (v5 -- Proposal 08: Comparative Execution)

- Side-by-side cumulative P&L chart comparing passive (limit-order) vs. aggressive (market-order) execution.
- "Price Efficiency" metric showing statistical difference between execution modes.
- Slippage vs. fill rate trade-off visualization during ILI spike periods.
- Data source: `GET /api/v1/demo/execution-comparison`

### 31. Pairs Verification Panel (v5 -- Proposal 08: Pairs Trading)

- Price-distance chart for tracked pairs showing divergence from historical formation period.
- Visual indicator when ILI signal and pairs signal diverge (potential dislocation).
- Formation/trading period separator on timeline.
- Data source: `GET /api/v1/pairs/active`

### 32. Leverage Regime Indicator (v5 -- Proposal 08: Leverage Rotation)

- Visual indicator showing current leverage regime: `LEVERAGE_ON` or `LEVERAGE_OFF`.
- S&P 500 200-day MA overlay chart with current position.
- Historical leverage rotation signals marked on ILI timeline.
- Data source: `GET /api/v1/leverage/status`

### 33. Dynamic Stops Panel (v5 -- Proposal 08: Markov Optimal Stops)

- Visualization of current dynamic stop-loss and take-profit levels for open positions.
- State indicator: "signal-active" (positive drift) vs. "signal-exhausted" (absorbing noise).
- Stop level history showing real-time adjustments.
- Data source: `GET /api/v1/stops/active`

### 34. Tail Risk Panel / QQ-Plot (v5 -- Proposal 11: Statistical Integrity)

- Interactive QQ-Plot for the current ILI distribution against Normal reference.
- Flags "Heavy Tail Regime" when plot deviates significantly from 45-degree line.
- Highlights tail deviations indicating Black Swan risk.
- Overlay historical regime classifications.
- Interactive: zoom, hover tooltips, exportable as PNG/SVG.
- Data source: `GET /api/v1/diagnostics/stats`

### 35. Volatility Persistence Panel / ACF Chart (v5 -- Proposal 11: Statistical Integrity)

- ACF chart showing autocorrelation of absolute ILI changes.
- Visually validates `RegimeDetector` volatility clustering findings.
- Contrasts weak linear dependence of raw returns with strong persistence of absolute returns.
- Data source: `GET /api/v1/diagnostics/stats`

### 36. Sample Adequacy Gauge / Convergence Plot (v5 -- Proposal 11: Statistical Integrity)

- Convergence plot (LLN) showing whether current rolling lookback window has reached statistical stability.
- Displays cumulative mean/variance series.
- Indicates whether current sample size is sufficient for reliable inference.
- Data source: `GET /api/v1/diagnostics/stats`

### 37. Market Efficiency Gap Indicator (v5 -- Proposal 11: Efficiency Diagnostic)

- Visualizes drift between fundamental ILI value and algorithmic high-frequency volume imbalance.
- Highlights periods where price discovery is driven by AT noise vs. fundamental macro events.
- AT-driven volume categorization per asset using Hendershott/Menkveld proxy.
- Data source: `GET /api/v1/kpi/efficiency-gap`

### 38. Monetary Policy Sensitivity Panel (v5 -- Proposal 11: Monetary Policy)

- Fed Balance Sheet expansion/contraction trends over time.
- Key Policy Rates: federal funds rate, IORB, SOFR spread dynamics.
- Yield Curve Shape: projected flattening/steepening as macro regime signal.
- Provides essential macro-context for interpreting ILI and signal status.
- Data source: `GET /api/v1/kpi/monetary-policy`

### 39. Model Tournament Comparison Panel (v5 -- Proposal 10: Multi-Model Benchmark)

- Side-by-side performance charts for Standard ILI, XGBoost-Augmented ILI, and LSTM-Augmented ILI.
- Regime-specific breakdown: which model performs best in uptrend/sideways/downtrend.
- SHAP feature attribution display showing top drivers for each model.
- Data source: `GET /api/v1/tournament/evaluate`

### 40. Greeks Sensitivity Dashboard (v5 -- Proposal 12: Greeks Risk Primitive)

- Standardized Greeks display: Repo-Delta, Rate-Delta, Rate-Gamma, Spread-Delta, Volga.
- Sensitivity-adjusted signal threshold visualization.
- T-Bill DV01 and convexity gauges from analytical Greeks service.
- Data source: `GET /api/v1/fixed-income/tbill-greeks`

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
│   │   ├── Sparkline.tsx             # Mini sparkline for KPI cards
│   │   ├── VolatilityClusterChart.tsx    # GARCH regime viz (v4)
│   │   └── PotentialWellChart.tsx         # QED potential well (v4)
│   │   ├── BacktestHeatmap.tsx              # Robustness heatmaps (v5)
│   │   ├── GammaProfileChart.tsx            # GEX gamma flip (v5)
│   │   └── ToxicityHeatmap.tsx              # Market toxicity (v5)
│   ├── kpi/
│   │   ├── IliCard.tsx
│   │   ├── LiquidityStressGauge.tsx
│   │   ├── RepoEquityBetaTable.tsx
│   │   ├── RrpDrainTrend.tsx
│   │   └── VolatilityRegimeIndicator.tsx
│   ├── panels/
│   │   ├── DataFreshnessPanel.tsx
│   │   ├── SystemHealthPanel.tsx
│   │   ├── RegimeComparisonPanel.tsx     # A/B regime compare (v4)
│   │   ├── MacroEnvironmentPanel.tsx     # Climate macro panel (v4)
│   │   ├── ParticipationPanel.tsx        # Governance panel (v4)
│   │   └── OptimizerStatusPanel.tsx      # Weight optimization status (v4)
│   │   ├── PerformanceDecompositionPanel.tsx  # Return Gap (v5)
│   │   ├── SignalExplainabilityPanel.tsx       # Why? panels (v5)
│   │   ├── TraderTypePanel.tsx                 # Trader type (v5)
│   │   └── SignalInformativenessPanel.tsx      # PJR (v5)
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
- [ ] Volatility cluster visualization renders GARCH regime correctly (v4).
- [ ] Regime comparison panel shows all 4 detectors side-by-side (v4).
- [ ] Potential well chart shows price position relative to QED minima (v4).
- [ ] Disaster overlay markers display on ILI history chart with tooltips (v4).
- [ ] Macro environment panel renders climate indices (v4).
- [ ] Stale data indicator shows X-Data-Age badge when LKG cache is active (v4).
- [ ] Participation governance panel shows admissibility status and suppression reasons (v4).
- [ ] Anomaly score indicator shows MSE with threshold line (v4).
- [ ] Optimizer status panel displays calibration state and fitness score (v4).
- [ ] Backtest heatmap renders time sensitivity and parameter sensitivity visualizations (v5).
- [ ] Performance decomposition panel shows HoldingsReturn and ReturnGap breakdown (v5).
- [ ] Signal explainability panel loads within 200ms and shows contributing Z-scores (v5).
- [ ] Gamma profile chart shows gamma flip zone when options data is enabled (v5).
- [ ] Audit trail displays audit_reason alongside config change diffs (v5).
- [ ] No configuration change visible in history without audit_reason (v5).
- [ ] Market toxicity heatmap updates near-real-time with color-coded classification (v5).
- [ ] Trader type dominance panel shows estimated breakdown per symbol (v5).
- [ ] Liquidity heatmap includes 5th axis for behavioural stress (v5).
- [ ] Liquidity reliability score displays inverse PLI with amber badge on high PLI (v5).
- [ ] Signal informativeness panel shows PJR for recent signals (v5).
- [ ] Performance duality comparison updates in real-time (v5).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Virtual Threads single-mode, Proxy Divergence Guard, direct FRED/NY Fed freshness, Dynamic Weighting ILI card. Added ILI status badges, dislocated shading, data freshness panel updates.                                                                                                                                                                                                                                                                   |
| v2      | Replaced Correlation Matrix from D3.js to FINOS Perspective `<perspective-viewer>` widget for high-frequency real-time updates. Added Perspective fallback for Liquidity Heatmap when data exceeds 10K points. Replaced SignalLog with Perspective data grid. Added FDC3 as deferred future extension. Updated technology table with Perspective and FDC3 rows.                                                                                                                        |
| v4      | Added 9 new dashboard components: Volatility Cluster Visualization (GARCH), Regime Comparison Panel (A/B testing), Potential Well Chart (QED), Disaster Overlay Markers (Natural Disaster), Structural Macro-Environment Panel (Climate), Stale Data Indicator (LKG Cache), Participation Governance Panel, Anomaly Score Indicator (Data Quality), Optimizer Status Panel. Updated directory structure with new chart and panel components. Added 9 validation items for v4 features. |
| v5      | `07-analytics-dashboard.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Added 11 new dashboard components from Proposals 05, 06, 07: Robustness Heatmap (time + parameter sensitivity), Performance Decomposition Panel (Return Gap), Signal Explainability Panel ("Why?" tooltips with Z-scores and percentile rank), Gamma Profile Chart (gamma flip zone), Audit Trail Display (audit_reason on config changes), Market Toxicity Heatmap, Trader Type Dominance Panel, Behavioural Stress Axis (5th axis on systemic risk heatmap), Liquidity Reliability Score (inverse PLI), Signal Informativeness Panel (PJR), Performance Duality Comparison (ILI vs. Buy & Hold). Updated directory structure. Added 12 validation items. |
| v5      | `07-analytics-dashboard.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Added 5 new dashboard components from Proposals 08, 09: Sentiment Heatmap (dual lexicon/FinBERT), Execution Comparison Panel (passive vs aggressive P&L), Pairs Verification Panel (divergence detection), Leverage Regime Indicator (200-day MA), Dynamic Stops Panel (Markov stop levels). Updated directory structure. Added 5 validation items. |
| v5.1    | `07-analytics-dashboard.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Added 7 new dashboard components from Proposals 10, 11, 12: Tail Risk QQ-Plot, Volatility Persistence ACF Chart, Sample Adequacy Convergence Plot, Market Efficiency Gap Indicator, Monetary Policy Sensitivity Panel, Model Tournament Comparison Panel, Greeks Sensitivity Dashboard. Updated directory structure. Added 7 validation items. |
| v6      | `07-analytics-dashboard.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Updated Data Freshness Panel: replaced "OpenBB sidecar connectivity" with "Yahoo Finance + Finnhub connectivity" for equity prices. Updated System Health Panel: circuit breaker status changed from "OpenBB, Polygon WS" to "Yahoo Finance, Finnhub WS". No structural changes to dashboard components. |

---

## Appendix: 06-frontend-dashboard.md

> *Merged from `gaps/06-frontend-dashboard.md` / `done/06-frontend-dashboard.md` during plan_v6 consolidation.*

# Frontend (Dashboard) — 2 Missing Items

**Plan ref:** `07-analytics-dashboard.md`

| #   | Item                     | Description                                                                                          |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------- |
| 1   | **D3.js integration**    | Plan specifies D3.js for heatmaps; current implementation uses only Perspective + lightweight-charts |
| 2   | **Playwright e2e tests** | Plan specifies Playwright; only Vitest unit tests exist                                              |

> **Note:** Perspective **is** used (CorrelationMatrix, SignalLog, LiquidityHeatmap). Auth **is** implemented (PKCE flow in `frontend/lib/auth.ts`). The 40 planned dashboard components are all implemented.

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 6. Frontend (Dashboard) — 2 Missing Items
**Plan ref:** `07-analytics-dashboard.md`

| #   | Item                     | Description                                                                                          |
| --- | ------------------------ | ---------------------------------------------------------------------------------------------------- |
| 1   | **D3.js integration**    | Plan specifies D3.js for heatmaps; current implementation uses only Perspective + lightweight-charts |
| 2   | **Playwright e2e tests** | Plan specifies Playwright; only Vitest unit tests exist                                              |

> **Note:** Perspective **is** used (CorrelationMatrix, SignalLog, LiquidityHeatmap). Auth **is** implemented (PKCE flow in `frontend/lib/auth.ts`). The 40 planned dashboard components are all implemented.

---
