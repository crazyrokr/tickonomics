-- Add direction column to track position side
ALTER TABLE virtual_portfolio_positions ADD COLUMN direction TEXT NOT NULL DEFAULT 'BUY';

-- Add closed_at column to track position lifecycle (mirrors resolved_at in proxy_divergence_events)
ALTER TABLE virtual_portfolio_positions ADD COLUMN closed_at TIMESTAMPTZ;

-- Partial index for open positions
CREATE INDEX idx_vpp_open ON virtual_portfolio_positions (symbol) WHERE closed_at IS NULL;

-- Trade lookup indexes
CREATE INDEX idx_vpt_symbol_time ON virtual_portfolio_trades (symbol, executed_at DESC);
CREATE INDEX idx_vpt_position_id ON virtual_portfolio_trades (position_id);

-- Signal quality report lookup
CREATE INDEX idx_sqr_date ON signal_quality_reports (report_date DESC);
