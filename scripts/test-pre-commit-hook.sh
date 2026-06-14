#!/usr/bin/env bash
# Given-When-Then tests for the pre-commit workflow-lint hook.
# Each scenario spins up a throwaway git repo with the hook installed and asserts
# whether a commit is allowed or blocked.
set -euo pipefail

if ! command -v actionlint >/dev/null 2>&1; then
	echo "SKIP: actionlint not installed" >&2
	exit 0
fi

root=$(git rev-parse --show-toplevel)
hook="$root/scripts/git-hooks/pre-commit"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

new_repo() {
	rm -rf "$work/repo"
	git init -q "$work/repo"
	git -C "$work/repo" config user.email test@example.test
	git -C "$work/repo" config user.name test
	mkdir -p "$work/repo/.git/hooks"
	cp "$hook" "$work/repo/.git/hooks/pre-commit"
	chmod +x "$work/repo/.git/hooks/pre-commit"
}

mkfile() {
	mkdir -p "$work/repo/$(dirname "$2")"
	printf '%s\n' "$1" > "$work/repo/$2"
}

commit_status() {
	git -C "$work/repo" commit -m t >/dev/null 2>&1 && echo ok || echo blocked
}

valid='on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: echo hi'

invalid='on: [push]
jobs:
  build:
    steps:
      - run: echo hi'

# Structurally valid, but shellcheck flags the unquoted $HOME (SC2086). The hook
# ignores SC* advisories, so this must still be allowed.
shellcheck_only='on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: echo $HOME'

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

# Given no workflow file is staged, When a commit is attempted,
# Then it is allowed (hook is a no-op).
new_repo
mkfile "x" "README.md"
git -C "$work/repo" add README.md
check "no-workflow-staged allows commit" "$(commit_status)" ok

# Given a valid workflow file is staged, When a commit is attempted,
# Then it is allowed.
new_repo
mkfile "$valid" ".github/workflows/valid.yml"
git -C "$work/repo" add .github/workflows/valid.yml
check "valid-workflow allowed" "$(commit_status)" ok

# Given an invalid workflow file is staged (missing runs-on),
# When a commit is attempted, Then it is blocked.
new_repo
mkfile "$invalid" ".github/workflows/bad.yml"
git -C "$work/repo" add .github/workflows/bad.yml
check "invalid-workflow blocked" "$(commit_status)" blocked

# Given invalid YAML outside .github/workflows is staged, When a commit is
# attempted, Then it is allowed (false-positive guard: non-workflows are not linted).
new_repo
mkfile "$invalid" "config/notaworkflow.yml"
git -C "$work/repo" add config/notaworkflow.yml
check "non-workflow-yaml not linted" "$(commit_status)" ok

# Given a workflow whose only finding is a shellcheck advisory (SC2086), When a
# commit is attempted, Then it is allowed (shellcheck is advisory, not blocking).
new_repo
mkfile "$shellcheck_only" ".github/workflows/shellcheck.yml"
git -C "$work/repo" add .github/workflows/shellcheck.yml
check "shellcheck-only-finding allowed" "$(commit_status)" ok

echo "---"
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
