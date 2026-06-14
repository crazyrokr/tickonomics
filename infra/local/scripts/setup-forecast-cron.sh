#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CRONTAB_SRC="${LOCAL_DIR}/crontab"
CRON_USER="${CRON_USER:-$(whoami)}"
DEST="/etc/cron.d/tickonomics-forecast"

if [ "$(id -u)" -ne 0 ]; then
  echo "[cron] This script writes to ${DEST}; re-run with sudo." >&2
  exit 1
fi

install -m 0644 "$CRONTAB_SRC" "$DEST"
# cron.d entries must specify a user and run with an environment that can find docker.
echo "[cron] Installed ${DEST} (runs as ${CRON_USER})"
echo "[cron] Edit the file to point PYTHONPATH/PATH and user at your installation."
