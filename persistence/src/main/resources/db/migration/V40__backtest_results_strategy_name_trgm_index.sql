-- P-H3: backs the strategy-name search on backtest_results.strategy_config.
-- The repository now queries `strategy_config->>'strategy' ILIKE '%...%'`. A leading-wildcard LIKE
-- on the casted JSON text cannot use any index; this functional GIN trigram index makes the
-- substring ILIKE indexable regardless of JSON shape.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_backtest_results_strategy_name_trgm
    ON backtest_results USING GIN ((strategy_config->>'strategy') gin_trgm_ops);
