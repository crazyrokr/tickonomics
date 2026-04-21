# ADR-010: Deployment & Operations Architecture

**Date:** 2026-05-31
**Status:** Accepted
**Supersedes:** None

## Context

Track 11 is the final implementation track. All 10 prior tracks are complete, but the project has no containerization infrastructure. The CI/CD pipelines (ADR-008) have placeholder deploy steps awaiting Docker images. We need Dockerfiles, Docker Compose configurations for multiple environments, observability infrastructure, and operational runbooks.

## Decision

### 1. Multi-stage Docker builds

All four services use multi-stage builds to minimize runtime image size:

| Service | Build stage | Runtime image | Size estimate |
|---------|-------------|---------------|---------------|
| Backend | `eclipse-temurin:25-jdk` | `eclipse-temurin:25-jre` | ~250MB |
| Analytics worker | `python:3.12-slim` | `python:3.12-slim` | ~400MB |
| Dashboard | `node:22-alpine` | `node:22-alpine` | ~120MB |
| Landing | `node:22-alpine` | `nginx:1.27-alpine` | ~25MB |

Build tools (cmake, gcc, git, maven) are installed in the build stage only and discarded.

### 2. Next.js standalone output for dashboard

The analytics dashboard uses `output: "standalone"` in `next.config.ts`. This produces a self-contained server without dev dependencies, reducing the runtime image from ~300MB (full node_modules) to ~120MB.

The landing page uses `output: "export"` (already configured) and is served by nginx.

### 3. nginx for static landing page

The landing page is a fully static export (`out/` directory). nginx serves it with SPA routing support (`try_files`), long-lived cache headers for static assets, and gzip compression. This avoids running a Node.js process for static content.

### 4. Jaeger all-in-one for observability

`jaegertracing/all-in-one:1.64` provides distributed tracing via OTLP gRPC (port 4317). The Spring Boot backend is already configured with Micrometer OTel bridge (ADR-004) exporting to this endpoint.

### 5. Docker Compose override pattern

Three compose files support different environments:
- `docker-compose.yml` — base configuration for local development
- `docker-compose.demo.yml` — enables demo mode with virtual portfolio
- `docker-compose.prod.yml` — resource limits, restart policies, internal-only DB port

Used together: `docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d`

### 6. Non-root container users

All services run as non-root users (`tickonomics`, `analytics`, `dashboard`). This reduces the impact of container escape vulnerabilities.

### 7. TA-Lib native bundled in JAR

The TA-Lib native `.so` is built in the Docker build stage and bundled into the Spring Boot fat JAR via the `copyTalibNative` Gradle task. The JNA wrapper loads it from the classpath at runtime. This avoids volume-mounting shared libraries or installing system packages in the runtime image.

### 8. Network isolation

All services share a single bridge network (`tickonomics-net`). In production, the TimescaleDB port is not exposed to the host — only accessible from other containers on the network.

### 9. requirements.txt format fix

The `analytics/requirements.txt` used `:` as the version separator (e.g., `fastapi:0.136.1`) instead of the standard `==`. This was corrected to `fastapi==0.136.1` as it would prevent `pip install` from working in the Docker build.

## Consequences

**Positive:**
- Single command (`docker compose up -d`) starts the entire stack
- Reproducible builds across environments
- Production compose hardening without modifying the base configuration
- Minimal runtime images reduce attack surface and deployment time
- Distributed tracing works out of the box

**Negative:**
- Backend Docker build is slow (~10 min) due to TA-Lib native compilation
- Analytics worker image is large due to numpy/scipy/pytorch dependencies
- Jaeger all-in-one is not suitable for high-volume production tracing (would need collector + backend separation)

**Mitigations:**
- Docker layer caching speeds up rebuilds when only application code changes
- Analytics worker deps rarely change, so the build stage is usually cached
- For production tracing at scale, replace Jaeger all-in-one with OTel Collector + Tempo/Jaeger backend
