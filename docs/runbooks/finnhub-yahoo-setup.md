# Finnhub WebSocket Configuration + Yahoo Finance Setup

## Overview

v6 replaced the paid Polygon/OpenBB data layer with free-tier sources. Real-time trades and news
arrive over the **Finnhub** WebSocket; historical OHLCV, macro, and commodity data are fetched over
REST from **Yahoo Finance**, **FRED**, **NY Fed**, **Alpha Vantage**, and **Ken French**. No sidecar
container is required — every client is a direct Java REST/WebSocket client in the `ingestion`
module.

## Prerequisites

- Docker Engine >= 24.0, Docker Compose v2
- A Finnhub API key (free tier) — set as `FINNHUB_API_KEY`
- An Alpha Vantage API key (free tier) — set as `ALPHAVANTAGE_API_KEY`
- Yahoo Finance, FRED, NY Fed, and Ken French require **no API key**

## Configuration

Both keys are wired into the backend environment in `docker-compose.yml`:

```yaml
backend:
  environment:
    FINNHUB_API_KEY: ${FINNHUB_API_KEY:-}
    ALPHAVANTAGE_API_KEY: ${ALPHAVANTAGE_API_KEY:-}
```

They bind to `monitor.finnhub.api-key` and `monitor.alpha-vantage.api-key` in
`app/src/main/resources/application.yml`. For local development, export them or add to `.env`:

```bash
export FINNHUB_API_KEY=xxxxxxxx
export ALPHAVANTAGE_API_KEY=xxxxxxxx
```

## Starting the Stack

```bash
docker compose up -d
```

The backend starts once TimescaleDB and the analytics worker pass their health checks.

## Verifying the Finnhub WebSocket

After startup, confirm trade subscriptions are live:

```bash
docker compose logs backend --tail=100 | grep -i "finnhub"
```

Look for successful `FinnhubWsClient` trade subscription lines for the configured symbols
(`monitor.finnhub.symbols`, default `SPY,QQQ,IWM,TLT,HYG,GLD`). The client auto-reconnects with
exponential backoff up to `ws-reconnect-backoff-max` (60s); see
[finnhub-websocket-outage.md](finnhub-websocket-outage.md) for the outage procedure.

## Verifying Yahoo Finance REST

Confirm historical OHLCV fetches are working (no key required — public endpoints with rate
limiting):

```bash
docker compose logs backend --tail=100 | grep -i "yahoo"
```

The `YahooFinanceClient` is the primary equity source; `FinnhubEquityClient` is the configured REST
fallback when Yahoo is unavailable.

## Free Data Source Inventory

| Client | Data | Key required | Module |
|---|---|---|---|
| `FinnhubWsClient` | Real-time trades | `FINNHUB_API_KEY` | ingestion |
| `FinnhubEquityClient` | Equity aggregates (REST fallback) | `FINNHUB_API_KEY` | ingestion |
| `FinnhubNewsClient` | Market news | `FINNHUB_API_KEY` | ingestion |
| `YahooFinanceClient` | Historical OHLCV (primary equity) | none | ingestion |
| `YahooOptionsClient` | Options chains | none | ingestion |
| `AlphaVantageClient` | Commodities + equities | `ALPHAVANTAGE_API_KEY` | ingestion |
| `FredClient` | FRED macro series | none | ingestion |
| `NyFedClient` | SOFR / rates | none | ingestion |
| `FrenchFactorClient` | Ken French factor data | none | ingestion |
| `DataHubBackfillClient` | CSV backfill | none | ingestion |
| `FedRSSClient` | Fed speeches / FOMC feeds | none | ingestion |

## Troubleshooting

### WebSocket does not subscribe

Confirm the key is non-empty and the symbols list is populated:

```bash
docker compose exec backend printenv FINNHUB_API_KEY
```

An empty key disables the WS client (`monitor.finnhub.ws-enabled`). Check
`monitor.finnhub.api-key` resolved correctly in the logs.

### Yahoo Finance rate limiting

Yahoo public endpoints throttle aggressive polling. If you see intermittent `429`s, the
`yahooFinanceApi` resilience4j retry instance backs off automatically; sustained throttling
indicates the poll interval (`monitor.equity-price.poll-interval-ms`) is too aggressive.

## Reference

- Plan: `docs/plan_v6/11-deployment-operations.md` — "Finnhub WebSocket Configuration + Yahoo
  Finance Setup" runbook
- ADR-012 — free data source migration
