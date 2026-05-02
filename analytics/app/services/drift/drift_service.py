"""Drift-diffusion simulation: Ito process via Euler-Maruyama discretization."""

import numpy as np


def simulate_ito(
    mu: float = 0.001,
    sigma: float = 0.02,
    initial_value: float = 0.5,
    t_max: float = 30.0,
    n_steps: int = 252,
    n_paths: int = 100,
    seed: int | None = None,
) -> dict:
    rng = np.random.default_rng(seed)

    dt = t_max / n_steps
    paths = np.zeros((n_paths, n_steps + 1))
    paths[:, 0] = initial_value

    for p in range(n_paths):
        dW = rng.standard_normal(n_steps) * np.sqrt(dt)
        for t in range(n_steps):
            paths[p, t + 1] = paths[p, t] + mu * dt + sigma * dW[t]

    terminal_values = paths[:, -1]
    theoretical_mean = initial_value + mu * t_max
    theoretical_std = sigma * np.sqrt(t_max)

    return {
        "paths": [[round(float(v), 6) for v in paths[p]] for p in range(min(n_paths, 10))],
        "terminal_mean": round(float(np.mean(terminal_values)), 6),
        "terminal_std": round(float(np.std(terminal_values)), 6),
        "theoretical_mean": round(theoretical_mean, 6),
        "theoretical_std": round(theoretical_std, 6),
        "hitting_probability": None,
        "expected_hitting_time": None,
        "n_paths": n_paths,
        "n_steps": n_steps,
    }


def barrier_hitting_probability(
    mu: float = 0.001,
    sigma: float = 0.02,
    initial_value: float = 0.5,
    barrier_level: float = 2.0,
    t_max: float = 30.0,
    n_steps: int = 252,
    n_paths: int = 1000,
    seed: int | None = None,
) -> dict:
    rng = np.random.default_rng(seed)

    dt = t_max / n_steps
    n_hit = 0
    hitting_times = []

    for _ in range(n_paths):
        x = initial_value
        hit = False
        for t in range(1, n_steps + 1):
            dW = rng.standard_normal() * np.sqrt(dt)
            x = x + mu * dt + sigma * dW
            if x >= barrier_level:
                n_hit += 1
                hitting_times.append(t * dt)
                hit = True
                break

    hit_prob = n_hit / n_paths
    expected_time = float(np.mean(hitting_times)) if hitting_times else None

    return {
        "hitting_probability": round(hit_prob, 4),
        "expected_hitting_time": round(expected_time, 2) if expected_time is not None else None,
        "n_paths_hitting_barrier": n_hit,
        "n_paths": n_paths,
        "barrier_level": barrier_level,
        "initial_value": initial_value,
    }
