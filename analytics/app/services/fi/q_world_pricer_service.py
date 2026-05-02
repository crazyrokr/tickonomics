"""Q-World bond pricer: CIR model fair-value T-Bill yield."""

import numpy as np
from scipy import optimize


def compute_fair_value(
    instrument: str,
    current_yield: float,
    lookback_days: int = 252,
) -> dict:
    if current_yield <= 0:
        return {"error": "current_yield must be positive"}

    tenor_map = {
        "3M_TBILL": 0.25,
        "6M_TBILL": 0.5,
        "1Y_TBILL": 1.0,
        "2Y_NOTE": 2.0,
        "5Y_NOTE": 5.0,
        "10Y_NOTE": 10.0,
    }

    tenor = tenor_map.get(instrument)
    if tenor is None:
        return {"error": f"Unknown instrument: {instrument}. Supported: {list(tenor_map.keys())}"}

    kappa = 0.5
    theta = current_yield * 0.98
    sigma = 0.01
    r0 = current_yield

    b = (1.0 - np.exp(-kappa * tenor)) / kappa
    a = (kappa * theta / (sigma ** 2)) * (
        (kappa + 0.5 * sigma ** 2 / kappa) * tenor
        - b
        - 0.25 * sigma ** 2 * b ** 2 / kappa
    )

    fair_yield = -(a - b * r0) / tenor

    residual = current_yield - fair_yield
    residual_std = 0.02
    dislocated = bool(abs(residual) > 2.0 * residual_std)

    return {
        "fair_value_yield": round(float(fair_yield), 6),
        "residual": round(float(residual), 6),
        "residual_std": round(residual_std, 6),
        "dislocated": dislocated,
        "model": "CIR",
        "instrument": instrument,
        "tenor_years": tenor,
    }


def compute_tbill_greeks(
    yield_level: float = 0.04,
    duration_years: float = 0.25,
) -> dict:
    if yield_level <= 0 or duration_years <= 0:
        return {"error": "yield_level and duration_years must be positive"}

    dv01 = duration_years * 0.0001
    convexity = 0.5 * duration_years ** 2

    rate_delta = -dv01
    rate_gamma = convexity

    return {
        "dv01": round(dv01, 6),
        "convexity": round(convexity, 6),
        "duration": round(duration_years, 6),
        "rate_delta": round(rate_delta, 6),
        "rate_gamma": round(rate_gamma, 6),
    }
