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
SCRIPTS_DIR="${FORECAST_DIR}/scripts"
TASK_ID="${TASK_ID:-$(date +%Y%m%d-%H%M%S)}"
AUTO_TERMINATE="${AUTO_TERMINATE:-true}"
INGESTION_TIMEOUT=600

log() { echo "[$(date '+%H:%M:%S')] $*"; }

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

DEVICE=$(lsblk -dnpo NAME,SIZE | sort -k2 -h | tail -1 | awk '{print $1}')
if [ -n "$DEVICE" ] && ! mountpoint -q /mnt/timescaledb; then
  if ! blkid "$DEVICE" &>/dev/null; then
    log "Formatting ${DEVICE} (first use)..."
    mkfs.ext4 -F "$DEVICE"
  fi
  mount "$DEVICE" /mnt/timescaledb
  log "Mounted ${DEVICE} at /mnt/timescaledb"
else
  log "Disk already mounted or no device found"
fi

# --- Phase 3: Login and pull images ---
log "Phase 3: Pulling container images..."
if [ -n "${REGISTRY_URL:-}" ]; then
  aws ecr get-login-password --region "${AWS_REGION:-us-east-1}" \
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
log "Phase 6: Waiting for data ingestion (timeout ${INGESTION_TIMEOUT}s)..."
ELAPSED=0
while [ "$ELAPSED" -lt "$INGESTION_TIMEOUT" ]; do
  ROW_COUNT=$(docker exec forecast-timescaledb-1 psql -U tickonomics -t -c \
    "SELECT COUNT(*) FROM rate_snapshots" 2>/dev/null | xargs || echo "0")
  log "  rate_snapshots rows: ${ROW_COUNT} (${ELAPSED}s/${INGESTION_TIMEOUT}s)"
  if [ "$ROW_COUNT" -ge 50 ] 2>/dev/null; then
    log "Ingestion threshold reached: ${ROW_COUNT} rows"
    break
  fi
  sleep 15
  ELAPSED=$((ELAPSED + 15))
done

if [ "$ELAPSED" -ge "$INGESTION_TIMEOUT" ]; then
  log "WARNING: Ingestion timeout reached, proceeding with available data"
fi

# --- Phase 7: Trigger forecast pipeline ---
log "Phase 7: Triggering KPI and signal computation..."
curl -sf -X POST http://localhost:8080/api/v1/kpis/compute --max-time 300 || log "KPI compute failed or not available"
curl -sf -X POST http://localhost:8080/api/v1/signals/generate --max-time 300 || log "Signal generation failed or not available"

log "Triggering analytics forecast..."
curl -sf -X POST http://localhost:8001/api/v1/analytics/forecast --max-time 300 || log "Analytics forecast failed or not available"

# --- Phase 8: Collect results ---
log "Phase 8: Collecting results..."
bash "${SCRIPTS_DIR}/collect-results.sh" "$TASK_ID"

# --- Phase 9: Shutdown ---
log "Phase 9: Graceful shutdown..."
bash "${SCRIPTS_DIR}/shutdown.sh"

# --- Phase 10: Self-terminate ---
if [ "$AUTO_TERMINATE" = "true" ]; then
  log "Phase 10: Self-terminating spot instance..."
  if command -v aws &>/dev/null; then
    TOKEN=$(curl -sf http://169.254.169.254/latest/api/token -X PUT -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
    INSTANCE_ID=$(curl -sf -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
    aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" --region "${AWS_REGION:-us-east-1}" || true
  fi
fi

log "=== Forecast Task ${TASK_ID} complete ==="
