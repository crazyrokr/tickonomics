# Orphan Resolution Decision

## Investigation

The plan identified `app/services/fi/q_world_pricer_service.py` as an orphaned service (no router, no `__init__.py`).

### Findings

1. **Router wiring:** `app/routers/fixed_income/fixed_income.py` imports from `app.services.fi.q_world_pricer_service` and exposes two endpoints:
   - `POST /api/v1/fixed-income/q-world-fair-value` → `compute_fair_value`
   - `POST /api/v1/fixed-income/tbill-greeks` → `compute_tbill_greeks`

2. **Missing `__init__.py`:** `app/services/fi/__init__.py` does not exist. Python can still import from this directory because it's imported via the `fi.q_world_pricer_service` path and Python 3 handles namespace packages.

3. **Test file exists:** `tests/test_q_world_pricer_service.py` with 6 test functions, 16 asserts, T4 highest tier.

4. **No functional impact:** The service works correctly without `__init__.py` in Python 3.12+.

## Decision

**Create `__init__.py`** to make the package explicit and avoid potential issues with packaging tools.

## Actions Taken

- Created `app/services/fi/__init__.py` (empty)
