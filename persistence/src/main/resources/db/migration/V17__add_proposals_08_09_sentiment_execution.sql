-- Sentiment history, execution type, pairs trading, markov stop calibration

CREATE TABLE sentiment_history
(
    time           TIMESTAMPTZ NOT NULL,
    symbol         TEXT,
    lexicon_score  DOUBLE PRECISION,
    bert_score     DOUBLE PRECISION,
    bert_certainty DOUBLE PRECISION,
    source_type    TEXT        NOT NULL,
    text_hash      TEXT        NOT NULL,
    polarity       DOUBLE PRECISION,
    subjectivity   DOUBLE PRECISION
);
SELECT create_hypertable('sentiment_history', 'time', chunk_time_interval => INTERVAL '1 day');

ALTER TABLE virtual_portfolio_trades
    ADD COLUMN execution_type TEXT NOT NULL DEFAULT 'AGGRESSIVE'
        CHECK (execution_type IN ('PASSIVE', 'AGGRESSIVE'));
ALTER TABLE signal_log
    ADD COLUMN execution_type TEXT
        CHECK (execution_type IN ('PASSIVE', 'AGGRESSIVE'));

CREATE TABLE pairs_trading_pairs
(
    id              BIGSERIAL PRIMARY KEY,
    symbol_a        TEXT        NOT NULL,
    symbol_b        TEXT        NOT NULL,
    formation_start DATE        NOT NULL,
    formation_end   DATE        NOT NULL,
    trading_start   DATE        NOT NULL,
    trading_end     DATE        NOT NULL,
    distance_score  DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE markov_stop_calibrations
(
    id                  BIGSERIAL,
    symbol              TEXT             NOT NULL,
    optimal_stop_loss   DOUBLE PRECISION NOT NULL,
    optimal_take_profit DOUBLE PRECISION NOT NULL,
    signal_drift        DOUBLE PRECISION,
    decay_intensity     DOUBLE PRECISION,
    converged           BOOLEAN          NOT NULL DEFAULT FALSE,
    iterations          INTEGER,
    calibrated_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, calibrated_at)
);
SELECT create_hypertable('markov_stop_calibrations', 'calibrated_at', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_sentiment_symbol_time ON sentiment_history (symbol, time DESC);
CREATE INDEX idx_sentiment_source_type ON sentiment_history (source_type, time DESC);
CREATE INDEX idx_sentiment_bert_certainty ON sentiment_history (bert_certainty) WHERE bert_certainty IS NOT NULL;
CREATE INDEX idx_pairs_trading_symbols ON pairs_trading_pairs (symbol_a, symbol_b);
CREATE INDEX idx_markov_stop_symbol ON markov_stop_calibrations (symbol, calibrated_at DESC);
