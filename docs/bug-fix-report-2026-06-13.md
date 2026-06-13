# Bug Fix Report — `./gradlew clean build test pytest` (2026-06-13)

## Objective

Restore a green build for `./gradlew clean build test pytest`. The integration-tests
module (`ApplicationStartupIT`) failed to load the Spring `ApplicationContext`, which also
blocked the Java build task. Python tests were unaffected.

## Root cause (unifying)

A latent Flyway failure aborted context startup early in the boot sequence, so Spring never
reached bean wiring. Fixing the migration unmasked two further, independent wiring defects that
had been silently present since the affected `develop`-branch features landed. Each is fixed below.

Result after fixes: Java build + tests pass, `ApplicationStartupIT` (context load + health
endpoint) passes, pytest **377 passed, 3 skipped, 1 xpassed**.

---

## B1 — Flyway migration V29/V31 use invalid TimescaleDB `compress_after` syntax (BLOCKER)

**Files**:
- `persistence/src/main/resources/db/migration/V29__create_factor_returns.sql`
- `persistence/src/main/resources/db/migration/V31__add_commodity_and_import_tracker.sql`

**Root cause**: Both migrations used `ALTER TABLE <t> SET (compress_after = '<interval>')`.
`compress_after` is not a valid table storage parameter in the test image
(`timescale/timescaledb:latest-pg16`), so Flyway aborted with
`ERROR: unrecognized parameter "compress_after"` at migration V29, halting startup.

**Fix**: Adopted the proven pattern already used in `V4__create_compression_retention.sql` —
enable compression via `timescaledb.compress` with `segmentby`/`orderby`, then register a policy
with `add_compression_policy(..., compress_after => INTERVAL ...)`.

```sql
ALTER TABLE factor_returns SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'factor_set, frequency, region',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('factor_returns', compress_after => INTERVAL '2 years');
```

`index_snapshots` follows the same shape with `compress_segmentby = 'index_type'` and a
`50 years` policy.

---

## B2 — Six CDM adapters injected by Spring but never registered as beans (BLOCKER)

**File**: `ingestion/src/main/java/com/tickonomics/ingestion/config/HttpClientConfig.java`

**Root cause**: Clients/schedulers (`AlphaVantageClient`, `EquityPriceScheduler`,
`FrenchFactorClient`, `NewsScheduler`, `YahooOptionsClient`) constructor-inject their
`*CdmAdapter` dependencies. The `cdm` module is intentionally Spring-free, so those adapters are
not `@Component`s; only `FredCdmAdapter` and `NyFedCdmAdapter` were registered as `@Bean`
(in `HttpClientConfig`). The remaining six were missing, so
`No qualifying bean of type '...CdmAdapter'` aborted context load. (Enabled in production by
`monitor.alpha-vantage.enabled` / `monitor.equity-price.enabled` in `application.yml`.)

**Fix**: Registered the six missing adapters as `@Bean` in `HttpClientConfig`, matching the
existing Fred/NyFed precedent (keeps the `cdm` module Spring-free):
`AlphaVantageCdmAdapter`, `YahooEquityCdmAdapter`, `FinnhubEquityCdmAdapter`,
`FrenchFactorCdmAdapter`, `NewsArticleCdmAdapter`, `YahooOptionsCdmAdapter`. All are stateless
no-arg mappers.

---

## B3 — Finnhub backoff config binds a duration string to a millisecond `int` (BLOCKER)

**File**: `app/src/main/resources/application.yml`

**Root cause**: `monitor.finnhub.ws-reconnect-backoff-max: 60s` is bound via
`@Value` into `private int reconnectBackoffMaxMs` (default `60000`). Spring cannot convert
`"60s"` to `int` (`NumberFormatException`), failing `finnhubWsClient` bean creation.

**Fix**: Set the value to `60000`, matching the field's millisecond contract, its default, and its
usage in `Math.min(reconnectDelayMs * 2, reconnectBackoffMaxMs)`.

---

## Verification

```
./gradlew clean build test pytest
...
BUILD SUCCESSFUL in 3m 10s
pytest: 377 passed, 3 skipped, 1 xpassed
```

## Files changed

- `persistence/src/main/resources/db/migration/V29__create_factor_returns.sql`
- `persistence/src/main/resources/db/migration/V31__add_commodity_and_import_tracker.sql`
- `ingestion/src/main/java/com/tickonomics/ingestion/config/HttpClientConfig.java`
- `app/src/main/resources/application.yml`
