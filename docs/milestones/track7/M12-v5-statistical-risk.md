# Milestone 12: v5 Statistical Integrity & Risk

**Status:** DONE
**Depends on:** M2 (Core Charts), M3 (KPI Cards), M5 (Perspective Grids)
**Estimated scope:** ~8 files

## Objective

Implement v5 statistical integrity diagnostics and risk visualization panels: QQ-plot, ACF chart, convergence plot, market efficiency gap, monetary policy sensitivity, model tournament comparison, and Greeks sensitivity dashboard.

## Components

### 12.1 Tail Risk Panel / QQ-Plot

**File:** `components/charts/TailRiskQQPlot.tsx`

- Interactive QQ-Plot for the current ILI distribution against Normal reference
- Flags "Heavy Tail Regime" when plot deviates significantly from 45-degree line
- Highlights tail deviations indicating Black Swan risk
- Overlay historical regime classifications
- Interactive: zoom, hover tooltips, exportable as PNG/SVG

Data source: `GET /api/v1/diagnostics/stats`

### 12.2 Volatility Persistence Panel / ACF Chart

**File:** `components/charts/VolatilityPersistenceACF.tsx`

- ACF chart showing autocorrelation of absolute ILI changes
- Visually validates `RegimeDetector` volatility clustering findings
- Contrasts weak linear dependence of raw returns with strong persistence of absolute returns

Data source: `GET /api/v1/diagnostics/stats`

### 12.3 Sample Adequacy Gauge / Convergence Plot

**File:** `components/charts/ConvergencePlot.tsx`

- Convergence plot (LLN) showing whether current rolling lookback window has reached statistical stability
- Displays cumulative mean/variance series
- Indicates whether current sample size is sufficient for reliable inference

Data source: `GET /api/v1/diagnostics/stats`

### 12.4 Market Efficiency Gap Indicator

**File:** `components/panels/MarketEfficiencyGapIndicator.tsx`

- Visualizes drift between fundamental ILI value and algorithmic high-frequency volume imbalance
- Highlights periods where price discovery is driven by AT noise vs. fundamental macro events
- AT-driven volume categorization per asset using Hendershott/Menkveld proxy

Data source: `GET /api/v1/kpi/efficiency-gap`

### 12.5 Monetary Policy Sensitivity Panel

**File:** `components/panels/MonetaryPolicyPanel.tsx`

- Fed Balance Sheet expansion/contraction trends over time
- Key Policy Rates: federal funds rate, IORB, SOFR spread dynamics
- Yield Curve Shape: projected flattening/steepening as macro regime signal
- Provides essential macro-context for interpreting ILI and signal status

Data source: `GET /api/v1/kpi/monetary-policy`

### 12.6 Model Tournament Comparison Panel

**File:** `components/panels/ModelTournamentPanel.tsx`

- Side-by-side performance charts for Standard ILI, XGBoost-Augmented ILI, and LSTM-Augmented ILI
- Regime-specific breakdown: which model performs best in uptrend/sideways/downtrend
- SHAP feature attribution display showing top drivers for each model

Data source: `GET /api/v1/tournament/evaluate`

### 12.7 Greeks Sensitivity Dashboard

**File:** `components/panels/GreeksSensitivityDashboard.tsx`

- Standardized Greeks display: Repo-Delta, Rate-Delta, Rate-Gamma, Spread-Delta, Volga
- Sensitivity-adjusted signal threshold visualization
- T-Bill DV01 and convexity gauges from analytical Greeks service

Data source: `GET /api/v1/fixed-income/tbill-greeks`

## Acceptance Criteria

- [ ] QQ-Plot renders ILI distribution against Normal reference with 45-degree line
- [ ] "Heavy Tail Regime" flagged when deviation exceeds threshold
- [ ] QQ-Plot supports zoom, hover tooltips, and PNG/SVG export
- [ ] ACF chart shows autocorrelation of absolute ILI changes
- [ ] Raw returns ACF contrasted with absolute returns ACF
- [ ] Convergence plot shows cumulative mean and variance series
- [ ] Sample adequacy indicator shows whether stability is reached
- [ ] Market efficiency gap shows drift between fundamental ILI and AT volume
- [ ] AT-driven periods highlighted on timeline
- [ ] Monetary policy panel shows Fed Balance Sheet trends
- [ ] Yield curve shape displayed with flattening/steepening projection
- [ ] Model tournament shows all 3 models side-by-side
- [ ] SHAP feature attribution renders for each model
- [ ] Regime-specific breakdown table shows best model per regime
- [ ] Greeks dashboard displays all 5 standardized Greeks
- [ ] T-Bill DV01 and convexity gauges render correctly
- [ ] Sensitivity-adjusted threshold visualization shown
- [ ] Unit tests for QQ-plot deviation detection, ACF computation, convergence logic

## Technical Notes

- QQ-Plot requires a statistical library for quantile computation (e.g., `simple-statistics` or D3.js `d3-array`)
- ACF chart uses the same data source as QQ-Plot — fetch once, compute both client-side
- Convergence plot may show long-running series — implement chart windowing for performance
- SHAP visualization requires a waterfall or force-plot component — consider `d3-shap` or custom SVG
- Greeks dashboard uses gauges — consider a reusable gauge component shared with M3's LiquidityStressGauge
- All diagnostic endpoints share `GET /api/v1/diagnostics/stats` — design the data fetching to support all 3 visualizations from a single request
