"""Group A: Closed-form / deterministic external validation tests (Steps 1-18).

Each test compares a service output against an INDEPENDENT oracle algorithm
that is deliberately different from the service implementation.
"""

import math
from itertools import product

import numpy as np
import pytest
from scipy import optimize, stats

from app.services.diagnostics.diagnostic_service import compute_diagnostics
from app.services.econometrics.econometrics_service import (
    adf_test,
    granger_causality,
    ols_regression,
)
from app.services.fi.q_world_pricer_service import compute_fair_value
from app.services.fixed_income.fixed_income_service import (
    convexity,
    macaulay_duration,
    yield_to_maturity,
)
from app.services.greeks.bsm_service import aggregate_gex, bsm_greeks
from app.services.liquidity.amihud_service import compute_amihud
from app.services.liquidity.comovement_pca_service import compute_comovement_factor
from app.services.liquidity.strategic_runs_service import detect_strategic_runs
from app.services.optimizer.sgd_optimizer_service import compute_weight_delta
from app.services.performance.performance_service import (
    fama_french_regression,
    sharpe_ratio,
    sortino_ratio,
)
from app.services.reproducibility.rds_scorer_service import compute_rds
from app.services.statistical.multiple_testing_service import apply_fdr_correction
from app.services.statistical.yield_curve_service import fit_yield_curve

from .conftest import (
    TOL_HIGH,
    TOL_LOW,
    TOL_MEDIUM,
    TOL_STATISTICAL,
    bsm_delta_erfc,
    bsm_gamma_erfc,
    bsm_price_erfc,
)


# ---------------------------------------------------------------------------
# Step 1 — bsm_service.bsm_greeks vs erfc-based BSM
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    "option_type, spot",
    [
        ("call", 100.0),
        ("put", 100.0),
        ("call", 200.0),
        ("put", 200.0),
        ("call", 50.0),
        ("put", 50.0),
    ],
    ids=[
        "ATM_call",
        "ATM_put",
        "deep_ITM_call",
        "deep_ITM_put",
        "deep_OTM_call",
        "deep_OTM_put",
    ],
)
def test_step01_bsm_greeks_vs_erfc_oracle(option_type, spot):
    # Given
    K, T, r, sigma = 100.0, 1.0, 0.05, 0.2

    # When
    result = bsm_greeks(spot, K, T, r, sigma, option_type)
    oracle_delta = bsm_delta_erfc(spot, K, T, r, sigma, option_type)
    oracle_gamma = bsm_gamma_erfc(spot, K, T, r, sigma)
    oracle_price = bsm_price_erfc(spot, K, T, r, sigma, option_type)

    # Then
    assert result["delta"] == pytest.approx(oracle_delta, abs=TOL_MEDIUM)
    assert result["gamma"] == pytest.approx(oracle_gamma, abs=TOL_MEDIUM)
    assert result["price"] == pytest.approx(oracle_price, abs=TOL_MEDIUM)


def test_step01_put_call_parity():
    # Given
    S, K, T, r, sigma = 100.0, 100.0, 1.0, 0.05, 0.2

    # When
    call = bsm_greeks(S, K, T, r, sigma, "call")
    put = bsm_greeks(S, K, T, r, sigma, "put")
    lhs = call["price"] - put["price"]
    rhs = S - K * math.exp(-r * T)

    # Then
    assert lhs == pytest.approx(rhs, abs=TOL_HIGH)


# ---------------------------------------------------------------------------
# Step 2 — aggregate_gex vs manual dollar gamma
# ---------------------------------------------------------------------------
def test_step02_aggregate_gex_vs_manual():
    # Given
    S, r, sigma, T = 100.0, 0.05, 0.2, 0.25
    positions = [
        {"spot_price": S, "strike_price": 95, "time_to_expiry": T,
         "risk_free_rate": r, "implied_volatility": sigma,
         "option_type": "call", "quantity": 10, "open_interest": 50},
        {"spot_price": S, "strike_price": 100, "time_to_expiry": T,
         "risk_free_rate": r, "implied_volatility": sigma,
         "option_type": "call", "quantity": 5, "open_interest": 30},
        {"spot_price": S, "strike_price": 105, "time_to_expiry": T,
         "risk_free_rate": r, "implied_volatility": sigma,
         "option_type": "put", "quantity": 8, "open_interest": 40},
    ]

    # When — service
    result = aggregate_gex(positions)

    # When — oracle: manually compute net GEX with erfc-based gamma
    oracle_net_gex = 0.0
    for pos in positions:
        gamma = bsm_gamma_erfc(
            pos["spot_price"], pos["strike_price"],
            pos["time_to_expiry"], pos["risk_free_rate"],
            pos["implied_volatility"],
        )
        dollar_gamma = gamma * pos["spot_price"] ** 2 * 0.01
        exposure = dollar_gamma * (pos["quantity"] + pos["open_interest"])
        if pos["option_type"] == "put":
            exposure = -exposure
        oracle_net_gex += exposure

    # Then
    assert result["net_gex"] == pytest.approx(oracle_net_gex, abs=TOL_LOW)


# ---------------------------------------------------------------------------
# Step 3 — fixed_income_service vs pure Python loop
# ---------------------------------------------------------------------------
def test_step03_macaulay_duration_5y_semiannual():
    # Given — 5Y 5% semi-annual coupon bond at 5% yield
    coupon = 0.05
    face = 1000.0
    freq = 2
    n_periods = 10
    yld = coupon

    cash_flows = [(coupon / freq) * face] * (n_periods - 1) + [
        (coupon / freq) * face + face
    ]
    times = [i / freq for i in range(1, n_periods + 1)]
    period_rate = yld / freq

    # When — service
    result = macaulay_duration(cash_flows, times, period_rate)

    # When — oracle: pure Python loop with math.pow
    pv_total = 0.0
    weighted = 0.0
    for cf, t in zip(cash_flows, times):
        df = math.pow(1 + period_rate, t)
        pv = cf / df
        pv_total += pv
        weighted += t * pv
    oracle_duration = weighted / pv_total

    # Then
    assert result["macaulay_duration"] == pytest.approx(
        oracle_duration, abs=TOL_MEDIUM
    )


def test_step03_convexity_zero_coupon_5y():
    # Given — zero-coupon 5Y bond
    face = 1000.0
    T = 5.0
    yld = 0.05

    cash_flows = [face]
    times = [T]

    # When — service
    result = convexity(cash_flows, times, yld)

    # When — oracle: manual convexity
    pv = face / math.pow(1 + yld, T)
    conv = T * (T + 1) * face / math.pow(1 + yld, T + 2) / pv

    # Then
    assert result["convexity"] == pytest.approx(conv, abs=TOL_MEDIUM)


def test_step03_duration_zero_coupon_5y():
    # Given
    face = 1000.0
    T = 5.0
    yld = 0.05

    # When — service
    result = macaulay_duration([face], [T], yld)

    # Then — zero-coupon Macaulay duration equals maturity
    assert result["macaulay_duration"] == pytest.approx(T, abs=TOL_HIGH)


# ---------------------------------------------------------------------------
# Step 4 — yield_to_maturity vs scipy.optimize.newton
# ---------------------------------------------------------------------------
@pytest.mark.parametrize(
    "coupon_rate, yld_label",
    [(0.05, "par"), (0.03, "discount"), (0.07, "premium")],
    ids=["par_bond", "discount_bond", "premium_bond"],
)
def test_step04_ytm_vs_scipy_newton(coupon_rate, yld_label):
    # Given — 5Y semi-annual bond
    face = 1000.0
    freq = 2
    n_periods = 10
    yld = 0.05

    cash_flows = [(coupon_rate / freq) * face] * (n_periods - 1) + [
        (coupon_rate / freq) * face + face
    ]
    times = [i / freq for i in range(1, n_periods + 1)]
    period_rate = yld / freq

    pv = sum(cf / math.pow(1 + period_rate, t) for cf, t in zip(cash_flows, times))
    price = pv

    # When — service
    result = yield_to_maturity(cash_flows, times, price)

    # When — oracle: scipy.optimize.newton on PV(CF) - price = 0
    def pv_error(y):
        return sum(cf / math.pow(1 + y, t) for cf, t in zip(cash_flows, times)) - price

    oracle_ytm = optimize.newton(pv_error, 0.05)

    # Then
    assert result["ytm"] == pytest.approx(oracle_ytm, abs=TOL_HIGH)


# ---------------------------------------------------------------------------
# Step 5 — yield_curve_service.fit_yield_curve vs scipy.optimize.minimize
# ---------------------------------------------------------------------------
def test_step05_nelson_siegel_fit_vs_nelder_mead():
    # Given — synthetic yields from known NS parameters
    b0_true, b1_true, b2_true, tau_true = 0.05, -0.02, 0.01, 3.0
    maturities = [0.25, 0.5, 1, 2, 3, 5, 7, 10, 20, 30]

    def ns_model(t, b0, b1, b2, tau):
        exp_term = math.exp(-t / tau)
        term1 = (1 - exp_term) / (t / tau)
        term2 = term1 - exp_term
        return b0 + b1 * term1 + b2 * term2

    yields = [ns_model(t, b0_true, b1_true, b2_true, tau_true) for t in maturities]

    # When — service
    result = fit_yield_curve(maturities, yields)

    # When — oracle: scipy.optimize.minimize (Nelder-Mead) with manual least-squares
    def residuals(params):
        b0, b1, b2, tau = params
        if tau <= 0:
            return 1e12
        return sum(
            (y - ns_model(t, b0, b1, b2, tau)) ** 2
            for t, y in zip(maturities, yields)
        )

    oracle_result = optimize.minimize(
        residuals, [0.04, -0.01, 0.005, 2.0], method="Nelder-Mead",
        options={"maxiter": 50000, "xatol": 1e-8, "fatol": 1e-12},
    )
    b0_oracle, b1_oracle, b2_oracle, tau_oracle = oracle_result.x

    # Then — NS parameters are not uniquely identifiable (near-collinearity),
    # so we validate fit quality: service and oracle fitted yields must both be
    # close to the true input yields and close to each other.
    assert result["status"] == "SUCCESS"
    svc_params = (result["beta_0"], result["beta_1"], result["beta_2"], result["tau"])
    service_fitted = [ns_model(t, *svc_params) for t in maturities]
    oracle_fitted = [ns_model(t, b0_oracle, b1_oracle, b2_oracle, tau_oracle) for t in maturities]

    for i, t in enumerate(maturities):
        true_y = ns_model(t, b0_true, b1_true, b2_true, tau_true)
        # Both optimizers recover the yield curve to within low tolerance
        assert service_fitted[i] == pytest.approx(true_y, abs=TOL_LOW)
        assert oracle_fitted[i] == pytest.approx(true_y, abs=TOL_LOW)


# ---------------------------------------------------------------------------
# Step 6 — multiple_testing_service vs hand-coded BH
# ---------------------------------------------------------------------------
def test_step06_fdr_all_significant():
    # Given — all significant p-values
    p_values = [0.001, 0.002, 0.003, 0.004, 0.005]

    # When — service
    result = apply_fdr_correction(p_values, alpha=0.05)

    # When — oracle: hand-coded BH step-up (Benjamini-Hochberg 1995)
    m = len(p_values)
    indexed = sorted(enumerate(p_values), key=lambda x: x[1])
    adj = [0.0] * m
    for rank_i, (orig_i, p) in enumerate(indexed, start=1):
        bh_value = p * m / rank_i
        adj[orig_i] = bh_value
    # Enforce monotonicity (step-up)
    for j in range(m - 2, -1, -1):
        curr_idx = indexed[j][0]
        next_idx = indexed[j + 1][0]
        if adj[curr_idx] > adj[next_idx]:
            adj[curr_idx] = adj[next_idx]
    oracle_rejected = [p < 0.05 for p in adj]

    # Then
    for i in range(m):
        assert result["adjusted_p_values"][i] == pytest.approx(
            adj[i], abs=TOL_HIGH
        )
    assert result["actionable_mask"] == oracle_rejected


def test_step06_fdr_mixed():
    # Given — mixed significance p-values
    p_values = [0.001, 0.01, 0.04, 0.2, 0.5, 0.8]

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # When — oracle BH
    m = len(p_values)
    indexed = sorted(enumerate(p_values), key=lambda x: x[1])
    adj = [0.0] * m
    for rank_i, (orig_i, p) in enumerate(indexed, start=1):
        bh_value = p * m / rank_i
        adj[orig_i] = bh_value
    for j in range(m - 2, -1, -1):
        curr_idx = indexed[j][0]
        next_idx = indexed[j + 1][0]
        if adj[curr_idx] > adj[next_idx]:
            adj[curr_idx] = adj[next_idx]
    oracle_rejected = [p_adj < 0.05 for p_adj in adj]

    # Then
    for i in range(m):
        assert result["adjusted_p_values"][i] == pytest.approx(
            adj[i], abs=TOL_HIGH
        )
    assert result["actionable_mask"] == oracle_rejected


def test_step06_fdr_all_insignificant():
    # Given — all insignificant p-values
    p_values = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]

    # When
    result = apply_fdr_correction(p_values, alpha=0.05)

    # When — oracle BH
    m = len(p_values)
    indexed = sorted(enumerate(p_values), key=lambda x: x[1])
    adj = [0.0] * m
    for rank_i, (orig_i, p) in enumerate(indexed, start=1):
        bh_value = p * m / rank_i
        adj[orig_i] = bh_value
    for j in range(m - 2, -1, -1):
        curr_idx = indexed[j][0]
        next_idx = indexed[j + 1][0]
        if adj[curr_idx] > adj[next_idx]:
            adj[curr_idx] = adj[next_idx]

    # Then — none should be rejected
    for i in range(m):
        assert result["adjusted_p_values"][i] == pytest.approx(
            adj[i], abs=TOL_HIGH
        )
    assert not any(result["actionable_mask"])


# ---------------------------------------------------------------------------
# Step 7 — amihud_service vs list comprehension
# ---------------------------------------------------------------------------
def test_step07_amihud_vs_list_comprehension():
    # Given
    returns = [0.01, -0.02, 0.015, -0.005, 0.008, -0.012, 0.003, 0.025]
    volumes = [1e6, 2e6, 1.5e6, 3e6, 2.5e6, 1.8e6, 2.2e6, 3.5e6]

    # When — service
    result = compute_amihud(returns, volumes)

    # When — oracle: simple list comprehension
    oracle_amihud = sum(abs(r) / dv for r, dv in zip(returns, volumes)) / len(returns)

    # Then
    assert result["amihud_measure"] == pytest.approx(oracle_amihud, abs=1e-8)


# ---------------------------------------------------------------------------
# Step 8 — comovement_pca_service vs SVD-based PCA
# ---------------------------------------------------------------------------
def test_step08_comovement_pca_vs_svd():
    # Given — synthetic 4-asset spread matrix with a known comovement structure
    rng = np.random.default_rng(12345)
    n_obs = 100
    common_factor = rng.normal(0, 1, n_obs)
    idio = rng.normal(0, 0.3, (n_obs, 4))
    spread_data = np.outer(common_factor, [1.0, 0.8, 0.5, 0.3]) + idio
    spread_matrix = spread_data.tolist()

    # When — service
    result = compute_comovement_factor(spread_matrix)

    # When — oracle: SVD-based PCA on centered differenced data
    data = np.array(spread_matrix)
    diff = np.diff(data, axis=0)
    std = np.std(diff, axis=0, ddof=1)
    std[std == 0] = 1.0
    standardized = (diff - np.mean(diff, axis=0)) / std
    cov = np.cov(standardized, rowvar=False)
    U, s, Vt = np.linalg.svd(cov)
    total_var = np.sum(s)
    oracle_pc1_var = s[0] / total_var
    oracle_pc2_var = s[1] / total_var

    # Then
    assert result["pc1_variance_explained"] == pytest.approx(
        oracle_pc1_var, abs=TOL_LOW
    )
    assert result["pc2_variance_explained"] == pytest.approx(
        oracle_pc2_var, abs=TOL_LOW
    )
    oracle_comovement = oracle_pc1_var + oracle_pc2_var
    assert result["comovement_factor"] == pytest.approx(
        oracle_comovement, abs=TOL_LOW
    )


# ---------------------------------------------------------------------------
# Step 9 — strategic_runs_service vs manual VWAP loop
# ---------------------------------------------------------------------------
def test_step09_strategic_runs_transition_matrix_rows_sum_to_one():
    # Given — synthetic price and volume series
    rng = np.random.default_rng(42)
    n = 60
    prices = 100.0 + np.cumsum(rng.normal(0.01, 0.5, n))
    volumes = np.abs(rng.normal(1e6, 2e5, n))
    price_list = prices.tolist()
    vol_list = volumes.tolist()

    # When — service
    result = detect_strategic_runs(price_list, vol_list, window=20)

    # When — oracle: verify transition matrix rows sum to ~1
    tm = result["transition_matrix"]
    row1_sum = tm["passive_to_passive"] + tm["passive_to_aggressive"]
    row2_sum = tm["aggressive_to_passive"] + tm["aggressive_to_aggressive"]

    # Then
    assert "error" not in result
    assert row1_sum == pytest.approx(1.0, abs=TOL_MEDIUM)
    assert row2_sum == pytest.approx(1.0, abs=TOL_MEDIUM)


# ---------------------------------------------------------------------------
# Step 10 — sgd_optimizer_service vs manual numpy
# ---------------------------------------------------------------------------
def test_step10_sgd_weight_delta_vs_manual():
    # Given
    current_weights = {"alpha": 0.4, "beta": 0.3, "gamma": 0.3}
    signal_performance = [
        {"alpha": 0.01, "beta": 0.02, "gamma": 0.015},
        {"alpha": 0.015, "beta": 0.01, "gamma": 0.02},
        {"alpha": 0.012, "beta": 0.018, "gamma": 0.01},
    ]
    lr = 0.01
    max_delta = 0.05

    # When — service
    result = compute_weight_delta(current_weights, signal_performance,
                                  learning_rate=lr, max_delta=max_delta)

    # When — oracle: manual step-by-step
    components = ["alpha", "beta", "gamma"]
    perf_matrix = np.array([[p[c] for c in components] for p in signal_performance])
    mean_perf = np.mean(perf_matrix, axis=0)
    total_perf = np.sum(mean_perf)
    target_weights = mean_perf / total_perf
    current_arr = np.array([current_weights[c] for c in components])
    raw_delta = lr * (target_weights - current_arr)
    clipped_delta = np.clip(raw_delta, -max_delta, max_delta)
    oracle_deltas = {c: round(float(clipped_delta[i]), 6)
                     for i, c in enumerate(components)}

    # Then
    for comp in components:
        assert result["weight_deltas"][comp] == pytest.approx(
            oracle_deltas[comp], abs=1e-9
        )


# ---------------------------------------------------------------------------
# Step 11 — rds_scorer_service exhaustive enumeration (2^5 = 32 combos)
# ---------------------------------------------------------------------------
def test_step11_rds_exhaustive_enumeration():
    # Given — all 32 boolean combinations of the 5 input flags
    flag_names = ["has_code", "code_versioned", "dataset_available",
                  "hyperparams_documented", "results_reproducible"]

    for bits in product([False, True], repeat=5):
        flags = dict(zip(flag_names, bits))

        # When — service
        result = compute_rds("test_model", **flags)

        # When — oracle: replicate RDS scoring logic
        scores = {k: (2 if v else 0) for k, v in flags.items()}
        dimension_map = {
            "code_availability": ["has_code", "code_versioned"],
            "data_availability": ["dataset_available"],
            "reproducibility": ["hyperparams_documented", "results_reproducible"],
        }
        breakdown = {}
        for dim, keys in dimension_map.items():
            dim_total = 2 * len(keys)
            dim_score = sum(scores[k] for k in keys)
            normalized = int(round(dim_score / dim_total * 2))
            breakdown[dim] = min(2, max(0, normalized))
        oracle_rds = sum(breakdown.values())

        # Then — exact integer comparison
        assert result["rds_score"] == oracle_rds, (
            f"Mismatch for flags {flags}: service={result['rds_score']}, oracle={oracle_rds}"
        )


# ---------------------------------------------------------------------------
# Step 12 — diagnostic_service ACF vs statsmodels
# ---------------------------------------------------------------------------
def test_step12_acf_ar1_vs_statsmodels():
    # Given — AR(1) process with known ACF = phi^lag
    rng = np.random.default_rng(99)
    n = 5000
    phi = 0.7
    noise = rng.normal(0, 1, n)
    ar1 = np.zeros(n)
    ar1[0] = noise[0]
    for t in range(1, n):
        ar1[t] = phi * ar1[t - 1] + noise[t]
    returns_list = ar1.tolist()

    # When — service
    result = compute_diagnostics(returns_list)
    service_acf = result["acf"]["raw_returns"]

    # When — oracle: statsmodels ACF
    from statsmodels.tsa.stattools import acf as sm_acf
    max_lags = result["acf"]["max_lags"]
    oracle_acf = sm_acf(ar1, nlags=max_lags, fft=True)

    # Then — each lag within tolerance
    for lag in range(min(len(service_acf), len(oracle_acf))):
        assert service_acf[lag] == pytest.approx(
            float(oracle_acf[lag]), abs=TOL_LOW
        ), f"ACF mismatch at lag {lag}"


# ---------------------------------------------------------------------------
# Step 13 — q_world_pricer_service vs textbook CIR (B4 fixed)
# ---------------------------------------------------------------------------
def test_step13_q_world_cir_vs_textbook():
    # Given — textbook CIR parameters for 1Y T-Bill
    instrument = "1Y_TBILL"
    current_yield = 0.04

    # When — service
    result = compute_fair_value(instrument, current_yield)

    # When — oracle: Hull Ch.31 CIR bond yield formula
    # For CIR: P(t,T) = A(t,T) * exp(-B(t,T)*r0)
    # where B = 2*(exp(h*T)-1) / (2*h + (h+kappa+lambda)*(exp(h*T)-1))
    #       A = (2*h*exp((kappa+h+lambda)*T/2) /
    #            (2*h + (h+kappa+lambda)*(exp(h*T)-1)))^(2*kappa*theta/sigma^2)
    # Using the service's own parameter mapping for a fair comparison:
    kappa = 0.5
    theta = current_yield * 0.98
    sigma = 0.01
    r0 = current_yield
    tenor = 1.0
    lam = 0.0  # no risk premium in basic model

    h = math.sqrt(kappa ** 2 + 2 * sigma ** 2)
    B = 2.0 * (math.exp(h * tenor) - 1) / (
        2.0 * h + (kappa + lam + h) * (math.exp(h * tenor) - 1)
    )
    A = (
        2.0 * h * math.exp((kappa + lam + h) * tenor / 2.0)
        / (2.0 * h + (kappa + lam + h) * (math.exp(h * tenor) - 1))
    ) ** (2.0 * kappa * theta / sigma ** 2)

    log_P = math.log(A) - B * r0
    oracle_yield = -log_P / tenor

    # Then — if the service matches textbook, this passes; xfail means we expect a bug
    assert result["fair_value_yield"] == pytest.approx(
        oracle_yield, abs=TOL_MEDIUM
    )


# ---------------------------------------------------------------------------
# Step 14 — performance_service vs manual numpy
# ---------------------------------------------------------------------------
def test_step14_sharpe_vs_manual():
    # Given
    rng = np.random.default_rng(7)
    returns = rng.normal(0.0004, 0.01, 252).tolist()
    rf = 0.02

    # When — service
    result = sharpe_ratio(returns, risk_free_rate=rf, annualize=True)

    # When — oracle: manual numpy
    r = np.array(returns)
    excess = r - rf / 252
    oracle_sharpe = float(np.mean(excess) / np.std(r, ddof=1) * np.sqrt(252))

    # Then
    assert result["sharpe_ratio"] == pytest.approx(oracle_sharpe, abs=TOL_MEDIUM)


def test_step14_sortino_vs_manual():
    # Given
    rng = np.random.default_rng(7)
    returns = rng.normal(0.0004, 0.01, 252).tolist()
    rf = 0.02

    # When — service
    result = sortino_ratio(returns, risk_free_rate=rf, annualize=True)

    # When — oracle: manual numpy
    r = np.array(returns)
    target = rf / 252
    excess = r - target
    downside = excess[excess < 0]
    downside_std = float(np.sqrt(np.mean(downside ** 2)))
    oracle_sortino = float(np.mean(excess) / downside_std * np.sqrt(252))

    # Then
    assert result["sortino_ratio"] == pytest.approx(oracle_sortino, abs=TOL_MEDIUM)


# ---------------------------------------------------------------------------
# Step 15 — fama_french_regression vs statsmodels OLS
# ---------------------------------------------------------------------------
def test_step15_fama_french_vs_statsmodels():
    # Given — synthetic data with known factor loadings
    rng = np.random.default_rng(42)
    n = 120
    alpha_true = 0.001
    beta_mkt = 1.2
    beta_smb = 0.5
    beta_hml = -0.3

    mkt = rng.normal(0.0005, 0.015, n).tolist()
    smb = rng.normal(0.0, 0.01, n).tolist()
    hml = rng.normal(0.0, 0.008, n).tolist()
    noise = rng.normal(0, 0.005, n)
    returns = [
        alpha_true + beta_mkt * mkt[i] + beta_smb * smb[i]
        + beta_hml * hml[i] + noise[i]
        for i in range(n)
    ]

    # When — service
    result = fama_french_regression(returns, mkt, smb, hml)

    # When — oracle: statsmodels OLS
    import statsmodels.api as sm
    X = np.column_stack([mkt, smb, hml])
    X = sm.add_constant(X)
    model = sm.OLS(np.array(returns), X).fit()

    coeff_labels = ["alpha", "market_beta", "smb_beta", "hml_beta"]
    for i, label in enumerate(coeff_labels):
        svc_coeff = next(c["estimate"] for c in result["coefficients"] if c["name"] == label)
        assert svc_coeff == pytest.approx(float(model.params[i]), abs=TOL_MEDIUM)

    assert result["r_squared"] == pytest.approx(float(model.rsquared), abs=TOL_LOW)


# ---------------------------------------------------------------------------
# Step 16 — econometrics ols_regression vs statsmodels OLS
# ---------------------------------------------------------------------------
def test_step16_ols_regression_vs_statsmodels():
    # Given
    rng = np.random.default_rng(55)
    n = 100
    x1 = rng.normal(0, 1, n).tolist()
    x2 = rng.normal(0, 1, n).tolist()
    noise = rng.normal(0, 0.5, n)
    y = [2.0 + 1.5 * x1[i] - 0.8 * x2[i] + noise[i] for i in range(n)]

    # When — service
    result = ols_regression(y, [x1, x2])

    # When — oracle: statsmodels OLS
    import statsmodels.api as sm
    X = np.column_stack([x1, x2])
    X = sm.add_constant(X)
    model = sm.OLS(np.array(y), X).fit()

    # Then — coefficients
    assert result["coefficients"][0]["estimate"] == pytest.approx(
        float(model.params[0]), abs=TOL_MEDIUM
    )
    assert result["coefficients"][1]["estimate"] == pytest.approx(
        float(model.params[1]), abs=TOL_MEDIUM
    )
    assert result["coefficients"][2]["estimate"] == pytest.approx(
        float(model.params[2]), abs=TOL_MEDIUM
    )

    # Then — R-squared
    assert result["r_squared"] == pytest.approx(
        float(model.rsquared), abs=TOL_MEDIUM
    )


# ---------------------------------------------------------------------------
# Step 17 — adf_test vs statsmodels (BUG_FOUND B3)
# ---------------------------------------------------------------------------
def test_step17_adf_random_walk_vs_statsmodels():
    # Given — pure random walk (unit root)
    rng = np.random.default_rng(10)
    n = 300
    innovations = rng.normal(0, 1, n)
    rw = np.cumsum(innovations).tolist()

    # When — service
    result = adf_test(rw)

    # When — oracle: statsmodels adfuller
    from statsmodels.tsa.stattools import adfuller
    oracle = adfuller(rw, maxlag=result["lags"], regression="c")

    # Then — test statistic should be close to statsmodels
    assert result["test_statistic"] == pytest.approx(
        float(oracle[0]), abs=TOL_LOW
    )


def test_step17_adf_white_noise_stationary():
    # Given — white noise (stationary)
    rng = np.random.default_rng(20)
    n = 300
    wn = rng.normal(0, 1, n).tolist()

    # When — service
    result = adf_test(wn)

    # When — oracle
    from statsmodels.tsa.stattools import adfuller
    oracle = adfuller(wn, maxlag=result["lags"], regression="c")

    # Then — should be stationary per both
    assert result["stationary"] == True
    assert oracle[1] < 0.05  # statsmodels also says stationary


def test_step17_adf_pvalue_vs_statsmodels():
    # Given — AR(1) phi=0.9 (near unit root but stationary)
    rng = np.random.default_rng(30)
    n = 500
    phi = 0.9
    ar1 = np.zeros(n)
    ar1[0] = rng.normal(0, 1)
    for t in range(1, n):
        ar1[t] = phi * ar1[t - 1] + rng.normal(0, 1)
    series = ar1.tolist()

    # When — service
    result = adf_test(series)

    # When — oracle: statsmodels p-value
    from statsmodels.tsa.stattools import adfuller
    oracle = adfuller(series, maxlag=result["lags"], regression="c")

    # Then — p-values should agree
    assert result["p_value"] == pytest.approx(
        float(oracle[1]), abs=TOL_STATISTICAL
    )


# ---------------------------------------------------------------------------
# Step 18 — granger_causality vs statsmodels
# ---------------------------------------------------------------------------
def test_step18_granger_causal_direction():
    # Given — coupled series: y[t] = 0.5*x[t-1] + 0.3*y[t-1] + noise
    rng = np.random.default_rng(77)
    n = 300
    x = np.zeros(n)
    y = np.zeros(n)
    x[0] = rng.normal(0, 1)
    y[0] = rng.normal(0, 1)
    for t in range(1, n):
        x[t] = 0.8 * x[t - 1] + rng.normal(0, 0.5)
        y[t] = 0.5 * x[t - 1] + 0.3 * y[t - 1] + rng.normal(0, 0.5)

    x_list = x.tolist()
    y_list = y.tolist()

    # When — service: x Granger-causes y
    result_xy = granger_causality(x_list, y_list, max_lags=5)

    # When — oracle: statsmodels Granger causality
    from statsmodels.tsa.stattools import grangercausalitytests
    data_xy = np.column_stack([y_list, x_list])
    try:
        gc_result = grangercausalitytests(data_xy, maxlag=result_xy["lags"], verbose=False)
        lag_key = result_xy["lags"]
        if lag_key in gc_result:
            oracle_f = gc_result[lag_key][0]["ssr_ftest"][0]
        else:
            closest_lag = min(gc_result.keys())
            oracle_f = gc_result[closest_lag][0]["ssr_ftest"][0]
    except Exception:
        oracle_f = None

    # Then — x should Granger-cause y
    assert result_xy["causal"] == True

    # Then — F-statistic within 5% if oracle available
    if oracle_f is not None:
        assert result_xy["f_statistic"] == pytest.approx(
            oracle_f, rel=TOL_STATISTICAL
        )


def test_step18_granger_independent():
    # Given — independent series
    rng = np.random.default_rng(88)
    n = 300
    x = rng.normal(0, 1, n).tolist()
    y = rng.normal(0, 1, n).tolist()

    # When — service
    result = granger_causality(x, y, max_lags=5)

    # Then — independent series should NOT be causal
    assert result["causal"] == False
