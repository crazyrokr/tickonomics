-- Continuous aggregates for OHLCV candles and daily KPI summary
-- materialized_only = false provides real-time aggregates (Finding 4)

CREATE
MATERIALIZED VIEW ohlcv_1min
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 minute', time) AS minute,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY minute, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1min',
                                       start_offset = > INTERVAL '3 hours',
                                       end_offset = > INTERVAL '1 minute',
                                       schedule_interval = > INTERVAL '1 minute');

CREATE
MATERIALIZED VIEW ohlcv_1h
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 hour', time) AS hour,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY hour, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1h',
                                       start_offset = > INTERVAL '7 days',
                                       end_offset = > INTERVAL '1 hour',
                                       schedule_interval = > INTERVAL '1 hour');

CREATE
MATERIALIZED VIEW ohlcv_1d
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY day, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1d',
                                       start_offset = > NULL,
                                       end_offset = > INTERVAL '1 day',
                                       schedule_interval = > INTERVAL '1 day');

CREATE
MATERIALIZED VIEW daily_kpi_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    AVG(ili_value) AS avg_ili,
    MIN(ili_value) AS min_ili,
    MAX(ili_value) AS max_ili,
    STDDEV(ili_value) AS stddev_ili
FROM ili_history
WHERE data_status = 'VALID'
GROUP BY day;

SELECT add_continuous_aggregate_policy('daily_kpi_summary',
                                       start_offset = > NULL,
                                       end_offset = > INTERVAL '1 day',
                                       schedule_interval = > INTERVAL '1 day');
