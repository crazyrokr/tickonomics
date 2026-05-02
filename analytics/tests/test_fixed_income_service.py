import numpy as np

from app.services.fixed_income.fixed_income_service import (
    convexity,
    macaulay_duration,
    yield_to_maturity,
)


def _bond_cash_flows(face: float, coupon_rate: float, n_years: int, freq: int = 2):
    """Generate cash flows and times for a semi-annual coupon bond."""
    coupon = face * coupon_rate / freq
    cf = [coupon] * (n_years * freq - 1) + [coupon + face]
    times = [i / freq for i in range(1, n_years * freq + 1)]
    return cf, times


def test_duration_benchmark_bond():
    """Given a 5-year 5% semi-annual bond at 5% yield, when duration, then matches expected ~4.49."""
    cf, times = _bond_cash_flows(1000, 0.05, 5)
    result = macaulay_duration(cf, times, 0.05)
    assert "error" not in result
    assert 4.0 < result["macaulay_duration"] < 5.0
    assert result["modified_duration"] < result["macaulay_duration"]
    assert result["n_cash_flows"] == 10


def test_duration_zero_coupon():
    """Given a zero-coupon bond, when duration, then equals maturity."""
    result = macaulay_duration([1000], [5.0], 0.05)
    assert abs(result["macaulay_duration"] - 5.0) < 1e-6


def test_duration_mismatched_lengths():
    """Given mismatched cash_flows and times, when duration, then error."""
    result = macaulay_duration([100, 200], [1.0], 0.05)
    assert "error" in result


def test_duration_empty():
    """Given no cash flows, when duration, then error."""
    result = macaulay_duration([], [], 0.05)
    assert "error" in result


def test_convexity_benchmark_bond():
    """Given a 5-year 5% semi-annual bond at 5% yield, when convexity, then positive."""
    cf, times = _bond_cash_flows(1000, 0.05, 5)
    result = convexity(cf, times, 0.05)
    assert "error" not in result
    assert result["convexity"] > 0
    assert result["price"] > 0


def test_convexity_zero_coupon():
    """Given a zero-coupon bond, when convexity, then matches t*(t+1)/(1+y)^2."""
    result = convexity([1000], [5.0], 0.05)
    expected = 5.0 * 6.0 / (1.05 ** 2)
    assert abs(result["convexity"] - expected) < 1e-3


def test_convexity_mismatched_lengths():
    """Given mismatched inputs, when convexity, then error."""
    result = convexity([100], [1.0, 2.0], 0.05)
    assert "error" in result


def test_ytm_par_bond():
    """Given a bond priced at par, when YTM, then equals coupon rate."""
    cf, times = _bond_cash_flows(1000, 0.05, 5)
    price_at_par = sum(c / (1.05) ** t for c, t in zip(cf, times))
    result = yield_to_maturity(cf, times, price_at_par)
    assert "error" not in result
    assert abs(result["ytm"] - 0.05) < 0.001
    assert result["converged"]


def test_ytm_discount_bond():
    """Given a bond priced below par, when YTM, then YTM > coupon rate."""
    cf, times = _bond_cash_flows(1000, 0.05, 5)
    result = yield_to_maturity(cf, times, 950.0)
    assert "error" not in result
    assert result["ytm"] > 0.05


def test_ytm_premium_bond():
    """Given a bond priced above par, when YTM, then YTM < coupon rate."""
    cf, times = _bond_cash_flows(1000, 0.05, 5)
    result = yield_to_maturity(cf, times, 1050.0)
    assert "error" not in result
    assert result["ytm"] < 0.05


def test_ytm_invalid_price():
    """Given negative price, when YTM, then error."""
    result = yield_to_maturity([100], [1.0], -10.0)
    assert "error" in result


def test_ytm_mismatched_lengths():
    """Given mismatched inputs, when YTM, then error."""
    result = yield_to_maturity([100, 200], [1.0], 100.0)
    assert "error" in result
