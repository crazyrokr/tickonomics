# Free Data Sources Integration — Implementation Report

**Date:** 2026-06-04
**Branch:** feature/dataset-sources
**Plan Reference:** `docs/plan_v5/FREE_DATA_SOURCES_INTEGRATION_PLAN.md` v1.3

---

## Summary

All 8 phases of the Free Data Sources Integration Plan have been implemented across 9 commits. The platform now uses **zero paid data sources**, replacing Polygon.io subscriptions with 7 free alternatives and filling 12 identified data gaps.

**Totals:** 74 files changed, +4,211 / -367 lines, 80+ new test cases.

---

## Commits

| # | SHA | Type | Description |
|---|:----|:-----|:------------|
| 1 | `faf78a5` | feat | Yahoo Finance + Finnhub equity price clients |
| 2 | `9f1c899` | feat | Finnhub WebSocket + Yahoo Options |
| 3 | `f9bbacd` | feat | Finnhub News + Fed RSS feeds |
| 4 | `92f9222` | feat | Ken French factor returns ingestion |
| 5 | `5521926` | feat | FRED yield curve extension |
| 6 | `06e2435` | feat | Alpha Vantage historical + commodity data |
| 7 | `b553c66` | feat | DataHub historical CSV backfill |
| 8 | `5089ad1` | chore | Remove deprecated Polygon and OpenBB dependencies |
| 9 | `2ed2177` | docs | ADR-012 architecture decision record |

---

## Phase Details

### Phase 1 — Replace Paid Equity Data (P0)

**Commit:** `faf78a5`
**Files:** 14 new

| Component | Files |
|:----------|:------|
| CDM raw models | `YahooOhlcv`, `FinnhubQuote` |
| CDM adapters | `YahooEquityCdmAdapter`, `FinnhubEquityCdmAdapter` |
| Ingestion clients | `EquityPriceClient` (interface), `YahooFinanceClient`, `FinnhubEquityClient`, `EquityPriceScheduler` |
| Configuration | `application.yml` — equity-price, yahoo-finance, finnhub, resilience4j retry instances |
| Tests | 19 tests: client parsing (valid/null/empty/malformed), adapter mapping, scheduler failover logic |

### Phase 2 — Replace Paid WebSocket & Options (P0/P1)

**Commit:** `9f1c899`
**Files:** 11 new

| Component | Files |
|:----------|:------|
| API contract | `EquityWsClient` (replaces `PolygonWsClient`) |
| CDM raw models | `FinnhubTrade`, `YahooOptionContract` |
| CDM adapters | `WsTradeCdmAdapter`, `YahooOptionsCdmAdapter` |
| Ingestion clients | `FinnhubWsClient` (real-time trades with reconnect backoff), `YahooOptionsClient` (chain data with Greeks) |
| Tests | 12 tests: WS subscribe/unsubscribe/trade parsing/error, options chain parsing/filtering, adapter type mapping |

### Phase 3 — News & Sentiment Sources (P1)

**Commit:** `f9bbacd`
**Files:** 10 new

| Component | Files |
|:----------|:------|
| CDM models | `NewsArticle` raw model, `NewsArticleCdmAdapter` |
| Ingestion clients | `NewsIngestionClient` (interface), `FinnhubNewsClient`, `FedRSSClient` (XML parsing), `NewsScheduler` |
| Configuration | application.yml — news, fed-rss, fedRssApi retry |
| Tests | 10 tests: Finnhub news JSON parsing, Fed RSS XML parsing (valid/empty title/malformed/missing date), adapter verification |

### Phase 4 — Ken French Factor Returns (P1)

**Commit:** `92f9222`
**Files:** 9 new

| Component | Files |
|:----------|:------|
| CDM models | `FactorSet` enum, `FactorReturn` record, `FrenchFactorRow` raw model |
| CDM adapter | `FrenchFactorCdmAdapter` (percentage/100 conversion, -99.99 sentinel sanitization) |
| Ingestion clients | `FrenchFactorClient` (ZIP download + CSV parse), `FrenchFactorScheduler` |
| Persistence | V29 Flyway migration — `factor_returns` hypertable (1-year chunks, 2-year compression) |
| Tests | 8 tests: 3-factor/5-factor/momentum CSV parsing, missing value sanitization, empty CSV, invalid input, percentage conversion |

### Phase 5 — FRED Yield Curve Extension (P2)

**Commit:** `5521926`
**Files:** 6 changed

| Component | Change |
|:----------|:-------|
| `FredClient` | SERIES_IDS expanded from 5 to 13 (added DGS1MO through DGS30) |
| `RateType` enum | Added TBILL_1M, TBILL_6M, TBILL_1Y, TBILL_2Y, TBILL_5Y, TBILL_10Y, TBILL_30Y |
| `CdmInstrumentMapper` | Mappings for 8 new yield curve FRED series |
| Persistence | V30 Flyway migration — extended `chk_rate_type_cdm` CHECK constraint |
| Tests | Existing tests updated with yield curve series test cases in parameterized `@Unroll` |

### Phase 6 — Alpha Vantage Historical & Commodity Data (P2)

**Commit:** `06e2435`
**Files:** 9 new

| Component | Files |
|:----------|:------|
| CDM models | `AlphaVantageDailyBar` raw model, `AlphaVantageCdmAdapter` |
| `InstrumentType` | Extended with COMMODITY_GOLD, COMMODITY_OIL, COMMODITY_GAS, COMMODITY_COPPER, COMMODITY_WHEAT, COMMODITY_CORN |
| Ingestion clients | `AlphaVantageClient` (rate-limit-aware, 12.1s inter-call delay), `AlphaVantageScheduler` (full load on first run) |
| Persistence | V31 Flyway migration — `data_import_tracker` table, `index_snapshots` hypertable |
| Configuration | application.yml — alpha-vantage.*, alphaVantageApi retry |
| Tests | 8 tests: adjusted OHLCV parsing, null response, rate limit info, zero-value skip, commodity parsing, adapter mapping |

### Phase 7 — DataHub Historical Backfill (P1/P2)

**Commit:** `b553c66`
**Files:** 13 new

| Component | Files |
|:----------|:------|
| CDM models | `ShillerSp500Row`, `DataHubPriceRow` raw models |
| CDM adapters | `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter` (WTI/Brent), `GoldPriceCdmAdapter` |
| `RateType` | Extended with VIX, OIL_WTI, OIL_BRENT, GOLD |
| Ingestion clients | `DataHubBackfillClient` (5 CSV datasets), `DataHubBackfillScheduler` (startup full-load + monthly incremental) |
| Persistence | V32 Flyway migration — extended `chk_rate_type_cdm` with VIX/oil/gold types |
| Configuration | application.yml — datahub.* namespace |
| Tests | 13 tests: Shiller CSV (valid/non-numeric/empty), VIX/oil/gold CSV (valid/zero-value/monthly/malformed), all 5 adapter mappings |

### Phase 8 — Cleanup & Removal

**Commit:** `5089ad1`
**Files:** 7 changed (4 deleted, 3 modified)

**Deleted:**
- `docker-compose.openbb.yml` — OpenBB sidecar (never had client code)
- `FederationDataClient.java` — never implemented interface
- `PolygonWsClient.java` — replaced by `EquityWsClient`
- `DefaultPolygonWsClient.java` + `PolygonWsConfig.java` — replaced by `FinnhubWsClient`
- `DefaultPolygonWsClientSpec.groovy` — old Polygon test

**Updated:**
- `.env.example` — removed `POLYGON_API_KEY`, added `FRED_API_KEY`, `FINNHUB_API_KEY`, `ALPHAVANTAGE_API_KEY`

---

## New CDM Types

### Raw Models (11)

| Model | Source | Purpose |
|:------|:-------|:--------|
| `YahooOhlcv` | Yahoo Finance | OHLCV bars |
| `FinnhubQuote` | Finnhub | Real-time equity quotes |
| `FinnhubTrade` | Finnhub WS | Normalized WebSocket trade event |
| `YahooOptionContract` | Yahoo Finance | Options chain contract data |
| `NewsArticle` | Finnhub / Fed RSS | News/speech article |
| `FrenchFactorRow` | Ken French | Parsed CSV factor row |
| `AlphaVantageDailyBar` | Alpha Vantage | Adjusted daily OHLCV |
| `ShillerSp500Row` | DataHub | S&P 500 monthly (1871–present) |
| `DataHubPriceRow` | DataHub | Generic rate/price row (VIX, oil, gold) |

### CDM Adapters (9)

`YahooEquityCdmAdapter`, `FinnhubEquityCdmAdapter`, `WsTradeCdmAdapter`, `YahooOptionsCdmAdapter`, `NewsArticleCdmAdapter`, `FrenchFactorCdmAdapter`, `AlphaVantageCdmAdapter`, `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter`, `GoldPriceCdmAdapter`

### Enums Extended

| Enum | Before | After | New Values |
|:-----|:-------|:------|:-----------|
| `RateType` | 10 | 22 | TBILL_1M/6M/1Y/2Y/5Y/10Y/30Y, VIX, OIL_WTI, OIL_BRENT, GOLD |
| `InstrumentType` | 9 | 15 | COMMODITY_GOLD/OIL/GAS/COPPER/WHEAT/CORN |
| `FactorSet` | — | 5 | FACTOR_3, FACTOR_5, MOMENTUM, ST_REVERSAL, LT_REVERSAL |

---

## Flyway Migrations

| Migration | Table(s) | Purpose |
|:----------|:---------|:--------|
| V29 | `factor_returns` | Ken French factor data (1-year chunks, 2-year compression) |
| V30 | `rate_snapshots` | Extended CHECK constraint for yield curve tenors |
| V31 | `data_import_tracker`, `index_snapshots` | Import state tracking + Shiller index hypertable (10-year chunks, 50-year compression) |
| V32 | `rate_snapshots` | Extended CHECK constraint for VIX, OIL_WTI, OIL_BRENT, GOLD |

---

## Configuration Namespaces Added

| Namespace | Keys | Default |
|:----------|:-----|:--------|
| `monitor.equity-price` | enabled, poll-interval-ms, symbols | 6h, SPY/QQQ/IWM/TLT/HYG/GLD |
| `monitor.yahoo-finance` | enabled, base-url, connect-timeout, read-timeout | yahoo.com, 5s/30s |
| `monitor.finnhub` | api-key, rest-enabled, rest-url, ws-enabled, ws-url, ws-reconnect-backoff-max, symbols, news-enabled | free key, wss://ws.finnhub.io |
| `monitor.news` | enabled, poll-interval-ms | 6h |
| `monitor.fed-rss` | enabled, speeches-url, fomc-url, poll-interval-ms | fed.gov RSS, 1h |
| `monitor.ken-french` | enabled, base-url, poll-interval-ms | dartmouth.edu, 24h |
| `monitor.yield-curve` | enabled | true |
| `monitor.alpha-vantage` | enabled, api-key, base-url, poll-interval-ms, symbols, commodities, rate-limit-delay-ms, initial-full-load | 12.1s delay, daily |
| `monitor.datahub` | enabled, base-url, startup-full-load, incremental-check-cron | monthly cron |

---

## Resilience4j Retry Instances Added

| Instance | Max Attempts | Wait Duration | Backoff |
|:---------|:-------------|:--------------|:--------|
| `yahooFinanceApi` | 3 | 2s | Exponential (2x) |
| `finnhubApi` | 3 | 1s | Exponential (2x) |
| `fedRssApi` | 3 | 2s | Exponential (2x) |
| `alphaVantageApi` | 3 | 12s | Exponential (2x) |

---

## Test Coverage

| Module | New Tests | Status |
|:-------|:----------|:-------|
| cdm (adapters) | 22 | All passing |
| ingestion (equity) | 19 | All passing |
| ingestion (ws) | 6 | All passing |
| ingestion (options) | 5 | All passing |
| ingestion (news) | 10 | All passing |
| ingestion (factor) | 8 | All passing |
| ingestion (alphavantage) | 7 | All passing |
| ingestion (datahub) | 8 | All passing |
| cdm (mapper) | 13 new rows | All passing |
| **Total** | **~80+** | **All passing** |

---

## Architecture Decision Record

Documented in `docs/adr/ADR-012-free-data-sources.md`.
