-- Durable idempotency keys on the core ingestion hypertables (Track 4, section 11).
--
-- Layered exactly-once model: the in-memory IdempotencyGuard is a fast pre-filter; this
-- partial unique index is the durable backstop that survives process restarts and
-- multi-instance writes. Nullable column + partial index keep existing keyless inserts
-- untouched (no backfill, no table rewrite).
--
-- The index includes the `time` partitioning column because TimescaleDB requires every
-- hypertable unique index to cover all partitioning columns (else: "cannot create a unique
-- index without the column 'time'"); since idempotency_key is a deterministic name-based
-- UUID, (time, idempotency_key) is unique per logical event and dedup semantics are
-- unchanged. The repository INSERTs use the matching ON CONFLICT (time, idempotency_key).
--
-- tick_data has compression enabled (V4); TimescaleDB blocks index creation on a
-- compressed hypertable, so compression is dropped for the change and restored afterward.
-- The compression/retention policy survives the toggle. rate_snapshots is uncompressed.

ALTER TABLE tick_data SET (timescaledb.compress = false);

ALTER TABLE tick_data ADD COLUMN IF NOT EXISTS idempotency_key UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tick_data_idempotency
    ON tick_data (time, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE tick_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
    );

ALTER TABLE rate_snapshots ADD COLUMN IF NOT EXISTS idempotency_key UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rate_snapshots_idempotency
    ON rate_snapshots (time, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
