# Consolidated Proposal: Risk Guardrails Module

**Status:** Consolidated Proposal for Tickonomics v3
**Target Tracks:** Track 2 (Database), Track 3 (Analytics Worker), Track 4 (Ingestion), Track 5 (Computation Engine),
Track 7 (Dashboard), Track 10 (Demo/Virtual Portfolio), Track 11 (Deployment)

---

## v3 Integration Points

| Track    | Module             | Enhancement                                                          |
|:---------|:-------------------|:---------------------------------------------------------------------|
| Track 2  | Database Schema    | `audit_reason` fields, `market_gamma_history` hypertable             |
| Track 3  | Analytics Worker   | BSM Greeks calculation, GEX aggregation service                      |
| Track 4  | Ingestion Layer    | `AlgorithmicSanityGuard`, options OI/IV data ingestion               |
| Track 5  | Computation Engine | AUMF status checks, scenario engine, GEX-weighted regime detection   |
| Track 7  | Dashboard          | Signal explainability, gamma profile chart, audit trail display      |
| Track 10 | Demo/Portfolio     | Market stability guard, circuit breaker++, dual execution evaluation |
| Track 11 | Deployment         | Systemic resilience monitor, global safe mode, Big Red Button        |

---

## Defense-in-Depth Architecture

This module implements a four-layer safety system that protects the platform across the full trading lifecycle:

```
Layer 1: PRE-TRADE     --> Market Stability Guardrails
Layer 2: DURING-TRADE  --> Algorithm Uncertainty Management Framework
Layer 3: SYSTEMIC      --> Systemic Resilience Monitor
Layer 4: POST-TRADE    --> Auditability & Behavioral Safety
Optional:              --> Gamma/GEX Market Microstructure Risk
```

---

## Core Proposals (Three-Layer Safety System)

### Layer 1: AI-Driven Market Stability Guardrails (AI_MARKET_STABILITY_GUARDRAILS)

**Source:** SSRN-6143508 (Seth)

**Objective:** Prevent the system from contributing to or being victimized by flash crashes and systemic market
instability.

**Module: MarketStabilityGuard (Track 10)**

- Monitors aggregate portfolio volatility.
- "Circuit Breaker++" extends `PaperTradingEngine` circuit breaker beyond simple connectivity checks.
- Halts virtual trading if anomalous volatility spike is detected across the whole portfolio.

**Module: OrderImpactPredictor (Track 10)**

- Pre-trade "Systemic Impact" check that simulates order impact on current liquidity.
- Runs a quick simulation before any order is submitted.
- Blocks orders estimated to cause adverse market impact beyond configurable threshold.

**Validation Criteria:**

- [ ] Market stability guard triggers within 100ms of detecting anomalous volatility.
- [ ] OrderImpactPredictor correctly blocks orders that would exceed impact thresholds.
- [ ] No false-positive circuit breaker triggers during normal market operations.

---

### Layer 2: Algorithm Uncertainty Management Framework (ALGORITHM_UNCERTAINTY_MANAGEMENT)

**Source:** SSRN-4179166

**Objective:** Formalize uncertainty management as a cohesive five-stage framework to mitigate the risk of algorithms
intensifying market crises.

**Five-Stage Model:**

1. **Categorize previous cases:** Analyze historical market stress events (March 2020, January 2015 SNB event).
2. **Identify algorithm behavior:** Understand how ILI models react in those cases.
3. **Create scenarios:** Draft "what-if" scenarios for potential future crises.
4. **Create best practices:** Define safe-operating boundaries.
5. **Design algorithm response:** Implement circuit breakers and safe modes.

**Module: Scenario Engine (Track 5)**

- Matches current market conditions against historical crisis profiles.
- Scenario-based signal suppression: suppress signals when conditions match known crisis patterns.
- Ethical boundary filters: prevent signals that might contribute to feedback loops in fragile markets.

**AUMF Status Integration:**

- `AumfStatus` enum added to `SignalStatus`: `SAFE_MODE`, `SUSPENDED_UNCERTAINTY`, `PROCEED_CAUTIOUSLY`.
- `SignalGenerator` includes AUMF status checks before dispatching alerts.

**Big Red Button (Track 11):**

- Formalized manual kill-switch as high-priority operational runbook.
- Immediately halts all algorithmic trading activity.
- Continuous self-evaluation integrated into live system health monitoring.

**Stress-Test Integration (Track 10):**

- AUMF scenarios used to stress-test `PaperTradingEngine`.
- Simulated flash crashes and divergence events verify system behavior under stress.

**Validation Criteria:**

- [ ] AUMF status correctly transitions to SAFE_MODE during simulated flash crashes.
- [ ] Scenario engine matches at least 5 known historical crisis patterns.
- [ ] Big Red Button halts all trading within 50ms of activation.

---

### Layer 3: Systemic Resilience Monitor (SYSTEMIC_RESILIENCE_MONITOR)

**Source:** SSRN-2235963 (Kirilenko and Lo, 2013)

**Objective:** Elevate resilience from localized circuit breakers to system-wide monitoring that detects and mitigates
complex cross-module feedback loops.

**Module: SystemicResilienceMonitor (Track 11)**

- Centralized service aggregating health metrics across all tracks.
- Monitors cross-module signals: Chronicle Queue depth (Ingestion), analytics worker latency (Analytics), system
  throughput.
- Detects "correlation events" (multiple tracks degrading simultaneously).

**Global Safe Mode (Track 4/11):**

- System-wide "Safe Mode" state triggered by correlated degradation.
- In Safe Mode:
    - All `TimescaleDbWriter` throughput is throttled.
    - `SignalGenerator` stops dispatching new alerts.
    - `VirtualPortfolio` execution is halted.
- Acts as the "circuit breaker of last resort" for the entire platform.

**Validation Criteria:**

- [ ] System correctly enters Global Safe Mode during simulated cascading failure (analytics stall + high queue depth).
- [ ] Recovery from Safe Mode requires explicit manual acknowledgment.
- [ ] Cross-module correlation detection triggers within 5 seconds of correlated degradation onset.

---

## Complementary Additions

### Auditability and Guardrails (AUDITABILITY_AND_GUARDRAILS)

**Source:** SSRN-1616043 (Muniesa, 2011)

**Objective:** Ensure full audit trail for all automated configuration changes and prevent "machine-go-mad" scenarios.

**Compliance Audit Log (Track 7):**

- Every automated configuration update requires a mandatory `audit_reason` field explaining the rationale (e.g., "
  recalibration based on Q2 2026 ILI drift").
- Dashboard displays this intent clearly alongside the change diff.
- `ConfigSnapshotRepository` augmented with human-readable "intent" field.

**Algorithmic Sanity Layer (Track 4):**

- `AlgorithmicSanityGuard` class in the Ingestion layer.
- Checks for extreme conditions (e.g., "price > 10% movement in < 1s").
- Forces system into "Manual Oversight" state if triggered.
- Acts as a global kill-switch for algorithmic trading with configurable "max-deviation" thresholds.

**Validation Criteria:**

- [ ] No configuration change can be committed without an `audit_reason`.
- [ ] AlgorithmicSanityGuard triggers on simulated "fat finger" scenarios.
- [ ] Manual Oversight state prevents all automated signal dispatch.

---

### Algorithmic Price Stability (ALGORITHMIC_PRICE_STABILITY)

**Source:** SSRN-4410212 (Altmann et al.)

**Objective:** Favor market-making (liquidity-providing) execution principles over predatory sniping to ensure the
platform acts as a market stabilizer.

**Module: MarketMakerExecutionModel (Track 10)**

- `PaperTradingEngine` execution strategy mimics "Market Making" behavior.
- Prefers limit orders to capture spread rather than aggressive market orders.
- Reduces system's contribution to price bubbles.

**Impact Reporting:**

- `volatility_impact` metric in `SignalQualityReport`.
- Measures whether executing the signal attenuated or exacerbated price volatility.
- Uses the study's findings as benchmark for "market-positive" impact.

**Validation Criteria:**

- [ ] Market-making mode produces lower volatility impact than sniper mode in backtests.
- [ ] Spread capture rate exceeds 60% in normal market conditions.

---

### Algorithm Aversion Mitigation (ALGORITHM_AVERSION_MITIGATION)

**Source:** SSRN-4817578

**Objective:** Build user trust in ILI-based signals through transparency, explainability, and human-in-the-loop
controls.

**Signal Logic Breakdown (Track 7 - Dashboard):**

- For every `ACTIONABLE` signal, a "Why?" tooltip/panel shows:
    - Contributing Z-scores (RRP, Spread, Vol).
    - Current percentile rank relative to last 252 days.
    - Active filters (e.g., "DR Window Breach confirmed").
- `SignalExplainabilityComponent` visualizes the weighted sum formula of the ILI in real-time.

**Human-in-the-Loop Toggle (Track 10):**

- `requires_approval` flag in `monitor.demo` configuration.
- "Manual Confirmation" mode for `PaperTradingEngine`: user must approve a signal before trade is logged.
- Provides the "Sense of Control" identified in the research as critical for algorithm acceptance.

**Performance Duality (Track 6 - Landing Page):**

- Side-by-side comparison of "Pure ILI" strategy vs. "Subjective Benchmark" (Buy & Hold or sentiment-only).
- Empirically demonstrates the algorithm's objective edge.
- Detailed "ILI Confidence Score" for every real-time data point.

**Mistake Attribution (Track 10):**

- In `SignalQualityReport`, explicitly attribute missed hits to "Data Anomalies" (external) or "Logic Bounds" (
  internal).
- Shows the system is "self-aware" of its limitations.

**Validation Criteria:**

- [ ] Signal explanation panel loads within 200ms.
- [ ] Human-in-the-loop mode correctly holds trades pending approval.
- [ ] Performance duality comparison updates in real-time with live data.

---

## Alternative Pluggable Implementations (A/B Testing Candidates)

### Market Gamma / GEX Monitor (MARKET_GAMMA_GEX)

**Source:** SSRN-5405157 - Black-Scholes-Merton Framework

**Status:** Optional market microstructure risk layer -- candidate for integration as a 6th axis on the Systemic Risk
Heatmap.

**Objective:** Estimate net gamma exposure of market makers in tracked symbols (SPY, QQQ), providing a predictive signal
for "hedging-induced volatility" which often precedes liquidity crises.

**Theoretical Basis:**

- Gamma is the second derivative of option price with respect to underlying price.
- Gamma Exposure (GEX): dollar amount of hedging required by market makers for every 1% move.
- Short Gamma: delta-hedging requires selling into falling market (pro-cyclical), draining liquidity.
- Long Gamma: volatility dampening via mean-reversion hedging.

**Module: BSM Greeks Calculation Service (Track 3 - Python Analytics Worker)**

- Closed-form BSM Greeks formulas calculate Gamma for every strike in the OI dataset.
- `scipy.sparse.linalg` for eigenvalue problems if spectral methods are applied.

**Module: Aggregate GEX Service (Track 3)**

- Summarizes net dollar-gamma exposure across the entire chain.
- Produces single `Market_GEX` metric.

**GEX-Weighted Volatility (Track 5):**

- Integrate `Market_GEX` into `RegimeDetector`:
    - Negative GEX (Short Gamma) = High probability of pinned volatility spikes.
    - Positive GEX (Long Gamma) = Volatility dampening (mean reversion).
- Add GEX as 6th axis to Systemic Risk Heatmap JSON.

**Options Data Ingestion (Track 4):**

- Poll options chain data (OI and Volume at various strikes) via Polygon Options API.
- Ingest ATM and OTM implied volatilities for BSM Greeks calculation.

**Database (Track 2):**

- `market_gamma_history` hypertable for GEX snapshots.

**Dashboard (Track 7):**

- "Gamma Flip Zone" visualization showing price level where market moves from positive to negative gamma.

**Validation Criteria:**

- [ ] GEX correctly predicts volatility spikes during known gamma squeeze events (e.g., GameStop 2021).
- [ ] GEX-weighted regime detection outperforms volatility-only regime detection on Sharpe ratio.
- [ ] Options data ingestion maintains < 5 second latency from Polygon API.

---

## Cross-Layer Interaction Map

```
                       +---------------------------+
                       | Layer 1: PRE-TRADE        |
                       | MarketStabilityGuard      |
                       | OrderImpactPredictor      |
                       +-----------+---------------+
                                   |
                                   v
                       +---------------------------+
                       | Layer 2: DURING-TRADE     |
                       | AUMF Scenario Engine      |
                       | Signal Suppression        |
                       | AumfStatus checks         |
                       +-----------+---------------+
                                   |
                                   v
                       +---------------------------+
                       | Layer 3: SYSTEMIC         |
                       | SystemicResilienceMonitor |
                       | Global Safe Mode          |
                       | Cross-module feedback     |
                       +-----------+---------------+
                                   |
                                   v
                       +---------------------------+
                       | Layer 4: POST-TRADE       |
                       | Audit Trail               |
                       | AlgorithmicSanityGuard    |
                       | Mistake Attribution       |
                       +-----------+---------------+
                                   |
                                   v
                  +----------------------------------+
                  | Alt: GEX Monitor                 |
                  | Options gamma exposure           |
                  | BSM Greeks calculation           |
                  | Gamma Flip Zone detection        |
                  +----------------------------------+
```

---

## Source Proposals

1. `AI_MARKET_STABILITY_GUARDRAILS_PROPOSAL.md` - Circuit breakers, MarketStabilityGuard, OrderImpactPredictor (
   SSRN-6143508)
2. `ALGORITHM_UNCERTAINTY_MANAGEMENT_PROPOSAL.md` - AUMF five-stage framework, scenario-based signal suppression, Big
   Red Button (SSRN-4179166)
3. `AUDITABILITY_AND_GUARDRAILS.md` - Audit reason fields, AlgorithmicSanityGuard for extreme conditions (SSRN-1616043)
4. `ALGORITHMIC_PRICE_STABILITY_PROPOSAL.md` - Market-making vs sniping behavior tuning, Market Impact metric (
   SSRN-4410212)
5. `SYSTEMIC_RESILIENCE_MONITOR.md` - Global Safe Mode, cross-module feedback loop detection (SSRN-2235963)
6. `MARKET_GAMMA_GEX_PROPOSAL.md` - Options gamma exposure via BSM, Gamma Squeeze detection, Polygon Options API (
   SSRN-5405157)
7. `ALGORITHM_AVERSION_MITIGATION_PROPOSAL.md` - UI transparency features, Why? panels, human-in-the-loop, performance
   duality (SSRN-4817578)
