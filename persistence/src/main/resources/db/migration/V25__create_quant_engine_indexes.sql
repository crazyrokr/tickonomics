CREATE INDEX idx_risk_evt_symbol ON risk_evt_parameters (symbol, time DESC);
CREATE INDEX idx_quantile_kpi ON quantile_coefficients (kpi_name, time DESC);
CREATE INDEX idx_synergy_source ON synergy_entropy_matrix (source_a, source_b);
CREATE INDEX idx_macro_shock_source ON macro_shock_irfs (shock_source, target_kpi);
CREATE INDEX idx_universe_stats_time ON universe_stats_history (time DESC);
