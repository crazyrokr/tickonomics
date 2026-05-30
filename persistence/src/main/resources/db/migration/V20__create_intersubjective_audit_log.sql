CREATE TABLE intersubjective_audit_log
(
    time          TIMESTAMPTZ NOT NULL,
    data_point_id UUID        NOT NULL,
    coding_rule   TEXT        NOT NULL,
    rule_version  TEXT        NOT NULL,
    input_hash    TEXT        NOT NULL,
    output_value  DOUBLE PRECISION,
    ir_score      DOUBLE PRECISION DEFAULT 1.0,
    metadata      JSONB
);
SELECT create_hypertable('intersubjective_audit_log', 'time', chunk_time_interval => INTERVAL '1 day');
CREATE INDEX idx_audit_log_data_point ON intersubjective_audit_log (data_point_id);
CREATE INDEX idx_audit_log_time ON intersubjective_audit_log (time DESC);
