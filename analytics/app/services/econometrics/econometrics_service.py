"""Econometrics service: ADF unit root test, Granger causality, OLS regression."""

import numpy as np
from typing import Optional
from scipy import stats


def adf_test(series: list[float], max_lags: Optional[int] = None) -> dict:
    """Augmented Dickey-Fuller unit root test.

    Returns test statistic, critical values, p-value, and lag order.
    """
    if len(series) < 10:
        return {"error": "Series must have at least 10 observations"}

    y = np.array(series)
    n = len(y)
    diff = np.diff(y)

    if max_lags is None:
        max_lags = int(12 * (n / 100) ** 0.25)

    best_lag = 0
    best_aic = np.inf

    for lags in range(0, max_lags + 1):
        try:
            y_dep = diff[lags:]
            n_eff = len(y_dep)

            x = np.column_stack([np.ones(n_eff), y[lags : lags + n_eff]])
            for i in range(1, lags + 1):
                x = np.column_stack([x, diff[lags - i : lags - i + n_eff]])

            beta = np.linalg.lstsq(x, y_dep, rcond=None)[0]
            resid = y_dep - x @ beta
            ssr = np.sum(resid ** 2)
            k = x.shape[1]
            aic = n_eff * np.log(ssr / n_eff) + 2 * k

            if aic < best_aic:
                best_aic = aic
                best_lag = lags
        except Exception:
            continue

    y_dep = diff[best_lag:]
    n_eff = len(y_dep)
    x = np.column_stack([np.ones(n_eff), y[best_lag : best_lag + n_eff]])
    for i in range(1, best_lag + 1):
        x = np.column_stack([x, diff[best_lag - i : best_lag - i + n_eff]])

    beta = np.linalg.lstsq(x, y_dep, rcond=None)[0]
    resid = y_dep - x @ beta
    se = np.sqrt(np.diag(np.linalg.inv(x.T @ x) * np.sum(resid ** 2) / (n_eff - x.shape[1])))
    adf_stat = beta[1] / se[1]

    critical_values = {"1%": -3.43, "5%": -2.86, "10%": -2.57}
    p_value = _adf_pvalue(adf_stat, n)

    return {
        "test_statistic": round(float(adf_stat), 4),
        "p_value": round(float(p_value), 4),
        "lags": best_lag,
        "critical_values": critical_values,
        "stationary": bool(adf_stat < critical_values["5%"]),
        "n_observations": n,
    }


def granger_causality(x: list[float], y: list[float], max_lags: int = 10) -> dict:
    """Granger causality test with AIC-based automatic lag selection."""
    if len(x) != len(y):
        return {"error": "Series must have equal length"}
    if len(x) < 15:
        return {"error": "Series must have at least 15 observations"}

    x_arr = np.array(x)
    y_arr = np.array(y)
    n = len(x_arr)

    best_lag = 1
    best_aic = np.inf

    for lag in range(1, min(max_lags + 1, n // 3)):
        try:
            restricted = _granger_restricted(y_arr, lag)
            unrestricted = _granger_unrestricted(y_arr, x_arr, lag)
            resid_r = restricted[1]
            resid_u = unrestricted[1]
            ssr_r = np.sum(resid_r ** 2)
            ssr_u = np.sum(resid_u ** 2)
            n_eff = len(resid_u)
            k_u = unrestricted[0].shape[1]
            aic = n_eff * np.log(ssr_u / n_eff) + 2 * k_u
            if aic < best_aic:
                best_aic = aic
                best_lag = lag
        except Exception:
            continue

    restricted = _granger_restricted(y_arr, best_lag)
    unrestricted = _granger_unrestricted(y_arr, x_arr, best_lag)
    ssr_r = np.sum(restricted[1] ** 2)
    ssr_u = np.sum(unrestricted[1] ** 2)
    n_eff = len(unrestricted[1])
    k_r = restricted[0].shape[1]
    k_u = unrestricted[0].shape[1]

    f_stat = ((ssr_r - ssr_u) / (k_u - k_r)) / (ssr_u / (n_eff - k_u))
    p_value = 1.0 - stats.f.cdf(f_stat, k_u - k_r, n_eff - k_u)

    return {
        "f_statistic": round(float(f_stat), 4),
        "p_value": round(float(p_value), 4),
        "lags": best_lag,
        "causal": bool(p_value < 0.05),
        "n_observations": n,
    }


def ols_regression(y: list[float], x: list[list[float]]) -> dict:
    """OLS regression with standard errors, t-statistics, p-values, and R-squared."""
    y_arr = np.array(y)
    n = len(y_arr)
    k = len(x)

    x_mat = np.column_stack([np.ones(n)] + [np.array(xi) for xi in x])

    beta = np.linalg.lstsq(x_mat, y_arr, rcond=None)[0]
    resid = y_arr - x_mat @ beta
    ssr = np.sum(resid ** 2)
    tss = np.sum((y_arr - np.mean(y_arr)) ** 2)
    r_squared = 1.0 - ssr / tss if tss > 0 else 0.0
    adj_r_squared = 1.0 - (1.0 - r_squared) * (n - 1) / (n - x_mat.shape[1])

    mse = ssr / (n - x_mat.shape[1])
    var_beta = mse * np.linalg.inv(x_mat.T @ x_mat)
    se = np.sqrt(np.diag(var_beta))
    t_stats = beta / se
    p_values = 2.0 * (1.0 - stats.t.cdf(np.abs(t_stats), n - x_mat.shape[1]))

    coefficients = []
    labels = ["intercept"] + [f"x{i+1}" for i in range(k)]
    for i, label in enumerate(labels):
        coefficients.append({
            "name": label,
            "estimate": round(float(beta[i]), 6),
            "std_error": round(float(se[i]), 6),
            "t_statistic": round(float(t_stats[i]), 4),
            "p_value": round(float(p_values[i]), 4),
        })

    return {
        "coefficients": coefficients,
        "r_squared": round(float(r_squared), 4),
        "adjusted_r_squared": round(float(adj_r_squared), 4),
        "n_observations": n,
        "residual_std_error": round(float(np.sqrt(mse)), 6),
    }


def _adf_pvalue(stat: float, n: int = 500) -> float:
    from statsmodels.tsa.stattools import mackinnonp
    return float(mackinnonp(stat, regression="c", N=1, lags=None))


def _granger_restricted(y: np.ndarray, lag: int):
    n = len(y)
    y_dep = y[lag:]
    n_eff = len(y_dep)
    x = np.column_stack([np.ones(n_eff)] + [y[lag - i - 1 : lag - i - 1 + n_eff] for i in range(lag)])
    beta = np.linalg.lstsq(x, y_dep, rcond=None)[0]
    return x, y_dep - x @ beta


def _granger_unrestricted(y: np.ndarray, x_exog: np.ndarray, lag: int):
    n = len(y)
    y_dep = y[lag:]
    n_eff = len(y_dep)
    x = np.column_stack(
        [np.ones(n_eff)]
        + [y[lag - i - 1 : lag - i - 1 + n_eff] for i in range(lag)]
        + [x_exog[lag - i - 1 : lag - i - 1 + n_eff] for i in range(lag)]
    )
    beta = np.linalg.lstsq(x, y_dep, rcond=None)[0]
    return x, y_dep - x @ beta
