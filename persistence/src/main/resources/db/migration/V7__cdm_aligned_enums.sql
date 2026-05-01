-- CDM-aligned enum constraints for canonical instrument naming

ALTER TABLE rate_snapshots
    ADD CONSTRAINT chk_rate_type_cdm
        CHECK (rate_type IN ('SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
                             'RRP', 'TGA', 'WALCL', 'TBILL_3M'));

ALTER TABLE zscore_series
    ADD CONSTRAINT chk_component_cdm
        CHECK (component IN ('Z_RRP', 'Z_SPREAD', 'Z_VOL',
                             'Z_LIQUIDITY_STRESS', 'Z_REPO_EQUITY_BETA',
                             'Z_RRP_DRAIN', 'Z_SYSTEMIC_RISK', 'Z_VOLATILITY_REGIME'));

ALTER TABLE signal_log
    ADD CONSTRAINT chk_direction_cdm
        CHECK (direction IN ('BUY', 'SELL'));

ALTER TABLE signal_log
    ADD CONSTRAINT chk_signal_status
        CHECK (status IN ('ACTIONABLE', 'SPECULATIVE_STALE_MACRO', 'COST_EXCEEDS_EXPECTED_MOVE',
                          'COOLDOWN', 'INSUFFICIENT_DATA'));

ALTER TABLE correlation_outputs
    ADD CONSTRAINT chk_metric_cdm
        CHECK (metric IN ('PEARSON_CORRELATION', 'GRANGER_CAUSALITY', 'OLS_BETA'));
