"""Backtest robustness scan via Sobol quasi-random parameter sweep."""

import numpy as np
from scipy.stats import qmc


def robustness_scan(
    strategy_returns: list[float],
    parameter_ranges: dict[str, list[float]],
    n_samples: int = 128,
    seed: int | None = None,
) -> dict:
    if len(strategy_returns) < 30:
        return {"error": "At least 30 return observations required"}

    if not parameter_ranges:
        return {"error": "Parameter ranges must be non-empty"}

    for name, bounds in parameter_ranges.items():
        if len(bounds) != 2 or bounds[0] >= bounds[1]:
            return {"error": f"Parameter '{name}' must have [min, max] with min < max"}

    param_names = list(parameter_ranges.keys())
    dim = len(param_names)
    lower = np.array([parameter_ranges[p][0] for p in param_names])
    upper = np.array([parameter_ranges[p][1] for p in param_names])

    m = max(1, int(np.ceil(np.log2(n_samples))))
    actual_samples = 2 ** m

    sampler = qmc.Sobol(d=dim, scramble=True, seed=seed)
    sobol_points = sampler.random_base2(m)
    scaled = qmc.scale(sobol_points, lower, upper)

    results = []
    sharpe_values = []
    drawdown_values = []

    returns_arr = np.array(strategy_returns)
    n_returns = len(returns_arr)

    for i in range(actual_samples):
        params = {param_names[j]: float(scaled[i, j]) for j in range(dim)}

        n_active = max(10, int(n_returns * params.get("window", 0.5)))
        n_active = min(n_active, n_returns)
        active_returns = returns_arr[-n_active:]

        threshold = params.get("threshold", 0.0)
        if threshold > 0:
            mask = np.abs(active_returns) > threshold
            if mask.sum() < 5:
                mask = np.ones(len(active_returns), dtype=bool)
            active_returns = active_returns[mask]

        sharpe = float(np.mean(active_returns) / np.std(active_returns, ddof=1)) if np.std(active_returns, ddof=1) > 0 else 0.0
        cumulative = np.cumsum(active_returns)
        peak = np.maximum.accumulate(cumulative)
        drawdown = float(np.min(cumulative - peak))
        win_rate = float(np.mean(active_returns > 0))

        sharpe_values.append(sharpe)
        drawdown_values.append(drawdown)

        results.append({
            "params": {k: round(v, 4) for k, v in params.items()},
            "sharpe": round(sharpe, 4),
            "max_drawdown": round(drawdown, 6),
            "win_rate": round(win_rate, 4),
        })

    best_idx = int(np.argmax(sharpe_values))
    worst_idx = int(np.argmin(drawdown_values))

    heatmap = {}
    for name in param_names:
        bins = np.linspace(lower[list(param_names).index(name)], upper[list(param_names).index(name)], 6)
        digitized = np.digitize(scaled[:, list(param_names).index(name)], bins)
        heatmap[name] = {
            f"bin_{b}": round(float(np.mean([sharpe_values[j] for j in range(actual_samples) if digitized[j] == b])), 4)
            for b in range(1, len(bins))
        }

    coverage = float(_compute_coverage(scaled, lower, upper))

    return {
        "results": results,
        "best_params": results[best_idx],
        "worst_drawdown_params": results[worst_idx],
        "heatmap_data": heatmap,
        "coverage": round(coverage, 4),
        "n_samples": actual_samples,
        "mean_sharpe": round(float(np.mean(sharpe_values)), 4),
        "std_sharpe": round(float(np.std(sharpe_values, ddof=1)), 4),
    }


def _compute_coverage(samples: np.ndarray, lower: np.ndarray, upper: np.ndarray) -> float:
    n, d = samples.shape
    if n == 0 or d == 0:
        return 0.0
    normalized = (samples - lower) / (upper - lower)
    n_bins = max(2, int(n ** (1.0 / d)))
    occupied = set()
    for i in range(n):
        bin_idx = tuple(int(min(normalized[i, j] * n_bins, n_bins - 1)) for j in range(d))
        occupied.add(bin_idx)
    return len(occupied) / (n_bins ** d)
