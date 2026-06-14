CREATE TABLE IF NOT EXISTS factor_returns (
    time            TIMESTAMPTZ     NOT NULL,
    factor_set      TEXT            NOT NULL,
    frequency       TEXT            NOT NULL,
    rm_rf           DOUBLE PRECISION,
    smb             DOUBLE PRECISION,
    hml             DOUBLE PRECISION,
    rmw             DOUBLE PRECISION,
    cma             DOUBLE PRECISION,
    rf              DOUBLE PRECISION,
    mom             DOUBLE PRECISION,
    st_rev          DOUBLE PRECISION,
    lt_rev          DOUBLE PRECISION,
    region          TEXT            NOT NULL DEFAULT 'US',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, factor_set, frequency, region)
);

SELECT create_hypertable('factor_returns', 'time', chunk_time_interval => INTERVAL '1 year');

ALTER TABLE factor_returns SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'factor_set, frequency, region',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('factor_returns', compress_after => INTERVAL '2 years');
