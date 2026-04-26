CREATE TABLE universe_stats_history (
    time            TIMESTAMPTZ PRIMARY KEY,
    universe_mean   DOUBLE PRECISION NOT NULL,
    universe_std    DOUBLE PRECISION NOT NULL,
    symbol_count    INT NOT NULL
);
SELECT create_hypertable('universe_stats_history', 'time', chunk_time_interval => INTERVAL '1 day');
