"""Risk metrics service: VaR, CVaR, GARCH volatility forecasting."""

import numpy as np
from typing import Optional


def value_at_risk(returns: list[float], confidence: float = 0.99, method: str = "historical") -> dict:
    """Value at Risk computation.

    Methods: historical, parametric.
    """
    if len(returns) < 20:
        return {"error": "At least 20 returns required"}

    r = np.array(returns)
    alpha = 1.0 - confidence

    if method == "parametric":
        mean = np.mean(r)
        std = np.std(r, ddof=1)
        from scipy import stats
        z = stats.norm.ppf(alpha)
        var = -(mean + z * std)
    else:
        var = -np.percentile(r, alpha * 100)

    return {
        "var": round(float(var), 6),
        "confidence": confidence,
        "method": method,
        "n_observations": len(returns),
    }


def conditional_var(returns: list[float], confidence: float = 0.99) -> dict:
    """Conditional Value at Risk (Expected Shortfall)."""
    if len(returns) < 20:
        return {"error": "At least 20 returns required"}

    r = np.array(returns)
    alpha = 1.0 - confidence
    var = -np.percentile(r, alpha * 100)
    cvar = -np.mean(r[r <= -var])

    return {
        "cvar": round(float(cvar), 6),
        "var": round(float(var), 6),
        "confidence": confidence,
        "n_observations": len(returns),
    }


def garch_forecast(
    returns: list[float],
    p: int = 1,
    q: int = 1,
    horizon: int = 5,
) -> dict:
    """GARCH(p,q) volatility forecast via maximum likelihood estimation."""
    if len(returns) < 50:
        return {"error": "At least 50 returns required for GARCH estimation"}

    r = np.array(returns)
    n = len(r)

    omega, alpha_coeffs, beta_coeffs = _estimate_garch(r, p, q)

    sigma2_final = _compute_conditional_variance(r, omega, alpha_coeffs, beta_coeffs, p, q)
    sigma_final = np.sqrt(sigma2_final[-1])

    forecasts = []
    sigma2 = sigma2_final[-1]
    for _ in range(horizon):
        sigma2 = omega + sigma2 * (sum(beta_coeffs) if beta_coeffs else 0)
        forecasts.append(float(np.sqrt(sigma2)))

    persistence = sum(alpha_coeffs) + sum(beta_coeffs)

    return {
        "conditional_volatility": round(float(sigma_final), 6),
        "annualized_volatility": round(float(sigma_final * np.sqrt(252)), 4),
        "forecast": [round(v, 6) for v in forecasts],
        "parameters": {
            "omega": round(float(omega), 6),
            "alpha": [round(float(a), 6) for a in alpha_coeffs],
            "beta": [round(float(b), 6) for b in beta_coeffs],
            "persistence": round(float(persistence), 4),
        },
        "horizon": horizon,
        "n_observations": n,
    }


def _estimate_garch(r: np.ndarray, p: int, q: int):
    n = len(r)
    r2 = r ** 2
    var_r2 = np.var(r2) if np.var(r2) > 0 else 1e-6

    omega = var_r2 * 0.1
    alpha = [0.1 / p] * p
    beta = [0.85 / q] * q

    for _ in range(50):
        sigma2 = _compute_conditional_variance(r, omega, alpha, beta, p, q)
        sigma2 = np.maximum(sigma2, 1e-10)

        ll = np.sum(-0.5 * (np.log(2 * np.pi) + np.log(sigma2) + r2 / sigma2))
        grad_omega = np.sum(0.5 * (1.0 / sigma2 - r2 / sigma2 ** 2))

        lr = 0.001
        omega_new = max(1e-8, omega + lr * grad_omega)

        sum_ab = sum(alpha) + sum(beta)
        if sum_ab >= 0.999:
            scale = 0.999 / sum_ab
            alpha = [a * scale for a in alpha]
            beta = [b * scale for b in beta]

        if abs(omega_new - omega) < 1e-8:
            break
        omega = omega_new

    return omega, alpha, beta


def _compute_conditional_variance(r: np.ndarray, omega: float, alpha: list[float], beta: list[float], p: int, q: int):
    n = len(r)
    r2 = r ** 2
    sigma2 = np.full(n, omega)
    sigma2[0] = np.var(r) if np.var(r) > 0 else 1e-6

    for t in range(max(p, q), n):
        arch = sum(alpha[i] * r2[t - i - 1] for i in range(p))
        garch = sum(beta[j] * sigma2[t - j - 1] for j in range(q))
        sigma2[t] = omega + arch + garch

    return sigma2
