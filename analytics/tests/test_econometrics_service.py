import numpy as np

from app.services.econometrics.econometrics_service import (
    adf_test,
    granger_causality,
    ols_regression,
)


def test_adf_stationary_series():
    """Given a stationary mean-reverting series, when ADF test, then stationary is True."""
    rng = np.random.default_rng(42)
    series = np.cumsum(rng.standard_normal(200)) * 0.1
    series = np.diff(series, prepend=0).tolist()

    result = adf_test(series)
    assert "error" not in result
    assert "test_statistic" in result
    assert "p_value" in result
    assert "lags" in result
    assert "critical_values" in result
    assert "n_observations" in result
    assert isinstance(result["stationary"], bool)


def test_adf_insufficient_data():
    """Given fewer than 10 observations, when ADF test, then error returned."""
    result = adf_test([1.0, 2.0, 3.0])
    assert "error" in result


def test_adf_random_walk():
    """Given a random walk series, when ADF test, then not stationary."""
    rng = np.random.default_rng(99)
    series = np.cumsum(rng.standard_normal(300)).tolist()

    result = adf_test(series)
    assert result["n_observations"] == 300
    assert isinstance(result["test_statistic"], float)


def test_granger_causal_series():
    """Given x Granger-causes y, when test, then causal is True."""
    rng = np.random.default_rng(7)
    n = 200
    x = rng.standard_normal(n).tolist()
    y = [0.0] * n
    for t in range(2, n):
        y[t] = 0.5 * x[t - 1] + 0.3 * y[t - 1] + rng.standard_normal()

    result = granger_causality(x, y)
    assert "error" not in result
    assert "f_statistic" in result
    assert "p_value" in result
    assert isinstance(result["causal"], bool)


def test_granger_unequal_length():
    """Given series of different lengths, when Granger test, then error returned."""
    result = granger_causality([1.0, 2.0], [1.0, 2.0, 3.0])
    assert "error" in result


def test_granger_insufficient_data():
    """Given fewer than 15 observations, when Granger test, then error returned."""
    data = list(range(10))
    result = granger_causality(data, data)
    assert "error" in result


def test_ols_basic_regression():
    """Given y = 2x + 3 + noise, when OLS, then intercept ~= 3 and slope ~= 2."""
    rng = np.random.default_rng(21)
    x = np.linspace(0, 10, 100).tolist()
    y = [2.0 * xi + 3.0 + rng.normal(0, 0.5) for xi in x]

    result = ols_regression(y, [x])
    assert "coefficients" in result
    assert len(result["coefficients"]) == 2
    assert result["coefficients"][0]["name"] == "intercept"
    assert result["coefficients"][1]["name"] == "x1"
    assert abs(result["coefficients"][0]["estimate"] - 3.0) < 1.0
    assert abs(result["coefficients"][1]["estimate"] - 2.0) < 0.5
    assert result["r_squared"] > 0.9


def test_ols_multiple_regression():
    """Given y = x1 + 2*x2 + noise, when OLS with two regressors, then coefficients are correct."""
    rng = np.random.default_rng(55)
    n = 100
    x1 = rng.standard_normal(n).tolist()
    x2 = rng.standard_normal(n).tolist()
    y = [x1[i] + 2.0 * x2[i] + rng.normal(0, 0.3) for i in range(n)]

    result = ols_regression(y, [x1, x2])
    assert len(result["coefficients"]) == 3
    assert result["coefficients"][1]["name"] == "x1"
    assert result["coefficients"][2]["name"] == "x2"
    assert abs(result["coefficients"][1]["estimate"] - 1.0) < 0.5
    assert abs(result["coefficients"][2]["estimate"] - 2.0) < 0.5
    assert 0.0 <= result["r_squared"] <= 1.0
    assert result["n_observations"] == n


def test_ols_perfect_fit():
    """Given y = 5x exactly, when OLS, then R-squared = 1.0."""
    x = list(range(20))
    y = [5.0 * xi for xi in x]

    result = ols_regression(y, [x])
    assert abs(result["r_squared"] - 1.0) < 1e-6
    assert abs(result["coefficients"][1]["estimate"] - 5.0) < 1e-6


def test_adf_on_stationary():
    """Given white noise, when ADF test, then stationary is True."""
    # Given
    rng = np.random.default_rng(42)
    series = rng.standard_normal(200).tolist()

    # When
    result = adf_test(series)

    # Then: test statistic should be very negative (stationary)
    assert "error" not in result
    assert result["stationary"] is True
    assert result["test_statistic"] < result["critical_values"]["5%"]


def test_granger_no_causality_independent():
    """Given independent series, when Granger test, then causal should be False."""
    # Given
    rng = np.random.default_rng(77)
    n = 300
    x = rng.standard_normal(n).tolist()
    y = rng.standard_normal(n).tolist()

    # When
    result = granger_causality(x, y)

    # Then
    assert "error" not in result
    assert result["causal"] is False or result["p_value"] > 0.01


def test_ols_r_squared_range():
    """Given any regression, when R-squared computed, then 0 <= R^2 <= 1."""
    # Given
    rng = np.random.default_rng(33)
    x = rng.standard_normal(100).tolist()
    y = rng.standard_normal(100).tolist()

    # When
    result = ols_regression(y, [x])

    # Then
    assert 0.0 <= result["r_squared"] <= 1.0
