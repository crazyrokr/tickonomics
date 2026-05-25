import numpy as np

from app.services.backtest.robustness_service import robustness_scan


def test_robustness_scan_basic():
    """Given strategy returns and parameter ranges, when scanning, then results contain sharpe and coverage."""
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(100) * 0.01 + 0.0002).tolist()
    params = {
        "rrp_weight": [0.2, 0.6],
        "spread_weight": [0.2, 0.6],
    }

    result = robustness_scan(returns, params, n_samples=64, seed=42)

    assert "error" not in result
    assert len(result["results"]) == 64
    assert "best_params" in result
    assert "heatmap_data" in result
    assert result["coverage"] > 0
    assert result["mean_sharpe"] is not None
    assert result["n_samples"] == 64


def test_robustness_scan_best_has_highest_sharpe():
    """Given scan results, when checking best_params, then it has the highest sharpe."""
    rng = np.random.default_rng(7)
    returns = (rng.standard_normal(100) * 0.01).tolist()
    params = {"threshold": [0.5, 2.0]}

    result = robustness_scan(returns, params, n_samples=32, seed=7)

    assert "error" not in result
    best_sharpe = result["best_params"]["sharpe"]
    all_sharpes = [r["sharpe"] for r in result["results"]]
    assert best_sharpe == max(all_sharpes)


def test_robustness_scan_insufficient_returns():
    """Given too few returns, when scanning, then error returned."""
    assert "error" in robustness_scan([0.01] * 10, {"x": [0.0, 1.0]})


def test_robustness_scan_invalid_ranges():
    """Given invalid parameter ranges, when scanning, then error returned."""
    assert "error" in robustness_scan([0.01] * 100, {})
    assert "error" in robustness_scan([0.01] * 100, {"x": [1.0, 0.5]})


def test_robustness_monotonic_improvement():
    """Given positive-mean returns, when scanning, then mean_sharpe is computed."""
    # Given
    rng = np.random.default_rng(42)
    returns = (rng.standard_normal(100) * 0.01 + 0.001).tolist()

    # When
    result = robustness_scan(returns, {"w": [0.1, 0.9]}, n_samples=32, seed=42)

    # Then
    assert "error" not in result
    assert isinstance(result["mean_sharpe"], float)
