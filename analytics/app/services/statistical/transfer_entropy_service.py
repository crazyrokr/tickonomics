import numpy as np


def _transfer_entropy(source: np.ndarray, target: np.ndarray, lag: int = 1) -> float:
    n = len(source)
    x_future = target[lag:]
    x_past = target[:-lag]
    y_past = source[:-lag]

    bins = int(np.sqrt(n / 10)) + 1
    bins = max(bins, 3)

    joint_3d, _ = np.histogramdd(
        np.column_stack([x_future, x_past, y_past]),
        bins=bins,
    )

    joint_2d_xf_xp, _ = np.histogramdd(
        np.column_stack([x_future, x_past]),
        bins=bins,
    )

    joint_2d_xp_yp, _ = np.histogramdd(
        np.column_stack([x_past, y_past]),
        bins=bins,
    )

    marginal_xp, _ = np.histogram(x_past, bins=bins)

    total = n - lag

    p_joint = joint_3d / total
    p_xf_xp = joint_2d_xf_xp / total
    p_xp_yp = joint_2d_xp_yp / total
    p_xp = marginal_xp / total

    te = 0.0
    for i in range(bins):
        for j in range(bins):
            for k in range(bins):
                if p_joint[i, j, k] > 0 and p_xf_xp[i, j] > 0 and p_xp_yp[j, k] > 0 and p_xp[j] > 0:
                    p_cond_joint = p_joint[i, j, k] / p_xp_yp[j, k]
                    p_cond_marginal = p_xf_xp[i, j] / p_xp[j]
                    if p_cond_marginal > 0:
                        te += p_joint[i, j, k] * np.log(p_cond_joint / p_cond_marginal)

    return max(0.0, te)


def compute_transfer_entropy(
    source: list[float], target: list[float], lag: int = 1, n_bootstraps: int = 1000
) -> dict:
    src = np.array(source)
    tgt = np.array(target)

    te_observed = _transfer_entropy(src, tgt, lag)

    rng = np.random.default_rng(42)
    te_null = np.zeros(n_bootstraps)
    for i in range(n_bootstraps):
        permuted = rng.permutation(src)
        te_null[i] = _transfer_entropy(permuted, tgt, lag)

    p_value = float(np.mean(te_null >= te_observed))

    return {
        "entropy_bits": te_observed,
        "p_value": p_value,
        "lag": lag,
        "is_significant": p_value < 0.05,
    }
