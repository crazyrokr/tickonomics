-- Prediction-market quotes for the perspective-mismatch signal (ADR-037, Track A).
-- Polymarket yes-outcome share price is a probability in [0, 1]; volume and liquidity are USDC.
-- Idempotency: nullable idempotency_key + partial unique index over (time, idempotency_key),
-- matching the layered once-only model used by tick_data / rate_snapshots (V35).

CREATE TABLE prediction_market_quotes
(
    time               TIMESTAMPTZ   NOT NULL,
    market_id          TEXT          NOT NULL,
    question           TEXT          NOT NULL,
    outcome_yes_price  DOUBLE PRECISION NOT NULL,
    volume             DOUBLE PRECISION,
    liquidity          DOUBLE PRECISION,
    source             TEXT          NOT NULL,
    idempotency_key    UUID
);
SELECT create_hypertable('prediction_market_quotes', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE UNIQUE INDEX IF NOT EXISTS idx_prediction_market_quotes_idempotency
    ON prediction_market_quotes (time, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_prediction_market_quotes_market_time
    ON prediction_market_quotes (market_id, time DESC);

ALTER TABLE prediction_market_quotes SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'market_id',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('prediction_market_quotes', compress_after => INTERVAL '30 days');
SELECT add_retention_policy('prediction_market_quotes', drop_after => INTERVAL '365 days');
