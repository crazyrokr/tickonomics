-- Proposals 05, 06, 07: reproducibility, risk guardrails, liquidity analysis

-- Backtesting robustness columns
ALTER TABLE backtest_results
    ADD COLUMN git_sha TEXT;
ALTER TABLE backtest_results
    ADD COLUMN dataset_hash TEXT;
ALTER TABLE backtest_results
    ADD COLUMN model_hyperparams JSONB;
ALTER TABLE backtest_results
    ADD COLUMN rds_score INTEGER;
ALTER TABLE backtest_results
    ADD COLUMN parameter_slice_metadata JSONB;

CREATE TABLE reproducibility_metadata
(
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    model_name        TEXT        NOT NULL,
    code_version      TEXT        NOT NULL,
    dataset_hash      TEXT        NOT NULL,
    hyperparams       JSONB       NOT NULL,
    rds_score         INTEGER     NOT NULL CHECK (rds_score BETWEEN 0 AND 2),
    benchmark_results JSONB
);

CREATE TABLE regulatory_compliance_reports
(
    id                        BIGSERIAL PRIMARY KEY,
    generated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    report_period_start       DATE        NOT NULL,
    report_period_end         DATE        NOT NULL,
    total_signals             INTEGER     NOT NULL,
    stressed_market_intervals INTEGER     NOT NULL,
    kill_switch_tests         INTEGER     NOT NULL,
    otr_breaches              INTEGER,
    compliance_status         TEXT        NOT NULL,
    report_metadata           JSONB
);

CREATE TABLE strategic_run_events
(
    id                BIGSERIAL PRIMARY KEY,
    detected_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol            TEXT        NOT NULL,
    direction         TEXT        NOT NULL,
    child_order_count INTEGER     NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ,
    impact_bps        DOUBLE PRECISION,
    metadata          JSONB
);

-- Audit fields on config changes
ALTER TABLE config_snapshots
    ADD COLUMN audit_reason TEXT;
ALTER TABLE config_snapshots
    ADD COLUMN changed_by TEXT;

-- Market gamma history (GEX)
CREATE TABLE market_gamma_history
(
    time                   TIMESTAMPTZ      NOT NULL,
    symbol                 TEXT             NOT NULL,
    net_gamma              DOUBLE PRECISION NOT NULL,
    gamma_flip_price       DOUBLE PRECISION,
    gex_dollar             DOUBLE PRECISION,
    implied_volatility_atm DOUBLE PRECISION
);
SELECT create_hypertable('market_gamma_history', 'time', chunk_time_interval = > INTERVAL '1 day');

-- Phantom liquidity index
CREATE TABLE phantom_liquidity_metrics
(
    time                 TIMESTAMPTZ      NOT NULL,
    symbol               TEXT             NOT NULL,
    pli                  DOUBLE PRECISION NOT NULL,
    canceled_volume      BIGINT,
    executed_volume      BIGINT,
    total_volume_at_best BIGINT
);
SELECT create_hypertable('phantom_liquidity_metrics', 'time', chunk_time_interval = > INTERVAL '1 day');

CREATE TABLE liquidity_comovement_snapshots
(
    id                     BIGSERIAL PRIMARY KEY,
    computed_at            TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    comovement_factor      DOUBLE PRECISION NOT NULL,
    variance_explained_pc1 DOUBLE PRECISION,
    variance_explained_pc2 DOUBLE PRECISION,
    symbols_included       TEXT[] NOT NULL
);

CREATE TABLE toxicity_scores
(
    id                   BIGSERIAL PRIMARY KEY,
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol               TEXT        NOT NULL,
    venue                TEXT,
    order_to_trade_ratio DOUBLE PRECISION,
    round_trip_pct       DOUBLE PRECISION,
    toxicity_class       TEXT        NOT NULL,
    confidence           DOUBLE PRECISION
);

ALTER TABLE tick_data
    ADD COLUMN order_flow_imbalance DOUBLE PRECISION;
ALTER TABLE tick_data
    ADD COLUMN canceled_volume BIGINT;

CREATE TABLE behavioural_risk_index
(
    time               TIMESTAMPTZ      NOT NULL,
    symbol             TEXT             NOT NULL,
    bri_score          DOUBLE PRECISION NOT NULL,
    ofi_zscore         DOUBLE PRECISION,
    spread_volatility  DOUBLE PRECISION,
    sentiment_polarity DOUBLE PRECISION,
    regime             TEXT             NOT NULL
);
SELECT create_hypertable('behavioural_risk_index', 'time', chunk_time_interval = > INTERVAL '1 day');

CREATE TABLE trader_type_estimates
(
    id                      BIGSERIAL PRIMARY KEY,
    computed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol                  TEXT        NOT NULL,
    algo_dominance          DOUBLE PRECISION,
    institutional_dominance DOUBLE PRECISION,
    professional_dominance  DOUBLE PRECISION,
    retail_dominance        DOUBLE PRECISION,
    spread_compression_pct  DOUBLE PRECISION
);

ALTER TABLE tick_data
    ADD COLUMN at_activity_proxy DOUBLE PRECISION;
