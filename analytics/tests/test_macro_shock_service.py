import numpy as np

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
