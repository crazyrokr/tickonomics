import numpy as np

from app.services.liquidity.amihud_service import compute_amihud
from app.services.liquidity.comovement_pca_service import compute_comovement_factor
from app.services.liquidity.strategic_runs_service import detect_strategic_runs


def test_comovement_factor_basic():
    """Given correlated spread matrix, when computing comovement, then factor is between 0 and 1."""
    rng = np.random.default_rng(42)
    base = rng.standard_normal(100)
    spreads = np.column_stack([base + rng.standard_normal(100) * 0.1 for _ in range(5)])

    result = compute_comovement_factor(spreads.tolist())

    assert "error" not in result
    assert 0 < result["comovement_factor"] <= 1.0
    assert result["pc1_variance_explained"] > 0
    assert result["n_symbols"] == 5


def test_comovement_factor_uncorrelated():
    """Given correlated vs uncorrelated spreads, when computing comovement, then correlated has higher PC1."""
    rng = np.random.default_rng(42)
    common = rng.standard_normal(100)
    correlated = np.column_stack([common + rng.standard_normal(100) * 0.05 for _ in range(5)])
    uncorrelated = rng.standard_normal((100, 5))

    r_corr = compute_comovement_factor(correlated.tolist())
    r_uncorr = compute_comovement_factor(uncorrelated.tolist())

    assert r_corr["pc1_variance_explained"] > r_uncorr["pc1_variance_explained"]


def test_comovement_insufficient_data():
    """Given too few rows, when computing comovement, then error returned."""
    assert "error" in compute_comovement_factor([[1.0, 2.0]])
    assert "error" in compute_comovement_factor([[1.0]])


def test_amihud_basic():
    """Given returns and volumes, when computing Amihud, then measure is positive."""
    result = compute_amihud(
        returns=[0.01, -0.02, 0.03, -0.01, 0.02],
        dollar_volumes=[5000000, 4500000, 6000000, 5500000, 4800000],
    )

    assert "error" not in result
    assert result["amihud_measure"] > 0
    assert result["normalized_measure"] > 0
    assert result["n_observations"] == 5


def test_amihud_higher_impact_with_smaller_volume():
    """Given smaller volumes, when computing Amihud, then measure is larger."""
    r1 = compute_amihud([0.01, -0.02], [1000000, 1000000])
    r2 = compute_amihud([0.01, -0.02], [100000000, 100000000])

    assert r1["amihud_measure"] > r2["amihud_measure"]


def test_amihud_zero_volumes():
    """Given all zero volumes, when computing Amihud, then error returned."""
    assert "error" in compute_amihud([0.01, 0.02], [0.0, 0.0])


def test_amihud_mismatched_lengths():
    """Given mismatched lengths, when computing Amihud, then error returned."""
    assert "error" in compute_amihud([0.01, 0.02], [1000000])


def test_strategic_runs_basic():
    """Given price/volume data, when detecting runs, then transition matrix is valid."""
    rng = np.random.default_rng(42)
    n = 100
    prices = (100.0 + np.cumsum(rng.standard_normal(n) * 0.5)).tolist()
    volumes = (rng.uniform(1000, 10000, n)).tolist()

    result = detect_strategic_runs(prices, volumes, window=10)

    assert "error" not in result
    assert result["current_state"] in ("passive", "aggressive")
    tm = result["transition_matrix"]
    pp = tm["passive_to_passive"]
    pa = tm["passive_to_aggressive"]
    assert abs(pp + pa - 1.0) < 0.02
    assert result["run_count_20obs"] >= 0


def test_strategic_runs_insufficient_data():
    """Given too few prices, when detecting runs, then error returned."""
    assert "error" in detect_strategic_runs([1.0, 2.0], [100.0, 200.0], window=10)


def test_strategic_runs_mismatched_lengths():
    """Given mismatched lengths, when detecting runs, then error returned."""
    assert "error" in detect_strategic_runs([1.0, 2.0, 3.0], [100.0], window=1)
