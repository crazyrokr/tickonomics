CREATE TABLE volatility_forecasts (
    time            TIMESTAMPTZ       NOT NULL,
    symbol          TEXT              NOT NULL,
    model           TEXT              NOT NULL DEFAULT 'garch',
    horizon_days    INTEGER           NOT NULL,
    forecast_vol    DOUBLE PRECISION  NOT NULL,
    realized_vol    DOUBLE PRECISION,
    mae_vs_baseline DOUBLE PRECISION,
    n_observations  INTEGER,
    parameters      JSONB,
    git_sha         TEXT,
    created_at      TIMESTAMPTZ       NOT NULL DEFAULT now()
);

SELECT create_hypertable('volatility_forecasts', 'time', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_vf_symbol_time ON volatility_forecasts (symbol, time DESC);
CREATE INDEX idx_vf_model_time  ON volatility_forecasts (model, time DESC);

SELECT add_retention_policy('volatility_forecasts', INTERVAL '365 days');
