"""Diagnostic service: QQ-plot data, ACF, convergence diagnostics."""

import numpy as np
from scipy import stats


def compute_diagnostics(returns: list[float]) -> dict:
    if len(returns) < 20:
        return {"error": "At least 20 observations required for diagnostics"}

    r = np.array(returns)

    sorted_r = np.sort(r)
    n = len(sorted_r)
    theoretical_quantiles = stats.norm.ppf(np.arange(1, n + 1) / (n + 1))

    qq_plot = {
        "sample_quantiles": [round(float(v), 6) for v in sorted_r[::max(1, n // 20)]],
        "theoretical_quantiles": [round(float(v), 6) for v in theoretical_quantiles[::max(1, n // 20)]],
    }

    max_lags = min(10, len(r) - 1)
    acf_raw = []
    acf_abs = []
    mean_r = np.mean(r)
    var_r = np.var(r, ddof=0)
    abs_r = np.abs(r)
    mean_abs = np.mean(abs_r)
    var_abs = np.var(abs_r, ddof=0)

    for lag in range(max_lags + 1):
        if lag == 0:
            acf_raw.append(1.0)
            acf_abs.append(1.0)
        else:
            cov_raw = np.mean((r[:-lag] - mean_r) * (r[lag:] - mean_r))
            acf_raw.append(round(float(cov_raw / var_r), 6) if var_r > 0 else 0.0)

            cov_abs = np.mean((abs_r[:-lag] - mean_abs) * (abs_r[lag:] - mean_abs))
            acf_abs.append(round(float(cov_abs / var_abs), 6) if var_abs > 0 else 0.0)

    cumsum = np.cumsum(r)
    cummean = cumsum / np.arange(1, n + 1)
    cumvar = np.array([np.var(r[:i + 1], ddof=1) for i in range(n)])

    step = max(1, n // 20)
    convergence = {
        "cumulative_mean": [round(float(v), 6) for v in cummean[::step]],
        "cumulative_variance": [round(float(v), 6) for v in cumvar[::step]],
    }

    return {
        "qq_plot": qq_plot,
        "acf": {
            "raw_returns": acf_raw,
            "absolute_returns": acf_abs,
            "max_lags": max_lags,
        },
        "convergence": convergence,
        "n_observations": n,
    }
