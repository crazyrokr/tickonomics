# ADR-003: Semaphore-Based Bulkheads with Virtual Threads

**Date:** 2026-05-30
**Status:** Accepted

## Context

The application enables Spring Boot virtual threads (`spring.threads.virtual.enabled: true`). Resilience4j supports two bulkhead types: semaphore-based and thread-pool-based. With virtual threads, thread-pool isolation is semantically weaker because virtual threads are cheap and effectively unlimited.

## Decision

Use Resilience4j `@Bulkhead` with `Bulkhead.Type.SEMAPHORE` (the default) for concurrency control. Three semaphore bulkheads are configured:
- `criticalIngestion` (permits=4) for FRED/NY Fed polling
- `highVolumeIngestion` (permits=16) for Polygon WS processing
- `computationEngine` (permits=8) for KPI computation

## Consequences

- Correct abstraction for virtual threads: limits concurrency at the call site rather than thread pool size
- Prevents cascading overload: if FRED API is slow, semaphore backpressure limits concurrent calls without consuming unbounded virtual threads
- Independent isolation: FRED/NY Fed saturation does not affect Polygon or computation capacity
