-- Indexes for v4 tables and query patterns

CREATE INDEX idx_market_events_type_time ON market_events (event_type, time DESC);
CREATE INDEX idx_disaster_alerts_detected ON disaster_alerts (detected_at DESC);
CREATE INDEX idx_disaster_alerts_severity ON disaster_alerts (severity) WHERE severity IN ('WARNING', 'CRITICAL');
CREATE INDEX idx_regime_results_method_time ON regime_detection_results (method, detected_at DESC);
CREATE INDEX idx_optimization_runs_status ON optimization_runs (status) WHERE status = 'RUNNING';
CREATE INDEX idx_active_weights_applied ON active_weights (applied_at DESC);
CREATE INDEX idx_ili_history_anomaly ON ili_history (is_suspect_anomaly) WHERE is_suspect_anomaly = TRUE;
