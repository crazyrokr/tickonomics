#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESULTS_DIR="${RESULTS_DIR:-${LOCAL_DIR}/forecast-results}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

if [ ! -d "$RESULTS_DIR" ]; then
  echo "[backup] Results directory ${RESULTS_DIR} does not exist; nothing to clean"
  exit 0
fi

echo "[backup] Pruning forecast results older than ${RETENTION_DAYS} days"
find "$RESULTS_DIR" -name "forecast-*.tar.gz" -mtime +"$RETENTION_DAYS" -delete
find "$RESULTS_DIR" -name "STATUS" -mtime +"$RETENTION_DAYS" -exec rm -f {} \;

echo "[backup] Done"
