#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/opt/tickonomics/backups}"
COMPOSE_FILE="${COMPOSE_FILE:-${LOCAL_DIR}/docker-compose.local.yml}"

LATEST=$(ls -t "${BACKUP_DIR}"/tickonomics_*.dump.gz 2>/dev/null | head -1)
if [ -z "${LATEST}" ]; then
  echo "ERROR: No backup found in ${BACKUP_DIR}"
  echo "Expected pattern: tickonomics_YYYYMMDD_HHMMSS.dump.gz"
  exit 1
fi

echo "WARNING: This will overwrite the current database."
echo "Restore from: ${LATEST}"
echo ""
read -rp "Type 'yes' to confirm: " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "Restore cancelled."
  exit 0
fi

echo "[restore] Restoring from ${LATEST}"

gunzip --stdout "${LATEST}" | \
  docker compose -f "${COMPOSE_FILE}" exec -T timescaledb \
  pg_restore -U tickonomics --clean --if-exists -d tickonomics

echo "[restore] Completed successfully."
