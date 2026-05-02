import numpy as np

from app.services.risk.risk_service import (
    conditional_var,
    garch_forecast,
    value_at_risk,
)


def test_var_historical_method():
    """Given 1000 normal returns, when historical VaR at 99%, then var is positive."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(1000).tolist()

    result = value_at_risk(returns, confidence=0.99, method="historical")
    assert "error" not in result
    assert result["var"] > 0
    assert result["confidence"] == 0.99
    assert result["method"] == "historical"
    assert result["n_observations"] == 1000


def test_var_parametric_method():
    """Given 1000 normal returns, when parametric VaR at 95%, then var is positive."""
    rng = np.random.default_rng(7)
    returns = rng.standard_normal(1000).tolist()

    result = value_at_risk(returns, confidence=0.95, method="parametric")
    assert "error" not in result
    assert result["var"] > 0
    assert result["method"] == "parametric"


def test_var_insufficient_data():
    """Given fewer than 20 returns, when VaR, then error returned."""
    result = value_at_risk([0.01, 0.02, -0.01])
    assert "error" in result


def test_var_parametric_vs_historical():
    """Given same returns, when comparing methods, then both produce positive VaR."""
    rng = np.random.default_rng(12)
    returns = rng.standard_normal(500).tolist()

    hist = value_at_risk(returns, confidence=0.99, method="historical")
    para = value_at_risk(returns, confidence=0.99, method="parametric")
    assert hist["var"] > 0
    assert para["var"] > 0


def test_cvar_basic():
    """Given 500 returns, when CVaR at 99%, then cvar >= var."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(500).tolist()

    result = conditional_var(returns, confidence=0.99)
    assert "error" not in result
    assert result["cvar"] >= result["var"]
    assert result["confidence"] == 0.99


def test_cvar_insufficient_data():
    """Given fewer than 20 returns, when CVaR, then error returned."""
    result = conditional_var([0.01] * 10)
    assert "error" in result


def test_garch_forecast_basic():
    """Given 200 returns, when GARCH(1,1) with 5-day horizon, then forecast has 5 values."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(200).tolist()

    result = garch_forecast(returns, p=1, q=1, horizon=5)
    assert "error" not in result
    assert len(result["forecast"]) == 5
    assert result["conditional_volatility"] > 0
    assert result["annualized_volatility"] > 0
    assert "parameters" in result
    assert "omega" in result["parameters"]
    assert len(result["parameters"]["alpha"]) == 1
    assert len(result["parameters"]["beta"]) == 1
    assert 0 < result["parameters"]["persistence"] < 1.0


def test_garch_insufficient_data():
    """Given fewer than 50 returns, when GARCH, then error returned."""
    result = garch_forecast(list(range(30)))
    assert "error" in result


def test_garch_persistence_near_one():
    """Given highly volatile returns, when GARCH, then persistence is near 1."""
    rng = np.random.default_rng(88)
    returns = (rng.standard_normal(300) * 0.05).tolist()

    result = garch_forecast(returns, p=1, q=1)
    assert result["parameters"]["persistence"] < 1.0
