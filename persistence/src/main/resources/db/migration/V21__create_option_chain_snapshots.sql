CREATE TABLE option_chain_snapshots
(
    time             TIMESTAMPTZ      NOT NULL,
    symbol           TEXT             NOT NULL,
    strike           DOUBLE PRECISION NOT NULL,
    expiry           DATE             NOT NULL,
    option_type      TEXT             NOT NULL,
    bid              DOUBLE PRECISION,
    ask              DOUBLE PRECISION,
    last_price       DOUBLE PRECISION,
    delta            DOUBLE PRECISION,
    gamma            DOUBLE PRECISION,
    theta            DOUBLE PRECISION,
    vega             DOUBLE PRECISION,
    rho              DOUBLE PRECISION,
    implied_vol      DOUBLE PRECISION,
    ttm_years        DOUBLE PRECISION,
    underlying_price DOUBLE PRECISION,
    PRIMARY KEY (time, symbol, strike, expiry, option_type)
);
SELECT create_hypertable('option_chain_snapshots', 'time', chunk_time_interval = > INTERVAL '1 day');
CREATE INDEX idx_option_chain_symbol_expiry ON option_chain_snapshots (symbol, expiry, strike);
