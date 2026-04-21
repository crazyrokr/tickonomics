# ADR-002: File-Based Overflow Buffer vs. Chronicle Queue

**Date:** 2026-05-30
**Status:** Accepted

## Context

The `TimescaleDbWriter` buffers data in-memory (`ConcurrentLinkedQueue`). On JVM crash, all buffered data is lost. The implementation plan specified Chronicle Queue for disk-backed persistence, but Chronicle Queue introduces significant operational complexity: native memory-mapped files, OS-level page alignment, and a heavy dependency footprint.

## Decision

Use a JSON-lines file-based overflow buffer instead of Chronicle Queue. The `TieredIngestionBuffer` delegates to an in-memory queue first, and overflows to a local JSON-lines file when the memory capacity is exceeded. On startup, any existing overflow file is replayed back into memory.

## Consequences

- **Simpler:** No native dependencies, pure Java I/O with Jackson serialization
- **Crash-safe:** Overflow items survive JVM kill; only in-memory items are at risk
- **Performance trade-off:** File I/O is millisecond-scale vs. Chronicle Queue's microsecond-scale, but overflow is only hit during ingestion spikes — the primary path remains in-memory
- **Recovery:** On restart, overflow file is replayed and truncated automatically
