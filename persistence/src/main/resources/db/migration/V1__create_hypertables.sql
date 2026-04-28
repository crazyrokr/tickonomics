-- Core hypertables for time-series data

CREATE TABLE tick_data (
    time        TIMESTAMPTZ NOT NULL,
    symbol      TEXT NOT NULL,
    price       DOUBLE PRECISION,
    volume      BIGINT,
    conditions  INTEGER[]
);
SELECT create_hypertable('tick_data', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE TABLE rate_snapshots (
    time        TIMESTAMPTZ NOT NULL,
    rate_type   TEXT NOT NULL,
    value       DOUBLE PRECISION,
    source      TEXT
);
SELECT create_hypertable('rate_snapshots', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE TABLE ili_history (
    time                    TIMESTAMPTZ NOT NULL,
    ili_value               DOUBLE PRECISION,
    z_rrp                   DOUBLE PRECISION,
    z_spread                DOUBLE PRECISION,
    z_vol                   DOUBLE PRECISION,
    data_status             TEXT NOT NULL,
    active_weights          JSONB,
    proxy_divergence_status TEXT,
    proxy_divergence_score  DOUBLE PRECISION
);
SELECT create_hypertable('ili_history', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE TABLE zscore_series (
    time            TIMESTAMPTZ NOT NULL,
    component       TEXT NOT NULL,
    raw_value       DOUBLE PRECISION,
    z_score         DOUBLE PRECISION,
    lookback_days   INTEGER NOT NULL
);
SELECT create_hypertable('zscore_series', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE TABLE correlation_outputs (
    time                TIMESTAMPTZ NOT NULL,
    symbol              TEXT NOT NULL,
    metric              TEXT NOT NULL,
    correlation         DOUBLE PRECISION,
    p_value             DOUBLE PRECISION,
    sample_size         INTEGER,
    lag_order           INTEGER,
    direction           TEXT
);
SELECT create_hypertable('correlation_outputs', 'time', chunk_time_interval => INTERVAL '1 day');
