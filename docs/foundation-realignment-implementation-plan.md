# Foundation Realignment — Implementation Plan

**Status:** Draft (uncommitted) · **Date:** 2026-06-14 · **Governing ADR:** [ADR-022](adr/ADR-022-foundation-realignment.md)

## Context

ADR-022 verified that the foundation was built for the wrong target: a hardcoded 6-ETF universe, no point-in-time / survivorship handling, on a free-only data stack (ADR-012) that cannot satisfy the cross-sectional research goal. This plan is the dependency-ordered work that puts the foundation on the verified target (D1–D5 + Tier-2). It is also the live tracker — update task status here as work proceeds, since it is the source of truth for progress.

## Dependency graph

```
[1] Sharadar PIT data source (D5)
      │  blocks
      ▼
[2] PIT universe mgmt + selection rule (D3/T1)
      │  blocks
      ▼
[3] Replace hardcoded universe + swap benchmark (D1/T1/T2) ──┐
                                                              │
[4] Cost model: commissions + borrow/shorting (T5) ──────────┤ all three block
[5] ONNX inference bridge (D4) ───────────────────────────────┤
                                                              ▼
                                                  [6] Validation gate (trust bar)
```

Tasks **4** and **5** are independent and can proceed in parallel with the 1→2→3 chain.

## Task tracker

Legend: **Ready** · **In progress** · **Blocked** · **Done**

| ID | Task | Decision | Depends on | Status |
|----|------|----------|-----------|--------|
| 1 | Integrate survivorship-free PIT data source (Sharadar SEP+SF1) | D5 | — | Ready |
| 2 | PIT universe management + selection rule | D3/T1 | 1 | Blocked |
| 3 | Replace hardcoded 6-ETF universe + swap benchmark | D1/T1/T2 | 2 | Blocked |
| 4 | Extend cost model (commissions + borrow/shorting) | T5 | — | Ready |
| 5 | ONNX inference bridge (Java engine) | D4 | — | Ready |
| 6 | Validation gate: walk-forward + bias-free regression | trust bar | 3,4,5 | Blocked |

---

## Task detail

Each task follows the repo's Given-When-Then test discipline with explicit focus on edge cases and false-positive scenarios (per project conventions).

### 1 — Integrate survivorship-free PIT data source (Sharadar SEP+SF1) · D5

**Scope**
- Confirm 2026 pricing and subscribe at [data.nasdaq.com](https://data.nasdaq.com) (SEP equity prices + SF1 fundamentals).
- Build a Sharadar ingestion client following the ADR-012 pattern: `RestClient` → `CdmAdapter` → `TimescaleDbWriter` with idempotency guards.
- Add CDM types for PIT prices and PIT fundamentals; add a Flyway migration for the new tables.

**Definition of done**
- A known delisted symbol resolves with full historical price history (survivorship-free).
- A PIT fundamentals snapshot returns the value known as-of a given date, not a later restated value.
- Given-When-Then unit tests cover the happy path and false-positives: restatement leakage, missing-delisted, duplicate-id idempotency.

**Gates:** task 2.

### 2 — PIT universe management + selection rule · D3/T1

**Scope**
- Design a point-in-time universe model: as-of-date membership, corporate-action-adjusted prices, active + delisted from the D5 source.
- Build a config-driven selection-rule engine (top-N by market cap with a liquidity floor, evaluated as-of each rebalance date).
- Flyway migration for universe as-of snapshots.

**Definition of done**
- A lookahead test proves no future information leaks into an as-of query (the core D3 guarantee).
- A stock delisted before period end is included only up to its delisting date.
- Rebalance-boundary membership is correct on the rebalance date.
- Given-When-Then tests focus on edge cases and false-positive scenarios.

**Gates:** task 3. **Blocked by:** 1.

### 3 — Replace hardcoded universe + swap benchmark · D1/T1/T2

**Scope**
- Remove the hardcoded SPY/QQQ/IWM/TLT/HYG/GLD universe and SPY benchmark; drive the universe from the PIT model (task 2).
- Swap the benchmark to a broad total-market index (CRSP / Russell 3000) or equal-weight universe.
- Re-point the 25 existing equity strategies (ADR-007) onto the PIT universe — re-evaluate any tuned to the 6-ETF set.

**Definition of done**
- No hardcoded symbol universe remains in source (`grep`-verified).
- Benchmark is configurable.
- Existing strategies run against the broad PIT universe.
- Given-When-Then tests confirm config-driven universe resolution.

**Blocked by:** 2.

### 4 — Extend cost model (commissions + borrow/shorting) · T5

**Scope**
- Eq553 (ADR-007) models slippage only. For long-short cross-sectional strategies, add commission and borrow/shorting-cost components on top of Eq553 (keep it the single source of truth).

**Definition of done**
- A long-short net-cost scenario shows borrow fees eroding a short premium.
- Given-When-Then tests cover long, short, and rebalance-turnover cost paths.

**Independent** — can start in parallel with the 1→2→3 chain.

### 5 — ONNX inference bridge (Java engine) · D4

**Scope**
- Define the strategy-inference contract: the Java backtest engine consumes an ONNX model + feature vector and returns a signal.
- Integrate `onnxruntime-java`; define the export path from pytorch training (`torch.onnx.export`) so Python-trained models run inside the Java engine without per-tick Python calls.

**Definition of done**
- A parity test confirms Java ONNX inference matches Python training-time inference within tolerance on a fixed feature vector.
- Given-When-Then tests cover model load, inference, and missing-feature handling.

**Independent** — can start in parallel.

### 6 — Validation gate: walk-forward + bias-free regression · trust bar

**Scope**
- Stand up a walk-forward / out-of-sample split harness on the new foundation.
- A regression test that runs one known factor (e.g., cross-sectional momentum) on the PIT universe and compares against a deliberately survivorship-biased baseline — the bias-free run must not show inflated alpha.

**Definition of done**
- Walk-forward produces out-of-sample metrics.
- The bias regression test fails if survivorship bias is reintroduced (the safety net for D3).
- This is the gate that earns the live/paper trust bar.

**Blocked by:** 3, 4, 5.

---

## Open follow-ups (do not block the chain)

- **D5 provider confirmation:** finalize Sharadar vs Norgate. Recommendation is Sharadar (clean REST API, Linux/Java fit); Norgate remains the fallback if index-membership timeseries is later required (accepting the Windows export bridge).
- **T1 parameters:** pin the top-N market-cap threshold and liquidity floor.
- **T2 index:** pick CRSP total market vs Russell 3000 vs equal-weight.

## Entry point

Start with **task 1** (Sharadar integration) and **task 4** or **5** in parallel — all three are unblocked.
