CREATE TABLE model_artifacts (
    id              BIGSERIAL         PRIMARY KEY,
    model_type      TEXT              NOT NULL,
    model_version   TEXT              NOT NULL DEFAULT '1.0',
    parameters      JSONB             NOT NULL,
    state_data      BYTEA,
    training_stats  JSONB,
    trained_at      TIMESTAMPTZ       NOT NULL DEFAULT now(),
    trained_rows    INTEGER,
    data_hash       TEXT,
    git_sha         TEXT,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_ma_type_active ON model_artifacts (model_type, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_ma_type_hash   ON model_artifacts (model_type, data_hash) WHERE is_active = TRUE;
