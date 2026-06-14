# ADR-025: Update the Benchmark PR Comment In Place Across Runs

**Date:** 2026-06-14
**Status:** Accepted
**Related:** ADR-016 (CI/CD Pipeline Finalization), ADR-024 (Benchmark Baseline Bootstrap)

## Context

The `Comment on PR` step in `benchmark.yml` posts the benchmark regression
report as a **new** PR comment on every run:

```sh
gh pr comment ${{ github.event.pull_request.number }} --body-file benchmark-report.md
```

The `benchmark` job triggers on every push to a `computation/**` / `analytics/**`
PR, so an active PR accumulates one comment per commit. The report clutters the
conversation and pushes reviewers' earlier discussion off-screen, while each new
comment is a near-identical refresh of the previous one.

## Decision

Make the report comment idempotent: find the bot's prior report comment on the PR
and **edit it in place**, only creating a new comment when none exists yet.

Two changes in `benchmark.yml`:

1. The `Publish benchmark report` step now stamps the report with a hidden HTML
   marker as its first line:

   ```sh
   echo "<!-- benchmark-report -->"
   echo "## Benchmark Regression Report"
   ```

   HTML comments are not rendered by GitHub Markdown, so the marker is invisible
   to readers while remaining a reliable, machine-matchable substring.

2. The `Comment on PR` step queries the PR's comments for the marker (scoped to
   `github-actions[bot]`, which is the author `GITHUB_TOKEN` posts as) and edits
   the most recent match; otherwise it creates a new comment:

   ```sh
   comment_id=$(gh api "repos/${{ github.repository }}/issues/${PR_NUMBER}/comments" \
     --paginate \
     --jq '.[] | select(.user.login == "github-actions[bot]") | select(.body | contains("<!-- benchmark-report -->")) | .id' \
     | tail -n 1)

   if [ -n "$comment_id" ]; then
     jq -n --rawfile body benchmark-report.md '{body: $body}' > "$RUNNER_TEMP/benchmark-comment.json"
     gh api -X PATCH "repos/${{ github.repository }}/issues/comments/${comment_id}" \
       --input "$RUNNER_TEMP/benchmark-comment.json"
   else
     gh pr comment "$PR_NUMBER" --body-file benchmark-report.md
   fi
   ```

   `tail -n 1` selects the newest matching comment (the API returns comments
   oldest-first), and `--paginate` covers PRs with more than one page of
   comments. The update uses `gh api` against the `issues/comments/{id}` endpoint
   rather than `gh pr comment`: the runner-shipped `gh` supports `--edit-last` /
   `--delete-last` on `gh pr comment` but **not** `--edit <id>`, so an in-place
   edit by comment ID must go through the REST API. `jq --rawfile` builds the
   `{"body": "<report>"}` JSON payload with full escaping, and `--input` sends
   it verbatim, sidestepping the `-f`/`-F` field-flag file syntax.

The implementation uses the already-available `gh` CLI (no third-party Action),
matching the step's existing approach.

## Consequences

- Each PR keeps at most one benchmark-report comment, updated in place on every
  run. Reviewers see the latest report without scrolling past stale copies.
- The hidden marker couples "this is the benchmark report" to a stable substring.
  Any future change to the report body that removes the marker would silently
  revert to creating new comments; the marker is kept on its own line to make
  that dependency visible.
- The runner-shipped `gh` exposes `--edit-last`/`--delete-last` on `gh pr comment`
  but not `--edit <id>`, so the in-place update goes through `gh api` PATCH on
  `issues/comments/{id}`. This depends only on the stable GitHub REST API, not on
  a `gh` subcommand flag.
- Behavioral change is scoped to the `Comment on PR` CI step; the benchmark
  tests, regression gate, and report contents are unchanged.

## Alternatives considered

- **`gh pr comment --edit-last --create-if-none`.** Concise and built into the
  runner's `gh`, but edits the bot's last comment regardless of content, so any
  other `github-actions[bot]` PR comment posted after the report would be
  overwritten. Today only `benchmark.yml` posts a bot PR comment (`demo-report.yml`
  opens issues, not comments), but the marker-based lookup is precise and stays
  correct if that changes.
- **`peter-evans/find-comment` + `create-or-update-comment` Actions.** Popular
  and well-tested, but adds third-party Action dependencies the workflow does not
  otherwise use; the `gh`-native lookup achieves the same result with no new
  supply-chain surface.
