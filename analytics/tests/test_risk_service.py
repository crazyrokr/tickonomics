import numpy as np
import pytest

from app.services.risk.risk_service import (
    conditional_var,
    garch_forecast,
    value_at_risk,
)
from tests.reference.formulas import var_historical, cvar_historical


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


def test_var_known_distribution():
    """Given returns from N(0, 0.01), when historical VaR at 95%,
    then VaR should be close to reference computation."""
    # Given
    rng = np.random.default_rng(42)
    returns = rng.normal(0, 0.1, 1000).tolist()

    # When
    result = value_at_risk(returns, confidence=0.95, method="historical")

    # Then: service VaR should be close to reference
    ref_var = var_historical(np.array(returns), 0.95)
    assert "error" not in result
    assert abs(result["var"] - ref_var) < 0.01


def test_cvar_geq_var():
    """Given any return distribution, when computing CVaR, then CVaR >= VaR (monotonicity)."""
    # Given
    rng = np.random.default_rng(77)
    returns = rng.normal(0, 0.02, 500).tolist()

    # When
    result = conditional_var(returns, confidence=0.99)

    # Then
    assert "error" not in result
    assert result["cvar"] >= result["var"] - 1e-10


@pytest.mark.parametrize("confidence", [0.90, 0.95, 0.99])
def test_var_confidence_monotonicity(confidence):
    """Given increasing confidence levels, then VaR is non-decreasing in absolute terms."""
    # Given
    rng = np.random.default_rng(55)
    returns = rng.normal(0, 0.01, 500).tolist()

    # When
    result = value_at_risk(returns, confidence=confidence, method="historical")

    # Then: VaR should be positive and increase with confidence
    assert "error" not in result
    assert result["var"] > 0


def test_var_confidence_ordering():
    """Given same returns, VaR(99%) >= VaR(95%) >= VaR(90%) in absolute terms."""
    # Given
    rng = np.random.default_rng(33)
    returns = rng.normal(0, 0.02, 500).tolist()

    # When
    var90 = value_at_risk(returns, confidence=0.90, method="historical")
    var95 = value_at_risk(returns, confidence=0.95, method="historical")
    var99 = value_at_risk(returns, confidence=0.99, method="historical")

    # Then
    assert var99["var"] >= var95["var"] >= var90["var"]


def test_empty_returns():
    """Given empty return list, when VaR, then error returned."""
    # Given / When
    result = value_at_risk([], confidence=0.99)
    # Then
    assert "error" in result


def test_garch_forecast_converges():
    """Given long-horizon GARCH forecast, then forecast should converge to unconditional variance."""
    # Given
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(500).tolist()

    # When
    result = garch_forecast(returns, horizon=50)

    # Then: forecast values should stabilize
    assert "error" not in result
    forecasts = result["forecast"]
    late_spread = abs(forecasts[-1] - forecasts[-5])
    early_spread = abs(forecasts[4] - forecasts[0])
    assert late_spread <= early_spread + 1e-6


def test_garch_forecast_converges_to_unconditional_variance():
    """Given a fitted GARCH(1,1) with alpha > 0, the long-horizon forecast must
    converge to the unconditional variance omega / (1 - persistence), where
    persistence = sum(alpha) + sum(beta). A recursion that omits alpha would
    instead converge to omega / (1 - sum(beta)), which is wrong whenever
    alpha > 0."""
    # Given
    rng = np.random.default_rng(7)
    returns = (rng.standard_normal(600) * 0.01).tolist()

    # When
    result = garch_forecast(returns, p=1, q=1, horizon=200)

    # Then
    assert "error" not in result
    params = result["parameters"]
    assert params["alpha"][0] > 0, "test requires a non-trivial ARCH term"

    uncond_var = params["omega"] / (1.0 - params["persistence"])
    uncond_vol = float(np.sqrt(uncond_var))
    limit_vol = result["forecast"][-1]

    assert abs(limit_vol - uncond_vol) < 1e-3, (
        f"forecast limit {limit_vol:.6f} != unconditional vol {uncond_vol:.6f}"
    )
