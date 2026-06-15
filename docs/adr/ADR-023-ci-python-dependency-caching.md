# ADR-023: CI Python Dependency Caching

**Date:** 2026-06-14
**Status:** Accepted
**Supersedes/extends:** ADR-016 (CI/CD Pipeline Finalization)

## Context

ADR-016 wired `pytest-benchmark` regression into two workflows: the standalone
`benchmark.yml` and the `benchmark`/`chaos-test` jobs of `chaos-benchmark.yml`. Each run
re-executed a "install dependencies" step that did `pip install -r analytics/requirements.txt`
against `analytics/requirements.txt`, whose pinned set includes `torch==2.6.0` plus
`numpy`, `scipy`, `statsmodels`, and `pyarrow` — roughly 1.5–2 GB of wheels when unpacked.

The workflows already declared `actions/setup-python` with `cache: pip`, which caches only
the PyPI **download** cache (`~/.cache/pip`). That eliminates re-downloading the wheels but
still unpacks and installs them into site-packages on every run. For torch that unpack step
dominates, costing roughly 1–3 minutes per job and exposing the benchmark gate to transient
PyPI failures. Two dead weight items were also present: a redundant `pip install
pytest-benchmark` (already pinned in `requirements.txt`) and the now-unnecessary `cache: pip`
once the installed environment itself is cached.

## Decision

Cache the **entire installed virtual environment** rather than only the download cache, so a
cache hit skips `pip install` entirely. Applied identically to all three install sites:
`benchmark.yml` (the `benchmark` job) and both `chaos-benchmark.yml` jobs (`chaos-test` and
`benchmark`).

Each site:

1. Drops `cache: pip` / `cache-dependency-path` from `actions/setup-python` (redundant under
   whole-venv caching) and gives the step `id: setup-python`.
2. Adds an `actions/cache@v4` step (`id: venv-cache`) with
   `path: .venv` and the key
   `venv-${{ runner.os }}-py${{ steps.setup-python.outputs.python-version }}-${{ hashFiles('analytics/requirements.txt') }}`.
3. Gates the install step with `if: steps.venv-cache.outputs.cache-hit != 'true'`. On a miss
   it creates a virtualenv at `.venv`, upgrades pip, and installs the requirements.
4. Adds a `Put venv on PATH` step that appends `$GITHUB_WORKSPACE/.venv/bin` to `$GITHUB_PATH`,
   so downstream steps (including those using `working-directory: analytics`) resolve the
   venv's `python`/`pytest`.
5. Drops the redundant `pip install pytest-benchmark`.

### Why the key includes OS, full Python version, and requirements hash

- **OS** — wheels are platform-specific; the key would otherwise cross-contaminate runners.
- **Full Python version** (`steps.setup-python.outputs.python-version`, e.g. `3.12.10`) — the
  venv's `python` is a symlink into the runner's hosted-toolcache Python. Pinning only `3.12`
  in the key would let a patch bump (e.g. `3.12.10` → `3.12.11`) restore a venv whose symlinks
  point at a path that no longer exists. Including the resolved version forces a clean rebuild
  on any patch bump instead of a silent breakage.
- **`hashFiles('analytics/requirements.txt')`** — because every dependency is pinned (`==`),
  a stable requirements hash means a stable installed environment; any change invalidates
  correctly.

No `restore-keys` are used: a partial restore from a different requirements set could mix
package versions, so only an exact key match is accepted.

## Consequences

- **Speed.** On a cache hit the install step is skipped; net job speedup is roughly 1–2
  minutes per run (download + extract of the ~1.5–2 GB archive vs. a full torch install),
  and the common case (requirements unchanged) becomes cache-stable.
- **Flakiness.** With hits served from the GitHub Actions cache, the benchmark gate no longer
  depends on PyPI availability for its common path.
- **Cache budget.** The venv consumes ~1.5–2 GB of the repo's 10 GB Actions cache budget,
  shared with the Gradle/TA-Lib caches in `chaos-benchmark.yml` and the benchmark-baseline
  cache. This is within budget but is now the single largest consumer.
- **Cross-job reuse.** In `chaos-benchmark.yml` the `chaos-test` job runs first (the
  `benchmark` job declares `needs: chaos-test`) and populates the venv cache under the shared
  key; the later `benchmark` job restores it within the same run.
- **Cache-miss cost.** A requirements change (or a runner Python patch bump) triggers one
  fresh install. The download is no longer served by `cache: pip` on that miss; the trade is
  accepted because misses are infrequent and correctness > marginal miss latency.

## Alternatives considered

- **Keep only `cache: pip`.** Retained the per-run install/unpack cost (the bulk of the time
  for torch). Rejected as insufficient.
- **Cache system site-packages instead of a venv.** Less predictable across runner image
  updates to system Python; a virtualenv at a stable repo-root path gives an isolated,
  relocatable target with a clean cache path.
- **Pre-bake dependencies into a custom Docker image.** Would also subsume the Gradle and
  TA-Lib native build, but adds image build/publish infrastructure disproportionate to the
  current need; deferred.
