# Tickonomics Project Improvement Proposals

Following an analysis of the existing `PLAN_V2` architecture and technical insights comparing **TimescaleDB** and *
*QuestDB**, the following improvements are proposed to optimize performance, maintainability, and scalability.

## 1. Time-Series Storage Architecture: Optimization

The current plan utilizes **TimescaleDB** for both time-series and relational data. While this simplifies
infrastructure, high-throughput financial data (like tick-level market data) can benefit from specialized time-series
engines.

* **Proposal:** Implement a "Tiered Storage" strategy.
    * **Hot Path (QuestDB or TimeBase-CE):** Keep the last 24–48 hours of tick data in an ultra-high-performance
      engine (like QuestDB, which provides vectorized query execution and zero-garbage-collection architecture). This
      addresses the "fast ingestion" requirement highlighted in the QuestDB/TimescaleDB comparison.
    * **Cold Path (TimescaleDB):** Migrate aggregated, historical tick data (after 48 hours) to TimescaleDB, leveraging
      its robust SQL support, compression, and mature PostgreSQL ecosystem for analytical and long-term reporting.
* **Rationale:** QuestDB excels at ingestion speed (4M rows/sec) and minimal hardware usage, but TimescaleDB is superior
  for complex join-heavy analytics and enterprise reliability. Tiering offers the best of both worlds.

## 2. Ingestion Resilience: Chronicle Queue Refinement

The plan identifies `Chronicle Queue` for disk-based overflow.

* **Proposal:** Implement a "Wait-Free" ingestion path for critical metrics.
    * Ensure the Java `TimescaleDbWriter` uses low-latency, garbage-free patterns (similar to QuestDB's internal
      philosophy: off-heap data structures and zero-GC).
    * For the Chronicle Queue overflow, configure it specifically for **memory-mapped files** on the dedicated NVMe
      partition to ensure consistent latency during ingestion spikes.
* **Rationale:** Preventing latency spikes during critical market events (e.g., funding market dislocation) is
  essential. A GC-heavy Java ingestion layer is a major bottleneck.

## 3. Query Performance: Advanced Materialization

* **Proposal:** Enhance real-time continuous aggregates with "Pre-computed Views" for complex KPIs.
    * Current plan: Uses `materialized_only=false` aggregates.
    * Improvement: Pre-compute complex calculations (like rolling Correlation or Beta) at the database layer using
      user-defined functions or custom continuous aggregate refresh cycles, rather than forcing the application layer or
      analytics worker to recompute them from raw ticks.
* **Rationale:** Shifting expensive math to the database engine (especially if using TimeBase's native analytics
  functions or TimescaleDB's hyper-functions) significantly reduces the load on the Java ingestion and computation
  engines.

## 4. Analytics Worker: IPC Efficiency

The plan correctly identifies **Apache Arrow IPC** as the solution to JSON bottlenecks.

* **Proposal:** Implement shared-memory transport for the Java-to-Python connection if the worker runs on the same host.
    * Current: Arrow IPC via network/socket.
    * Improvement: Use shared-memory file buffers for IPC between the Java process and the Python worker to eliminate
      network overhead entirely.
* **Rationale:** For ultra-low latency signal generation, IPC via network is still a bottleneck. Shared memory (
  supported by Arrow) reduces latency to nanoseconds.

## 5. Architectural Alignment: Governance & Data Consistency

* **Proposal:** Adopt the FINOS Common Domain Model (CDM) more strictly in the `IngestionLayer`.
    * Instead of custom `FederationDataClient` return types, create a "CDM Adapter" layer that maps ingested raw data
      into canonical CDM objects before they reach the `ComputationEngine`.
* **Rationale:** This ensures the computation engine (which calculates KPIs) works on a standardized data model,
  regardless of whether the source was FRED, NY Fed, or Polygon, reducing the risk of logical errors in the ILI formula
  due to data-source-specific field variations.

## Implementation Roadmap (Phased)

1. **Immediate (Phase 0/1):** Implement the `cdm/` module and force the canonical model in the `IngestionLayer`.
2. **Short-term (Phase 2):** Benchmark Java ingestion layer for GC spikes; adopt off-heap patterns if necessary.
3. **Medium-term (Phase 4):** Evaluate adding QuestDB as a "Hot Ingestion" sidecar if tick-level performance bottlenecks
   emerge in TimescaleDB.
4. **Long-term:** Implement Shared Memory Arrow IPC for the Analytics Worker.
