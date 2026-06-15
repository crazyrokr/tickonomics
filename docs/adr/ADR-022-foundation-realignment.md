# ADR-022: Foundation Realignment to Cross-Sectional Research Goal

**Status:** Accepted
**Date:** 2026-06-14

## Context

Tickonomics is a personal quantitative research platform whose goal is to discover equity-trading signals that generalize and make money, validated ultimately by live/paper trading rather than in-sample statistics, with a deliberately broad edge scope (macro/regime, cross-sectional factors, technical, statistical/ML).

A decision-verification review on 2026-06-14 surfaced that several load-bearing choices had been made implicitly in code without an explicit decision, and that two of them conflict with the committed architecture:

1. The code hardcodes a 6-ETF universe (SPY, QQQ, IWM, TLT, HYG, GLD) and a SPY benchmark, but the goal requires a **broad cross-sectional stock universe** for factor ranking.
2. There is **no survivorship-bias or point-in-time handling** — the top source of illusory backtest alpha for the stated trust bar.
3. **ADR-012** committed the platform to a free-only data stack, which structurally cannot deliver survivorship-free, point-in-time, broad-universe cross-sectional data: Alpha Vantage free tier is 5 req/min; Yahoo/Alpha Vantage carry current symbols only (delisted names are absent); Ken French provides factor portfolio returns, not single-name data.

This ADR records the explicit decisions that resolve these gaps and amends ADR-012.

## Decision

**Primary (D1–D5):**

1. **Universe (D1):** broad cross-sectional stock universe (hundreds–thousands of names) for value/momentum/reversal ranking. The 6-ETF set is a placeholder to be replaced; the universe becomes config-driven with an explicit selection rule (top-N by market cap with a liquidity floor).
2. **Granularity (D2):** mixed / per-strategy — daily bars, intraday bars, and tick-level microstructure. Multi-granularity is a first-class platform concern; event-driven tick execution is in scope.
3. **Point-in-time correctness (D3):** survivorship-bias-free universe membership and corporate-action-adjusted data are foundation requirements to be satisfied before any backtest result is trusted.
4. **Strategy/engine boundary (D4):** the Java backtest engine (`computation`, per ADR-007) is the single system of record — one definition of a backtest and of "robust." Python is the research and ML-training surface; trained models cross to Java via ONNX (`onnxruntime` Java API) for inference. Rules-based strategies (factor/macro/technical) are authored natively in Java. Rationale: tick execution (D2) makes per-tick Python calls too slow, and the trust bar (D3) forbids two divergent backtest engines.
5. **Data-source amendment (D5):** add one targeted paid point-in-time, survivorship-bias-free source for the cross-sectional universe (e.g., Norgate, Sharadar, or Compustat). Free sources (ADR-012) are retained for macro, ETF, intraday, and ML-feature breadth. Specific provider pending a procurement comparison.

**Secondary (Tier-2, confirmed 2026-06-14):**

- **T1 — Universe storage:** config-driven with an explicit selection rule.
- **T2 — Benchmark:** changed from SPY to a broad total-market index (CRSP / Russell 3000) or an equal-weight universe benchmark.
- **T3 — Live broker:** a broker abstraction is defined now; live-broker implementation follows after paper validation. Paper trading continues via the existing engine (ADR-009).
- **T4 — Scope:** equities-only, single-currency (USD) for now.
- **T5 — Transaction costs:** model extended beyond Eq553 slippage (ADR-007) to include commissions and borrow/shorting costs, which are material for long-short cross-sectional strategies.
- **T6 — API surface:** the Java Spring API is the system-of-record / user-facing surface; the Python FastAPI service is research tooling (consistent with D4).
- **T7 — Reproducibility:** run-determinism already exists via ReproducibilityService (ADR-007: git SHA + dataset hash); point-in-time data correctness is provided by D5.

## Consequences

**Positive:**
- The data layer becomes capable of producing trustworthy cross-sectional signals, removing the structural survivorship-bias risk that would have invalidated backtest results.
- One backtest engine preserves a single definition of "robust," directly supporting the live/paper trust bar.
- Decisions are explicit and traceable, preventing silent drift between the goal and the implementation.

**Negative:**
- D5 reintroduces a recurring data cost (the single paid PIT source), partially reversing ADR-012's zero-cost stance.
- D1/T1 require reworking equity ingestion scale, universe management, the benchmark, and possibly the CDM shape away from a handful of instruments.
- D3/D5 add a point-in-time data-management burden (membership histories, corporate-action adjustments) to the foundation.
- D4 adds an ONNX export/inference bridge as a new foundation task.

## Amendment to ADR-012

ADR-012's free-only constraint is relaxed: the platform now uses free sources **plus one paid point-in-time source** (D5) to satisfy D1 and D3. All other aspects of ADR-012 (the seven free providers, CDM types, Flyway migrations V29–V32, fallback structure) remain in effect.

## Related ADRs

- **ADR-007** (backtesting framework) — provides BacktestEngine, Eq553 slippage, ReproducibilityService; D4 confirms Java as system of record.
- **ADR-009** (demo/virtual portfolio) — paper trading.
- **ADR-012** (free data sources) — amended by this ADR (D5).

## Open follow-ups

- Select the D5 point-in-time provider (Norgate vs Sharadar vs Compustat): cost, coverage, PIT depth, integration effort.
- Pin the T1 universe selection rule parameters and the T2 benchmark index.
- Build the D4 ONNX inference bridge and the D3/T1 point-in-time universe management as foundation tasks before evaluating real cross-sectional strategies.
