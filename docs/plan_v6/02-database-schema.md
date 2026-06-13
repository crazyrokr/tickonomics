# Track 2: Database Schema (TimescaleDB)

**Phase:** Phase 0
**Can start:** After Track 1 (project scaffolding exists)
**Blocks:** Tracks 4, 5
**Depends on:** Track 1

---

## Objective

Set up TimescaleDB as the single database for both time-series and relational data.
Implement all Flyway migrations including hypertables, continuous aggregates, retention
policies, and compression policies.

---

## Key Design Decision

**Single database: TimescaleDB (PostgreSQL + time-series extension)**

This replaces the v5 dual-database architecture (QuestDB + PostgreSQL) with a single
TimescaleDB instance that handles both time-series (via hypertables) and relational data
(via regular PostgreSQL tables). Schema migrations use **Flyway only** — single target.

**Analysis findings applied (v1):**

- **Finding 4 (Real-Time Aggregates):** Continuous aggregates use `materialized_only = false`
  so that TimescaleDB transparently joins materialized historical data with raw `tick_data`,
  providing true real-time OHLCV without a 1-minute blind spot.
- **Finding 3 (Proxy Divergence Guard):** `ili_history` includes `proxy_divergence_status`
  column to track T-Bill/SOFR dislocation. A new `proxy_divergence_events` table records
  dislocation incidents for audit and backtesting.
- **Finding 6 (Dynamic Weighting):** `ili_history` uses `DEGRADED_COMPONENT_STALE` status
  instead of `DATA_INSUFFICIENT` when a component is stale. The `active_weights` JSONB column
  records the actual weights used after redistribution.

**External integration (v2):**

- **FINOS CDM-aligned enums:** `rate_type` values in `rate_snapshots` and `component` values in
  `zscore_series` use CDM-aligned identifiers from the `cdm/` module (Track 1). This provides
  canonical naming that maps to CDM `FloatingRateNote` and `Bill` instrument types. A migration
  adds a `CHECK` constraint enforcing CDM-valid values.

---

## Database Tables

### Hypertables (Time-Series)

| Table                       | Purpose                                      | Chunk Interval | Retention                   |
|:----------------------------|:---------------------------------------------|:---------------|:----------------------------|
| `tick_data`                 | Real-time tick data from Finnhub WebSocket   | 1 day          | 90 days, then auto-compress |
| `rate_snapshots`            | SOFR, EFFR, TGCR, BGCR, IORB, etc.           | 1 day          | Indefinite                  |
| `ili_history`               | ILI values over time                         | 1 day          | Indefinite                  |
| `zscore_series`             | Z-score normalized component values          | 1 day          | Indefinite                  |
| `correlation_outputs`       | Rolling correlation and beta outputs         | 1 day          | Indefinite                  |
| `market_events`             | Regime detection market events (v4)          | 1 day          | Indefinite                  |
| `market_gamma_history`      | GEX snapshots per symbol                     | 1 day          | Indefinite                  | *(v5: Proposal 06)*
| `phantom_liquidity_metrics` | Phantom liquidity index over time            | 1 day          | 90 days                     | *(v5: Proposal 07)*
| `order_flow_imbalance`      | Per-symbol OFI metrics                       | 1 day          | 30 days                     | *(v5: Proposal 07)*
| `factor_returns`            | Ken French factor returns (3/5 factor, momentum) | 1 year     | Indefinite                  | *(v6: Phase 4)*
| `index_snapshots`           | Historical index snapshots (Shiller S&P 500) | 10 years       | Indefinite                  | *(v6: Phase 7)*

### Regular Tables (Relational)

| Table                            | Purpose                                                                         | Retention             |
|:---------------------------------|:--------------------------------------------------------------------------------|:----------------------|
| `config_snapshots`               | Before/after diffs on configuration changes                                     | Indefinite            |
| `alert_rules`                    | User-defined alert thresholds                                                   | Indefinite            |
| `signal_log`                     | Append-only signal history                                                      | 2 years, then archive |
| `backtest_results`               | Backtest run outputs                                                            | Indefinite            |
| `ingestion_dlq`                  | Dead letter queue for failed ingestion events                                   | 90 days               |
| `virtual_portfolio_positions`    | Demo portfolio open positions (Phase 6)                                         | Indefinite            |
| `virtual_portfolio_trades`       | Demo portfolio trade history (Phase 6)                                          | Indefinite            |
| `signal_quality_reports`         | Daily signal quality metrics (Phase 6)                                          | Indefinite            |
| `disaster_alerts`                | External disaster alerts from USGS/GDACS (v4)                                   | Indefinite            |
| `regime_detection_results`       | Regime detection run outputs (v4)                                               | Indefinite            |
| `optimization_runs`              | Weight optimization run history (v4)                                            | Indefinite            |
| `active_weights`                 | Active weights snapshot per optimization source (v4)                            | Indefinite            |
| `liquidity_stress_results`       | Liquidity stress test results (v4)                                              | Indefinite            |
| `liquidity_comovement_snapshots` | PCA-based comovement factor snapshots                                           | Indefinite            | *(v5: Proposal 07)*
| `toxicity_scores`                | Per-venue/asset toxicity scores                                                 | Indefinite            | *(v5: Proposal 07)*
| `behavioural_risk_index`         | BRI scores with OFI, spread vol, sentiment components                           | Indefinite            | *(v5: Proposal 07)*
| `trader_type_estimates`          | Trader type dominance estimates per symbol                                      | Indefinite            | *(v5: Proposal 07)*
| `regulatory_compliance_reports`  | Self-certification reports for MiFID II style compliance                        | Indefinite            | *(v5: Proposal 05)*
| `strategic_run_events`           | Strategic run detection events                                                  | Indefinite            | *(v5: Proposal 05)*
| `reproducibility_metadata`       | Audit-grade artifacts: code version, dataset hash, model hyperparams, RDS score | Indefinite            | *(v5: Proposal 05)*
| `data_import_tracker`            | Import state tracker for incremental fetches                                    | Indefinite            | *(v6: Phase 6)*

### Continuous Aggregates

| Materialized View         | Source                                         | Refresh Interval |
|:--------------------------|:-----------------------------------------------|:-----------------|
| `ohlcv_1min`              | `tick_data` via `candlestick_agg()`            | Every 1 minute   |
| `ohlcv_1h`                | `tick_data` via `candlestick_agg()`            | Every 1 hour     |
| `ohlcv_1d`                | `tick_data` via `candlestick_agg()`            | Every 1 day      |
| `daily_kpi_summary`       | ILI + KPI daily aggregates                     | Every 1 day      |
| `kpi_rolling_correlation` | `correlation_outputs` daily rollup             | Every 1 day (v3) |
| `kpi_rolling_beta`        | `correlation_outputs` daily rollup             | Every 1 day (v3) |
| `kpi_zscore_daily`        | `zscore_series` daily rollup                   | Every 1 day (v3) |
| `daily_event_summary`     | `market_events` daily aggregation by type      | Every 1 day (v4) |
| `ili_robustness_heatmap`  | Parameter sensitivity from backtest results    | Every 1 day      | *(v5: Proposal 05)*
| `daily_comovement_factor` | Daily comovement factor from 5-min spread data | Every 1 day      | *(v5: Proposal 07)*
| `daily_toxicity_summary`  | Daily toxicity aggregation by venue            | Every 1 day      | *(v5: Proposal 07)*

---

## Flyway Migration Files

All migrations in `persistence/src/main/resources/db/migration/`:

### V1__create_hypertables.sql

```sql
-- tick_data hypertable
CREATE TABLE tick_data (
    time        TIMESTAMPTZ NOT NULL,
    symbol      TEXT NOT NULL,
    price       DOUBLE PRECISION,
    volume      BIGINT,
    conditions  INTEGER[]
);
SELECT create_hypertable('tick_data', 'time', chunk_time_interval => INTERVAL '1 day');

-- rate_snapshots hypertable
CREATE TABLE rate_snapshots (
    time        TIMESTAMPTZ NOT NULL,
    rate_type   TEXT NOT NULL,
    value       DOUBLE PRECISION,
    source      TEXT
);
SELECT create_hypertable('rate_snapshots', 'time', chunk_time_interval => INTERVAL '1 day');

-- ili_history hypertable
CREATE TABLE ili_history (
    time                    TIMESTAMPTZ NOT NULL,
    ili_value               DOUBLE PRECISION,
    z_rrp                   DOUBLE PRECISION,
    z_spread                DOUBLE PRECISION,
    z_vol                   DOUBLE PRECISION,
    data_status             TEXT NOT NULL,          -- VALID, DATA_INSUFFICIENT, DEGRADED_COMPONENT_STALE, DISLOCATED
    active_weights          JSONB,                  -- actual weights used after dynamic redistribution (Finding 6)
    proxy_divergence_status TEXT,                   -- NULL, DIVERGENT, SUPPRESSED (Finding 3)
    proxy_divergence_score  DOUBLE PRECISION        -- std deviations of T-Bill/SOFR divergence (Finding 3)
);
SELECT create_hypertable('ili_history', 'time', chunk_time_interval => INTERVAL '1 day');

-- zscore_series hypertable
CREATE TABLE zscore_series (
    time            TIMESTAMPTZ NOT NULL,
    component       TEXT NOT NULL,
    raw_value       DOUBLE PRECISION,
    z_score         DOUBLE PRECISION,
    lookback_days   INTEGER NOT NULL
);
SELECT create_hypertable('zscore_series', 'time', chunk_time_interval => INTERVAL '1 day');

-- correlation_outputs hypertable
CREATE TABLE correlation_outputs (
    time                TIMESTAMPTZ NOT NULL,
    symbol              TEXT NOT NULL,
    metric              TEXT NOT NULL,
    correlation         DOUBLE PRECISION,
    p_value             DOUBLE PRECISION,
    sample_size         INTEGER,
    lag_order           INTEGER,
    direction           TEXT
);
SELECT create_hypertable('correlation_outputs', 'time', chunk_time_interval => INTERVAL '1 day');
```

### V2__create_relational_tables.sql

```sql
CREATE TABLE config_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    config_key  TEXT NOT NULL,
    old_value   TEXT,
    new_value   TEXT
);

CREATE TABLE alert_rules (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    condition   JSONB NOT NULL,
    enabled     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE signal_log (
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,
    status          TEXT NOT NULL,
    ili_percentile  DOUBLE PRECISION,
    ili_value       DOUBLE PRECISION,
    expected_move   DOUBLE PRECISION,
    estimated_cost  DOUBLE PRECISION,
    signal_metadata JSONB
);

CREATE TABLE backtest_results (
    id              BIGSERIAL PRIMARY KEY,
    run_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    strategy_config JSONB NOT NULL,
    date_range      TSTZRANGE NOT NULL,
    sharpe_ratio    DOUBLE PRECISION,
    max_drawdown    DOUBLE PRECISION,
    win_rate        DOUBLE PRECISION,
    profit_factor   DOUBLE PRECISION,
    equity_curve    JSONB
);

CREATE TABLE ingestion_dlq (
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          TEXT NOT NULL,
    payload         JSONB NOT NULL,
    error_message   TEXT,
    replayed        BOOLEAN DEFAULT FALSE
);

-- Proxy divergence events (Finding 3: Proxy Divergence Guard)
-- Records T-Bill/SOFR dislocation incidents for audit and backtesting.
CREATE TABLE proxy_divergence_events (
    id                  BIGSERIAL PRIMARY KEY,
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sofr_value          DOUBLE PRECISION,
    tbill_proxy_value   DOUBLE PRECISION,
    correlation_5d      DOUBLE PRECISION,        -- 5-day rolling correlation before event
    divergence_score    DOUBLE PRECISION,         -- std deviations from expected relationship
    resolution          TEXT,                     -- NULL, SOFR_PUBLISHED, MANUAL_CLEAR, EXPIRED
    resolved_at         TIMESTAMPTZ
);
```

### V3__create_continuous_aggregates.sql

```sql
-- 1-minute OHLCV candles (Real-Time Aggregates: materialized_only = false)
-- Finding 4: transparently joins materialized data with live tick_data,
-- eliminating the 1-minute blind spot.
CREATE MATERIALIZED VIEW ohlcv_1min
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 minute', time) AS minute,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY minute, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1min',
    start_offset => INTERVAL '3 hours',
    end_offset => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute');

-- 1-hour OHLCV candles
CREATE MATERIALIZED VIEW ohlcv_1h
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 hour', time) AS hour,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY hour, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1h',
    start_offset => INTERVAL '7 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

-- 1-day OHLCV candles
CREATE MATERIALIZED VIEW ohlcv_1d
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    symbol,
    candlestick_agg(time, price, volume) AS candle
FROM tick_data
GROUP BY day, symbol;

SELECT add_continuous_aggregate_policy('ohlcv_1d',
    start_offset => NULL,
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');

-- Daily KPI summary
CREATE MATERIALIZED VIEW daily_kpi_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    AVG(ili_value) AS avg_ili,
    MIN(ili_value) AS min_ili,
    MAX(ili_value) AS max_ili,
    STDDEV(ili_value) AS stddev_ili
FROM ili_history
WHERE data_status = 'VALID'
GROUP BY day;

SELECT add_continuous_aggregate_policy('daily_kpi_summary',
    start_offset => NULL,
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');
```

### V4__create_compression_retention.sql

```sql
-- Compression on tick_data
ALTER TABLE tick_data SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('tick_data', compress_after => INTERVAL '7 days');

-- Retention policies
SELECT add_retention_policy('tick_data', drop_after => INTERVAL '90 days');
SELECT add_retention_policy('signal_log', drop_after => INTERVAL '730 days');
SELECT add_retention_policy('ingestion_dlq', drop_after => INTERVAL '90 days');
```

### V5__create_indexes.sql

```sql
CREATE INDEX idx_tick_data_symbol_time ON tick_data (symbol, time DESC);
CREATE INDEX idx_rate_snapshots_type_time ON rate_snapshots (rate_type, time DESC);
CREATE INDEX idx_ili_history_time ON ili_history (time DESC);
CREATE INDEX idx_signal_log_symbol_time ON signal_log (symbol, created_at DESC);
CREATE INDEX idx_signal_log_status ON signal_log (status);
CREATE INDEX idx_correlation_outputs_symbol_metric ON correlation_outputs (symbol, metric, time DESC);
CREATE INDEX idx_ingestion_dlq_replayed ON ingestion_dlq (replayed) WHERE NOT replayed;
CREATE INDEX idx_proxy_divergence_events_detected ON proxy_divergence_events (detected_at DESC);
CREATE INDEX idx_ili_history_data_status ON ili_history (data_status) WHERE data_status != 'VALID';
```

### V6__create_demo_tables.sql (for Phase 6)

```sql
CREATE TABLE virtual_portfolio_positions (
    id              BIGSERIAL PRIMARY KEY,
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    quantity        DOUBLE PRECISION NOT NULL,
    entry_price     DOUBLE PRECISION NOT NULL,
    current_price   DOUBLE PRECISION,
    unrealized_pnl  DOUBLE PRECISION,
    stop_loss_price DOUBLE PRECISION,
    take_profit_price DOUBLE PRECISION,
    signal_id       BIGINT REFERENCES signal_log(id)
);

CREATE TABLE virtual_portfolio_trades (
    id              BIGSERIAL PRIMARY KEY,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,
    quantity        DOUBLE PRECISION NOT NULL,
    fill_price      DOUBLE PRECISION NOT NULL,
    commission      DOUBLE PRECISION NOT NULL DEFAULT 0,
    slippage        DOUBLE PRECISION NOT NULL DEFAULT 0,
    realized_pnl    DOUBLE PRECISION,
    position_id     BIGINT REFERENCES virtual_portfolio_positions(id),
    signal_id       BIGINT REFERENCES signal_log(id),
    trade_type      TEXT NOT NULL DEFAULT 'PAPER'
);

CREATE TABLE signal_quality_reports (
    id                  BIGSERIAL PRIMARY KEY,
    report_date         DATE NOT NULL UNIQUE,
    total_signals       INTEGER NOT NULL,
    actionable_signals  INTEGER NOT NULL,
    hit_rate_1d         DOUBLE PRECISION,
    hit_rate_5d         DOUBLE PRECISION,
    hit_rate_10d        DOUBLE PRECISION,
    hit_rate_20d        DOUBLE PRECISION,
    false_positive_rate DOUBLE PRECISION,
    avg_return_per_signal DOUBLE PRECISION,
    portfolio_pnl       DOUBLE PRECISION,
    portfolio_sharpe    DOUBLE PRECISION,
    vs_spy_return       DOUBLE PRECISION,
    verification_progress JSONB
);
```

### V7__cdm_aligned_enums.sql — v2

```sql
-- FINOS CDM-aligned enum constraints for canonical instrument naming.
-- Values correspond to the CDM projection in the cdm/ module (Track 1).

-- rate_snapshots.rate_type: CDM FloatingRateNote identifiers
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN ('SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
                          'RRP', 'TGA', 'WALCL', 'TBILL_3M',
                          'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
                          'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y',
                          'VIX', 'OIL_WTI', 'OIL_BRENT', 'GOLD'));

-- zscore_series.component: CDM-aligned component identifiers
ALTER TABLE zscore_series ADD CONSTRAINT chk_component_cdm
    CHECK (component IN ('Z_RRP', 'Z_SPREAD', 'Z_VOL',
                         'Z_LIQUIDITY_STRESS', 'Z_REPO_EQUITY_BETA',
                         'Z_RRP_DRAIN', 'Z_SYSTEMIC_RISK', 'Z_VOLATILITY_REGIME'));

-- signal_log.direction: standardized direction enum
ALTER TABLE signal_log ADD CONSTRAINT chk_direction_cdm
    CHECK (direction IN ('BUY', 'SELL'));

-- signal_log.status: signal status enum
ALTER TABLE signal_log ADD CONSTRAINT chk_signal_status
    CHECK (status IN ('ACTIONABLE', 'SPECULATIVE_STALE_MACRO', 'COST_EXCEEDS_EXPECTED_MOVE',
                      'COOLDOWN', 'INSUFFICIENT_DATA'));

-- correlation_outputs.metric: CDM-aligned metric identifiers
ALTER TABLE correlation_outputs ADD CONSTRAINT chk_metric_cdm
    CHECK (metric IN ('PEARSON_CORRELATION', 'GRANGER_CAUSALITY', 'OLS_BETA'));
```

### V8__create_precomputed_kpi_views.sql — v3

Pre-computed continuous aggregates for rolling correlation and beta. These reduce per-cycle
TA-Lib computation in the Java layer by materializing stable-window KPIs at the database level
(Proposal #3).

```sql
-- Rolling 20-day Pearson correlation between funding metrics and equity returns
-- Computed from correlation_outputs (already stored by ComputationEngine)
CREATE MATERIALIZED VIEW kpi_rolling_correlation
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    symbol,
    metric,
    AVG(correlation) AS avg_correlation,
    MIN(p_value)    AS min_p_value,
    AVG(sample_size) AS avg_sample_size,
    COUNT(*)         AS observations
FROM correlation_outputs
WHERE metric = 'PEARSON_CORRELATION'
GROUP BY day, symbol, metric;

SELECT add_continuous_aggregate_policy('kpi_rolling_correlation',
    start_offset => INTERVAL '30 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');

-- Rolling 60-day beta between equity and repo rate changes
CREATE MATERIALIZED VIEW kpi_rolling_beta
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    symbol,
    AVG(correlation) AS avg_beta,
    AVG(p_value)    AS avg_p_value,
    COUNT(*)         AS observations
FROM correlation_outputs
WHERE metric = 'OLS_BETA'
GROUP BY day, symbol;

SELECT add_continuous_aggregate_policy('kpi_rolling_beta',
    start_offset => INTERVAL '90 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');

-- Z-score summary per component (pre-aggregated daily)
CREATE MATERIALIZED VIEW kpi_zscore_daily
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    component,
    AVG(z_score)  AS avg_z_score,
    MIN(z_score)  AS min_z_score,
    MAX(z_score)  AS max_z_score,
    STDDEV(z_score) AS stddev_z_score,
    AVG(raw_value) AS avg_raw_value
FROM zscore_series
GROUP BY day, component;

SELECT add_continuous_aggregate_policy('kpi_zscore_daily',
    start_offset => INTERVAL '30 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');
```

### V9__add_anomaly_columns.sql — v4

Data quality module: anomaly scoring columns on existing hypertables (Proposal 01).

```sql
-- Proposal 01: Data Quality Module
ALTER TABLE ili_history ADD COLUMN anomaly_score DOUBLE PRECISION;
ALTER TABLE ili_history ADD COLUMN is_suspect_anomaly BOOLEAN DEFAULT FALSE;
ALTER TABLE rate_snapshots ADD COLUMN anomaly_score DOUBLE PRECISION;
ALTER TABLE rate_snapshots ADD COLUMN is_suspect_anomaly BOOLEAN DEFAULT FALSE;
```

### V10__add_idempotency_and_events.sql — v4

Resilience idempotency keys, regime detection market events, disaster alerts, and regime
detection results (Proposals 02, 03).

```sql
-- Proposal 02: Resilience - Idempotency keys
ALTER TABLE ingestion_dlq ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX idx_ingestion_dlq_idempotency ON ingestion_dlq(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Proposal 03: Regime Detection - Market events hypertable
CREATE TABLE market_events (
    time            TIMESTAMPTZ NOT NULL,
    event_type      TEXT NOT NULL,  -- DIRECTIONAL_CHANGE, OVERSHOOT, EXOGENOUS_SHOCK
    source_symbol   TEXT,
    magnitude       DOUBLE PRECISION,
    metadata        JSONB
);
SELECT create_hypertable('market_events', 'time', chunk_time_interval => INTERVAL '1 day');

-- Proposal 03: Disaster alerts table
CREATE TABLE disaster_alerts (
    id              BIGSERIAL PRIMARY KEY,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          TEXT NOT NULL,  -- USGS, GDACS
    alert_type      TEXT NOT NULL,  -- EARTHQUAKE, TSUNAMI, HURRICANE
    severity        TEXT NOT NULL,  -- ADVISORY, WARNING, CRITICAL
    magnitude       DOUBLE PRECISION,
    location        TEXT,
    raw_payload     JSONB
);

-- Proposal 03: Regime detection results
CREATE TABLE regime_detection_results (
    id              BIGSERIAL PRIMARY KEY,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    method          TEXT NOT NULL,  -- GARCH, CNN_LSTM, QED, KMEANS
    regime          TEXT NOT NULL,  -- LOW_VOL, NORMAL, HIGH_VOL, METASTABLE, UNSTABLE, EXOGENOUS_SHOCK
    confidence      DOUBLE PRECISION,
    metadata        JSONB
);
```

### V11__add_optimization_tables.sql — v4

Weight optimization runs, active weights snapshots, and liquidity stress test results
(Proposals 04, 02).

```sql
-- Proposal 04: Weight Optimization
CREATE TABLE optimization_runs (
    id              BIGSERIAL PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    method          TEXT NOT NULL,  -- BAYESIAN, FIREFLY, RAHF
    status          TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING, COMPLETED, FAILED
    input_params    JSONB NOT NULL,
    output_params   JSONB,
    fitness_score   DOUBLE PRECISION,
    iterations      INTEGER,
    metadata        JSONB
);

-- Proposal 04: Active weights snapshot (extends config_snapshots concept)
CREATE TABLE active_weights (
    id              BIGSERIAL PRIMARY KEY,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    weights         JSONB NOT NULL,
    source          TEXT NOT NULL,  -- MANUAL, BAYESIAN, FIREFLY, ONLINE_SGD
    backtest_sharpe DOUBLE PRECISION,
    validation_passed BOOLEAN DEFAULT FALSE
);

-- Proposal 02: Liquidity stress test results
CREATE TABLE liquidity_stress_results (
    id              BIGSERIAL PRIMARY KEY,
    run_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scenario        TEXT NOT NULL,
    impact_metrics  JSONB NOT NULL,
    strategy_survived BOOLEAN
);
```

### V12__add_v4_continuous_aggregates.sql — v4

Continuous aggregate for market event daily summary (Proposal 03).

```sql
-- Market event aggregation by day
CREATE MATERIALIZED VIEW daily_event_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    event_type,
    COUNT(*) AS event_count,
    AVG(magnitude) AS avg_magnitude,
    MAX(magnitude) AS max_magnitude
FROM market_events
GROUP BY day, event_type;
```

### V13__add_v4_indexes.sql — v4

Indexes for v4 tables and new query patterns.

```sql
CREATE INDEX idx_market_events_type_time ON market_events (event_type, time DESC);
CREATE INDEX idx_disaster_alerts_detected ON disaster_alerts (detected_at DESC);
CREATE INDEX idx_disaster_alerts_severity ON disaster_alerts (severity) WHERE severity IN ('WARNING', 'CRITICAL');
CREATE INDEX idx_regime_results_method_time ON regime_detection_results (method, detected_at DESC);
CREATE INDEX idx_optimization_runs_status ON optimization_runs (status) WHERE status = 'RUNNING';
CREATE INDEX idx_active_weights_applied ON active_weights (applied_at DESC);
CREATE INDEX idx_ili_history_anomaly ON ili_history (is_suspect_anomaly) WHERE is_suspect_anomaly = TRUE;
```

### V14__add_proposals_05_06_07.sql — v5

```sql
-- Proposal 05: Backtesting Robustness

-- Reproducibility metadata for audit-grade artifacts
ALTER TABLE backtest_results ADD COLUMN git_sha TEXT;
ALTER TABLE backtest_results ADD COLUMN dataset_hash TEXT;
ALTER TABLE backtest_results ADD COLUMN model_hyperparams JSONB;
ALTER TABLE backtest_results ADD COLUMN rds_score INTEGER; -- 0-2 rubric
ALTER TABLE backtest_results ADD COLUMN parameter_slice_metadata JSONB; -- For heatmap generation

CREATE TABLE reproducibility_metadata (
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    model_name      TEXT NOT NULL,
    code_version    TEXT NOT NULL,
    dataset_hash    TEXT NOT NULL,
    hyperparams     JSONB NOT NULL,
    rds_score       INTEGER NOT NULL CHECK (rds_score BETWEEN 0 AND 2),
    benchmark_results JSONB
);

-- Regulatory compliance reports (MiFID II style self-certification)
CREATE TABLE regulatory_compliance_reports (
    id                  BIGSERIAL PRIMARY KEY,
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    report_period_start DATE NOT NULL,
    report_period_end   DATE NOT NULL,
    total_signals       INTEGER NOT NULL,
    stressed_market_intervals INTEGER NOT NULL,
    kill_switch_tests   INTEGER NOT NULL,
    otr_breaches        INTEGER,
    compliance_status   TEXT NOT NULL, -- COMPLIANT, DEGRADED, NON_COMPLIANT
    report_metadata     JSONB
);

-- Strategic run events
CREATE TABLE strategic_run_events (
    id              BIGSERIAL PRIMARY KEY,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL, -- BUY_RUN, SELL_RUN
    child_order_count INTEGER NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ,
    impact_bps      DOUBLE PRECISION,
    metadata        JSONB
);

-- Proposal 06: Risk Guardrails

-- Audit reason fields on configuration changes
ALTER TABLE config_snapshots ADD COLUMN audit_reason TEXT;
ALTER TABLE config_snapshots ADD COLUMN changed_by TEXT;

-- Market gamma history (GEX)
CREATE TABLE market_gamma_history (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    net_gamma       DOUBLE PRECISION NOT NULL,
    gamma_flip_price DOUBLE PRECISION,
    gex_dollar      DOUBLE PRECISION,
    implied_volatility_atm DOUBLE PRECISION
);
SELECT create_hypertable('market_gamma_history', 'time', chunk_time_interval => INTERVAL '1 day');

-- Proposal 07: Liquidity Analysis

-- Phantom liquidity index
CREATE TABLE phantom_liquidity_metrics (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    pli             DOUBLE PRECISION NOT NULL, -- Phantom Liquidity Index (0-1)
    canceled_volume BIGINT,
    executed_volume BIGINT,
    total_volume_at_best BIGINT
);
SELECT create_hypertable('phantom_liquidity_metrics', 'time', chunk_time_interval => INTERVAL '1 day');

-- Liquidity comovement snapshots
CREATE TABLE liquidity_comovement_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    comovement_factor DOUBLE PRECISION NOT NULL, -- 0-1 range
    variance_explained_pc1 DOUBLE PRECISION,
    variance_explained_pc2 DOUBLE PRECISION,
    symbols_included TEXT[] NOT NULL
);

-- Toxicity scores per venue/asset
CREATE TABLE toxicity_scores (
    id              BIGSERIAL PRIMARY KEY,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    venue           TEXT,
    order_to_trade_ratio DOUBLE PRECISION,
    round_trip_pct  DOUBLE PRECISION,
    toxicity_class  TEXT NOT NULL, -- HARMFUL, BENEFICIAL, NEUTRAL
    confidence      DOUBLE PRECISION
);

-- Order flow imbalance hypertable
ALTER TABLE tick_data ADD COLUMN order_flow_imbalance DOUBLE PRECISION;
ALTER TABLE tick_data ADD COLUMN canceled_volume BIGINT;

-- Behavioural risk index
CREATE TABLE behavioural_risk_index (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    bri_score       DOUBLE PRECISION NOT NULL, -- 0-1 range
    ofi_zscore      DOUBLE PRECISION,
    spread_volatility DOUBLE PRECISION,
    sentiment_polarity DOUBLE PRECISION,
    regime          TEXT NOT NULL -- NORMAL, HERDING, PANIC, OVERCONFIDENCE
);
SELECT create_hypertable('behavioural_risk_index', 'time', chunk_time_interval => INTERVAL '1 day');

-- Trader type estimates per symbol
CREATE TABLE trader_type_estimates (
    id              BIGSERIAL PRIMARY KEY,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    algo_dominance  DOUBLE PRECISION,
    institutional_dominance DOUBLE PRECISION,
    professional_dominance DOUBLE PRECISION,
    retail_dominance DOUBLE PRECISION,
    spread_compression_pct DOUBLE PRECISION
);

-- AT activity proxy column
ALTER TABLE tick_data ADD COLUMN at_activity_proxy DOUBLE PRECISION;
```

### V15__add_proposals_05_06_07_aggregates.sql — v5

```sql
-- Robustness heatmap data from backtest results
CREATE MATERIALIZED VIEW ili_robustness_heatmap
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', run_at) AS day,
    strategy_config->>'buy_percentile' AS buy_pct,
    strategy_config->>'sell_percentile' AS sell_pct,
    AVG(sharpe_ratio) AS avg_sharpe,
    AVG(win_rate) AS avg_win_rate,
    COUNT(*) AS run_count
FROM backtest_results
WHERE parameter_slice_metadata IS NOT NULL
GROUP BY day, buy_pct, sell_pct;

SELECT add_continuous_aggregate_policy('ili_robustness_heatmap',
    start_offset => NULL,
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');

-- Daily comovement factor
CREATE MATERIALIZED VIEW daily_comovement_factor
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', computed_at) AS day,
    AVG(comovement_factor) AS avg_comovement,
    MAX(comovement_factor) AS max_comovement,
    COUNT(*) AS observations
FROM liquidity_comovement_snapshots
GROUP BY day;

SELECT add_continuous_aggregate_policy('daily_comovement_factor',
    start_offset => INTERVAL '30 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');

-- Daily toxicity summary
CREATE MATERIALIZED VIEW daily_toxicity_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', computed_at) AS day,
    symbol,
    venue,
    AVG(order_to_trade_ratio) AS avg_otr,
    AVG(round_trip_pct) AS avg_round_trip,
    COUNT(*) AS observations,
    COUNT(*) FILTER (WHERE toxicity_class = 'HARMFUL') AS harmful_count
FROM toxicity_scores
GROUP BY day, symbol, venue;

SELECT add_continuous_aggregate_policy('daily_toxicity_summary',
    start_offset => INTERVAL '30 days',
    end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day');
```

### V16__add_proposals_05_06_07_indexes.sql — v5

```sql
CREATE INDEX idx_market_gamma_symbol_time ON market_gamma_history (symbol, time DESC);
CREATE INDEX idx_phantom_liquidity_symbol_time ON phantom_liquidity_metrics (symbol, time DESC);
CREATE INDEX idx_behavioural_risk_symbol_time ON behavioural_risk_index (symbol, time DESC);
CREATE INDEX idx_strategic_run_symbol_time ON strategic_run_events (symbol, detected_at DESC);
CREATE INDEX idx_toxicity_scores_symbol ON toxicity_scores (symbol, computed_at DESC);
CREATE INDEX idx_trader_type_symbol ON trader_type_estimates (symbol, computed_at DESC);
CREATE INDEX idx_reproducibility_model ON reproducibility_metadata (model_name, created_at DESC);
CREATE INDEX idx_regulatory_compliance_status ON regulatory_compliance_reports (compliance_status) WHERE compliance_status != 'COMPLIANT';
CREATE INDEX idx_config_snapshots_audit ON config_snapshots (audit_reason) WHERE audit_reason IS NOT NULL;
CREATE INDEX idx_tick_data_ofi ON tick_data (order_flow_imbalance) WHERE order_flow_imbalance IS NOT NULL;
CREATE INDEX idx_tick_data_at_proxy ON tick_data (at_activity_proxy) WHERE at_activity_proxy IS NOT NULL;
```

### V17__add_proposals_08_09_sentiment_execution.sql — v5

```sql
-- Sentiment history hypertable (dual-score: lexicon + BERT)
CREATE TABLE sentiment_history (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT,
    lexicon_score   DOUBLE PRECISION,
    bert_score      DOUBLE PRECISION,
    bert_certainty  DOUBLE PRECISION,
    source_type     TEXT NOT NULL,  -- FOMC, FED_SPEAKER, MARKET_NEWS, EARNINGS
    text_hash       TEXT NOT NULL,
    polarity        DOUBLE PRECISION,
    subjectivity    DOUBLE PRECISION
);
SELECT create_hypertable('sentiment_history', 'time', chunk_time_interval => INTERVAL '1 day');

-- Execution type for dual portfolio comparison
ALTER TABLE virtual_portfolio_trades ADD COLUMN execution_type TEXT NOT NULL DEFAULT 'AGGRESSIVE'
    CHECK (execution_type IN ('PASSIVE', 'AGGRESSIVE'));
ALTER TABLE signal_log ADD COLUMN execution_type TEXT
    CHECK (execution_type IN ('PASSIVE', 'AGGRESSIVE'));

-- Pairs trading tracking
CREATE TABLE pairs_trading_pairs (
    id              BIGSERIAL PRIMARY KEY,
    symbol_a        TEXT NOT NULL,
    symbol_b        TEXT NOT NULL,
    formation_start DATE NOT NULL,
    formation_end   DATE NOT NULL,
    trading_start   DATE NOT NULL,
    trading_end     DATE NOT NULL,
    distance_score  DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Markov stop calibration results
CREATE TABLE markov_stop_calibrations (
    id                  BIGSERIAL PRIMARY KEY,
    symbol              TEXT NOT NULL,
    optimal_stop_loss   DOUBLE PRECISION NOT NULL,
    optimal_take_profit DOUBLE PRECISION NOT NULL,
    signal_drift        DOUBLE PRECISION,
    decay_intensity     DOUBLE PRECISION,
    converged           BOOLEAN NOT NULL DEFAULT FALSE,
    iterations          INTEGER,
    calibrated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SELECT create_hypertable('markov_stop_calibrations', 'calibrated_at', chunk_time_interval => INTERVAL '7 days');

CREATE INDEX idx_sentiment_symbol_time ON sentiment_history (symbol, time DESC);
CREATE INDEX idx_sentiment_source_type ON sentiment_history (source_type, time DESC);
CREATE INDEX idx_sentiment_bert_certainty ON sentiment_history (bert_certainty) WHERE bert_certainty IS NOT NULL;
CREATE INDEX idx_pairs_trading_symbols ON pairs_trading_pairs (symbol_a, symbol_b);
CREATE INDEX idx_markov_stop_symbol ON markov_stop_calibrations (symbol, calibrated_at DESC);
```

### V18__add_proposals_10_11_12_diagnostics_greeks.sql -- v5

```sql
-- Fed balance sheet data for monetary policy sensitivity panel
CREATE TABLE fed_balance_sheet (
    time            TIMESTAMPTZ NOT NULL,
    total_assets    DOUBLE PRECISION NOT NULL,
    securities_held DOUBLE PRECISION,
    loans_held      DOUBLE PRECISION,
    reserve_balance DOUBLE PRECISION,
    reverse_repo    DOUBLE PRECISION,
    tga_balance     DOUBLE PRECISION
);
SELECT create_hypertable('fed_balance_sheet', 'time', chunk_time_interval => INTERVAL '7 weeks');

-- Model tournament results
CREATE TABLE tournament_results (
    id              BIGSERIAL PRIMARY KEY,
    run_date        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol          TEXT NOT NULL,
    model_name      TEXT NOT NULL,
    sharpe          DOUBLE PRECISION,
    hit_rate        DOUBLE PRECISION,
    max_drawdown    DOUBLE PRECISION,
    regime_type     TEXT,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL
);

-- Greeks sensitivity snapshots
CREATE TABLE greeks_sensitivity (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT,
    repo_delta      DOUBLE PRECISION,
    rate_delta      DOUBLE PRECISION,
    rate_gamma      DOUBLE PRECISION,
    spread_delta    DOUBLE PRECISION,
    volga           DOUBLE PRECISION,
    dv01            DOUBLE PRECISION,
    convexity       DOUBLE PRECISION
);
SELECT create_hypertable('greeks_sensitivity', 'time', chunk_time_interval => INTERVAL '1 day');

-- Q-world risk premium residuals
CREATE TABLE risk_premium_residuals (
    time            TIMESTAMPTZ NOT NULL,
    instrument      TEXT NOT NULL,
    observed_yield  DOUBLE PRECISION,
    fair_value_yield DOUBLE PRECISION,
    residual        DOUBLE PRECISION,
    residual_std    DOUBLE PRECISION,
    dislocated      BOOLEAN DEFAULT FALSE
);
SELECT create_hypertable('risk_premium_residuals', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE INDEX idx_fed_balance_sheet_time ON fed_balance_sheet (time DESC);
CREATE INDEX idx_tournament_run_date ON tournament_results (run_date DESC);
CREATE INDEX idx_tournament_symbol_model ON tournament_results (symbol, model_name);
CREATE INDEX idx_greeks_sensitivity_symbol_time ON greeks_sensitivity (symbol, time DESC);
CREATE INDEX idx_risk_premium_time ON risk_premium_residuals (instrument, time DESC);

-- Proposal 13: Statistical Methods

CREATE TABLE evt_risk_metrics (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    shape_xi        DOUBLE PRECISION,
    scale_beta      DOUBLE PRECISION,
    threshold_u     DOUBLE PRECISION,
    tail_var_99     DOUBLE PRECISION
);
SELECT create_hypertable('evt_risk_metrics', 'time', chunk_time_interval => INTERVAL '1 day');

ALTER TABLE backtest_results ADD COLUMN adjusted_p_values JSONB;

CREATE INDEX idx_evt_risk_symbol_time ON evt_risk_metrics (symbol, time DESC);

-- Proposal 14: 151 Trading Strategies

CREATE TABLE strategy_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    category        TEXT NOT NULL,  -- OPTIONS, EQUITY, FIXED_INCOME, COMMODITY, FX, VOLATILITY
    section_ref     TEXT,           -- e.g., "2.40" for Long Call Butterfly
    formula_refs    TEXT[],         -- e.g., {"Eq.17", "Eq.18"}
    priority        INT NOT NULL DEFAULT 2,  -- 1=P1, 2=P2, 3=P3
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alpha_signals (
    time            TIMESTAMPTZ NOT NULL,
    strategy_id     UUID NOT NULL REFERENCES strategy_definitions(id),
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,  -- LONG, SHORT, NEUTRAL
    strength        DOUBLE PRECISION,
    confidence      DOUBLE PRECISION,
    metadata        JSONB,
    PRIMARY KEY (time, strategy_id, symbol)
);
SELECT create_hypertable('alpha_signals', 'time', chunk_time_interval => INTERVAL '7 days');

CREATE TABLE option_chain_snapshots (
    time            TIMESTAMPTZ NOT NULL,
    symbol          TEXT NOT NULL,
    strike          DOUBLE PRECISION NOT NULL,
    expiry          DATE NOT NULL,
    option_type     TEXT NOT NULL,  -- CALL, PUT
    bid             DOUBLE PRECISION,
    ask             DOUBLE PRECISION,
    last_price      DOUBLE PRECISION,
    volume          BIGINT,
    open_interest   BIGINT,
    delta           DOUBLE PRECISION,
    gamma           DOUBLE PRECISION,
    theta           DOUBLE PRECISION,
    vega            DOUBLE PRECISION,
    rho             DOUBLE PRECISION,
    implied_vol     DOUBLE PRECISION,
    ttm_years       DOUBLE PRECISION,
    underlying_price DOUBLE PRECISION,
    PRIMARY KEY (time, symbol, strike, expiry, option_type)
);
SELECT create_hypertable('option_chain_snapshots', 'time', chunk_time_interval => INTERVAL '1 day');

CREATE INDEX idx_alpha_signals_strategy ON alpha_signals (strategy_id, time DESC);
CREATE INDEX idx_option_chain_symbol_expiry ON option_chain_snapshots (symbol, expiry, strike);
```

### V19__add_v6_factor_returns_index_snapshots.sql — v6

```sql
-- v6: Ken French factor returns (Phase 4)
CREATE TABLE IF NOT EXISTS factor_returns (
    time            TIMESTAMPTZ     NOT NULL,
    factor_set      TEXT            NOT NULL,   -- '3FACTOR', '5FACTOR', 'MOMENTUM'
    frequency       TEXT            NOT NULL,   -- 'MONTHLY', 'DAILY'
    rm_rf           DOUBLE PRECISION,
    smb             DOUBLE PRECISION,
    hml             DOUBLE PRECISION,
    rmw             DOUBLE PRECISION,
    cma             DOUBLE PRECISION,
    rf              DOUBLE PRECISION,
    mom             DOUBLE PRECISION,
    st_rev          DOUBLE PRECISION,
    lt_rev          DOUBLE PRECISION,
    region          TEXT            NOT NULL DEFAULT 'US',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, factor_set, frequency, region)
);
SELECT create_hypertable('factor_returns', 'time', chunk_time_interval => INTERVAL '1 year');
ALTER TABLE factor_returns SET (compress_after = '2 years');

-- v6: Historical index snapshots from DataHub (Phase 7)
CREATE TABLE IF NOT EXISTS index_snapshots (
    time            TIMESTAMPTZ     NOT NULL,
    index_type      TEXT            NOT NULL,   -- 'SP500_SHILLER'
    price           DOUBLE PRECISION NOT NULL,
    dividend        DOUBLE PRECISION,
    earnings        DOUBLE PRECISION,
    cpi             DOUBLE PRECISION,
    long_interest_rate DOUBLE PRECISION,
    real_price      DOUBLE PRECISION,
    real_dividend   DOUBLE PRECISION,
    real_earnings   DOUBLE PRECISION,
    cape            DOUBLE PRECISION,
    source          TEXT            NOT NULL DEFAULT 'DATAHUB_SHILLER',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, index_type)
);
SELECT create_hypertable('index_snapshots', 'time', chunk_time_interval => INTERVAL '10 years');
ALTER TABLE index_snapshots SET (compress_after = '50 years');

-- v6: Import state tracker for incremental fetches (Phase 6)
CREATE TABLE IF NOT EXISTS data_import_tracker (
    symbol          TEXT            NOT NULL,
    source          TEXT            NOT NULL,
    last_imported   TIMESTAMPTZ     NOT NULL,
    rows_imported   BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (symbol, source)
);
```

---

## TimescaleDB Features Used

| Feature                                                | Usage                                                                                                                  |
|:-------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------|
| Hypertables                                            | All time-series tables partitioned by time                                                                             |
| Continuous Aggregates                                  | `ohlcv_1min`, `ohlcv_1h`, `ohlcv_1d` auto-refreshed from `tick_data`                                                   |
| **Real-Time Aggregates** (`materialized_only = false`) | Transparently joins materialized data with live `tick_data`, eliminating the 1-minute aggregate blind spot (Finding 4) |
| `candlestick_agg()`                                    | Financial aggregate for OHLCV construction                                                                             |
| `time_bucket()`                                        | Time-based grouping                                                                                                    |
| `first()` / `last()`                                   | Time-series ordering functions                                                                                         |
| `time_bucket_gapfill()` + `locf()`                     | Gap-filling for missing business days                                                                                  |
| Columnstore compression                                | Auto-compress tick data older than 7 days (~90% reduction)                                                             |
| Retention policies                                     | Auto-drop tick chunks older than 90 days                                                                               |
| SkipScan                                               | Fast "latest price per symbol" queries                                                                                 |

---

## Data Alignment Strategy

All series aligned via `time_bucket()` to a common daily grid at US equity market close (4:00 PM ET):

- Equity prices from Yahoo Finance REST (primary) + Finnhub REST (fallback): last price before 4:00 PM ET.
- FRED (direct client): daily close-aligned by definition.
- NY Fed (direct client): SOFR mapped to the prior business day's grid position.
- T-Bill proxy: daily yield from Federal Reserve H.15 (via direct NY Fed client).
- Finnhub ticks: aggregated by continuous aggregates into OHLCV candles (real-time via
  `materialized_only = false`), last close before 4:00 PM ET.

---

## Missing Data Policy

| Scenario                            | Strategy                                                                                                                                                                                                                                                                                          |
|:------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FRED/NY Fed weekend or holiday gap  | `time_bucket_gapfill()` with `locf()` up to 3 business days. After 3 days, mark `STALE`.                                                                                                                                                                                                          |
| NY Fed T+1 publication lag          | SOFR always as-of yesterday. T-Bill proxy fills the gap.                                                                                                                                                                                                                                          |
| T-Bill/SOFR dislocation (Finding 3) | **Proxy Divergence Guard:** If T-Bill yield trend diverges > 2 std deviations from the last 5-day SOFR/T-Bill correlation, flag ILI as `DISLOCATED` and suppress automated signals. Record event in `proxy_divergence_events` table. Clear when official SOFR published and correlation restored. |
| Polygon WebSocket disconnect        | Auto-reconnect (1s to 60s). If > 5 min, switch to equity aggregates via direct API.                                                                                                                                                                                                               |
| Outlier detection                   | Any rate move > 50 bps/day flagged `SUSPECT`.                                                                                                                                                                                                                                                     |
| TimescaleDB unreachable             | Buffer up to 1M events in-memory. > 80% memory -> overflow to Chronicle Queue on dedicated storage (Finding 7).                                                                                                                                                                                   |
| Zero-variance component (Finding 6) | Component z-score returns `NaN` (not `0.0`). Weight redistributed proportionally among valid components. ILI status set to `DEGRADED_COMPONENT_STALE`.                                                                                                                                            |

---

## JDBC Configuration

- Single connection pool via HikariCP.
- Standard PostgreSQL JDBC driver (TimescaleDB is a PostgreSQL extension).
- Batched multi-row INSERT for tick data (configurable `batch-size`, default 500).
- Flush interval: configurable (default 500ms).

---

## Persistence Module Structure

```
persistence/
├── src/main/java/com/tickonomics/persistence/
│   ├── config/
│   │   └── TimescaleDbConfig.java        # HikariCP, Flyway config
│   ├── repository/
│   │   ├── TickDataRepository.java
│   │   ├── RateSnapshotRepository.java
│   │   ├── IliHistoryRepository.java
│   │   ├── ZscoreSeriesRepository.java
│   │   ├── CorrelationOutputRepository.java
│   │   ├── ProxyDivergenceEventRepository.java    # Finding 3
│   │   ├── ConfigSnapshotRepository.java
│   │   ├── SignalLogRepository.java
│   │   ├── BacktestResultRepository.java
│   │   ├── IngestionDlqRepository.java
│   │   ├── VirtualPortfolioRepository.java   # Phase 6
│   │   ├── SignalQualityReportRepository.java # Phase 6
│   │   ├── MarketEventRepository.java        # v4: Proposal 03
│   │   ├── DisasterAlertRepository.java       # v4: Proposal 03
│   │   ├── RegimeDetectionResultRepository.java # v4: Proposal 03
│   │   ├── OptimizationRunRepository.java     # v4: Proposal 04
│   │   ├── ActiveWeightRepository.java        # v4: Proposal 04
│   │   └── LiquidityStressResultRepository.java # v4: Proposal 02
│   │   ├── MarketGammaHistoryRepository.java      # v5: Proposal 06
│   │   ├── PhantomLiquidityRepository.java        # v5: Proposal 07
│   │   ├── ComovementSnapshotRepository.java      # v5: Proposal 07
│   │   ├── ToxicityScoreRepository.java           # v5: Proposal 07
│   │   ├── BehaviouralRiskRepository.java         # v5: Proposal 07
│   │   ├── TraderTypeEstimateRepository.java      # v5: Proposal 07
│   │   ├── ReproducibilityMetadataRepository.java # v5: Proposal 05
│   │   ├── RegulatoryComplianceRepository.java    # v5: Proposal 05
│   │   ├── StrategicRunEventRepository.java       # v5: Proposal 05
│   │   └── LiquidityComovementRepository.java     # v5: Proposal 07
│   └── entity/
│       └── (JPA entities matching tables above)
└── src/main/resources/db/migration/
    ├── V1__create_hypertables.sql
    ├── V2__create_relational_tables.sql
    ├── V3__create_continuous_aggregates.sql
    ├── V4__create_compression_retention.sql
    ├── V5__create_indexes.sql
    ├── V6__create_demo_tables.sql
    ├── V7__cdm_aligned_enums.sql                  # v2: CDM enum constraints
    ├── V8__create_precomputed_kpi_views.sql       # v3: pre-computed KPI aggregates
    ├── V9__add_anomaly_columns.sql                # v4: anomaly scoring columns
    ├── V10__add_idempotency_and_events.sql        # v4: idempotency keys, market events, disaster alerts
    ├── V11__add_optimization_tables.sql           # v4: optimization runs, active weights, liquidity stress
    ├── V12__add_v4_continuous_aggregates.sql      # v4: daily event summary
    └── V13__add_v4_indexes.sql                    # v4: indexes for v4 tables
    ├── V14__add_proposals_05_06_07.sql             # v5: proposals 05, 06, 07 tables and columns
    ├── V15__add_proposals_05_06_07_aggregates.sql  # v5: continuous aggregates for proposals 05, 07
    └── V16__add_proposals_05_06_07_indexes.sql     # v5: indexes for proposal 05, 06, 07 tables
    ├── V19__add_v6_factor_returns_index_snapshots.sql  # v6: factor returns, index snapshots, import tracker
```

---

## Validation

- [ ] All Flyway migrations apply cleanly to a fresh TimescaleDB instance.
- [ ] Hypertables created with correct chunk intervals.
- [ ] Continuous aggregates refresh successfully with `materialized_only = false`.
- [ ] Real-Time Aggregates return live data for the current minute (Finding 4).
- [ ] Compression policy compresses data older than 7 days.
- [ ] Retention policy drops data older than 90 days.
- [ ] `time_bucket_gapfill()` + `locf()` fills weekend/holiday gaps correctly.
- [ ] `candlestick_agg()` produces correct OHLCV from tick data.
- [ ] `proxy_divergence_events` table records dislocation incidents (Finding 3).
- [ ] `ili_history.active_weights` stores redistributed weights after dynamic weighting (Finding 6).
- [ ] `ili_history.proxy_divergence_status` tracks DISLOCATED/SUPPRESSED states (Finding 3).
- [ ] Integration tests with TimescaleDB testcontainers pass.
- [ ] V7 CDM enum constraints reject invalid `rate_type`, `component`, `direction`, `status`, and `metric` values (v2).
- [ ] `rate_snapshots.rate_type` values match CDM projection enums from `cdm/` module (v2).
- [ ] `zscore_series.component` values match CDM-aligned identifiers (v2).
- [ ] V8 pre-computed KPI views (`kpi_rolling_correlation`, `kpi_rolling_beta`, `kpi_zscore_daily`) refresh correctly (
  v3).
- [ ] Pre-computed rolling correlation matches TA-Lib `TA_CORREL` output within tolerance (v3).
- [ ] Pre-computed rolling beta matches TA-Lib `TA_BETA` output within tolerance (v3).
- [ ] V9 anomaly columns (`anomaly_score`, `is_suspect_anomaly`) added to `ili_history` and `rate_snapshots` without
  data loss (v4).
- [ ] V10 idempotency key unique index on `ingestion_dlq` allows NULLs and enforces uniqueness on non-NULL values (v4).
- [ ] V10 `market_events` hypertable created with 1-day chunk interval (v4).
- [ ] V10 `disaster_alerts` stores USGS/GDACS payloads with correct severity and alert_type values (v4).
- [ ] V10 `regime_detection_results` records method, regime, and confidence for each detection run (v4).
- [ ] V11 `optimization_runs` tracks BAYESIAN/FIREFLY/RAHF run lifecycle from RUNNING to COMPLETED/FAILED (v4).
- [ ] V11 `active_weights` stores JSONB weights with source attribution and backtest Sharpe (v4).
- [ ] V11 `liquidity_stress_results` captures scenario impact metrics and survival flag (v4).
- [ ] V12 `daily_event_summary` continuous aggregate refreshes and groups `market_events` by day and type (v4).
- [ ] V13 partial indexes (`idx_disaster_alerts_severity`, `idx_optimization_runs_status`, `idx_ili_history_anomaly`)
  return correct subsets (v4).
- [ ] V13 all new indexes used by EXPLAIN for representative queries (v4).
- [ ] V14 columns added to `backtest_results` (git_sha, dataset_hash, rds_score, parameter_slice_metadata) without data
  loss (v5).
- [ ] V14 `market_gamma_history` hypertable created for GEX snapshots (v5).
- [ ] V14 `config_snapshots.audit_reason` NOT NULL constraint enforced via application logic (v5).
- [ ] V14 `phantom_liquidity_metrics` hypertable tracks PLI per symbol (v5).
- [ ] V14 `order_flow_imbalance` and `canceled_volume` columns added to `tick_data` (v5).
- [ ] V14 `toxicity_scores` stores per-venue toxicity with class labels (v5).
- [ ] V14 `behavioural_risk_index` hypertable tracks BRI components per symbol (v5).
- [ ] V14 `strategic_run_events` records detected strategic runs per symbol (v5).
- [ ] V14 `reproducibility_metadata` stores audit-grade artifacts with RDS score (0-2) (v5).
- [ ] V14 `regulatory_compliance_reports` captures self-certification report data (v5).
- [ ] V15 `ili_robustness_heatmap` aggregate produces correct parameter sensitivity data (v5).
- [ ] V15 `daily_comovement_factor` and `daily_toxicity_summary` aggregates refresh correctly (v5).
- [ ] V16 new indexes used by EXPLAIN for representative queries (v5).
- [ ] V19 `factor_returns` hypertable created with 1-year chunk interval and accepts CDM factor_set/frequency/region combinations (v6).
- [ ] V19 `index_snapshots` hypertable created with 10-year chunk interval and stores Shiller S&P 500 data (v6).
- [ ] V19 `data_import_tracker` unique constraint on (symbol, source) prevents duplicate tracker entries (v6).
- [ ] V19 compression policies on `factor_returns` (2 years) and `index_snapshots` (50 years) apply correctly (v6).
- [ ] V7 `chk_rate_type_cdm` constraint accepts extended rate types: TBILL yield curve (1M-30Y), VIX, OIL_WTI, OIL_BRENT, GOLD (v6).

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|:--------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Real-Time Aggregates, Proxy Divergence Guard, Dynamic Weighting. Added `proxy_divergence_events` table, `active_weights` JSONB column.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| v2      | Added `V7__cdm_aligned_enums.sql` Flyway migration with CHECK constraints enforcing CDM-valid values for `rate_snapshots.rate_type`, `zscore_series.component`, `signal_log.direction`, `signal_log.status`, and `correlation_outputs.metric`. Added V7 to migration file list.                                                                                                                                                                                                                                                                                                                                                                                                          |
| v3      | Added `V8__create_precomputed_kpi_views.sql` (Proposal #3). Three new continuous aggregates: `kpi_rolling_correlation` (20d Pearson), `kpi_rolling_beta` (60d), `kpi_zscore_daily` (daily component rollup). Updated continuous aggregates table. Added V8 to migration file list.                                                                                                                                                                                                                                                                                                                                                                                                       |
| v4      | Added V9-V13 Flyway migrations from four consolidated proposals. V9: anomaly scoring columns on `ili_history` and `rate_snapshots` (Proposal 01). V10: idempotency keys on `ingestion_dlq`, `market_events` hypertable, `disaster_alerts`, `regime_detection_results` (Proposals 02, 03). V11: `optimization_runs`, `active_weights`, `liquidity_stress_results` (Proposals 04, 02). V12: `daily_event_summary` continuous aggregate. V13: indexes for v4 tables and anomaly queries. Updated hypertables, regular tables, continuous aggregates, persistence module structure, and validation checklist.                                                                                |
| v5      | Added V14-V16 Flyway migrations from three consolidated proposals. V14: reproducibility metadata, regulatory compliance, strategic runs, audit_reason on config_snapshots, market_gamma_history hypertable, phantom_liquidity_metrics, liquidity_comovement_snapshots, toxicity_scores, behavioural_risk_index, trader_type_estimates, order_flow_imbalance/canceled_volume/at_activity_proxy columns on tick_data. V15: ili_robustness_heatmap, daily_comovement_factor, daily_toxicity_summary continuous aggregates. V16: indexes for all new tables and columns. Updated hypertables, regular tables, continuous aggregates, persistence module structure, and validation checklist. |
| v5      | Added V17 Flyway migration from Proposals 08, 09: `sentiment_history` hypertable (dual-score: lexicon + BERT), `execution_type` column on `virtual_portfolio_trades` and `signal_log`, `pairs_trading_pairs` table for formation/trading period tracking, `markov_stop_calibrations` hypertable for dynamic stop calibration results. 5 new indexes.                                                                                                                                                                                                                                                                                                                                     |
| v5.1    | Added V18 Flyway migration from Proposals 10, 11, 12: `fed_balance_sheet` hypertable (monetary policy data), `tournament_results` table (multi-model benchmark storage), `greeks_sensitivity` hypertable (standardized risk primitives), `risk_premium_residuals` hypertable (Q-world P-vs-Q comparison). 5 new indexes.                                                                                                                                                                                                                                                                                                                                                                 |
| v6      | Data source migration: tick data source changed from Polygon WebSocket to Finnhub WebSocket. Equity price source changed from FMP/Intrinio (OpenBB secondary) to Yahoo Finance REST (primary) + Finnhub REST (fallback). Extended `chk_rate_type_cdm` with T-Bill yield curve tenors (1M, 6M, 1Y, 2Y, 5Y, 10Y, 30Y from FRED), VIX, OIL_WTI, OIL_BRENT, GOLD (from DataHub). Added V19 migration: `factor_returns` hypertable (Ken French 3/5 factor + momentum), `index_snapshots` hypertable (Shiller S&P 500 from DataHub), `data_import_tracker` table (incremental fetch state). Updated hypertables, regular tables, persistence module structure, and validation checklist. |

---

## Appendix: 04-database-tables.md

> *Merged from `gaps/04-database-tables.md` / `done/04-database-tables.md` during plan_v6 consolidation.*

# Database — 2 Missing Tables

**Plan ref:** `02-database-schema.md`

| #   | Table                  | Status      | Note                                                                        |
| --- | ---------------------- | ----------- | --------------------------------------------------------------------------- |
| 1   | `order_flow_imbalance` | **MISSING** | V16 creates an index referencing it, but no `CREATE TABLE` exists           |
| 2   | `evt_risk_metrics`     | **MISSING** | V22 creates `risk_evt_parameters` instead; no hypertable `evt_risk_metrics` |

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## 4. Database — 2 Missing Tables
**Plan ref:** `02-database-schema.md`

| #   | Table                  | Status      | Note                                                                        |
| --- | ---------------------- | ----------- | --------------------------------------------------------------------------- |
| 1   | `order_flow_imbalance` | **MISSING** | V16 creates an index referencing it, but no `CREATE TABLE` exists           |
| 2   | `evt_risk_metrics`     | **MISSING** | V22 creates `risk_evt_parameters` instead; no hypertable `evt_risk_metrics` |

---
