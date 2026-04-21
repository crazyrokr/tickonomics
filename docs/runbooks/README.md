# Tickonomics Runbooks

Operational procedures for deployment, monitoring, and data management.

## Quick Reference

| Endpoint | URL | Purpose |
|----------|-----|---------|
| Backend health | `http://localhost:8080/actuator/health` | Spring Boot health check |
| Analytics worker health | `http://localhost:8001/health` | FastAPI health with dependency versions |
| Dashboard | `http://localhost:3001` | Analytics dashboard UI |
| Landing page | `http://localhost:3000` | Marketing page |
| Jaeger UI | `http://localhost:16686` | Distributed tracing |
| TimescaleDB | `localhost:5432` | PostgreSQL (dev only) |

## Runbooks

- [Deployment Procedures](deployment.md) — Starting, stopping, updating services across environments
- [Monitoring & Troubleshooting](monitoring-troubleshooting.md) — Health checks, tracing, common issues
- [Data Management](data-management.md) — Retention, compression, backup, recovery
