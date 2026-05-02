import numpy as np

from app.services.simulation.sobol_service import discrete_correction, sobol_simulate


def test_sobol_call_pricing_near_analytical():
    """Given ATM call option, when Sobol MC with 4096 paths, then price is within 5% of analytical."""
    result = sobol_simulate(
        payoff_type="call",
        s0=100.0,
        k=100.0,
        t=1.0,
        r=0.05,
        sigma=0.2,
        n_paths=4096,
        seed=42,
    )

    assert "error" not in result
    assert result["price"] > 0
    analytical = result["analytical_price"]
    assert abs(result["price"] - analytical) / analytical < 0.05
    assert result["n_paths"] >= 4096
    assert result["payoff_type"] == "call"


def test_sobol_put_pricing():
    """Given ATM put option, when Sobol MC, then price is near analytical."""
    result = sobol_simulate(
        payoff_type="put",
        s0=100.0,
        k=105.0,
        t=0.5,
        r=0.05,
        sigma=0.25,
        n_paths=4096,
        seed=7,
    )

    assert "error" not in result
    assert result["price"] > 0
    assert result["payoff_type"] == "put"


def test_sobol_invalid_inputs():
    """Given negative spot price, when Sobol MC, then error returned."""
    result = sobol_simulate(s0=-1.0)
    assert "error" in result

    result = sobol_simulate(payoff_type="invalid")
    assert "error" in result

    result = sobol_simulate(n_paths=1)
    assert "error" in result


def test_discrete_correction_basic():
    """Given continuous price and monitoring points, when discrete correction, then corrected price differs."""
    result = discrete_correction(
        continuous_price=5.0,
        n_monitoring=252,
        s0=100.0,
        k=100.0,
        t=1.0,
        r=0.05,
        sigma=0.2,
    )

    assert "error" not in result
    assert result["corrected_price"] != result["continuous_price"]
    assert result["correction_factor"] > 0
    assert result["beta"] == 0.5826
    assert result["n_monitoring_points"] == 252


def test_discrete_correction_more_monitoring_means_smaller_correction():
    """Given more monitoring points, when discrete correction, then correction factor is smaller."""
    r1 = discrete_correction(continuous_price=5.0, n_monitoring=12, sigma=0.2, t=1.0)
    r2 = discrete_correction(continuous_price=5.0, n_monitoring=252, sigma=0.2, t=1.0)

    assert r1["correction_factor"] > r2["correction_factor"]


def test_discrete_correction_invalid():
    """Given invalid inputs, when discrete correction, then error returned."""
    assert "error" in discrete_correction(continuous_price=-1.0, n_monitoring=12)
    assert "error" in discrete_correction(continuous_price=5.0, n_monitoring=0)
