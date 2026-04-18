# Consolidated Proposal: Resilience and Operations Module

**Status:** Proposal
**Applicability:** Tickonomics v3 (Java 25 Spring Boot + Python FastAPI + TimescaleDB)

## v3 Integration Points

| Track    | Component                | Role                                                     |
|----------|--------------------------|----------------------------------------------------------|
| Track 2  | Database Schema          | Idempotency key column, ON CONFLICT handling             |
| Track 4  | Ingestion Layer          | Idempotency keys, LKG cache, bulkhead-isolated executors |
| Track 5  | Computation Engine       | Bulkhead isolation, participation governance logic       |
| Track 7  | Analytics Dashboard      | Stale data indicator (X-Data-Age header)                 |
| Track 8  | Backtesting Framework    | Liquidity stress test integration                        |
| Track 9  | CI/CD                    | OpenTelemetry integration, chaos suite                   |
| Track 10 | Demo / Virtual Portfolio | Kill-switch verification                                 |
| Track 11 | Deployment               | Bulkhead configuration, tracing infrastructure           |

---

## Core Proposal: Microservices Resilience Patterns

**Source:** RESILIENCE_IMPROVEMENTS (PRIMARY)

### 1. Idempotency Keys (Ingestion Layer)

Every external API request (FRED, NY Fed, Polygon) and message in the Ingestion Pipeline must carry an idempotency key.

#### Implementation

- Add `idempotency_key` (UUID) to `IngestionDLQ` schema.
- Update `TimescaleDbWriter` to use `INSERT ... ON CONFLICT (idempotency_key) DO NOTHING` to prevent duplicate writes
  during network retries.

#### Rationale

If a retry is triggered by a network timeout, duplicate tick data would skew ILI calculations. Idempotency keys ensure
exactly-once delivery semantics even in the presence of retries.

### 2. Distributed Tracing (Observability)

Implement comprehensive tracing beyond standard logging.

#### Implementation

- Integrate **OpenTelemetry** across the `Java Backend` and `Python Analytics Worker`.
- Pass trace IDs in headers/metadata when sending data via Arrow IPC.
- Use Jaeger or Tempo for visualizing the latency of the end-to-end signal flow from Polygon tick to Signal Generation.

#### Rationale

Identifying which component in a complex distributed chain (Java to Python to Java) is the bottleneck during high-load
market events requires distributed tracing. Standard log aggregation alone cannot reconstruct the full request path.

### 3. Graceful Degradation: Last Known Good Cache

Handle service dependency failure by serving stale but usable data.

#### Implementation

- Implement an in-memory "Last Known Good" (LKG) cache for each critical data source (FRED/NY Fed/OpenBB).
- If a circuit breaker opens, the system serves the LKG value and attaches an `X-Data-Age: STALE` header to all API
  responses.

#### Rationale

Prevents the dashboard and analytics engine from outputting empty or `NaN` results, which could trigger false alarms or
halt backtesting when a downstream source is momentarily unreachable. Users and downstream systems can inspect the
`X-Data-Age` header to make informed decisions about whether to act on stale data.

### 4. Bulkhead Pattern (Isolation)

Isolate resource pools to prevent cascading failures.

#### Implementation

Replace the single virtual thread pool with separate dedicated executor services:

1. **Critical Ingestion** (FRED/NY Fed) - highest priority, smallest pool.
2. **High-Volume Ingestion** (Polygon WS) - large pool, tolerant of delays.
3. **Computation Engine** (ILI/Signals) - medium pool, must not be starved.

If the Polygon stream hangs due to network saturation, the ILI calculation and core FRED ingestion threads remain
unaffected.

#### Rationale

Prevents a single misbehaving service (e.g., a slow WebSocket reconnection) from starving the thread pool required for
core KPI processing.

### 5. Automated Chaos Testing (Chaos Engineering)

Actively test system resilience by injecting faults in staging.

#### Implementation

- Integrate fault-injection middleware (e.g., Chaos Mesh or Toxiproxy) into integration tests.
- Create a "Chaos Suite" that validates:
    - "What if TimescaleDB latency hits 1s?"
    - "What if the Python Analytics Worker restarts?"
    - "What if the Chronicle Queue reaches capacity?"

#### Rationale

Ensures that the circuit breakers and retry logic described in the design actually function under adverse conditions.
Resilience patterns that are never tested in production-like conditions often fail when they are needed most.

### 6. Implementation Roadmap (Core)

| Priority | Feature                                | Track       |
|----------|----------------------------------------|-------------|
| **High** | Idempotency Keys in DB                 | Track 2, 4  |
| **High** | OpenTelemetry/Distributed Tracing      | Track 9     |
| **Med**  | LKG Cache for Data Sources             | Track 4, 5  |
| **Med**  | Executor Service Isolation (Bulkheads) | Track 5, 11 |
| **Low**  | Chaos Suite in CI                      | Track 9     |

---

## Complementary Additions

### A. Participation Governance and Decision Flow Formalization

**Source:** PARTICIPATION_GOVERNANCE (COMPLEMENTARY)
**Reference:** `ssrn-6745620.pdf` - "From Forecast Closure to Participation Closure: The Structural Failure Chain of
Quantitative Finance" by Xiao Gang Bai

#### Objective

The paper critiques forecast-centric models and proposes "Participation Closure" as an alternative. This aligns with
tickonomics' shift toward participation admissibility (e.g., Proxy Divergence Guard, ILI status checks). The concept
of "Participation Governance" maps directly to `IliCalculator` and `IntradayProxyService` logic where the system
determines if conditions are "admissible" for trading before allocating opportunity.

#### Formal Decision Decomposition

Formally define the `SignalGenerator` decision flow using the paper's decomposition:

$$\text{Signal}_t = g(s_t, h_t) \times b(\hat{r}_t)$$

Where:

- `g(s_t, h_t)` represents the **admissibility check**: ILI status, proxy divergence, regime checks. This function
  returns 0 (inadmissible) or 1 (admissible).
- `b(r_hat_t)` represents the **signal allocation logic**: the actual buy/sell/hold decision based on forecast returns.

Currently these are implicitly combined. Formalizing the decomposition makes the decision logic auditable and testable.

#### ParticipationGovernanceService

Consider renaming the `IliCalculator` decision orchestration layer to `ParticipationGovernanceService` to emphasize that
signal admissibility is a first-class primitive, not a secondary overlay. This service is responsible for:

- Evaluating all admissibility conditions.
- Logging which stage of the decomposition caused a suppression.
- Providing a participation audit trail.

#### Explicit Suppression Reason Logging

Update `SignalGenerator` to explicitly log which stage of the admissibility decomposition caused a suppression:

- "Forecast valid but Participation inadmissible due to proxy dislocation."
- "ILI status degraded; participation suppressed."
- "Regime check failed; participation inadmissible."

This provides the operational transparency needed for debugging and regulatory compliance.

#### Bellman-Governed Architecture Extension

Explore whether the proposed "Bellman-governed architectures" can be integrated into the backtesting framework (Track 8)
to test participation-constrained policies rather than pure forecast-optimization. This would allow backtesting
strategies that optimize for when to participate rather than just what to predict.

#### Integration Plan

- Phase: Phase 2 (Computation Engine)
- Track: 5
- Implementation: Formalize the decomposition logic in `SignalGenerator.java` and `IntradayProxyService.java`.

---

### B. Liquidity Stress Testing Module

**Source:** LIQUIDITY_STRESS_TEST (COMPLEMENTARY)
**Reference:** SSRN-3126136

#### Objective

Implement a `LiquidityStressTestModule` in Track 5 (Computation Engine) to simulate extreme liquidity withdrawal
scenarios and stress-test tickonomics' strategies.

#### Rationale

Research (SSRN-3126136) demonstrates that algorithmic traders often withdraw liquidity during extreme market stress (
e.g., the 2015 Swiss franc cap removal), leading to rapid market deterioration. Simulating this behavior allows
tickonomics to evaluate its strategies' resilience during flash-crash scenarios and improve its risk-off logic.

#### Module Structure

- Add `LiquidityStressTestModule` to `computation/src/main/java/com/tickonomics/computation/stress/`.
- Develop scenarios to simulate liquidity withdrawal and increased "uninformative volatility" based on observed data
  from the Swiss franc event.

#### Swiss Franc Cap Removal Model

The 2015 Swiss franc cap removal serves as the canonical model for extreme liquidity withdrawal. Key characteristics to
simulate:

- Sudden disappearance of algorithmic liquidity providers.
- Rapid spread widening to multiples of normal levels.
- Increased "uninformative volatility" (price moves without fundamental news).
- Cascading margin calls forcing further liquidity withdrawal.

#### Integration Points

1. **Backtest Engine (Track 8):** Integrate with the `BacktestEngine` to enable "Stress Mode," where historical
   backtests are subjected to liquidity shocks injected at random or specific points.
2. **Risk Management (Track 5):** Use stress test results to auto-adjust `SystemicResilienceMonitor` thresholds.
3. **Asset Flagging:** Flag liquidity-constrained assets that show high sensitivity to AT-driven liquidity withdrawal.

#### Benefits

- Enables proactive stress testing of trading strategies against market-documented "black swan" liquidity behavior.
- Validates the effectiveness of the platform's risk-off/deleveraging logic under extreme stress.
- Improves systemic resilience by better modeling the impact of algorithmic liquidity withdrawal.
- Complements the infrastructure resilience patterns (core proposal) by adding financial resilience testing.

---

## Source Proposals

1. **RESILIENCE_IMPROVEMENTS** (PRIMARY) - Industry best practices for microservices and distributed systems resiliency
2. **PARTICIPATION_GOVERNANCE** (COMPLEMENTARY) - `ssrn-6745620.pdf` - "From Forecast Closure to Participation Closure"
   by Xiao Gang Bai
3. **LIQUIDITY_STRESS_TEST** (COMPLEMENTARY) - SSRN-3126136
