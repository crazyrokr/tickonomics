import numpy as np

from app.services.climate.climate_service import simulate_climate


def test_simulate_climate_returns_valid_structure():
    """Given default parameters, when simulating, then all expected fields are present."""
    result = simulate_climate(n_steps=50, n_paths=20, seed=42)

    assert "error" not in result
    assert "projected_liquidity_impact" in result
    assert "energy_mix_shift" in result
    assert "fossil_reduction" in result["energy_mix_shift"]
    assert "renewable_increase" in result["energy_mix_shift"]
    assert "nuclear_stable" in result["energy_mix_shift"]
    assert result["n_paths"] == 20
    assert result["n_steps"] == 50


def test_simulate_climate_energy_shift_sums_within_range():
    """Given policy parameters, when simulating, then energy shifts are non-negative and bounded."""
    result = simulate_climate(adaptation_finance_increase=0.2, carbon_tax=100, n_steps=50, n_paths=10, seed=7)

    shift = result["energy_mix_shift"]
    assert shift["fossil_reduction"] >= 0
    assert shift["renewable_increase"] >= 0
    assert shift["nuclear_stable"] >= 0
    assert shift["fossil_reduction"] <= 0.5


def test_simulate_climate_deterministic_with_seed():
    """Given same seed, when simulating twice, then results are identical."""
    r1 = simulate_climate(n_steps=30, n_paths=5, seed=123)
    r2 = simulate_climate(n_steps=30, n_paths=5, seed=123)

    assert r1["mean_path"] == r2["mean_path"]
    assert r1["projected_liquidity_impact"] == r2["projected_liquidity_impact"]


def test_simulate_climate_mean_reverts():
    """Given mean-reverting parameters, when simulating, then path converges toward mu."""
    result = simulate_climate(
        theta=2.0,
        mu=0.05,
        sigma=0.01,
        seasonal_amplitude=0.0,
        n_steps=500,
        n_paths=50,
        seed=42,
    )

    assert result["n_steps"] == 500
    final_mean = result["mean_path"][-1]
    assert abs(final_mean - 0.05) < 0.1
