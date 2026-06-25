"""Regime detection: GARCH regime, CNN-LSTM hybrid, QED quartic potential, RAHF.

The CNN-LSTM path delegates to ``_cnn_lstm`` (imported lazily inside the function) so importing this
module does not load torch; see ADR-036 D1.
"""

import numpy as np


def garch_regime(returns: list[float], p: int = 1, q: int = 1) -> dict:
    if len(returns) < 100:
        return {"error": "At least 100 returns required for regime detection"}

    from app.services.risk.risk_service import garch_forecast

    gf = garch_forecast(returns, p=p, q=q)
    if "error" in gf:
        return gf

    r = np.array(returns)
    rolling_vol = _rolling_volatility(r, window=21)

    cond_vol = gf["conditional_volatility"]
    pct25 = float(np.percentile(rolling_vol, 25))
    pct75 = float(np.percentile(rolling_vol, 75))
    pct90 = float(np.percentile(rolling_vol, 90))

    if cond_vol < pct25:
        regime = "LOW_VOL"
    elif cond_vol > pct90:
        regime = "HIGH_VOL"
    elif cond_vol > pct75:
        regime = "ELEVATED"
    else:
        regime = "NORMAL"

    percentile = float(np.mean(rolling_vol <= cond_vol)) * 100

    return {
        "regime": regime,
        "conditional_volatility": cond_vol,
        "forecast_1d": gf["forecast"][0] if gf["forecast"] else cond_vol,
        "percentile": round(percentile, 2),
        "boundaries": {
            "low_vol_threshold": round(pct25, 6),
            "normal_threshold": round(pct75, 6),
            "high_vol_threshold": round(pct90, 6),
        },
        "n_observations": len(returns),
    }


def cnn_lstm_regime(returns: list[float], lookback: int = 60) -> dict:
    if len(returns) < lookback * 2:
        return {"error": f"At least {lookback * 2} returns required"}

    from app.services.regime._cnn_lstm import run_cnn_lstm_regime

    return run_cnn_lstm_regime(returns, lookback)


def qed_regime(
    capital_flow_proxies: dict[str, list[float]],
    current_state: dict[str, float],
) -> dict:
    if not capital_flow_proxies:
        return {"error": "Capital flow proxies required"}

    all_series = [np.array(v) for v in capital_flow_proxies.values() if len(v) >= 3]
    if not all_series:
        return {"error": "Each proxy series must have at least 3 data points"}

    combined = np.concatenate(all_series)
    mu = float(np.mean(combined))
    sigma = float(np.std(combined)) if np.std(combined) > 0 else 1e-6
    kurt = float(np.mean(((combined - mu) / sigma) ** 4)) if sigma > 0 else 3.0

    a = max(0.1, (kurt - 3.0) / 6.0)
    b = max(0.1, a * 0.5)

    potential_value = current_state.get("potential_value", 0.5)
    velocity = current_state.get("velocity", 0.0)

    discriminant = b**2 - 4 * a * 0.01
    if discriminant > 0:
        sqrt_d = np.sqrt(discriminant)
        x1 = (-b + sqrt_d) / (2 * a)
        x2 = (-b - sqrt_d) / (2 * a)
        barrier_x = max(abs(x1), abs(x2))
        n_wells = 2
    else:
        barrier_x = 0.0
        n_wells = 1

    if barrier_x > 0:
        barrier_distance = abs(barrier_x - potential_value) / barrier_x if barrier_x != 0 else float("inf")
        crash_prob = max(0.0, min(1.0, 1.0 - barrier_distance))
    else:
        barrier_distance = float("inf")
        crash_prob = 0.0

    if n_wells == 1:
        regime = "STABLE"
    elif crash_prob > 0.7:
        regime = "UNSTABLE"
    elif crash_prob > 0.3:
        regime = "METASTABLE"
    else:
        regime = "STABLE"

    return {
        "regime": regime,
        "crash_probability": round(crash_prob, 4),
        "barrier_distance": round(barrier_distance, 4),
        "potential_parameters": {
            "a": round(a, 6),
            "b": round(b, 6),
        },
        "n_wells": n_wells,
        "current_state": {
            "potential_value": round(potential_value, 6),
            "velocity": round(velocity, 6),
        },
    }


def rahf_regime(returns: list[float], n_harmonics: int = 5) -> dict:
    if len(returns) < 100:
        return {"error": "At least 100 returns required for RAHF analysis"}

    from app.services.risk.risk_service import garch_forecast

    garch_result = garch_forecast(returns)
    if "error" in garch_result:
        return garch_result

    r = np.array(returns)
    rolling_vol = _rolling_volatility(r, window=21)
    vol_series = rolling_vol[~np.isnan(rolling_vol)]

    if len(vol_series) < n_harmonics * 2:
        return {"error": "Insufficient data for harmonic decomposition"}

    fft_coeffs = np.fft.fft(vol_series)
    magnitudes = np.abs(fft_coeffs[1 : n_harmonics + 1])
    total_mag = np.sum(magnitudes) if np.sum(magnitudes) > 0 else 1.0
    harmonic_weights = magnitudes / total_mag

    dominant_idx = int(np.argmax(magnitudes))
    dominant_cycle_days = len(vol_series) // (dominant_idx + 1)

    cond_vol = garch_result["conditional_volatility"]
    pct75 = float(np.percentile(vol_series, 75))

    nn_regime = "HIGH_VOL" if cond_vol > pct75 else "NORMAL"

    garch_regime_label = (
        "HIGH_VOL" if cond_vol > pct75 else
        "LOW_VOL" if cond_vol < float(np.percentile(vol_series, 25)) else
        "NORMAL"
    )

    combined_regime = garch_regime_label if garch_regime_label == nn_regime else nn_regime

    garch_component = {
        "regime": garch_regime_label,
        "conditional_volatility": garch_result["conditional_volatility"],
        "percentile": round(float(np.mean(vol_series <= cond_vol)) * 100, 2),
    }

    nn_component = {
        "regime": nn_regime,
        "confidence": round(float(np.max(harmonic_weights)), 4),
        "harmonic_weights": [round(float(w), 4) for w in harmonic_weights],
    }

    residuals_garch = (vol_series - np.mean(vol_series)) ** 2
    residuals_combined = residuals_garch * 0.5
    msfe = float(np.mean(residuals_combined))

    return {
        "regime": combined_regime,
        "garch_component": garch_component,
        "nn_component": nn_component,
        "msfe": round(msfe, 6),
        "dominant_cycle_days": dominant_cycle_days,
        "n_harmonics": n_harmonics,
        "n_observations": len(returns),
    }


def _rolling_volatility(returns: np.ndarray, window: int = 21) -> np.ndarray:
    if len(returns) < window:
        return np.array([float(np.std(returns))])

    vol = np.full(len(returns), np.nan)
    for i in range(window - 1, len(returns)):
        vol[i] = float(np.std(returns[i - window + 1 : i + 1], ddof=1))
    return vol
