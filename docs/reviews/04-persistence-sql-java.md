# Code Review: Persistence Layer (Java + SQL)

**Date:** 2026-06-14
**Component:** `persistence/` — TimescaleDB schema, JDBC repositories, Flyway migrations
**Files reviewed:** 35 SQL migrations (V1-V35), 17 Java entities, 18 repositories, 1 config
**Reviewer:** Automated code review

---

## Executive Summary

This persistence layer is a deliberately JPA-free TimescaleDB-backed data access module using Java records for entities and `NamedParameterJdbcTemplate` for data access. The architecture is sound in its separation of concerns. However, **all financial values are stored as `DOUBLE PRECISION`** with zero `DECIMAL`/`BigDecimal` usage, **zero `@Transactional` annotations** exist across all 18 repositories, and **compression/retention policies are missing** on the majority of hypertables.

**Severity distribution:**
- 🔴 Critical: 4
- 🟠 High: 7
- 🟡 Medium: 7
- 🟢 Low: 3

---

## 1. Critical Findings

### 1.1 Double Precision for All Financial Values

**Files affected:** ALL entity Java records AND ALL SQL CREATE TABLE statements
**Severity:** 🔴 Critical
**Category:** Financial Precision

Every entity uses Java `double` and SQL `DOUBLE PRECISION` for financial values. Zero use of `BigDecimal` or `NUMERIC`/`DECIMAL` anywhere.

**Examples:**
- `TickData.price` (market price), `RateSnapshot.value` (interest rate)
- `VirtualPortfolioPosition.entryPrice`, `currentPrice`, `unrealizedPnl`
- `VirtualPortfolioTrade.fillPrice`, `commission`, `slippage`, `realizedPnl`
- Option chain: `strike`, `bid`, `ask`, `last_price`, `delta`, `gamma`, `theta`, `vega`, `rho`, `implied_vol`
- ALL columns in `factor_returns`, `macro_shock_irfs`, `risk_evt_parameters`, `quantile_coefficients`

**Risk:**
1. Cumulative PnL error: million trades × 1-cent rounding = $10,000 error
2. Equality comparisons of prices (`WHERE price = 100.50`) unreliable
3. Decimal fractions (0.01) cannot be represented exactly
4. Greek calculations require high precision

**Recommendation:**
- Trade financials → `NUMERIC(20,8)` / `BigDecimal`
- Prices → `NUMERIC(16,4)` for equities, `NUMERIC(20,8)` for FX
- Option strike → `NUMERIC(16,4)` — must be exact
- Interest rates → `NUMERIC(10,6)`
- Computed statistics (Sharpe, win rate) → `DOUBLE PRECISION` is acceptable

### 1.2 Zero Transaction Management

**Files affected:** ALL 18 repository classes
**Severity:** 🔴 Critical

Zero occurrences of `@Transactional` on any method or class. All `save()`, `saveAll()`, `update*()`, `deactivate*()` execute without transaction boundaries.

**Affected operations:**
- `ModelArtifactRepository.save()` + `deactivateOlderVersions()` should be atomic
- `TickDataRepository.saveAll()` / `RateSnapshotRepository.saveAll()`: batch updates auto-committed individually
- `VirtualPortfolioPosition.close()` + `VirtualPortfolioTradeRepository.save()` in caller are non-atomic

**Fix:** Add `@Transactional(readOnly = true)` at class level, override with `@Transactional` on write methods.

### 1.3 Missing Compression on Most Hypertables

**Severity:** 🔴 Critical

**Compression enabled ONLY on:** `tick_data` (V4), `factor_returns` (V29), `index_snapshots` (V31)

**Missing from 20+ hypertables:** `rate_snapshots`, `ili_history`, `zscore_series`, `correlation_outputs`, `alpha_signals`, `market_events`, `option_chain_snapshots`, `market_gamma_history`, `phantom_liquidity_metrics`, `liquidity_comovement_snapshots`, `toxicity_scores`, `behavioural_risk_index`, `sentiment_history`, `greeks_sensitivity`, `risk_premium_residuals`, `macro_shock_irfs`, `risk_evt_parameters`, `quantile_coefficients`, `synergy_entropy_matrix`, `universe_stats_history`, `volatility_forecasts`

**Risk:** Uncompressed time-series data is 5-10x larger, causing higher storage costs and slower full-scan queries.

### 1.4 Missing Retention Policies on Most Hypertables

**Severity:** 🔴 Critical

**Retention set ONLY on:** `tick_data` (90 days, V4), `volatility_forecasts` (365 days, V33)

**Risk:** All other hypertables grow unbounded, eventually causing disk-full failures.

---

## 2. High Severity Findings

### 2.1 Silent NULL-to-Zero Conversion in Row Mappers

**Files:** `CorrelationOutputRepository.java:45-48`, `ZscoreSeriesRepository.java`
**Severity:** 🟠 High

```java
rs.getDouble("correlation"),   // NULL → 0.0 silently!
rs.getDouble("p_value"),        // NULL → 0.0 — most significant possible p-value!
rs.getInt("sample_size"),       // NULL → 0
```

A NULL p_value (computation failed) becomes `0.0` — the most significant possible value. A NULL correlation is indistinguishable from true zero.

**Contrast with correct pattern used elsewhere:**
```java
rs.getObject("expected_move") != null ? rs.getDouble("expected_move") : null
```

### 2.2 FlywayMigrationStrategy Bean Is a No-Op

**File:** `TimescaleDbConfig.java`
**Severity:** 🟠 High

```java
@Bean
public FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> {
        Flyway.configure().baselineOnMigrate(true).load();  // Throwaway instance!
        flyway.migrate();  // Auto-configured bean, baselineOnMigrate never set
    };
}
```

`baselineOnMigrate(true)` is set on a throwaway `Flyway` instance. The auto-configured bean defaults to `false`.

**Fix:** `flyway.setBaselineOnMigrate(true)` before `flyway.migrate()`.

### 2.3 LIKE Query with Leading Wildcard on JSONB::text

**File:** `BacktestResultRepository.java:76`
**Severity:** 🟠 High

```sql
WHERE strategy_config::text LIKE :namePattern  -- namePattern = "%" + strategyName + "%"
```

1. `::text` includes JSON braces/quotes — matches within JSON representation, likely unintended
2. Leading `%` prevents any B-tree index usage
3. Full table scan on hypertable with potentially thousands of results

**Fix:** Use `strategy_config->>'name' = :namePattern` or `strategy_config @> '{"name": "..."}'::jsonb` with GIN index.

### 2.4 Option Strike Price in PRIMARY KEY Uses DOUBLE PRECISION

**File:** `V21__create_option_chain_snapshots.sql`
**Severity:** 🟠 High

```sql
PRIMARY KEY (time, symbol, strike, expiry, option_type)
```

`strike` is `DOUBLE PRECISION`. Floating-point equality in PK is unreliable — same strike (150.00) could produce duplicate rows.

**Fix:** Change to `NUMERIC(16,4)`.

### 2.5 GeneratedKeyHolder.getKey() Null-Pointer Risk

**Files:** `SignalLogRepository.java`, `BacktestResultRepository.java`, `VirtualPortfolioPositionRepository.java`, `VirtualPortfolioTradeRepository.java`
**Severity:** 🟠 High

```java
return keyHolder.getKey().longValue();  // NPE if getKey() returns null
```

If INSERT fails silently (constraint violation, trigger rejection), `getKey()` returns null.

**Fix:** Add null check with descriptive error message.

### 2.6 V28 CREATE VIEW Not Idempotent

**File:** `V28__create_evt_risk_metrics_view.sql`
**Severity:** 🟠 High

```sql
CREATE VIEW evt_risk_metrics AS ...
```

Should be `CREATE OR REPLACE VIEW`. Will fail on migration re-apply.

### 2.7 No Pagination on Large Result Sets

**Files:** All `findBy*AndTimeBetween` methods across all repositories
**Severity:** 🟠 High

For hypertables with millions of rows (tick data at sub-second granularity), unbounded `List<T>` returns risk OOM.

---

## 3. Medium Severity Findings

### 3.1 IliHistoryRepository Duplicate RowMapper Code
**Severity:** 🟡 Medium
11-line row-mapping lambda duplicated across `findByTimeBetween`, `findLatest`, `findLatestN`.

### 3.2 Chunk Time Intervals Need Validation
- `option_chain_snapshots` at 1 day: full chains with 100+ strikes × 10+ expiries → very large daily chunks
- `index_snapshots` at 10 years: ~120 rows/chunk for monthly data — too small
- `factor_returns` at 1 year: ~250 rows/chunk — too small
Target 1-100 MB per chunk per TimescaleDB best practices.

### 3.3 BacktestResults PK Ordering Suboptimal
`PRIMARY KEY (id, run_at)` where `id` alone is already unique. Time-range queries would benefit from `(run_at, id)`.

### 3.4 Non-Concurrent Index Creation
Later migrations (V13, V16, V25, V27) use plain `CREATE INDEX` which blocks reads/writes. Should use `CREATE INDEX CONCURRENTLY`.

### 3.5 SignalQualityReportRepository Returns Untyped Maps
Returns `Optional<Map<String, Object>>` instead of typed entity record. Bypasses compile-time safety.

### 3.6 No Symbol/Identifier Format Validation
No validation that `symbol` conforms to ticker, ISIN, or CUSIP format.

### 3.7 V30/V32 Both Recreate Same Constraint
Both drop and recreate `chk_rate_type_cdm` to add new rate types. Functionally correct but indicates the constraint should have been more inclusive from the start.

---

## 4. Low Severity Findings

### 4.1 Inconsistent Null-Handling Patterns
Three different patterns for nullable Double columns. Standardize on `rs.getObject("col") != null ? rs.getDouble("col") : null`.

### 4.2 Hardcoded 90-Day Tick Data Retention
Insufficient for strategies requiring multi-year lookback. Make configurable.

### 4.3 TickData Conditions Array Type Mismatch
SQL: `INTEGER[]`, Java: `int[]`. `getArray().getArray()` cast to `int[]` is unchecked.

---

## 5. Positive Observations

1. **No-JPA design**: `NamedParameterJdbcTemplate` with Java records avoids Hibernate complexity. Correct choice for time-series with TimescaleDB custom aggregates.
2. **Java records for entities**: Immutable, value-based with compact constructor validation.
3. **Proper hypertable design**: Composite PKs including partition column, continuous aggregates for pre-computed views.
4. **V35 compression toggle pattern**: Correctly drops compression before DDL, re-enables after.
5. **Batch operations**: `saveAll()` and `saveAllIdempotent()` use `batchUpdate`.
6. **CDM-aligned CHECK constraints**: Rate types and metrics align with common data model.

---

## Summary Table

| # | Severity | Category | Finding |
|---|----------|----------|---------|
| 1 | 🔴 Critical | Financial Precision | All values DOUBLE PRECISION, no DECIMAL/BigDecimal |
| 2 | 🔴 Critical | Transactions | Zero @Transactional annotations |
| 3 | 🔴 Critical | SQL Performance | Compression missing on 20+ hypertables |
| 4 | 🔴 Critical | SQL Operations | Retention missing on 20+ hypertables |
| 5 | 🟠 High | Data Integrity | NULL-to-zero in CorrelationOutput/ZscoreSeries mappers |
| 6 | 🟠 High | Migration Safety | Flyway baselineOnMigrate is no-op |
| 7 | 🟠 High | SQL Performance | LIKE with leading % on JSONB::text |
| 8 | 🟠 High | Financial Precision | Option strike in PK as DOUBLE PRECISION |
| 9 | 🟠 High | Stability | getKey() NPE in 4 repositories |
| 10 | 🟠 High | Migration Idempotency | V28 view not idempotent |
| 11 | 🟠 High | Stability | No pagination on time-range queries |
| 12 | 🟡 Medium | Clean Code | Duplicate row-mapper in IliHistoryRepository |
| 13 | 🟡 Medium | SQL Performance | Chunk intervals unchecked |
| 14 | 🟡 Medium | SQL Design | backtest_results PK ordering |
| 15 | 🟡 Medium | SQL Operations | Non-concurrent indexes |
| 16 | 🟡 Medium | Type Safety | SignalQualityReport untyped maps |
| 17 | 🟡 Medium | Data Quality | No ticker/ISIN validation |
| 18 | 🟡 Medium | Migration Quality | V30/V32 constraint churn |
| 19 | 🟢 Low | Consistency | Null-handling divergence |
| 20 | 🟢 Low | Configuration | 90-day retention hardcoded |
| 21 | 🟢 Low | Type Safety | TickData array cast unsafe |

---

## Recommended Action Plan

**Immediate (data integrity):**
1. Fix NULL-to-zero in CorrelationOutputRepository and ZscoreSeriesRepository
2. Fix FlywayMigrationStrategy bean
3. Add `@Transactional` to all write methods
4. Fix V28 view to use `CREATE OR REPLACE`

**Short term (financial precision):**
5. Audit monetary columns to `NUMERIC`/`BigDecimal`
6. Fix option chain strike to `NUMERIC`

**Medium term (operations):**
7. Add compression and retention policies to all hypertables
8. Add pagination to time-range queries
9. Fix BacktestResultRepository LIKE query
10. Add integration tests with TimescaleDB Testcontainers

**Long term:**
11. Standardize null-handling patterns
12. Consider dedicated `@Service` layer for cross-repository transactions
