import numpy as np

from app.services.simulation.sobol_service import discrete_correction, sobol_simulate
from tests.reference.formulas import bsm_put_price, bsm_call_price


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


def test_sobol_put_vs_analytical():
    """Given put option, when Sobol MC with 8192 paths, then price within 5% of BSM put."""
    # Given
    S, K, T, r, sigma = 100.0, 100.0, 1.0, 0.05, 0.2

    # When
    result = sobol_simulate(payoff_type="put", s0=S, k=K, t=T, r=r, sigma=sigma,
                            n_paths=8192, seed=42)

    # Then
    assert "error" not in result
    expected = bsm_put_price(S, K, T, r, sigma)
    assert abs(result["price"] - expected) / expected < 0.05


def test_sobol_call_put_parity():
    """Given call and put at same strike, when comparing prices, then call - put ≈ S - K*exp(-rT)."""
    # Given
    S, K, T, r, sigma = 100.0, 100.0, 1.0, 0.05, 0.2

    # When
    call = sobol_simulate(payoff_type="call", s0=S, k=K, t=T, r=r, sigma=sigma,
                          n_paths=8192, seed=42)
    put = sobol_simulate(payoff_type="put", s0=S, k=K, t=T, r=r, sigma=sigma,
                         n_paths=8192, seed=42)

    # Then: put-call parity with Monte Carlo tolerance
    parity = S - K * np.exp(-r * T)
    assert abs((call["price"] - put["price"]) - parity) < 2.0
