"""Group B: Statistical / econometric external validation tests (Steps 19-25).

Each test compares a service output against an INDEPENDENT oracle algorithm
that is deliberately different from the service implementation.
"""

import numpy as np
import pytest
from scipy import optimize, stats

from app.services.ml.volatility_forecast_service import forecast_volatility
from app.services.risk.risk_service import conditional_var, garch_forecast, value_at_risk
from app.services.statistical.evt_risk_service import EvtRiskService
from app.services.statistical.macro_shock_service import compute_impulse_response
from app.services.statistical.quantile_regression_service import calculate_quantile_bands
from app.services.statistical.transfer_entropy_service import compute_transfer_entropy

from .conftest import TOL_LOW, TOL_MEDIUM, TOL_STATISTICAL, make_garch_data


# ---------------------------------------------------------------------------
# Step 19 — risk_service.garch_forecast vs scipy.optimize.minimize (L-BFGS-B)
# Bug B1: GARCH gradient ascent only updates omega; alpha/beta stay at
# their initial values (0.1 and 0.85).
# ---------------------------------------------------------------------------
@pytest.mark.xfail(reason="B1: GARCH gradient ascent only updates omega", strict=False)
def test_step19_garch_forecast_vs_scipy_lbfgsb():
    # Given — GARCH(1,1) data with known parameters
    true_omega, true_alpha, true_beta = 0.1, 0.15, 0.80
    returns, _ = make_garch_data(omega=true_omega, alpha=true_alpha,
                                  beta=true_beta, n=1000, seed=42)
    returns_list = returns.tolist()

    # When — service
    result = garch_forecast(returns_list, p=1, q=1, horizon=5)

    # When — oracle: scipy.optimize.minimize (L-BFGS-B) on negative log-likelihood
    r = np.array(returns_list)
    n = len(r)
    r2 = r ** 2

    def neg_log_likelihood(params):
        omega, alpha, beta = params
        if omega <= 0 or alpha < 0 or beta < 0 or alpha + beta >= 1:
            return 1e12
        sigma2 = np.full(n, omega / (1 - alpha - beta))
        for t in range(1, n):
            sigma2[t] = omega + alpha * r2[t - 1] + beta * sigma2[t - 1]
        sigma2 = np.maximum(sigma2, 1e-10)
        ll = np.sum(-0.5 * (np.log(2 * np.pi) + np.log(sigma2) + r2 / sigma2))
        return -ll

    x0 = [np.var(r2) * 0.1, 0.1, 0.8]
    bounds = [(1e-8, None), (1e-6, 0.999), (1e-6, 0.999)]
    oracle_res = optimize.minimize(neg_log_likelihood, x0, method="L-BFGS-B",
                                   bounds=bounds, options={"maxiter": 5000})
    oracle_omega, oracle_alpha, oracle_beta = oracle_res.x

    svc_params = result["parameters"]

    # Then — service parameters should be within 30% of oracle estimates
    assert svc_params["omega"] == pytest.approx(oracle_omega, rel=0.30)
    assert svc_params["alpha"][0] == pytest.approx(oracle_alpha, rel=0.30)
    assert svc_params["beta"][0] == pytest.approx(oracle_beta, rel=0.30)


def test_step19_garch_alpha_beta_are_initial_values():
    """Confirm B1: alpha and beta are never updated from initial values."""
    # Given — GARCH(1,1) data
    returns, _ = make_garch_data(omega=0.1, alpha=0.15, beta=0.80,
                                  n=1000, seed=42)
    returns_list = returns.tolist()

    # When
    result = garch_forecast(returns_list, p=1, q=1, horizon=5)

    # Then — service hardcodes alpha=[0.1] and beta=[0.85] as initial values
    # and never updates them (B1 bug). This documents the exact broken values.
    assert result["parameters"]["alpha"] == [pytest.approx(0.1, abs=TOL_MEDIUM)]
    assert result["parameters"]["beta"] == [pytest.approx(0.85, abs=TOL_MEDIUM)]


# ---------------------------------------------------------------------------
# Step 20 — value_at_risk, conditional_var vs manual numpy
# ---------------------------------------------------------------------------
def test_step20_historical_var_vs_numpy():
    # Given — 1000 normal returns
    rng = np.random.default_rng(42)
    returns = rng.normal(0, 0.02, 1000).tolist()
    confidence = 0.99

    # When — service
    result = value_at_risk(returns, confidence=confidence, method="historical")

    # When — oracle: manual historical VaR
    r = np.array(returns)
    alpha = 1.0 - confidence
    oracle_var = -float(np.percentile(r, alpha * 100))

    # Then
    assert result["var"] == pytest.approx(oracle_var, abs=TOL_MEDIUM)


def test_step20_parametric_var_vs_numpy():
    # Given
    rng = np.random.default_rng(42)
    returns = rng.normal(0, 0.02, 1000).tolist()
    confidence = 0.99

    # When — service
    result = value_at_risk(returns, confidence=confidence, method="parametric")

    # When — oracle: manual parametric VaR
    r = np.array(returns)
    alpha = 1.0 - confidence
    oracle_var = -(float(np.mean(r)) + stats.norm.ppf(alpha) * float(np.std(r, ddof=1)))

    # Then
    assert result["var"] == pytest.approx(oracle_var, abs=TOL_MEDIUM)


def test_step20_cvar_vs_manual():
    # Given
    rng = np.random.default_rng(42)
    returns = rng.normal(0, 0.02, 1000).tolist()
    confidence = 0.99

    # When — service
    result = conditional_var(returns, confidence=confidence)

    # When — oracle: manual CVaR (Expected Shortfall)
    r = np.array(returns)
    alpha = 1.0 - confidence
    var_val = -float(np.percentile(r, alpha * 100))
    oracle_cvar = -float(np.mean(r[r <= -var_val]))

    # Then
    assert result["var"] == pytest.approx(var_val, abs=TOL_MEDIUM)
    assert result["cvar"] == pytest.approx(oracle_cvar, abs=TOL_MEDIUM)


# ---------------------------------------------------------------------------
# Step 21 — evt_risk_service vs manual GPD MLE
# ---------------------------------------------------------------------------
def test_step21_evt_fit_vs_manual_gpd_mle():
    # Given — simulate GPD(xi=0.3, beta=1.5) exceedances
    rng = np.random.default_rng(42)
    n = 2000
    base_data = rng.normal(0, 1, n).tolist()
    true_xi, true_beta = 0.3, 1.5
    exceedances = rng.gamma(1.0, true_beta / (1 + true_xi), size=200).tolist()
    oracle_data = base_data + exceedances

    # When — service
    svc = EvtRiskService()
    result = svc.fit_tail_distribution(oracle_data, quantile_u=0.90)

    # When — oracle: manual GPD MLE via scipy.optimize.minimize
    series = np.array(oracle_data)
    u = np.quantile(series, 0.90)
    exc = series[series > u] - u

    def neg_gpd_ll(params):
        xi, beta = params
        if beta <= 0:
            return 1e12
        if xi == 0:
            return -np.sum(np.log(1.0 / beta) - exc / beta)
        z = 1 + xi * exc / beta
        if np.any(z <= 0):
            return 1e12
        return -np.sum(np.log(1.0 / beta) - (1 + 1.0 / xi) * np.log(z))

    x0 = [0.1, 1.0]
    bounds = [(-0.5, 2.0), (0.01, 10.0)]
    oracle_res = optimize.minimize(neg_gpd_ll, x0, method="L-BFGS-B",
                                   bounds=bounds, options={"maxiter": 5000})
    oracle_xi, oracle_beta = oracle_res.x

    # Then — 10% relative tolerance
    assert result["status"] == "SUCCESS"
    assert result["shape_xi"] == pytest.approx(oracle_xi, rel=0.10)
    assert result["scale_beta"] == pytest.approx(oracle_beta, rel=0.10)


# ---------------------------------------------------------------------------
# Step 22 — macro_shock_service point estimates vs manual VAR(1) OLS
# ---------------------------------------------------------------------------
def test_step22_impulse_response_point_estimates_vs_manual_ols():
    # Given — 2-variable VAR(1) with known coefficient matrix
    # Y[t] = c + A * Y[t-1] + e[t], A = [[0.5, 0.2], [0.1, 0.4]]
    rng = np.random.default_rng(42)
    n = 500
    A_true = np.array([[0.5, 0.2], [0.1, 0.4]])
    c_true = np.array([0.0, 0.0])
    Y = np.zeros((n, 2))
    for t in range(1, n):
        Y[t] = c_true + A_true @ Y[t - 1] + rng.normal(0, 0.5, 2)

    columns = ["var1", "var2"]
    data = Y.tolist()

    # When — service
    result = compute_impulse_response(columns, data, steps=5)

    # When — oracle: manual OLS for VAR(1)
    # X = Y[t-1], Y_target = Y[t]
    X_ols = Y[:-1]
    Y_target = Y[1:]
    # B = (X'X)^{-1} X'Y
    B_hat = np.linalg.lstsq(X_ols, Y_target, rcond=None)[0]
    # IRF via cumulative sum of coefficient matrix powers
    oracle_irf_var1 = []
    oracle_irf_var2 = []
    power = np.eye(2)
    for step in range(result["horizon"][0] if "horizon" in result else 6):
        oracle_irf_var1.append(float(power[0, 0]))
        oracle_irf_var2.append(float(power[1, 0]))
        power = power @ B_hat

    # Then — point estimates should be close to manual OLS IRF
    assert "error" not in result
    svc_var1 = result["response_paths"]["var1"]
    svc_var2 = result["response_paths"]["var2"]

    for i in range(min(len(svc_var1), len(oracle_irf_var1))):
        assert svc_var1[i] == pytest.approx(oracle_irf_var1[i], abs=TOL_STATISTICAL)
    for i in range(min(len(svc_var2), len(oracle_irf_var2))):
        assert svc_var2[i] == pytest.approx(oracle_irf_var2[i], abs=TOL_STATISTICAL)


@pytest.mark.xfail(reason="B5: CI uses response*-1.96 instead of response-1.96*SE",
                   strict=False)
def test_step22_impulse_response_ci_bug_b5():
    """Verify B5: confidence intervals are response*-1.96 instead of response +/- 1.96*SE."""
    # Given — simple 2-variable VAR(1)
    rng = np.random.default_rng(42)
    n = 500
    A_true = np.array([[0.5, 0.2], [0.1, 0.4]])
    Y = np.zeros((n, 2))
    for t in range(1, n):
        Y[t] = A_true @ Y[t - 1] + rng.normal(0, 0.5, 2)

    columns = ["var1", "var2"]
    data = Y.tolist()

    # When
    result = compute_impulse_response(columns, data, steps=5)

    # Then — the B5 bug: CI_low = response * -1.96 (instead of response - 1.96*SE)
    # So CI_low should be exactly -1.96 * response for each step
    for col in columns:
        for i in range(len(result["response_paths"][col])):
            response = result["response_paths"][col][i]
            ci_low = result["confidence_low"][col][i]
            # If B5 bug exists: ci_low == response * -1.96
            assert ci_low == pytest.approx(response * -1.96, abs=TOL_MEDIUM)


# ---------------------------------------------------------------------------
# Step 23 — quantile_regression_service vs manual LP (Koenker-Bassett)
# ---------------------------------------------------------------------------
def test_step23_quantile_regression_vs_linprog():
    # Given — y = 2x + 3 + noise, fit 0.5-quantile
    rng = np.random.default_rng(42)
    n = 200
    x = rng.uniform(0, 10, n)
    noise = rng.normal(0, 1, n)
    y = 2.0 * x + 3.0 + noise

    x_data = [[1.0, float(xi)] for xi in x]
    y_data = y.tolist()

    # When — service
    result = calculate_quantile_bands(x_data, y_data, quantiles=[0.5])

    # When — oracle: scipy.optimize.linprog (Koenker-Bassett LP formulation)
    # Minimize sum of check-function residuals:
    # min  tau * sum(u_i) + (1-tau) * sum(v_i)
    # s.t. y = X * beta + u - v,  u >= 0, v >= 0
    tau = 0.5
    X_mat = np.column_stack([np.ones(n), x])
    k = X_mat.shape[1]

    # LP variables: [beta_0, beta_1, u_1, ..., u_n, v_1, ..., v_n]
    c_lp = np.zeros(k + 2 * n)
    c_lp[k:k + n] = tau       # u coefficients
    c_lp[k + n:] = 1 - tau    # v coefficients

    # Equality constraint: y = X*beta + u - v
    A_eq = np.zeros((n, k + 2 * n))
    A_eq[:, :k] = X_mat
    A_eq[:, k:k + n] = np.eye(n)
    A_eq[:, k + n:] = -np.eye(n)
    b_eq = y

    bounds_lp = [(None, None)] * k + [(0, None)] * (2 * n)
    lp_res = optimize.linprog(c_lp, A_eq=A_eq, b_eq=b_eq, bounds=bounds_lp,
                              method="highs")
    oracle_intercept = lp_res.x[0]
    oracle_slope = lp_res.x[1]

    # Then — 10% relative tolerance on slope
    svc_intercept = result["coefficients"][0][0]
    svc_slope = result["coefficients"][0][1]
    assert svc_slope == pytest.approx(oracle_slope, rel=0.10)


# ---------------------------------------------------------------------------
# Step 24 — transfer_entropy_service on known processes
# ---------------------------------------------------------------------------
def test_step24_transfer_entropy_coupled():
    """Coupled process: y[t] = 0.8*x[t-1] + noise => TE(X->Y) >> 0."""
    # Given
    rng = np.random.default_rng(42)
    n = 500
    x = rng.normal(0, 1, n)
    y = np.zeros(n)
    for t in range(1, n):
        y[t] = 0.8 * x[t - 1] + rng.normal(0, 0.5)

    # When
    result = compute_transfer_entropy(x.tolist(), y.tolist(), lag=1, n_bootstraps=50)

    # Then — TE should be clearly positive for coupled process
    assert result["entropy_bits"] > 0.01


def test_step24_transfer_entropy_independent():
    """Independent Gaussians => TE(X->Y) should be approximately zero."""
    # Given
    rng = np.random.default_rng(123)
    n = 500
    x = rng.normal(0, 1, n).tolist()
    y = rng.normal(0, 1, n).tolist()

    # When
    result = compute_transfer_entropy(x, y, lag=1, n_bootstraps=50)

    # Then — TE should be near zero for independent processes
    # Histogram-based TE estimator has positive bias with finite samples,
    # so we use a generous upper bound and verify it is much smaller
    # than the coupled case (which is >> 0.01)
    assert result["entropy_bits"] < 0.5


def test_step24_transfer_entropy_bidirectional():
    """Bidirectional coupling => TE > 0 in both directions."""
    # Given
    rng = np.random.default_rng(42)
    n = 500
    x = np.zeros(n)
    y = np.zeros(n)
    for t in range(1, n):
        x[t] = 0.5 * y[t - 1] + rng.normal(0, 0.5)
        y[t] = 0.5 * x[t - 1] + rng.normal(0, 0.5)

    # When
    result_xy = compute_transfer_entropy(x.tolist(), y.tolist(), lag=1, n_bootstraps=50)
    result_yx = compute_transfer_entropy(y.tolist(), x.tolist(), lag=1, n_bootstraps=50)

    # Then — both directions should show positive TE
    assert result_xy["entropy_bits"] > 0.01
    assert result_yx["entropy_bits"] > 0.01


# ---------------------------------------------------------------------------
# Step 25 — volatility_forecast_service (delegates to GARCH, B1)
# ---------------------------------------------------------------------------
@pytest.mark.xfail(reason="B1: delegates to broken GARCH", strict=False)
def test_step25_volatility_forecast_reasonable():
    """Volatility forecast delegates to garch_forecast which has bug B1."""
    # Given — GARCH(1,1) data with known parameters
    returns, sigma2_true = make_garch_data(omega=0.1, alpha=0.15, beta=0.80,
                                            n=1000, seed=42)
    returns_list = returns.tolist()

    # When — service
    result = forecast_volatility(returns_list, horizon_days=5, model="garch")

    # When — oracle: true unconditional volatility
    true_uncond_var = 0.1 / (1 - 0.15 - 0.80)
    true_uncond_vol = np.sqrt(true_uncond_var)

    # Then — forecast values should be positive and not wildly off
    assert "error" not in result
    for f in result["forecast"]:
        assert f > 0
        # A correct implementation should produce forecasts within 50% of true vol
        assert f == pytest.approx(true_uncond_vol, rel=0.50)
