import numpy as np

from app.services.ml.volatility_forecast_service import forecast_volatility


def test_volatility_forecast_basic():
    """Given 200 returns, when forecasting 5-day volatility, then 5 forecast values returned."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(200).tolist()

    result = forecast_volatility(returns, horizon_days=5)

    assert "error" not in result
    assert len(result["forecast"]) == 5
    assert all(v > 0 for v in result["forecast"])
    assert result["horizon_days"] == 5
    assert result["model"] == "garch"
    assert result["n_observations"] == 200


def test_volatility_forecast_insufficient_data():
    """Given fewer than 50 returns, when forecasting, then error returned."""
    assert "error" in forecast_volatility([0.01] * 30, horizon_days=5)


def test_volatility_forecast_invalid_horizon():
    """Given invalid horizon, when forecasting, then error returned."""
    assert "error" in forecast_volatility([0.01] * 100, horizon_days=0)
    assert "error" in forecast_volatility([0.01] * 100, horizon_days=50)
