"""Sobol quasi-random Monte Carlo simulation for option pricing."""

import numpy as np
from scipy import stats
from scipy.stats import qmc


def sobol_simulate(
    payoff_type: str = "call",
    s0: float = 100.0,
    k: float = 100.0,
    t: float = 1.0,
    r: float = 0.05,
    sigma: float = 0.2,
    n_paths: int = 1024,
    n_steps: int = 1,
    seed: int | None = None,
) -> dict:
    if s0 <= 0 or k <= 0 or t <= 0 or sigma <= 0:
        return {"error": "s0, k, t, sigma must be positive"}

    if n_paths < 2:
        return {"error": "At least 2 paths required"}

    if payoff_type not in ("call", "put"):
        return {"error": "payoff_type must be 'call' or 'put'"}

    dim = n_steps
    m = int(np.ceil(np.log2(n_paths)))
    actual_paths = 2 ** m

    sampler = qmc.Sobol(d=dim, scramble=True, seed=seed)
    sobol_samples = sampler.random_base2(m)
    z = stats.norm.ppf(sobol_samples)

    dt = t / n_steps
    log_s = np.full(actual_paths, np.log(s0))

    for step in range(n_steps):
        log_s = log_s + (r - 0.5 * sigma ** 2) * dt + sigma * np.sqrt(dt) * z[:, step]

    s_t = np.exp(log_s)

    if payoff_type == "call":
        payoffs = np.maximum(s_t - k, 0.0)
    else:
        payoffs = np.maximum(k - s_t, 0.0)

    discounted = np.exp(-r * t) * payoffs
    price = float(np.mean(discounted))
    std_error = float(np.std(discounted, ddof=1) / np.sqrt(actual_paths))
    ci_lo = price - 1.96 * std_error
    ci_hi = price + 1.96 * std_error

    analytical = _bsm_price(s0, k, t, r, sigma, payoff_type)

    return {
        "price": round(price, 6),
        "std_error": round(std_error, 6),
        "confidence_interval": [round(ci_lo, 6), round(ci_hi, 6)],
        "analytical_price": round(analytical, 6),
        "pricing_error": round(abs(price - analytical), 6),
        "n_paths": actual_paths,
        "payoff_type": payoff_type,
    }


def discrete_correction(
    continuous_price: float,
    n_monitoring: int,
    s0: float = 100.0,
    k: float = 100.0,
    t: float = 1.0,
    r: float = 0.05,
    sigma: float = 0.2,
) -> dict:
    if continuous_price <= 0:
        return {"error": "continuous_price must be positive"}
    if n_monitoring < 1:
        return {"error": "n_monitoring must be at least 1"}
    if s0 <= 0 or k <= 0 or t <= 0 or sigma <= 0:
        return {"error": "s0, k, t, sigma must be positive"}

    beta = 0.5826

    h = np.sqrt(t / n_monitoring)
    correction_factor = beta * sigma * h
    adjusted_barrier = continuous_price + correction_factor

    return {
        "corrected_price": round(adjusted_barrier, 6),
        "continuous_price": round(continuous_price, 6),
        "correction_factor": round(correction_factor, 6),
        "beta": beta,
        "n_monitoring_points": n_monitoring,
        "sampling_interval": round(float(h), 6),
    }


def _bsm_price(s0: float, k: float, t: float, r: float, sigma: float, payoff_type: str) -> float:
    d1 = (np.log(s0 / k) + (r + 0.5 * sigma ** 2) * t) / (sigma * np.sqrt(t))
    d2 = d1 - sigma * np.sqrt(t)
    if payoff_type == "call":
        return float(s0 * stats.norm.cdf(d1) - k * np.exp(-r * t) * stats.norm.cdf(d2))
    return float(k * np.exp(-r * t) * stats.norm.cdf(-d2) - s0 * stats.norm.cdf(-d1))
