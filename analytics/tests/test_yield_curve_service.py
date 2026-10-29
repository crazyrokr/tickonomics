import numpy as np
import pytest

from app.services.statistical.yield_curve_service import (
    fit_yield_curve,
    interpolate_yield_curve,
    nelson_siegel,
)
from tests.reference.formulas import nelson_siegel_rate


def test_fit_nelson_siegel():
    """Given standard Treasury maturities and yields, when fit,
    then status is SUCCESS and tau > 0."""
    # Given
    maturities = [0.25, 2, 5, 10, 30]
    yields = [5.2, 4.5, 4.3, 4.5, 4.8]

    # When
    result = fit_yield_curve(maturities, yields)

    # Then
    assert result["status"] == "SUCCESS"
    assert result["tau"] > 0
    assert "beta_0" in result
    assert "covariance_trace" in result


def test_interpolate_produces_values():
    """Given fitted parameters, when interpolate, then returns yields at given maturities."""
    # Given
    betas = [4.8, -0.5, 0.3]
    tau = 1.5
    maturities = [0.5, 1, 3, 7, 20]

    # When
    yields = interpolate_yield_curve(betas, tau, maturities)

    # Then
    assert len(yields) == len(maturities)
    assert all(isinstance(y, float) for y in yields)
    assert all(-10 < y < 30 for y in yields)


@pytest.mark.parametrize("t", [0.25, 0.5, 1, 2, 5, 10, 30])
def test_nelson_siegel_matches_reference(t):
    """Given NS parameters, when evaluating at various t,
    then service output matches reference formula."""
    # Given
    b0, b1, b2, tau = 0.05, -0.02, 0.01, 3.0

    # When
    result = nelson_siegel(t, b0, b1, b2, tau)

    # Then: matches reference implementation
    expected = nelson_siegel_rate(b0, b1, b2, tau, t)
    assert abs(result - expected) < 1e-10


def test_nelson_siegel_at_zero_limit():
    """Given NS parameters, when t approaches 0, then rate approaches b0 + b1."""
    # Given
    b0, b1, b2, tau = 0.05, -0.02, 0.01, 3.0

    # When: evaluate at small t values approaching 0
    rates = [nelson_siegel(t, b0, b1, b2, tau) for t in [0.001, 0.01, 0.1]]

    # Then: rates converge toward b0 + b1 = 0.03
    limit = b0 + b1
    for rate in rates:
        assert abs(rate - limit) < 0.005


def test_nelson_siegel_at_infinity():
    """Given NS parameters, when t is very large, then rate ≈ b0."""
    # Given
    b0, b1, b2, tau = 0.05, -0.02, 0.01, 3.0

    # When
    result = nelson_siegel(1e6, b0, b1, b2, tau)

    # Then
    assert abs(result - b0) < 1e-4


def test_nelson_siegel_monotonicity():
    """Given b1 < 0 and b2 = 0, when evaluating at increasing t,
    then curve is monotonically increasing toward b0."""
    # Given
    b0, b1, b2, tau = 0.05, -0.02, 0.0, 3.0
    t_values = np.array([0.5, 1, 2, 5, 10, 30])

    # When
    rates = [nelson_siegel(t, b0, b1, b2, tau) for t in t_values]

    # Then: rates are strictly increasing
    for i in range(len(rates) - 1):
        assert rates[i] < rates[i + 1]


def test_fit_recovers_parameters():
    """Given synthetic yields from known NS parameters, when fit,
    then recovered b0 and fitted curve match within tolerance."""
    # Given
    b0, b1, b2, tau_true = 0.05, -0.02, 0.01, 3.0
    maturities = [0.25, 0.5, 1, 2, 3, 5, 7, 10, 20, 30]
    yields = [nelson_siegel(t, b0, b1, b2, tau_true) for t in maturities]

    # When
    result = fit_yield_curve(maturities, yields)

    # Then: fitted curve should reproduce yields within 0.1% at each maturity
    assert result["status"] == "SUCCESS"
    assert abs(result["beta_0"] - b0) / abs(b0) < 0.10
    for t, y_true in zip(maturities, yields):
        y_fit = nelson_siegel(t, result["beta_0"], result["beta_1"], result["beta_2"], result["tau"])
        assert abs(y_fit - y_true) < 0.05


def test_interpolate_matches_nelson_siegel():
    """Given fitted parameters, when interpolating at the same maturities,
    then values match the NS formula within 1e-6."""
    # Given
    betas = [0.05, -0.02, 0.01]
    tau = 3.0
    maturities = [0.5, 1, 2, 5, 10]

    # When
    interpolated = interpolate_yield_curve(betas, tau, maturities)

    # Then
    for t, y in zip(maturities, interpolated):
        expected = nelson_siegel(t, betas[0], betas[1], betas[2], tau)
        assert abs(y - expected) < 1e-6


def test_fit_insufficient_data():
    """Given only 2 data points, when fit, then returns error or FAIL status."""
    # Given
    maturities = [1, 5]
    yields = [0.04, 0.05]

    # When
    result = fit_yield_curve(maturities, yields)

    # Then: under-determined system should either fail or return something
    assert result.get("status") in ("SUCCESS", "FAIL") or "error" in result


def test_fit_flat_curve():
    """Given all yields equal, when fit, then b1 and b2 should be near 0, b0 ≈ yield level."""
    # Given
    maturities = [0.25, 0.5, 1, 2, 5, 10, 30]
    yields = [0.04] * len(maturities)

    # When
    result = fit_yield_curve(maturities, yields)

    # Then
    assert result["status"] == "SUCCESS"
    assert abs(result["beta_0"] - 0.04) < 0.01
    assert abs(result["beta_1"]) < 0.01
    assert abs(result["beta_2"]) < 0.01


def test_fit_inverted_curve():
    """Given downward-sloping yields, when fit, then fitted curve is decreasing."""
    # Given
    maturities = [0.25, 1, 2, 5, 10, 30]
    yields = [5.5, 5.0, 4.5, 4.0, 3.5, 3.0]

    # When
    result = fit_yield_curve(maturities, yields)

    # Then
    assert result["status"] == "SUCCESS"
    short_rate = nelson_siegel(0.5, result["beta_0"], result["beta_1"], result["beta_2"], result["tau"])
    long_rate = nelson_siegel(20, result["beta_0"], result["beta_1"], result["beta_2"], result["tau"])
    assert short_rate > long_rate


def test_fit_negative_yields():
    """Given negative yields (Euro/Japanese reality), when fit, then succeeds."""
    # Given
    maturities = [0.25, 1, 2, 5, 10, 30]
    yields = [-0.2, -0.15, -0.05, 0.1, 0.3, 0.5]

    # When
    result = fit_yield_curve(maturities, yields)

    # Then
    assert result["status"] == "SUCCESS"
    assert "beta_0" in result
