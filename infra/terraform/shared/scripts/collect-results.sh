#!/usr/bin/env bash
set -euo pipefail

TASK_ID="${1:-$(date +%Y%m%d-%H%M%S)}"
BUCKET="${RESULTS_BUCKET:-}"
REGION="${AWS_REGION:-us-east-1}"
RESULTS_DIR="/opt/tickonomics/results"
API_BASE="http://localhost:8080"
ANALYTICS_BASE="http://localhost:8001"

mkdir -p "$RESULTS_DIR"

echo "[collect] Collecting forecast results for task ${TASK_ID}"

collect_endpoint() {
  local name="$1"
  local url="$2"
  local outfile="${RESULTS_DIR}/${name}.json"

  echo "[collect] GET ${url}"
  if curl -sf --max-time 30 "${url}" -o "${outfile}"; then
    echo "[collect]   -> ${outfile} ($(wc -c < "${outfile}") bytes)"
  else
    echo "[collect]   -> FAILED (non-fatal)"
    echo '{"error": "endpoint unreachable"}' > "${outfile}"
  fi
}

collect_endpoint "kpis"              "${API_BASE}/api/v1/kpis"
collect_endpoint "signals"           "${API_BASE}/api/v1/signals"
collect_endpoint "strategies"        "${API_BASE}/api/v1/strategies"
collect_endpoint "backtest-results"  "${API_BASE}/api/v1/backtest/results"
collect_endpoint "risk-summary"      "${API_BASE}/api/v1/risk/summary"
collect_endpoint "regime"            "${API_BASE}/api/v1/regime"
collect_endpoint "liquidity"         "${API_BASE}/api/v1/liquidity"

collect_endpoint "analytics-evt"     "${ANALYTICS_BASE}/api/v1/statistical/evt"
collect_endpoint "analytics-risk"    "${ANALYTICS_BASE}/api/v1/risk"
collect_endpoint "analytics-regime"  "${ANALYTICS_BASE}/api/v1/regime"
collect_endpoint "analytics-diagnostics" "${ANALYTICS_BASE}/api/v1/diagnostics"

echo "[collect] Dumping PostgreSQL database..."
docker exec forecast-timescaledb-1 pg_dump -U tickonomics tickonomics \
  --no-owner --no-privileges --compress=9 \
  > "${RESULTS_DIR}/tickonomics_dump.sql.gz" 2>/dev/null \
  || echo "[collect] pg_dump failed (non-fatal)"

echo "[collect] Creating results archive..."
tar -czf "/tmp/forecast-results-${TASK_ID}.tar.gz" -C "$RESULTS_DIR" .

if [ -n "$BUCKET" ]; then
  echo "[collect] Uploading to s3://${BUCKET}/${TASK_ID}/"
  aws s3 cp "/tmp/forecast-results-${TASK_ID}.tar.gz" \
    "s3://${BUCKET}/${TASK_ID}/forecast-results.tar.gz" \
    --region "$REGION"
  echo "[collect] Upload complete"
else
  echo "[collect] No RESULTS_BUCKET set; archive at /tmp/forecast-results-${TASK_ID}.tar.gz"
fi

echo "[collect] Done"
