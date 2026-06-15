# ADR-024: Bootstrap Benchmark Regression Baseline When No Prior Save Exists

**Date:** 2026-06-14
**Status:** Accepted
**Related:** ADR-016 (CI/CD Pipeline Finalization), ADR-023 (CI Python Dependency Caching)

## Context

The `Performance Benchmark Regression` step in `benchmark.yml` (and the parallel
`Run benchmark regression` step in the `benchmark` job of `chaos-benchmark.yml`)
failed with:

```
pytest.UsageError: --benchmark-compare-fail requires valid --benchmark-compare.
```

Root cause, verified end-to-end against the `pytest_benchmark==5.2.3` source
(`session.py`):

- `--benchmark-compare` (no explicit file) loads the most recent saved run from
  storage via `list(self.storage.load())[-1:]`. When `analytics/.benchmarks`
  holds no baseline JSON — the first run on a branch, or any run after the
  `benchmark-baseline-${{ runner.os }}` Actions cache is evicted (LRU pressure
  from the ~1.5–2 GB venv cache documented in ADR-023) — that list is empty, so
  `compared_mapping` stays `{}`. `handle_loading` only logs a warning.
- `check_regressions` then sees `self.compare_fail` truthy (from
  `--benchmark-compare-fail=mean:10%`) and `compared_mapping` empty, and raises
  the hard `UsageError` above. Reproduced locally with an empty `.benchmarks`.

Because the step sets `continue-on-error: true`, the error did not fail the job
— but it ended up in `benchmark-output.txt` and was posted as the PR comment,
so the regression gate reported a traceback instead of a comparison. The gate
was therefore non-functional in exactly the cache-miss case it must tolerate.

## Decision

Gate the comparison flags on the presence of a saved baseline. In both workflows
the `Run benchmark regression` step now builds a `compare_args` variable and only
adds `--benchmark-compare --benchmark-compare-fail=mean:10%` when at least one
baseline JSON exists:

```sh
compare_args=""
if [ "$(find .benchmarks -name '*.json' 2>/dev/null | wc -l)" -gt 0 ]; then
  compare_args="--benchmark-compare --benchmark-compare-fail=mean:10%"
fi
python -m pytest tests \
  --benchmark-only \
  --benchmark-storage=.benchmarks \
  --benchmark-autosave \
  $compare_args ...
```

Effect:

- **No baseline (bootstrap):** comparison is skipped; `--benchmark-autosave`
  writes the first baseline into `.benchmarks` and the Actions cache stores it.
- **Baseline present:** both flags are supplied and `pytest-benchmark` compares
  against the loaded save, applying the `mean:10%` regression gate.

`--benchmark-autosave` and `--benchmark-storage=.benchmarks` are always passed,
so every run both compares (when possible) and refreshes the saved record. The
`find … | wc -l` check is depth-agnostic, matching `pytest-benchmark`'s
`.benchmarks/<machine_id>/<NNNN>_<name>.json` layout without coupling the gate
to a specific platform directory.

The advisory design (`continue-on-error: true` plus the PR-comment report) is
unchanged: a confirmed regression still surfaces as a PR comment rather than a
hard job failure, consistent with ADR-016's intent.

## Consequences

- The gate no longer errors on a cache miss or first run; it bootstraps cleanly
  and resumes comparison on the next run.
- The baseline cache key `benchmark-baseline-${{ runner.os }}` is fixed (no
  `restore-keys`), so under `actions/cache@v4` semantics the saved baseline is
  not overwritten once the key exists. This is the intended "compare against the
  established baseline" behavior, and the new bootstrap path makes the gate
  self-healing if that single entry is ever evicted.
- No behavioral change to the benchmark tests themselves (`analytics/tests/`
  `@pytest.mark.benchmark`); this is a CI-step-only change.

## Alternatives considered

- **Pass `--benchmark-compare` unconditionally and rely on the warning.**
  Rejected: `check_regressions` raises a hard error whenever `compare_fail` is
  set without a loadable baseline, which is precisely the reported failure.
- **Check in a baseline JSON and pin `--benchmark-compare=<file>`.** Would remove
  the cache dependency entirely but adds committed, churn-prone binary-ish JSON
  and a manual update step; disproportionate to an advisory gate. Deferred.
