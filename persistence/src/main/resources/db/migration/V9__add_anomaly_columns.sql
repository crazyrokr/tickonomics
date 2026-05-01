-- Anomaly scoring columns on existing hypertables

ALTER TABLE ili_history
    ADD COLUMN anomaly_score DOUBLE PRECISION;
ALTER TABLE ili_history
    ADD COLUMN is_suspect_anomaly BOOLEAN DEFAULT FALSE;
ALTER TABLE rate_snapshots
    ADD COLUMN anomaly_score DOUBLE PRECISION;
ALTER TABLE rate_snapshots
    ADD COLUMN is_suspect_anomaly BOOLEAN DEFAULT FALSE;
