# Milestone 8: v4 Governance & Quality

**Status:** DONE
**Depends on:** M3 (KPI Cards), M4 (System Monitoring)
**Estimated scope:** ~5 files

## Objective

Implement governance, data quality, and optimization panels from v4 proposals: macro-environment climate analysis, participation governance, anomaly detection scoring, and optimizer status tracking.

## Components

### 8.1 Macro Environment Panel

**File:** `components/panels/MacroEnvironmentPanel.tsx`

- Indices derived from carbon emission coefficients and renewable energy potential
- Climate sensitivity impact on ILI thresholds
- Forward-looking climate scenario selector: baseline, carbon tax, green transition
- Historical correlation between climate indices and liquidity conditions

Data source: `POST /api/v1/climate/simulate`

### 8.2 Participation Governance Panel

**File:** `components/panels/ParticipationPanel.tsx`

- Current admissibility status for each data source and composite indicator
- Active suppression reasons: stale data, anomaly detected, manual override, etc.
- Audit trail of recent suppression events with timestamps and triggering conditions
- Toggle to expand full participation history for any given indicator

Data source: `GET /api/v1/participation/status`

### 8.3 Anomaly Score Indicator

**Integration into:** KPI cards (M3)

- Autoencoder MSE reconstruction score with configurable threshold line
- Visual badge (amber) when `SUSPECT_ANOMALY` flag is active on any data point
- Historical anomaly score sparkline showing recent trend
- Click-through to anomaly detail view with feature-level breakdown

Data source: ILI history with `anomaly_score` column from `GET /api/v1/kpi/ili/history`

### 8.4 Optimizer Status Panel

**File:** `components/panels/OptimizerStatusPanel.tsx`

- Current optimization method: Bayesian or Firefly
- Last calibration date, current fitness score (Sharpe ratio), convergence status
- Weight history chart showing how ILI component weights evolved over calibration cycles
- A/B comparison mode: side-by-side metrics for competing optimizer runs

Data source: `GET /api/v1/optimization/status`

## Acceptance Criteria

- [ ] Macro environment panel renders climate indices with scenario selector
- [ ] Climate scenario selector switches between baseline/carbon tax/green transition
- [ ] Participation governance panel shows admissibility status per data source
- [ ] Suppression reasons listed with timestamps
- [ ] Participation history expandable for any indicator
- [ ] Anomaly score indicator displays MSE with threshold line in KPI cards
- [ ] `SUSPECT_ANOMALY` badge appears in amber when anomaly detected
- [ ] Anomaly sparkline shows recent score trend
- [ ] Optimizer status panel displays calibration state and fitness score
- [ ] Weight history chart shows weight evolution over calibration cycles
- [ ] A/B comparison mode works for competing optimizer runs
- [ ] Unit tests for anomaly threshold logic, participation status parsing, optimizer state

## Technical Notes

- The climate simulation endpoint (`POST /api/v1/climate/simulate`) requires a request body with scenario parameters
- Participation governance data may have many suppression events — implement virtualized scrolling
- Anomaly score integration extends the existing KPI cards from M3 — add the indicator as an overlay, not a separate card
- Optimizer weight history may span many calibration cycles — implement chart zoom for long histories
