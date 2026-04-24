import numpy as np
from statsmodels.stats.multitest import multipletests


def apply_fdr_correction(p_values: list[float], alpha: float = 0.05) -> dict:
    if not p_values:
        return {
            "actionable_mask": [],
            "adjusted_p_values": [],
            "rejected_count": 0,
            "discovery_reduction": 0.0,
        }

    p_array = np.array(p_values)
    m = len(p_values)
    reject, adjusted_p, _, _ = multipletests(p_array, alpha=alpha, method="fdr_bh")

    return {
        "actionable_mask": reject.tolist(),
        "adjusted_p_values": adjusted_p.tolist(),
        "rejected_count": int(np.sum(reject)),
        "discovery_reduction": float(1.0 - np.sum(reject) / m),
    }
