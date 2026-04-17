# Implementation Dependency Graph

## Plan Version

This is **plan v4** — extends v3 with seven consolidated proposals:

- **01-CONSOLIDATED_DATA_QUALITY_MODULE.md** — Autoencoder anomaly detection, stochastic drift-diffusion
- **02-CONSOLIDATED_RESILIENCE_OPERATIONS_MODULE.md** — Idempotency, tracing, LKG cache, bulkheads, chaos testing,
  participation governance, liquidity stress testing
- **03-CONSOLIDATED_REGIME_DETECTION_MODULE.md** — GARCH regime engine, CNN-LSTM, QED, event-based time, climate
  sensitivity, natural disaster shocks
- **04-CONSOLIDATED_WEIGHT_OPTIMIZATION_MODULE.md** — Bayesian optimizer, Firefly algorithm, RAHF framework, ambiguity
  aversion, efficiency-depth weighting
- **05-CONSOLIDATED_BACKTESTING_ROBUSTNESS_MODULE.md** — Multi-dimensional parameter scanning, Sobol simulation, Riemann
  Zeta discrete monitoring, return gap diagnostic, ML reproducibility, regulatory behavioral testing, de-rounding,
  periodicity filter, price impact, price jump ratio
- **06-CONSOLIDATED_RISK_GUARDRAILS_MODULE.md** — Market stability guardrails, AUMF uncertainty management, systemic
  resilience monitor, auditability, algorithmic price stability, algorithm aversion mitigation, market gamma/GEX monitor
- **07-CONSOLIDATED_LIQUIDITY_ANALYSIS_MODULE.md** — Liquidity comovement, phantom liquidity detection, trader toxicity,
  algorithmic intensity KPI, trader-type dynamics, behavioural liquidity guard, intraday pattern alignment, intraday
  session filter, AT liquidity impact

See changelog at the end of each file.

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

## Consolidated Proposal Integration (v4)

### Proposal 01: Data Quality Module

| Decision                             | Status              | Rationale                                                                                                                              |
|:-------------------------------------|:--------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| Autoencoder anomaly detection        | **Adopt**           | Data-driven anomaly detection replacing fixed thresholds. Adapts to market conditions. Applied to Tracks 2, 3, 4, 5.                   |
| Stochastic drift-diffusion framework | **Adopt (Phase 2)** | Ito process framework for continuous parameter monitoring. Complements autoencoder point-in-time detection. Applied to Tracks 3, 5, 8. |

### Proposal 02: Resilience and Operations Module

| Decision                          | Status              | Rationale                                                                                                                                 |
|:----------------------------------|:--------------------|:------------------------------------------------------------------------------------------------------------------------------------------|
| Idempotency keys on ingestion     | **Adopt**           | Exactly-once delivery semantics during retries. Prevents duplicate tick data from skewing ILI. Applied to Tracks 2, 4.                    |
| OpenTelemetry distributed tracing | **Adopt**           | End-to-end latency visibility across Java-Python chain. Applied to Track 9.                                                               |
| Last Known Good (LKG) cache       | **Adopt**           | Prevents NaN/empty results when sources are unreachable. Applied to Tracks 4, 5, 7.                                                       |
| Bulkhead executor isolation       | **Adopt**           | Prevents cascading thread pool starvation. Three dedicated pools for ingestion, computation, and high-volume WS. Applied to Tracks 5, 11. |
| Chaos testing in CI               | **Adopt**           | Validates resilience patterns under adverse conditions. Applied to Track 9.                                                               |
| Participation governance          | **Adopt (Phase 2)** | Formalizes signal admissibility decomposition. Makes decision logic auditable. Applied to Track 5.                                        |
| Liquidity stress testing          | **Adopt (Phase 2)** | Simulates extreme liquidity withdrawal (Swiss franc model). Applied to Tracks 5, 8.                                                       |

### Proposal 03: Regime Detection Module

| Decision                         | Status                | Rationale                                                                                                                             |
|:---------------------------------|:----------------------|:--------------------------------------------------------------------------------------------------------------------------------------|
| GARCH regime engine (PRIMARY)    | **Adopt**             | Replaces K-means with conditional variance classification. Superior volatility regime detection. Applied to Tracks 3, 5, 7, 8.        |
| Online weight optimizer (SGD)    | **Adopt**             | Self-tuning ILI weights based on signal performance. Reduces manual recalibration. Applied to Tracks 3, 5.                            |
| CNN-LSTM regime (Alternative A)  | **Adopt (Pluggable)** | Deep learning ensemble for spatial+temporal dependencies. A/B testing candidate. Applied to Tracks 3, 5.                              |
| QED model (Alternative B)        | **Adopt (Pluggable)** | Metastable quartic potential modeling. Crash probability from capital flow proxies. A/B testing candidate. Applied to Tracks 3, 5, 8. |
| Event-based intrinsic time       | **Adopt (Phase 2)**   | Scale-invariant, event-driven aggregation. SurpriseIndicator for anomalous trajectories. Applied to Tracks 4, 5, 8.                   |
| Climate-liquidity sensitivity    | **Adopt (Phase 2)**   | Long-term structural macro adjustment. Climate sensitivity factor for ILI thresholds. Applied to Tracks 3, 5, 7, 8, 10.               |
| Natural disaster exogenous shock | **Adopt**             | EXOGENOUS_SHOCK regime override from USGS/GDACS feeds. Proactive DISLOCATED flagging. Applied to Tracks 4, 5, 7.                      |

### Proposal 04: Weight Optimization Module

| Decision                        | Status                | Rationale                                                                                                                |
|:--------------------------------|:----------------------|:-------------------------------------------------------------------------------------------------------------------------|
| Bayesian optimizer (PRIMARY)    | **Adopt**             | Closed-loop weight optimization with 3 profiles. Validation pipeline and auto-deployment. Applied to Tracks 3, 5, 8, 11. |
| Firefly algorithm (Alternative) | **Adopt (Pluggable)** | Metaheuristic optimizer. A/B testing candidate against Bayesian. Applied to Track 8.                                     |
| RAHF framework (Alternative)    | **Adopt (Phase 2)**   | GARCH + neural network hybrid. Latent-regime view. Applied to Track 3.                                                   |
| Ambiguity aversion bands        | **Adopt**             | Uncertainty bands on ILI, worst-case optimization mode. Applied to Tracks 5, 8.                                          |
| Efficiency-depth weighting      | **Adopt (Phase 2)**   | Regime-aware weights based on efficiency regimes. Applied to Tracks 5, 8.                                                |

### Proposal 05: Backtesting Robustness Module

| Decision                                              | Status    | Rationale                                                                                                                                        |
|:------------------------------------------------------|:----------|:-------------------------------------------------------------------------------------------------------------------------------------------------|
| Multi-dimensional parameter scanning                  | **Adopt** | Grid/Sobol search across ILI weights, percentile thresholds, and cooldown. Prevents overfitting to single optimum. Applied to Tracks 8, 3.       |
| Robustness heatmaps (time + parameter)                | **Adopt** | Time sensitivity and parameter sensitivity visualizations. Detects narrow profitability islands. Applied to Tracks 7, 8.                         |
| Advanced slippage module                              | **Adopt** | Volume-scaled slippage replacing flat-rate assumptions. Applied to Tracks 8, 10.                                                                 |
| Sobol quasi-random Monte Carlo                        | **Adopt** | Better state-space coverage than pseudo-random for stress-testing ILI signals. Applied to Tracks 3, 8.                                           |
| Broadie-Kou-Glasserman discrete monitoring correction | **Adopt** | Riemann Zeta-based beta correction for discrete barrier monitoring. Applied to Track 5.                                                          |
| Return Gap performance diagnostic                     | **Adopt** | Separates execution alpha from static holdings return. Applied to Tracks 5, 7, 8.                                                                |
| ML reproducibility scoring (RDS)                      | **Adopt** | 0-2 rubric for disclosure quality. Audit-grade artifacts for regulator-defensible deployment. Applied to Tracks 8, 9.                            |
| Regulatory algorithm behavioral testing               | **Adopt** | MiFID II-style SMC scenarios, OTR monitoring, circuit breaker, kill-switch verification, self-certification report. Applied to Tracks 8, 10, 11. |
| De-rounding data filter                               | **Adopt** | Smooths AT activity spikes at round time marks. Reduces noise from human programmer bias. Applied to Tracks 4, 10.                               |
| Time-periodicity filter                               | **Adopt** | Removes 1-second granular algorithmic loop artifacts. Complements de-rounding filter. Applied to Tracks 4, 5.                                    |
| Submission-based price impact KPI                     | **Adopt** | Tracks intended vs. executed move at submission time. Tiered cost model for passive/aggressive/large orders. Applied to Tracks 5, 10.            |
| Price Jump Ratio diagnostic                           | **Adopt** | Quantifies signal informativeness relative to announcement-period price variation. Applied to Tracks 5, 7, 8.                                    |

### Proposal 06: Risk Guardrails Module

| Decision                           | Status                | Rationale                                                                                                                                              |
|:-----------------------------------|:----------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
| Market stability guardrails        | **Adopt**             | MarketStabilityGuard + OrderImpactPredictor pre-trade checks. Prevents flash crash contribution. Applied to Track 10.                                  |
| AUMF five-stage framework          | **Adopt**             | Scenario-based signal suppression, ethical boundary filters, AUMF status integration. Applied to Tracks 5, 11.                                         |
| Big Red Button kill-switch         | **Adopt**             | Manual kill-switch with < 50ms latency. Halts all algorithmic trading. Applied to Track 11.                                                            |
| Systemic resilience monitor        | **Adopt**             | Cross-module feedback loop detection, global safe mode. Circuit breaker of last resort. Applied to Track 11.                                           |
| Global Safe Mode                   | **Adopt**             | System-wide safe mode triggered by correlated degradation. Throttles ingestion, stops signals, halts execution. Applied to Tracks 4, 11.               |
| Auditability (audit_reason fields) | **Adopt**             | Mandatory audit_reason on all automated config changes. Dashboard displays intent alongside diff. Applied to Tracks 2, 7.                              |
| Algorithmic sanity guard           | **Adopt**             | Extreme condition detection (fat finger). Forces Manual Oversight state. Applied to Track 4.                                                           |
| Algorithmic price stability        | **Adopt**             | Market-making execution model. Prefers limit orders over aggressive sniping. Applied to Track 10.                                                      |
| Algorithm aversion mitigation      | **Adopt**             | Signal explainability panels, human-in-the-loop toggle, performance duality comparison. Applied to Tracks 7, 10.                                       |
| Market Gamma / GEX monitor         | **Adopt (Pluggable)** | BSM Greeks calculation, gamma flip zone detection. Optional 6th axis on systemic risk heatmap. A/B testing candidate. Applied to Tracks 2, 3, 4, 5, 7. |

### Proposal 07: Liquidity Analysis Module

| Decision                                 | Status                | Rationale                                                                                                                                                  |
|:-----------------------------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Liquidity comovement monitor (PRIMARY)   | **Adopt**             | PCA-based variance factor on 5-min standardized spreads. Leading indicator for systemic stress. Applied to Tracks 3, 5.                                    |
| Phantom liquidity detection (PRIMARY)    | **Adopt**             | PLI comparing canceled vs. executed volume. Discounts ILI for unreliable depth. Applied to Tracks 5, 10.                                                   |
| Trader toxicity monitor (PRIMARY)        | **Adopt**             | Toxicity scores based on order-to-trade ratios, round-trip percentage. Differentiates harmful vs. beneficial AT. Applied to Tracks 4, 5, 7.                |
| Algorithmic intensity KPI                | **Adopt**             | Message-based AT proxy. Quintile bucketing for liquidity premium. Applied to Tracks 5, 7.                                                                  |
| Trader-type liquidity dynamics           | **Adopt**             | Source classifier for ALGO/Institutional/Professional/Retail. Spread compression and cancellation monitoring. Applied to Tracks 4, 5, 10.                  |
| Behavioural liquidity guard              | **Adopt**             | BRI from OFI, spread volatility, sentiment polarity. Herding filter and panic guard. 5th axis on systemic risk heatmap. Applied to Tracks 2, 4, 5, 10.     |
| Intraday liquidity pattern alignment     | **Adopt**             | Time-adaptive signal thresholds based on reverse U-shape intraday pattern. Applied to Tracks 5, 10.                                                        |
| Intraday session filter (Defining Range) | **Adopt**             | DR-based signal confidence, news confidence decay, 2:1 RRR testing for DR breaches. Applied to Tracks 4, 5, 10.                                            |
| AT liquidity impact (Alternative)        | **Adopt (Pluggable)** | Amihud measure, mean reversion speed, 20-minute lagged quality effect. A/B testing candidate against TimeOfDayThresholdManager. Applied to Tracks 3, 5, 8. |

---

## Changelog

| Version | File                     | Change                                                                                                                                                                                                                                                                                                    |
|:--------|:-------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | `00-dependency-graph.md` | Applied analysis findings: Virtual Threads primary, Arrow IPC, direct FRED/NY Fed clients. Added architectural decisions table.                                                                                                                                                                           |
| v2      | `00-dependency-graph.md` | Added "External Integration Decisions" section with FINOS CDM (adopt), Perspective (adopt), TimeBase-CE (defer), FDC3 (defer). Added decision table.                                                                                                                                                      |
| v3      | `00-dependency-graph.md` | Added "Improvement Proposals" section: wait-free ingestion (adopt), pre-computed KPI views (adopt), CDM adapter (adopt), QuestDB tiered storage (defer), shared-memory Arrow IPC (defer). Added proposal decision table.                                                                                  |
| v4      | `00-dependency-graph.md` | Added "Consolidated Proposal Integration" section covering all 7 proposals (Data Quality, Resilience & Operations, Regime Detection, Weight Optimization, Backtesting Robustness, Risk Guardrails, Liquidity Analysis). 55 integration decisions with adopt/defer/pluggable status and track assignments. |
