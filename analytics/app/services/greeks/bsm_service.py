"""BSM Greeks calculation and GEX aggregation for options chain data."""

import numpy as np
from scipy import stats


def bsm_greeks(
    spot_price: float,
    strike_price: float,
    time_to_expiry: float,
    risk_free_rate: float,
    implied_volatility: float,
    option_type: str = "call",
) -> dict:
    if spot_price <= 0 or strike_price <= 0 or time_to_expiry <= 0 or implied_volatility <= 0:
        return {"error": "spot_price, strike_price, time_to_expiry, implied_volatility must be positive"}

    if option_type not in ("call", "put"):
        return {"error": "option_type must be 'call' or 'put'"}

    s, k, t, r, v = spot_price, strike_price, time_to_expiry, risk_free_rate, implied_volatility
    sqrt_t = np.sqrt(t)

    d1 = (np.log(s / k) + (r + 0.5 * v ** 2) * t) / (v * sqrt_t)
    d2 = d1 - v * sqrt_t

    nd1 = stats.norm.cdf(d1)
    nd2 = stats.norm.cdf(d2)
    npd1 = stats.norm.pdf(d1)

    gamma = npd1 / (s * v * sqrt_t)
    vega = s * npd1 * sqrt_t / 100.0
    rho_sign = 1.0 if option_type == "call" else -1.0

    if option_type == "call":
        delta = nd1
        theta = (-s * npd1 * v / (2 * sqrt_t) - r * k * np.exp(-r * t) * nd2) / 365.0
        price = float(s * nd1 - k * np.exp(-r * t) * nd2)
        rho = rho_sign * k * t * np.exp(-r * t) * nd2 / 100.0
    else:
        delta = nd1 - 1.0
        theta = (-s * npd1 * v / (2 * sqrt_t) + r * k * np.exp(-r * t) * stats.norm.cdf(-d2)) / 365.0
        price = float(k * np.exp(-r * t) * stats.norm.cdf(-d2) - s * stats.norm.cdf(-d1))
        rho = rho_sign * k * t * np.exp(-r * t) * stats.norm.cdf(-d2) / 100.0

    return {
        "delta": round(float(delta), 6),
        "gamma": round(float(gamma), 6),
        "theta": round(float(theta), 6),
        "vega": round(float(vega), 6),
        "rho": round(float(rho), 6),
        "price": round(price, 6),
        "option_type": option_type,
    }


def aggregate_gex(positions: list[dict]) -> dict:
    if not positions:
        return {"error": "No positions provided"}

    total_call_gamma = 0.0
    total_put_gamma = 0.0
    gex_by_strike = {}

    for pos in positions:
        s = pos.get("spot_price", 0.0)
        k = pos.get("strike_price", 0.0)
        t = pos.get("time_to_expiry", 0.0)
        r = pos.get("risk_free_rate", 0.05)
        v = pos.get("implied_volatility", 0.0)
        opt_type = pos.get("option_type", "call")
        qty = pos.get("quantity", 0.0)
        oi = pos.get("open_interest", 0.0)

        if s <= 0 or k <= 0 or t <= 0 or v <= 0:
            continue

        sqrt_t = np.sqrt(t)
        d1 = (np.log(s / k) + (r + 0.5 * v ** 2) * t) / (v * sqrt_t)
        gamma = float(stats.norm.pdf(d1) / (s * v * sqrt_t))

        dollar_gamma = gamma * s * s * 0.01

        if opt_type == "call":
            exposure = dollar_gamma * (qty + oi)
            total_call_gamma += exposure
        else:
            exposure = -dollar_gamma * (qty + oi)
            total_put_gamma += exposure

        strike_key = str(k)
        gex_by_strike[strike_key] = gex_by_strike.get(strike_key, 0.0) + exposure

    net_gex = total_call_gamma + total_put_gamma

    sorted_strikes = sorted(gex_by_strike.keys(), key=lambda x: float(x))
    flip_point = None
    for i in range(len(sorted_strikes) - 1):
        v1 = gex_by_strike[sorted_strikes[i]]
        v2 = gex_by_strike[sorted_strikes[i + 1]]
        if (v1 < 0 and v2 > 0) or (v1 > 0 and v2 < 0):
            denom = abs(v1) + abs(v2)
            if denom > 0:
                flip_point = round(float(sorted_strikes[i]) + abs(v1) / denom * (float(sorted_strikes[i + 1]) - float(sorted_strikes[i])), 2)
            break

    dealer_position_bias = "long_gamma" if net_gex > 0 else "short_gamma"

    return {
        "net_gex": round(net_gex, 2),
        "total_call_gamma": round(total_call_gamma, 2),
        "total_put_gamma": round(total_put_gamma, 2),
        "gex_by_strike": {k: round(v, 2) for k, v in gex_by_strike.items()},
        "flip_point": flip_point,
        "dealer_position_bias": dealer_position_bias,
        "n_positions": len(positions),
    }
