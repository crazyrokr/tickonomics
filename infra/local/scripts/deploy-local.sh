#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="$(cd "${LOCAL_DIR}/../.." && pwd)"
ENV_FILE="${LOCAL_DIR}/.env.local"
COMPOSE_FILE="${LOCAL_DIR}/docker-compose.local.yml"

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: ${ENV_FILE} not found."
  echo "  cp ${LOCAL_DIR}/.env.local.example ${ENV_FILE}"
  echo "  Fill in the values before deploying."
  exit 1
fi

echo "=== Building images from source ==="
cd "${PROJECT_DIR}"
docker compose -f docker-compose.yml build

echo "=== Starting all services ==="
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans

echo "=== Waiting for backend health ==="
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "All services healthy."
    echo ""
    echo "  Dashboard:  http://localhost:3000 (or via Caddy on port 80)"
    echo "  API:        http://localhost:8080"
    echo "  Grafana:    http://localhost:3001"
    echo "  Jaeger:     http://localhost:16686"
    echo "  Prometheus: http://localhost:9090"
    exit 0
  fi
  sleep 5
done

echo "WARNING: Backend health check timed out after 5 minutes."
echo "  Check: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} ps"
exit 1
