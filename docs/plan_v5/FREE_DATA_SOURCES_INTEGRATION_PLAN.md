# Free Data Sources Integration Plan

**Version:** 1.3
**Date:** 2026-06-03
**Scope:** Replace paid data dependencies with free alternatives; fill existing data gaps identified in Plan V5.

---

## 1. Executive Summary

The Tickonomics platform currently relies on **two paid Polygon.io subscriptions** (equity ticks via WebSocket, options snapshots via REST) for market data, while the OpenBB sidecar referenced throughout Plan V5 has **no client code** and adds operational burden. This plan proposes replacing all paid dependencies with free data sources, eliminating the OpenBB dependency entirely, and filling six identified data gaps using freely available APIs.

**Goals:**

- Zero recurring data costs for development and demo deployment.
- Remove the OpenBB sidecar container (simplify operations).
- Maintain data quality parity with current paid sources for ILI computation and demo portfolio.
- All proposed sources have free tiers with sufficient rate limits for the platform's polling cadence.
- **v1.1 addition:** Ken French Data Library provides free Fama-French factor returns (Rm-Rf, SMB, HML, RMW, CMA, Mom) for the existing factor regression, tournament benchmarking, and risk decomposition.
- **v1.2 addition:** Alpha Vantage provides free deep historical equity OHLCV (20+ years, split/dividend adjusted), commodities (gold, oil, gas, copper, wheat), and fundamental data (earnings, balance sheets) via a once-daily REST poll. Fills the backtesting history gap and adds commodities as a new asset class.
- **v1.3 addition:** DataHub provides free, no-auth, deep-historical CSV datasets for S&P 500 (Shiller, 1871–present with CAPE), VIX (1990–present daily), oil prices (WTI/Brent, 1986–present daily), and gold (1833–present monthly). One-time backfill via stable CSV URLs. Fills the historical benchmark index, volatility index, and deep commodity history gaps for backtesting and regime detection.

---

## 2. Current Data Source Inventory

### 2.1 Implemented — Free (Retain As-Is)

| Source | Client | Data | Rate Limit | API Key |
|:-------|:-------|:-----|:-----------|:--------|
| FRED REST API | `FredClient.java` | EFFR, RRP, TGA, WALCL, IORB, economic calendar | 120 req/min (free key) | Free signup |
| NY Fed REST API | `NyFedClient.java` | SOFR, TGCR, BGCR, Treasury rates | No published limit | None |
| USGS Earthquake | `DisasterAlertClient.java` | Earthquake events (mag >= 5.0) | No published limit | None |
| FRED Releases API | `EconomicCalendarClient.java` | Economic release calendar | 120 req/min (shared with FRED) | Free signup |

### 2.2 Implemented — Paid (Replace)

| Source | Client | Data | Cost | Replacement |
|:-------|:-------|:-----|:-----|:------------|
| Polygon WebSocket | `DefaultPolygonWsClient.java` | Real-time equity ticks | $29–$199/mo | Yahoo Finance / Finnhub |
| Polygon Options REST | `OptionsDataClient.java` | Options chain snapshots with Greeks | $29–$199/mo | Yahoo Finance options |

### 2.3 Referenced but Not Implemented (Remove)

| Source | Plan Reference | Status | Decision |
|:-------|:---------------|:-------|:---------|
| OpenBB Platform sidecar | Track 4 §3, Track 5 | Docker compose overlay exists, no client code | **Remove entirely** |

### 2.4 Identified Data Gaps (Fill)

| Gap | Consumers | Priority |
|:----|:----------|:---------|
| Equity/ETF historical OHLCV prices | `PaperTradingEngine`, `CorrelationEngine`, backtesting, dashboard | P0 |
| Real-time equity price ticks | ILI correlation analysis, signal generation | P0 |
| Options chain data (OI, IV, Greeks) | GEX monitor, risk guardrails | P1 |
| News / FOMC text for sentiment analysis | `SentimentAnalyzer` (Proposal 09), BRI computation | P1 |
| Treasury yield curve (multi-tenor) | Monetary policy sensitivity panel, Q-world fair value | P2 |
| Fed speeches / FOMC statements | FinBERT hawkish/dovish detection | P2 |
| Fama-French factor returns (Rm-Rf, SMB, HML, RMW, CMA, Mom) | `fama_french_regression()`, tournament service, risk decomposition, regime detection | P1 |
| Historical equity benchmark index (S&P 500 level, dividend, earnings, CAPE) | `WalkForwardValidator`, `CrossModelValidator`, regime detection, alpha evaluation | P1 |
| Volatility index (VIX daily OHLC) | `RegimeService`, `EvtRiskService`, cross-asset correlation | P1 |
| Deep historical equity OHLCV (20+ years, adjusted) | `WalkForwardValidator`, `CrossModelValidator`, backtesting, external validation | P2 |
| Commodities (gold, oil, gas, copper, wheat) | Portfolio diversification analysis, inflation hedging, ILI commodity sensitivity | P2 |
| Fundamental data (earnings, balance sheet, cash flow) | Value factor signals, earnings surprise detection | P2 |

---

## 3. Proposed Free Data Sources

### 3.1 Equity/ETF Historical & Real-Time Prices — P0

**Primary: Yahoo Finance (yfinance)**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}` |
| Auth | None (session cookie-based, no API key) |
| Rate Limit | ~2,000 req/hour (unofficial, varies) |
| Data | Historical OHLCV (1m–1mo intervals), real-time price quotes |
| Coverage | US equities, ETFs, indices |
| Latency | ~15 min delayed; real-time via `/v8/finance/chart` with `interval=1m` |
| Stability | Yahoo occasionally changes endpoints; requires header spoofing |

**Fallback: Finnhub**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://finnhub.io/api/v1/quote` (real-time), `/stock/candle` (OHLCV) |
| Auth | Free API key (signup required) |
| Rate Limit | 60 calls/min (free tier) |
| Data | Real-time quotes, historical candles (1–D intervals) |
| Coverage | US equities, forex, crypto |
| Cost | Free tier sufficient for ~10 symbols at 6h polling |

**Integration approach:**

```
┌─────────────────────────────────────────────────┐
│  EquityPriceClient (interface)                  │
│  ├── YahooFinanceClient (primary)               │
│  │   └── RestClient + session cookie mgmt       │
│  └── FinnhubClient (fallback)                   │
│      └── RestClient + API key                   │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  EquityPriceCdmAdapter                          │
│  └── Maps Yahoo/Finnhub response → CdmTick      │
└─────────────────────────────────────────────────┘
```

**Why both:** Yahoo Finance has no official API key or SLA — it can break without notice. Finnhub provides a stable, key-authenticated fallback with clear rate limits. For the platform's polling cadence (6h for historical, configurable for intraday), the 60 req/min free tier is ample.

### 3.2 Real-Time Equity Ticks — P0

**Primary: Finnhub WebSocket**

| Attribute | Value |
|:----------|:------|
| Endpoint | `wss://ws.finnhub.io` |
| Auth | Free API key (same key as REST) |
| Rate Limit | No WebSocket-specific limit on free tier |
| Data | Real-time trades (price, volume, timestamp, conditions) |
| Coverage | US equities |
| Latency | Near real-time (~0.5s) |

**Integration approach:**

Replace `DefaultPolygonWsClient` with `FinnhubWsClient` implementing the same `PolygonWsClient` interface (renamed to `EquityWsClient`). The existing WebSocket infrastructure (reconnection backoff, circuit breaker, tick-to-CDM adapter) is reused — only the connection handshake and message parsing changes.

```java
// Reuse existing interface, rename to be source-agnostic
public interface EquityWsClient {
    void connect();
    void subscribe(String symbol);
    void disconnect();
    boolean isConnected();
}
```

**Why not Yahoo Finance WebSocket:** Yahoo does not offer a public WebSocket API. Finnhub's free WebSocket is the only zero-cost real-time equity feed with trade-level granularity.

### 3.3 Options Chain Data — P1

**Primary: Yahoo Finance Options**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://query1.finance.yahoo.com/v7/finance/options/{symbol}` |
| Auth | None (same session as equity prices) |
| Rate Limit | Shared with equity (within Yahoo's unofficial limit) |
| Data | Full options chain: strikes, expiry dates, bid/ask, IV, OI, volume, Greeks |
| Coverage | US-listed equity and ETF options |
| Latency | ~15 min delayed |

**Integration approach:**

Replace `OptionsDataClient`'s Polygon REST calls with Yahoo Finance options endpoint. The existing `OptionsChainSnapshot` model and CDM adapter remain unchanged — only the HTTP target and response parser change.

### 3.4 News / FOMC Text for Sentiment — P1

**Primary: Finnhub Market News**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://finnhub.io/api/v1/news?category=general` |
| Auth | Free API key |
| Rate Limit | 60 calls/min (shared with equity) |
| Data | Market news headlines, summaries, source, timestamp, URL |
| Coverage | General financial news, categorized by topic |

**Secondary: Federal Reserve Board RSS Feeds**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://www.federalreserve.gov/feeds/speeches.xml`, `https://www.federalreserve.gov/feeds/press_monetary.xml` |
| Auth | None |
| Rate Limit | None (public RSS) |
| Data | FOMC statements, Fed speeches, press releases (full text) |
| Coverage | All Federal Reserve communications |

**Integration approach:**

```
┌─────────────────────────────────────────────────────┐
│  NewsIngestionClient (interface)                    │
│  ├── FinnhubNewsClient                              │
│  │   └── REST polling, 6h interval for general news │
│  └── FedRSSClient                                   │
│      └── RSS polling, 1h interval for FOMC/speeches │
└─────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  NewsText stored in sentiment_history hypertable     │
│  ├── FinBERT processes FOMC speeches → hawkish/dove  │
│  └── SALI lexicon scores general news headlines      │
└─────────────────────────────────────────────────────┘
```

### 3.5 Treasury Yield Curve (Multi-Tenor) — P2

**Primary: FRED (Existing Client Extension)**

| Attribute | Value |
|:----------|:------|
| Endpoint | Existing `FredClient` — add series |
| Auth | Existing FRED API key |
| Rate Limit | 120 req/min (existing) |
| Data | Yields at 1M, 3M, 6M, 1Y, 2Y, 3Y, 5Y, 7Y, 10Y, 20Y, 30Y |

**Series to add:**

| FRED Series ID | Tenor | Frequency |
|:---------------|:------|:----------|
| `DGS1MO` | 1-Month | Daily |
| `DGS3MO` | 3-Month | Daily |
| `DGS6MO` | 6-Month | Daily |
| `DGS1` | 1-Year | Daily |
| `DGS2` | 2-Year | Daily |
| `DGS5` | 5-Year | Daily |
| `DGS10` | 10-Year | Daily |
| `DGS30` | 30-Year | Daily |

**Integration approach:**

No new client needed. Extend `FredClient.FredSeriesConfig` with additional series IDs. The existing FRED polling, CDM adapter, and `TimescaleDbWriter` pipeline handles these transparently. Data stored in `rate_snapshots` with `rate_type` values matching CDM enums (`TBILL_1M`, `TBILL_3M`, etc.).

### 3.6 Fed Speeches / FOMC Full Text — P2

**Primary: Federal Reserve RSS Feeds** (same as §3.4 secondary)

Covered by the `FedRSSClient` above. FOMC statements and Fed speeches are the primary input for FinBERT hawkish/dovish classification.

**Additional: SEC EDGAR Full-Text Search**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://efts.sec.gov/LATEST/search-index?q=...` or `https://data.sec.gov/submissions/CIK={cik}.json` |
| Auth | None (User-Agent header required) |
| Rate Limit | 10 req/sec (stated policy) |
| Data | 10-K, 10-Q filings, insider trading (Form 4), institutional holdings (13F) |
| Coverage | All US public company filings |

SEC EDGAR is a P2 stretch goal for future regulatory compliance reports and insider trading signals. Not required for initial free-tier deployment.

### 3.7 Fama-French Factor Returns — P1

**Primary: Ken French Data Library (Dartmouth / Tuck)**

| Attribute | Value |
|:----------|:------|
| Base URL | `https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp/` |
| Auth | None — fully public, no API key |
| Rate Limit | None (static CSV files, monthly updated) |
| Data | Factor returns (Rm-Rf, SMB, HML, RMW, CMA, RF, Mom, ST Rev, LT Rev) and sorted portfolios |
| Frequency | Monthly and Daily |
| Coverage | US (1926–present), Developed, Emerging markets |
| Format | ZIP-compressed CSV; missing values coded as -99.99 or -999 |

**Key datasets:**

| File | Factors / Portfolios | Use Case |
|:-----|:---------------------|:---------|
| `F-F_Research_Data_Factors_CSV.zip` | Rm-Rf, SMB, HML, RF | 3-factor regression (existing) |
| `F-F_Research_Data_5_Factors_2x3_CSV.zip` | Rm-Rf, SMB, HML, RMW, CMA, RF | 5-factor regression (upgrade) |
| `Momentum_Factor_CSV.zip` | Mom (UMD) | Tournament momentum benchmark |
| `F-F_ST_Reversal_Factor_CSV.zip` | ST Rev | Short-term reversal analysis |
| `F-F_LT_Reversal_Factor_CSV.zip` | LT Rev | Long-term reversal analysis |
| `6_Portfolios_Formed_on_Size_and_BM_2x3_CSV.zip` | Small/Big × Value/Neutral/Growth | External validation portfolios |
| `25_Portfolios_Formed_on_Size_and_BM_5x5_CSV.zip` | 5×5 Size × B/M | Granular benchmarking |

**Integration approach:**

```
┌────────────────────────────────────────────────────────┐
│  FrenchFactorClient                                    │
│  ├── Scheduled monthly poll (1st of month)             │
│  ├── HTTP GET *.zip → unzip → parse CSV                │
│  ├── Detects new data rows since last import           │
│  └── Stores incremental rows in factor_returns table   │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  FrenchFactorCdmAdapter                                │
│  └── CSV row → FactorReturn CDM record                 │
│      (date, factor_set, rm_rf, smb, hml, rmw, cma,    │
│       rf, mom)                                         │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  Consumers                                             │
│  ├── Performance service: 5-factor regression          │
│  ├── Tournament service: Mom factor as benchmark       │
│  ├── Risk service: factor-based risk decomposition     │
│  └── Regime detection: factor spreads as features      │
└────────────────────────────────────────────────────────┘
```

**Why this source:** The project's `fama_french_regression()` at `analytics/app/services/performance/performance_service.py:59` already supports 3-factor OLS but requires callers to manually supply `smb` and `hml` data — there is no automated factor data ingestion. The Ken French Data Library is the canonical source for this data: free, authoritative, updated monthly, and trivially parseable (plain CSV). It also enables upgrading from 3-factor to 5-factor (adding RMW, CMA), replacing synthetic momentum benchmarks in the tournament service with the actual Mom/UMD factor, and providing sorted portfolio returns for out-of-sample backtest validation.

**Why P1:** The existing 3-factor regression is incomplete without factor data. No other free source provides Fama-French factors. Integration effort is low (CSV parsing, no auth) and impact is high (completes a half-implemented feature).

### 3.8 Alpha Vantage Historical & Commodity Data — P2

**Primary: Alpha Vantage REST API**

| Attribute | Value |
|:----------|:------|
| Endpoint | `https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol={symbol}&outputsize=full&apikey={key}` |
| Auth | Free API key (signup at https://www.alphavantage.co/support/#api-key) |
| Rate Limit | 5 req/min, 500 req/day (free); 75 req/min, 15,000 req/day ($49.99/mo "Fundamental" tier) |
| Data | Adjusted daily OHLCV (20+ years, split + dividend corrected), commodities, fundamental data |
| Coverage | US equities, global equities, commodities, forex, crypto, economic indicators |
| Latency | End-of-day (previous close available after market hours) |
| Stability | Official API with SLA on paid tiers; free tier is rate-limited but stable |

**Key endpoints for once-daily retrieval:**

| API Function | Data | Use Case |
|:-------------|:-----|:---------|
| `TIME_SERIES_DAILY_ADJUSTED` | 20+ years OHLCV with adjusted close, split coefficient, dividend amount | Backtesting, walk-forward validation, correlation analysis |
| `TIME_SERIES_WEEKLY` / `MONTHLY` | Weekly/monthly aggregated OHLCV | Long-term trend analysis |
| `COMMODITY_DAILY` (v3.0+) | Gold, crude oil, natural gas, copper, wheat, corn | Commodity sensitivity, inflation hedging analysis |
| `EARNINGS` | Quarterly EPS (actual vs estimated) | Earnings surprise factor |
| `BALANCE_SHEET` | Annual/quarterly balance sheet | Value factor signals |
| `INCOME_STATEMENT` | Revenue, net income, margins | Fundamental screening |
| `ECONOMIC_INDICATORS` | Real GDP, CPI, unemployment, treasury yields | Cross-check against FRED data |

**Integration approach:**

```
┌────────────────────────────────────────────────────────────┐
│  AlphaVantageClient                                        │
│  ├── @Scheduled daily poll (06:00 UTC, market closed)      │
│  ├── Fetches adjusted daily OHLCV for configured symbols   │
│  ├── Fetches commodity prices (gold, oil, gas, copper)     │
│  ├── Incremental: only new rows since last import date     │
│  ├── Resilience4j retry (3 attempts, exponential backoff)  │
│  └── Rate-limited: 1 req / 12s to stay within 5/min cap    │
└────────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│  AlphaVantageCdmAdapter                                    │
│  ├── AV adjusted daily → CdmTick (OHLCV)                  │
│  ├── AV commodity → CdmTick (commodity symbols)           │
│  └── AV fundamentals → FundamentalSnapshot CDM record     │
└────────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│  Consumers                                                 │
│  ├── WalkForwardValidator: 20-year backtest windows        │
│  ├── CrossModelValidator: out-of-sample validation         │
│  ├── CorrelationEngine: long-term equity correlations      │
│  ├── ClimateRiskGuard: commodity price sensitivity         │
│  └── Dashboard: historical price charts (20+ years)        │
└────────────────────────────────────────────────────────────┘
```

**Why Alpha Vantage over Yahoo Finance for historical data:**

| Factor | Yahoo Finance | Alpha Vantage |
|:-------|:-------------|:-------------|
| Adjusted close (splits + dividends) | Inconsistent adjustment method | Official split-coefficient + dividend fields |
| History depth | Varies by symbol | Consistent 20+ years for US equities |
| Commodity data | Limited | Gold, oil, gas, copper, wheat, corn (v3.0+) |
| Fundamental data | Not available via unofficial API | Earnings, balance sheet, income statement |
| API stability | No SLA, unofficial endpoints | Official API, versioned, documented |
| Rate limits | Unofficial (~2000/hr) | Clear limits (5/min free, 75/min paid) |
| Cost | Free | Free tier sufficient for once-daily use |

**Why P2:** Alpha Vantage fills the deep-history gap for backtesting that Yahoo Finance's 6-hour polling cannot address (Yahoo provides recent data; Alpha Vantage provides the full 20+ year adjusted series). It also adds commodities as an entirely new asset class. The free tier's 500 calls/day is ample for once-daily polling of ~10 symbols + 4 commodities (14 calls/day, 2.8% utilization). If backtesting demand grows, a paid tier unlock ($49.99/mo) removes all rate constraints.

**Free tier call budget:**

| Daily Call | Count | Cumulative |
|:-----------|:------|:-----------|
| Adjusted OHLCV (6 equity symbols, incremental) | 6 | 6 |
| Commodity daily (gold, oil, gas, copper) | 4 | 10 |
| Earnings (6 symbols, quarterly check) | 0–6 | 16 |
| Balance sheet (6 symbols, quarterly check) | 0–6 | 22 |
| **Total (typical day)** | **~14–22** | **2.8–4.4% of 500 daily cap** |

### 3.9 DataHub Historical Datasets — P1/P2

**Primary: DataHub Core Datasets (CSV)**

| Attribute | Value |
|:----------|:------|
| URL | `https://datahub.io/core/{dataset}/_r/-/data/{file}.csv` |
| Auth | None — fully public, no API key |
| Rate Limit | None (static CSV files served via CDN) |
| Format | CSV with stable r-link URLs; `datapackage.json` for machine-readable schema |
| License | ODC-PDDL-1.0 / Public Domain |
| Update Frequency | Monthly to ~2 months (varies by dataset) |
| Use Case | Historical backfill only (not a live streaming source) |

**Key datasets to integrate:**

| Dataset | CSV URL | Granularity | Coverage | Priority | Consumer Services |
|:--------|:--------|:------------|:---------|:---------|:------------------|
| S&P 500 (Shiller) | `core/s-and-p-500/_r/-/data/data.csv` | Monthly | 1871–present | P1 | Backtest, regime detection, CAPE analysis, alpha evaluation |
| VIX (CBOE) | `core/finance-vix/_r/-/data/vix-daily.csv` | Daily | 1990–present | P1 | Volatility regime detection, EVT risk, cross-asset correlation |
| Oil (WTI) | `core/oil-prices/_r/-/data/wti-daily.csv` | Daily | 1986–present | P2 | Macro shock IRF, energy-macro correlation, ILI sensitivity |
| Oil (Brent) | `core/oil-prices/_r/-/data/brent-daily.csv` | Daily | 1987–present | P2 | Cross-regime energy analysis, international benchmark |
| Gold | `core/gold-prices/_r/-/data/monthly.csv` | Monthly | 1833–present | P2 | Safe-haven correlation, inflation hedging, cross-asset analysis |

**Integration approach:**

```
┌──────────────────────────────────────────────────────────────┐
│  DataHubBackfillClient                                       │
│  ├── One-time full load on startup (if target table empty)   │
│  ├── Periodic incremental check (monthly, 1st of month)      │
│  ├── HTTP GET CSV from stable r-link URLs                    │
│  ├── Parse CSV → CDM records (per-dataset adapters)          │
│  ├── Detect new rows since max(timestamp) in target table    │
│  └── Write incremental rows via TimescaleDbWriter            │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  CDM Adapters (per dataset)                                  │
│  ├── ShillerSp500CdmAdapter → index_snapshots (SP500, CAPE)  │
│  ├── VixCdmAdapter          → rate_snapshots (VIX)           │
│  ├── OilPriceCdmAdapter     → rate_snapshots (OIL_WTI,       │
│  │                             OIL_BRENT)                     │
│  └── GoldPriceCdmAdapter    → rate_snapshots (GOLD)          │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│  Consumers                                                   │
│  ├── WalkForwardValidator: S&P 500 as backtest benchmark     │
│  ├── RegimeService: VIX for volatility regime classification │
│  ├── MacroShockService: oil prices for energy shock IRFs     │
│  ├── EVT risk service: VIX for tail risk calibration         │
│  ├── CorrelationEngine: cross-asset (oil, gold, S&P, VIX)    │
│  └── Dashboard: historical price charts (100+ years)         │
└──────────────────────────────────────────────────────────────┘
```

**Why DataHub:**

1. **Zero-cost, zero-auth** — plain CSV with stable URLs, no API key, no rate limits. Just `HTTP GET`.
2. **Deep history** — S&P 500 back to 1871 (153+ years), gold to 1833 (190+ years), VIX to 1990 (35+ years). No other free source provides this depth.
3. **Authoritative provenance** — Robert Shiller (S&P 500), CBOE (VIX), EIA (oil), World Bank (gold). Same sources the project already trusts via FRED.
4. **Complements Alpha Vantage** — Alpha Vantage provides live daily updates (20+ years); DataHub provides the deep historical tail (100+ years for gold, 50+ years for S&P 500). Together they give full coverage from 1833 to present.
5. **Machine-readable schema** — each dataset has `datapackage.json` with field types and descriptions for auto-generated CDM adapter field mappings.

**Why P1 for S&P 500 + VIX, P2 for Oil + Gold:**

The S&P 500 Shiller dataset provides the CAPE ratio (cyclically adjusted P/E) which is directly referenced in regime detection logic and fills the "historical equity benchmark" gap entirely. VIX fills the "volatility index" gap — the project has no volatility index source at all, and VIX is the canonical "fear gauge" that drives regime classification. Oil and gold are important for macro analysis but are secondary to the core ILI + regime detection pipeline.

**Deferred DataHub datasets:**

| Dataset | Reason for Deferral | Trigger Condition |
|:--------|:--------------------|:------------------|
| Natural Gas (Henry Hub) | Secondary energy commodity; oil covers primary macro-energy channel | Energy analysis expands beyond WTI/Brent |
| S&P 500 Company Lists | Project focuses on macro/index-level signals, not individual stock screening | Universe expands to individual equity analysis |
| NYSE/NASDAQ Listings | Not applicable to current index-focused architecture | Individual stock coverage required |
| S&P 500 Companies Financials | Fundamental data available via Alpha Vantage with daily updates | Alpha Vantage fundamentals insufficient |

See `docs/plan_v5/deferred-items-implementation-plan.md` §6 for the high-level integration plan for deferred datasets.

---

## 4. Integration Architecture

### 4.1 Source Layer Diagram

```
                        ┌─────────────────────────────────────┐
                        │        CDM Adapter Layer             │
                        │  (existing: FredCdmAdapter,          │
                        │   NyFedCdmAdapter, etc.)             │
                        └──────────────┬──────────────────────┘
                                       │
      ┌──────────────┬─────────────┬───┴───┬──────────────┬────────────────┬───────────────┬───────────┐
      │              │             │       │              │                │               │           │
┌─────▼─────┐ ┌─────▼─────┐ ┌────▼────┐ ┌▼────────────┐ ┌▼─────────────┐ ┌▼───────────┐ ┌▼──────────────┐ ┌▼──────────┐
│   FRED    │ │  NY Fed   │ │ Yahoo   │ │  Finnhub    │ │  Fed RSS     │ │ Ken French │ │ Alpha Vantage │ │ DataHub   │
│  (FREE)   │ │  (FREE)   │ │ Finance │ │  (FREE)     │ │  (FREE)      │ │  (FREE)    │ │   (FREE)      │ │  (FREE)   │
│           │ │           │ │ (FREE)  │ │             │ │              │ │            │ │               │ │           │
│ Rates     │ │ SOFR/TGCR │ │ OHLCV   │ │ Real-time   │ │ FOMC text    │ │ 3/5-Factor │ │ Deep hist     │ │ S&P 500   │
│ Balance   │ │ BGCR      │ │ Options │ │ equity WS   │ │ Speeches     │ │ Momentum   │ │ OHLCV (adj)   │ │ (Shiller) │
│ sheet     │ │ Treasury  │ │ chain   │ │ News feed   │ │ Press        │ │ Reversal   │ │ Commodities   │ │ VIX       │
│ Yield     │ │ rates     │ │         │ │             │ │ releases     │ │ Portfolios │ │ Fundamentals  │ │ Oil/Gold  │
│ curve     │ │           │ │         │ │             │ │              │ │            │ │               │ │           │
└───────────┘ └───────────┘ └─────────┘ └─────────────┘ └──────────────┘ └────────────┘ └───────────────┘ └───────────┘

      │              │             │              │                │           │               │
      └──────────────┴─────────────┴──────────────┴────────────────┴───────────┴───────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │    Ingestion Pipeline (existing)     │
                        │  ┌────────────────────────────┐     │
                        │  │ DataQualityChecker         │     │
                        │  │ ProxyDivergenceGuard       │     │
                        │  │ TimescaleDbWriter          │     │
                        │  │ CircuitBreakers / Retry    │     │
                        │  └────────────────────────────┘     │
                        └─────────────────────────────────────┘
```

### 4.2 New Java Classes

All new classes follow existing patterns: `@Scheduled` polling, `RestClient` HTTP, CDM adapters, Resilience4j resilience.

```
ingestion/src/main/java/com/tickonomics/ingestion/
├── equity/
│   ├── EquityPriceClient.java              # Interface: fetch OHLCV + real-time quote
│   ├── YahooFinanceClient.java             # Primary: Yahoo Finance REST
│   ├── FinnhubEquityClient.java            # Fallback: Finnhub REST
│   └── EquityPriceScheduler.java           # @Scheduled polling coordinator
├── ws/
│   ├── EquityWsClient.java                 # Interface (renamed from PolygonWsClient)
│   └── FinnhubWsClient.java               # Replaces DefaultPolygonWsClient
├── news/
│   ├── NewsIngestionClient.java            # Interface: fetch news articles
│   ├── FinnhubNewsClient.java              # Finnhub market news
│   └── FedRSSClient.java                   # Federal Reserve RSS feeds
└── options/
    └── YahooOptionsClient.java             # Replaces Polygon OptionsDataClient
        (existing OptionsDataClient.java refactored)
├── factor/
│   ├── FrenchFactorClient.java             # Ken French CSV download + parse
│   └── FrenchFactorScheduler.java          # @Scheduled monthly poll (1st of month)
├── alphavantage/
│   ├── AlphaVantageClient.java             # Alpha Vantage REST (historical OHLCV, commodities)
│   └── AlphaVantageScheduler.java          # @Scheduled daily poll (06:00 UTC)
├── datahub/
│   ├── DataHubBackfillClient.java          # DataHub CSV fetch + parse (backfill mode)
│   └── DataHubBackfillScheduler.java       # Startup full-load + monthly incremental check

cdm/src/main/java/com/tickonomics/cdm/adapter/
├── YahooEquityCdmAdapter.java              # Yahoo OHLCV → CdmTick
├── FinnhubEquityCdmAdapter.java            # Finnhub quote → CdmTick
├── YahooOptionsCdmAdapter.java             # Yahoo options → OptionsChainSnapshot
├── NewsArticleCdmAdapter.java              # News/Fed text → canonical news record
├── FrenchFactorCdmAdapter.java             # Ken French CSV → FactorReturn CDM
├── AlphaVantageCdmAdapter.java             # AV adjusted daily → CdmTick / commodity tick
├── ShillerSp500CdmAdapter.java             # Shiller S&P 500 CSV → IndexSnapshot CDM
├── VixCdmAdapter.java                      # DataHub VIX CSV → CdmRateSnapshot
├── OilPriceCdmAdapter.java                 # DataHub WTI/Brent CSV → CdmRateSnapshot
└── GoldPriceCdmAdapter.java                # DataHub Gold CSV → CdmRateSnapshot
```

### 4.3 Configuration Changes

```yaml
monitor:
  # --- Existing FRED (unchanged) ---
  fred:
    base-url: "https://api.stlouisfed.org/fred"
    api-key: "${FRED_API_KEY}"
    poll-interval-ms: 300000
    # New: yield curve series
    yield-curve-enabled: true

  # --- Existing NY Fed (unchanged) ---
  nyfed:
    base-url: "https://markets.newyorkfed.org/api"
    poll-interval-ms: 300000

  # --- NEW: Yahoo Finance ---
  yahoo-finance:
    base-url: "https://query1.finance.yahoo.com"
    poll-interval-ms: 21600000          # 6 hours
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    include-options: true
    options-poll-interval-ms: 3600000   # 1 hour (market hours only)
    options-symbols: ["SPY", "QQQ"]
    connect-timeout: "5s"
    read-timeout: "30s"

  # --- NEW: Finnhub (primary WS, fallback REST) ---
  finnhub:
    api-key: "${FINNHUB_API_KEY}"
    ws-url: "wss://ws.finnhub.io"
    rest-url: "https://finnhub.io/api/v1"
    ws-enabled: true
    ws-reconnect-backoff-max: "60s"
    rest-poll-interval-ms: 300000       # 5 min fallback
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    news-enabled: true
    news-poll-interval-ms: 21600000     # 6 hours
    connect-timeout: "5s"
    read-timeout: "30s"

  # --- NEW: Federal Reserve RSS ---
  fed-rss:
    enabled: true
    speeches-url: "https://www.federalreserve.gov/feeds/speeches.xml"
    fomc-url: "https://www.federalreserve.gov/feeds/press_monetary.xml"
    poll-interval-ms: 3600000           # 1 hour

  # --- NEW: Ken French Data Library ---
  ken-french:
    enabled: true
    base-url: "https://mba.tuck.dartmouth.edu/pages/faculty/ken.french/ftp"
    poll-interval-ms: 86400000          # 24 hours (check for monthly updates)
    poll-day-of-month: 1                # Primary poll on 1st of each month
    datasets:
      - name: "3-factor"
        file: "F-F_Research_Data_Factors_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RF"]
      - name: "5-factor"
        file: "F-F_Research_Data_5_Factors_2x3_CSV.zip"
        factors: ["RM-RF", "SMB", "HML", "RMW", "CMA", "RF"]
      - name: "momentum"
        file: "Momentum_Factor_CSV.zip"
        factors: ["MOM"]
    connect-timeout: "10s"
    read-timeout: "60s"                 # ZIP files can be large

  # --- NEW: Alpha Vantage (deep historical + commodities) ---
  alpha-vantage:
    enabled: true
    api-key: "${ALPHAVANTAGE_API_KEY}"
    base-url: "https://www.alphavantage.co/query"
    poll-cron: "0 0 6 * * *"            # 06:00 UTC daily (after US market close)
    symbols: ["SPY", "QQQ", "IWM", "TLT", "HYG", "GLD"]
    commodities: ["GOLD", "OIL", "NATURAL_GAS", "COPPER"]
    fetch-mode: "incremental"            # Only new rows since last import; "full" for initial load
    initial-full-load: true              # On first run, fetch full 20+ year history
    rate-limit-delay-ms: 12100           # 12.1s between calls (max 5/min → safe margin)
    connect-timeout: "10s"
    read-timeout: "30s"
    include-fundamentals: false          # Enable earnings/balance sheet (P3 stretch goal)

  # --- NEW: DataHub Historical Backfill ---
  datahub:
    enabled: true
    base-url: "https://datahub.io"
    startup-full-load: true              # Full load on first startup if tables empty
    incremental-check-cron: "0 0 2 1 * *" # 02:00 UTC on 1st of each month (check for updates)
    connect-timeout: "10s"
    read-timeout: "60s"                  # CSV files can be large
    datasets:
      sp500-shiller:
        enabled: true
        csv-url: "/core/s-and-p-500/_r/-/data/data.csv"
        target-table: "index_snapshots"
        description: "Monthly S&P 500 (Shiller): price, dividend, earnings, CPI, CAPE since 1871"
      vix:
        enabled: true
        csv-url: "/core/finance-vix/_r/-/data/vix-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "VIX"
        description: "Daily CBOE Volatility Index (OHLC) since 1990"
      oil-wti:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/wti-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_WTI"
        description: "Daily WTI spot price ($/bbl) since 1986"
      oil-brent:
        enabled: true
        csv-url: "/core/oil-prices/_r/-/data/brent-daily.csv"
        target-table: "rate_snapshots"
        rate-type: "OIL_BRENT"
        description: "Daily Brent spot price ($/bbl) since 1987"
      gold:
        enabled: true
        csv-url: "/core/gold-prices/_r/-/data/monthly.csv"
        target-table: "rate_snapshots"
        rate-type: "GOLD"
        description: "Monthly gold price ($/oz) since 1833"

  # --- REMOVED ---
  # polygon.ws-url          → replaced by finnhub.ws-url
  # polygon.api-key          → replaced by finnhub.api-key
  # openbb.base-url          → removed entirely
```

### 4.4 New CDM Enum Values

Extend `rate_type` CHECK constraint in `rate_snapshots`:

```sql
ALTER TABLE rate_snapshots DROP CONSTRAINT chk_rate_type_cdm;
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN (
        'SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
        'RRP', 'TGA', 'WALCL', 'TBILL_3M',
        -- New yield curve tenors
        'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
        'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y'
    ));
```

New `factor_returns` hypertable for Ken French data:

```sql
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
```

---

## 5. Implementation Phases

### Phase 1 — Replace Paid Equity Data (P0)

**Estimated effort:** 3–4 days
**Blocks:** Nothing (parallel with existing work)
**Depends on:** Existing ingestion infrastructure

| Step | Task | Files |
|:-----|:-----|:------|
| 1.1 | Create `EquityPriceClient` interface | `ingestion/.../equity/EquityPriceClient.java` |
| 1.2 | Implement `YahooFinanceClient` (historical OHLCV) | `ingestion/.../equity/YahooFinanceClient.java` |
| 1.3 | Implement `FinnhubEquityClient` (fallback) | `ingestion/.../equity/FinnhubEquityClient.java` |
| 1.4 | Create CDM adapters for Yahoo/Finnhub equity | `cdm/.../adapter/YahooEquityCdmAdapter.java`, `FinnhubEquityCdmAdapter.java` |
| 1.5 | Implement `EquityPriceScheduler` with circuit breaker | `ingestion/.../equity/EquityPriceScheduler.java` |
| 1.6 | Wire into existing `TimescaleDbWriter` pipeline | Update config, resilience beans |
| 1.7 | Write WireMock tests for both clients | `ingestion/src/test/...` |
| 1.8 | Integration test: Yahoo → CDM adapter → TimescaleDB | Testcontainers |

**Acceptance criteria:**
- [ ] `YahooFinanceClient` fetches OHLCV for all configured symbols
- [ ] `FinnhubEquityClient` activates when Yahoo circuit breaker opens
- [ ] Both clients produce `CdmTick` via adapters — no source-specific types leak
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] LKG cache serves stale data with `X-Data-Age: STALE` on source failure

### Phase 2 — Replace Paid WebSocket & Options (P0/P1)

**Estimated effort:** 2–3 days
**Depends on:** Phase 1

| Step | Task | Files |
|:-----|:-----|:------|
| 2.1 | Rename `PolygonWsClient` → `EquityWsClient` interface | `api-contracts/.../client/` |
| 2.2 | Implement `FinnhubWsClient` (real-time trades) | `ingestion/.../ws/FinnhubWsClient.java` |
| 2.3 | Update `PolygonTickCdmAdapter` → generic `WsTickCdmAdapter` | `cdm/.../adapter/` |
| 2.4 | Implement `YahooOptionsClient` replacing Polygon options | `ingestion/.../options/YahooOptionsClient.java` |
| 2.5 | Create `YahooOptionsCdmAdapter` | `cdm/.../adapter/YahooOptionsCdmAdapter.java` |
| 2.6 | Deprecate `DefaultPolygonWsClient` and old `OptionsDataClient` | Mark `@Deprecated` |
| 2.7 | Write tests for Finnhub WS (mock WebSocket server) | `ingestion/src/test/...` |
| 2.8 | Write tests for Yahoo options parsing | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FinnhubWsClient` connects, subscribes, receives trades, writes to `tick_data`
- [ ] Auto-reconnect works after disconnect (1s → 60s backoff)
- [ ] `YahooOptionsClient` fetches full options chain with Greeks and OI
- [ ] Options data flows to `option_chain_snapshots` hypertable
- [ ] No paid Polygon dependency on the runtime classpath (compile-only stub)

### Phase 3 — News & Sentiment Sources (P1)

**Estimated effort:** 2–3 days
**Depends on:** Phase 1 (news needs to flow into existing pipeline)

| Step | Task | Files |
|:-----|:-----|:------|
| 3.1 | Create `NewsIngestionClient` interface | `ingestion/.../news/NewsIngestionClient.java` |
| 3.2 | Implement `FinnhubNewsClient` (market news) | `ingestion/.../news/FinnhubNewsClient.java` |
| 3.3 | Implement `FedRSSClient` (FOMC/speeches) | `ingestion/.../news/FedRSSClient.java` |
| 3.4 | Create `NewsArticleCdmAdapter` | `cdm/.../adapter/NewsArticleCdmAdapter.java` |
| 3.5 | Write to `sentiment_history` hypertable | Update `TimescaleDbWriter` mapping |
| 3.6 | Wire to `SentimentAnalyzer` (Proposal 09) | `computation/.../sentiment/` |
| 3.7 | Write tests with WireMock (Finnhub) and mock RSS (Fed) | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FinnhubNewsClient` fetches market news headlines at configured interval
- [ ] `FedRSSClient` parses FOMC statements and Fed speeches from RSS
- [ ] News articles stored in `sentiment_history` with `source_type` classification
- [ ] FinBERT receives Fed speech text for hawkish/dovish classification
- [ ] SALI lexicon scores general news headlines

### Phase 4 — Ken French Factor Returns (P1)

**Estimated effort:** 2–3 days
**Depends on:** Existing ingestion infrastructure
**Blocks:** Phase 5 (yield curve) is independent; Phase 8 (cleanup) depends on all prior phases

| Step | Task | Files |
|:-----|:-----|:------|
| 4.1 | Create `FrenchFactorClient` — HTTP GET ZIP, unzip, parse CSV | `ingestion/.../factor/FrenchFactorClient.java` |
| 4.2 | Create `FrenchFactorScheduler` — `@Scheduled` monthly poll | `ingestion/.../factor/FrenchFactorScheduler.java` |
| 4.3 | Create `FrenchFactorCdmAdapter` — CSV row → `FactorReturn` CDM | `cdm/.../adapter/FrenchFactorCdmAdapter.java` |
| 4.4 | Add `FactorReturn` CDM model and `FactorSet` enum | `cdm/.../model/FactorReturn.java`, `cdm/.../FactorSet.java` |
| 4.5 | Flyway migration: create `factor_returns` hypertable | `persistence/.../db/migration/V20__create_factor_returns.sql` |
| 4.6 | Update `TimescaleDbWriter` to handle `FactorReturn` records | `persistence/.../TimescaleDbWriter.java` |
| 4.7 | Upgrade `fama_french_regression()` to support 5-factor model | `analytics/.../performance/performance_service.py` |
| 4.8 | Wire `FrenchFactorClient` config in `application.yml` | `app/src/main/resources/application.yml` |
| 4.9 | Wire Mom factor into tournament service as external benchmark | `analytics/.../benchmark/tournament_service.py` |
| 4.10 | Write tests: CSV parsing, CDM adapter, incremental import | `ingestion/src/test/...`, `analytics/tests/...` |

**Acceptance criteria:**
- [ ] `FrenchFactorClient` downloads and parses 3-factor, 5-factor, and momentum ZIP files
- [ ] Incremental import detects new rows since last import (no duplicates)
- [ ] Factor returns stored in `factor_returns` hypertable with correct date alignment
- [ ] Missing values (-99.99, -999) handled gracefully (stored as NULL)
- [ ] `fama_french_regression()` supports 5-factor mode (Rm-Rf, SMB, HML, RMW, CMA)
- [ ] Tournament service uses actual Mom/UMD factor instead of synthetic momentum
- [ ] Python analytics worker reads factor data from TimescaleDB for regressions
- [ ] WireMock tests cover: success, 404, corrupt ZIP, malformed CSV

### Phase 5 — Yield Curve Extension (P2)

**Estimated effort:** 1 day
**Depends on:** Nothing (extends existing FRED client)

| Step | Task | Files |
|:-----|:-----|:------|
| 5.1 | Add yield curve series IDs to `FredSeriesConfig` | `ingestion/.../fred/FredSeriesConfig.java` |
| 5.2 | Add CDM enum values for yield curve tenors | `cdm/.../RateType.java` |
| 5.3 | Flyway migration: extend `chk_rate_type_cdm` constraint | `persistence/.../db/migration/V19__add_yield_curve_enums.sql` |
| 5.4 | Update `MonetaryPolicySensitivityService` to consume yield curve data | `computation/.../` |
| 5.5 | Write tests verifying new series ingestion | `ingestion/src/test/...` |

**Acceptance criteria:**
- [ ] `FredClient` fetches all 8 yield curve tenors in addition to existing series
- [ ] Data stored in `rate_snapshots` with CDM-aligned `rate_type` values
- [ ] Yield curve shape (2Y–10Y spread, 3M–10Y spread) computable from stored data
- [ ] `MonetaryPolicySensitivityService` renders yield curve panel on dashboard

### Phase 6 — Alpha Vantage Historical & Commodity Data (P2)

**Estimated effort:** 3–4 days
**Depends on:** Phase 1 (ingestion pipeline and CDM adapter patterns established)
**Blocks:** Phase 8 (cleanup)

| Step | Task | Files |
|:-----|:-----|:------|
| 6.1 | Create `AlphaVantageClient` — REST client with rate-limit-aware sequential calls | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.2 | Create `AlphaVantageScheduler` — `@Scheduled(cron)` daily at 06:00 UTC | `ingestion/.../alphavantage/AlphaVantageScheduler.java` |
| 6.3 | Create `AlphaVantageCdmAdapter` — adjusted daily OHLCV → `CdmTick`, commodity → `CdmTick` | `cdm/.../adapter/AlphaVantageCdmAdapter.java` |
| 6.4 | Create `AlphaVantageRawModels` — response DTOs for TIME_SERIES_DAILY_ADJUSTED, COMMODITY_DAILY | `ingestion/.../alphavantage/AlphaVantageRawModels.java` |
| 6.5 | Add `InstrumentType.COMMODITY` enum and commodity subtypes (`GOLD`, `OIL`, `NATURAL_GAS`, `COPPER`, `WHEAT`, `CORN`) to CDM | `cdm/.../enums/InstrumentType.java` |
| 6.6 | Implement incremental fetch logic — query `tick_data` for last timestamp per symbol, pass `startdate` parameter to Alpha Vantage | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.7 | Implement initial full-load mode — `outputsize=full` on first run per symbol (tracked in import metadata table) | `ingestion/.../alphavantage/AlphaVantageClient.java` |
| 6.8 | Add Resilience4j retry config for `alphaVantageApi` in `application.yml` | `app/src/main/resources/application.yml` |
| 6.9 | Flyway migration: add `commodity` to `InstrumentType` CHECK constraint; add `data_import_tracker` table for incremental state | `persistence/.../db/migration/V21__add_commodity_and_import_tracker.sql` |
| 6.10 | Wire `AlphaVantageCdmAdapter` output to existing `TimescaleDbWriter` | `ingestion/.../alphavantage/AlphaVantageScheduler.java` |
| 6.11 | Wire commodity data to `ClimateRiskGuard` for commodity price sensitivity | `computation/.../risk/` |
| 6.12 | Write unit tests: response parsing, CDM adapter, incremental logic, rate limiting | `ingestion/src/test/.../alphavantage/` |
| 6.13 | Write WireMock integration tests: success, 429 rate limit, invalid API key, malformed JSON, empty response | `ingestion/src/test/.../alphavantage/` |

**Acceptance criteria:**
- [ ] `AlphaVantageClient` fetches adjusted daily OHLCV for all configured symbols
- [ ] Adjusted close reflects split and dividend corrections (verified against known corporate actions)
- [ ] Commodity daily prices fetched for gold, crude oil, natural gas, copper
- [ ] Rate limiting enforced: no more than 1 call per 12.1s (≤5/min)
- [ ] Incremental mode fetches only new rows since last import (tracked per symbol in `data_import_tracker`)
- [ ] Initial full load fetches 20+ year history on first run per symbol
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] Commodity data identifiable by `InstrumentType.COMMODITY` subtype in CDM
- [ ] Resilience4j retry handles transient 429/5xx responses with exponential backoff
- [ ] WireMock tests cover: success, 429 rate limit, 401 invalid key, malformed JSON, empty series, split-adjusted verification

### Phase 7 — DataHub Historical Backfill (P1/P2)

**Estimated effort:** 2–3 days
**Depends on:** Phase 4 (ingestion pipeline patterns), Phase 6 (CDM adapter patterns for commodity/index types)
**Blocks:** Phase 8 (cleanup)

| Step | Task | Files |
|:-----|:-----|:------|
| 7.1 | Create `DataHubBackfillClient` — HTTP GET CSV from stable r-link URLs with streaming CSV parser | `ingestion/.../datahub/DataHubBackfillClient.java` |
| 7.2 | Create `DataHubBackfillScheduler` — startup full-load trigger + `@Scheduled(cron)` monthly incremental check | `ingestion/.../datahub/DataHubBackfillScheduler.java` |
| 7.3 | Create `ShillerSp500CdmAdapter` — Shiller CSV row → `IndexSnapshot` CDM record (price, dividend, earnings, CPI, CAPE) | `cdm/.../adapter/ShillerSp500CdmAdapter.java` |
| 7.4 | Create `VixCdmAdapter` — VIX CSV row → `CdmRateSnapshot` with `rate_type='VIX'` | `cdm/.../adapter/VixCdmAdapter.java` |
| 7.5 | Create `OilPriceCdmAdapter` — WTI/Brent CSV row → `CdmRateSnapshot` with `rate_type='OIL_WTI'`/`'OIL_BRENT'` | `cdm/.../adapter/OilPriceCdmAdapter.java` |
| 7.6 | Create `GoldPriceCdmAdapter` — Gold CSV row → `CdmRateSnapshot` with `rate_type='GOLD'` | `cdm/.../adapter/GoldPriceCdmAdapter.java` |
| 7.7 | Extend CDM `RateType` enum with `VIX`, `OIL_WTI`, `OIL_BRENT`, `GOLD` | `cdm/.../RateType.java` |
| 7.8 | Add `InstrumentType.INDEX` and `INDEX_SP500` to CDM | `cdm/.../enums/InstrumentType.java` |
| 7.9 | Flyway migration: extend `chk_rate_type_cdm` constraint for VIX/oil/gold; create `index_snapshots` hypertable | `persistence/.../db/migration/V22__datahub_backfill.sql` |
| 7.10 | Wire `DataHubBackfillClient` config in `application.yml` under `datahub.*` namespace | `app/src/main/resources/application.yml` |
| 7.11 | Wire Shiller data to backtesting services (`WalkForwardValidator`, `CrossModelValidator`) as benchmark index | `computation/.../backtest/` |
| 7.12 | Wire VIX to regime detection and EVT risk services as volatility input | `analytics/.../regime/`, `analytics/.../risk/` |
| 7.13 | Wire oil/gold to macro shock and cross-asset correlation services | `analytics/.../statistical/`, `analytics/.../econometrics/` |
| 7.14 | Write unit tests: CSV parsing for each dataset, CDM adapter field mapping, incremental import logic | `ingestion/src/test/.../datahub/` |
| 7.15 | Write WireMock integration tests: success, 404, empty CSV, malformed rows, schema changes | `ingestion/src/test/.../datahub/` |

**Acceptance criteria:**
- [ ] `DataHubBackfillClient` fetches all 5 configured CSV files from stable r-link URLs
- [ ] S&P 500 Shiller data (1871–present) loaded into `index_snapshots` with price, dividend, earnings, CPI, CAPE
- [ ] VIX daily data (1990–present) loaded into `rate_snapshots` with `rate_type='VIX'`
- [ ] WTI daily (1986–present) and Brent daily (1987–present) loaded into `rate_snapshots` with correct rate types
- [ ] Gold monthly (1833–present) loaded into `rate_snapshots` with `rate_type='GOLD'`
- [ ] Startup detection: if target table is empty, perform full load; otherwise incremental only
- [ ] Incremental import detects new rows since `max(timestamp)` — no duplicates
- [ ] Shiller PE10 values of 0.0 (1871–1880, insufficient trailing history) stored as NULL
- [ ] Gold pre-1960 monthly values (annual averages repeated per month) annotated in metadata column
- [ ] WireMock tests cover: success, 404, empty CSV, malformed rows, schema drift

**`index_snapshots` hypertable schema:**

```sql
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
    cape            DOUBLE PRECISION,           -- NULL for 1871-1880 (insufficient history)
    source          TEXT            NOT NULL DEFAULT 'DATAHUB_SHILLER',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (time, index_type)
);

SELECT create_hypertable('index_snapshots', 'time', chunk_time_interval => INTERVAL '10 years');
ALTER TABLE index_snapshots SET (compress_after = '50 years');
```

**`rate_snapshots` CHECK constraint extension:**

```sql
ALTER TABLE rate_snapshots DROP CONSTRAINT chk_rate_type_cdm;
ALTER TABLE rate_snapshots ADD CONSTRAINT chk_rate_type_cdm
    CHECK (rate_type IN (
        'SOFR', 'EFFR', 'TGCR', 'BGCR', 'IORB', 'OBFR',
        'RRP', 'TGA', 'WALCL', 'TBILL_3M',
        'TBILL_1M', 'TBILL_6M', 'TBILL_1Y', 'TBILL_2Y',
        'TBILL_5Y', 'TBILL_10Y', 'TBILL_30Y',
        -- DataHub historical backfill types
        'VIX', 'OIL_WTI', 'OIL_BRENT', 'GOLD'
    ));
```

### Phase 8 — Cleanup & Removal (Final)

**Estimated effort:** 1 day
**Depends on:** Phases 1–7 complete and validated

| Step | Task |
|:-----|:-----|
| 8.1 | Remove `docker-compose.openbb.yml` overlay |
| 8.2 | Remove `FederationDataClient` interface (never implemented, superseded) |
| 8.3 | Remove `OpenBBAnalyticsClient` interface and references |
| 8.4 | Remove `openbb.*` configuration keys from `application.yml` |
| 8.5 | Delete `DefaultPolygonWsClient.java` (replaced by `FinnhubWsClient`) |
| 8.6 | Remove `POLYGON_API_KEY` from `.env.example` |
| 8.7 | Add `FRED_API_KEY`, `FINNHUB_API_KEY`, and `ALPHAVANTAGE_API_KEY` to `.env.example` |
| 8.8 | Update `application.yml` resilience4j: rename `polygonApi` → `finnhubApi` |
| 8.9 | Remove Polygon dependency from `ingestion/build.gradle` |
| 8.10 | Update documentation references to reflect new data sources |

---

## 6. Free Tier Rate Limit Analysis

### Projected Daily API Calls

| Source | Endpoint | Calls/Day | Free Tier Limit | Margin |
|:-------|:---------|:----------|:----------------|:-------|
| FRED | Series observations | ~120 (5 min × 13 series × 24h) | 172,800/day (120/min) | 99.9% headroom |
| NY Fed | Rate endpoints | ~144 (3 rates × 48 polls) | No published limit | N/A |
| Yahoo Finance | OHLCV + options | ~200 (6 symbols × 6h + 2 options × 24h) | ~48,000/day (est.) | 99.6% headroom |
| Finnhub REST | Quotes + news | ~300 (6 × 48 polls + news 6h) | 86,400/day (60/min) | 99.7% headroom |
| Finnhub WS | Real-time trades | Persistent connection | No WS limit (free) | N/A |
| Fed RSS | Speeches + FOMC | ~48 (2 feeds × 24 polls) | No published limit | N/A |
| USGS | Earthquake feed | ~2,880 (30s polls) | No published limit | N/A |
| Ken French | Factor CSV files | ~1 (monthly download, 3 ZIP files) | No limit (static files) | N/A |
| Alpha Vantage | Adjusted OHLCV + commodities | ~14–22 (once-daily poll) | 500/day (5/min) | 95.6–97.2% headroom |
| DataHub | Historical CSV backfill | ~5 (monthly incremental check, 5 CSV files) | No limit (static CDN files) | N/A |

**Total estimated daily calls: ~3,725** — well within all free tier limits.

### Finnhub Free Tier Constraints

| Feature | Free | Paid ($49/mo) |
|:--------|:-----|:--------------|
| US equity quotes | Yes (15 min delayed) | Real-time |
| WebSocket trades | Yes | Yes |
| Historical candles | Yes (1-day resolution) | 1-minute |
| Market news | Yes | Yes |
| Options data | No | Yes |
| Rate limit | 60/min | 600/min |

**Impact assessment:** The 15-minute quote delay on Finnhub REST is acceptable because:
1. Real-time trades via Finnhub WebSocket provide tick-level data for signal generation.
2. Historical OHLCV from Yahoo Finance provides the same daily/intraday bars that Polygon provided.
3. The ILI computation uses daily-aligned macro data, not tick-level equity feeds.

---

## 7. Risk Assessment

### High Risk

| Risk | Mitigation |
|:-----|:-----------|
| Yahoo Finance endpoint changes without notice | `FinnhubEquityClient` as automatic fallback; circuit breaker isolates Yahoo failures; endpoint versioned in config |
| Finnhub free tier changes or discontinues | Yahoo Finance as primary REST fallback; both sources have independent circuit breakers; Alpha Vantage provides a third independent equity source for historical data |
| Alpha Vantage free tier insufficient for growing symbol list | Current 14–22 calls/day uses only 4.4% of 500/day cap; paid tier ($49.99/mo) unlocks 15,000 calls/day if needed; rate-limit-aware scheduler enforces 12.1s spacing |
| Yahoo Finance rate limiting / IP blocking | Session rotation, polite polling (6h intervals), `User-Agent` header configuration, exponential backoff on 429 responses |

### Medium Risk

| Risk | Mitigation |
|:-----|:-----------|
| Finnhub WebSocket free tier has fewer symbols than Polygon | Limit to 6 core symbols (SPY, QQQ, IWM, TLT, HYG, GLD) — sufficient for ILI correlation and demo portfolio |
| Yahoo options data may have 15-min delay | Acceptable for GEX monitoring (daily regime detection, not intraday trading) |
| Fed RSS structure changes | Defensive XML parsing with fallback to raw text extraction |
| Alpha Vantage API response format changes | Response DTOs versioned; WireMock tests pin expected schema; Resilience4j retry on transient errors |
| DataHub CSV schema changes between updates | `datapackage.json` fetched alongside CSV for schema validation; `DataHubBackfillClient` validates field count/type before parsing; malformed rows skipped with warning log |

### Low Risk

| Risk | Mitigation |
|:-----|:-----------|
| FRED rate limit hit (120/min) | 13 series at 5-min intervals = ~2.6 req/min — 98% headroom |
| NY Fed API downtime | LKG cache serves stale data; existing resilience patterns handle this |

---

## 8. Environment Variables

```bash
# .env.example (updated)

# Required — free signup at https://fred.stlouisfed.org/docs/api/api_key/
FRED_API_KEY=

# Required — free signup at https://finnhub.io/register
FINNHUB_API_KEY=

# Required — free signup at https://www.alphavantage.co/support/#api-key
ALPHAVANTAGE_API_KEY=

# Removed:
# POLYGON_API_KEY=    (no longer needed)
```

---

## 9. Validation Checklist

### Phase 1 — Equity Prices

- [ ] `YahooFinanceClient` fetches OHLCV for SPY, QQQ, IWM, TLT, HYG, GLD
- [ ] Yahoo response parsed into `CdmTick` via `YahooEquityCdmAdapter`
- [ ] `FinnhubEquityClient` fetches real-time quotes for the same symbols
- [ ] Finnhub response parsed into `CdmTick` via `FinnhubEquityCdmAdapter`
- [ ] Circuit breaker on Yahoo opens after 5 failures; Finnhub takes over
- [ ] LKG cache serves last known price with `X-Data-Age: STALE` header
- [ ] Data written to `tick_data` hypertable with correct symbol, price, volume
- [ ] Continuous aggregates (`ohlcv_1min`, `ohlcv_1h`, `ohlcv_1d`) produce correct candles
- [ ] WireMock tests cover: success, 404, 429, timeout, malformed JSON

### Phase 2 — WebSocket & Options

- [ ] `FinnhubWsClient` connects to `wss://ws.finnhub.io` and authenticates
- [ ] Subscribes to configured symbols, receives trade events
- [ ] Trade events parsed into `CdmTick` with correct price, volume, timestamp
- [ ] Auto-reconnect works after disconnect with exponential backoff (1s → 60s)
- [ ] `YahooOptionsClient` fetches options chain for SPY and QQQ
- [ ] Options chain includes: strikes, expiry, bid, ask, IV, OI, volume, Greeks
- [ ] Data stored in `option_chain_snapshots` hypertable
- [ ] `DefaultPolygonWsClient` and old `OptionsDataClient` marked `@Deprecated`

### Phase 3 — News & Sentiment

- [ ] `FinnhubNewsClient` fetches market news articles
- [ ] `FedRSSClient` parses FOMC statements and Fed speeches from RSS XML
- [ ] News articles stored in `sentiment_history` with `source_type` classification
- [ ] FinBERT hawkish/dovish classification receives Fed speech text
- [ ] SALI lexicon scoring receives general news headlines
- [ ] Duplicate news articles filtered by `text_hash` (idempotency)

### Phase 4 — Ken French Factors

- [ ] `FrenchFactorClient` downloads and parses 3-factor, 5-factor, momentum ZIP files
- [ ] CSV rows parsed into `FactorReturn` CDM records with correct date alignment
- [ ] Missing values (-99.99, -999) stored as NULL, not as valid returns
- [ ] Incremental import detects new rows since last import (no duplicates)
- [ ] Factor returns stored in `factor_returns` hypertable with UNIQUE constraint enforced
- [ ] `fama_french_regression()` supports 5-factor mode with RMW, CMA regressors
- [ ] Tournament service uses Mom/UMD factor as benchmark (replaces synthetic momentum)
- [ ] WireMock tests cover: successful ZIP download, 404, corrupt ZIP, malformed CSV rows
- [ ] Python analytics tests verify 5-factor regression against known factor values

### Phase 5 — Yield Curve

- [ ] `FredClient` fetches 8 yield curve tenors (DGS1MO through DGS30)
- [ ] Data stored in `rate_snapshots` with CDM-aligned rate_type values
- [ ] 2Y–10Y spread and 3M–10Y spread computable from stored data
- [ ] Flyway migration V19 extends CHECK constraint without data loss

### Phase 6 — Alpha Vantage Historical & Commodity

- [ ] `AlphaVantageClient` fetches adjusted daily OHLCV for all configured symbols
- [ ] Adjusted close reflects split and dividend corrections (spot-check against known corporate actions)
- [ ] Commodity daily prices fetched for gold, crude oil, natural gas, copper
- [ ] Rate limiting enforced: scheduler spaces calls at 12.1s intervals (≤5/min)
- [ ] Incremental mode fetches only new rows since last import (tracked in `data_import_tracker`)
- [ ] Initial full load fetches 20+ year history on first run per symbol
- [ ] Data flows to `tick_data` hypertable via existing `TimescaleDbWriter`
- [ ] Commodity data identifiable by `InstrumentType.COMMODITY` subtype in CDM
- [ ] WireMock tests cover: success, 429 rate limit, 401 invalid key, malformed JSON, empty series
- [ ] Flyway migration V21 adds commodity instrument types and `data_import_tracker` table

### Phase 7 — DataHub Historical Backfill

- [ ] `DataHubBackfillClient` fetches all 5 configured CSV files from stable r-link URLs
- [ ] S&P 500 Shiller data (1871–present) loaded into `index_snapshots` with price, dividend, earnings, CPI, CAPE
- [ ] VIX daily data (1990–present) loaded into `rate_snapshots` with `rate_type='VIX'`
- [ ] WTI daily (1986–present) and Brent daily (1987–present) loaded into `rate_snapshots`
- [ ] Gold monthly (1833–present) loaded into `rate_snapshots` with `rate_type='GOLD'`
- [ ] Startup full-load works when target tables are empty
- [ ] Incremental monthly check imports only new rows since `max(timestamp)` — no duplicates
- [ ] Shiller PE10 values of 0.0 (1871–1880) stored as NULL
- [ ] Gold pre-1960 annual-averaged rows annotated in metadata
- [ ] WireMock tests cover: success, 404, empty CSV, malformed rows, schema drift
- [ ] Backtesting services receive S&P 500 benchmark for walk-forward validation
- [ ] Regime detection receives VIX for volatility regime classification
- [ ] Macro shock service receives oil/gold for energy and safe-haven impulse responses
- [ ] Flyway migration V22 extends `chk_rate_type_cdm` and creates `index_snapshots` hypertable

### Phase 8 — Cleanup

- [ ] No Polygon import remains in `ingestion/build.gradle`
- [ ] No `openbb.*` configuration keys in `application.yml`
- [ ] No `FederationDataClient` or `OpenBBAnalyticsClient` interfaces remain
- [ ] `docker-compose.openbb.yml` removed from repository
- [ ] `.env.example` lists `FRED_API_KEY`, `FINNHUB_API_KEY`, and `ALPHAVANTAGE_API_KEY`
- [ ] All tests pass after cleanup

---

## 10. Dependency Impact

### Modules Modified

| Module | Change | Backward Compatible |
|:-------|:-------|:--------------------|
| `ingestion` | New equity, ws, news, options, factor, alphavantage, datahub clients | Yes (additive) |
| `cdm` | New adapters, extended `RateType` enum (`VIX`, `OIL_WTI`, `OIL_BRENT`, `GOLD`), `FactorReturn` model, `FactorSet` enum, `InstrumentType.COMMODITY` subtypes, `InstrumentType.INDEX`, `IndexSnapshot` model | Yes (additive) |
| `api-contracts` | `EquityWsClient` interface (renamed) | No (rename) |
| `persistence` | V19 for yield curve enums, V20 for `factor_returns`, V21 for commodity types + `data_import_tracker`, V22 for DataHub backfill (`index_snapshots`, extended `chk_rate_type_cdm`) | Yes (additive) |
| `app` | Updated `application.yml`, `.env.example` | No (config changes) |
| `web` | No changes (consumes computation layer) | Yes |
| `computation` | `MonetaryPolicySensitivityService` updated; `ClimateRiskGuard` consumes commodity data | Yes (additive) |
| `analytics` | `fama_french_regression()` upgraded to 5-factor; tournament service uses Mom benchmark | No (behavior change) |
| `frontend` | No changes | Yes |

### Docker Compose Changes

| Service | Action |
|:--------|:-------|
| `openbb` | Remove from `docker-compose.openbb.yml` |
| Main app | No changes (runs same Java process) |
| Analytics worker | No changes |

---

## Changelog

| Version | Change |
|:--------|:-------|
| 1.0 | Initial plan: 6 free data sources replacing 2 paid Polygon subscriptions and removing the unimplemented OpenBB sidecar. 5 implementation phases. |
| 1.1 | Added Ken French Data Library (§3.7) as P1 source for Fama-French factor returns. New Phase 4 (factor ingestion + 5-factor upgrade + Mom benchmark). Added `factor_returns` hypertable, `FrenchFactorClient`, `FrenchFactorCdmAdapter`. Renumbered Yield Curve → Phase 5, Cleanup → Phase 6. |
| 1.2 | Added Alpha Vantage (§3.8) as P2 source for deep historical equity OHLCV (20+ years, split/dividend adjusted), commodities (gold, oil, gas, copper), and fundamental data. New Phase 6 (Alpha Vantage ingestion with incremental fetch, rate-limit-aware scheduler, commodity CDM types). Renumbered Cleanup → Phase 7. Added `AlphaVantageClient`, `AlphaVantageCdmAdapter`, `AlphaVantageRawModels`, `data_import_tracker` table, `InstrumentType.COMMODITY` subtypes. Updated rate limit analysis (~14–22 calls/day, 4.4% of free tier cap). |
| 1.3 | Added DataHub (§3.9) as P1/P2 source for deep-historical CSV backfill: S&P 500 Shiller (1871–present, CAPE), VIX (1990–present daily), oil WTI/Brent (1986–present daily), gold (1833–present monthly). Zero-cost, zero-auth, stable CDN URLs. New Phase 7 (DataHub ingestion with startup full-load + monthly incremental, `index_snapshots` hypertable, extended `rate_snapshots` CHECK constraint for `VIX`/`OIL_WTI`/`OIL_BRENT`/`GOLD`). Renumbered Cleanup → Phase 8. Added `DataHubBackfillClient`, `DataHubBackfillScheduler`, `ShillerSp500CdmAdapter`, `VixCdmAdapter`, `OilPriceCdmAdapter`, `GoldPriceCdmAdapter`, `index_snapshots` hypertable. Updated data gaps (added historical equity benchmark + VIX), source diagram, rate limit analysis. |
