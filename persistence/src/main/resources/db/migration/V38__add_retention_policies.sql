-- Add retention policies to hypertables that lack them (P-C4).
-- 2 of 28 hypertables already had retention: tick_data (V4, 90 days), volatility_forecasts (V33, 365 days).
-- This migration enables retention on the remaining 26 hypertables.
--
-- Retention periods are chosen based on data frequency, volume, and analytical value.
-- Each retention period is set longer than the corresponding compression interval (V37) so chunks
-- are compressed first and dropped later. The three single-series tables skipped for compression
-- (no segmentby column) still benefit from retention.

-- ── High-frequency daily data: retain 365 days ──
-- Compression: 30 days. These tables hold operational metrics where year-old data
-- has diminishing analytical value.

SELECT add_retention_policy('rate_snapshots', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('zscore_series', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('correlation_outputs', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('market_events', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('market_gamma_history', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('phantom_liquidity_metrics', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('toxicity_scores', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('behavioural_risk_index', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('sentiment_history', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('greeks_sensitivity', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('risk_premium_residuals', drop_after => INTERVAL '365 days');
SELECT add_retention_policy('intersubjective_audit_log', drop_after => INTERVAL '365 days');

-- ── Core KPI and signals: retain 730 days (2 years) ──
-- Compression: 30 days. These tables contain the flagship ILI history and strategy
-- alpha signals — longer retention supports multi-year backtesting and trend analysis.

SELECT add_retention_policy('ili_history', drop_after => INTERVAL '730 days');
SELECT add_retention_policy('alpha_signals', drop_after => INTERVAL '730 days');

-- ── Option chain snapshots: retain 180 days ──
-- Compression: 30 days. Full option chains (many strikes x expiries per symbol)
-- generate high row volume; shorter retention keeps storage bounded.

SELECT add_retention_policy('option_chain_snapshots', drop_after => INTERVAL '180 days');

-- ── Medium-frequency data: retain 730 days (2 years) ──
-- Compression: 90 days. Backtest results and model calibrations are regenerated
-- periodically but provide useful comparison history.

SELECT add_retention_policy('backtest_results', drop_after => INTERVAL '730 days');
SELECT add_retention_policy('markov_stop_calibrations', drop_after => INTERVAL '730 days');
SELECT add_retention_policy('risk_evt_parameters', drop_after => INTERVAL '730 days');

-- ── Low-frequency analytical data: retain 1825 days (5 years) ──
-- Compression: 365 days. Research outputs (macro shock IRFs, quantile regression
-- coefficients, transfer entropy matrices) are expensive to recompute and provide
-- long-term analytical value.

SELECT add_retention_policy('macro_shock_irfs', drop_after => INTERVAL '1825 days');
SELECT add_retention_policy('quantile_coefficients', drop_after => INTERVAL '1825 days');
SELECT add_retention_policy('synergy_entropy_matrix', drop_after => INTERVAL '1825 days');

-- ── Long-term reference data: retain 3650 days (10 years) ──
-- Compression: 2 years (factor_returns), 50 years (index_snapshots), or none.
-- Ken French factor returns and Shiller CAPE data are external reference datasets
-- where historical depth is inherently valuable. Fed balance sheet data is a
-- macro reference series with a long historical tail.

SELECT add_retention_policy('factor_returns', drop_after => INTERVAL '3650 days');
SELECT add_retention_policy('index_snapshots', drop_after => INTERVAL '3650 days');
SELECT add_retention_policy('fed_balance_sheet', drop_after => INTERVAL '3650 days');

-- ── Single-series analytical tables (no compression, compression skipped in V37) ──
-- These tables hold one row per time point with no categorical segmentby column.
-- Retention still applies and prevents unbounded growth.

SELECT add_retention_policy('universe_stats_history', drop_after => INTERVAL '1825 days');
SELECT add_retention_policy('liquidity_comovement_snapshots', drop_after => INTERVAL '1825 days');
