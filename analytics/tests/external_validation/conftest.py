"""Shared tolerances and helpers for external validation tests."""

import math

import numpy as np
from scipy.special import erfc

# Standard tolerances
TOL_EXACT = 1e-10
TOL_HIGH = 1e-6
TOL_MEDIUM = 1e-4
TOL_LOW = 0.01
TOL_STATISTICAL = 0.05


def norm_cdf_erfc(x: float) -> float:
    """Standard normal CDF using math.erfc — independent of scipy.stats.norm."""
    return 0.5 * erfc(-x / math.sqrt(2))


def norm_pdf_erfc(x: float) -> float:
    """Standard normal PDF using math.exp/math.sqrt — independent of scipy.stats.norm."""
    return math.exp(-0.5 * x * x) / math.sqrt(2 * math.pi)


def bsm_price_erfc(S: float, K: float, T: float, r: float, sigma: float,
                   option_type: str = "call") -> float:
    """BSM price using erfc-based N(x) — independent of scipy.stats."""
    if T <= 0:
        if option_type == "call":
            return max(S - K, 0.0)
        return max(K - S, 0.0)
    d1 = (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))
    d2 = d1 - sigma * math.sqrt(T)
    if option_type == "call":
        return S * norm_cdf_erfc(d1) - K * math.exp(-r * T) * norm_cdf_erfc(d2)
    return K * math.exp(-r * T) * norm_cdf_erfc(-d2) - S * norm_cdf_erfc(-d1)


def bsm_delta_erfc(S: float, K: float, T: float, r: float, sigma: float,
                   option_type: str = "call") -> float:
    if T <= 0:
        if option_type == "call":
            return 1.0 if S > K else 0.0
        return -1.0 if S < K else 0.0
    d1 = (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))
    if option_type == "call":
        return norm_cdf_erfc(d1)
    return norm_cdf_erfc(d1) - 1.0


def bsm_gamma_erfc(S: float, K: float, T: float, r: float, sigma: float) -> float:
    if T <= 0:
        return 0.0
    d1 = (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))
    return norm_pdf_erfc(d1) / (S * sigma * math.sqrt(T))


def make_garch_data(omega: float, alpha: float, beta: float, n: int = 1000,
                    seed: int = 42) -> tuple[np.ndarray, np.ndarray]:
    """Simulate GARCH(1,1) data with known parameters."""
    rng = np.random.default_rng(seed)
    r = np.zeros(n)
    sigma2 = np.full(n, omega / (1 - alpha - beta))
    r[0] = rng.normal(0, np.sqrt(sigma2[0]))
    for t in range(1, n):
        sigma2[t] = omega + alpha * r[t - 1] ** 2 + beta * sigma2[t - 1]
        r[t] = rng.normal(0, np.sqrt(sigma2[t]))
    return r, sigma2
