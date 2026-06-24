# Perspective-Mismatch Signal — Implementation & Integration Plan

*Source paper:* `docs/02-polygnosis-prediction-markets.md` (PolyGnosis 2.0, Wang et al., May 2026).
*Governing ADR:* `docs/adr/ADR-037-perspective-mismatch-signal.md`.

## Goal

Detect when prediction-market prices (Polymarket) and OSINT news coverage (GDELT) diverge
("perspective mismatch") and emit the divergence as an equity-keyed alpha signal through the existing
Java signal pipeline. The design encodes the paper's two findings as invariants: a **reflection cap**
and **deterministic validation that agents cannot override**.

## Locked decisions (see ADR-037)

| # | Decision | Value |
|---|---|---|
| 1 | LLM provider | **Provider-abstracted, local-first.** OpenAI-compatible runner (Ollama/vLLM/LM Studio/llama.cpp) via `openai` SDK; Anthropic optional. Malformed agent output → ignored (graceful degradation). |
| 2 | Sequencing | **Track A now** (deterministic-only MVP, zero LLM cost); **Track B after** the ADR-022/036 foundation. |
| 3 | Data sources | **Polymarket CLOB/Gamma + GDELT 2.0**, both free and key-less. |
| 4 | Signal identity | **Minimal** — generic `SignalNotification` on `/ws/signals` + optional asyncapi `category`. `EM.01` catalog deferred. |

**Deterministic-sufficient rule (centerpiece):** a deterministic divergence check is *necessary +
authoritative* to emit; agents are corroborative-only. `CodingRule` IR scores:
`PERSPECTIVE_MISMATCH_DETERMINISTIC = 0.95` (passes the 0.9 gate), `PERSPECTIVE_MISMATCH_AGENT = 0.70`
(agent-only path suppressed by the trust gate).

## Architecture / data flow

```
[Polymarket CLOB]──┐                                  ┌─ deterministic divergence (Python, hard check)
                   ├─ ingestion (Java) ── TimescaleDB ─┤
[GDELT 2.0 events]─┘   new CDM types + hypertables    └─→ perspex engine (Python; agents in Track B)
                                                                │ verdict = deterministic (Track A)
   Java PerspexSignalGenerator ←── sendAnalysisRequest ────────┘
        │  IntersubjectiveAuditService.logTransformation (CodingRule IR scores)
        │  trust gate (irScore >= 0.9) ── pass → AlphaSignal
        └→ SignalWebSocketHandler.broadcast(/ws/signals)  → dashboard
```

---

## Track A — now (foundation-independent, deterministic-only MVP)

### A0 · ADR + config
- `docs/adr/ADR-037-perspective-mismatch-signal.md` (this plan's governing record).
- `app/src/main/resources/application.yml`: `monitor.polymarket.*`, `monitor.osint.*`,
  `monitor.perspex.*` (`enabled: false` defaults), `resilience4j` bulkhead/retry instances.

### A1 · Data foundation (Java ingestion — CdmAdapter pattern)

**CDM (`cdm` module):**
| File | Purpose |
|---|---|
| `cdm/adapter/raw/PolymarketQuote.java` | record: `time, marketId, slug, question, outcomeYesPrice, volume, liquidity, source` |
| `cdm/adapter/raw/OsintEvent.java` | record (GDELT shape): `time, eventId, source, headline, avgTone, themes, actors, sourceUrl` |
| `cdm/model/CdmPredictionMarketQuote.java`, `CdmNewsEvent.java` | CDM types |
| `cdm/adapter/PolymarketCdmAdapter.java`, `OsintCdmAdapter.java` | `implements CdmAdapter<Raw, Cdm>` |

**Ingestion (`ingestion` module):**
| File | Purpose |
|---|---|
| `ingestion/polymarket/PolymarketClient.java` | `@Component @ConditionalOnProperty @Scheduled @Bulkhead("criticalIngestion") @Retry`; Polymarket Gamma/CLOB REST |
| `ingestion/osint/OsintClient.java` | same pattern; GDELT 2.0 Events API |
| `TimescaleDbWriter.java` | add `writePredictionMarketQuote(...)`, `writeNewsEvent(...)` + buffers/flush/metrics |
| persistence `PredictionMarketQuote`, `NewsEvent` entities + repos | `saveAllIdempotent` pattern |

**Migrations (`persistence/.../db/migration/`) — add new, never edit applied:**
| File | Purpose |
|---|---|
| `V41__add_prediction_market_quotes.sql` | hypertable `prediction_market_quotes` |
| `V42__add_news_events.sql` | hypertable `news_events` + GIN index on headline/themes |

### A2 · Deterministic mismatch engine (Python, no LLM)
| File | Purpose |
|---|---|
| `analytics/app/services/perspex/_deterministic.py` | numeric/statistical divergence of market-implied vs news-tone-derived probability; resolves mismatch → affected equity tickers (non-equity filtered); returns `{score, direction, confidence, tickers, provenance}` |
| `analytics/app/services/perspex/perspex_service.py` | public orchestrator: query recent quotes+events → deterministic check → emit verdict |
| `analytics/app/routers/perspex.py` | `POST /api/v1/perspex/analyze` |
| `analytics/app/models/{requests,responses}.py` | `PerspexAnalyzeRequest`, `PerspexAnalyzeResponse` |
| `analytics/app/main.py` | register router |
| **No new dependency** | (Track A uses no LLM) |

### A4-deterministic · Java integration
| Layer | Change |
|---|---|
| `computation/audit/CodingRule.java` | add `PERSPECTIVE_MISMATCH_DETERMINISTIC` (IR 0.95) |
| `computation/perspex/PerspexSignalGenerator.java` (new `@Component @Scheduled`) | `AnalyticsWorkerClient.sendAnalysisRequest("perspex/analyze", …)` → `IntersubjectiveAuditService.logTransformation(...)` → trust gate → `AlphaSignal` → `SignalWebSocketHandler.broadcast(SignalNotification(...))` |
| `api-contracts/.../openapi.yaml` | `GET /api/v1/quant/signals/perspective-mismatch` + schema (keep `ApiContractsSpec` green) |
| `api-contracts/.../asyncapi.yaml` | optional `category` field on `SignalNotification` |
| `web/.../controller/QuantController.java` | implement the new GET endpoint |

### A5 · Paper validation + observability
- Paper-trade deterministic signals via ADR-009 demo engine.
- Micrometer: `perspex_deterministic_verdicts`, `perspex_signals_emitted`, `perspex_signals_gated`, `perspex_ingestion_lag`.
- Runbook `docs/runbooks/perspex-mismatch.md`; kill-switch `monitor.perspex.enabled=false`.

**Track A tests (Given-When-Then, false-positive focus):** deterministic divergence math; aligned
market+news → no signal; stale/low-liquidity → neutral; non-equity event → filtered; Java trust-gate
suppression; CdmAdapter round-trips; client fixture parsing (Polymarket/GDELT); `TimescaleDbWriter` new
flushes; `ApiContractsSpec` green.

---

## Track B — deferred until after the ADR-022/036 foundation

### B3 · LLM agent layer (findings #1 + #2)
| File | Purpose |
|---|---|
| `analytics/app/services/perspex/_llm.py` | `LlmClient` Protocol + `AnthropicLlmClient`, `OpenAiCompatibleLlmClient` (Ollama/vLLM/LM Studio/llama.cpp), `FakeLlmClient` (tests) |
| `analytics/app/services/perspex/_agents.py` | N independent diverse-prompt agents; **reflection cap** (`PERSPEX_REFLECTION_MAX_STEPS`), cost/run **resource budget**, consensus-divergence detection; lazy SDK import; structured-output repair + lenient JSON extraction; malformed → ignored |
| `analytics/app/services/perspex/perspex_service.py` (extended) | agents run only on deterministic-flagged candidates; agent output = annotation + optional confidence boost; agent-only never emits |
| `analytics/requirements.txt` | `anthropic` + `openai` (httpx already present) |
| `analytics/app/config.py` | `PERSPEX_LLM_PROVIDER`, `PERSPEX_LLM_BASE_URL`, `PERSPEX_LLM_MODEL`, `PERSPEX_LLM_API_KEY`, `PERSPEX_REFLECTION_MAX_STEPS`, `PERSPEX_MIN_AGREE`, `PERSPEX_COST_BUDGET_USD`, `PERSPEX_RUN_BUDGET_SECONDS` |
| `computation/audit/CodingRule.java` | add `PERSPECTIVE_MISMATCH_AGENT` (IR 0.70) |
| `docker-compose.dev.yml` | optional `ollama` service under `profiles: [local-llm]`; document `ollama pull qwen2.5:7b-instruct` |

**Local defaults:** provider `ollama`, base_url `http://localhost:11434/v1`, model `qwen2.5:7b-instruct`,
no api key. Cloud: provider `anthropic`, model `claude-haiku-4-5-20251001`.

**B3 tests:** reflection-cap enforcement; agent-only-without-deterministic suppressed; consensus-divergence
detection; cost/run-budget trip; malformed-output-ignored (mock SDKs, no network); OpenAI-compat client
against httpx-mocked Ollama.

### B4-deep / B5 · Optional
- `EM.01` event/macro catalog family + backtest wiring (only if paper P&L justifies it).
- Frontend mismatch panel (`npm run generate:api`).

---

## Guardrails from the paper (testable invariants across both tracks)
1. **Reflection cap** — `PERSPEX_REFLECTION_MAX_STEPS` hard stop (B3 test enforces).
2. **Consensus bias → hard validation** — deterministic is necessary+authoritative; agents corroborative-only; agent-only path IR 0.70 < gate → suppressed (holds in Track A trivially; enforced in Track B).
3. **"Doing less mattered more"** — Track A ships value with zero LLM spend; agents are opt-in corroborators.

## Build / verify gates
- Java: `./gradlew build -x :integration-tests:test` (CI gate); `./gradlew lint` advisory.
- Python: `python -m pytest tests -m "not benchmark"`; `ruff check .` advisory.
- API: `ApiContractsSpec` must stay green; frontend `npm run generate:api` (Track B).
- After code lands: `graphify update .`.
