# Verification Audit — Consolidated Report

> Generated from Phase 1 audit of `analytics/` test suite.

## 1. Router Registry

49 endpoints across 27 routers (1 GET + 48 POST). Full details in `router_registry.json`.

## 2. Service Inventory

29 service files with 48 public functions. Full details in `service_inventory.json`.

## 3. Test Inventory

27 test files with 151 test functions. Full details in `test_inventory.json`.

## 4. Triangulation Matrix

All 29 service files have matching routers and test files. One structural gap:
- Missing `app/services/fi/__init__.py` — resolved (created).

Full matrix in `triangulation_matrix.md`.

## 5. Assertion Quality

- **Total asserts:** 408 across 27 files
- **T2+ (relative/invariant/known-answer):** 167 (41%)
- **All files reach T4** — no critically weak files
- **Lowest T2+:** test_tournament_service.py (11%)

Full table in `assertion_quality.md`.

## 6. Edge-Case Coverage

| Category | Present | Absent |
|----------|:-------:|:------:|
| Insufficient Data | 16 | 11 |
| Boundary Values | 0 | 27 |
| Invalid Input | 11 | 16 |
| False-Positive Guard | 2 | 25 |
| Math Invariant | 13 | 14 |

Full matrix in `edge_case_coverage.md`.

## 7. GWT Compliance

- **Compliant:** 6/27 files
- **Non-compliant:** 21/27 files

## 8. Gap Prioritization

Top 5 remediation targets by priority score:
1. test_yield_curve_service.py (priority: 20)
2. test_quantile_regression_service.py (priority: 15)
3. test_transfer_entropy_service.py (priority: 15)
4. test_bsm_service.py (priority: 15)
5. test_evt_risk_service.py (priority: 15)

Full ranked list in `gap_prioritization.md`.

## 9. Orphan Resolution

`fi/q_world_pricer_service.py` was NOT an orphan — it's wired into the `fixed_income` router. Missing `__init__.py` created. Details in `orphan_resolution.md`.
