"""Fixed income analytics: duration, convexity, yield-to-maturity."""

import numpy as np


def macaulay_duration(cash_flows: list[float], times: list[float], yield_rate: float) -> dict:
    if len(cash_flows) != len(times):
        return {"error": "cash_flows and times must have equal length"}
    if len(cash_flows) == 0:
        return {"error": "At least one cash flow required"}
    if yield_rate <= -1.0:
        return {"error": "Yield must be greater than -1"}

    cf = np.array(cash_flows)
    t = np.array(times)
    discount_factors = (1 + yield_rate) ** t
    pv_cf = cf / discount_factors
    price = np.sum(pv_cf)

    if price <= 0:
        return {"error": "Price is non-positive with given inputs"}

    duration = np.sum(t * pv_cf) / price
    return {
        "macaulay_duration": round(float(duration), 6),
        "modified_duration": round(float(duration / (1 + yield_rate)), 6),
        "price": round(float(price), 6),
        "n_cash_flows": len(cash_flows),
    }


def convexity(cash_flows: list[float], times: list[float], yield_rate: float) -> dict:
    if len(cash_flows) != len(times):
        return {"error": "cash_flows and times must have equal length"}
    if len(cash_flows) == 0:
        return {"error": "At least one cash flow required"}
    if yield_rate <= -1.0:
        return {"error": "Yield must be greater than -1"}

    cf = np.array(cash_flows)
    t = np.array(times)
    discount_factors = (1 + yield_rate) ** t
    pv_cf = cf / discount_factors
    price = np.sum(pv_cf)

    if price <= 0:
        return {"error": "Price is non-positive with given inputs"}

    conv = np.sum(t * (t + 1) * pv_cf) / (price * (1 + yield_rate) ** 2)
    return {
        "convexity": round(float(conv), 6),
        "price": round(float(price), 6),
        "modified_duration": round(float(np.sum(t * pv_cf) / price / (1 + yield_rate)), 6),
        "n_cash_flows": len(cash_flows),
    }


def yield_to_maturity(
    cash_flows: list[float],
    times: list[float],
    price: float,
    guess: float = 0.05,
    max_iterations: int = 200,
    tolerance: float = 1e-10,
) -> dict:
    if len(cash_flows) != len(times):
        return {"error": "cash_flows and times must have equal length"}
    if len(cash_flows) == 0:
        return {"error": "At least one cash flow required"}
    if price <= 0:
        return {"error": "Price must be positive"}

    cf = np.array(cash_flows)
    t = np.array(times)

    y = guess
    for _ in range(max_iterations):
        discount_factors = (1 + y) ** t
        pv = cf / discount_factors
        f = np.sum(pv) - price

        df = -np.sum(t * cf / discount_factors / (1 + y))
        if abs(df) < 1e-15:
            break

        step = f / df
        y = y - step

        if abs(step) < tolerance:
            break

    if y <= -1.0:
        return {"error": "YTM converged to invalid value <= -100%"}

    return {
        "ytm": round(float(y), 8),
        "price_input": price,
        "converged": abs(step) < tolerance,
        "n_cash_flows": len(cash_flows),
    }
