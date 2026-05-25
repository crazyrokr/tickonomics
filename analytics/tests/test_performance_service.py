import numpy as np
import pytest

from app.services.performance.performance_service import (
    fama_french_regression,
    sharpe_ratio,
    sortino_ratio,
)
from tests.reference.formulas import sharpe_exact, sortino_exact


def test_sharpe_positive_returns():
    """Given positive excess returns, when Sharpe, then positive ratio."""
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(252) * 0.01 + 0.002).tolist()

    result = sharpe_ratio(returns, risk_free_rate=0.0)
    assert "error" not in result
    assert result["sharpe_ratio"] > 0
    assert result["n_observations"] == 252


def test_sharpe_negative_returns():
    """Given negative excess returns, when Sharpe, then negative ratio."""
    rng = np.random.default_rng(7)
    returns = (rng.standard_normal(252) * 0.01 - 0.002).tolist()

    result = sharpe_ratio(returns)
    assert "error" not in result
    assert result["sharpe_ratio"] < 0


def test_sharpe_insufficient_data():
    """Given 1 return, when Sharpe, then error."""
    result = sharpe_ratio([0.01])
    assert "error" in result


def test_sharpe_zero_std():
    """Given constant returns, when Sharpe, then error (zero std)."""
    result = sharpe_ratio([0.01] * 50)
    assert "error" in result


def test_sortino_basic():
    """Given mixed returns, when Sortino, then ratio computed."""
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(252) * 0.02).tolist()

    result = sortino_ratio(returns, risk_free_rate=0.0)
    assert "error" not in result
    assert "sortino_ratio" in result
    assert result["n_negative"] > 0


def test_sortino_insufficient_data():
    """Given 1 return, when Sortino, then error."""
    result = sortino_ratio([0.01])
    assert "error" in result


def test_sortino_all_positive():
    """Given all positive excess returns, when Sortino, then error (no downside)."""
    result = sortino_ratio([0.01] * 50, risk_free_rate=-0.10)
    assert "error" in result


def test_fama_french_capm():
    """Given returns and market returns only, when FF regression, then CAPM model."""
    rng = np.random.default_rng(42)
    n = 120
    market = rng.standard_normal(n).tolist()
    returns = [0.001 + 1.2 * m + rng.normal(0, 0.01) for m in market]

    result = fama_french_regression(returns, market)
    assert "error" not in result
    assert result["model"] == "Fama-French CAPM"
    assert len(result["coefficients"]) == 2
    assert result["coefficients"][0]["name"] == "alpha"
    assert result["coefficients"][1]["name"] == "market_beta"
    assert abs(result["coefficients"][1]["estimate"] - 1.2) < 0.3


def test_fama_french_three_factor():
    """Given returns, market, SMB, HML, when FF regression, then 3-factor model."""
    rng = np.random.default_rng(55)
    n = 120
    market = rng.standard_normal(n).tolist()
    smb = rng.standard_normal(n).tolist()
    hml = rng.standard_normal(n).tolist()
    returns = [0.001 + 1.0 * market[i] + 0.5 * smb[i] - 0.3 * hml[i] + rng.normal(0, 0.01)
               for i in range(n)]

    result = fama_french_regression(returns, market, smb, hml)
    assert "error" not in result
    assert result["model"] == "Fama-French 3-factor"
    assert len(result["coefficients"]) == 4
    assert abs(result["coefficients"][2]["estimate"] - 0.5) < 0.3
    assert abs(result["coefficients"][3]["estimate"] - (-0.3)) < 0.3
    assert result["r_squared"] > 0.5


def test_fama_french_insufficient_data():
    """Given fewer than 20 observations, when FF, then error."""
    result = fama_french_regression([0.01] * 10, [0.01] * 10)
    assert "error" in result


def test_fama_french_unequal_lengths():
    """Given unequal return/market lengths, when FF, then error."""
    result = fama_french_regression([0.01] * 25, [0.01] * 30)
    assert "error" in result


def test_sharpe_known_values():
    """Given known return series, when computing Sharpe, then matches reference formula."""
    # Given
    rng = np.random.default_rng(42)
    returns = rng.normal(0.001, 0.02, 252)
    rf = 0.0

    # When
    result = sharpe_ratio(returns.tolist(), risk_free_rate=rf, annualize=True)
    ref = sharpe_exact(returns, rf=rf / 252, annualize=True)

    # Then: service Sharpe should be close to reference (within rounding)
    assert "error" not in result
    assert abs(result["sharpe_ratio"] - ref) < 0.5


def test_sharpe_single_return():
    """Given single return, when Sharpe, then error (division by zero in std)."""
    # Given / When
    result = sharpe_ratio([0.05])
    # Then
    assert "error" in result


def test_ff_regression_r_squared_range():
    """Given any FF regression, when R-squared computed, then 0 <= R^2 <= 1."""
    # Given
    rng = np.random.default_rng(77)
    n = 120
    market = rng.standard_normal(n).tolist()
    returns = [rng.normal(0, 0.02) for _ in range(n)]

    # When
    result = fama_french_regression(returns, market)

    # Then
    assert "error" not in result
    assert 0.0 <= result["r_squared"] <= 1.0


def test_ff_regression_beta_near_one():
    """Given y = x + noise, when FF regression, then market beta ≈ 1.0."""
    # Given
    rng = np.random.default_rng(33)
    n = 200
    market = rng.standard_normal(n).tolist()
    returns = [m + rng.normal(0, 0.01) for m in market]

    # When
    result = fama_french_regression(returns, market)

    # Then
    assert "error" not in result
    assert abs(result["coefficients"][1]["estimate"] - 1.0) < 0.2


@pytest.mark.parametrize("annualize", [True, False])
def test_sharpe_annualization_flag(annualize):
    """Given same returns, when toggling annualize, then both compute without error."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(100) * 0.02 + 0.001).tolist()

    # When
    result = sharpe_ratio(returns, annualize=annualize)

    # Then
    assert "error" not in result
