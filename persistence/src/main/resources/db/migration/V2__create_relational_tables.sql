-- Relational tables for configuration, signals, backtesting, and DLQ

CREATE TABLE config_snapshots
(
    id         BIGSERIAL PRIMARY KEY,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    config_key TEXT        NOT NULL,
    old_value  TEXT,
    new_value  TEXT
);

CREATE TABLE alert_rules
(
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT        NOT NULL,
    condition  JSONB       NOT NULL,
    enabled    BOOLEAN              DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE signal_log
(
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT        NOT NULL,
    direction       TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    ili_percentile  DOUBLE PRECISION,
    ili_value       DOUBLE PRECISION,
    expected_move   DOUBLE PRECISION,
    estimated_cost  DOUBLE PRECISION,
    signal_metadata JSONB
);

CREATE TABLE backtest_results
(
    id              BIGSERIAL PRIMARY KEY,
    run_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    strategy_config JSONB       NOT NULL,
    date_range      TSTZRANGE   NOT NULL,
    sharpe_ratio    DOUBLE PRECISION,
    max_drawdown    DOUBLE PRECISION,
    win_rate        DOUBLE PRECISION,
    profit_factor   DOUBLE PRECISION,
    equity_curve    JSONB
);

CREATE TABLE ingestion_dlq
(
    id            BIGSERIAL PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source        TEXT        NOT NULL,
    payload       JSONB       NOT NULL,
    error_message TEXT,
    replayed      BOOLEAN              DEFAULT FALSE
);

CREATE TABLE proxy_divergence_events
(
    id                BIGSERIAL PRIMARY KEY,
    detected_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sofr_value        DOUBLE PRECISION,
    tbill_proxy_value DOUBLE PRECISION,
    correlation_5d    DOUBLE PRECISION,
    divergence_score  DOUBLE PRECISION,
    resolution        TEXT,
    resolved_at       TIMESTAMPTZ
);
