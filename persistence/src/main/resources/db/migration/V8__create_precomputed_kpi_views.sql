-- Pre-computed continuous aggregates for rolling correlation, beta, and z-score

CREATE
MATERIALIZED VIEW kpi_rolling_correlation
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    symbol,
    metric,
    AVG(correlation) AS avg_correlation,
    MIN(p_value)    AS min_p_value,
    AVG(sample_size) AS avg_sample_size,
    COUNT(*)         AS observations
FROM correlation_outputs
WHERE metric = 'PEARSON_CORRELATION'
GROUP BY day, symbol, metric;

SELECT add_continuous_aggregate_policy('kpi_rolling_correlation',
                                       start_offset => INTERVAL '30 days',
                                       end_offset => INTERVAL '1 day',
                                       schedule_interval => INTERVAL '1 day');

CREATE
MATERIALIZED VIEW kpi_rolling_beta
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    symbol,
    AVG(correlation) AS avg_beta,
    AVG(p_value)    AS avg_p_value,
    COUNT(*)         AS observations
FROM correlation_outputs
WHERE metric = 'OLS_BETA'
GROUP BY day, symbol;

SELECT add_continuous_aggregate_policy('kpi_rolling_beta',
                                       start_offset => INTERVAL '90 days',
                                       end_offset => INTERVAL '1 day',
                                       schedule_interval => INTERVAL '1 day');

CREATE
MATERIALIZED VIEW kpi_zscore_daily
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    component,
    AVG(z_score)  AS avg_z_score,
    MIN(z_score)  AS min_z_score,
    MAX(z_score)  AS max_z_score,
    STDDEV(z_score) AS stddev_z_score,
    AVG(raw_value) AS avg_raw_value
FROM zscore_series
GROUP BY day, component;

SELECT add_continuous_aggregate_policy('kpi_zscore_daily',
                                       start_offset => INTERVAL '30 days',
                                       end_offset => INTERVAL '1 day',
                                       schedule_interval => INTERVAL '1 day');
