-- Add compression to hypertables that lack it (P-C3).
-- 3 of 28 hypertables already had compression: tick_data (V4), factor_returns (V29), index_snapshots (V31).
-- This migration enables compression on 22 of the remaining 25.
-- Three single-global-series tables (fed_balance_sheet, universe_stats_history,
-- liquidity_comovement_snapshots) are skipped — they have no categorical column for
-- segmentby, and TIMESCALEDB 2.x requires at least one segment column for ALTER TABLE SET.

-- ── High-frequency daily data: compress after 30 days ──

ALTER TABLE rate_snapshots SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'rate_type',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('rate_snapshots', compress_after => INTERVAL '30 days');

ALTER TABLE ili_history SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'data_status',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('ili_history', compress_after => INTERVAL '30 days');

ALTER TABLE zscore_series SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'component',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('zscore_series', compress_after => INTERVAL '30 days');

ALTER TABLE correlation_outputs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('correlation_outputs', compress_after => INTERVAL '30 days');

ALTER TABLE market_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'event_type',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('market_events', compress_after => INTERVAL '30 days');

ALTER TABLE market_gamma_history SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('market_gamma_history', compress_after => INTERVAL '30 days');

ALTER TABLE phantom_liquidity_metrics SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('phantom_liquidity_metrics', compress_after => INTERVAL '30 days');

ALTER TABLE toxicity_scores SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'computed_at DESC, id'
);
SELECT add_compression_policy('toxicity_scores', compress_after => INTERVAL '30 days');

ALTER TABLE behavioural_risk_index SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('behavioural_risk_index', compress_after => INTERVAL '30 days');

ALTER TABLE sentiment_history SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'source_type',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('sentiment_history', compress_after => INTERVAL '30 days');

ALTER TABLE greeks_sensitivity SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('greeks_sensitivity', compress_after => INTERVAL '30 days');

ALTER TABLE risk_premium_residuals SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'instrument',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('risk_premium_residuals', compress_after => INTERVAL '30 days');

ALTER TABLE alpha_signals SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol, strategy_id',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('alpha_signals', compress_after => INTERVAL '30 days');

ALTER TABLE intersubjective_audit_log SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'coding_rule',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('intersubjective_audit_log', compress_after => INTERVAL '30 days');

ALTER TABLE option_chain_snapshots SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC, strike, expiry, option_type'
);
SELECT add_compression_policy('option_chain_snapshots', compress_after => INTERVAL '30 days');

ALTER TABLE volatility_forecasts SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('volatility_forecasts', compress_after => INTERVAL '30 days');

-- ── Medium-frequency data: compress after 90 days ──

ALTER TABLE backtest_results SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'git_sha',
    timescaledb.compress_orderby = 'run_at DESC, id'
);
SELECT add_compression_policy('backtest_results', compress_after => INTERVAL '90 days');

ALTER TABLE markov_stop_calibrations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'calibrated_at DESC, id'
);
SELECT add_compression_policy('markov_stop_calibrations', compress_after => INTERVAL '90 days');

ALTER TABLE risk_evt_parameters SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('risk_evt_parameters', compress_after => INTERVAL '90 days');

-- ── Low-frequency analytical data: compress after 365 days ──

ALTER TABLE macro_shock_irfs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'shock_source',
    timescaledb.compress_orderby = 'time DESC, target_kpi'
);
SELECT add_compression_policy('macro_shock_irfs', compress_after => INTERVAL '365 days');

ALTER TABLE quantile_coefficients SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'kpi_name',
    timescaledb.compress_orderby = 'time DESC, quantile'
);
SELECT add_compression_policy('quantile_coefficients', compress_after => INTERVAL '365 days');

ALTER TABLE synergy_entropy_matrix SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'source_a',
    timescaledb.compress_orderby = 'time DESC, source_b'
);
SELECT add_compression_policy('synergy_entropy_matrix', compress_after => INTERVAL '365 days');

-- ── Skipped (single global series, no segment column) ──
-- fed_balance_sheet, universe_stats_history, liquidity_comovement_snapshots
-- These tables hold one row per time point with no categorical column suitable
-- for segmentby. Compression would provide minimal benefit and the ALTER TABLE
-- syntax requires at least one segment column in TimescaleDB < 2.17.
