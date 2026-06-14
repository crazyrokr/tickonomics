CREATE TABLE IF NOT EXISTS data_import_tracker (
    id              SERIAL          PRIMARY KEY,
    source          TEXT            NOT NULL,
    symbol          TEXT            NOT NULL,
    last_import_time TIMESTAMPTZ    NOT NULL,
    full_load_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (source, symbol)
);

CREATE TABLE IF NOT EXISTS index_snapshots (
    time                TIMESTAMPTZ     NOT NULL,
    index_type          TEXT            NOT NULL,
    price               DOUBLE PRECISION NOT NULL,
    dividend            DOUBLE PRECISION,
    earnings            DOUBLE PRECISION,
    cpi                 DOUBLE PRECISION,
    long_interest_rate  DOUBLE PRECISION,
    real_price          DOUBLE PRECISION,
    real_dividend       DOUBLE PRECISION,
    real_earnings       DOUBLE PRECISION,
    cape                DOUBLE PRECISION,
    source              TEXT            NOT NULL DEFAULT 'DATAHUB_SHILLER',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, index_type)
);

SELECT create_hypertable('index_snapshots', 'time', chunk_time_interval => INTERVAL '10 years', migrate_data => true);

ALTER TABLE index_snapshots SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'index_type',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('index_snapshots', compress_after => INTERVAL '50 years');
