# Analytics Test Verification Plan

> Verified and improved based on actual codebase audit of 30 test files, 27 services, 26 routers.

## Context

- **Scope:** `analytics/` — Python FastAPI microservice
- **Test runner:** pytest (configured via `analytics/pyproject.toml`, invoked through `./gradlew pytest`)
- **Convention:** `test_<name>_service.py` mapping to `app/services/<name>/<name>_service.py`
- **CLAUDE.md rules:** Given-When-Then structure, full unit test coverage, edge cases and false-positive scenarios, ADR for architectural changes

---

## Phase 0: Pre-Audit Setup

### 0.1 Add `pytest-cov` dependency

The coverage step (Phase 3.2) requires `pytest-cov`, which is **missing** from both `requirements.txt` and `pyproject.toml`.

**Action:**
- Add `pytest-cov>=5.0` to `analytics/pyproject.toml` under `[project.optional-dependencies] test`
- Add `pytest-cov==5.0.0` to `analytics/requirements.txt`

### 0.2 Create audit directory

Create `analytics/tests/audit/` (gitignored) for all working artifacts produced during verification.

---

## Phase 1: Audit

### 1.1 Router-Service-Test Triangulation

Build a three-way cross-reference between routers, services, and tests to detect structural gaps.

| Step | Action | Tool |
|------|--------|------|
| 1.1.1 | Read `main.py`, extract all `include_router` calls into a structured list: `(router_module, prefix, tag)` | `Read` |
| 1.1.2 | Scan `services/` for all service files: `(service_path, class_or_function_name)` | `find` + `Read` |
| 1.1.3 | Scan `tests/` for all test files: `(test_path, test_functions[])` | `find` + `grep` |
| 1.1.4 | Scan `routers/` for all router files: `(router_path, endpoint_functions[])` | `find` + `grep` |
| 1.1.5 | Build a **triangulation matrix** crossing all three dimensions and flag: (a) services with no test, (b) services with no router (orphaned), (c) routers with no test, (d) tests with no service (stale) | Manual synthesis |
| 1.1.6 | Write `verification_audit.md` with the full matrix | `Write` |

**Expected finding:** `fi/q_world_pricer_service.py` is orphaned — has tests but no router, no `__init__.py`, and no entry in `main.py`.

### 1.2 Assertion Quality Classification

Classify every assert statement into one of five quality tiers:

| Tier | Label | Example | Found In |
|------|-------|---------|----------|
| T0 | Smoke | `assert result["status"] == "SUCCESS"` | yield_curve, risk |
| T1 | Structural | `assert len(forecasts) == 5` | risk, sgd_optimizer |
| T2 | Relative | `assert corr_pc1 > uncorr_pc1` | liquidity |
| T3 | Invariant | `assert abs(weights_sum - 1.0) < 1e-9` | sgd_optimizer, fixed_income |
| T4 | Known-answer (gold) | `assert abs(duration - 5.0) < 1e-6` | bsm, fixed_income |

| Step | Action | Tool |
|------|--------|------|
| 1.2.1 | For each test function, classify **every assert statement** into tier T0–T4 | `Read` + manual review |
| 1.2.2 | For each test file, compute aggregate quality: `% of asserts at T2+` and `highest tier reached` | Manual synthesis |
| 1.2.3 | Classify edge-case coverage per file: (a) insufficient-data, (b) boundary values, (c) invalid inputs, (d) false-positive guards, (e) mathematical invariants | `Read` + manual review |
| 1.2.4 | Update `verification_audit.md` with per-file quality tier and edge-case coverage matrix | `Edit` |

**Expected findings:**
- `test_yield_curve_service.py` → T0-only, 0% T2+
- `test_bsm_service.py` → T4, 60% T2+
- `test_fixed_income_service.py` → T4, 50% T2+

### 1.3 Gap Prioritization

| Step | Action |
|------|--------|
| 1.3.1 | From the audit, produce a ranked list of gaps: files where `highest tier < T3` sorted by (business criticality × gap severity) |
| 1.3.2 | Identify which services have **no known-answer test at all** (no T4 assert anywhere) |
| 1.3.3 | Flag the orphaned service (`fi/q_world_pricer_service.py`) for a decision: wire it into `main.py` or remove it |
| 1.3.4 | Flag the unused `client` fixture in `conftest.py` — all 30 test files bypass FastAPI `TestClient`; 26 HTTP endpoints have zero integration test coverage |

---

## Phase 2: Gold Standard Implementation

### 2.1 Reference Module

A reference module with its own tests prevents circular validation. Without it, incorrect reference calculations would silently validate wrong service outputs.

| Step | Action |
|------|--------|
| 2.1.1 | Create `analytics/tests/reference/__init__.py` and `analytics/tests/reference/formulas.py` |
| 2.1.2 | In `formulas.py`, implement pure-Python or numpy reference functions for each domain: BSM closed-form pricing, Nelson-Siegel yield curve parameters, EVT GPD quantile, CIR bond pricing, etc. |
| 2.1.3 | Write `analytics/tests/reference/test_formulas.py` — unit tests for the reference module itself using analytically known trivial cases (e.g., zero-coupon bond, ATM option) |
| 2.1.4 | Run `pytest tests/reference/test_formulas.py` — **must pass before proceeding** |

### 2.2 Prioritized Test Implementation

For each service in the gap list (from 1.3.1), in priority order:

| Step | Action |
|------|--------|
| 2.2.1 | Read the service source to extract the core algorithm and identify testable mathematical properties |
| 2.2.2 | Read existing tests to avoid duplication |
| 2.2.3 | Write new test functions using `@pytest.mark.parametrize` for boundary sweeps. Each function must include code-level `# Given / # When / # Then` comments per CLAUDE.md rules |
| 2.2.4 | Each new test must contain at least one T3 (invariant) or T4 (known-answer) assert using the reference module from 2.1 |
| 2.2.5 | Include explicit **false-positive guards**: a test that verifies the service **rejects** well-formed but inappropriate inputs (e.g., uniform distribution for EVT tail fitting) |
| 2.2.6 | Run `pytest tests/<test_file>.py -v --tb=short` — **must pass before moving to the next service** |

### 2.3 HTTP Integration Tests

All 26 API endpoints are untested at the HTTP level. The `conftest.py` already provides a `client` fixture that is unused.

| Step | Action |
|------|--------|
| 2.3.1 | Create `analytics/tests/test_api_integration.py` |
| 2.3.2 | Use the existing `client` fixture from `conftest.py` |
| 2.3.3 | For each router endpoint, write at least one happy-path and one error-path test using `@pytest.mark.parametrize` over `(endpoint, payload, expected_status)` |
| 2.3.4 | Run `pytest tests/test_api_integration.py -v` |

---

## Phase 3: Validation and Reporting

### 3.1 Full Suite Execution

| Step | Action |
|------|--------|
| 3.1.1 | `cd analytics && pytest tests/ -v --tb=short -x 2>&1 \| tee ../test_run.log` — `-x` stops on first failure for fast feedback; `tee` preserves real-time output and stderr |
| 3.1.2 | If failures occur, fix and re-run. Only proceed when all tests pass |

### 3.2 Coverage Measurement

| Step | Action |
|------|--------|
| 3.2.1 | `cd analytics && pytest tests/ --cov=app/services --cov-report=term-missing --cov-report=html:../coverage_html 2>&1 \| tee ../coverage.log` |
| 3.2.2 | Parse the `term-missing` output to identify any service file with `< 80%` line coverage |
| 3.2.3 | For sub-80% files, add targeted tests for uncovered branches |

### 3.3 Structural Checks

Enforce CLAUDE.md rules across all test files.

| Step | Action |
|------|--------|
| 3.3.1 | Verify all test files have code-level `# Given / # When / # Then` comments (grep for `# Given` and `# Then` in each file) |
| 3.3.2 | Verify no test file uses only T0 (smoke) assertions — every file must reach at least T1 |
| 3.3.3 | Verify the orphaned service from 1.3.3 has been resolved (wired into `main.py` or removed) |

### 3.4 Final Report

| Step | Action |
|------|--------|
| 3.4.1 | Write `docs/verification_report.md` containing: (a) triangulation matrix, (b) per-file quality tier, (c) coverage summary, (d) gap remediation summary, (e) list of known-answer reference formulas, (f) unresolved items |
| 3.4.2 | Write an ADR in `docs/adr/` documenting the verification methodology (required by CLAUDE.md for architectural changes) |

---

## Improvements Over Original Plan

| Original | Improved | Rationale |
|----------|----------|-----------|
| Binary Smoke/Correctness | 5-tier T0–T4 classification | Reflects actual quality spectrum; enables prioritized remediation |
| One-directional service→test mapping | Triangulation matrix (router↔service↔test) | Catches orphans like `fi/q_world_pricer_service.py` |
| `VerificationReference.py` script | Testable `tests/reference/` module with own tests | Prevents circular validation — wrong references would pass wrong services |
| No integration tests | `test_api_integration.py` using existing `client` fixture | 26 untested HTTP endpoints is a critical gap |
| `pytest analytics/tests/` from root | `cd analytics && pytest tests/` | Tests import `from app.*` — must run from `analytics/` |
| `pytest > test_run.log` | `pytest 2>&1 \| tee test_run.log` | Preserves real-time output and stderr |
| No coverage dependency check | Phase 0 adds `pytest-cov` | Step 3.2.2 would silently fail without it |
| No GWT enforcement | Phase 3.3.1 structural check | Enforces CLAUDE.md rules |
| No parametrize | `@pytest.mark.parametrize` for boundaries | Systematic edge-case coverage instead of ad-hoc inline code |
| No ADR | Phase 3.4.2 | Required by CLAUDE.md for architectural changes |
| Run from project root | Use `./gradlew pytest` | Already configured in `analytics/build.gradle` |

---

## Known Issues Discovered During Audit

1. **Orphaned `fi/q_world_pricer_service.py`** — has implementation + tests but no router, no `__init__.py`, not in `main.py`. Unreachable from the API.
2. **Unused `client` fixture** — `conftest.py` provides a `TestClient` but zero tests use it. `httpx` dependency is declared but unused in tests.
3. **`test_yield_curve_service.py` critically weak** — only 2 smoke-level tests with assertion range `(-10, 30)`, wider than any realistic Treasury yield.
4. **Inconsistent GWT enforcement** — only 2 of 30 test files use code-level `# Given / # When / # Then`; the rest only use GWT in docstrings.
5. **No `@pytest.mark.parametrize` anywhere** — boundary conditions handled ad-hoc rather than systematically.
6. **Stale `egg-info/SOURCES.txt`** — only lists the original 6 services; not rebuilt after additions.
7. **`arrow_ipc.py` transport stub** — docstring-only, not implemented.
