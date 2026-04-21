# OpenBB Platform Sidecar Setup

## Overview

The OpenBB Platform sidecar provides supplementary market data, econometrics,
and equity pricing to the analytics worker via a containerised REST API.

## Prerequisites

- Docker Engine >= 24.0
- Docker Compose v2
- `docker-compose.openbb.yml` present in the project root

## Starting the Sidecar

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.openbb.yml \
  up -d openbb
```

The container takes up to 60 seconds to become ready. Wait for the health check
to pass before proceeding.

## Health Check

```bash
curl -s http://localhost:8002/api/v1/health
```

Expected response: `{"status": "ok"}`

## Configuring the Analytics Worker

Set the environment variable so the analytics worker can reach OpenBB:

```bash
export ANALYTICS_OPENBB_URL=http://openbb:8002
```

In `docker-compose.openbb.yml` this is already wired through the internal
network. For local development add it to your `.env` file.

## Available Endpoints

| Endpoint | Purpose |
|---|---|
| `/api/v1/equity/price` | Historical and current equity prices |
| `/api/v1/econometrics` | Econometric data feeds |

Full OpenAPI spec is available at `http://localhost:8002/docs`.

## Data Persistence

All cached data is stored in the `openbb_data` Docker volume:

```bash
docker volume inspect openbb_data
```

The volume survives container restarts. To wipe cached data:

```bash
docker compose -f docker-compose.yml -f docker-compose.openbb.yml down -v
```

## Troubleshooting

### Slow Startup

The health check has a 60-second start period. If it still fails after that:

```bash
docker compose -f docker-compose.yml -f docker-compose.openbb.yml logs openbb --tail 50
```

### API Rate Limits

OpenBB proxies external providers that enforce rate limits. If you see `429`
responses, reduce request frequency or check the provider dashboard for quota
status.
