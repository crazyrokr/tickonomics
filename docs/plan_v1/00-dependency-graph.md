# Implementation Dependency Graph

## Analysis Findings Applied

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

