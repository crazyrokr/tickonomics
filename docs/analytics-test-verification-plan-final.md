# Analytics Test Verification Plan — Final Version

> Fully broken-down, atomic execution plan.
> Based on codebase audit of 30 test files, 27 service files (55 public functions), 26 routers, 26 API endpoints.
> Scope: `analytics/` — Python FastAPI microservice.

---

## Inventory at a Glance

| Dimension | Count | Notes |
|-----------|-------|-------|
| Service files | 27 | Across 23 subdirectories under `app/services/` |
| Public functions | 55 | All return `dict` or `list[float]` |
| Router files | 26 | Registered in `main.py` with `app.include_router()` |
| Test files | 30 | Under `tests/`, naming convention `test_<name>_service.py` |
| API endpoints | 26 | Zero HTTP-level test coverage |
| Orphaned services | 1 | `fi/q_world_pricer_service.py` — no router, no `__init__.py` |
| Unused fixtures | 1 | `conftest.py` provides `TestClient`, no test uses it |

---

## Assertion Quality Tiers

Every assert in the codebase falls into exactly one tier. This classification drives the entire audit.

| Tier | Label | Definition | Example |
|------|-------|------------|---------|
| T0 | Smoke | Checks presence/absence of keys, status codes, non-negativity | `assert "error" not in result` |
| T1 | Structural | Checks output shape, length, type | `assert len(forecasts) == 5` |
| T2 | Relative | Compares two outputs from same function under different inputs | `assert corr_pc1 > uncorr_pc1` |
| T3 | Invariant | Verifies a mathematical property that must always hold | `assert abs(weights_sum - 1.0) < 1e-9` |
| T4 | Known-answer | Compares output to an independently computed expected value | `assert abs(duration - 5.0) < 1e-6` |

---

## Service-to-Router-to-Test Master Map

| # | Router Prefix | Router File | Service File | Service Functions | Test File |
|---|---------------|-------------|--------------|-------------------|-----------|
| 1 | `/health` | `routers/health.py` | N/A (inline) | N/A | N/A |
| 2 | `/api/v1/econometrics` | `routers/econometrics/econometrics.py` | `services/econometrics/econometrics_service.py` | `adf_test`, `granger_causality`, `ols_regression` | `tests/test_econometrics_service.py` |
| 3 | `/api/v1/fixed-income` | `routers/fixed_income/fixed_income.py` | `services/fixed_income/fixed_income_service.py` | `macaulay_duration`, `convexity`, `yield_to_maturity` | `tests/test_fixed_income_service.py` |
| 4 | `/api/v1/performance` | `routers/performance/performance.py` | `services/performance/performance_service.py` | `sharpe_ratio`, `sortino_ratio`, `fama_french_regression` | `tests/test_performance_service.py` |
| 5 | `/api/v1/risk` | `routers/risk/risk.py` | `services/risk/risk_service.py` | `value_at_risk`, `conditional_var`, `garch_forecast` | `tests/test_risk_service.py` |
| 6 | `/api/v1/statistical/evt` | `routers/statistical/evt_risk.py` | `services/statistical/evt_risk_service.py` | `EvtRiskService.fit_tail_distribution`, `EvtRiskService.simulate_tail_paths` | `tests/test_evt_risk_service.py` |
| 7 | `/api/v1/statistical/fdr` | `routers/statistical/multiple_testing.py` | `services/statistical/multiple_testing_service.py` | `apply_fdr_correction` | `tests/test_multiple_testing_service.py` |
| 8 | `/api/v1/statistical/yield-curve` | `routers/statistical/yield_curve.py` | `services/statistical/yield_curve_service.py` | `nelson_siegel`, `fit_yield_curve`, `interpolate_yield_curve` | `tests/test_yield_curve_service.py` |
| 9 | `/api/v1/statistical/macro` | `routers/statistical/macro_shock.py` | `services/statistical/macro_shock_service.py` | `compute_impulse_response` | `tests/test_macro_shock_service.py` |
| 10 | `/api/v1/statistical/qr` | `routers/statistical/quantile_regression.py` | `services/statistical/quantile_regression_service.py` | `calculate_quantile_bands` | `tests/test_quantile_regression_service.py` |
| 11 | `/api/v1/statistical/entropy` | `routers/statistical/transfer_entropy.py` | `services/statistical/transfer_entropy_service.py` | `compute_transfer_entropy` | `tests/test_transfer_entropy_service.py` |
| 12 | `/api/v1/anomaly` | `routers/anomaly/anomaly.py` | `services/anomaly/anomaly_service.py` | `train_autoencoder`, `detect_anomalies` | `tests/test_anomaly_service.py` |
| 13 | `/api/v1/regime` | `routers/regime/regime.py` | `services/regime/regime_service.py` | `garch_regime`, `cnn_lstm_regime`, `qed_regime`, `rahf_regime` | `tests/test_regime_service.py` |
| 14 | `/api/v1/climate` | `routers/climate/climate.py` | `services/climate/climate_service.py` | `simulate_climate` | `tests/test_climate_service.py` |
| 15 | `/api/v1/drift` | `routers/drift/drift.py` | `services/drift/drift_service.py` | `simulate_ito`, `barrier_hitting_probability` | `tests/test_drift_service.py` |
| 16 | `/api/v1/optimizer` | `routers/optimizer/optimizer.py` | `services/optimizer/sgd_optimizer_service.py` | `compute_weight_delta` | `tests/test_sgd_optimizer_service.py` |
| 17 | `/api/v1/simulate` | `routers/simulation/simulation.py` | `services/simulation/sobol_service.py` | `sobol_simulate`, `discrete_correction` | `tests/test_sobol_service.py` |
| 18 | `/api/v1/greeks` | `routers/greeks/greeks.py` | `services/greeks/bsm_service.py` | `bsm_greeks`, `aggregate_gex` | `tests/test_bsm_service.py` |
| 19 | `/api/v1/liquidity` | `routers/liquidity/liquidity.py` | `services/liquidity/amihud_service.py` | `compute_amihud` | `tests/test_liquidity_services.py` |
| 19 | `/api/v1/liquidity` | `routers/liquidity/liquidity.py` | `services/liquidity/comovement_pca_service.py` | `compute_comovement_factor` | `tests/test_liquidity_services.py` |
| 19 | `/api/v1/liquidity` | `routers/liquidity/liquidity.py` | `services/liquidity/strategic_runs_service.py` | `detect_strategic_runs` | `tests/test_liquidity_services.py` |
| 20 | `/api/v1/backtest` | `routers/backtest/backtest.py` | `services/backtest/robustness_service.py` | `robustness_scan` | `tests/test_robustness_service.py` |
| 21 | `/api/v1/reproducibility` | `routers/reproducibility/reproducibility.py` | `services/reproducibility/rds_scorer_service.py` | `compute_rds` | `tests/test_rds_scorer_service.py` |
| 22 | `/api/v1/sentiment` | `routers/sentiment/sentiment.py` | `services/sentiment/sentiment_service.py` | `analyze_sentiment`, `analyze_lexicon` | `tests/test_sentiment_service.py` |
| 23 | `/api/v1/stops` | `routers/stops/stops.py` | `services/stops/markov_stop_service.py` | `calibrate_stops` | `tests/test_stops_service.py` |
| 24 | `/api/v1/diagnostics` | `routers/diagnostics/diagnostics.py` | `services/diagnostics/diagnostic_service.py` | `compute_diagnostics` | `tests/test_diagnostics_service.py` |
| 25 | `/api/v1/analytics` | `routers/ml/volatility.py` | `services/ml/volatility_forecast_service.py` | `forecast_volatility` | `tests/test_volatility_forecast_service.py` |
| 26 | `/api/v1/tournament` | `routers/tournament/tournament.py` | `services/benchmark/tournament_service.py` | `evaluate_tournament` | `tests/test_tournament_service.py` |
| 27 | `/api/v1/explainability` | `routers/explainability/explainability.py` | `services/explainability/shap_service.py` | `compute_feature_importance` | `tests/test_explainability_service.py` |
| **ORPHAN** | *(none)* | *(none)* | `services/fi/q_world_pricer_service.py` | `compute_fair_value`, `compute_tbill_greeks` | `tests/test_q_world_pricer_service.py` |

---

## Phase 0: Pre-Audit Setup

### 0.1 Add `pytest-cov` dependency

**Why:** Phase 3.2 uses `--cov` flag. `pytest-cov` is absent from both `requirements.txt` and `pyproject.toml`. Without it, coverage measurement will fail.

- **0.1.1** Open `analytics/pyproject.toml`.
- **0.1.2** Under `[project.optional-dependencies] test`, add the line `"pytest-cov>=5.0"`.
  - Before: `test = ["pytest>=8.0", "httpx>=0.28.0"]`
  - After: `test = ["pytest>=8.0", "httpx>=0.28.0", "pytest-cov>=5.0"]`
- **0.1.3** Open `analytics/requirements.txt`.
- **0.1.4** Append `pytest-cov==5.0.0` at the end of the file.
- **0.1.5** Run `cd analytics && pip install -e ".[test]"` to verify the installation succeeds.
- **0.1.6** Run `cd analytics && python -m pytest --version` to confirm `pytest-cov` is available as a plugin.

### 0.2 Create audit working directory

- **0.2.1** Create directory: `analytics/tests/audit/`.
- **0.2.2** Add `analytics/tests/audit/.gitkeep` so the directory is tracked.
- **0.2.3** Append `audit/` to `analytics/.dockerignore` (audit artifacts are not for production).
- **0.2.4** Verify `.dockerignore` exists and contains the new entry.

### 0.3 Verify current test suite passes (baseline)

- **0.3.1** Run `cd analytics && python -m pytest tests/ -v --tb=short 2>&1 | tee tests/audit/baseline_run.log`.
- **0.3.2** Record the baseline: total tests, passed, failed, errors in `tests/audit/baseline_run.log`.
- **0.3.3** If any failures exist, log them but do **not** fix them yet — they become inputs to Phase 1.

---

## Phase 1: Audit

### 1.1 Router Registry Extraction

**Goal:** Produce a machine-readable list of every registered router.

- **1.1.1** Read `analytics/app/main.py` (70 lines).
- **1.1.2** Extract every line matching `app.include_router(...)`.
- **1.1.3** For each match, parse into `(router_import_name, prefix, tags)`.
- **1.1.4** Resolve the import to its file path by reading the import statements at the top of `main.py`. Example: `from app.routers.risk import risk` → `app/routers/risk/risk.py`.
- **1.1.5** For each router file, read it and extract every `@router.post(...)` or `@router.get(...)` decorator to get the HTTP method and sub-path.
- **1.1.6** Assemble the full endpoint table: `(http_method, full_url, router_file, handler_function_name)`.
- **1.1.7** Write the result to `tests/audit/router_registry.json` as a JSON array.

**Expected output:** 26 router entries. `fi/q_world_pricer_service.py` will **not** appear — confirming the orphan.

### 1.2 Service Inventory

**Goal:** Enumerate every public function in every service file with its full signature.

- **1.2.1** Run `find analytics/app/services -name "*.py" ! -name "__init__.py" | sort` to list all 27 service files.
- **1.2.2** For each service file, read it and extract every `def ` line that is not prefixed with `_`.
- **1.2.3** For each function, record: `(file_path, function_name, parameters, return_annotation)`.
- **1.2.4** For `EvtRiskService` (the only class-based service), also extract class-level methods.
- **1.2.5** Write the result to `tests/audit/service_inventory.json`.

**Expected output:** 55 public functions across 27 files.

### 1.3 Test Inventory

**Goal:** Enumerate every test function in every test file.

- **1.3.1** Run `find analytics/tests -name "test_*.py" | sort` to list all 30 test files.
- **1.3.2** For each test file, run `grep -n "^def test_" <file>` to extract all test function names and line numbers.
- **1.3.3** Record: `(test_file, test_function_name, start_line_number)`.
- **1.3.4** Write the result to `tests/audit/test_inventory.json`.

### 1.4 Triangulation Matrix Construction

**Goal:** Cross-reference routers, services, and tests to detect structural gaps.

- **1.4.1** Join `router_registry.json` with `service_inventory.json` on the service file path. Flag:
  - **R1 — Router without service:** a router file references a service that does not exist.
  - **R2 — Service without router:** a service file has no corresponding router entry.
- **1.4.2** Join `service_inventory.json` with `test_inventory.json` on naming convention (`test_<service_name>_service.py` ↔ `<service_name>_service.py`). Flag:
  - **S1 — Service without test:** a service file has no test file.
  - **S2 — Test without service:** a test file exists but the service file is missing.
- **1.4.3** Produce the full triangulation matrix:

  | Router? | Service? | Test? | Status |
  |---------|----------|-------|--------|
  | ✅ | ✅ | ✅ | Complete |
  | ❌ | ✅ | ✅ | **Orphaned service** (e.g., `fi/q_world_pricer_service.py`) |
  | ✅ | ✅ | ❌ | **Untested service** |
  | ❌ | ❌ | ✅ | **Stale test** |

- **1.4.4** Write the matrix to `tests/audit/triangulation_matrix.md`.

### 1.5 Per-Test Assertion Quality Audit

**Goal:** Classify every `assert` statement in every test file into tier T0–T4.

For each of the 30 test files:

- **1.5.1** Read the full file.
- **1.5.2** Find every `assert` statement using `grep -n "assert " <file>`.
- **1.5.3** For each assert, classify it:
  - **T0 (Smoke):** Checks key existence, status string, non-negativity, type. No numerical comparison to an expected value.
    - Pattern: `assert "error" not in`, `assert result["status"] == "SUCCESS"`, `assert value > 0`, `assert isinstance(...)`
  - **T1 (Structural):** Checks output shape, length, dimension, key count.
    - Pattern: `assert len(...) == N`, `assert result.shape == (...)`, `assert set(result.keys()) == {...}`
  - **T2 (Relative):** Compares two outputs from the same function under different inputs.
    - Pattern: `assert result_A > result_B` where A and B differ in input, `assert abs(a - b) < tol` where `a` and `b` are outputs of the same function
  - **T3 (Invariant):** Verifies a mathematical identity that must hold for all valid inputs.
    - Pattern: `assert abs(sum - 1.0) < eps`, `assert call_price - put_price ≈ parity`, `assert modified_duration < macaulay_duration`
  - **T4 (Known-answer):** Compares output to a pre-computed expected value from an independent source.
    - Pattern: `assert abs(result - expected) < tolerance` where `expected` is a literal constant or computed from a closed-form formula
- **1.5.4** For each test file, tally the asserts per tier. Compute:
  - `total_asserts`
  - `T2+_count` and `T2+_pct`
  - `highest_tier`
- **1.5.5** Write per-file results to `tests/audit/assertion_quality.md`.

**Expected per-file results (from prior audit):**

| Test File | Total Asserts | T0 | T1 | T2 | T3 | T4 | Highest | T2+% |
|-----------|--------------|----|----|----|----|----|----|---------|------|
| test_yield_curve_service.py | ~4 | ~4 | 0 | 0 | 0 | 0 | T0 | 0% |
| test_risk_service.py | ~12 | ~6 | ~4 | ~1 | ~1 | 0 | T3 | ~17% |
| test_bsm_service.py | ~15 | ~4 | ~2 | 0 | ~3 | ~6 | T4 | ~60% |
| test_fixed_income_service.py | ~18 | ~4 | ~3 | ~2 | ~5 | ~4 | T4 | ~61% |
| test_sgd_optimizer_service.py | ~14 | ~3 | ~4 | 0 | ~7 | 0 | T3 | ~50% |
| test_evt_risk_service.py | ~8 | ~2 | ~2 | 0 | ~2 | ~2 | T4 | ~50% |
| test_liquidity_services.py | ~16 | ~5 | ~3 | ~4 | ~4 | 0 | T3 | ~50% |
| test_sobol_service.py | ~10 | ~4 | ~2 | ~1 | ~2 | ~1 | T4 | ~40% |
| test_anomaly_service.py | ~12 | ~4 | ~3 | ~1 | ~4 | 0 | T3 | ~42% |

### 1.6 Edge-Case Coverage Audit

**Goal:** For each test file, assess coverage across five edge-case categories.

For each of the 30 test files:

- **1.6.1** Scan for insufficient-data tests: grep for patterns like `len(...)<`, `insufficient`, `fewer`, `min_`, `too few`. Mark present/absent.
- **1.6.2** Scan for boundary-value tests: grep for `0`, `1e-`, `-`, `inf`, `nan`, `boundary`, `edge`. Mark present/absent.
- **1.6.3** Scan for invalid-input tests: grep for `"error"`, `invalid`, `negative`, `mismatch`. Mark present/absent.
- **1.6.4** Scan for false-positive guards: grep for patterns where the test verifies the function **rejects** or **returns zero** for data that should not trigger a positive result. Mark present/absent.
- **1.6.5** Scan for mathematical-invariant tests: grep for `abs(`, `< tolerance`, `< eps`, `< 1e-`. Mark present/absent.
- **1.6.6** Compile into a matrix:

  | Test File | Insufficient Data | Boundary Values | Invalid Input | False-Positive Guard | Math Invariant |
  |-----------|:-:|:-:|:-:|:-:|:-:|
  | test_yield_curve_service.py | ❌ | ❌ | ❌ | ❌ | ❌ |
  | test_bsm_service.py | ❌ | ✅ | ✅ | ❌ | ✅ |
  | test_fixed_income_service.py | ✅ | ❌ | ✅ | ❌ | ✅ |
  | ... | ... | ... | ... | ... | ... |

- **1.6.7** Write the matrix to `tests/audit/edge_case_coverage.md`.

### 1.7 GWT Compliance Audit

**Goal:** Verify which test files use code-level `# Given / # When / # Then` comments vs. docstring-only GWT.

- **1.7.1** For each test file, run `grep -c "# Given" <file>` and `grep -c "# Then" <file>`.
- **1.7.2** If both counts are > 0, mark the file as **code-level GWT**. Otherwise, mark as **docstring-only GWT**.
- **1.7.3** Compile a list of non-compliant files.
- **1.7.4** Append to `tests/audit/assertion_quality.md`.

**Expected result:** Only `test_evt_risk_service.py` and `test_yield_curve_service.py` use code-level GWT. 28 files are non-compliant.

### 1.8 Gap Prioritization

**Goal:** Produce a ranked remediation list.

- **1.8.1** From the assertion quality audit (1.5), extract all test files where `highest_tier < T3`. These are the **primary remediation targets**.
- **1.8.2** From the edge-case coverage audit (1.6), extract all test files with 2 or more ❌ marks. These are **secondary remediation targets**.
- **1.8.3** From the triangulation matrix (1.4), extract all structural gaps (orphaned, untested, stale).
- **1.8.4** Assign business criticality scores (1–5) based on domain:

  | Domain | Criticality | Rationale |
  |--------|:-----------:|-----------|
  | risk (VaR, CVaR, GARCH) | 5 | Direct capital-at-risk computation |
  | greeks (BSM) | 5 | Options pricing — financial accuracy is mandatory |
  | fixed_income (duration, convexity, YTM) | 5 | Bond pricing — regulatory accuracy |
  | statistical/evt_risk | 5 | Tail risk — regulatory capital |
  | statistical/multiple_testing (FDR) | 4 | Research integrity — false discovery control |
  | statistical/yield_curve | 4 | Curve fitting — pricing input |
  | liquidity (Amihud, PCA, runs) | 4 | Market microstructure — execution quality |
  | performance (Sharpe, Sortino, FF) | 4 | Strategy evaluation — investment decisions |
  | simulation (Sobol) | 3 | Monte Carlo — numerical approximation |
  | optimizer (SGD) | 3 | Portfolio construction — weight allocation |
  | statistical/quantile_regression | 3 | Risk modeling |
  | statistical/transfer_entropy | 3 | Information flow analysis |
  | statistical/macro_shock | 3 | Stress testing |
  | regime (GARCH, CNN-LSTM, QED, RAHF) | 3 | Market state detection |
  | stops (Markov) | 2 | Trade execution |
  | backtest (robustness) | 2 | Strategy validation |
  | econometrics (ADF, Granger, OLS) | 2 | Research tooling |
  | anomaly (autoencoder) | 2 | Detection tooling |
  | sentiment | 2 | Signal input |
  | climate | 1 | Research simulation |
  | drift (Ito) | 1 | Research simulation |
  | reproducibility (RDS) | 1 | Scoring heuristic |
  | diagnostics | 1 | Summary statistics |
  | explainability (SHAP) | 1 | Feature importance |
  | benchmark (tournament) | 1 | Strategy comparison |
  | volatility_forecast | 2 | ML-based forecasting |
  | fi/q_world_pricer (ORPHAN) | 0 | Unreachable — must resolve first |

- **1.8.5** Compute `priority_score = criticality × (5 - highest_tier)` for each primary target.
- **1.8.6** Sort descending. The top entries are remediated first.
- **1.8.7** Write the ranked list to `tests/audit/gap_prioritization.md`.

**Expected top priorities:**

| Rank | Test File | Criticality | Highest Tier | Gap Score | Priority |
|------|-----------|:-----------:|:------------:|:---------:|:--------:|
| 1 | test_yield_curve_service.py | 4 | T0 | 5 | 20 |
| 2 | test_macro_shock_service.py | 3 | T0–T1 | 4–5 | 12–15 |
| 3 | test_risk_service.py | 5 | T3 | 2 | 10 |
| 4 | test_liquidity_services.py | 4 | T3 | 2 | 8 |
| 5 | test_performance_service.py | 4 | T1–T2 | 3–4 | 12–16 |
| 6 | test_climate_service.py | 1 | T0–T1 | 4–5 | 4–5 |
| 7 | test_drift_service.py | 1 | T0–T1 | 4–5 | 4–5 |

### 1.9 Orphan Resolution Decision

**Goal:** Decide what to do with `fi/q_world_pricer_service.py`.

- **1.9.1** Read `analytics/app/services/fi/q_world_pricer_service.py` to assess completeness and quality.
- **1.9.2** Read `analytics/tests/test_q_world_pricer_service.py` to assess test coverage.
- **1.9.3** Check if `analytics/app/services/fi/__init__.py` exists (it does not).
- **1.9.4** Decide one of:
  - **Wire in:** Create `__init__.py`, create a router file, add `include_router` to `main.py`.
  - **Remove:** Delete the service and its test file.
- **1.9.5** Record the decision in `tests/audit/orphan_resolution.md`.
- **1.9.6** Execute the decision (this is the only step that modifies production code during Phase 1).

### 1.10 Produce Consolidated Audit Report

- **1.10.1** Merge all audit artifacts into `tests/audit/verification_audit.md`:
  - Router registry table (from 1.1)
  - Service inventory table (from 1.2)
  - Test inventory table (from 1.3)
  - Triangulation matrix (from 1.4)
  - Assertion quality matrix (from 1.5)
  - Edge-case coverage matrix (from 1.6)
  - GWT compliance list (from 1.7)
  - Gap prioritization table (from 1.8)
  - Orphan resolution decision (from 1.9)

---

## Phase 2: Gold Standard Implementation

### 2.1 Create Reference Module

**Goal:** Build a self-tested library of closed-form reference implementations that serve as expected-value oracles for T4 tests.

#### 2.1.1 Create module structure

- **2.1.1.1** Create directory `analytics/tests/reference/`.
- **2.1.1.2** Create `analytics/tests/reference/__init__.py` (empty).

#### 2.1.2 Implement `analytics/tests/reference/formulas.py`

Implement one pure function per domain. Each function must have a docstring citing the source formula (textbook, paper, or standard reference).

| Function | Domain | Source Formula | Input | Output |
|----------|--------|----------------|-------|--------|
| `bsm_call_price(S, K, T, r, sigma)` | Options pricing | Black-Scholes closed form: `S·N(d1) - K·e^(-rT)·N(d2)` | 5 floats | float |
| `bsm_put_price(S, K, T, r, sigma)` | Options pricing | Put-call parity: `call - S + K·e^(-rT)` | 5 floats | float |
| `bsm_delta(S, K, T, r, sigma, option_type)` | Greeks | `N(d1)` for call, `N(d1)-1` for put | 6 params | float |
| `bsm_gamma(S, K, T, r, sigma)` | Greeks | `n(d1) / (S·sigma·sqrt(T))` | 5 floats | float |
| `nelson_siegel_rate(b0, b1, b2, tau, t)` | Yield curve | `b0 + (b1+b2)·(1-e^(-t/tau))/(t/tau) - b2·e^(-t/tau)` | 5 floats | float |
| `gpd_quantile(xi, beta, exceedance_prob, threshold, n_total, n_exceed)` | EVT | GPD quantile: `threshold + (beta/xi)·((n_total/n_exceed · p)^(-xi) - 1)` | 5 floats | float |
| `macaulay_duration_exact(cash_flows, times, y)` | Fixed income | `sum(t·PV(CF)) / sum(PV(CF))` | 3 arrays | float |
| `convexity_exact(cash_flows, times, y)` | Fixed income | `sum(t·(t+1)·PV(CF)) / ((1+y)^2 · sum(PV(CF)))` | 3 arrays | float |
| `zero_coupon_duration(t)` | Fixed income | `t` (identity for zero-coupon) | float | float |
| `zero_coupon_convexity(t, y)` | Fixed income | `t·(t+1) / (1+y)^2` | 2 floats | float |
| `amihud_exact(returns, dollar_volumes)` | Liquidity | `mean(abs(return) / dollar_volume)` | 2 arrays | float |
| `sharpe_exact(returns, rf, annualize, periods_per_year=252)` | Performance | `mean(R-rf) / std(R-rf) · sqrt(periods)` | arrays + bools | float |
| `sortino_exact(returns, rf, annualize, periods_per_year=252)` | Performance | `mean(R-rf) / downside_deviation · sqrt(periods)` | arrays + bools | float |
| `fdr_bh_corrected(p_values, alpha)` | Multiple testing | Benjamini-Hochberg step-up procedure | array + float | list[bool] |
| `var_historical(returns, confidence)` | Risk | `percentile(returns, 100·(1-confidence))` | array + float | float |
| `cvar_historical(returns, confidence)` | Risk | `mean(returns[returns <= VaR])` | array + float | float |

- **2.1.2.1** Write each function with a docstring citing the source (e.g., *"Hull, Options Futures and Other Derivatives, Ch.21"*).
- **2.1.2.2** Use only `numpy` and `scipy.stats.norm` — no service imports.
- **2.1.2.3** All functions must be pure: no side effects, no RNG, no state.

#### 2.1.3 Write `analytics/tests/reference/test_formulas.py`

Test the reference module itself against analytically trivial known cases.

| Test | Function Under Test | Input | Expected Output | Source |
|------|---------------------|-------|-----------------|--------|
| ATM call at expiry | `bsm_call_price` | S=100, K=100, T=0, r=0.05, σ=0.2 | 0.0 | Intrinsic value at expiry |
| Deep ITM call | `bsm_call_price` | S=200, K=100, T=1, r=0, σ=0.01 | ≈100 | Nearly deterministic |
| Put-call parity | `bsm_call_price` + `bsm_put_price` | S=100, K=105, T=0.5, r=0.05, σ=0.2 | `call - put = S - K·e^(-rT)` | Parity identity |
| ATM delta range | `bsm_delta` | S=100, K=100, T=1, r=0.05, σ=0.2, call | 0.5 < δ < 0.7 | BSM property |
| Zero-coupon duration | `macaulay_duration_exact` | CF=[0,0,0,0,100], t=[1,2,3,4,5], y=0.05 | 5.0 | Exact identity |
| Zero-coupon convexity | `convexity_exact` | CF=[0,0,0,0,100], t=[1,2,3,4,5], y=0.05 | `5·6/1.05^2 ≈ 27.21` | Exact formula |
| Nelson-Siegel at t=0 | `nelson_siegel_rate` | b0=0.05, b1=-0.02, b2=0.01, tau=3, t=0 | `b0 + b1 = 0.03` | Limit as t→0 |
| Nelson-Siegel as t→∞ | `nelson_siegel_rate` | b0=0.05, b1=-0.02, b2=0.01, tau=3, t=1000 | `b0 = 0.05` | Limit as t→∞ |
| VaR on known distribution | `var_historical` | returns=[-0.05, -0.03, -0.01, 0.01, 0.03], confidence=0.8 | -0.03 | 20th percentile |
| FDR all significant | `fdr_bh_corrected` | p=[0.001, 0.002, 0.003], alpha=0.05 | [True, True, True] | All below threshold |

- **2.1.3.1** Each test uses code-level `# Given / # When / # Then` comments.
- **2.1.3.2** Each test is a T4 known-answer test with explicit tolerance.

#### 2.1.4 Validate reference module

- **2.1.4.1** Run `cd analytics && python -m pytest tests/reference/test_formulas.py -v --tb=short`.
- **2.1.4.2** If any test fails, debug the reference function (not the test — the test uses trivial inputs).
- **2.1.4.3** Only proceed to Phase 2.2 when all reference tests pass.

### 2.2 Prioritized Test Remediation

For each service in the gap prioritization list (from 1.8.7), top to bottom:

#### Per-service remediation template

The following steps are repeated for each target service. Each iteration produces a **single test file update** that is validated before moving on.

##### Step A: Analyze service source

- **A.1** Read the full service file.
- **A.2** List all public functions and their parameters.
- **A.3** Identify which mathematical properties or identities can be tested as T3/T4.
- **A.4** Identify edge cases that are missing from existing tests.

##### Step B: Analyze existing tests

- **B.1** Read the full existing test file.
- **B.2** List all existing test functions and their assert tiers.
- **B.3** Identify gaps: untested functions, untested edge cases, missing tiers.

##### Step C: Write new tests

- **C.1** Open the existing test file for editing.
- **C.2** For each gap identified in B.3, write a new test function:
  - **C.2.1** Function name follows pattern `test_<function>_<property_or_edge_case>`.
  - **C.2.2** Docstring in GWT prose: `"""Given ..., when ..., then ..."""`.
  - **C.2.3** Code body uses `# Given`, `# When`, `# Then` comment markers.
  - **C.2.4** Contains at least one T3 or T4 assert using the reference module from 2.1.
  - **C.2.5** Uses `@pytest.mark.parametrize` for boundary sweeps where applicable.
  - **C.2.6** Includes a false-positive guard test: verify the function **rejects** or **returns zero/null** for inputs that should not produce a positive result.

##### Step D: Validate

- **D.1** Run `cd analytics && python -m pytest tests/<test_file>.py -v --tb=short`.
- **D.2** If failures, debug and fix. Do not proceed until all tests pass.
- **D.3** Run the full suite to verify no regression: `cd analytics && python -m pytest tests/ --tb=short -q`.

#### 2.2.1 `test_yield_curve_service.py` (Priority: 20 — highest gap)

**Service:** `services/statistical/yield_curve_service.py`
**Functions:** `nelson_siegel(t, b0, b1, b2, tau)`, `fit_yield_curve(maturities, yields)`, `interpolate_yield_curve(betas, tau, maturities)`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_nelson_siegel_at_zero` | T4 | `nelson_siegel(0, b0, b1, b2, tau) == b0 + b1` — exact limit |
| `test_nelson_siegel_at_infinity` | T4 | `nelson_siegel(1e6, b0, b1, b2, tau) ≈ b0` — exact limit |
| `test_nelson_siegel_monotonicity` | T3 | With b1 < 0 and b2 = 0, curve is monotonically increasing toward b0 |
| `test_fit_recovers_parameters` | T4 | Generate synthetic yields from known NS parameters, fit, verify recovered params within 10% |
| `test_interpolate_matches_nelson_siegel` | T4 | Interpolate at the same maturities used for fitting — values must match within 1e-6 |
| `test_fit_insufficient_data` | T0/T1 | 2 points only — must return error or FIT_FAILURE |
| `test_fit_flat_curve` | T3 | All yields equal — b1 and b2 should be near 0, b0 ≈ yield level |
| `test_fit_inverted_curve` | T3 | Downward-sloping yields — fitted curve must be decreasing |
| `test_interpolate_extrapolation_warning` | T1 | Interpolate beyond the fitted maturity range — verify behavior |
| `test_fit_negative_yields` | T3 | Negative yield inputs — verify the fit handles them (Euro-Japanese bond market reality) |

**Parametrize sets:**
- `nelson_siegel` at t = [0, 0.25, 0.5, 1, 2, 5, 10, 30]
- `fit_yield_curve` with flat / normal / inverted / humped curve shapes

#### 2.2.2 `test_macro_shock_service.py` (Priority: ~14)

**Service:** `services/statistical/macro_shock_service.py`
**Function:** `compute_impulse_response(columns, data, steps=5)`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_impulse_response_shape` | T1 | Output has `steps` entries per column |
| `test_impulse_response_diagonal_positive` | T2 | Own-shock response at step 0 should be positive |
| `test_impulse_response_decays` | T3 | Response magnitude should generally decay over steps for stationary VAR |
| `test_impulse_response_sum_equals_coefficients` | T4 | For known 2-variable VAR(1) with explicit coefficients, verify IRF matches manual computation |
| `test_impulse_response_insufficient_data` | T0 | Fewer observations than variables — must return error |
| `test_impulse_response_single_variable` | T3 | 1-variable IRF should equal AR(1) impulse response: `phi^step` |

**Parametrize sets:**
- `steps` = [1, 5, 10, 20]
- `columns` count = [1, 2, 5]

#### 2.2.3 `test_risk_service.py` (Priority: 10)

**Service:** `services/risk/risk_service.py`
**Functions:** `value_at_risk`, `conditional_var`, `garch_forecast`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_var_known_distribution` | T4 | Generate returns from N(0, 0.01); VaR(99%) should be ≈ `norm.ppf(0.01) * 0.1` for historical method |
| `test_cvar_geq_var` | T3 | CVaR must always be ≥ VaR in absolute terms (monotonicity invariant) |
| `test_parametric_vs_historical_spread` | T2 | For normal returns, parametric VaR should be within 20% of historical VaR |
| `test_garch_forecast_converges` | T3 | Long-horizon GARCH forecast should converge to unconditional variance |
| `test_garch_persistence_bound_tight` | T4 | For known GARCH(1,1) with α+β=0.95, verify persistence ≈ 0.95 ± 0.05 |
| `test_var_confidence_monotonicity` | T3 | VaR(99%) ≥ VaR(95%) ≥ VaR(90%) in absolute terms |
| `test_empty_returns` | T0 | Empty list returns error |
| `test_var_all_positive_returns` | T3 | If all returns > 0, VaR should be positive (no losses) |

**Parametrize sets:**
- `confidence` = [0.90, 0.95, 0.99]
- `method` = ["historical", "parametric"]
- `horizon` = [1, 5, 10, 22]

#### 2.2.4 `test_performance_service.py` (Priority: ~14)

**Service:** `services/performance/performance_service.py`
**Functions:** `sharpe_ratio`, `sortino_ratio`, `fama_french_regression`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_sharpe_known_values` | T4 | Constant returns: Sharpe = `mean(R-rf) / std(R-rf) * sqrt(252)` — compare to reference |
| `test_sharpe_zero_returns` | T3 | All returns = 0 with rf = 0 → Sharpe = 0 or undefined |
| `test_sortino_geq_sharpe` | T3 | For negatively skewed returns, Sortino ≥ Sharpe (downside dev ≤ total dev) |
| `test_sortino_known_values` | T4 | Known return series with hand-computed Sortino |
| `test_ff_regression_r_squared_range` | T3 | R² must be in [0, 1] |
| `test_ff_regression_alpha_intercept` | T3 | Alpha (intercept) should be small for random returns |
| `test_ff_regression_beta_near_one` | T4 | When y = x + noise, market beta ≈ 1.0 |
| `test_sharpe_single_return` | T0 | Single return → undefined (division by zero in std) |

**Parametrize sets:**
- `annualize` = [True, False]
- `risk_free_rate` = [0.0, 0.02, 0.05]

#### 2.2.5 `test_liquidity_services.py` (Priority: 8)

**Service:** `services/liquidity/amihud_service.py`, `comovement_pca_service.py`, `strategic_runs_service.py`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_amihud_known_values` | T4 | Compute Amihud manually: `mean(abs(r)/dv)` and compare |
| `test_amihud_monotonicity_with_volume` | T3 | Same returns, lower volume → higher Amihud |
| `test_comovement_pca_variance_sum` | T3 | Sum of explained variance ratios ≤ 1.0 |
| `test_comovement_pca_orthogonality` | T3 | PC1 · PC2 ≈ 0 (dot product of loadings) |
| `test_strategic_runs_transition_matrix_stochastic` | T3 | Each row of transition matrix sums to 1.0 |
| `test_strategic_runs_no_signal_in_random_data` | T3 | Pure random data should produce near-zero acceleration flags |

#### 2.2.6 `test_econometrics_service.py` (Priority: ~8)

**Service:** `services/econometrics/econometrics_service.py`
**Functions:** `adf_test`, `granger_causality`, `ols_regression`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_adf_on_random_walk` | T4 | Random walk should be non-stationary: p-value > 0.05 |
| `test_adf_on_stationary` | T4 | White noise should be stationary: p-value < 0.05 |
| `test_granger_no_causality_independent` | T3 | Independent series should show no Granger causality |
| `test_granger_known_causality` | T4 | y[t] = 0.8·x[t-1] + noise → x Granger-causes y |
| `test_ols_known_coefficients` | T4 | y = 2·x + 3 → slope ≈ 2, intercept ≈ 3 |
| `test_ols_r_squared_one` | T3 | Perfect linear relationship → R² = 1.0 |
| `test_ols_perfect_multicollinearity` | T0 | x1 = x2 → should return error or handle gracefully |

#### 2.2.7 `test_climate_service.py` (Priority: ~5)

**Service:** `services/climate/climate_service.py`
**Function:** `simulate_climate`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_simulate_climate_reproducibility` | T3 | Same seed → identical output (deterministic) |
| `test_simulate_climate_path_count` | T1 | Output should contain exactly `n_paths` paths |
| `test_simulate_climate_step_count` | T1 | Each path should have exactly `n_steps` steps |
| `test_simulate_climate_positive_carbon_tax` | T2 | Higher carbon tax should produce lower average temperature drift |

#### 2.2.8 `test_drift_service.py` (Priority: ~5)

**Service:** `services/drift/drift_service.py`
**Functions:** `simulate_ito`, `barrier_hitting_probability`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_ito_reproducibility` | T3 | Same seed → identical paths |
| `test_ito_expected_drift` | T4 | For known μ, σ: E[S_T] ≈ S_0 · e^(μT) ± tolerance |
| `test_ito_positive_initial` | T3 | GBM paths should stay positive when S_0 > 0 |
| `test_barrier_probability_monotonic` | T3 | Closer barrier → higher hitting probability |
| `test_barrier_zero_distance` | T0 | Barrier = initial_value → probability = 1.0 (or near 1) |

#### 2.2.9 `test_regime_service.py` (Priority: ~8)

**Service:** `services/regime/regime_service.py`
**Functions:** `garch_regime`, `cnn_lstm_regime`, `qed_regime`, `rahf_regime`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_garch_regime_states_valid` | T1 | Output states are in {0, 1} (binary) |
| `test_garch_regime_two_clusters` | T3 | Sufficient bimodal data → should detect 2 regimes |
| `test_qed_regime_weights_sum` | T3 | State weights sum to 1.0 |
| `test_rahf_regime_period_count` | T1 | Output has at least 1 regime |
| `test_cnn_lstm_regime_output_shape` | T1 | Output length matches input length |

#### 2.2.10 `test_sentiment_service.py` (Priority: ~6)

**Service:** `services/sentiment/sentiment_service.py`
**Functions:** `analyze_sentiment`, `analyze_lexicon`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_sentiment_positive_text` | T2 | Clearly positive text → sentiment score > 0 |
| `test_sentiment_negative_text` | T2 | Clearly negative text → sentiment score < 0 |
| `test_sentiment_neutral_text` | T3 | Neutral text → sentiment score near 0 |
| `test_sentiment_consistency` | T3 | Same text → same result (deterministic) |
| `test_lexicon_sources_match` | T1 | Output has one entry per source |

#### 2.2.11 `test_quantile_regression_service.py` (Priority: ~9)

**Service:** `services/statistical/quantile_regression_service.py`
**Function:** `calculate_quantile_bands`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_bands_monotone` | T3 | Lower quantile band ≤ median band ≤ upper quantile band |
| `test_bands_known_linear` | T4 | y = 2x + noise → 0.5-quantile slope ≈ 2 |
| `test_bands_coverage` | T3 | For 0.1/0.5/0.9 quantiles, ~80% of data falls between 0.1 and 0.9 bands |
| `test_bands_insufficient_data` | T0 | 3 data points → error |

#### 2.2.12 `test_transfer_entropy_service.py` (Priority: ~9)

**Service:** `services/statistical/transfer_entropy_service.py`
**Function:** `compute_transfer_entropy`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_te_zero_for_independent` | T3 | Independent series → TE ≈ 0 |
| `test_te_positive_for_coupled` | T2 | Coupled series (y depends on lagged x) → TE > 0 |
| `test_te_asymmetry` | T3 | TE(X→Y) ≠ TE(Y→X) for unidirectional coupling |
| `test_te_bootstrap_p_value` | T1 | Output includes a p-value |
| `test_te_known_coupling` | T4 | y[t] = 0.8·x[t-1] + noise → TE(X→Y) significantly > TE(Y→X) |

#### 2.2.13 `test_multiple_testing_service.py` (Priority: ~12)

**Service:** `services/statistical/multiple_testing_service.py`
**Function:** `apply_fdr_correction`

| New Test | Tier | Description |
|----------|------|-------------|
| `test_fdr_all_significant` | T4 | All tiny p-values → all rejected (matches reference `fdr_bh_corrected`) |
| `test_fdr_none_significant` | T4 | All large p-values → none rejected |
| `test_fdr_monotonicity` | T3 | If p_i < p_j and p_i is not rejected, then p_j is also not rejected |
| `test_fdr_known_bh_step` | T4 | Manual BH step on [0.01, 0.04, 0.03, 0.20] at α=0.05 → verify specific rejections |

#### 2.2.14 Remaining lower-priority services

For these services, the gap is smaller. Add at least one T3+ test per function:

| Test File | New Test(s) | Tier |
|-----------|-------------|------|
| test_sobol_service.py | `test_sobol_put_vs_analytical` (T4): put price within 5% of BSM put | T4 |
| test_sobol_service.py | `test_sobol_call_put_parity` (T3): `call - put ≈ S - K·e^(-rT)` | T3 |
| test_anomaly_service.py | `test_subtle_outlier_detection` (T2): 3σ outlier (not 10×) detected | T2 |
| test_diagnostics_service.py | `test_diagnostics_known_stats` (T4): mean, std match numpy for known input | T4 |
| test_robustness_service.py | `test_robustness_monotonic_improvement` (T2): better params → higher metric | T2 |
| test_tournament_service.py | `test_tournament_ranking_consistency` (T3): higher Sharpe → higher rank | T3 |
| test_rds_scorer_service.py | `test_rds_perfect_reproducibility` (T4): all flags True → score = 1.0 | T4 |
| test_stops_service.py | `test_stops_loss_triggers_stop` (T3): series of losses → stop probability > 0.5 | T3 |
| test_explainability_service.py | `test_importance_sum_to_one` (T3): normalized importances sum to 1.0 | T3 |
| test_volatility_forecast_service.py | `test_forecast_positive_variance` (T3): all forecast variances > 0 | T3 |

### 2.3 HTTP Integration Tests

**Goal:** Test all 26 API endpoints at the HTTP level using the existing `client` fixture.

#### 2.3.1 Create test file

- **2.3.1.1** Create `analytics/tests/test_api_integration.py`.
- **2.3.1.2** Import `pytest` and the `client` fixture from `conftest.py`.
- **2.3.1.3** Define a `@pytest.fixture` for a minimal valid payload per endpoint.

#### 2.3.2 Define the endpoint matrix

| # | Method | URL | Required Payload Keys |
|---|--------|-----|-----------------------|
| 1 | POST | `/api/v1/econometrics/adf-test` | `series` |
| 2 | POST | `/api/v1/econometrics/granger-causality` | `x`, `y` |
| 3 | POST | `/api/v1/econometrics/ols` | `y`, `x` |
| 4 | POST | `/api/v1/fixed-income/duration` | `cash_flows`, `times`, `yield_rate` |
| 5 | POST | `/api/v1/fixed-income/convexity` | `cash_flows`, `times`, `yield_rate` |
| 6 | POST | `/api/v1/fixed-income/ytm` | `cash_flows`, `times`, `price` |
| 7 | POST | `/api/v1/performance/sharpe` | `returns` |
| 8 | POST | `/api/v1/performance/sortino` | `returns` |
| 9 | POST | `/api/v1/performance/fama-french` | `returns`, `market_returns` |
| 10 | POST | `/api/v1/risk/var` | `returns` |
| 11 | POST | `/api/v1/risk/cvar` | `returns` |
| 12 | POST | `/api/v1/risk/garch` | `returns` |
| 13 | POST | `/api/v1/statistical/evt/fit` | `data` |
| 14 | POST | `/api/v1/statistical/evt/simulate` | `shape_xi`, `scale_beta` |
| 15 | POST | `/api/v1/statistical/fdr` | `p_values` |
| 16 | POST | `/api/v1/statistical/yield-curve/fit` | `maturities`, `yields` |
| 17 | POST | `/api/v1/statistical/yield-curve/interpolate` | `betas`, `tau`, `maturities` |
| 18 | POST | `/api/v1/statistical/macro` | `columns`, `data` |
| 19 | POST | `/api/v1/statistical/qr` | `x_data`, `y_data` |
| 20 | POST | `/api/v1/statistical/entropy` | `source`, `target` |
| 21 | POST | `/api/v1/anomaly/train` | `data` |
| 22 | POST | `/api/v1/anomaly/detect` | `data`, `model_state` |
| 23 | POST | `/api/v1/regime/garch` | `returns` |
| 24 | POST | `/api/v1/regime/cnn-lstm` | `returns` |
| 25 | POST | `/api/v1/regime/qed` | `capital_flow_proxies`, `current_state` |
| 26 | POST | `/api/v1/regime/rahf` | `returns` |

> Note: The exact URL and payload structure for each endpoint must be verified by reading the router files. The table above is based on naming conventions and may need adjustment.

#### 2.3.3 Write integration tests

- **2.3.3.1** For each endpoint, write a happy-path test:

  ```python
  @pytest.mark.parametrize("endpoint,payload", [
      ("/api/v1/risk/var", {"returns": [0.01, -0.02, 0.03, ...]}),
      ("/api/v1/fixed-income/duration", {"cash_flows": [...], "times": [...], "yield_rate": 0.05}),
      ...
  ])
  def test_endpoint_happy_path(client, endpoint, payload):
      # Given: a valid payload for the endpoint
      # When: the endpoint is called
      response = client.post(endpoint, json=payload)
      # Then: the response status is 200 and body contains expected keys
      assert response.status_code == 200
      assert "error" not in response.json()
  ```

- **2.3.3.2** For each endpoint, write an error-path test:

  ```python
  @pytest.mark.parametrize("endpoint,payload", [
      ("/api/v1/risk/var", {"returns": []}),
      ("/api/v1/fixed-income/duration", {"cash_flows": [], "times": [], "yield_rate": -1}),
      ...
  ])
  def test_endpoint_error_path(client, endpoint, payload):
      # Given: an invalid payload
      # When: the endpoint is called
      response = client.post(endpoint, json=payload)
      # Then: the response either returns an error key or a 4xx status
      body = response.json()
      assert "error" in body or response.status_code >= 400
  ```

- **2.3.3.3** Add a health-check test: `GET /health` returns 200.
- **2.3.3.4** All tests use code-level `# Given / # When / # Then` comments.

#### 2.3.4 Validate integration tests

- **2.3.4.1** Run `cd analytics && python -m pytest tests/test_api_integration.py -v --tb=short`.
- **2.3.4.2** Fix any endpoint URL or payload mismatches by reading the router files.
- **2.3.4.3** Re-run until all pass.

#### 2.3.5 Verify routers match integration tests

- **2.3.5.1** Cross-reference the 26 tested endpoints against the router registry from step 1.1.
- **2.3.5.2** Any untested router is flagged as a gap.

### 2.4 GWT Compliance Remediation

**Goal:** Bring all 30 test files to code-level `# Given / # When / # Then` compliance.

For each non-compliant test file (identified in 1.7):

- **2.4.1** Read the file.
- **2.4.2** For each test function, add `# Given`, `# When`, `# Then` comment markers above the corresponding code sections.
  - `# Given` — placed above the data setup / input construction lines.
  - `# When` — placed above the function call line.
  - `# Then` — placed above the first assert line.
- **2.4.3** Do not alter any test logic, only add comments.
- **2.4.4** Run `cd analytics && python -m pytest tests/<file> -v --tb=short` to verify no breakage.
- **2.4.5** Move to the next file.

**Files requiring GWT remediation (28 files):**

All test files except `test_evt_risk_service.py` and `test_yield_curve_service.py`.

---

## Phase 3: Validation and Reporting

### 3.1 Full Suite Execution

- **3.1.1** Run the complete test suite:
  ```bash
  cd analytics && python -m pytest tests/ -v --tb=short -x 2>&1 | tee tests/audit/final_run.log
  ```
- **3.1.2** If `-x` stops on a failure:
  - **3.1.2.1** Read the failure output.
  - **3.1.2.2** Fix the failing test or the underlying service code.
  - **3.1.2.3** Re-run from 3.1.1.
- **3.1.3** Once all tests pass, remove `-x` and run again to get the full summary:
  ```bash
  cd analytics && python -m pytest tests/ -v --tb=short 2>&1 | tee tests/audit/final_run.log
  ```
- **3.1.4** Record from the summary line: `X passed, Y failed, Z errors, W warnings in T seconds`.

### 3.2 Coverage Measurement

- **3.2.1** Run coverage analysis:
  ```bash
  cd analytics && python -m pytest tests/ --cov=app/services --cov-report=term-missing --cov-report=html:tests/audit/coverage_html 2>&1 | tee tests/audit/coverage.log
  ```
- **3.2.2** Parse `coverage.log` for the `term-missing` section. For each service file, extract the coverage percentage.
- **3.2.3** Identify all files below 80% coverage:
  ```bash
  grep -E "^app/services/.*\s+[0-6][0-9]%\s" tests/audit/coverage.log
  ```
- **3.2.4** For each sub-80% file:
  - **3.2.4.1** Read the coverage HTML report to identify uncovered lines.
  - **3.2.4.2** Write additional tests targeting uncovered branches.
  - **3.2.4.3** Re-run coverage for that specific file:
    ```bash
    cd analytics && python -m pytest tests/<test_file>.py --cov=app/services/<service_dir>/<service_file>.py --cov-report=term-missing
    ```
  - **3.2.4.4** Repeat until ≥ 80% or the uncovered lines are defensive/error-path code that is impractical to trigger.
- **3.2.5** Re-run full coverage (step 3.2.1) to get the final numbers.

### 3.3 Structural Compliance Checks

- **3.3.1** **GWT compliance check:**
  ```bash
  for f in analytics/tests/test_*.py; do
    given=$(grep -c "# Given" "$f")
    then=$(grep -c "# Then" "$f")
    if [ "$given" -eq 0 ] || [ "$then" -eq 0 ]; then
      echo "NON-COMPLIANT: $f (Given=$given, Then=$then)"
    fi
  done
  ```
  **Pass condition:** Zero output (all files compliant).

- **3.3.2** **Minimum assertion tier check:**
  - Re-classify all test files using the same T0–T4 rubric from 1.5.
  - Any file still at T0-only is flagged.
  - **Pass condition:** No file is T0-only.

- **3.3.3** **Orphan resolution check:**
  - Verify that `fi/q_world_pricer_service.py` has been either:
    - Wired: `fi/__init__.py` exists, a router exists, `main.py` includes it.
    - Removed: Neither the service file nor the test file exists.
  - **Pass condition:** Orphan is resolved.

- **3.3.4** **Integration test completeness check:**
  - Count tested endpoints in `test_api_integration.py`.
  - Compare against router registry from 1.1 (26 endpoints).
  - **Pass condition:** All 26 endpoints have at least one happy-path and one error-path test.

- **3.3.5** **No test skips or xfails without justification:**
  ```bash
  grep -rn "pytest.mark.skip\|pytest.mark.xfail" analytics/tests/
  ```
  **Pass condition:** No skips or xfails, or each has an inline comment explaining why.

### 3.4 Final Report

#### 3.4.1 Write `docs/verification_report.md`

Structure:

```markdown
# Analytics Test Verification Report

## Executive Summary
- Total services: 27
- Total public functions: 55
- Total test files: 30 + 1 integration + 1 reference = 32
- Total test functions: <count>
- Coverage: <overall_pct>% average across app/services/

## 1. Triangulation Matrix
<from 1.4>

## 2. Assertion Quality Summary
<from 1.5, updated with new tests>

## 3. Edge-Case Coverage Summary
<from 1.6, updated with new tests>

## 4. GWT Compliance
- Compliant files: 30/30
- Non-compliant: 0

## 5. Coverage by Service
<table from 3.2>

## 6. Gap Remediation Summary
<table: gap → remediation → status>

## 7. Known-Answer Reference Formulas
<table from 2.1.2>

## 8. Integration Test Results
<table from 2.3>

## 9. Unresolved Items
- <any remaining gaps or decisions deferred>
```

#### 3.4.2 Write ADR: `docs/adr/ADR-XXX-analytics-test-verification.md`

```markdown
# ADR-XXX: Analytics Test Verification Methodology

## Status
Accepted

## Context
The analytics microservice had 30 test files with inconsistent assertion quality,
no HTTP integration tests, an orphaned service, and no coverage measurement.

## Decision
Adopt a tiered assertion quality framework (T0–T4) to classify, prioritize, and
remediate test gaps. Implement a reference formula module as the oracle for
known-answer (T4) tests.

## Consequences
- All test files must reach at least T1 assertion quality.
- All mathematical services must have at least one T4 test.
- All API endpoints must have HTTP-level integration tests.
- Coverage target: ≥ 80% per service file.
```

---

## Appendix A: Complete Step Index

| Phase | Step | Substeps | Produces |
|-------|------|----------|----------|
| 0 | 0.1 Add pytest-cov | 0.1.1–0.1.6 | Updated pyproject.toml, requirements.txt |
| 0 | 0.2 Create audit dir | 0.2.1–0.2.4 | tests/audit/ directory |
| 0 | 0.3 Baseline run | 0.3.1–0.3.3 | tests/audit/baseline_run.log |
| 1 | 1.1 Router registry | 1.1.1–1.1.7 | tests/audit/router_registry.json |
| 1 | 1.2 Service inventory | 1.2.1–1.2.5 | tests/audit/service_inventory.json |
| 1 | 1.3 Test inventory | 1.3.1–1.3.4 | tests/audit/test_inventory.json |
| 1 | 1.4 Triangulation | 1.4.1–1.4.4 | tests/audit/triangulation_matrix.md |
| 1 | 1.5 Assertion quality | 1.5.1–1.5.5 | tests/audit/assertion_quality.md |
| 1 | 1.6 Edge-case coverage | 1.6.1–1.6.7 | tests/audit/edge_case_coverage.md |
| 1 | 1.7 GWT compliance | 1.7.1–1.7.4 | Appended to assertion_quality.md |
| 1 | 1.8 Gap prioritization | 1.8.1–1.8.7 | tests/audit/gap_prioritization.md |
| 1 | 1.9 Orphan resolution | 1.9.1–1.9.6 | tests/audit/orphan_resolution.md |
| 1 | 1.10 Consolidated audit | 1.10.1 | tests/audit/verification_audit.md |
| 2 | 2.1 Reference module | 2.1.1–2.1.4 | tests/reference/ (3 files) |
| 2 | 2.2.1 Yield curve | A.1–D.3 | Updated test_yield_curve_service.py |
| 2 | 2.2.2 Macro shock | A.1–D.3 | Updated test_macro_shock_service.py |
| 2 | 2.2.3 Risk | A.1–D.3 | Updated test_risk_service.py |
| 2 | 2.2.4 Performance | A.1–D.3 | Updated test_performance_service.py |
| 2 | 2.2.5 Liquidity | A.1–D.3 | Updated test_liquidity_services.py |
| 2 | 2.2.6 Econometrics | A.1–D.3 | Updated test_econometrics_service.py |
| 2 | 2.2.7 Climate | A.1–D.3 | Updated test_climate_service.py |
| 2 | 2.2.8 Drift | A.1–D.3 | Updated test_drift_service.py |
| 2 | 2.2.9 Regime | A.1–D.3 | Updated test_regime_service.py |
| 2 | 2.2.10 Sentiment | A.1–D.3 | Updated test_sentiment_service.py |
| 2 | 2.2.11 Quantile regression | A.1–D.3 | Updated test_quantile_regression_service.py |
| 2 | 2.2.12 Transfer entropy | A.1–D.3 | Updated test_transfer_entropy_service.py |
| 2 | 2.2.13 Multiple testing | A.1–D.3 | Updated test_multiple_testing_service.py |
| 2 | 2.2.14 Remaining services | per file | Updated individual test files |
| 2 | 2.3 HTTP integration | 2.3.1–2.3.5 | tests/test_api_integration.py |
| 2 | 2.4 GWT remediation | 2.4.1–2.4.5 | All 28 non-compliant files fixed |
| 3 | 3.1 Full suite run | 3.1.1–3.1.4 | tests/audit/final_run.log |
| 3 | 3.2 Coverage | 3.2.1–3.2.5 | tests/audit/coverage.log + coverage_html/ |
| 3 | 3.3 Structural checks | 3.3.1–3.3.5 | Compliance report (pass/fail per check) |
| 3 | 3.4 Final report | 3.4.1–3.4.2 | docs/verification_report.md + docs/adr/ADR-XXX |

---

## Appendix B: File Tree of Artifacts Produced

```
analytics/
├── pyproject.toml                          (modified — add pytest-cov)
├── requirements.txt                        (modified — add pytest-cov)
├── tests/
│   ├── audit/
│   │   ├── .gitkeep
│   │   ├── baseline_run.log
│   │   ├── router_registry.json
│   │   ├── service_inventory.json
│   │   ├── test_inventory.json
│   │   ├── triangulation_matrix.md
│   │   ├── assertion_quality.md
│   │   ├── edge_case_coverage.md
│   │   ├── gap_prioritization.md
│   │   ├── orphan_resolution.md
│   │   ├── verification_audit.md
│   │   ├── final_run.log
│   │   ├── coverage.log
│   │   └── coverage_html/
│   ├── reference/
│   │   ├── __init__.py
│   │   ├── formulas.py
│   │   └── test_formulas.py
│   ├── test_api_integration.py             (new)
│   └── test_*_service.py                   (28 files modified for GWT compliance)
│                                           (14 files modified for new tests)

docs/
├── verification_report.md                  (new)
└── adr/
    └── ADR-XXX-analytics-test-verification.md  (new)
```

---

## Appendix C: Known Issues Carried Forward

These issues were discovered during the initial audit. Phase 2 addresses most of them. Items that require separate work are noted.

| # | Issue | Resolved By | Status |
|---|-------|-------------|--------|
| 1 | Orphaned `fi/q_world_pricer_service.py` | Phase 1 step 1.9 | Decision needed |
| 2 | Unused `client` fixture | Phase 2 step 2.3 | Resolved by integration tests |
| 3 | `test_yield_curve_service.py` critically weak | Phase 2 step 2.2.1 | Resolved by new T3/T4 tests |
| 4 | Inconsistent GWT enforcement | Phase 2 step 2.4 | Resolved by remediation |
| 5 | No `@pytest.mark.parametrize` | Phase 2 steps 2.2.x | Resolved by new tests |
| 6 | Stale `egg-info/SOURCES.txt` | `pip install -e .` in step 0.1.5 | Resolved by rebuild |
| 7 | `arrow_ipc.py` transport stub | Out of scope — documented | Deferred |
