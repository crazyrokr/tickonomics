# Ingestion Layer — 7 Missing Components

**Plan ref:** `04-ingestion-layer.md`

| #   | Component                       | Plan Section  | Description                                                                                                 |
| --- | ------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------- |
| 1   | `DisasterAlertClient`           | §Component 15 | USGS Earthquake + GDACS RSS polling, EXOGENOUS_SHOCK regime trigger                                         |
| 2   | `AlgorithmicSanityGuard`        | §Component 19 | Price >10% in <1s or >10000 msg/sec detection, Manual Oversight trigger                                     |
| 3   | `OrderCancellationMonitor`      | §Component 23 | Cancellation ratios per symbol, volatility leading indicator                                                |
| 4   | `OpenBBClient` (Java)           | §Component 3  | OpenBB sidecar for equity/ETF prices (deferred to Python-only)                                              |
| 5   | `EventBasedTimeConverter`       | §Component 14 | Maps raw ticks to directional change and overshoot events                                                   |
| 6   | `AnomalyDetectionWorker` (Java) | §Component 10 | Async autoencoder-based anomaly detection in Java                                                           |
| 7   | **Chronicle Queue integration** | §Component 6  | Off-heap ring buffer + disk-backed overflow (replaced by `TieredIngestionBuffer` with `FileOverflowBuffer`) |

> **Note:** Items 4, 6, and 7 may be intentional design simplifications — the analytics worker handles anomaly detection in Python, and `FileOverflowBuffer` replaces Chronicle Queue. `OpenBBClient` was deferred in favor of direct FRED/NY Fed clients.
