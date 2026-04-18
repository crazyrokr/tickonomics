# Analysis Findings: Funding Market Monitor Plan v7

## 1. Architectural Redundancy: The "Dual-Mode" Maintenance Trap

**Problem:** ADR-001 and ADR-011 propose maintaining bit-perfect parity between **Spring MVC (Virtual Threads)** and *
*WebFlux (Reactive)**.

* **Risk:** This effectively doubles the development and testing effort for every ingestion pipeline, controller, and
  service. The "Cross-Stack Consistency Test Suite" (ADR-011) adds significant complexity just to manage this
  self-imposed debt.
* **Contradiction:** High-performance systems benefit from specialization. Java 25's Virtual Threads are designed
  precisely to make the blocking model (MVC) performant at scale without the cognitive load of Reactive streams.
* **Proposal:** **Consolidate on Virtual Threads.** Use Mode A (MVC + Virtual Threads) as the primary architecture.
  Reserve WebFlux only for the WebSocket relay layer if high-fan-out backpressure becomes an issue, but eliminate the "
  dual-mode" requirement for business logic and ingestion.

## 2. Analytical Bottleneck: AIC Lag Selection Loop

**Problem:** The plan (Phase 2) describes an AIC loop in Java that makes up to 10 REST calls to the Python worker per
symbol/metric pair to determine the optimal Granger lag.

* **Risk:** With 10+ symbols and multiple metrics, this creates a "N*10" REST call explosion every calculation cycle.
  JSON serialization/deserialization overhead will become the primary system bottleneck.
* **Proposal:** **Push the AIC loop into the Python worker.** Java should send the data once and request the "Optimal
  Granger Result."
* **Better Alternative:** Use **FastAPI with Apache Arrow (IPC)** or **gRPC** instead of standard REST/JSON for the
  sidecar communication to minimize serialization latency for large time-series arrays.

## 3. Data Integrity: Intraday Proxy Dislocation

**Problem:** Using 3-month T-Bills as a real-time proxy for SOFR (ADR-010) is a clever gap-fill, but it carries a hidden
risk.

* **Risk:** In "Flight to Quality" events, T-Bill yields often **drop** (buying pressure) while SOFR **spikes** (
  collateral scarcity). The proxy would show "improving liquidity" while the actual market is seizing up.
* **Proposal:** Implement a **"Proxy Divergence Guard."** If the T-Bill yield trend moves >2 standard deviations away
  from the last 5-day SOFR/T-Bill correlation, flag the ILI as `DISLOCATED` and suppress automated signals until the
  next official SOFR publication.

## 4. TimescaleDB Refresh Latency

**Problem:** The Continuous Aggregate policy for `ohlcv_1min` uses an `end_offset => INTERVAL '1 minute'`.

* **Risk:** This means the most recent minute of data is never visible in the aggregate; it is always "in flight."
* **Fix:** Enable **Real-Time Aggregates** by setting `timescaledb.materialized_only = false`. This allows TimescaleDB
  to transparently join the materialized historical data with the "raw" `tick_data` currently in the hypertable,
  providing true real-time OHLCV without manual aggregation logic.

## 5. Dependency Fragmentation: OpenBB vs. Direct Polygon

**Problem:** The plan relies on OpenBB for FRED/Fed data but uses direct Polygon WS for ticks because OpenBB removed the
Polygon provider (v4.7.0).

* **Risk:** OpenBB v4 is moving toward a "provider-heavy" model that might introduce breaking changes in endpoint
  signatures (e.g., the recent `fixedincome.sofr` → `fixedincome.rate.sofr` shift).
* **Proposal:** **Stabilize the Critical Path.** Implement a direct, lightweight Java client for FRED and the NY Fed API
  for the core `ILI` components. Use the OpenBB sidecar only for "Long Tail" analytics (econometrics, factor analysis)
  where its breadth is an asset, but keep the core "Funding Stress" logic free of sidecar dependencies.

## 6. Zero Variance Guard Refinement

**Problem:** The plan defaults Z-scores to `0.0` (neutral) when `stddev < 0.0001`.

* **Risk:** This masks "stale data" or "frozen markets" as "healthy/neutral liquidity."
* **Proposal:** Instead of `0.0`, return `NaN` and implement a **Dynamic Weighting** strategy in the `ILI` calculation.
  If a component is `NaN`, its weight should be redistributed proportionally among the remaining valid components, and
  the ILI status should be set to `DEGRADED_COMPONENT_STALE`.

## 7. Performance: Chronicle Queue Placement

**Problem:** Phase 1 uses Chronicle Queue for overflow when memory > 80%.

* **Optimization:** To ensure this doesn't throttle TimescaleDB, the Chronicle Queue path should be mapped to a *
  *`tmpfs` (RAM disk)** for transient spikes, or a dedicated **NVMe partition** with `MemoryMappedFiles` configuration.
  Avoid sharing the same physical disk as the PostgreSQL `data` directory to prevent I/O wait contention during
  high-volume bursts.

## 8. Summary of Proposed ADR Updates

* **Update ADR-001:** Pivot to Virtual Threads as the primary standard; deprecate dual-mode parity.
* **New ADR-023:** Implement Apache Arrow for high-performance sidecar IPC.
* **Update ADR-010:** Add Divergence Guard logic to the Intraday Proxy.
* **Update ADR-017:** Shift to Real-Time Aggregates (`materialized_only = false`).
