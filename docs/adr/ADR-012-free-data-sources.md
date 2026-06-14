# ADR-012: Free Data Sources Integration

**Status:** Implemented; amended by [ADR-022](ADR-022-foundation-realignment.md) (2026-06-14) — free-only constraint relaxed to add a single paid point-in-time source (D5) so the platform can satisfy survivorship-free, point-in-time cross-sectional requirements. All other aspects of this ADR remain in effect.
**Date:** 2026-06-04
**Decision:** Replace all paid data dependencies (Polygon.io) with free alternatives, remove unimplemented OpenBB sidecar, and fill 12 identified data gaps using free APIs.

## Context

The platform relied on two paid Polygon.io subscriptions ($29–$199/mo each) for equity tick data and options snapshots. The OpenBB sidecar container was referenced in Docker Compose but had zero client code. Twelve data gaps were identified across the platform: equity historical OHLCV, real-time ticks, options chains, news/sentiment, yield curve, Fed speeches, Fama-French factors, benchmark index, VIX, deep historical equity, commodities, and fundamentals.

## Decision

Replace all paid sources with seven free data providers:

1. **Yahoo Finance** — Equity OHLCV (primary) and options chains (free, no API key)
2. **Finnhub** — Real-time equity WebSocket, REST fallback, and market news (free API key)
3. **Federal Reserve RSS** — FOMC statements and Fed speeches (free, no auth)
4. **Ken French Data Library** — Fama-French factor returns (free, static CSV)
5. **FRED** — Yield curve extension via existing client (free API key)
6. **Alpha Vantage** — Deep historical equity OHLCV 20+ years and commodities (free API key)
7. **DataHub** — Historical CSV backfill: S&P 500 Shiller, VIX, oil, gold (free, CDN)

## Architecture

Each source follows the established pattern:
- **Ingestion client** (`@Component`, `@Scheduled`, `RestClient`) fetches raw data
- **CDM adapter** (`CdmAdapter<T, R>`) maps source-specific types to canonical CDM records
- **TimescaleDbWriter** writes CDM records through the existing buffer pipeline with idempotency guards

New CDM types added: `FactorSet`, `FactorReturn`, `YahooOhlcv`, `FinnhubQuote`, `FinnhubTrade`, `YahooOptionContract`, `NewsArticle`, `AlphaVantageDailyBar`, `ShillerSp500Row`, `DataHubPriceRow`, `FrenchFactorRow`. RateType extended from 10 to 22 values. InstrumentType extended from 9 to 15 values.

New Flyway migrations: V29 (factor_returns), V30 (yield curve enums), V31 (import tracker + index_snapshots), V32 (VIX/oil/gold rate types).

## Consequences

**Positive:**
- Zero recurring data costs for development and demo deployment
- OpenBB sidecar removed (one fewer container to manage)
- 12 data gaps filled, enabling backtesting over 100+ year horizons
- Fama-French 5-factor regression now has actual data (was half-implemented)
- Each source has at least one fallback (Yahoo/Finnhub for equity, FRED/DataHub for historical)

**Negative:**
- Yahoo Finance is unofficial (no SLA) — mitigated by Finnhub fallback and circuit breakers
- Finnhub free tier has 15-min delay on REST quotes — mitigated by WebSocket for real-time
- Alpha Vantage free tier limited to 5 req/min — mitigated by 12.1s inter-call delay and daily-only polling
- DataHub updates monthly, not real-time — acceptable for historical backfill use case

## Files Changed

- 30+ new Java source files across ingestion/, cdm/, api-contracts/
- 4 Flyway SQL migrations (V29–V32)
- 80+ new test cases across all affected modules
- 7 files deleted (Polygon client, OpenBB compose, FederationDataClient)
- application.yml extended with 8 new configuration namespaces
