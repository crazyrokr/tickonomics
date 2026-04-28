-- Core indexes for time-series and relational tables

CREATE INDEX idx_tick_data_symbol_time ON tick_data (symbol, time DESC);
CREATE INDEX idx_rate_snapshots_type_time ON rate_snapshots (rate_type, time DESC);
CREATE INDEX idx_ili_history_time ON ili_history (time DESC);
CREATE INDEX idx_signal_log_symbol_time ON signal_log (symbol, created_at DESC);
CREATE INDEX idx_signal_log_status ON signal_log (status);
CREATE INDEX idx_correlation_outputs_symbol_metric ON correlation_outputs (symbol, metric, time DESC);
CREATE INDEX idx_ingestion_dlq_replayed ON ingestion_dlq (replayed) WHERE NOT replayed;
CREATE INDEX idx_proxy_divergence_events_detected ON proxy_divergence_events (detected_at DESC);
CREATE INDEX idx_ili_history_data_status ON ili_history (data_status) WHERE data_status != 'VALID';
