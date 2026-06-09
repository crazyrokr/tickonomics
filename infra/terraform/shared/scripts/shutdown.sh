#!/usr/bin/env bash
set -euo pipefail

echo "[shutdown] Initiating graceful shutdown..."

COMPOSE_FILE="/opt/tickonomics/docker-compose.forecast.yml"

if [ -f "$COMPOSE_FILE" ]; then
  echo "[shutdown] Stopping Docker Compose services..."
  docker compose -f "$COMPOSE_FILE" down --timeout 60 || true
fi

echo "[shutdown] Syncing filesystem..."
sync

echo "[shutdown] Unmounting persistent disk..."
if mountpoint -q /mnt/timescaledb; then
  umount /mnt/timescaledb || echo "[shutdown] umount failed (may already be unmounted)"
fi

echo "[shutdown] Shutdown complete"
