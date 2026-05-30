-- candlestick_agg: aggregate function for OHLCV candle construction
-- Compatible with TimescaleDB continuous aggregates (partializable via COMBINEFUNC)

CREATE TYPE candlestick AS (
    open       DOUBLE PRECISION,
    high       DOUBLE PRECISION,
    low        DOUBLE PRECISION,
    close      DOUBLE PRECISION,
    volume     BIGINT,
    open_time  TIMESTAMPTZ,
    close_time TIMESTAMPTZ
);

CREATE TYPE candlestick_agg_state AS (
    open_time    TIMESTAMPTZ,
    open_price   DOUBLE PRECISION,
    close_time   TIMESTAMPTZ,
    close_price  DOUBLE PRECISION,
    high_price   DOUBLE PRECISION,
    low_price    DOUBLE PRECISION,
    total_volume BIGINT
);

CREATE OR REPLACE FUNCTION candlestick_agg_sfunc(
    state candlestick_agg_state,
    ts TIMESTAMPTZ,
    price DOUBLE PRECISION,
    vol BIGINT
) RETURNS candlestick_agg_state AS $$
BEGIN
    IF price IS NULL THEN
        RETURN state;
    END IF;
    IF state IS NULL THEN
        RETURN (ts, price, ts, price, price, price, COALESCE(vol, 0))::candlestick_agg_state;
    END IF;
    RETURN (
        CASE WHEN ts < state.open_time THEN ts ELSE state.open_time END,
        CASE WHEN ts < state.open_time THEN price ELSE state.open_price END,
        CASE WHEN ts > state.close_time THEN ts ELSE state.close_time END,
        CASE WHEN ts > state.close_time THEN price ELSE state.close_price END,
        GREATEST(state.high_price, price),
        LEAST(state.low_price, price),
        state.total_volume + COALESCE(vol, 0)
    )::candlestick_agg_state;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION candlestick_agg_combinefunc(
    state1 candlestick_agg_state,
    state2 candlestick_agg_state
) RETURNS candlestick_agg_state AS $$
BEGIN
    IF state1 IS NULL THEN RETURN state2; END IF;
    IF state2 IS NULL THEN RETURN state1; END IF;
    RETURN (
        CASE WHEN state1.open_time <= state2.open_time
             THEN state1.open_time ELSE state2.open_time END,
        CASE WHEN state1.open_time <= state2.open_time
             THEN state1.open_price ELSE state2.open_price END,
        CASE WHEN state1.close_time >= state2.close_time
             THEN state1.close_time ELSE state2.close_time END,
        CASE WHEN state1.close_time >= state2.close_time
             THEN state1.close_price ELSE state2.close_price END,
        GREATEST(state1.high_price, state2.high_price),
        LEAST(state1.low_price, state2.low_price),
        state1.total_volume + state2.total_volume
    )::candlestick_agg_state;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION candlestick_agg_ffunc(
    state candlestick_agg_state
) RETURNS candlestick AS $$
BEGIN
    IF state IS NULL THEN RETURN NULL; END IF;
    RETURN (
        state.open_price,
        state.high_price,
        state.low_price,
        state.close_price,
        state.total_volume,
        state.open_time,
        state.close_time
    )::candlestick;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE AGGREGATE candlestick_agg(TIMESTAMPTZ, DOUBLE PRECISION, BIGINT) (
    SFUNC = candlestick_agg_sfunc,
    STYPE = candlestick_agg_state,
    FINALFUNC = candlestick_agg_ffunc,
    COMBINEFUNC = candlestick_agg_combinefunc
);

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
                                       start_offset => INTERVAL '3 hours',
                                       end_offset => INTERVAL '1 minute',
                                       schedule_interval => INTERVAL '1 minute');

CREATE
MATERIALIZED VIEW ohlcv_1h
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 hour', time) AS hour,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY hour, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1h',
                                       start_offset => INTERVAL '7 days',
                                       end_offset => INTERVAL '1 hour',
                                       schedule_interval => INTERVAL '1 hour');

CREATE
MATERIALIZED VIEW ohlcv_1d
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT time_bucket('1 day', time) AS day,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY day, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1d',
                                       start_offset => NULL,
                                       end_offset => INTERVAL '1 day',
                                       schedule_interval => INTERVAL '1 day');

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
                                       start_offset => NULL,
                                       end_offset => INTERVAL '1 day',
                                       schedule_interval => INTERVAL '1 day');
