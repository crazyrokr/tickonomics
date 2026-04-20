# Milestone 7: v4 Regime Analytics

**Status:** DONE
**Depends on:** M2 (Core Charts), M3 (KPI Cards), M4 (System Monitoring)
**Estimated scope:** ~6 files

## Objective

Implement regime detection visualizations from v4 proposals: GARCH volatility clusters, multi-model regime comparison, QED potential well, natural disaster overlay markers, and stale data indicators.

## Components

### 7.1 Volatility Cluster Chart

**File:** `components/charts/VolatilityClusterChart.tsx`

- GARCH-implied risk regime over time
- Color-coded clusters: `LOW_VOL` (green), `NORMAL` (blue), `HIGH_VOL` (red)
- Regime transitions highlighted with vertical markers
- Legend showing current regime and time spent in each cluster

Data source: `GET /api/v1/regime/garch`

### 7.2 Regime Comparison Panel

**File:** `components/panels/RegimeComparisonPanel.tsx`

- Side-by-side comparison of GARCH, CNN-LSTM, QED, and RAHF regime classifications
- Agreement/disagreement indicator with consensus label
- Periods where models diverge highlighted as "potential regime transition zones"
- Historical accuracy overlay showing which model performed best during past transitions

Data source: `GET /api/v1/regime/compare`

### 7.3 Potential Well Chart

**File:** `components/charts/PotentialWellChart.tsx`

- Current price position relative to QED (quartic potential) minima
- Metastable vs unstable potential regions visually distinct
- Particle-in-well analogy: price as ball rolling on potential surface
- Well depth indicates stability; shallow wells flagged as transition risk zones

Data source: `GET /api/v1/regime/qed`

### 7.4 Disaster Overlay Markers

**Integration into:** `components/charts/PriceIliChart.tsx`

- Vertical markers on ILI history chart for disaster events (earthquakes, hurricanes, pandemics)
- Tooltip on hover: disaster type, severity, magnitude, location
- Impact shading showing post-event ILI movement window
- Filter toggle to show/hide disaster markers

Data source: `GET /api/v1/disaster/alerts`

### 7.5 Stale Data Indicator

**Integration into:** `components/panels/DataFreshnessPanel.tsx`

- Displays `X-Data-Age: STALE` badge when Last Known Good (LKG) cache is actively serving data
- Shows timestamp of last fresh data per source
- Visual warning (amber border) when data is served from cache rather than live source
- Countdown indicator showing elapsed time since last fresh update

Data source: `X-Data-Age` response header from API calls

## Acceptance Criteria

- [ ] Volatility cluster chart renders GARCH regime with correct color coding
- [ ] Regime transitions shown with vertical markers
- [ ] Regime comparison panel shows all 4 detectors side-by-side
- [ ] Consensus indicator reflects model agreement/disagreement
- [ ] Potential well chart shows price position relative to QED minima
- [ ] Shallow wells flagged as transition risk zones
- [ ] Disaster overlay markers display on ILI history chart with tooltips
- [ ] Disaster filter toggle shows/hides markers
- [ ] Stale data indicator shows `X-Data-Age: STALE` badge when LKG cache is active
- [ ] Amber border warning visible when data is served from cache
- [ ] All integrated components (PriceIliChart, DataFreshnessPanel) updated to support v4 features
- [ ] Unit tests for regime coloring logic, consensus computation, and stale detection

## Technical Notes

- Disaster overlay markers require extending the existing `PriceIliChart.tsx` from M2
- The potential well chart is mathematically intensive — consider pre-computing on the backend
- Stale data indicator reads the `X-Data-Age` HTTP header, which must be exposed by the API client
- Regime comparison consensus is computed client-side from the 4 model outputs
