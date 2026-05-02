"""Climate-liquidity model: stochastic simulation with mean-reversion and seasonal forcing."""

import numpy as np


def simulate_climate(
    adaptation_finance_increase: float = 0.1,
    carbon_tax: float = 50.0,
    theta: float = 0.5,
    mu: float = 0.02,
    sigma: float = 0.15,
    seasonal_amplitude: float = 0.05,
    seasonal_period: float = 252.0,
    n_steps: int = 252,
    n_paths: int = 100,
    seed: int | None = None,
) -> dict:
    rng = np.random.default_rng(seed)

    fossil_baseline = 0.60
    renewable_baseline = 0.25
    nuclear_baseline = 0.15

    fossil_reduction = min(0.5, adaptation_finance_increase * 0.4 + carbon_tax / 500.0)
    renewable_increase = fossil_reduction * 0.75
    nuclear_stable = fossil_reduction * 0.25

    paths = np.zeros((n_paths, n_steps))
    for p in range(n_paths):
        x = mu
        for t in range(n_steps):
            dt = 1.0 / n_steps
            seasonal = seasonal_amplitude * np.sin(2.0 * np.pi * t / seasonal_period)
            dW = rng.standard_normal() * np.sqrt(dt)
            x = x + theta * (mu + seasonal - x) * dt + sigma * dW
            paths[p, t] = x

    mean_path = np.mean(paths, axis=0)
    std_path = np.std(paths, axis=0)

    projected_impact = float(np.mean(mean_path[-20:]))
    converged = bool(np.abs(mean_path[-1] - mu) < 2.0 * float(std_path[-1]))

    return {
        "projected_liquidity_impact": round(projected_impact, 6),
        "energy_mix_shift": {
            "fossil_reduction": round(float(fossil_reduction), 4),
            "renewable_increase": round(float(renewable_increase), 4),
            "nuclear_stable": round(float(nuclear_stable), 4),
        },
        "converged_to_mean": converged,
        "paths": [[round(float(v), 6) for v in paths[p]] for p in range(min(n_paths, 10))],
        "mean_path": [round(float(v), 6) for v in mean_path],
        "std_path": [round(float(v), 6) for v in std_path],
        "n_paths": n_paths,
        "n_steps": n_steps,
    }
