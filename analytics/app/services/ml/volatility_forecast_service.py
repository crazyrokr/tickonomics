"""Volatility forecaster: multi-horizon GARCH-based forecast."""

import numpy as np

from app.services.risk.risk_service import garch_forecast


def forecast_volatility(
    returns: list[float],
    horizon_days: int = 5,
    model: str = "garch",
) -> dict:
    if len(returns) < 50:
        return {"error": "At least 50 returns required for volatility forecasting"}

    if horizon_days < 1 or horizon_days > 30:
        return {"error": "horizon_days must be between 1 and 30"}

    gf = garch_forecast(returns, p=1, q=1, horizon=horizon_days)
    if "error" in gf:
        return gf

    r = np.array(returns)
    realized_vol = float(np.std(r[-21:], ddof=1)) * np.sqrt(252) if len(r) >= 21 else gf["annualized_volatility"]

    forecast_values = gf["forecast"]

    baseline_forecast = [gf["conditional_volatility"]] * horizon_days
    mae = float(np.mean(np.abs(np.array(forecast_values) - np.array(baseline_forecast))))

    return {
        "forecast": [round(v, 6) for v in forecast_values],
        "model": model,
        "current_realized_vol": round(realized_vol, 6),
        "mae_vs_baseline": round(mae, 6),
        "horizon_days": horizon_days,
        "n_observations": len(returns),
    }
