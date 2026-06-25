# ADR-037: Perspective-Mismatch Signal (PolyGnosis-Inspired)

**Status:** Proposed
**Date:** 2026-06-24

## Context

`docs/02-polygnosis-prediction-markets.md` summarizes *PolyGnosis 2.0* (Wang et al., May 2026): two
independent "mood" streams — prediction-market prices and OSINT news/event coverage — are stitched
together, and an agent team flags **perspective mismatches** (moments when the market's implied mood and
the media's mood diverge) as candidate early trading signals. Two findings from the paper are
load-bearing for *how* we build this, not just *whether*:

1. **Uncapped reflection produces confident drift** — more reasoning made the agents worse, not better.
   Reflection must be hard-capped.
2. **Consensus bias produces shared error** — agents converged on the same verdict regardless of the
   evidence in front of them. The fix is **hard, deterministic validation the agents cannot talk their
   way around.**

Tickonomics is an edge-agnostic personal quant-research sandbox (ADR-022) whose current strategic focus
is a broad cross-sectional equity universe. Prediction markets and OSINT news are a different asset class
/ signal type (event/regime alpha), so this feature must be positioned deliberately relative to the
foundation work.

## Decision

**Primary (D1–D8):**

1. **Positioning (D1):** the perspective-mismatch detector is an **optional, config-gated,
   research-track event/regime alpha source**. It is **not** part of the single Java backtest engine
   (ADR-007 / ADR-022 D4) and does **not** block the ADR-022 / ADR-036 foundation. It ships disabled by
   default (`monitor.perspex.enabled=false`).

2. **Data sources (D2):** **Polymarket** (CLOB/Gamma public API) and **GDELT 2.0 Events** (the global
   news-event database the paper used) — both free and key-less, consistent with ADR-012's retention of
   free sources for macro/event breadth. They get **new CDM types and hypertables**; they are *not*
   forced into `tick_data` / `InstrumentType`, which are reserved for the equity cross-sectional path.

3. **Deterministic-sufficient rule (D3):** a **deterministic divergence check** (numeric/statistical
   comparison of market-implied probability vs news-tone-derived probability) is **necessary and
   authoritative** to emit a signal. LLM agents, when enabled, are **corroborative only and never
   authoritative**. This directly instantiates the paper's finding #2 using the platform's existing
   **IR-Score trust gate** (ADR-011 / `IntersubjectiveAuditService`, `BaseEquityStrategy.compute` returns
   neutral when `irScore < 0.9`).

4. **CodingRule IR scores (D4):** add two rules to `CodingRule`:
   - `PERSPECTIVE_MISMATCH_DETERMINISTIC` — default IR **0.95** (passes the 0.9 gate on its own).
   - `PERSPECTIVE_MISMATCH_AGENT` — default IR **0.70** (an agent-only path scores below the gate and is
     therefore **suppressed** by the trust gate, for free — no special-case code).

5. **LLM provider abstraction (D5):** the agent layer is **provider-abstracted and local-first**. Any
   **OpenAI-compatible** runner (Ollama, vLLM, LM Studio, llama.cpp-server) is reached via the `openai`
   SDK with a configurable `base_url`; Anthropic is optional. Smaller local models emit unreliable
   structured output, so a **malformed agent response is ignored** and the system degrades cleanly to
   deterministic-only — making the local path safe to default to.

6. **Guardrails (D6):** **reflection cap** (`PERSPEX_REFLECTION_MAX_STEPS`), **consensus-divergence
   detection** across independent diverse-prompt agents, and a **generalized resource budget** (USD for
   cloud, wall-clock for local) that stops spawning agents when tripped while still allowing deterministic
   signals to emit.

7. **Equity scope (D7):** consistent with ADR-022 T4 (equities-only, USD), the engine **resolves each
   mismatch to affected equity ticker(s)**; non-equity markets/events are filtered out and do not emit
   signals.

8. **Signal identity (D8):** **minimal** — emit a generic `SignalNotification` (symbol = resolved ticker)
   on the existing `/ws/signals` channel; add an **optional `category`** field to the AsyncAPI
   `SignalNotification` so the dashboard can filter mismatch signals. A dedicated `EM.01` (event/macro)
   strategy-catalog family and backtest wiring are **deferred** pending paper-trading validation.

**Phasing:**

- **Track A (now, foundation-independent, deterministic-only MVP):** ADR + config, data foundation
  (ingestion + CDM + migrations), deterministic engine (Python), Java signal-pipeline integration
  (CodingRule + `PerspexSignalGenerator` + trust gate + WebSocket broadcast), API contract, paper-trading
  validation + observability. Zero LLM cost.
- **Track B (after the ADR-022 / ADR-036 foundation):** the LLM agent layer (D5/D6), full deep wiring,
  and the optional frontend panel.

## Consequences

**Positive:**

- Delivers a new event/regime alpha source **without** disturbing the single backtest engine or the
  foundation work.
- Reuses the IR-Score trust gate to enforce the paper's hard-validation finding with no new gating
  machinery.
- Local-first LLM keeps market/news context on-box (privacy), costs nothing, and degrades gracefully.
- The deterministic MVP is useful and testable in isolation, before any LLM dependency is introduced.

**Negative:**

- Adds two new ingestion sources, two new hypertables, new CDM types, and a new Python service domain —
  surface-area growth on a foundation-focused branch (mitigated by `enabled:false` defaults and
  self-contained phasing).
- Track B adds LLM runtime/ops burden (Ollama service, model pulls, latency) and two SDK dependencies.
- Event→ticker resolution (D7) is heuristic and a source of false positives; it is covered by tests and
  gated downstream.

## Related ADRs

- **ADR-022** (foundation realignment) — positioning, equity scope (T4), single-backtest-engine boundary
  (D4); this feature is deliberately orthogonal and non-blocking.
- **ADR-012** (free data sources) — Polymarket + GDELT extend the free macro/event breadth.
- **ADR-011** (gap elimination / IR Score) — the trust gate that enforces D3/D4.
- **ADR-007** (backtesting framework) — `BacktestEngine` / `ReproducibilityService`; this feature stays
  out of that path.
- **ADR-009** (demo/virtual portfolio) — paper-trading validation of emitted signals.
- **ADR-036** (post-review remediation architecture) — lazy-import / model-checkpoint patterns reused by
  the Python engine; Track B does not precede this.

## Open follow-ups

- Implement Track B (LLM agent layer) once the ADR-022/036 foundation is in place.
- Pin the event→ticker resolution heuristic (D7) and validate its precision/recall on historical
  Polymarket + GDELT data.
- Decide whether paper-trading edge justifies the deferred `EM.01` catalog family + backtest wiring.
