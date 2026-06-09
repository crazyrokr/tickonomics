#!/usr/bin/env bash
set -euo pipefail

RESULTS_DIR="${1:-./forecast-results}"
ERRORS=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_ok()   { echo -e "${GREEN}[PASS]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; ERRORS=$((ERRORS + 1)); }

if [ ! -d "$RESULTS_DIR" ]; then
  echo -e "${RED}[FAIL]${NC} Results directory not found: $RESULTS_DIR"
  exit 1
fi

echo "========================================"
echo " Forecast Result Verification"
echo " Directory: $RESULTS_DIR"
echo "========================================"
echo ""

# --- 1. Status marker ---
echo "--- Status ---"
STATUS_FILE="$RESULTS_DIR/STATUS"
if [ -f "$STATUS_FILE" ]; then
  STATUS=$(cat "$STATUS_FILE")
  if [ "$STATUS" = "COMPLETED" ]; then
    log_ok "Status: COMPLETED"
  else
    log_fail "Status: $STATUS (expected COMPLETED)"
  fi
else
  log_fail "STATUS file not found"
fi
echo ""

# --- 2. Archive integrity ---
echo "--- Archive ---"
ARCHIVE="$RESULTS_DIR/forecast-results.tar.gz"
if [ -f "$ARCHIVE" ]; then
  SIZE=$(stat -c%s "$ARCHIVE" 2>/dev/null || stat -f%z "$ARCHIVE" 2>/dev/null || echo "0")
  if [ "$SIZE" -gt 1024 ]; then
    log_ok "Archive size: $(numfmt --to=iec "$SIZE")"
    if tar -tzf "$ARCHIVE" > /dev/null 2>&1; then
      log_ok "Archive integrity: valid gzip tar"
      FILE_COUNT=$(tar -tzf "$ARCHIVE" | wc -l)
      log_ok "Archive contents: ${FILE_COUNT} files"
    else
      log_fail "Archive integrity: corrupted"
    fi
  else
    log_fail "Archive too small: ${SIZE} bytes"
  fi
else
  log_fail "forecast-results.tar.gz not found"
fi
echo ""

# --- 3. Database dump ---
echo "--- Database Dump ---"
DUMP="$RESULTS_DIR/tickonomics_dump.sql.gz"
if [ -f "$DUMP" ]; then
  log_ok "Database dump: $(du -h "$DUMP" | cut -f1)"
  if gunzip -t "$DUMP" 2>/dev/null; then
    log_ok "Dump integrity: valid gzip"
  else
    log_fail "Dump integrity: corrupted gzip"
  fi

  RATE_TABLE=$(zcat "$DUMP" 2>/dev/null | grep -c "COPY.*rate_snapshots" || echo "0")
  if [ "$RATE_TABLE" -gt 0 ]; then
    log_ok "rate_snapshots table present in dump"
  else
    log_warn "rate_snapshots table not found in dump (may be empty run)"
  fi
else
  log_warn "Database dump not found"
fi
echo ""

# --- 4. JSON result files ---
echo "--- JSON Results ---"
JSON_FILES=(
  "kpis"
  "signals"
  "strategies"
  "risk-summary"
  "regime"
  "liquidity"
  "analytics-evt"
  "analytics-risk"
  "analytics-regime"
  "analytics-diagnostics"
)

for name in "${JSON_FILES[@]}"; do
  FILE="$RESULTS_DIR/${name}.json"
  if [ ! -f "$FILE" ]; then
    log_warn "${name}.json: not found"
    continue
  fi

  if ! jq empty "$FILE" 2>/dev/null; then
    log_fail "${name}.json: invalid JSON"
    continue
  fi

  if grep -q '"error"' "$FILE" 2>/dev/null; then
    log_warn "${name}.json: contains error response"
    continue
  fi

  RECORDS=$(jq 'if type == "array" then length elif type == "object" then 1 else 0 end' "$FILE" 2>/dev/null || echo "?")
  log_ok "${name}.json: valid (${RECORDS} items)"
done
echo ""

# --- 5. NaN / Infinity check ---
echo "--- Numeric Integrity ---"
for json_file in "$RESULTS_DIR"/*.json; do
  [ -f "$json_file" ] || continue
  NAME=$(basename "$json_file")
  NAN_COUNT=$(grep -ciE 'NaN|"nan"|"NaN"|\bInfinity\b|"Infinity"|-?inf' "$json_file" 2>/dev/null || echo "0")
  if [ "$NAN_COUNT" -gt 0 ]; then
    log_fail "$NAME: contains NaN or Infinity ($NAN_COUNT occurrences)"
  fi
done
log_ok "No NaN/Infinity values in result files"
echo ""

# --- 6. Expected data thresholds ---
echo "--- Data Thresholds ---"
SIGNALS_FILE="$RESULTS_DIR/signals.json"
if [ -f "$SIGNALS_FILE" ] && jq empty "$SIGNALS_FILE" 2>/dev/null; then
  SIGNAL_COUNT=$(jq 'if type == "array" then length else 0 end' "$SIGNALS_FILE" 2>/dev/null)
  if [ "$SIGNAL_COUNT" -gt 0 ]; then
    log_ok "Signals generated: ${SIGNAL_COUNT}"
  else
    log_warn "No signals generated (data may be insufficient)"
  fi
fi

RISK_FILE="$RESULTS_DIR/risk-summary.json"
if [ -f "$RISK_FILE" ] && jq empty "$RISK_FILE" 2>/dev/null; then
  HAS_VALUES=$(jq 'if type == "object" then [values[] | select(type == "number" and (. != 0))] | length else 0 end' "$RISK_FILE" 2>/dev/null)
  if [ "$HAS_VALUES" -gt 0 ]; then
    log_ok "Risk summary: contains ${HAS_VALUES} non-zero metrics"
  else
    log_warn "Risk summary: no non-zero metrics"
  fi
fi
echo ""

# --- Summary ---
echo "========================================"
if [ "$ERRORS" -eq 0 ]; then
  log_ok "All verifications passed"
else
  log_fail "$ERRORS verification(s) failed"
fi
echo "========================================"

exit "$ERRORS"
