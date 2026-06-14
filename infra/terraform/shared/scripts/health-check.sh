#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-/opt/tickonomics/docker-compose.forecast.yml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-forecast}"
export COMPOSE_PROJECT_NAME="$PROJECT_NAME"

MAX_WAIT="${1:-600}"
ELAPSED=0
INTERVAL=10

service_health() {
  local service="$1"
  local container_id
  container_id=$(docker compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null || true)
  if [ -z "$container_id" ]; then
    echo "missing"
    return
  fi
  docker inspect --format='{{.State.Health.Status}}' "$container_id" 2>/dev/null || echo "starting"
}

echo "[health-check] Waiting up to ${MAX_WAIT}s for all services to be healthy..."

while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
  BACKEND=$(service_health "backend")
  ANALYTICS=$(service_health "analytics-worker")
  DB=$(service_health "timescaledb")

  echo "[health-check] backend=${BACKEND} analytics=${ANALYTICS} db=${DB} (${ELAPSED}s/${MAX_WAIT}s)"

  if [ "$BACKEND" = "healthy" ] && [ "$ANALYTICS" = "healthy" ] && [ "$DB" = "healthy" ]; then
    echo "[health-check] All services healthy after ${ELAPSED}s"
    exit 0
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo "[health-check] TIMEOUT: services not healthy after ${MAX_WAIT}s" >&2
docker compose -f "$COMPOSE_FILE" ps || true
exit 1
