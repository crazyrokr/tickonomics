-- ============================================================================
-- V36: Add NUMERIC columns alongside existing DOUBLE PRECISION for money fields.
-- This is the additive phase of the BigDecimal/NUMERIC migration (ADR-033).
-- Old columns remain in place for rollback safety. Backfill copies existing data
-- so new Java code can read exclusively from the new columns immediately after
-- deployment.
-- ============================================================================

-- ---- virtual_portfolio_positions ----
ALTER TABLE virtual_portfolio_positions
    ADD COLUMN quantity_num          NUMERIC(16,4),
    ADD COLUMN entry_price_num       NUMERIC(20,8),
    ADD COLUMN current_price_num     NUMERIC(20,8),
    ADD COLUMN unrealized_pnl_num    NUMERIC(20,8),
    ADD COLUMN stop_loss_price_num   NUMERIC(20,8),
    ADD COLUMN take_profit_price_num NUMERIC(20,8);

UPDATE virtual_portfolio_positions SET
    quantity_num          = quantity::numeric(16,4),
    entry_price_num       = entry_price::numeric(20,8),
    current_price_num     = current_price::numeric(20,8),
    unrealized_pnl_num    = unrealized_pnl::numeric(20,8),
    stop_loss_price_num   = stop_loss_price::numeric(20,8),
    take_profit_price_num = take_profit_price::numeric(20,8);

-- ---- virtual_portfolio_trades ----
ALTER TABLE virtual_portfolio_trades
    ADD COLUMN quantity_num     NUMERIC(16,4),
    ADD COLUMN fill_price_num   NUMERIC(20,8),
    ADD COLUMN commission_num   NUMERIC(20,8),
    ADD COLUMN slippage_num     NUMERIC(20,8),
    ADD COLUMN realized_pnl_num NUMERIC(20,8);

UPDATE virtual_portfolio_trades SET
    quantity_num     = quantity::numeric(16,4),
    fill_price_num   = fill_price::numeric(20,8),
    commission_num   = commission::numeric(20,8),
    slippage_num     = slippage::numeric(20,8),
    realized_pnl_num = realized_pnl::numeric(20,8);

-- ---- tick_data (hypertable — must decompress first, per V35 pattern) ----
ALTER TABLE tick_data SET (timescaledb.compress = false);

ALTER TABLE tick_data ADD COLUMN IF NOT EXISTS price_num NUMERIC(20,8);

UPDATE tick_data SET price_num = price::numeric(20,8)
    WHERE price IS NOT NULL AND price_num IS NULL;

ALTER TABLE tick_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
    );
