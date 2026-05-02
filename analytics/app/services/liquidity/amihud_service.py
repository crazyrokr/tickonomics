"""Amihud illiquidity measure for high-frequency price impact estimation."""

import numpy as np


def compute_amihud(
    returns: list[float],
    dollar_volumes: list[float],
) -> dict:
    if len(returns) < 2:
        return {"error": "At least 2 observations required"}

    if len(returns) != len(dollar_volumes):
        return {"error": "returns and dollar_volumes must have same length"}

    r = np.array(returns)
    dv = np.array(dollar_volumes)

    zero_vol = dv == 0
    if np.all(zero_vol):
        return {"error": "All dollar volumes are zero"}

    valid = ~zero_vol
    r_valid = r[valid]
    dv_valid = dv[valid]

    amihud = np.mean(np.abs(r_valid) / dv_valid)
    normalized = float(np.mean(np.abs(r_valid)) / np.mean(dv_valid)) if np.mean(dv_valid) > 0 else 0.0

    return {
        "amihud_measure": round(float(amihud), 10),
        "normalized_measure": round(normalized, 10),
        "n_observations": int(np.sum(valid)),
    }
