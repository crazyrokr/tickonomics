#!/usr/bin/env bash
# Given-When-Then guard: the act dry-run gate workflows must declare no
# `services:` blocks. act 0.2.89 panics (nil-pointer dereference in
# containerReference.GetHealth) when `act -n` resolves a job that carries a
# service container, because the dry-run code path has no Docker client. The
# upstream fix (nektos/act PR #5782) is unmerged, so service-bearing workflows
# are kept out of the dry-run gate. See ADR-026 and ADR-027.
#
# The gate list below must match `gateWorkflows` in build.gradle and
# `GATE_WORKFLOWS` in the Makefile.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
gate_workflows=(
  "$root/.github/workflows/pr-checks.yml"
  "$root/.github/workflows/benchmark.yml"
)

has_services() {
  # actionlint guarantees `services` only appears as the jobs.<id>.services
  # container map, so any indented `services:` key is a service-container block.
  grep -qE '^[[:space:]]+services:' "$1"
}

pass=0
fail=0
check() {
  local name=$1 got=$2 want=$3
  if [ "$got" = "$want" ]; then
    echo "PASS: $name"
    pass=$((pass + 1))
  else
    echo "FAIL: $name (got=$got want=$want)"
    fail=$((fail + 1))
  fi
}

# Given a gate workflow file, When scanned for a services block,
# Then none is present (the gate stays act-dry-run-safe).
for wf in "${gate_workflows[@]}"; do
  if [ ! -f "$wf" ]; then
    echo "FAIL: gate workflow missing: $wf" >&2
    fail=$((fail + 1))
    continue
  fi
  if has_services "$wf"; then
    check "$(basename "$wf") service-free" present absent
  else
    check "$(basename "$wf") service-free" absent absent
  fi
done

# Given a throwaway workflow carrying a services block, When scanned,
# Then it is detected (proves the guard actually flags services, not a no-op).
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat >"$work/with-services.yml" <<'YAML'
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    services:
      db:
        image: postgres
YAML
if has_services "$work/with-services.yml"; then
  check "detector flags a services block" present present
else
  check "detector flags a services block" absent present
fi

echo "---"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
