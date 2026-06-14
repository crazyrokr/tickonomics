#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CRONTAB_SRC="${LOCAL_DIR}/crontab"
CRON_USER="${CRON_USER:-$(whoami)}"
DEST="/etc/cron.d/tickonomics"

if [ "$(id -u)" -ne 0 ]; then
  echo "[cron] This script writes to ${DEST}; re-run with sudo." >&2
  exit 1
fi

# Update paths in crontab to match actual installation directory
sed "s|/opt/tickonomics|${LOCAL_DIR}/../..|g" "${CRONTAB_SRC}" > /tmp/tickonomics-crontab
install -m 0644 /tmp/tickonomics-crontab "${DEST}"
rm -f /tmp/tickonomics-crontab

echo "[cron] Installed ${DEST} (runs as ${CRON_USER})"
echo "[cron] Verify: cat ${DEST}"
