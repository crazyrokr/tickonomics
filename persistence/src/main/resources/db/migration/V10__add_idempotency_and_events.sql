-- Idempotency keys, market events hypertable, disaster alerts, regime detection results

ALTER TABLE ingestion_dlq
    ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX idx_ingestion_dlq_idempotency ON ingestion_dlq (idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE market_events
(
    time          TIMESTAMPTZ NOT NULL,
    event_type    TEXT        NOT NULL,
    source_symbol TEXT,
    magnitude     DOUBLE PRECISION,
    metadata      JSONB
);
SELECT create_hypertable('market_events', 'time', chunk_time_interval = > INTERVAL '1 day');

CREATE TABLE disaster_alerts
(
    id          BIGSERIAL PRIMARY KEY,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source      TEXT        NOT NULL,
    alert_type  TEXT        NOT NULL,
    severity    TEXT        NOT NULL,
    magnitude   DOUBLE PRECISION,
    location    TEXT,
    raw_payload JSONB
);

CREATE TABLE regime_detection_results
(
    id          BIGSERIAL PRIMARY KEY,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    method      TEXT        NOT NULL,
    regime      TEXT        NOT NULL,
    confidence  DOUBLE PRECISION,
    metadata    JSONB
);
