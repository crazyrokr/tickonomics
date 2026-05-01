-- Demo/virtual portfolio tables (Phase 6)

CREATE TABLE virtual_portfolio_positions
(
    id                BIGSERIAL PRIMARY KEY,
    opened_at         TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    symbol            TEXT             NOT NULL,
    quantity          DOUBLE PRECISION NOT NULL,
    entry_price       DOUBLE PRECISION NOT NULL,
    current_price     DOUBLE PRECISION,
    unrealized_pnl    DOUBLE PRECISION,
    stop_loss_price   DOUBLE PRECISION,
    take_profit_price DOUBLE PRECISION,
    signal_id         BIGINT REFERENCES signal_log (id)
);

CREATE TABLE virtual_portfolio_trades
(
    id           BIGSERIAL PRIMARY KEY,
    executed_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    symbol       TEXT             NOT NULL,
    direction    TEXT             NOT NULL,
    quantity     DOUBLE PRECISION NOT NULL,
    fill_price   DOUBLE PRECISION NOT NULL,
    commission   DOUBLE PRECISION NOT NULL DEFAULT 0,
    slippage     DOUBLE PRECISION NOT NULL DEFAULT 0,
    realized_pnl DOUBLE PRECISION,
    position_id  BIGINT REFERENCES virtual_portfolio_positions (id),
    signal_id    BIGINT REFERENCES signal_log (id),
    trade_type   TEXT             NOT NULL DEFAULT 'PAPER'
);

CREATE TABLE signal_quality_reports
(
    id                    BIGSERIAL PRIMARY KEY,
    report_date           DATE    NOT NULL UNIQUE,
    total_signals         INTEGER NOT NULL,
    actionable_signals    INTEGER NOT NULL,
    hit_rate_1d           DOUBLE PRECISION,
    hit_rate_5d           DOUBLE PRECISION,
    hit_rate_10d          DOUBLE PRECISION,
    hit_rate_20d          DOUBLE PRECISION,
    false_positive_rate   DOUBLE PRECISION,
    avg_return_per_signal DOUBLE PRECISION,
    portfolio_pnl         DOUBLE PRECISION,
    portfolio_sharpe      DOUBLE PRECISION,
    vs_spy_return         DOUBLE PRECISION,
    verification_progress JSONB
);
