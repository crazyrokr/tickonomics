#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_ROOT="$PROJECT_ROOT/infra/terraform"
TFLINT_CONFIG="$PROJECT_ROOT/.tflint.hcl"
ENVIRONMENTS=(aws gcp azure)
ERRORS=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_ok()   { echo -e "${GREEN}[PASS]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; ERRORS=$((ERRORS + 1)); }

echo "========================================"
echo " Terraform Validation Suite"
echo "========================================"
echo ""

# --- Check 1: Format ---
echo "--- terraform fmt ---"
if terraform fmt -check -recursive "$TF_ROOT" 2>/dev/null; then
  log_ok "All files formatted correctly"
else
  log_fail "Formatting issues found. Run: terraform fmt -recursive $TF_ROOT"
fi
echo ""

# --- Check 2: Module path resolution ---
echo "--- Module source paths ---"
while IFS= read -r line; do
  dir=$(echo "$line" | cut -d: -f1 | xargs dirname)
  src=$(echo "$line" | grep -oP 'source = "\K[^"]+')
  target="$dir/$src"
  if [ ! -d "$target" ]; then
    log_fail "BROKEN: $target (from $line)"
  else
    log_ok "Resolved: $src"
  fi
done < <(grep -rn 'source = "\.\./\.\./modules/' "$TF_ROOT/environments/"*/main.tf 2>/dev/null || true)
echo ""

# --- Check 3: Init + Validate per environment ---
for env in "${ENVIRONMENTS[@]}"; do
  echo "--- terraform validate: $env ---"
  ENV_DIR="$TF_ROOT/environments/$env"

  rm -rf "$ENV_DIR/.terraform" "$ENV_DIR/.terraform.lock.hcl" 2>/dev/null

  if terraform -chdir="$ENV_DIR" init -backend=false -input=false > /dev/null 2>&1; then
    if terraform -chdir="$ENV_DIR" validate > /dev/null 2>&1; then
      log_ok "$env: validate passed"
    else
      log_fail "$env: validate failed"
      terraform -chdir="$ENV_DIR" validate 2>&1 | sed 's/^/  /'
    fi
  else
    log_fail "$env: init failed"
    terraform -chdir="$ENV_DIR" init -backend=false -input=false 2>&1 | tail -5 | sed 's/^/  /'
  fi
  echo ""
done

# --- Check 4: tflint ---
echo "--- tflint ---"
for env in "${ENVIRONMENTS[@]}"; do
  ENV_DIR="$TF_ROOT/environments/$env"
  if tflint --chdir="$ENV_DIR" -c "$TFLINT_CONFIG" > /dev/null 2>&1; then
    log_ok "$env: tflint passed"
  else
    log_fail "$env: tflint issues found"
    tflint --chdir="$ENV_DIR" -c "$TFLINT_CONFIG" 2>&1 | sed 's/^/  /'
  fi
done
echo ""

# --- Summary ---
echo "========================================"
if [ "$ERRORS" -eq 0 ]; then
  log_ok "All checks passed"
else
  log_fail "$ERRORS check(s) failed"
fi
echo "========================================"

exit "$ERRORS"
