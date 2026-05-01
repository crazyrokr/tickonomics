import numpy as np

from app.services.statistical.yield_curve_service import (
    fit_yield_curve,
    interpolate_yield_curve,
)


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
