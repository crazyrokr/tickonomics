-- P-H4: Replace DOUBLE PRECISION with NUMERIC in primary-key columns.
-- DOUBLE PRECISION (IEEE 754 floating point) in a primary key is unreliable
-- because floating-point equality is inexact. Two rows with the same logical
-- value (e.g. strike 100.00 or quantile 0.05) can fail to match on lookup,
-- silently skipping rows in joins and WHERE clauses.
--
-- NUMERIC provides exact decimal representation suitable for financial prices
-- and statistical quantile values, both of which require reliable equality
-- semantics when used as key columns.

-- option_chain_snapshots: strike is an option exercise price in dollars.
-- NUMERIC(18,6) supports strikes up to $999,999,999,999.999999.

ALTER TABLE option_chain_snapshots
    ALTER COLUMN strike TYPE NUMERIC(18, 6);

-- quantile_coefficients: quantile is a statistical quantile in (0, 1).
-- NUMERIC(10,8) supports values from 0.00000001 to 0.99999999 with 8 decimal
-- digits — sufficient for any realistic quantile regression grid.

ALTER TABLE quantile_coefficients
    ALTER COLUMN quantile TYPE NUMERIC(10, 8);
