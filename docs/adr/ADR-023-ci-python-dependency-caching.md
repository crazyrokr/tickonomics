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

## Addendum (2026-10-18): closing the remaining CI cache gaps

The original decision was applied only to `benchmark.yml` and `chaos-benchmark.yml`. An audit
of all workflows found three remaining gaps, fixed in one change:

1. **`pr-checks.yml` `python-test`** still used `cache: pip` (download-only) and re-ran the
   full ~1.5–2 GB torch install on every PR. It now uses the **same `venv-*` key** as
   `chaos-benchmark.yml` (OS + resolved Python version + requirements hash), so the installed
   environment is shared across workflows and the install is skipped on a hit, exactly as
   decided above.
2. **`pr-checks.yml` `code-quality`** cached `~/.gradle/{caches,wrapper}` under its own
   `gradle-cq-*` key — a second, parallel dependency download per PR (and no
   `configuration-cache`). It now uses `gradle/actions/setup-gradle@v6` like
   `java-build-test`, so both jobs restore one shared dependency cache entry.
3. **`codeql.yml`** (runs on every PR; 3-language matrix) had no Java toolchain or Gradle
   cache at all. The `java-kotlin` matrix entry now sets up Temurin 25 plus
   `gradle/actions/setup-gradle@v6` before `autobuild`, so the CodeQL build restores cached
   dependencies instead of downloading from scratch.

**Consequence.** The venv cache is now the single shared Python environment across
`pr-checks`, `chaos-benchmark`, and `benchmark`; one entry, reused by three workflows. The
Gradle dependency cache in `pr-checks` is likewise one entry shared by three jobs
(`java-build-test`, `java-build-test-arm64` via per-arch native keys, `code-quality`),
plus the CodeQL job. Total cache budget usage is unchanged in kind (the `gradle-cq-*`
entry is gone, replaced by the existing setup-gradle entry).

## Addendum (2026-10-22): `code-quality` mavenLocal gap (TA-Lib FFM wrapper)

The audit above covered the *remote dependency* cache; `code-quality` still lacked the
mavenLocal setup the two build jobs carry. `:computation:compileClasspath` resolves
`com.tictactec.ta.lib:ta-lib-wrapper:0.7.0-20260817.2`, which exists only in `~/.m2`
(installed by `:computation:setupTalib` from the git-tag-pinned clone; 404 on Maven
Central). `setup-gradle` restores `~/.gradle`, which never caches mavenLocal artifacts,
so on a fresh runner `./gradlew lint` could not resolve the classpath — under
`org.gradle.configuration-cache=true` that surfaced at configuration-cache *store* time
as `error writing value of type 'DefaultResolvableConfiguration'` on
`:computation:compileJava`, and the step's `continue-on-error: true` kept the job green:
the advisory lint job had in fact never executed. `code-quality` now carries the same
`talib-native-x86_64-*` / `talib-wrapper-*` cache entries (the wrapper key is now shared
by all four jobs that need it) plus the `setupTalib` step on a miss, mirroring
`java-build-test`. The other three workflows using the artifact
(`chaos-benchmark`, `integration-tests`, `deploy-production`) already had the pattern;
the gap was unique to `code-quality`.

## Addendum (2026-10-23): single saver per TA-Lib cache key

The shared keys above were each saved by every job that used them: on a fresh
key (a `computation/build.gradle` change, or an expired entry) the concurrent
restores all missed, and every job's `actions/cache@v6` post step raced to save
the same entry — three jobs on `talib-native-x86_64-*` (`pr-checks`
`java-build-test` + `code-quality`, `integration-tests` `integration-test`) and
four on `talib-wrapper-*` (those three plus `java-build-test-arm64`). Only the
first reservation wins; the losers log `Failed to save: Unable to reserve cache
... another job may be creating this cache` in the post step (non-fatal, but
noisy, and each loser had already re-run `:computation:setupTalib` locally).

Each key now has exactly one saver. `java-build-test` saves
`talib-native-x86_64-*` and `talib-wrapper-*`; `java-build-test-arm64` saves
the per-arch `talib-native-aarch64-*`; every other consumer (`code-quality`,
`integration-test`, and the arm job's wrapper entry) switched to
`actions/cache/restore@v6`, which has no post step and therefore cannot lose a
reservation. A restore-only job that misses simply runs `setupTalib` itself
under the existing `cache-hit != 'true'` guard — same cost profile as before,
minus the save race. `scripts/validate-cicd.py` now asserts exactly one saver
per key across the five workflows carrying those keys, so the conflict cannot
silently return.

**Scope widened the same day.** `chaos-benchmark`, `deploy-production`,
`codeql`, and (2026-10-24) `chaos-tests` joined the shared keys. The first two carried legacy entries —
`talib-jna-*` (a JNA-era name) and `talib-native-${hash of a gitignored
VERSION file}`, which degenerated to an empty hash and never hit — and their
"build native" step never installed the FFM wrapper JAR into `~/.m2`, so their
Gradle builds could not resolve `com.tictactec.ta.lib:ta-lib-wrapper` on the
compile classpath: CodeQL `autobuild` failed storing the configuration cache,
and the chaos/promotion builds hit the same wall. All three now restore the
shared `talib-native-x86_64-*` / `talib-wrapper-*` entries via
`actions/cache/restore@v6` (restore-only, per the single-saver rule) and run
`:computation:setupTalib` when either restore misses. `codeql`'s workflow name
became `CodeQL Weekly` to match its schedule-first cadence. `chaos-tests`
(weekly chaos scenarios, manual dispatch) got the same entries when its
`bootJar` build was found to need the wrapper on the classpath.

## Addendum (2026-10-24): build cache and build-scan publication scope

A published build scan (`zolxf3h3n6yqu`, local `clean build`, 3m36s, 203
tasks) was audited via the scan's JSON sections. Findings and fixes:

1. **Build cache enabled.** 89 tasks reported "Not cacheable: Caching not
   enabled for build" — `org.gradle.caching` was never set. It is now on in
   `gradle.properties`. No workflow change was needed: every CI Gradle job
   already caches `~/.gradle/caches`, which contains `build-cache-1/`, so
   repeated builds of unchanged modules now restore compile/SpotBugs/test
   results instead of re-executing them (verified locally: 14/77 tasks
   FROM-CACHE after clearing all `build/` outputs).
2. **Container-based tests excluded from cache reads.** `:integration-tests:test`
   runs against the mutable `timescale/timescaledb:latest-pg16` image tag,
   which file-input-based cache keys cannot see; that task carries
   `outputs.cacheIf { false }` so a cache hit can never replay results
   against a different image. (Only module using Testcontainers.)
3. **Build-scan publication restricted to CI.** `com.gradle.develocity`
   4.6.1 publishes *every* build by default (empirically: a local
   `gradlew help` uploaded a scan). `settings.gradle` now carries
   `publishing.onlyIf { CI == 'true' }`, so only GitHub Actions / `act`
   runs publish to scans.gradle.com; local work stays private.
4. **Not actionable (third-party).** All 30 deprecations
   (`Configuration.setVisible`, removal scheduled for Gradle 11) originate
   from `com.github.spotbugs` 6.5.13 — the newest release on the plugin
   portal; Arrow 19.0.0's `sun.misc.Unsafe` warning is likewise upstream.
5. **Critical path (context, no change).** `:integration-tests:test` is
   158s of the 216s wall time; SpotBugs tasks account for roughly 40% of
   cumulative task time. The advisory `lint` aggregation stays opt-in, and
   the PR gate keeps excluding the integration module as before.
