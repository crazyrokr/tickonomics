#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${LOCAL_DIR}/docker-compose.forecast.local.yml}"
RESULTS_DIR="${RESULTS_DIR:-${LOCAL_DIR}/forecast-results}"
MIN_ROWS="${MIN_ROWS:-50}"
COMPUTE_SETTLE_SECONDS="${COMPUTE_SETTLE_SECONDS:-120}"
API_BASE="http://localhost:8080"
ANALYTICS_BASE="http://localhost:8001"

TASK_ID="${TASK_ID:-$(date +%Y%m%d-%H%M%S)}"
RUN_DIR="${RESULTS_DIR}/${TASK_ID}"
mkdir -p "$RUN_DIR"

log() { echo "[$(date '+%H:%M:%S')] [${TASK_ID}] $*"; }

set_status() {
  echo "$1" > "${RUN_DIR}/STATUS"
}

cleanup() {
  local exit_code=$?
  local current=""
  [ -f "${RUN_DIR}/STATUS" ] && current=$(cat "${RUN_DIR}/STATUS" 2>/dev/null || echo "")
  if [ "$exit_code" -ne 0 ] && [ "$current" != "COMPLETED" ]; then
    log "Pipeline failed (exit ${exit_code}); marking FAILED"
    set_status "FAILED"
  fi
  log "Stopping containers..."
  docker compose -f "$COMPOSE_FILE" down --timeout 60 2>/dev/null || true
}
trap cleanup EXIT

set_status "RUNNING"
log "=== Tickonomics Local Forecast ${TASK_ID} ==="

log "Starting forecast services..."
docker compose -f "$COMPOSE_FILE" up -d

log "Waiting for backend health..."
for i in $(seq 1 60); do
  if curl -sf "${API_BASE}/actuator/health" >/dev/null 2>&1; then
    log "Backend healthy"
    break
  fi
  [ "$i" -eq 60 ] && { log "FATAL: backend health timeout"; exit 1; }
  sleep 5
done

log "Waiting for data ingestion (min ${MIN_ROWS} rows)..."
ROW_COUNT=0
for i in $(seq 1 120); do
  ROW_COUNT=$(docker compose -f "$COMPOSE_FILE" exec -T timescaledb \
    psql -U tickonomics -t -c "SELECT COUNT(*) FROM rate_snapshots" 2>/dev/null | xargs || echo "0")
  log "  rate_snapshots rows: ${ROW_COUNT} (${i}x5s)"
  if [ "${ROW_COUNT:-0}" -ge "$MIN_ROWS" ] 2>/dev/null; then
    break
  fi
  sleep 5
done

if [ "${ROW_COUNT:-0}" -lt "$MIN_ROWS" ] 2>/dev/null; then
  log "FATAL: only ${ROW_COUNT:-0} rows available (minimum: ${MIN_ROWS}). Aborting."
  exit 1
fi
log "Ingestion threshold reached: ${ROW_COUNT} rows"

log "Triggering analytics forecast (backend @Scheduled jobs drive KPI/signal compute)..."
curl -sf -X POST "${ANALYTICS_BASE}/api/v1/analytics/volatility-forecast" --max-time 300 \
  || log "WARNING: analytics volatility-forecast failed or unavailable"

log "Settling ${COMPUTE_SETTLE_SECONDS}s for scheduled KPI/signal computation..."
sleep "$COMPUTE_SETTLE_SECONDS"

log "Collecting results..."
collect_endpoint() {
  local name="$1" url="$2"
  local outfile="${RUN_DIR}/${name}.json"
  if curl -sf --max-time 30 "${url}" -o "${outfile}"; then
    log "  collected ${name} ($(wc -c < "${outfile}") bytes)"
  else
    echo '{"error": "endpoint unreachable"}' > "${outfile}"
    log "  WARNING: ${name} collection failed (non-fatal)"
  fi
}

collect_endpoint "signals"              "${API_BASE}/api/v1/quant/signals/active"
collect_endpoint "strategies"           "${API_BASE}/api/v1/quant/strategies/active"
collect_endpoint "risk-tail-parameters" "${API_BASE}/api/v1/quant/risk/tail-parameters"
collect_endpoint "risk-evt-tail"        "${API_BASE}/api/v1/quant/risk/evt-tail"
collect_endpoint "macro-shock-response" "${API_BASE}/api/v1/quant/macro/shock-response"
collect_endpoint "volatility-forecast"  "${ANALYTICS_BASE}/api/v1/analytics/volatility-forecast"

log "Dumping PostgreSQL database..."
docker compose -f "$COMPOSE_FILE" exec -T timescaledb pg_dump -U tickonomics tickonomics \
  --no-owner --no-privileges --compress=9 > "${RUN_DIR}/pg_dump.sql.gz" 2>/dev/null \
  || log "WARNING: pg_dump failed (non-fatal)"

log "Archiving results..."
tar -czf "${RESULTS_DIR}/forecast-${TASK_ID}.tar.gz" -C "$RESULTS_DIR" "$TASK_ID" 2>/dev/null || true

set_status "COMPLETED"
log "=== Local Forecast ${TASK_ID} complete ==="
