# Implementation Dependency Graph

## Plan Version

This is **plan v3** — extends v2 with improvement proposals from
`PROPOSALS_FOR_IMPROVEMENT.md`. See changelog at the end of each file.

## Analysis Findings Applied (v1)

This dependency graph reflects the analysis findings from `PLAN_V7_ANALYSIS_FINDINGS.md`:

- **Finding 1 (Virtual Threads Primary, WebFlux-Ready):** Virtual Threads + Spring MVC is the
  primary runtime. No dual-mode parity requirement and no cross-stack consistency tests. However,
  the shared service layer is defined behind interfaces so that WebFlux adapters can be added
  in the future as a separate module (`webflux/`) without rewriting business logic.
- **Finding 2 (AIC Loop in Python Worker):** AIC lag selection pushed entirely into the Python
  analytics worker (Track 3). Java sends data once, receives optimal result. Arrow IPC replaces
  REST/JSON for high-throughput time-series transfer.
- **Finding 5 (Direct FRED/NY Fed Clients):** Core ILI data sources (FRED, NY Fed) fetched via
  direct lightweight Java HTTP clients — no OpenBB dependency on the critical ingestion path.
  OpenBB sidecar retained only for long-tail analytics (econometrics, factor analysis).

## External Integration Decisions (v2)

From `EXTERNAL_INTEGRATION_PLAN.md`:

- **FINOS CDM (Adopt — Subset):** Use a CDM projection for instrument type standardization
  in API contracts and database schema. Adopt only the instrument types consumed by tickonomics
  (Repo, SOFR, T-Bill, equity). Avoid full model adoption — limits complexity and boilerplate.
- **Perspective (Adopt — Optional):** Use FINOS Perspective for high-frequency data grids
  and heatmaps in the analytics dashboard. Complements D3.js for data-heavy panels (correlation
  matrix, signal log, liquidity heatmap). Visual integration risk mitigated by confining to
  tabular/grid contexts.
- **FINOS TimeBase-CE (Defer):** Noted as future high-performance backup to TimescaleDB.
  Deferred due to operational burden and contradiction of single-DB architecture.
- **FINOS FDC3 (Defer):** Noted as future dashboard interoperability standard. Deferred until
  integration with professional financial desktops (Bloomberg, Reuters) is required.

---

## Parallel Execution Tracks

```
Track 1: Scaffolding & API Contracts (Phase 0)
    ↓
    ├── Track 2: Database Schema (Phase 0)
    │       ↓
    │       ├── Track 4: Ingestion Layer (Phase 1)
    │       │       ↓
    │       │       └── Track 10: Demo / Virtual Portfolio (Phase 6)
    │       │
    │       └── Track 5: Computation Engine (Phase 2)
    │               ↓
    │               ├── Track 10: Demo / Virtual Portfolio (Phase 6)
    │               └── Track 8: Backtesting Framework (Phase 5)
    │
    └── Track 7: Analytics Dashboard (Phase 4)

Track 3: Python Analytics Worker ────────────────── feeds into Track 5 (Arrow IPC)
Track 6: Landing Page ──────────────────────────── feeds into Track 10
Track 9: CI/CD Pipeline ────────────────────────── independent of all tracks
Track 11: Deployment & Operations ──────────────── depends on all tracks
```

## Parallelization Rules

| These tracks can run FULLY in parallel     | Reason                                                  |
|:-------------------------------------------|:--------------------------------------------------------|
| Track 2, Track 3, Track 6, Track 9         | Different languages/ecosystems, no shared code          |
| Track 4, Track 5 (after Track 2 completes) | Ingestion and computation are decoupled by the database |
| Track 7, Track 6                           | Two separate Next.js apps with no shared runtime code   |

| This track MUST complete first              | Before these can start             |
|:--------------------------------------------|:-----------------------------------|
| Track 1 (Scaffolding & API Contracts)       | Track 2, Track 4, Track 5, Track 7 |
| Track 2 (Database Schema)                   | Track 4, Track 5                   |
| Track 4 (Ingestion) + Track 5 (Computation) | Track 10 (Demo)                    |
| Track 5 (Computation)                       | Track 8 (Backtesting)              |
| All tracks                                  | Track 11 (Deployment)              |

## File Index

| File                              | Track | Phase(s) | Can start when   |
|:----------------------------------|:------|:---------|:-----------------|
| `01-scaffolding-api-contracts.md` | 1     | Phase 0  | Immediately      |
| `02-database-schema.md`           | 2     | Phase 0  | After Track 1    |
| `03-analytics-worker.md`          | 3     | Phase 0  | Immediately      |
| `04-ingestion-layer.md`           | 4     | Phase 1  | After Track 2    |
| `05-computation-engine.md`        | 5     | Phase 2  | After Track 2    |
| `06-landing-page.md`              | 6     | Phase 3  | Immediately      |
| `07-analytics-dashboard.md`       | 7     | Phase 4  | After Track 1    |
| `08-backtesting-framework.md`     | 8     | Phase 5  | After Track 5    |
| `09-cicd-pipeline.md`             | 9     | Phase 7  | Immediately      |
| `10-demo-virtual-portfolio.md`    | 10    | Phase 6  | After Tracks 4+5 |
| `11-deployment-operations.md`     | 11    | Phase 8  | After all tracks |

## Key Architectural Decisions (from Analysis)

| Decision                                         | Rationale                                                                                                                                                                                                         |
|:-------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Virtual Threads primary, WebFlux-ready           | Virtual Threads + MVC is the primary runtime. Shared service layer uses interfaces so WebFlux adapters can be added later as a separate module without rewriting business logic. No dual-mode parity requirement. |
| Arrow IPC for sidecar communication              | Eliminates JSON serialization bottleneck for time-series arrays. Critical for AIC loop and batch analytics.                                                                                                       |
| Direct FRED/NY Fed Java clients                  | Removes OpenBB sidecar from the critical ILI ingestion path. OpenBB reserved for econometrics only.                                                                                                               |
| Real-Time Aggregates (`materialized_only=false`) | Transparent live+materialized data merge in TimescaleDB eliminates the 1-minute aggregate blind spot.                                                                                                             |
| Proxy Divergence Guard                           | Detects T-Bill/SOFR dislocation during flight-to-quality events and suppresses stale proxy signals.                                                                                                               |
| Dynamic Weighting for zero-variance              | Redistributes weight among valid components instead of masking stale data as neutral (0.0).                                                                                                                       |
| Chronicle Queue on dedicated storage             | `tmpfs` or separate NVMe partition prevents I/O contention with TimescaleDB.                                                                                                                                      |

## External Integration Decisions (v2)

| Decision                              | Status               | Rationale                                                                                                                                            |
|:--------------------------------------|:---------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| FINOS CDM subset for instrument types | **Adopt**            | Canonical instrument model for Repo, SOFR, T-Bill, equity. Reduces ad-hoc enum proliferation in API contracts and schema. Applied to Tracks 1, 2, 5. |
| Perspective for data grids/heatmaps   | **Adopt (optional)** | High-performance grid/chart for correlation matrix, signal log, liquidity heatmap. Complements D3.js. Applied to Track 7.                            |
| FINOS TimeBase-CE                     | **Defer**            | Future backup to TimescaleDB. High operational burden, contradicts single-DB architecture. Noted in Track 11.                                        |
| FINOS FDC3                            | **Defer**            | Future desktop interoperability. Only relevant for Bloomberg/Reuters integration. Noted in Track 7.                                                  |

## Improvement Proposals (v3)

From `PROPOSALS_FOR_IMPROVEMENT.md`:

- **Proposal #2 (Wait-Free Ingestion — Adopt selective):** `TimescaleDbWriter` uses off-heap,
  GC-friendly buffer patterns. Chronicle Queue configured for memory-mapped files on dedicated
  NVMe partition. Prevents GC-induced latency spikes during market events. Applied to Track 4.
- **Proposal #3 (Pre-computed KPI Views — Adopt):** Rolling correlation and beta pre-computed
  as TimescaleDB continuous aggregates using user-defined aggregates. Reduces per-cycle TA-Lib
  computation load for stable-window KPIs. Applied to Tracks 2, 5.
- **Proposal #5 (CDM Adapter in IngestionLayer — Adopt):** Raw data from `FederationDataClient`
  mapped to CDM objects at the ingestion boundary via `CdmAdapter`. Computation engine consumes
  CDM-typed data regardless of source (FRED, NY Fed, Polygon). Prevents source-specific field
  mismatch in ILI calculation. Applied to Tracks 1, 4.
- **Proposal #1 (Tiered Storage — Defer):** QuestDB hot path reintroduces dual-DB operational
  burden. Current TimescaleDB handles ~10 symbols of tick data adequately. Re-evaluate only if
  write throughput becomes a measured bottleneck. Noted in Track 11.
- **Proposal #4 (Shared-Memory Arrow IPC — Defer):** Only beneficial for same-host deployments.
  Containerized architecture uses separate containers — shared memory undermines isolation.
  Socket-based Arrow IPC is sufficient. Revisit for bare-metal deployments. Noted in Track 11.

| Decision                                | Status                | Rationale                                                                                                     |
|:----------------------------------------|:----------------------|:--------------------------------------------------------------------------------------------------------------|
| Wait-free ingestion (off-heap, zero-GC) | **Adopt (selective)** | Prevents GC pauses during market events. Off-heap buffer + memory-mapped Chronicle Queue. Applied to Track 4. |
| Pre-computed KPI views in TimescaleDB   | **Adopt**             | Rolling correlation/beta as continuous aggregates. Reduces Java computation load. Applied to Tracks 2, 5.     |
| CDM Adapter in IngestionLayer           | **Adopt**             | CDM-typed data at ingestion boundary. Source-agnostic computation. Applied to Tracks 1, 4.                    |
| QuestDB tiered storage                  | **Defer**             | Dual-DB operational burden. ~10 symbols don't justify 4M rows/sec capacity. Noted in Track 11.                |
| Shared-memory Arrow IPC                 | **Defer**             | Container isolation conflict. Socket IPC sufficient. Noted in Track 11.                                       |

---

## Changelog

| Version | File                     | Change                                                                                                                                                                                                                   |
|:--------|:-------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | `00-dependency-graph.md` | Applied analysis findings: Virtual Threads primary, Arrow IPC, direct FRED/NY Fed clients. Added architectural decisions table.                                                                                          |
| v2      | `00-dependency-graph.md` | Added "External Integration Decisions" section with FINOS CDM (adopt), Perspective (adopt), TimeBase-CE (defer), FDC3 (defer). Added decision table.                                                                     |
| v3      | `00-dependency-graph.md` | Added "Improvement Proposals" section: wait-free ingestion (adopt), pre-computed KPI views (adopt), CDM adapter (adopt), QuestDB tiered storage (defer), shared-memory Arrow IPC (defer). Added proposal decision table. |
