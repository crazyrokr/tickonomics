#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="/etc/tickonomics/config.env"
if [ -f "$CONFIG_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
  set +a
fi

FORECAST_DIR="/opt/tickonomics"
COMPOSE_FILE="${COMPOSE_FILE:-${FORECAST_DIR}/docker-compose.forecast.yml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-forecast}"
SCRIPTS_DIR="${FORECAST_DIR}/scripts"
RESULTS_DIR="${RESULTS_DIR:-${FORECAST_DIR}/results}"
TASK_ID="${TASK_ID:-$(date +%Y%m%d-%H%M%S)}"
AUTO_TERMINATE="${AUTO_TERMINATE:-true}"
INGESTION_TIMEOUT="${INGESTION_TIMEOUT:-600}"
MIN_ROWS="${MIN_ROWS:-50}"
COMPUTE_SETTLE_SECONDS="${COMPUTE_SETTLE_SECONDS:-120}"
RESULTS_BUCKET="${RESULTS_BUCKET:-}"
AWS_REGION="${AWS_REGION:-us-east-1}"

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

mkdir -p "$RESULTS_DIR" /mnt/timescaledb

upload_status() {
  local status="$1"
  echo "$status" > "${RESULTS_DIR}/STATUS"
  if [ -n "$RESULTS_BUCKET" ] && command -v aws >/dev/null 2>&1; then
    echo "$status" | aws s3 cp - "s3://${RESULTS_BUCKET}/${TASK_ID}/STATUS" \
      --region "$AWS_REGION" 2>/dev/null || log "WARNING: status upload failed"
  fi
}

upload_partial_results() {
  if [ ! -d "$RESULTS_DIR" ] || [ -z "$(ls -A "$RESULTS_DIR" 2>/dev/null)" ]; then
    return 0
  fi
  if [ -z "$RESULTS_BUCKET" ] || ! command -v aws >/dev/null 2>&1; then
    return 0
  fi
  tar -czf "/tmp/forecast-results-${TASK_ID}-partial.tar.gz" -C "$RESULTS_DIR" . 2>/dev/null || return 0
  aws s3 cp "/tmp/forecast-results-${TASK_ID}-partial.tar.gz" \
    "s3://${RESULTS_BUCKET}/${TASK_ID}/forecast-results-partial.tar.gz" \
    --region "$AWS_REGION" 2>/dev/null || log "WARNING: partial results upload failed"
}

cleanup() {
  local exit_code=$?
  local current=""
  [ -f "${RESULTS_DIR}/STATUS" ] && current=$(cat "${RESULTS_DIR}/STATUS" 2>/dev/null || echo "")
  if [ "$exit_code" -ne 0 ] && [ "$current" != "COMPLETED" ]; then
    log "Pipeline failed (exit ${exit_code}); marking FAILED"
    upload_status "FAILED"
    upload_partial_results
  fi
}
trap cleanup EXIT

upload_status "RUNNING"
log "=== Tickonomics Forecast Task ${TASK_ID} ==="

# --- Phase 1: Install prerequisites ---
log "Phase 1: Installing Docker and AWS CLI..."
if ! command -v docker &>/dev/null; then
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg lsb-release awscli
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  systemctl start docker
  systemctl enable docker
else
  log "Docker already installed"
fi

# --- Phase 2: Mount persistent disk ---
log "Phase 2: Mounting persistent disk at /mnt/timescaledb..."
mkdir -p /mnt/timescaledb
if ! mountpoint -q /mnt/timescaledb; then
  DEVICE="${DATA_DEVICE:-}"
  if [ -z "$DEVICE" ]; then
    DEVICE=$(lsblk -dnpo NAME,MOUNTPOINT | awk '$2 == "" {print $1}' | head -1)
  fi
  if [ -n "$DEVICE" ] && [ -b "$DEVICE" ]; then
    if ! blkid "$DEVICE" >/dev/null 2>&1; then
      log "Formatting ${DEVICE} (first use)..."
      mkfs.ext4 -F "$DEVICE"
    fi
    mount "$DEVICE" /mnt/timescaledb
    log "Mounted ${DEVICE} at /mnt/timescaledb"
  else
    log "WARNING: no data device found; using existing /mnt/timescaledb contents"
  fi
else
  log "Disk already mounted at /mnt/timescaledb"
fi

# --- Phase 3: Login and pull images ---
log "Phase 3: Pulling container images..."
if [ -n "${REGISTRY_URL:-}" ]; then
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$REGISTRY_URL"
  docker compose -f "$COMPOSE_FILE" pull
else
  log "No REGISTRY_URL set; images must be pre-loaded"
fi

# --- Phase 4: Start services ---
log "Phase 4: Starting Docker Compose services..."
docker compose -f "$COMPOSE_FILE" up -d

# --- Phase 5: Wait for healthy ---
log "Phase 5: Waiting for services to become healthy..."
bash "${SCRIPTS_DIR}/health-check.sh" 600

# --- Phase 6: Wait for data ingestion ---
log "Phase 6: Waiting for data ingestion (min ${MIN_ROWS} rows, timeout ${INGESTION_TIMEOUT}s)..."
ELAPSED=0
ROW_COUNT=0
while [ "$ELAPSED" -lt "$INGESTION_TIMEOUT" ]; do
  ROW_COUNT=$(docker compose -f "$COMPOSE_FILE" exec -T timescaledb \
    psql -U tickonomics -t -c "SELECT COUNT(*) FROM rate_snapshots" 2>/dev/null | xargs || echo "0")
  log "  rate_snapshots rows: ${ROW_COUNT} (${ELAPSED}s/${INGESTION_TIMEOUT}s)"
  if [ "${ROW_COUNT:-0}" -ge "$MIN_ROWS" ] 2>/dev/null; then
    break
  fi
  sleep 15
  ELAPSED=$((ELAPSED + 15))
done

if [ "${ROW_COUNT:-0}" -lt "$MIN_ROWS" ] 2>/dev/null; then
  log "FATAL: only ${ROW_COUNT:-0} rows available (minimum: ${MIN_ROWS}). Aborting."
  exit 1
fi
log "Ingestion threshold reached: ${ROW_COUNT} rows"

# --- Phase 7: Trigger forecast computation ---
log "Phase 7: Triggering analytics forecast (backend @Scheduled jobs drive KPI/signal compute)..."
curl -sf -X POST "http://localhost:8001/api/v1/analytics/volatility-forecast" \
  --max-time 300 || log "WARNING: analytics volatility-forecast failed or unavailable"

log "Settling ${COMPUTE_SETTLE_SECONDS}s for scheduled KPI/signal computation..."
sleep "$COMPUTE_SETTLE_SECONDS"

# --- Phase 8: Collect results ---
log "Phase 8: Collecting results..."
bash "${SCRIPTS_DIR}/collect-results.sh" "$TASK_ID"

# Mark COMPLETED before shutdown so a later shutdown/self-terminate failure cannot
# flip a successful run to FAILED (the EXIT trap only writes FAILED if status != COMPLETED).
upload_status "COMPLETED"
log "=== Forecast Task ${TASK_ID} complete ==="

# --- Phase 9: Shutdown ---
log "Phase 9: Graceful shutdown..."
bash "${SCRIPTS_DIR}/shutdown.sh" || log "WARNING: graceful shutdown reported an error (results already uploaded)"

# --- Phase 10: Self-terminate ---
if [ "$AUTO_TERMINATE" = "true" ]; then
  log "Phase 10: Self-terminating spot instance..."
  if command -v aws >/dev/null 2>&1; then
    TOKEN=$(curl -sf http://169.254.169.254/latest/api/token \
      -X PUT -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || echo "")
    INSTANCE_ID=$(curl -sf -H "X-aws-ec2-metadata-token: ${TOKEN}" \
      http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null || echo "")
    if [ -n "$INSTANCE_ID" ]; then
      aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" \
        --region "$AWS_REGION" || log "WARNING: self-terminate failed"
    fi
  fi
fi
