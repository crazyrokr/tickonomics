# ADR-004: Micrometer Observation API for OpenTelemetry Tracing

**Date:** 2026-05-30
**Status:** Accepted

## Context

The application had no observability beyond SLF4J logging. OpenTelemetry is the standard for distributed tracing, but integrating the OTel SDK directly requires manual span creation, context propagation, and exporter configuration.

## Decision

Use Spring Boot 3.5's Micrometer Observation API with the OTel bridge (`micrometer-tracing-bridge-otel`). This auto-instruments `RestClient`, `JdbcTemplate`, and `@Scheduled` tasks without explicit OTel SDK usage. The OTLP exporter sends traces to a configurable endpoint.

Dependencies added:
- `io.micrometer:micrometer-observation` (ingestion, computation)
- `io.micrometer:micrometer-tracing-bridge-otel` (ingestion, computation)
- `io.opentelemetry:opentelemetry-exporter-otlp` (app, runtimeOnly)
- `spring-boot-starter-actuator` (app)

## Consequences

- Zero-instrumentation tracing for RestClient calls and JdbcTemplate queries
- Custom spans via `@Observed` annotation where needed
- OTLP exporter configured via `management.otlp.tracing.endpoint`
- Tracing is production-ready but sampling probability is configurable (`management.tracing.sampling.probability`)
