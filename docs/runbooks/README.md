# Tickonomics Runbooks

Operational procedures for deployment, monitoring, data management, and incident response for the
Tickonomics platform.

## Quick Reference

| Endpoint | URL | Purpose |
|----------|-----|---------|
| Backend health | `http://localhost:8080/actuator/health` | Spring Boot health check |
| Analytics worker health | `http://localhost:8001/health` | FastAPI health with dependency versions |
| Dashboard | `http://localhost:3001` | Analytics dashboard UI |
| Landing page | `http://localhost:3000` | Marketing page |
| Jaeger UI | `http://localhost:16686` | Distributed tracing |
| TimescaleDB | `localhost:5432` | PostgreSQL (dev only) |

## Runbook Index

### Deployment & Operations
- [Deployment Procedures](deployment.md) — Starting, stopping, updating services across environments
- [Analytics Worker Deployment](analytics-worker-deployment.md) — Build, start, and verify the Python FastAPI worker
- [Data Management](data-management.md) — Retention, compression, backup, recovery
- [Monitoring & Troubleshooting](monitoring-troubleshooting.md) — Health checks, tracing, common issues

### Data Sources (v6 free-tier)
- [Finnhub WebSocket + Yahoo Finance Setup](finnhub-yahoo-setup.md) — Configure and verify free data source clients
- [Finnhub WebSocket Outage Response](finnhub-websocket-outage.md) — Circuit breaker, REST fallback, recovery
- [Options Data Pipeline Verification](options-data-pipeline.md) — Options ingestion and GEX calculation
- [Proxy Divergence Event Review](proxy-divergence-event-review.md) — SOFR / T-Bill dislocation handling (Finding 3)
- [De-Rounding & Periodicity Filter Verification](derounding-filter-verification.md) — Data quality filter checks
- [Disaster Alert Verification](disaster-alert-verification.md) — Exogenous-shock regime trigger checks

### Resilience & Safety
- [Big Red Button — Kill-Switch](big-red-button.md) — Manual emergency halt (`/api/v1/demo/kill-switch/*`)
- [Systemic Resilience Monitor — Global Safe Mode](systemic-resilience-monitor.md) — Automatic correlated-degradation protection (`/api/v1/demo/safe-mode/*`)
- [Chronicle / Overflow Buffer Recovery](chronicle-queue-overflow.md) — Ingestion overflow volume replay
- [Bulkhead Pool Monitoring](bulkhead-pool-monitoring.md) — Per-pool executor utilization tuning

### Monitoring & Tuning
- [Distributed Tracing with Jaeger](distributed-tracing-jaeger.md) — End-to-end Java→Python→Java latency
- [Calibration Task Monitoring](calibration-task-monitoring.md) — `ScheduledCalibrationTask` lifecycle
- [ILI Weight Recalibration](ili-weight-recalibration.md) — Bayesian weight optimization review
- [TimescaleDB Continuous Aggregate Refresh](timescaledb-aggregate-refresh.md) — CAGG staleness and refresh
- [TA-Lib Adapter Integration](talib-adapter-integration.md) — Pure-Java TA-Lib usage

### Compliance
- [Regulatory Compliance Report Generation](regulatory-compliance-report.md) — MiFID II-style self-certification reports

## Related

- Plan: [`docs/plan_v6/11-deployment-operations.md`](../plan_v6/11-deployment-operations.md)
- Architecture decisions: [`docs/adr/`](../adr/) — notably ADR-017 (kill-switch) and ADR-018 (Systemic Resilience Monitor)
