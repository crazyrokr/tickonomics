# Runbook: Perspective-Mismatch Signal (Perspex)

*Feature:* prediction-market vs OSINT-news divergence signals (ADR-037). This runbook covers the
Track A deterministic-only MVP. The LLM agent layer (Track B) is documented in ADR-037 and the plan
at `docs/perspex-perspective-mismatch-plan.md`.

## What it does

Polls **Polymarket** (Gamma API) for prediction-market quotes and **GDELT 2.0** for OSINT news events,
persists them to TimescaleDB, and runs a **deterministic** divergence check (market-implied probability
vs news-tone-derived probability). Actionable mismatches on equity-relevant markets are broadcast as
`SignalNotification` on `/ws/signals` and are inspectable via `GET /api/v1/perspex/mismatches`.

Everything is **disabled by default**. Enable the three sources independently.

## Enable

Set in `application.yml` (or env):

```yaml
monitor:
  polymarket:
    enabled: true
  osint:
    enabled: true
  perspex:
    enabled: true
```

- `polymarket` + `osint` start ingesting into `prediction_market_quotes` / `news_events`.
- `perspex` starts the `PerspexSignalGenerator` scheduler and serves the REST endpoint.

Neither source needs an API key (both are free and public).

## Key config (`monitor.perspex.*`)

| Knob | Default | Purpose |
|---|---|---|
| `poll-interval-ms` | 600000 | Signal-generation cadence |
| `lookback-minutes` | 240 | How far back to read quotes/events |
| `divergence-threshold` | 0.25 | Min \|market − news\| probability gap to emit |
| `min-liquidity` / `min-volume` | 1000 / 500 | Thin-market false-positive guard |
| `max-quotes` / `max-events` | 200 / 200 | Per-run data cap |
| `worker-function` | api/v1/perspex/analyze | Analytics-worker endpoint |
| `agent-enabled` | false | Track B LLM corroborator (not yet implemented) |

## Kill switch

Disable the whole feature: `monitor.perspex.enabled=false` (stops generation + the scheduler). To also
stop ingestion: set `monitor.polymarket.enabled=false` and `monitor.osint.enabled=false`.

## Trust gate / false positives

Every emitted signal is logged to the `IntersubjectiveAuditService` under
`CodingRule.PERSPECTIVE_MISMATCH_DETERMINISTIC` (IR 0.95, above the 0.9 gate). The generator broadcasts
only when `isActionable` is true. Markets are skipped (not signalled) when they are: below the
liquidity/volume floor (`skipped_thin`), non-equity / no cashtag (`skipped_no_ticker`), lacking
topically related news (`skipped_no_news`), or aligned with the news (`skipped_aligned`).

## Metrics (Micrometer / Prometheus)

- `ingestion.write.success{type=prediction_market|news}`, `ingestion.write.failure`, `ingestion.write.duplicate`
- `ingestion.buffer.prediction_market.size`, `ingestion.buffer.news.size`

## Common issues

- **No signals emitted.** Most likely cause: Polymarket questions rarely carry cashtags, so few markets
  resolve to an equity ticker (T4 filter). Confirm data is landing
  (`select count(*) from prediction_market_quotes; select count(*) from news_events;`) and inspect
  `GET /api/v1/perspex/mismatches` for the skip-reason counters.
- **503 from `/api/v1/perspex/mismatches`.** The analytics worker (`analytics.worker.url`, default
  `http://localhost:8001`) is unreachable. Check `GET /health` on the worker; the endpoint degrades to
  deterministic-only and never crashes when the worker is down.
- **LLM agents (Track B) misbehaving.** Not applicable in Track A. When Track B lands, malformed agent
  output is ignored (graceful degradation to deterministic-only) and the reflection cap
  (`PERSPEX_REFLECTION_MAX_STEPS`) bounds runaway reasoning.

## Data management

`prediction_market_quotes` and `news_events` are hypertables with 30-day compression and 365-day
retention (migrations V41/V42). See `docs/runbooks/data-management.md` for generic compression/retention
operations.
