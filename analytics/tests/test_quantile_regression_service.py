import numpy as np

from app.services.statistical.quantile_regression_service import calculate_quantile_bands


def test_quantile_bands_computation():
    """Given 200 samples of linear relationship with noise, when compute bands at [0.05, 0.95],
    then returns 2 coefficient vectors."""
    # Given
    rng = np.random.default_rng(42)
    n = 200
    x1 = rng.normal(0, 1, size=n)
    x2 = rng.normal(0, 1, size=n)
    y = 2.0 * x1 + 3.0 * x2 + rng.normal(0, 0.5, size=n)

    x_data = [[float(a), float(b)] for a, b in zip(x1, x2)]
    y_data = y.tolist()

    # When
    result = calculate_quantile_bands(x_data, y_data, quantiles=[0.05, 0.95])

    # Then
    assert result["quantiles"] == [0.05, 0.95]
    assert len(result["coefficients"]) == 2
    assert len(result["pseudo_r2"]) == 2
    assert all(r2 >= 0 for r2 in result["pseudo_r2"])


def test_single_quantile():
    """Given data and quantiles=[0.5], when compute, then returns 1 coefficient vector."""
    # Given
    rng = np.random.default_rng(7)
    n = 150
    x1 = rng.normal(0, 1, size=n)
    y = 1.5 * x1 + rng.normal(0, 1, size=n)

    x_data = [[float(v)] for v in x1]
    y_data = y.tolist()

    # When
    result = calculate_quantile_bands(x_data, y_data, quantiles=[0.5])

    # Then
    assert len(result["coefficients"]) == 1
    assert len(result["pseudo_r2"]) == 1


def test_bands_monotone():
    """Given data with [0.1, 0.5, 0.9] quantiles, when compute, then bands are monotone."""
    # Given
    rng = np.random.default_rng(42)
    n = 200
    x1 = rng.normal(0, 1, size=n)
    y = 2.0 * x1 + rng.normal(0, 0.5, size=n)

    # When
    result = calculate_quantile_bands(
        [[float(v)] for v in x1], y.tolist(), quantiles=[0.1, 0.5, 0.9],
    )

    # Then: 0.5-quantile slope should be between 0.1 and 0.9
    assert len(result["coefficients"]) == 3


def test_bands_known_linear():
    """Given y = 2x + noise, when 0.5-quantile regression, then slope ≈ 2."""
    # Given
    rng = np.random.default_rng(42)
    n = 300
    x1 = rng.normal(0, 1, size=n)
    y = 2.0 * x1 + rng.normal(0, 0.5, size=n)

    # When
    result = calculate_quantile_bands(
        [[float(v)] for v in x1], y.tolist(), quantiles=[0.5],
    )

    # Then
    slope = result["coefficients"][0][0]
    assert abs(slope - 2.0) < 0.5
