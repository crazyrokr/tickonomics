-- Continuous aggregates for proposals 05, 07

CREATE
MATERIALIZED VIEW ili_robustness_heatmap
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', run_at) AS day,
    strategy_config->>'buy_percentile' AS buy_pct,
    strategy_config->>'sell_percentile' AS sell_pct,
    AVG(sharpe_ratio) AS avg_sharpe,
    AVG(win_rate) AS avg_win_rate,
    COUNT(*) AS run_count
FROM backtest_results
WHERE parameter_slice_metadata IS NOT NULL
GROUP BY day, buy_pct, sell_pct;

SELECT add_continuous_aggregate_policy('ili_robustness_heatmap',
                                       start_offset = > NULL,
                                       end_offset = > INTERVAL '1 day',
                                       schedule_interval = > INTERVAL '1 day');

CREATE
MATERIALIZED VIEW daily_comovement_factor
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', computed_at) AS day,
    AVG(comovement_factor) AS avg_comovement,
    MAX(comovement_factor) AS max_comovement,
    COUNT(*) AS observations
FROM liquidity_comovement_snapshots
GROUP BY day;

SELECT add_continuous_aggregate_policy('daily_comovement_factor',
                                       start_offset = > INTERVAL '30 days',
                                       end_offset = > INTERVAL '1 day',
                                       schedule_interval = > INTERVAL '1 day');

CREATE
MATERIALIZED VIEW daily_toxicity_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', computed_at) AS day,
    symbol,
    venue,
    AVG(order_to_trade_ratio) AS avg_otr,
    AVG(round_trip_pct) AS avg_round_trip,
    COUNT(*) AS observations,
    COUNT(*) FILTER (WHERE toxicity_class = 'HARMFUL') AS harmful_count
FROM toxicity_scores
GROUP BY day, symbol, venue;

SELECT add_continuous_aggregate_policy('daily_toxicity_summary',
                                       start_offset = > INTERVAL '30 days',
                                       end_offset = > INTERVAL '1 day',
                                       schedule_interval = > INTERVAL '1 day');
