ALTER TABLE rate_snapshots DROP CONSTRAINT IF EXISTS chk_rate_type_cdm;
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN (
        'SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
        'RRP', 'TGA', 'WALCL', 'TBILL_3M',
        'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
        'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y'
    ));
