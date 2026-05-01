-- Diagnostics, greeks, and statistical method tables
-- Note: strategy_definitions, alpha_signals, option_chain_snapshots, evt_risk_metrics
-- are created by V19, V21, V22 respectively (with richer schemas).

CREATE TABLE fed_balance_sheet
(
    time            TIMESTAMPTZ      NOT NULL,
    total_assets    DOUBLE PRECISION NOT NULL,
    securities_held DOUBLE PRECISION,
    loans_held      DOUBLE PRECISION,
    reserve_balance DOUBLE PRECISION,
    reverse_repo    DOUBLE PRECISION,
    tga_balance     DOUBLE PRECISION
);
SELECT create_hypertable('fed_balance_sheet', 'time', chunk_time_interval = > INTERVAL '7 days');

CREATE TABLE tournament_results
(
    id           BIGSERIAL PRIMARY KEY,
    run_date     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol       TEXT        NOT NULL,
    model_name   TEXT        NOT NULL,
    sharpe       DOUBLE PRECISION,
    hit_rate     DOUBLE PRECISION,
    max_drawdown DOUBLE PRECISION,
    regime_type  TEXT,
    period_start DATE        NOT NULL,
    period_end   DATE        NOT NULL
);

CREATE TABLE greeks_sensitivity
(
    time         TIMESTAMPTZ NOT NULL,
    symbol       TEXT,
    repo_delta   DOUBLE PRECISION,
    rate_delta   DOUBLE PRECISION,
    rate_gamma   DOUBLE PRECISION,
    spread_delta DOUBLE PRECISION,
    volga        DOUBLE PRECISION,
    dv01         DOUBLE PRECISION,
    convexity    DOUBLE PRECISION
);
SELECT create_hypertable('greeks_sensitivity', 'time', chunk_time_interval = > INTERVAL '1 day');

CREATE TABLE risk_premium_residuals
(
    time             TIMESTAMPTZ NOT NULL,
    instrument       TEXT        NOT NULL,
    observed_yield   DOUBLE PRECISION,
    fair_value_yield DOUBLE PRECISION,
    residual         DOUBLE PRECISION,
    residual_std     DOUBLE PRECISION,
    dislocated       BOOLEAN DEFAULT FALSE
);
SELECT create_hypertable('risk_premium_residuals', 'time', chunk_time_interval = > INTERVAL '1 day');

ALTER TABLE backtest_results
    ADD COLUMN adjusted_p_values JSONB;

CREATE INDEX idx_fed_balance_sheet_time ON fed_balance_sheet (time DESC);
CREATE INDEX idx_tournament_run_date ON tournament_results (run_date DESC);
CREATE INDEX idx_tournament_symbol_model ON tournament_results (symbol, model_name);
CREATE INDEX idx_greeks_sensitivity_symbol_time ON greeks_sensitivity (symbol, time DESC);
CREATE INDEX idx_risk_premium_time ON risk_premium_residuals (instrument, time DESC);
