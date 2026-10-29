import numpy as np
import pytest

from app.services.statistical.macro_shock_service import compute_impulse_response


def test_impulse_response_computation():
    """Given 100 time steps of 2-variable data, when compute impulse response with 5 steps,
    then returns horizon [0..4] and response paths."""
    # Given
    rng = np.random.default_rng(42)
    n = 100
    columns = ["gdp", "cpi"]
    data = rng.normal(0, 1, size=(n, 2)).tolist()

    # When
    result = compute_impulse_response(columns, data, steps=5)

    # Then
    assert "horizon" in result
    n_points = len(result["horizon"])
    assert n_points >= 5
    assert "gdp" in result["response_paths"]
    assert "cpi" in result["response_paths"]
    assert len(result["response_paths"]["gdp"]) == n_points
    assert result["shock_std_dev"] == 1.0


def test_insufficient_data():
    """Given 3 time steps, when compute, then returns error."""
    # Given
    columns = ["gdp", "cpi"]
    data = [[1.0, 2.0], [3.0, 4.0], [5.0, 6.0]]

    # When
    result = compute_impulse_response(columns, data, steps=5)

    # Then
    assert "error" in result


@pytest.mark.parametrize("steps", [1, 5, 10])
def test_impulse_response_shape(steps):
    """Given valid data, when computing IRF, then output has steps+1 entries per column."""
    # Given
    rng = np.random.default_rng(123)
    columns = ["gdp", "cpi"]
    data = rng.normal(0, 1, size=(100, 2)).tolist()

    # When
    result = compute_impulse_response(columns, data, steps=steps)

    # Then
    assert "error" not in result
    assert len(result["horizon"]) == steps + 1
    for col in columns:
        assert len(result["response_paths"][col]) == steps + 1


def test_impulse_response_diagonal_positive():
    """Given 2-variable VAR, when computing IRF,
    then own-shock response at step 0 should be positive for the shocked variable."""
    # Given
    rng = np.random.default_rng(99)
    columns = ["gdp", "cpi"]
    data = rng.normal(0, 1, size=(150, 2)).tolist()

    # When
    result = compute_impulse_response(columns, data, steps=5)

    # Then: first variable's own-shock response at step 0 should be non-negative
    assert "error" not in result
    assert result["response_paths"]["gdp"][0] >= 0


def test_impulse_response_decays():
    """Given stationary VAR, when computing IRF over 20 steps,
    then response magnitude should generally decay."""
    # Given
    rng = np.random.default_rng(7)
    columns = ["gdp", "cpi"]
    data = rng.normal(0, 1, size=(200, 2)).tolist()

    # When
    result = compute_impulse_response(columns, data, steps=20)

    # Then: later responses should be smaller than early ones on average
    assert "error" not in result
    gdp_response = np.array(result["response_paths"]["gdp"])
    early = np.abs(gdp_response[:5])
    late = np.abs(gdp_response[-5:])
    assert np.mean(early) >= np.mean(late)


def test_impulse_response_single_variable():
    """Given 1-variable data, when computing IRF, then returns error (VAR requires 2+)."""
    # Given
    rng = np.random.default_rng(42)
    columns = ["gdp"]
    data = rng.normal(0, 1, size=(100, 1)).tolist()

    # When
    result = compute_impulse_response(columns, data, steps=5)

    # Then: VAR requires at least 2 variables
    assert "error" in result
