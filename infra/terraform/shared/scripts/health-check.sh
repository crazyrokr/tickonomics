#!/usr/bin/env bash
set -euo pipefail

MAX_WAIT="${1:-600}"
ELAPSED=0
INTERVAL=10

echo "[health-check] Waiting up to ${MAX_WAIT}s for all services to be healthy..."

while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
  BACKEND=$(docker inspect --format='{{.State.Health.Status}}' forecast-backend-1 2>/dev/null || echo "missing")
  ANALYTICS=$(docker inspect --format='{{.State.Health.Status}}' forecast-analytics-worker-1 2>/dev/null || echo "missing")
  DB=$(docker inspect --format='{{.State.Health.Status}}' forecast-timescaledb-1 2>/dev/null || echo "missing")

  echo "[health-check] backend=${BACKEND} analytics=${ANALYTICS} db=${DB} (${ELAPSED}s/${MAX_WAIT}s)"

  if [ "$BACKEND" = "healthy" ] && [ "$ANALYTICS" = "healthy" ] && [ "$DB" = "healthy" ]; then
    echo "[health-check] All services healthy after ${ELAPSED}s"
    exit 0
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo "[health-check] TIMEOUT: services not healthy after ${MAX_WAIT}s" >&2
docker compose -f /opt/tickonomics/docker-compose.forecast.yml ps
exit 1
