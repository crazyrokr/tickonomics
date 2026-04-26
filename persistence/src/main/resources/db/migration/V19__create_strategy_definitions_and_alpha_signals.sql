CREATE TABLE strategy_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    category        TEXT NOT NULL,
    section_ref     TEXT,
    formula_refs    TEXT[],
    priority        INT NOT NULL DEFAULT 2,
    complexity_tier TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    expected_hit_rate DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alpha_signals (
    time            TIMESTAMPTZ NOT NULL,
    strategy_id     UUID NOT NULL REFERENCES strategy_definitions(id),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,
    strength        DOUBLE PRECISION CHECK (strength BETWEEN 0 AND 1),
    confidence      DOUBLE PRECISION CHECK (confidence BETWEEN 0 AND 1),
    expected_move   DOUBLE PRECISION,
    metadata        JSONB,
    PRIMARY KEY (time, strategy_id, symbol)
);
SELECT create_hypertable('alpha_signals', 'time', chunk_time_interval => INTERVAL '7 days');
CREATE INDEX idx_alpha_signals_strategy ON alpha_signals (strategy_id, time DESC);
