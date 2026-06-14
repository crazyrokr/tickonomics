WORKFLOWS := $(wildcard .github/workflows/*.yml)
GATE_WORKFLOWS := .github/workflows/pr-checks.yml .github/workflows/benchmark.yml
DEFAULT_GATE := .github/workflows/pr-checks.yml

.PHONY: workflow-lint workflow-list workflow-dryrun workflow-run install-hooks act-pull test-hooks

# Tier 1 (static, fast, no Docker): lint all workflow files with actionlint.
workflow-lint:
	actionlint $(WORKFLOWS)

# List the resolved action/job graph for every workflow (no Docker pull).
workflow-list:
	act -l

# Tier 2 (graph resolution, pulls runner image once): dry-run the PR-gate
# workflows for the pull_request event without executing any steps.
workflow-dryrun:
	@set -e; for wf in $(GATE_WORKFLOWS); do \
		[ -f "$$wf" ] || continue; \
		echo ">> act dry-run: $$wf"; \
		act -n pull_request -W "$$wf"; \
	done

# Run a single job locally, e.g. `make workflow-run JOB=python-test`.
# Optional WF= picks a different workflow file (defaults to pr-checks.yml).
workflow-run:
	@test -n "$(JOB)" || { echo "Usage: make workflow-run JOB=<job-id> [WF=path]"; exit 2; }
	act pull_request -W $(or $(WF),$(DEFAULT_GATE)) -j $(JOB)

install-hooks:
	scripts/install-git-hooks.sh

# Pre-pull the runner image so the first dry-run is not dominated by a download.
act-pull:
	docker pull catthehacker/ubuntu:act-22.04

test-hooks:
	scripts/test-pre-commit-hook.sh
