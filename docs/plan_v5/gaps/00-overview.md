# Plan v5 vs. Implementation — Gap Status (Updated 2026-06-03)

## Summary

| Area                      | Previously Missing      | Status                                |
| ------------------------- | ----------------------- | ------------------------------------- |
| Computation Engine (Java) | 37 components           | **All 37 implemented** with tests     |
| Ingestion Layer (Java)    | 7 components            | **4 implemented** (3 deferred)        |
| CI/CD Workflows           | 3 workflows             | **All 3 created**                     |
| Database Tables           | 2 tables                | **1 view created** (1 is a column)    |
| Docker/Infra              | 3 items                 | **3 implemented** (OpenBB, Security)  |
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
| `OpenBBClient` (Java) | Direct FRED/NY Fed clients sufficient |
| `AnomalyDetectionWorker` (Java) | Python analytics worker handles this |
| Chronicle Queue | Replaced by `FileOverflowBuffer` |
| `webflux/` subproject | Deferred per original plan |

## Implementation Record

See [ADR-011](../../adr/ADR-011-gap-elimination.md) for full implementation details.
