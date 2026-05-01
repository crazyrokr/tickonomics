-- Compression and retention policies

ALTER TABLE tick_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
    );
SELECT add_compression_policy('tick_data', compress_after = > INTERVAL '7 days');

SELECT add_retention_policy('tick_data', drop_after = > INTERVAL '90 days');
SELECT add_retention_policy('signal_log', drop_after = > INTERVAL '730 days');
SELECT add_retention_policy('ingestion_dlq', drop_after = > INTERVAL '90 days');
