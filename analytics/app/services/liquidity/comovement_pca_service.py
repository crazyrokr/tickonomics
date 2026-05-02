"""Liquidity comovement factor via PCA on cross-asset spreads."""

import numpy as np


def compute_comovement_factor(
    spread_matrix: list[list[float]],
    n_symbols: int | None = None,
) -> dict:
    if len(spread_matrix) < 3:
        return {"error": "At least 3 time points required"}

    data = np.array(spread_matrix)
    if data.ndim != 2:
        return {"error": "spread_matrix must be 2-dimensional"}

    if n_symbols is not None and data.shape[1] != n_symbols:
        return {"error": f"Expected {n_symbols} symbols, got {data.shape[1]}"}

    if data.shape[1] < 2:
        return {"error": "At least 2 symbols required for PCA"}

    diff = np.diff(data, axis=0)
    if diff.shape[0] < 2:
        return {"error": "Insufficient data after differencing"}

    std = np.std(diff, axis=0, ddof=1)
    std[std == 0] = 1.0
    standardized = (diff - np.mean(diff, axis=0)) / std

    cov = np.cov(standardized, rowvar=False)
    eigenvalues, _ = np.linalg.eigh(cov)
    eigenvalues = np.sort(eigenvalues)[::-1]

    total_var = np.sum(eigenvalues)
    if total_var == 0:
        return {"comovement_factor": 0.0, "pc1_variance_explained": 0.0, "pc2_variance_explained": 0.0}

    pc1_var = eigenvalues[0] / total_var
    pc2_var = eigenvalues[1] / total_var if len(eigenvalues) > 1 else 0.0

    comovement_factor = pc1_var + pc2_var

    return {
        "comovement_factor": round(float(comovement_factor), 4),
        "pc1_variance_explained": round(float(pc1_var), 4),
        "pc2_variance_explained": round(float(pc2_var), 4),
        "n_symbols": data.shape[1],
        "n_observations": data.shape[0],
    }
