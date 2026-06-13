# Implementation Dependency Graph

## Plan Version

This is **plan v6** — extends v5 with free-tier data source migration:

- Replaced all paid data sources (Polygon, OpenBB, FMP/Intrinio) with free alternatives.
- Primary equity data: Yahoo Finance (REST) + Finnhub (REST fallback, WebSocket real-time).
- Historical deep data: Alpha Vantage.
- Macro data backfill: DataHub (Shiller CAPE, Robert Shiller datasets).
- Factor model data: Ken French Data Library (Fama-French factors).

Retains all v5 proposals:

- **08-CONSOLIDATED_TRADING_STRATEGY_ENHANCEMENT.md** — Markov optimal stops, leverage rotation, pairs trading
  verification, comparative execution analysis, portfolio management algebra, algorithmic behavior alignment
- **09-CONSOLIDATED_SENTIMENT_ANALYSIS_MODULE.md** — FinBERT sentiment intelligence, SALI lexicon-based sentiment index
- **10-CONSOLIDATED_ML_DRL_AGENT_FRAMEWORK.md** — Online learning/regret minimization, walk-forward validation,
  multi-model benchmark, LSTM volatility, SHAP explainability
- **11-CONSOLIDATED_DASHBOARD_ENHANCEMENT_MODULE.md** — Statistical integrity diagnostics (QQ-plot, ACF, convergence),
  market efficiency gap, monetary policy sensitivity
- **12-CONSOLIDATED_FIXED_INCOME_DERIVATIVES_MODULE.md** — Analytical Greeks for T-Bill proxy, Q-world risk-premium
  residual, standardized Greeks nomenclature
- **13 & 14-QUANTITATIVE_ENGINE_INTEGRATION (RUNBOOK_QUANTITATIVE_ENGINE_INTEGRATION.md)** — Consolidates 151 strategies
  encyclopedia with advanced statistical rigor (BH-FDR, EVT, IR audit logs, QR bands, TVP-SVAR)

Retains all v4 proposals:

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
  All data sources are free (FRED, NY Fed, Yahoo Finance, Finnhub, Alpha Vantage, DataHub, Ken French). No paid dependencies or sidecar containers.

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
| Direct FRED/NY Fed Java clients                  | All data sources are free-tier — zero recurring data costs. Yahoo Finance (primary) + Finnhub (fallback) for equity. Finnhub WS for real-time. Alpha Vantage for deep history. DataHub for historical backfill.                                                                                                               |
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
  CDM-typed data regardless of source (FRED, NY Fed, Yahoo Finance, Finnhub). Prevents source-specific field
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

### Proposal 08: Trading Strategy Enhancement

| Decision                             | Status                | Rationale                                                                                                                                                                                |
|:-------------------------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Formulaic Alpha Library (101 alphas) | **Defer**             | Equity-focused scope creep for an ILI/fixed-income platform. MegaAlphaEngine concept noted for future equity signal expansion.                                                           |
| Markov-Modulated Optimal Stops       | **Adopt (Phase 2)**   | State-dependent stops replace static stop-loss/take-profit in PaperTradingEngine. SSRN-2381830. Applied to Tracks 3, 5, 10.                                                              |
| Auto-Trading Strategy Improvements   | **Defer**             | RabbitMQ/MassTransit contradicts Arrow IPC + virtual threads architecture. Monte Carlo overlaps with existing Bayesian optimizer.                                                        |
| Leverage Rotation Strategy           | **Adopt**             | 200-day MA rotation between leveraged equity and risk-free assets. Simple, well-tested tail-risk truncation. SSRN-2741701. Applied to Tracks 5, 10.                                      |
| Pairs Trading Verification           | **Adopt (Phase 2)**   | Orthogonal cross-validation of ILI signals via minimum-distance pairs. Divergence detection between funding liquidity and relative value. SSRN-141615. Applied to Tracks 5, 8.           |
| Comparative Execution Analysis       | **Adopt**             | Passive vs aggressive dual portfolio comparison. Quantifies execution alpha. SSRN-4419304. Applied to Tracks 2, 5, 7, 10.                                                                |
| Portfolio Management Algebra         | **Adopt**             | Standardized margin/rebalancing algebra and linear cost model. SSRN-2314590. Applied to Tracks 5, 10.                                                                                    |
| Captive Finance Commitment Signal    | **Defer**             | Niche for Fed/Treasury-focused ILI platform. Captive vs bank financing is peripheral to core thesis.                                                                                     |
| Algorithmic Behavior Alignment       | **Adopt (selective)** | Time-decay function for IntradayProxyService near SOFR publication reduces false proxy signals. Liquidity-adjusted slippage in signal cost model. SSRN-1142738. Applied to Tracks 5, 10. |
| Special-Purpose Order Support        | **Defer**             | Pegged/combo/linked orders require live execution to validate. Demo platform cannot properly test these order types.                                                                     |

### Proposal 09: Sentiment Analysis

| Decision                                 | Status                | Rationale                                                                                                                                           |
|:-----------------------------------------|:----------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------|
| FinBERT Sentiment Intelligence (PRIMARY) | **Adopt**             | Contextual Fed sentiment analysis. Hawkish/dovish detection complements quantitative FRED data. SSRN-4995172. Applied to Tracks 2, 3, 5, 7, 8, 10.  |
| SALI Lexicon-Based (ALTERNATIVE)         | **Adopt (Pluggable)** | Lightweight fallback with no GPU requirement. Phase 1 deployment before FinBERT. A/B testing candidate. SSRN-4458417. Applied to Tracks 2, 3, 5, 7. |

### Proposal 10: ML/DRL Agent Framework

| Decision                                      | Status                | Rationale                                                                                                                                            |
|:----------------------------------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| FinRL PPO Agent (PRIMARY)                     | **Defer**             | DRL black-box conflicts with 90-day reproducible verification. Massive GPU/ops burden for demo platform.                                             |
| DQN/PPO Adaptive Portfolio (Alt A)            | **Defer**             | Overlaps with existing Bayesian weight optimizer.                                                                                                    |
| TD3 Continuous Action Space (Alt B)           | **Defer**             | Position sizing improvements achievable more simply.                                                                                                 |
| Contextual Bandit Execution (Alt C)           | **Defer**             | Requires live Level 2 order book data not currently ingested.                                                                                        |
| LangGraph ReAct Agent (Alt D)                 | **Defer**             | LLM non-determinism conflicts with reproducibility requirements. Latency/cost concerns.                                                              |
| Online Learning / Regret Minimization (Alt E) | **Adopt**             | AgnosticAggregator using exponential weights. Mathematically rigorous, no GPU needed. Walk-forward validation. SSRN-5242085. Applied to Tracks 5, 8. |
| ML Price Prediction (Complementary)           | **Defer**             | Depends on Level 2 market data (bid-ask pressure, order imbalance) not in current Finnhub feed.                                                      |
| ML Quant Finance Trends (Complementary)       | **Adopt (selective)** | Walk-forward validation for WeightOptimizer. LSTM volatility forecaster as GARCH alternative. SSRN-3397005. Applied to Tracks 3, 8.                  |
| Multi-Model Benchmark (Complementary)         | **Adopt**             | Tournament framework comparing ILI vs XGBoost vs LSTM. CrossModelValidator. Crucial for demo credibility. SSRN-4901967. Applied to Tracks 3, 5, 8.   |
| FinRL-Meta + Explainability (Complementary)   | **Adopt (selective)** | SHAP explainability for existing models only. Defer FinRL-Meta unified data processor dependency. SSRN-3955949. Applied to Tracks 3, 7.              |

### Proposal 11: Dashboard Enhancement

| Decision                          | Status    | Rationale                                                                                                                                             |
|:----------------------------------|:----------|:------------------------------------------------------------------------------------------------------------------------------------------------------|
| Statistical Integrity Dashboard   | **Adopt** | QQ-plots, ACF charts, convergence plots. Low complexity, high transparency value. SSRN-5755602. Applied to Tracks 3, 7, 8.                            |
| Market Efficiency Diagnostic      | **Adopt** | Fundamental-algorithmic gap indicator. Leverages existing AlgorithmicIntensityMetric AT proxy data. SSRN-2400527. Applied to Tracks 5, 7.             |
| Monetary Policy Sensitivity Panel | **Adopt** | Fed balance sheet, policy rates, yield curve shape. Directly relevant to ILI thesis. Uses existing FRED client. SSRN-2727899. Applied to Tracks 4, 7. |

### Proposal 12: Fixed Income & Derivatives

| Decision                      | Status                | Rationale                                                                                                                                                                                                            |
|:------------------------------|:----------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spectral Bond Engine          | **Adopt (selective)** | Analytical Greeks (DV01, convexity) for 3-month T-Bill proxy only. Defer full spectral PDE decomposition and yield curve stress testing as over-engineering for current scope. SSRN-5047749. Applied to Tracks 3, 5. |
| Q-World Integration           | **Adopt**             | Risk-premium residual monitor validates ProxyDivergenceGuard DISLOCATED status independently. CIR model for fair-value T-Bill yield. SSRN-1717163. Applied to Tracks 3, 5.                                           |
| CVA/FVA Valuation Adjustments | **Defer**             | Institutional-grade feature overkill for demo/verification platform. No counterparty risk in paper trading.                                                                                                          |
| Greeks Risk Primitive         | **Adopt**             | Standardized nomenclature for existing KPIs (Repo-Delta, Rate-Delta, Spread-Delta). Sensitivity-adjusted signal thresholds. SSRN-5187624. Applied to Tracks 5, 7.                                                    |

---

## Changelog

| Version | File                     | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|:--------|:-------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v1      | `00-dependency-graph.md` | Applied analysis findings: Virtual Threads primary, Arrow IPC, direct FRED/NY Fed clients. Added architectural decisions table.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| v2      | `00-dependency-graph.md` | Added "External Integration Decisions" section with FINOS CDM (adopt), Perspective (adopt), TimeBase-CE (defer), FDC3 (defer). Added decision table.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| v3      | `00-dependency-graph.md` | Added "Improvement Proposals" section: wait-free ingestion (adopt), pre-computed KPI views (adopt), CDM adapter (adopt), QuestDB tiered storage (defer), shared-memory Arrow IPC (defer). Added proposal decision table.                                                                                                                                                                                                                                                                                                                                                                                                                           |
| v4      | `00-dependency-graph.md` | Added "Consolidated Proposal Integration" section covering all 7 proposals (Data Quality, Resilience & Operations, Regime Detection, Weight Optimization, Backtesting Robustness, Risk Guardrails, Liquidity Analysis). 55 integration decisions with adopt/defer/pluggable status and track assignments.                                                                                                                                                                                                                                                                                                                                          |
| v5      | `00-dependency-graph.md` | Added Proposal 08 (Trading Strategy Enhancement) and Proposal 09 (Sentiment Analysis) integration decisions. 8 adopt, 4 defer out of 12 elements. Adopted: Markov stops, leverage rotation, pairs trading verification, comparative execution, portfolio algebra, algorithmic behavior alignment, FinBERT, SALI. Deferred: 101 alphas, auto-trading, captive finance, special-purpose orders.                                                                                                                                                                                                                                                      |
| v5.1    | `00-dependency-graph.md` | Added Proposal 10 (ML/DRL Agent Framework), Proposal 11 (Dashboard Enhancement), Proposal 12 (Fixed Income & Derivatives) integration decisions. 10 adopt (3 selective), 7 defer out of 17 elements. Adopted: online learning/regret minimization, walk-forward validation, multi-model benchmark, LSTM volatility (selective), SHAP explainability (selective), statistical integrity dashboard, market efficiency diagnostic, monetary policy sensitivity, analytical Greeks (selective), Q-world risk-premium residual, Greeks risk primitive. Deferred: FinRL PPO, DQN, TD3, contextual bandit, LangGraph ReAct, ML price prediction, CVA/FVA. |
| v6      | `00-dependency-graph.md` | Migrated all data sources to free-tier. Removed OpenBB sidecar and paid dependencies (Polygon, FMP, Intrinio). Finding 5 updated: all data sources are free (FRED, NY Fed, Yahoo Finance, Finnhub, Alpha Vantage, DataHub, Ken French). Proposal #5 CDM adapters updated: PolygonTickCdmAdapter replaced with WsTickCdmAdapter (Finnhub WS), added YahooFinanceCdmAdapter, FinnhubRestCdmAdapter, AlphaVantageCdmAdapter, DataHubCdmAdapter, KenFrenchCdmAdapter. Key Architectural Decisions updated to reflect zero recurring data costs.                                                                                                    |

---

## Appendix: 00-overview.md

> *Merged from `gaps/00-overview.md` / `done/00-overview.md` during plan_v6 consolidation.*

# Plan v5 vs. Implementation — Gap Status (Updated 2026-06-03)

## Summary

| Area                      | Previously Missing      | Status                                |
| ------------------------- | ----------------------- | ------------------------------------- |
| Computation Engine (Java) | 37 components           | **All 37 implemented** with tests     |
| Ingestion Layer (Java)    | 7 components            | **4 implemented** (3 deferred)        |
| CI/CD Workflows           | 3 workflows             | **All 3 created**                     |
| Database Tables           | 2 tables                | **1 view created** (1 is a column)    |
| Docker/Infra              | 3 items                 | **3 implemented** (Security). OpenBB removed in v6. |
| Frontend (Dashboard)      | 2 items                 | **2 implemented** (D3.js, Playwright) |
| Runbooks                  | 17 runbooks             | **All 17 written**                    |
| Auth/Security             | OAuth2+PKCE             | **Spring Security configured**        |
| Integration Tests         | Empty module            | **Foundation with Testcontainers**    |

## Resolved Items

See individual gap files for original details:
- [01-computation-engine.md](01-computation-engine.md) — 37 components implemented
- [02-ingestion-layer.md](02-ingestion-layer.md) — 4 components implemented, 3 intentionally deferred
- [03-cicd-workflows.md](03-cicd-workflows.md) — 3 workflows created
- [04-database-tables.md](04-database-tables.md) — evt_risk_metrics view created; order_flow_imbalance is a column
- [05-docker-infra.md](05-docker-infra.md) — Spring Security + OpenBB sidecar configured
- [06-frontend-dashboard.md](06-frontend-dashboard.md) — D3.js heatmap + Playwright e2e added
- [07-runbooks.md](07-runbooks.md) — 17 runbooks written
- [08-structural-gaps.md](08-structural-gaps.md) — integration-tests foundation created

## Intentionally Deferred

| Item | Reason |
|------|--------|
| `OpenBBClient` (Java) | SUPERSEDED: replaced by YahooFinanceClient + FinnhubEquityClient in v6 free data source migration |
| `AnomalyDetectionWorker` (Java) | Python analytics worker handles this |
| Chronicle Queue | Replaced by `FileOverflowBuffer` |
| `webflux/` subproject | Deferred per original plan |

## Implementation Record

See [ADR-011](../../adr/ADR-011-gap-elimination.md) for full implementation details.

---

## Appendix: 00-implementation-priority.md

> *Merged from `gaps/00-implementation-priority.md` / `done/00-implementation-priority.md` during plan_v6 consolidation.*

# Implementation Priority by Dependencies

Each phase must complete before the next begins. Within a phase, items can be parallelized.

---

## Phase 0 — Foundation (blocking everything)

These are prerequisites that all subsequent layers depend on.

| Priority | Item | From | Why first |
|----------|------|------|-----------|
| 0.1 | `order_flow_imbalance` table | [04-database](04-database-tables.md) | V16 already has a dangling index referencing it; v5 liquidity components write to it |
| 0.2 | `evt_risk_metrics` hypertable | [04-database](04-database-tables.md) | Risk services query this; EVT framework is a dependency for `RiskPremiumResidualMonitor` |
| 0.3 | Spring Security / OAuth2+PKCE | [05-docker-infra](05-docker-infra.md) | All `/api/**` endpoints are unauthenticated; every new service adds endpoints |
| 0.4 | `integration-tests/` module — write first test class | [08-structural](08-structural-gaps.md) | No integration coverage exists; every new component needs integration verification |

---

## Phase 1 — Regime & ILI Primitives (v4)

Computation engine components that are *inputs* to everything else.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 1.1 | `SessionRangeService` | Produces session labels (Asian/EU/US); consumed by `TimeOfDayThresholdManager`, `RegimeAwareWeightingService` |
| 1.2 | `AmbiguityAdjustedIli` | Extends ILI with UNCERTAIN status; required by `ToxicityAdjustedIli`, `SaliProcessor` |
| 1.3 | `SurpriseIndicator` | Entropy scoring on ILI output; consumed by `BehaviouralRiskProcessor` and v5.1 ML validators |
| 1.4 | `ParticipationGovernanceService` | Signal decomposition + admissibility; gates all downstream signal processors |
| 1.5 | `LiquidityStressTestModule` | Stress scenario calibration; feeds into `RegimeAwareWeightingService` thresholds |

---

## Phase 2 — Optimization & Climate (v4)

Depends on Phase 1 regime/ILI outputs.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 2.1 | `RegimeAwareWeightingService` | Consumes `SessionRangeService` output; produces weight vectors used by v5 aggregators |
| 2.2 | `ScheduledCalibrationTask` | Periodic task that recalibrates weights; depends on `RegimeAwareWeightingService` |
| 2.3 | `FireflyWeightOptimizer` | Alternative optimizer for weight search; runs inside `ScheduledCalibrationTask` |
| 2.4 | `ClimateSensitivityFactor` | Adjusts ILI thresholds; consumed by `ClimateRiskGuard` and v5 liquidity adjusters |
| 2.5 | `ClimateRiskGuard` | Wraps `ClimateSensitivityFactor`; feeds confidence adjustments to ILI pipeline |

---

## Phase 3 — Ingestion Guards (v4)

Real-time ingestion safety nets — independent of computation but needed before v5 liquidity processing.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 3.1 | `AlgorithmicSanityGuard` | Detects >10% flash moves, >10k msg/sec; triggers regime overrides that affect ILI |
| 3.2 | `DisasterAlertClient` | Produces EXOGENOUS_SHOCK regime; consumed by `LiquidityStressTestModule` triggers |
| 3.3 | `EventBasedTimeConverter` | Maps ticks to directional change events; feeds `AlgorithmicIntensityMetric` |
| 3.4 | `OrderCancellationMonitor` | Cancellation ratio metric; consumed by `BehaviouralRiskProcessor` |

---

## Phase 4 — Liquidity Layer (v5 Core)

The largest single block. All items consume Phase 1–3 outputs.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 4.1 | `AlgorithmicIntensityMetric` | Consumes `EventBasedTimeConverter`; produces AT proxy quintiles used by 4.3–4.6 |
| 4.2 | `LiquiditySourceClassifier` | Trader type estimation; feeds `BehaviouralRiskProcessor` and `PhantomLiquidityService` |
| 4.3 | `PhantomLiquidityService` | PLI calculation; depends on `AlgorithmicIntensityMetric` + `LiquiditySourceClassifier` |
| 4.4 | `ToxicityAdjustedIli` | Depends on `AmbiguityAdjustedIli` (Phase 1); consumes `PhantomLiquidityService` |
| 4.5 | `ComovementTrigger` | Liquidity comovement factor; feeds `DefiningRangeService` |
| 4.6 | `TimeOfDayThresholdManager` | Consumes `SessionRangeService`; adjusts thresholds consumed by 4.7–4.9 |
| 4.7 | `DefiningRangeService` | DR-based confidence; depends on `ComovementTrigger` + `TimeOfDayThresholdManager` |
| 4.8 | `BehaviouralRiskProcessor` | BRI with systemic risk axis; consumes `SurpriseIndicator`, `OrderCancellationMonitor`, `LiquiditySourceClassifier` |
| 4.9 | `LiquidityMeanReversionSpeed` | AT lagged quality; depends on `AlgorithmicIntensityMetric` |
| 4.10 | `LiquidityPremiumFactor` | AT intensity premium; depends on `AlgorithmicIntensityMetric` + `LiquidityMeanReversionSpeed` |

---

## Phase 5 — Core Analytics (v5)

Trade execution and signal quality — depends on liquidity layer.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 5.1 | `StrategicRunService` | Groups child orders; feeds `PriceImpactKpi` |
| 5.2 | `PriceImpactKpi` | NBBO midpoint; depends on `StrategicRunService` order grouping |
| 5.3 | `InformationEfficiencyAnalyzer` | Price Jump Ratio; depends on `PriceImpactKpi` data |
| 5.4 | `ComparativeExecutionAnalysis` | Passive vs aggressive execution; depends on `PriceImpactKpi` |
| 5.5 | `LeverageSignaler` | 200-day MA rotation; independent, but feeds `PortfolioManagementAlgebra` |
| 5.6 | `PairsTradingEngine` | Minimum-distance pairs; independent, but its output feeds `PortfolioManagementAlgebra` |
| 5.7 | `PortfolioManagementAlgebra` | Margin algebra; depends on `LeverageSignaler`, `ComparativeExecutionAnalysis` |
| 5.8 | `AlgorithmicBehaviorAlignment` | Time-decay proxy; depends on `AlgorithmicIntensityMetric` from Phase 4 |
| 5.9 | `SaliProcessor` | 70/30 ILI-sentiment blend; depends on `AmbiguityAdjustedIli` + sentiment API |
| 5.10 | `SentimentVolatilityGuard` | BERT threshold adjustment; depends on `SaliProcessor` output |

---

## Phase 6 — ML Validation (v5.1)

Requires all Phase 1–5 outputs for training/tournament data.

| Priority | Component | Dependency rationale |
|----------|-----------|---------------------|
| 6.1 | `WalkForwardValidator` | Anchored WFA; needs full ILI signal history from Phases 1–5 |
| 6.2 | `CrossModelValidator` | Tournament ILI vs XGBoost vs LSTM; depends on `WalkForwardValidator` for fair splits |
| 6.3 | `VotingClassifier` | ML confirmation filter; depends on `CrossModelValidator` model outputs |
| 6.4 | `AgnosticAggregator` | Regret-min weight aggregation; consumes `CrossModelValidator` predictions |
| 6.5 | `MarketEfficiencyMonitor` | Gap indicator; depends on `InformationEfficiencyAnalyzer` |
| 6.6 | `RiskPremiumResidualMonitor` | Q-world validation; depends on `evt_risk_metrics` table (Phase 0) + `PhantomLiquidityService` |
| 6.7 | `MarketSensitivityLibrary` | Greeks nomenclature; depends on `LiquidityPremiumFactor` + `RiskPremiumResidualMonitor` |

---

## Phase 7 — CI/CD & Infrastructure

Needs stable codebase from Phases 0–6.

| Priority | Item | From |
|----------|------|------|
| 7.1 | `demo-report.yml` weekly workflow | [03-cicd](03-cicd-workflows.md) |
| 7.2 | `deploy-landing.yml` push workflow | [03-cicd](03-cicd-workflows.md) |
| 7.3 | `chaos-tests.yml` weekly workflow | [03-cicd](03-cicd-workflows.md) |

---

## Phase 8 — Frontend Completion

Needs backend APIs from Phases 1–6.

| Priority | Item | From |
|----------|------|------|
| 8.1 | D3.js heatmap integration | [06-frontend](06-frontend-dashboard.md) |
| 8.2 | Playwright e2e tests | [06-frontend](06-frontend-dashboard.md) |

---

## Phase 9 — Runbooks

Documentation for all operational procedures; can only be accurate after implementation is stable.

| Priority | Runbook | Reason it's here |
|----------|---------|------------------|
| 9.1 | Big Red Button Runbook | Emergency ops — document first among runbooks |
| 9.2 | Finnhub WebSocket Outage Procedure | Critical path dependency |
| 9.3 | Systemic Resilience Monitor Runbook | Depends on `BehaviouralRiskProcessor` (Phase 4) |
| 9.4 | Bulkhead Pool Monitoring | Depends on Spring Security (Phase 0) |
| 9.5 | Distributed Tracing with Jaeger | Infra runbook, no code deps |
| 9.6 | ILI Weight Recalibration | Depends on `ScheduledCalibrationTask` (Phase 2) |
| 9.7 | Calibration Task Monitoring | Depends on `ScheduledCalibrationTask` (Phase 2) |
| 9.8 | Proxy Divergence Event Review | Depends on `RiskPremiumResidualMonitor` (Phase 6) |
| 9.9 | Disaster Alert Verification | Depends on `DisasterAlertClient` (Phase 3) |
| 9.10 | TimescaleDB Continuous Aggregate Refresh | DB ops |
| 9.11 | Analytics Worker Deployment | Deployment |
| 9.12 | Free Data Source Configuration (Finnhub + Yahoo Finance) | Depends on free data source setup |
| 9.13 | TA-Lib Adapter Integration | Integration guide |
| 9.14 | De-Rounding Filter Verification | Filter verification |
| 9.15 | Yahoo Finance options | Pipeline verification |
| 9.16 | Regulatory Compliance Report Generation | Compliance |
| 9.17 | Chronicle Queue Overflow Recovery | May be obsolete (replaced by FileOverflowBuffer) |

---

## Dependency Graph (simplified)

```
Phase 0: Foundation
  ├── Phase 1: Regime & ILI (v4)
  │     ├── Phase 2: Optimization & Climate (v4)
  │     └── Phase 3: Ingestion Guards
  │           └── Phase 4: Liquidity Layer (v5)
  │                 └── Phase 5: Core Analytics (v5)
  │                       └── Phase 6: ML Validation (v5.1)
  ├── Phase 7: CI/CD
  ├── Phase 8: Frontend
  └── Phase 9: Runbooks
```

## Component Count by Phase

| Phase | Components | Cumulative |
|-------|-----------|------------|
| 0 — Foundation | 4 | 4 |
| 1 — Regime & ILI | 5 | 9 |
| 2 — Optimization & Climate | 5 | 14 |
| 3 — Ingestion Guards | 4 | 18 |
| 4 — Liquidity Layer | 10 | 28 |
| 5 — Core Analytics | 10 | 38 |
| 6 — ML Validation | 7 | 45 |
| 7 — CI/CD | 3 | 48 |
| 8 — Frontend | 2 | 50 |
| 9 — Runbooks | 17 | 67 |

**Total: 67 items across 10 phases.**

---

## v6 Changelog

- **Phase 9, item 9.2:** Renamed "Polygon WebSocket Outage Procedure" to "Finnhub WebSocket Outage Procedure" reflecting v6 free data source migration.
- **Phase 9, item 9.12:** Renamed "OpenBB Sidecar Setup" to "Free Data Source Configuration (Finnhub + Yahoo Finance)" reflecting v6 free data source migration.
- **Phase 9, item 9.15:** Renamed "Polygon options" to "Yahoo Finance options" reflecting v6 free data source migration.

---

## Appendix: IMPLEMENTATION_SUMMARY.md

> *Merged from `gaps/IMPLEMENTATION_SUMMARY.md` / `done/IMPLEMENTATION_SUMMARY.md` during plan_v6 consolidation.*

# Quantitative Engine Implementation Summary

**Date:** 2026-05-23
**Spec:** RUNBOOK_QUANTITATIVE_ENGINE.md v5.3.0-INSTITUTIONAL-SPEC
**Build status:** All Java tests pass, all 15 Python tests pass

---

## Architecture

```
api-contracts → cdm → persistence → ingestion, computation → web → app
                                                         ↑
                                              analytics (Python FastAPI, REST IPC)
integration-tests → app
```

---

## Module inventory (136 files total)

**cdm (8 Java files)** — Shared domain model

- Enums: InstrumentType, OptionType, DayCountConvention
- Records: CdmOptionSnapshot (17 fields with Greeks + BigDecimal strike), CdmRateSnapshot, CdmTick
- Compact constructors with validation (strike > 0, ask >= bid, finite values)
- 8 tests in CdmOptionSnapshotTest, CdmRateSnapshotTest

**persistence (7 SQL migrations V19–V25)** — TimescaleDB hypertables

- V19: strategy_definitions + alpha_signals (7-day chunks)
- V20: intersubjective_audit_log (1-day chunks)
- V21: option_chain_snapshots (1-day chunks)
- V22: macro_shock_irfs + risk_evt_parameters (30-day chunks)
- V23: quantile_coefficients + synergy_entropy_matrix (30-day chunks)
- V24: universe_stats_history (1-day chunks)
- V25: Performance indexes for all quant engine tables

**computation (87 Java files)** — Core engine

- strategy/ (4 files): Strategy, BaseStrategy<T>, StrategyContext, AlphaSignal (pre-existing)
- audit/ (4 files): CodingRule enum, AuditEntry record, IntersubjectiveAuditService (@Component, SHA-256 hashing, IR
  score gating at 0.90)
- options/ (33 files): StrategyType enum (30 types), LegGroup record, LegMatchService (butterfly/condor/vertical
  matching with strike tolerance), BaseOptionStrategy abstract class, 30 concrete strategies (BullCallSpread through
  CalendarStraddle), StrategyRegistry
- equity/ (29 files): UniverseContext record, UniverseAggregator (AtomicReference, 1000-symbol cap), EquityStrategyType
  enum (25 types), BaseEquityStrategy abstract class, 25 concrete strategies (MomentumDecile through IchimokuCloud)
- fixedincome/ (5 files): FixedIncomeStrategyType enum, BondPosition record, FixedIncomePortfolio record,
  FixedIncomePortfolioBuilder (bullet/barbell/duration-neutral)
- backtest/ (5 files): ExecutionDelay enum, BacktestResult record, DelayDExecutor (Delay-0 vs Delay-1, fragility
  detection at 3.0 Sharpe ratio), Eq553SlippageModel (zeta=0.15, volume-scaled)

**ingestion (2 Java files)** — Data quality

- kernel/KernelAggregator: Realized variance with Tukey-Hanning and Parzen kernels, autocovariance computation

**web (4 Java files)** — REST API

- config/WebConfig: CORS for /api/**
- controller/QuantController: 7 endpoints under /api/v1/quant/ (signals/active, strategies/active,
  strategies/options/butterfly, risk/tail-parameters, risk/evt-tail, audit/intersubjective-reproducibility/{id},
  macro/shock-response)
- controller/HealthController: GET /health

**app (1 Java file)** — Spring Boot entry point

- TickonomicsApplication: @SpringBootApplication
- application.yml: virtual threads enabled, PostgreSQL/TimescaleDB, Flyway, analytics worker URL

**analytics (31 Python files)** — Statistical sidecar

- FastAPI app on port 8001 with 7 routers
- services/statistical/evt_risk_service: GPD fitting via scipy.stats.genpareto, tail VaR at 99.9%
- services/statistical/multiple_testing_service: BH-FDR via statsmodels
- services/statistical/yield_curve_service: Nelson-Siegel via scipy.optimize.curve_fit
- services/statistical/macro_shock_service: TVP-SVAR IRF via statsmodels VARMAX
- services/statistical/quantile_regression_service: QR bands via statsmodels QuantReg
- services/statistical/transfer_entropy_service: TE with bootstrap permutation test
- 15 tests covering all services with Given-When-Then structure

---

## Key runbook mandates enforced

1. **IR Score Gating:** No signal transitions to ACTIONABLE if ir_score < 0.90 (BaseOptionStrategy.compute,
   IntersubjectiveAuditService)
2. **Universe Mean MANDATE (Eq 293):** All equity strategies use shared UniverseAggregator (no independent mean
   calculation)
3. **Day-Count Conventions:** ACT/365 for equity options, ACT/360 for IR, ACT/ACT for Treasuries (CdmOptionSnapshot +
   DayCountConvention enum)
4. **Volume-Scaled Slippage (Eq 553):** Eq553SlippageModel with zeta=0.15, marks strategies as LIQUIDITY_FRAGILE when
   adjusted return < 0
5. **Leg Symmetry Constraint:** Butterfly requires K2 = (K1+K3)/2 within 0.01 tolerance (LegMatchService)
6. **FDR False-Positive Control:** BH-FDR correction prevents p-hacking in strategy selection

---

## Build configuration

- Spring Boot 3.5.0, Java 25, Gradle multi-module
- `-parameters` compiler flag for Spring MVC parameter name resolution
- dependency-management plugin with Spring Boot BOM
- Resilience4j 2.3.0 for ingestion circuit breaker

---

## Not yet implemented (deferred from runbook)

- V1–V18 SQL migrations (core platform tables from 02-database-schema.md)
- Arrow IPC transport optimization (Phase 7 optimization)
- Christian-Christoffersen VaR validation (Phase 9 Python)
- Kubernetes Helm charts for HA deployment (Phase 10)
- Morning pre-flight / signal dislocation protocols (Phase 10 operational)

---

## Appendix: Deferred Items

> *Merged from `deferred-items-implementation-plan.md` during plan_v6 consolidation.*

## Overview

Five items were intentionally deferred during gap elimination. Each has a valid architectural reason for deferral but remains on the roadmap for when the triggering condition materializes.

| # | Item | Trigger Condition | Estimated Effort |
|---|------|-------------------|------------------|
| 1 | OpenBBClient (Java) | Equity/ETF universe exceeds Polygon coverage | 3 days |
| 2 | AnomalyDetectionWorker (Java) | Latency requirement forces in-process detection | 5 days |
| 3 | Chronicle Queue | Ingestion throughput exceeds FileOverflowBuffer capacity | 4 days |
| 4 | webflux/ Subproject | Reactive runtime needed for streaming endpoints | 7 days |
| 5 | analytics/ Java Bridge | Java modules need direct access to analytics computations | 5 days |
| 6 | DataHub Deferred Datasets | Project expands beyond core index/macro analysis scope | 2–3 days |

---


## Implementation Priority (When Triggered)

```
Priority 1: OpenBBClient            — SUPERSEDED by v6 free data source migration
Priority 2: AnomalyDetectionWorker   — medium effort, latency-sensitive use case
Priority 3: Chronicle Queue          — significant effort, only when throughput demands it
Priority 4: DataHub Deferred Datasets — low effort, reuses existing DataHub pipeline
Priority 5: analytics Java Bridge    — medium effort, improves type safety
Priority 6: webflux/ Subproject      — highest effort, architectural shift
```


## Cost Estimate Summary

| Item | Java Files | Config Files | Test Files | Total |
|------|-----------|-------------|-----------|-------|
| OpenBBClient | 3 | 1 | 3 | 7 |
| AnomalyDetectionWorker | 3 | 0 | 3 | 6 |
| Chronicle Queue | 2 | 2 | 2 | 6 |
| webflux/ Subproject | 4 | 2 | 3 | 9 |
| analytics Java Bridge | 5+ | 1 | 2+ | 8+ |
| DataHub Deferred Datasets | 0–4 | 1 | 0–4 | 1–9 |
| **Total** | **17–21+** | **7** | **13–17+** | **37–45+** |

---

---

## Appendix: Gap Analysis (v5 → Implementation)

> *Merged from `LEFT_AFTER_FIRST_ITERATION.md` during plan_v6 consolidation.*

## Summary

| Area                      | Planned        | Implemented    | Missing                            |
| ------------------------- | -------------- | -------------- | ---------------------------------- |
| Computation Engine (Java) | ~64 components | ~27 components | **37 components**                  |
| Ingestion Layer (Java)    | 23 components  | ~16 components | **7 components**                   |
| CI/CD Workflows           | 7 workflows    | 4 workflows    | **3 workflows**                    |
| Database Tables           | 34+ tables     | 31 tables      | **2 tables**                       |
| Docker/Infra              | 7 items        | 4 items        | **3 items**                        |
| Frontend (Dashboard)      | 40 components  | 52 components* | **2 items** (D3.js, e2e)           |
| Runbooks                  | 21 runbooks    | 4 runbooks     | **17 runbooks**                    |
| Auth/Security             | OAuth2+PKCE    | Partial        | **Spring Security config missing** |

\*Dashboard has more components than planned due to granular breakdown.

---


## Prioritized Summary

**Critical gaps** (core functionality missing):
1. **37 computation engine components** — the v4 governance, v5 liquidity, and v5.1 ML integration layers are entirely absent from Java
2. **Spring Security / OAuth2** — no API authentication or authorization
3. **Integration tests** — empty module, no integration test coverage

**Significant gaps** (operational completeness):
4. **3 CI/CD workflows** — demo reporting, landing deployment, chaos testing
5. **17 runbooks** — operational documentation at ~19% of plan
6. **7 ingestion components** — disaster alerts, sanity guard, cancellation monitor, event-based time

**Minor gaps** (partial coverage or intentional deferral):
7. **2 database tables** — `order_flow_imbalance` (dangling index), `evt_risk_metrics`
8. **D3.js + Playwright** — frontend uses alternative libraries
9. **Chronicle Queue** — replaced by simpler `FileOverflowBuffer`
10. **webflux/ module** — intentionally deferred per plan
