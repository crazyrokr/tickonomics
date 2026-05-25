import numpy as np

from app.services.drift.drift_service import barrier_hitting_probability, simulate_ito


def test_simulate_ito_terminal_convergence():
    """Given 5000 paths, when simulating arithmetic BM, then terminal mean is near theoretical."""
    result = simulate_ito(
        mu=0.0,
        sigma=1.0,
        initial_value=0.0,
        t_max=1.0,
        n_steps=252,
        n_paths=5000,
        seed=42,
    )

    assert "error" not in result
    assert abs(result["terminal_mean"] - result["theoretical_mean"]) < 0.1
    assert abs(result["terminal_std"] - result["theoretical_std"]) < 0.1


def test_simulate_ito_with_drift():
    """Given positive drift, when simulating, then terminal mean exceeds initial value."""
    result = simulate_ito(
        mu=0.1,
        sigma=0.2,
        initial_value=0.0,
        t_max=10.0,
        n_steps=252,
        n_paths=1000,
        seed=7,
    )

    assert "error" not in result
    assert result["terminal_mean"] > result["theoretical_mean"] - 0.5
    assert result["theoretical_mean"] == 1.0
    assert result["n_paths"] == 1000


def test_simulate_ito_deterministic_with_seed():
    """Given same seed, when simulating twice, then results are identical."""
    r1 = simulate_ito(mu=0.01, sigma=0.1, seed=99, n_steps=50, n_paths=5)
    r2 = simulate_ito(mu=0.01, sigma=0.1, seed=99, n_steps=50, n_paths=5)

    assert r1["terminal_mean"] == r2["terminal_mean"]


def test_barrier_hitting_probability_zero_drift():
    """Given zero drift and barrier far above, when simulating, then hitting probability is low."""
    result = barrier_hitting_probability(
        mu=0.0,
        sigma=0.1,
        initial_value=0.0,
        barrier_level=10.0,
        t_max=1.0,
        n_steps=50,
        n_paths=2000,
        seed=42,
    )

    assert "error" not in result
    assert result["hitting_probability"] < 0.5
    assert result["n_paths_hitting_barrier"] >= 0
    assert result["n_paths"] == 2000


def test_barrier_hitting_probability_high_drift():
    """Given high positive drift toward barrier, when simulating, then hitting probability is high."""
    result = barrier_hitting_probability(
        mu=0.5,
        sigma=0.1,
        initial_value=0.0,
        barrier_level=1.0,
        t_max=10.0,
        n_steps=252,
        n_paths=1000,
        seed=42,
    )

    assert "error" not in result
    assert result["hitting_probability"] > 0.3
    assert result["expected_hitting_time"] is not None or result["hitting_probability"] == 0.0


def test_ito_reproducibility():
    """Given same seed, when simulating twice, then identical paths."""
    # Given / When
    r1 = simulate_ito(mu=0.01, sigma=0.1, seed=99, n_steps=50, n_paths=10)
    r2 = simulate_ito(mu=0.01, sigma=0.1, seed=99, n_steps=50, n_paths=10)

    # Then
    assert r1["terminal_mean"] == r2["terminal_mean"]


def test_ito_positive_initial():
    """Given positive initial value with small noise, when simulating GBM, then paths stay positive."""
    # Given
    result = simulate_ito(
        mu=0.05, sigma=0.01, initial_value=100.0,
        t_max=1.0, n_steps=50, n_paths=500, seed=42,
    )

    # Then
    assert "error" not in result
    assert result["terminal_mean"] > 0


def test_barrier_probability_monotonic():
    """Given closer barrier, when simulating, then higher hitting probability."""
    # Given
    close = barrier_hitting_probability(
        mu=0.0, sigma=0.2, initial_value=0.0, barrier_level=1.0,
        t_max=5.0, n_steps=100, n_paths=2000, seed=42,
    )
    far = barrier_hitting_probability(
        mu=0.0, sigma=0.2, initial_value=0.0, barrier_level=5.0,
        t_max=5.0, n_steps=100, n_paths=2000, seed=42,
    )

    # Then
    assert close["hitting_probability"] > far["hitting_probability"]


def test_barrier_zero_distance():
    """Given barrier equal to initial value, when simulating, then probability is near 1.0."""
    # Given / When
    result = barrier_hitting_probability(
        mu=0.0, sigma=0.01, initial_value=0.0, barrier_level=0.0,
        t_max=1.0, n_steps=50, n_paths=500, seed=42,
    )

    # Then
    assert result["hitting_probability"] > 0.9
