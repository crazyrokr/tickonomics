# ADR-030: Same-Runner Benchmark A/B to Eliminate Cross-Runner False Positives

**Date:** 2026-06-15
**Status:** Accepted
**Supersedes/extends:** ADR-024 (Benchmark Baseline Bootstrap)
**Related:** ADR-016 (CI/CD Pipeline Finalization), ADR-025 (Benchmark PR Comment Dedup)

## Context

A Dependabot PR bumping a **Java** dependency
(`org.apache.arrow:arrow-memory-unsafe` 18.3.0 → 19.0.0, touching only
`computation/build.gradle`) was flagged for a **Python** benchmark regression:

```
Performance has regressed:
    test_fama_french_capm - Field 'mean' has failed PercentageRegressionCheck: 77.18 > 10.00
```

The branch cannot affect that test — `test_fama_french_capm` is a NumPy
least-squares regression in `analytics/`, and the JVM Arrow allocator it bumps is
not referenced on the Python side. The regression was a **false positive**, and
pytest-benchmark diagnosed it in its own first output line:

```
PytestBenchmarkWarning: Benchmark machine_info is different.
```

The ADR-024 gate compared the candidate run against a cached baseline under the
Actions cache key `benchmark-baseline-${{ runner.os }}` (just `Linux`) on an
**unpinned** `runs-on: ubuntu-latest` job. GitHub rotates that pool across Azure
VM families, so the two runs this gate joined landed on different hardware:

| | Baseline (`0001`) | Candidate (`0002`) |
| --- | --- | --- |
| CPU | AMD EPYC 9V74 (Zen 4 / Genoa) | AMD EPYC 7763 (Zen 3 / Milan) |
| Clock | 3.69 GHz | 2.45 GHz (~34% slower) |
| SIMD | AVX-512 | no AVX-512 |
| L2 / L3 | 2 MB / 1 MB | 1 MB / 512 KB |

`test_fama_french_capm`'s hot path is dense linear algebra routed to OpenBLAS
(`np.linalg.lstsq`, `np.linalg.inv`, matrix products in
`performance_service.py`). On Genoa OpenBLAS uses AVX-512 (8 doubles/vector); on
Milan it falls back to AVX-2 (4 doubles/vector) on top of the lower clock and
half the cache — fully accounting for the observed +77% mean. The `max` time
moving the *other* way (651 μs → 432 μs) while `min` rose 1.74× is the tell-tale
of different hardware, not slower code.

Root cause: the ADR-024 design assumes runner homogeneity across cached runs,
which `ubuntu-latest` does not provide.

## Decision

Run the baseline and the candidate **in the same job on the same runner**, and
gate the comparison with a small dedicated comparator instead of
pytest-benchmark's cached-baseline `--benchmark-compare`/`--benchmark-compare-fail`.

### `benchmark.yml` — same-runner A/B

The `benchmark` job now checks out both refs into sibling directories and runs
pytest-benchmark in each:

1. Resolve refs: on `pull_request`, `base = github.event.pull_request.base.sha`
   and `head = github.event.pull_request.head.sha`; on `workflow_dispatch`,
   both collapse to `GITHUB_SHA` (no comparison).
2. `actions/checkout@v4` the head into `head/` and, for PRs, the base into
   `base/`.
3. Set up Python once and install once from `head/analytics/requirements.txt`
   into a single shared environment — both runs execute against the identical
   NumPy/OpenBLAS install, so the only varying input is the **source code** under
   test.
4. Run `pytest tests --benchmark-only --benchmark-storage=.benchmarks
   --benchmark-save=head` in `head/analytics`, and the same with
   `--benchmark-save=base` in `base/analytics`.
5. Compare the two saved JSONs with `compare_benchmarks.py` (below).

Because both runs share one runner and one Python install, the comparison is
machine-equivalent by construction — the cross-generation false positive cannot
recur. The cross-run `benchmark-baseline-${{ runner.os }}` cache and the
`--benchmark-compare` / `--benchmark-compare-fail` flags are removed; the job no
longer depends on a persisted baseline for its PR path.

### `analytics/compare_benchmarks.py` — the gate

A stdlib-only CLI (fulfilling the `compare_benchmarks.py` deferred in ADR-016)
that reads two pytest-benchmark result JSONs, matches benchmarks by name, and
computes the percentage change of the candidate versus the baseline on a chosen
metric (default `mean`, threshold default 10%):

```
python compare_benchmarks.py BASE.json HEAD.json [--threshold 10] [--metric mean] [--format markdown]
```

Exit codes: `0` = no regression, `1` = a benchmark exceeded `+threshold%`, `2` =
usage / IO / parse error. Regression is strictly `delta > threshold` (matching
pytest-benchmark's `PercentageRegressionCheck`), so a delta exactly at the
threshold does not fire. Unmatched benchmarks, missing values, and a zero
baseline are reported but never counted as regressions. The report prints both
runs' `machine_info.cpu.brand_raw` and a `same runner` flag as an audit trail —
if a future change reintroduces cross-runner comparison, the flag exposes it
immediately. The comparison logic is a pure `evaluate()` function unit-tested
directly (Given-When-Then, with explicit false-positive edge cases); `main()`
handles only argument parsing and IO.

The script lives at `analytics/compare_benchmarks.py` (repository root of the
analytics package), diverging from the `analytics/scripts/` path sketched in the
plan documents — there is no existing `scripts/` directory, and a top-level tool
matches how the workflow invokes it.

### Advisory posture retained

Consistent with ADR-016/024, the comparison step runs with
`continue-on-error: true` and the verdict is posted as the in-place PR comment
from ADR-025. The job does not hard-fail on a regression; the comment states
`REGRESSION` or `NO REGRESSION` for human review. Promoting to a blocking gate
is a deliberate future step once the same-runner comparison has a track record.

### Non-PR trigger

On `workflow_dispatch` there is no base ref, so the base checkout, base
benchmark, and comparison steps are skipped; the head benchmark runs and its JSON
is recorded. This keeps manual runs useful for inspecting current performance
without a spurious "no baseline" error.

## Consequences

- The cross-runner false positive is eliminated for the PR path: base and head
  are benchmarked on one runner, so a delta reflects a code change, not a VM
  family rotation.
- The `benchmark-baseline-${{ runner.os }}` Actions cache is removed. The PR
  comparison no longer depends on a persisted baseline; each run is self-contained.
- The gate's authority moves from pytest-benchmark's storage-based compare to
  `compare_benchmarks.py`, removing the `--benchmark-compare-fail` storage-quirk
  class of bugs (the hard `UsageError` on a missing baseline that ADR-024 worked
  around, and the cross-`machine_info` comparison that caused this incident).
- `scripts/validate-cicd.py` `assert_benchmark_workflow` is updated to require
  `compare_benchmarks` and a `github.event.pull_request.base.sha` checkout,
  replacing the prior `--benchmark-compare` assertion.
- The advisory posture is unchanged; a regression surfaces as a PR comment, not a
  failed job.
- The shared-environment assumption means a PR that changes `analytics/requirements.txt`
  (e.g. bumping NumPy) compares old code on the new NumPy against new code on the
  new NumPy — a dependency confound. This is acceptable for an advisory gate and
  for the dependency-bump PRs (like the Arrow case) that do not touch Python deps.
- `chaos-benchmark.yml`'s `benchmark` job is **out of scope**: it triggers on
  `schedule` + `workflow_dispatch`, not `pull_request`, so there is no PR base to
  A/B. Its fix is a different design (compare a scheduled run against a committed
  baseline) and is left as a follow-up; its advisory artifact-upload means a
  cross-runner false positive there is non-blocking.

## Alternatives considered

- **Machine-aware baseline cache key.** Key the cache on CPU brand/family instead
  of `runner.os`, or skip comparison when pytest-benchmark reports mismatched
  `machine_info`. Keeps the cached-baseline design but only compares like-for-like.
  Rejected as the primary fix: it preserves the fragile cross-run dependency and
  still silently skips comparison whenever GitHub rotates the runner, leaving the
  gate inactive precisely when it should guard merges. Same-runner A/B makes the
  comparison valid unconditionally.
- **Pin a stable runner class** (e.g. a `*-large` runner, which sees less family
  churn). Reduces the variance but does not eliminate it — GitHub does not
  guarantee a single CPU generation even within a named runner class, and it
  would not have validated the comparison logic itself.
- **Loosen the 10% threshold** to absorb the ~70% cross-generation swing.
  Rejected: it silences the symptom while lowering the gate's sensitivity to real
  regressions, and leaves the underlying cross-runner comparison intact.
- **Check in a baseline JSON and pin `--benchmark-compare=<file>`.** Considered
  and rejected in ADR-024 as churn-prone committed binary-ish JSON with a manual
  update step; still rejected here for the same reason, and because it would
  re-introduce a cross-runner comparison (the checked-in baseline was recorded on
  one specific CPU).
