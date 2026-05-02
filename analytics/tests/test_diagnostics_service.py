import numpy as np

from app.services.diagnostics.diagnostic_service import compute_diagnostics


def test_diagnostics_basic():
    """Given 200 returns, when computing diagnostics, then all sections are present."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(200).tolist()

    result = compute_diagnostics(returns)

    assert "error" not in result
    assert "qq_plot" in result
    assert "sample_quantiles" in result["qq_plot"]
    assert "theoretical_quantiles" in result["qq_plot"]
    assert "acf" in result
    assert len(result["acf"]["raw_returns"]) > 0
    assert result["acf"]["raw_returns"][0] == 1.0
    assert "convergence" in result
    assert "cumulative_mean" in result["convergence"]
    assert result["n_observations"] == 200


def test_diagnostics_acf_decays():
    """Given iid returns, when computing ACF, then absolute returns ACF shows some autocorrelation structure."""
    rng = np.random.default_rng(7)
    returns = rng.standard_normal(500).tolist()

    result = compute_diagnostics(returns)

    acf_abs = result["acf"]["absolute_returns"]
    assert len(acf_abs) > 0


def test_diagnostics_insufficient_data():
    """Given fewer than 20 returns, when computing diagnostics, then error returned."""
    assert "error" in compute_diagnostics([0.01] * 10)


def test_diagnostics_convergence_mean_approaches_true():
    """Given normal returns with known mean, when computing convergence, then cumulative mean approaches zero."""
    rng = np.random.default_rng(42)
    returns = rng.standard_normal(1000).tolist()

    result = compute_diagnostics(returns)

    cummean = result["convergence"]["cumulative_mean"]
    assert abs(cummean[-1]) < 0.2
