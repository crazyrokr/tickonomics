# Deployment Procedures

## Local Development

Start all services:

```bash
docker compose up -d
```

Follow logs:

```bash
docker compose logs -f backend
```

Stop all services:

```bash
docker compose down
```

Stop and remove volumes (resets database):

```bash
docker compose down -v
```

## Demo Environment

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
```

Demo mode enables the virtual portfolio with $100,000 balance and auto-executes signals.

## Production

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Production overrides add:
- Resource limits on all services
- Restart policies (`unless-stopped`)
- TimescaleDB port not exposed to host
- JSON file logging with rotation

## Building Individual Services

```bash
docker compose build backend
docker compose build analytics-worker
docker compose build dashboard
docker compose build landing
```

Rebuild without cache:

```bash
docker compose build --no-cache backend
```

## Rolling Updates

1. Build the updated image:

   ```bash
   docker compose build backend
   ```

2. Restart only the changed service:

   ```bash
   docker compose up -d --no-deps backend
   ```

3. Verify health:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

## Rollback

1. Find the previous image:

   ```bash
   docker images tickonomics-backend
   ```

2. Tag and restart with the previous image:

   ```bash
   docker compose down backend
   docker run -d --name backend <previous-image-id>
   ```

## Smoke Tests

After deployment, verify all services:

```bash
curl -s http://localhost:8080/actuator/health | jq .status
curl -s http://localhost:8001/health | jq .status
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
curl -s -o /dev/null -w "%{http_code}" http://localhost:16686
```

Expected: all return `UP` or `200`.

## Initial Setup

1. Copy `.env.example` to `.env` and configure:

   ```bash
   cp .env.example .env
   ```

2. Set `FINNHUB_API_KEY` (and `ALPHAVANTAGE_API_KEY`) for live market data. Yahoo Finance, FRED,
   NY Fed, and Ken French require no key.

3. Start services. Flyway migrations apply automatically on first backend startup.
