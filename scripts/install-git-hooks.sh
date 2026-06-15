#!/usr/bin/env bash
# Install the version-controlled git hooks from scripts/git-hooks into .git/hooks.
# Re-run after pulling changes to the hook sources.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
src="$root/scripts/git-hooks"
dst="$root/.git/hooks"

mkdir -p "$dst"
for hook in pre-commit pre-push; do
	cp "$src/$hook" "$dst/$hook"
	chmod +x "$dst/$hook"
	echo "installed $dst/$hook"
done

cat <<'EOF'

Hooks installed.
  pre-commit: actionlint on staged workflows + npm run lint on staged frontend apps (always on).
  pre-push:   act dry-run of gate workflows (opt-in).

Enable the pre-push dry-run with one of:
  export WORKFLOW_DRYRUN_ON_PUSH=1
  git config --bool workflow.dryrunOnPush true
EOF
