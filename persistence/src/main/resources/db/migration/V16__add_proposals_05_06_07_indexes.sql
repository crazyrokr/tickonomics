-- Indexes for proposal 05, 06, 07 tables

CREATE INDEX idx_market_gamma_symbol_time ON market_gamma_history (symbol, time DESC);
CREATE INDEX idx_phantom_liquidity_symbol_time ON phantom_liquidity_metrics (symbol, time DESC);
CREATE INDEX idx_behavioural_risk_symbol_time ON behavioural_risk_index (symbol, time DESC);
CREATE INDEX idx_strategic_run_symbol_time ON strategic_run_events (symbol, detected_at DESC);
CREATE INDEX idx_toxicity_scores_symbol ON toxicity_scores (symbol, computed_at DESC);
CREATE INDEX idx_trader_type_symbol ON trader_type_estimates (symbol, computed_at DESC);
CREATE INDEX idx_reproducibility_model ON reproducibility_metadata (model_name, created_at DESC);
CREATE INDEX idx_regulatory_compliance_status ON regulatory_compliance_reports (compliance_status) WHERE compliance_status != 'COMPLIANT';
CREATE INDEX idx_config_snapshots_audit ON config_snapshots (audit_reason) WHERE audit_reason IS NOT NULL;
CREATE INDEX idx_tick_data_ofi ON tick_data (order_flow_imbalance) WHERE order_flow_imbalance IS NOT NULL;
CREATE INDEX idx_tick_data_at_proxy ON tick_data (at_activity_proxy) WHERE at_activity_proxy IS NOT NULL;
