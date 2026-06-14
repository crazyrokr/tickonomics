#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/opt/tickonomics/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
COMPOSE_FILE="${COMPOSE_FILE:-${LOCAL_DIR}/docker-compose.local.yml}"

mkdir -p "${BACKUP_DIR}"

echo "[backup] Starting pg_dump at ${TIMESTAMP}"

# Logical backup via pg_dump (custom format)
docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
  pg_dump -U tickonomics --format=custom \
  > "${BACKUP_DIR}/tickonomics_${TIMESTAMP}.dump"

# Compress
gzip -f "${BACKUP_DIR}/tickonomics_${TIMESTAMP}.dump"
echo "[backup] Created: ${BACKUP_DIR}/tickonomics_${TIMESTAMP}.dump.gz"

# Optional: rsync to external drive / NAS / remote server
if [ -n "${RSYNC_TARGET:-}" ]; then
  echo "[backup] Syncing to ${RSYNC_TARGET}"
  rsync -az --delete "${BACKUP_DIR}/" "${RSYNC_TARGET}"
fi

# Optional: sync to S3 for hybrid deployment
if [ "${S3_BACKUP:-false}" = "true" ] && [ -n "${S3_BUCKET:-}" ]; then
  echo "[backup] Syncing to s3://${S3_BUCKET}/backups/"
  aws s3 sync "${BACKUP_DIR}/" "s3://${S3_BUCKET}/backups/" --delete
fi

# Clean old backups
DELETED=$(find "${BACKUP_DIR}" -name "tickonomics_*.dump.gz" -mtime "+${RETENTION_DAYS}" -delete -print | wc -l)
echo "[backup] Cleaned ${DELETED} old backup(s) (retention: ${RETENTION_DAYS} days)"
echo "[backup] Done"
