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

| Table                 | Purpose                              | Chunk Interval | Retention                   |
|:----------------------|:-------------------------------------|:---------------|:----------------------------|
| `tick_data`           | Real-time tick data from Polygon     | 1 day          | 90 days, then auto-compress |
| `rate_snapshots`      | SOFR, EFFR, TGCR, BGCR, IORB, etc.   | 1 day          | Indefinite                  |
| `ili_history`         | ILI values over time                 | 1 day          | Indefinite                  |
| `zscore_series`       | Z-score normalized component values  | 1 day          | Indefinite                  |
| `correlation_outputs` | Rolling correlation and beta outputs | 1 day          | Indefinite                  |

### Regular Tables (Relational)

| Table                         | Purpose                                       | Retention             |
|:------------------------------|:----------------------------------------------|:----------------------|
| `config_snapshots`            | Before/after diffs on configuration changes   | Indefinite            |
| `alert_rules`                 | User-defined alert thresholds                 | Indefinite            |
| `signal_log`                  | Append-only signal history                    | 2 years, then archive |
| `backtest_results`            | Backtest run outputs                          | Indefinite            |
| `ingestion_dlq`               | Dead letter queue for failed ingestion events | 90 days               |
| `virtual_portfolio_positions` | Demo portfolio open positions (Phase 6)       | Indefinite            |
| `virtual_portfolio_trades`    | Demo portfolio trade history (Phase 6)        | Indefinite            |
| `signal_quality_reports`      | Daily signal quality metrics (Phase 6)        | Indefinite            |

### Continuous Aggregates

| Materialized View   | Source                              | Refresh Interval |
|:--------------------|:------------------------------------|:-----------------|
| `ohlcv_1min`        | `tick_data` via `candlestick_agg()` | Every 1 minute   |
| `ohlcv_1h`          | `tick_data` via `candlestick_agg()` | Every 1 hour     |
| `ohlcv_1d`          | `tick_data` via `candlestick_agg()` | Every 1 day      |
| `daily_kpi_summary` | ILI + KPI daily aggregates          | Every 1 day      |

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
                          'RRP', 'TGA', 'WALCL', 'TBILL_3M'));

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

- Equity prices from direct FMP/Intrinio API (or OpenBB as secondary): last price before 4:00 PM ET.
- FRED (direct client): daily close-aligned by definition.
- NY Fed (direct client): SOFR mapped to the prior business day's grid position.
- T-Bill proxy: daily yield from Federal Reserve H.15 (via direct NY Fed client).
- Polygon ticks: aggregated by continuous aggregates into OHLCV candles (real-time via
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
| TimescaleDB unreachable             | Buffer up to 1M events in-memory. > 80% memory → overflow to Chronicle Queue on dedicated storage (Finding 7).                                                                                                                                                                                    |
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
│   │   └── SignalQualityReportRepository.java # Phase 6
│   └── entity/
│       └── (JPA entities matching tables above)
└── src/main/resources/db/migration/
    ├── V1__create_hypertables.sql
    ├── V2__create_relational_tables.sql
    ├── V3__create_continuous_aggregates.sql
    ├── V4__create_compression_retention.sql
    ├── V5__create_indexes.sql
    ├── V6__create_demo_tables.sql
    └── V7__cdm_aligned_enums.sql        # v2: CDM enum constraints
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

---

## Changelog

| Version | Change                                                                                                                                                                                                                                                                          |
|:--------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | Applied analysis findings: Real-Time Aggregates, Proxy Divergence Guard, Dynamic Weighting. Added `proxy_divergence_events` table, `active_weights` JSONB column.                                                                                                               |
| v2      | Added `V7__cdm_aligned_enums.sql` Flyway migration with CHECK constraints enforcing CDM-valid values for `rate_snapshots.rate_type`, `zscore_series.component`, `signal_log.direction`, `signal_log.status`, and `correlation_outputs.metric`. Added V7 to migration file list. |
| v3      | Added `V8__create_precomputed_kpi_views.sql` (Proposal #3). Three new continuous aggregates: `kpi_rolling_correlation`, `kpi_rolling_beta`, `kpi_zscore_daily`. Updated continuous aggregates table.                                                                            |
