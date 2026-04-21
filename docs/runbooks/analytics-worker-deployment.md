# Analytics Worker Deployment

## Overview

The Python analytics worker exposes quantitative analysis, risk, backtesting,
and econometrics endpoints via a FastAPI application.

## Building the Container Image

```bash
docker compose build analytics-worker
```

## Running Locally (Development)

```bash
cd analytics
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

## Running via Docker Compose

```bash
docker compose up -d analytics-worker
```

## Health Check

```bash
curl -s http://localhost:8001/health
```

Expected response: `{"status": "ok"}`

## API Documentation

Swagger UI is available at:

```
http://localhost:8001/docs
```

ReDoc is available at:

```
http://localhost:8001/redoc
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PYTHONPATH` | `/app` | Module search path |
| `LOG_LEVEL` | `INFO` | Logging verbosity |
| `ANALYTICS_HOST` | `0.0.0.0` | Bind address |
| `ANALYTICS_PORT` | `8001` | Bind port |

Set these in `.env` or pass them directly:

```bash
LOG_LEVEL=DEBUG docker compose up analytics-worker
```

## Scaling

The worker is stateless and can be horizontally scaled behind a load balancer:

```bash
docker compose up -d --scale analytics-worker=3
```

Ensure the load balancer (e.g., nginx, traefik) routes to all instances and
uses the `/health` endpoint for active health checks.

## Monitoring

Check health:

```bash
curl -sf http://localhost:8001/health || echo "UNHEALTHY"
```

Stream logs:

```bash
docker compose logs analytics-worker -f --tail 100
```

Watch for `ERROR` or `CRITICAL` lines in production and alert on elevated
latency in the `/health` response time.
