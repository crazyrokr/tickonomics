-- OSINT news events for the perspective-mismatch signal (ADR-037, Track A).
-- GDELT 2.0 shape: avg_tone is the news-tone signal (typically [-15, +15]); themes and actors are
-- semicolon-delimited GDELT codes. Self-contained store, distinct from the non-persisting
-- sentiment-forwarding NewsArticle path (NewsScheduler).
-- Idempotency: nullable idempotency_key + partial unique index over (time, idempotency_key) (V35 model).

CREATE TABLE news_events
(
    time             TIMESTAMPTZ   NOT NULL,
    event_id         TEXT          NOT NULL,
    source           TEXT          NOT NULL,
    headline         TEXT          NOT NULL,
    avg_tone         DOUBLE PRECISION,
    themes           TEXT,
    actors           TEXT,
    url              TEXT,
    idempotency_key  UUID
);
SELECT create_hypertable('news_events', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE UNIQUE INDEX IF NOT EXISTS idx_news_events_idempotency
    ON news_events (time, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_news_events_time
    ON news_events (time DESC);

-- Substring search over headlines for event/topic retrieval (matches the V40 trigram pattern).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_news_events_headline_trgm
    ON news_events USING GIN (headline gin_trgm_ops);

ALTER TABLE news_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'source',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('news_events', compress_after => INTERVAL '30 days');
SELECT add_retention_policy('news_events', drop_after => INTERVAL '365 days');
